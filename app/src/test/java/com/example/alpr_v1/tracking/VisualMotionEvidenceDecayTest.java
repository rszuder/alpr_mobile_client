package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VisualMotionEvidenceDecayTest {
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
