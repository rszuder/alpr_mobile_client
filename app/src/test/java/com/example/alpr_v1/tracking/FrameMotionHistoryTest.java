package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FrameMotionHistoryTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void composesOnlyMotionNewerThanInferenceSourceFrame() {
        FrameMotionHistory history = new FrameMotionHistory();
        history.record(100L, FrameMotionTransform.translation(0.03f, 0.01f));
        history.record(200L, FrameMotionTransform.translation(-0.08f, 0.02f));
        history.record(300L, FrameMotionTransform.translation(0.01f, -0.04f));

        FrameMotionTransform motion = history.transformAfter(100L);

        assertTrue(motion.valid);
        assertEquals(-0.07f, motion.mapX(0.5f, 0.5f) - 0.5f, EPSILON);
        assertEquals(-0.02f, motion.mapY(0.5f, 0.5f) - 0.5f, EPSILON);
    }

    @Test
    public void transformBetweenStopsAtRequestedDestinationFrame() {
        FrameMotionHistory history = new FrameMotionHistory();
        history.record(100L, FrameMotionTransform.translation(0.10f, 0f));
        history.record(200L, FrameMotionTransform.translation(0.20f, 0f));
        history.record(300L, FrameMotionTransform.translation(0.30f, 0f));

        FrameMotionTransform transform = history.transformBetween(100L, 200L);

        assertEquals(0.20f, transform.mapX(0.5f, 0.5f) - 0.5f, 0.0001f);
    }

    @Test
    public void resetMakesOldInferenceTimestampUnusable() {
        FrameMotionHistory history = new FrameMotionHistory();
        history.record(100L, FrameMotionTransform.translation(0.1f, 0f));
        history.reset();

        assertFalse(history.transformAfter(50L).valid);
    }

    @Test
    public void sourceAtLatestFrameNeedsIdentityCompensation() {
        FrameMotionHistory history = new FrameMotionHistory();
        history.record(100L, FrameMotionTransform.translation(0.1f, 0f));

        FrameMotionTransform motion = history.transformAfter(100L);

        assertTrue(motion.valid);
        assertFalse(motion.significant());
    }
}
