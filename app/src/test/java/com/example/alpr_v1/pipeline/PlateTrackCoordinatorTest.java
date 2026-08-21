package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.MotionBoxTracker;
import com.example.alpr_v1.vision.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlateTrackCoordinatorTest {
    @Test
    public void stopsSchedulingAfterTwoConsistentRecognitions() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        PlateTrackCoordinator.Decision first = update(coordinator, 1, 0.8f);
        assertTrue(first.recognize);
        coordinator.recordRecognition(
                first.trackId, 0.8f, 1,
                characters(), Arrays.asList("A", "1")
        );

        PlateTrackCoordinator.Decision second = update(coordinator, 2, 0.8f);
        assertTrue(second.recognize);
        coordinator.recordRecognition(
                second.trackId, 0.8f, 2,
                characters(), Arrays.asList("A", "1")
        );

        PlateTrackCoordinator.Decision third = update(coordinator, 3, 0.9f);
        assertFalse(third.recognize);
        assertTrue(third.currentResult.stable);
    }

    @Test
    public void rejectsInvalidGeometryBeforeCharacterStage() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        List<PlateTrackCoordinator.Decision> decisions = coordinator.update(
                Collections.singletonList(new PlateTrackCoordinator.Observation(
                        0,
                        new MotionBoxTracker.Box(0.1f, 0.2f, 0.4f, 0.3f),
                        0.9f,
                        false
                )),
                1,
                1_000_000_000L
        );

        assertFalse(decisions.get(0).recognize);
    }

    private static PlateTrackCoordinator.Decision update(
            PlateTrackCoordinator coordinator, long frame, float quality
    ) {
        return coordinator.update(
                Collections.singletonList(new PlateTrackCoordinator.Observation(
                        0,
                        new MotionBoxTracker.Box(0.1f, 0.2f, 0.4f, 0.3f),
                        quality,
                        true
                )),
                frame,
                frame * 100_000_000L
        ).get(0);
    }

    private static List<Detection> characters() {
        return Arrays.asList(
                new Detection(0, 0.9f, 0, 0, 10, 20, Collections.emptyList()),
                new Detection(1, 0.9f, 15, 0, 25, 20, Collections.emptyList())
        );
    }
}
