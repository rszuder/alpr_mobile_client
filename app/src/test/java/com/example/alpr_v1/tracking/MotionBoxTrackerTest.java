package com.example.alpr_v1.tracking;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MotionBoxTrackerTest {
    @Test
    public void removesOverlappingCopiesFromOneFrame() {
        MotionBoxTracker tracker = new MotionBoxTracker();
        List<MotionBoxTracker.Result> result = tracker.update(
                Arrays.asList(
                        observation(0.10f, 0.20f, 0.40f, 0.30f, "WE12345", 0),
                        observation(0.11f, 0.20f, 0.41f, 0.31f, "WE12345", 1)
                ),
                1_000_000_000L,
                1_000_000_000L
        );

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).sourceIndex);
    }

    @Test
    public void predictsPositionForwardDuringCameraMovement() {
        MotionBoxTracker tracker = new MotionBoxTracker();
        tracker.update(
                Collections.singletonList(observation(0.10f, 0.20f, 0.30f, 0.30f, "WE12345", 0)),
                1_000_000_000L,
                1_000_000_000L
        );
        List<MotionBoxTracker.Result> result = tracker.update(
                Collections.singletonList(observation(0.20f, 0.20f, 0.40f, 0.30f, "WE12345", 0)),
                1_100_000_000L,
                1_200_000_000L
        );

        assertEquals(1, result.size());
        assertTrue(result.get(0).box.centerX() > 0.30f);
    }

    @Test
    public void replacesUnmatchedTrackWithoutLeavingGhostBox() {
        MotionBoxTracker tracker = new MotionBoxTracker();
        tracker.update(
                Collections.singletonList(observation(0.05f, 0.20f, 0.20f, 0.30f, "A", 0)),
                1_000_000_000L,
                1_000_000_000L
        );
        List<MotionBoxTracker.Result> result = tracker.update(
                Collections.singletonList(observation(0.75f, 0.20f, 0.90f, 0.30f, "B", 0)),
                1_100_000_000L,
                1_100_000_000L
        );

        assertEquals(1, result.size());
        assertEquals("B", result.get(0).label);
    }

    @Test
    public void bridgesOnlyOneEmptyDetectionFrame() {
        MotionBoxTracker tracker = new MotionBoxTracker();
        tracker.update(
                Collections.singletonList(observation(0.10f, 0.20f, 0.30f, 0.30f, "A", 0)),
                1_000_000_000L,
                1_000_000_000L
        );

        assertEquals(1, tracker.update(
                Collections.emptyList(), 1_100_000_000L, 1_100_000_000L
        ).size());
        assertTrue(tracker.update(
                Collections.emptyList(), 1_200_000_000L, 1_200_000_000L
        ).isEmpty());
    }

    @Test
    public void smoothsSmallFrameToFrameJitter() {
        MotionBoxTracker tracker = new MotionBoxTracker();
        tracker.update(
                Collections.singletonList(observation(0.15f, 0.20f, 0.25f, 0.30f, "A", 0)),
                1_000_000_000L,
                1_000_000_000L
        );
        List<MotionBoxTracker.Result> result = tracker.update(
                Collections.singletonList(observation(0.16f, 0.20f, 0.26f, 0.30f, "A", 0)),
                1_100_000_000L,
                1_100_000_000L
        );

        assertEquals(1, result.size());
        assertTrue(result.get(0).box.centerX() > 0.20f);
        assertTrue(result.get(0).box.centerX() < 0.21f);
    }

    private static MotionBoxTracker.Observation observation(
            float left,
            float top,
            float right,
            float bottom,
            String label,
            int sourceIndex
    ) {
        return new MotionBoxTracker.Observation(
                new MotionBoxTracker.Box(left, top, right, bottom),
                label,
                sourceIndex
        );
    }
}
