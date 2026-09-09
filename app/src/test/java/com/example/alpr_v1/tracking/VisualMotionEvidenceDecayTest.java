package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VisualMotionEvidenceDecayTest {
    @Test
    public void rockingCameraDropoutDoesNotBecomeHardCutInsideFreshEvidenceWindow() {
        VisualMotionEvidenceDecay decay = new VisualMotionEvidenceDecay();
        long lastMotion = 10_000_000_000L;
        decay.record(lastMotion, FrameMotionQuality.syntheticReliable(), false);
        // Phone log: valid motion at 16:55:52.913, failed flow 150 ms later,
        // 51.6% changed pixels, gyro momentarily quiet at the reversal.
        for (long offset : new long[]{150_000_000L, 497_000_000L, 700_000_000L}) {
            VisualMotionEvidenceDecay.Snapshot recent = decay.snapshot(lastMotion + offset);
            assertFalse(com.example.alpr_v1.ui.PreviewContinuityUiPolicy
                    .shouldForceHardSceneBoundaryFromDirectLuma(
                            true, .516f, 49.7f, true, .516f, 49.7f,
                            false, recent.protectsContinuity()));
        }
        VisualMotionEvidenceDecay.Snapshot expired = decay.snapshot(
                lastMotion + VisualMotionEvidenceDecay.SETTLE_RETENTION_NANOS + 1L);
        assertTrue(com.example.alpr_v1.ui.PreviewContinuityUiPolicy
                .shouldForceHardSceneBoundaryFromDirectLuma(
                        true, .516f, 49.7f, true, .516f, 49.7f,
                        false, expired.protectsContinuity()));
        decay.reset();
        assertFalse(decay.snapshot(lastMotion).protectsContinuity());
    }
    @Test
    public void oneMotionFrameIsNoLongerCurrentEvidenceAfterOneSecond() {
        VisualMotionEvidenceDecay decay = new VisualMotionEvidenceDecay();
        long started = 10_000_000_000L;
        decay.record(started, FrameMotionQuality.syntheticReliable(), false);

        assertTrue(decay.snapshot(started + 300_000_000L).motionEstimated);
        VisualMotionEvidenceDecay.Snapshot afterOneSecond =
                decay.snapshot(started + 1_000_000_000L);
        assertFalse(afterOneSecond.motionEstimated);
        assertTrue(afterOneSecond.settling);
    }

    @Test
    public void settlingAlsoExpiresAndUnreliableMotionIsIgnored() {
        VisualMotionEvidenceDecay decay = new VisualMotionEvidenceDecay();
        long started = 10_000_000_000L;
        decay.record(started, FrameMotionQuality.unavailable(30), false);
        assertFalse(decay.snapshot(started).motionEstimated);

        decay.record(started, FrameMotionQuality.syntheticReliable(), true);
        assertFalse(decay.snapshot(
                started + VisualMotionEvidenceDecay.SETTLE_RETENTION_NANOS + 1L
        ).settling);
    }
}
