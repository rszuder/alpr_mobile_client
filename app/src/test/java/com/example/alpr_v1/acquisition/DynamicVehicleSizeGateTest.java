package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicVehicleSizeGateTest {
    private static final int WIDTH = 640, HEIGHT = 480;
    @Test public void changingConfiguredThresholdsRequiresNewMpAndRespectsNewValues() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 140, 100, 1);
        assertTrue(gate.canSchedule(7));
        gate.setConfig(new DynamicVehicleSizeGate.Config(160, 120, 140, 100));
        assertFalse(gate.canSchedule(7));
        observe(gate, 140, 100, 2);
        assertFalse(gate.canSchedule(7));
        observe(gate, 160, 120, 3);
        assertTrue(gate.canSchedule(7));
        gate.setConfig(new DynamicVehicleSizeGate.Config(160, 120, 140, 100));
        assertTrue(gate.canSchedule(7));
    }

    @Test public void bothDimensionsMustReachEntryThreshold() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 119, 200, 1);
        assertFalse(gate.canSchedule(7));
        observe(gate, 200, 79, 2);
        assertFalse(gate.canSchedule(7));
        observe(gate, 120, 80, 3);
        assertTrue(gate.canSchedule(7));
        assertTrue(gate.check(7, stamp(3), WIDTH, HEIGHT).allowed());
    }

    @Test public void hysteresisKeepsQualifiedEntityUntilEitherExitThresholdIsCrossed() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 120, 80, 1);
        observe(gate, 110, 70, 2);
        assertTrue(gate.canSchedule(7));
        observe(gate, 100, 64, 3);
        assertTrue(gate.canSchedule(7));
        observe(gate, 99, 70, 4);
        assertFalse(gate.canSchedule(7));
        observe(gate, 110, 70, 5);
        assertFalse(gate.canSchedule(7));
        observe(gate, 120, 80, 6);
        assertTrue(gate.canSchedule(7));
        observe(gate, 150, 63, 7);
        assertFalse(gate.canSchedule(7));
    }

    @Test public void admissionCannotBeReusedForAnotherImageOrScaledInput() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 130, 90, 1);
        assertTrue(gate.canSchedule(7));
        assertEquals(DynamicVehicleSizeGate.Reason.VEHICLE_MEASUREMENT_REQUIRED,
                gate.check(7, stamp(2), WIDTH, HEIGHT).reason);
        assertEquals(DynamicVehicleSizeGate.Reason.VEHICLE_MEASUREMENT_REQUIRED,
                gate.check(7, stamp(1), WIDTH * 2, HEIGHT * 2).reason);
        assertTrue(gate.check(7, stamp(1), WIDTH, HEIGHT).allowed());
    }

    @Test public void secondCheckRejectsShrinkAfterQueueSelection() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 200, 140, 1);
        assertTrue(gate.canSchedule(7));
        observe(gate, 80, 50, 2);
        DynamicVehicleSizeGate.Decision rejected = gate.check(7, stamp(2), WIDTH, HEIGHT);
        assertEquals(DynamicVehicleSizeGate.Reason.VEHICLE_TOO_SMALL, rejected.reason);
        assertEquals(80f, rejected.widthPx, 0.01f);
        assertEquals(50f, rejected.heightPx, 0.01f);
    }

    @Test public void absentFreshDetectionCannotReuseEarlierGeometryOrHysteresis() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 150, 90, 1);
        gate.observe(stamp(2), WIDTH, HEIGHT, Collections.emptyMap());
        assertFalse(gate.canSchedule(7));
        assertEquals(DynamicVehicleSizeGate.Reason.VEHICLE_MEASUREMENT_REQUIRED,
                gate.check(7, stamp(2), WIDTH, HEIGHT).reason);
        observe(gate, 110, 70, 3);
        assertFalse(gate.canSchedule(7));
    }

    @Test public void sceneAndCameraTransformChangesInvalidateMeasurement() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 200, 140, 1);
        for (ContinuityStamp changed : new ContinuityStamp[]{
                new ContinuityStamp(2, 0, 0, 1, 1, SourceTimestampDomain.CAMERAX_SENSOR),
                new ContinuityStamp(1, 1, 0, 1, 1, SourceTimestampDomain.CAMERAX_SENSOR),
                new ContinuityStamp(1, 0, 1, 1, 1, SourceTimestampDomain.CAMERAX_SENSOR)}) {
            assertFalse(gate.check(7, changed, WIDTH, HEIGHT).allowed());
        }
        gate.reset();
        assertFalse(gate.canSchedule(7));
    }

    @Test public void disabledGateDoesNotChangeStaticOrFullFrameAdmission() {
        DynamicVehicleSizeGate gate = enabled();
        observe(gate, 30, 20, 1);
        assertFalse(gate.canSchedule(7));
        gate.setEnabled(false);
        assertTrue(gate.canSchedule(7));
        assertTrue(gate.check(7, null, 0, 0).allowed());
        gate.setEnabled(true);
        assertFalse(gate.canSchedule(7));
    }

    @Test public void configurationCanBeCalibratedWithoutChangingIdentityLogic() {
        DynamicVehicleSizeGate gate = new DynamicVehicleSizeGate(
                new DynamicVehicleSizeGate.Config(200, 150, 180, 120));
        gate.setEnabled(true);
        observe(gate, 150, 100, 1);
        assertFalse(gate.canSchedule(7));
        observe(gate, 200, 150, 2);
        assertTrue(gate.canSchedule(7));
    }

    private static DynamicVehicleSizeGate enabled() {
        DynamicVehicleSizeGate gate = new DynamicVehicleSizeGate();
        gate.setEnabled(true);
        return gate;
    }
    private static void observe(DynamicVehicleSizeGate gate, int width, int height, long sequence) {
        gate.observe(stamp(sequence), WIDTH, HEIGHT, Collections.singletonMap(7L,
                new NormalizedBounds(0, 0, width / (float) WIDTH, height / (float) HEIGHT)));
    }
    private static ContinuityStamp stamp(long sequence) {
        return new ContinuityStamp(1, 0, 0, sequence, sequence, SourceTimestampDomain.CAMERAX_SENSOR);
    }
}
