package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;
import com.example.alpr_v1.continuity.VehicleContinuityEvidence;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

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
        assertNotNull(SceneTransitionCoordinator.class.getDeclaredMethod(
                "completeSoftReacquire",
                SoftReacquireResult.class,
                long.class
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

    @Test
    public void reacquireSnapshotContainsVisiblePoolNotHistoricalRepository() {
        VehicleTrackingFrame visible = new VehicleTrackingFrame(
                9L,
                1_000L,
                1_010L,
                0L,
                Arrays.asList(candidate(11L), candidate(12L), candidate(13L))
        );

        Set<Long> snapshot = MobileAlprEngine.snapshotVisibleContinuityEntityIds(
                visible,
                17L
        );

        assertEquals(4, snapshot.size());
        assertTrue(snapshot.contains(11L));
        assertTrue(snapshot.contains(12L));
        assertTrue(snapshot.contains(13L));
        assertTrue(snapshot.contains(17L));
    }

    private static VehicleCandidate candidate(long entityId) {
        return new VehicleCandidate(
                entityId,
                entityId + 100L,
                new NormalizedBounds(0.1f, 0.2f, 0.4f, 0.6f),
                0.9f,
                0.9f,
                0.1f,
                false,
                0,
                1_000L,
                1_010L
        );
    }
}
