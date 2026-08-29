package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.VehicleContinuityEvidence;

import org.junit.Test;

public final class MobileAlprEngineContinuityApiTest {
    @Test
    public void engineSeparatesHardResetHoldReacquireAndTargetRelease() throws Exception {
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "hardResetScene", String.class
        ));
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "beginSoftHold", long.class, String.class
        ));
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "beginSoftReacquire",
                long.class,
                String.class,
                long.class,
                long.class
        ));
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "releaseFocusedTarget", String.class
        ));
    }

    @Test
    public void secondarySceneDetectorIsNotOwnedByMutatingEngine()
            throws Exception {
        assertNotNull(AlprPipeline.class.getDeclaredField(
                "rotatedSceneDetector"
        ));
        try {
            MobileAlprEngine.class.getDeclaredField("sceneChangeDetector");
            org.junit.Assert.fail("engine must not own the secondary detector");
        } catch (NoSuchFieldException expected) {
            // Expected: preflight belongs to AlprPipeline.
        }
        try {
            MobileAlprEngine.class.getDeclaredMethod(
                    "consumeInternalSceneEvidence"
            );
            org.junit.Assert.fail("engine must not expose post-run scene evidence");
        } catch (NoSuchMethodException expected) {
            // Expected: evidence is consumed before engine.run().
        }
    }

    @Test
    public void forcedFreshMpContinuesOnlyWhileNoFreshMeasurementExists() {
        SoftReacquireReport stale = SoftReacquireReport.pending(
                VehicleContinuityEvidence.empty(),
                "mp_source_frame_predates_recovery"
        );
        VehicleContinuityEvidence freshVehicles = new VehicleContinuityEvidence(
                1, 1, 1, 0, 0, 1f,
                0f, 0f, 0L, false, false, 1
        );
        SoftReacquireReport waitingForMt = SoftReacquireReport.pending(
                freshVehicles,
                "fresh_mp_recovered_active_vehicle_waiting_for_mt"
        );
        SoftReacquireReport terminal = SoftReacquireReport.terminal(
                SoftReacquireResult.VEHICLE_POOL_RECOVERED,
                freshVehicles,
                "fresh_mp_recovered_vehicle_pool"
        );

        assertTrue(MobileAlprEngine.shouldContinueForcedFreshMp(stale));
        assertFalse(MobileAlprEngine.shouldContinueForcedFreshMp(waitingForMt));
        assertFalse(MobileAlprEngine.shouldContinueForcedFreshMp(terminal));
    }
}
