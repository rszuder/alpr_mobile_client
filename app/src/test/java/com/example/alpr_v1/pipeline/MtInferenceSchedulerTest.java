package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MtInferenceSchedulerTest {
    @Test
    public void healthyTrackerSkipsMtUntilPeriodicRefresh() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler(3);

        MtInferenceScheduler.Decision first = scheduler.plan(targetInput(1L, 0.92f));
        assertEquals(MtInferenceScheduler.Kind.TARGET_ROI, first.kind);
        scheduler.onMtResult(first, 1L, true);

        assertFalse(scheduler.plan(targetInput(2L, 0.91f)).runsMt());
        assertFalse(scheduler.plan(targetInput(3L, 0.90f)).runsMt());

        MtInferenceScheduler.Decision refresh = scheduler.plan(targetInput(4L, 0.90f));
        assertEquals(MtInferenceScheduler.Kind.TARGET_ROI, refresh.kind);
        assertEquals("periodic_refresh", refresh.reason);
    }

    @Test
    public void twoVehicleRoisAreStaggeredAcrossFrames() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();

        MtInferenceScheduler.Decision first = scheduler.plan(searchInput(10L, 2));
        assertEquals(MtInferenceScheduler.Kind.VEHICLE_ROI, first.kind);
        assertEquals(0, first.vehicleRegionIndex);
        scheduler.onMtResult(first, 10L, true);

        MtInferenceScheduler.Decision second = scheduler.plan(searchInput(11L, 2));
        assertEquals(MtInferenceScheduler.Kind.VEHICLE_ROI, second.kind);
        assertEquals(1, second.vehicleRegionIndex);
    }

    @Test
    public void failedVehicleRoiDefersFullFrameToNextFrame() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();

        MtInferenceScheduler.Decision roi = scheduler.plan(searchInput(20L, 2));
        assertEquals(MtInferenceScheduler.Kind.VEHICLE_ROI, roi.kind);
        scheduler.onMtResult(roi, 20L, false);

        MtInferenceScheduler.Decision fallback = scheduler.plan(searchInput(21L, 2));
        assertEquals(MtInferenceScheduler.Kind.FULL_FRAME, fallback.kind);
        assertEquals("deferred_full_frame_fallback", fallback.reason);
    }

    @Test
    public void failedTargetUsesExpandedRoiBeforeFullFrame() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();

        MtInferenceScheduler.Decision target = scheduler.plan(targetInput(30L, 0.40f));
        assertEquals(MtInferenceScheduler.Kind.TARGET_ROI, target.kind);
        scheduler.onMtResult(target, 30L, false);

        MtInferenceScheduler.Decision expanded = scheduler.plan(targetInput(31L, 0.40f));
        assertEquals(MtInferenceScheduler.Kind.TARGET_ROI, expanded.kind);
        assertEquals(2, expanded.recoveryLevel);
        assertEquals(0.45f, expanded.targetMargin, 0.0001f);
        scheduler.onMtResult(expanded, 31L, false);

        MtInferenceScheduler.Decision full = scheduler.plan(targetInput(32L, 0.40f));
        assertEquals(MtInferenceScheduler.Kind.FULL_FRAME, full.kind);
        assertEquals(3, full.recoveryLevel);
    }

    @Test
    public void rapidMotionForcesTargetRefresh() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();
        MtInferenceScheduler.Decision anchor = scheduler.plan(targetInput(40L, 0.95f));
        scheduler.onMtResult(anchor, 40L, true);

        MtInferenceScheduler.Input rapid = new MtInferenceScheduler.Input(
                41L,
                true,
                TargetSnapshot.State.LOCKED,
                0.95f,
                0,
                false,
                true,
                false,
                0
        );
        MtInferenceScheduler.Decision decision = scheduler.plan(rapid);
        assertTrue(decision.runsMt());
        assertEquals("rapid_camera_motion", decision.reason);
    }

    @Test
    public void failedFullFrameRequestsLevelFourVehicleRecovery() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();
        MtInferenceScheduler.Decision target = scheduler.plan(targetInput(50L, 0.30f));
        scheduler.onMtResult(target, 50L, false);
        MtInferenceScheduler.Decision expanded = scheduler.plan(targetInput(51L, 0.30f));
        scheduler.onMtResult(expanded, 51L, false);
        MtInferenceScheduler.Decision full = scheduler.plan(targetInput(52L, 0.30f));
        scheduler.onMtResult(full, 52L, false);

        assertTrue(scheduler.requiresVehicleRecovery());
        MtInferenceScheduler.Input recovery = new MtInferenceScheduler.Input(
                53L,
                true,
                TargetSnapshot.State.LOST,
                0f,
                3,
                false,
                false,
                false,
                2
        );
        MtInferenceScheduler.Decision levelFour = scheduler.plan(recovery);
        assertEquals(MtInferenceScheduler.Kind.VEHICLE_ROI, levelFour.kind);
        assertEquals(4, levelFour.recoveryLevel);
    }

    private static MtInferenceScheduler.Input targetInput(long frameId, float quality) {
        return new MtInferenceScheduler.Input(
                frameId,
                true,
                quality >= MtInferenceScheduler.QUALITY_OK
                        ? TargetSnapshot.State.LOCKED
                        : TargetSnapshot.State.DEGRADED,
                quality,
                quality < MtInferenceScheduler.QUALITY_INVALID ? 1 : 0,
                false,
                false,
                false,
                0
        );
    }

    private static MtInferenceScheduler.Input searchInput(long frameId, int regions) {
        return new MtInferenceScheduler.Input(
                frameId,
                false,
                TargetSnapshot.State.SEARCHING,
                0f,
                0,
                false,
                false,
                false,
                regions
        );
    }
}
