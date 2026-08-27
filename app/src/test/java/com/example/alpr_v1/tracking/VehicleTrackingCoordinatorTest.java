package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.VehicleEntityRepository;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class VehicleTrackingCoordinatorTest {
    @Test
    public void stateSurvivesConsumerRecreationUntilExplicitSceneReset() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator(repository);
        VehicleTrackingFrame first = coordinator.updateFromMp(
                1L, 100L, 130L, Collections.singletonList(observation(0.1f))
        );
        long entityId = first.candidates.get(0).entityId;

        // A new engine/consumer uses the same runtime coordinator.
        VehicleTrackingFrame second = coordinator.updateFromMp(
                2L, 200L, 230L, Collections.singletonList(observation(0.12f))
        );

        assertEquals(entityId, second.candidates.get(0).entityId);
        assertSame(repository, coordinator.repository());
        assertEquals(0L, second.sceneGeneration);

        assertEquals(1L, coordinator.resetScene());
        assertEquals(0, coordinator.latestFrame().candidates.size());
        assertEquals(0, repository.size());
    }

    @Test
    public void predictionKeepsSourceAndSnapshotTimesSeparate() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        coordinator.updateFromMp(
                4L, 1_000L, 1_100L, Collections.singletonList(observation(0.2f))
        );

        VehicleTrackingFrame predicted = coordinator.predict(5L, 1_200L, 1_500L);

        assertEquals(5L, predicted.sourceFrameId);
        assertEquals(1_200L, predicted.sourceTimestampNanos);
        assertEquals(1_500L, predicted.snapshotTimestampNanos);
        assertEquals(500L, predicted.candidates.get(0).predictionAgeNanos);
    }

    @Test
    public void effectiveConfidenceDecaysWithAgeAndMisses() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame fresh = coordinator.updateFromMp(
                1L,
                1_000_000_000L,
                1_000_000_000L,
                Collections.singletonList(observation(0.2f))
        );
        VehicleTrackingFrame missed = coordinator.updateFromMp(
                2L,
                1_500_000_000L,
                1_800_000_000L,
                Collections.emptyList()
        );

        assertTrue(missed.candidates.get(0).predicted);
        assertEquals(1, missed.candidates.get(0).missedUpdates);
        assertTrue(missed.candidates.get(0).effectiveConfidence
                < fresh.candidates.get(0).effectiveConfidence);
        assertEquals(500_000_000L, coordinator.lastMpObservationGapNanos());
    }

    @Test
    public void emitsBoundedLifecycleEventsWithSceneIdentity() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        coordinator.updateFromMp(
                1L, 1_000_000_000L, 1_000_000_000L,
                Collections.singletonList(observation(0.2f))
        );
        List<VehicleTrackingEvent> created = coordinator.drainEvents();

        assertTrue(hasEvent(created, "vehicle_track_created"));
        assertTrue(hasEvent(created, "vehicle_entity_created"));
        assertEquals(0, coordinator.drainEvents().size());

        for (int index = 0; index < 200; index++) {
            coordinator.recordEvent(
                    "synthetic", 1L, 1L, 0L, index, index, "bounded_test"
            );
        }
        assertEquals(128, coordinator.drainEvents().size());
    }

    private static boolean hasEvent(
            List<VehicleTrackingEvent> events,
            String eventType
    ) {
        for (VehicleTrackingEvent event : events) {
            if (eventType.equals(event.eventType)) return true;
        }
        return false;
    }

    private static VehicleTrackManager.Observation observation(float left) {
        return new VehicleTrackManager.Observation(
                new NormalizedBounds(left, 0.2f, left + 0.3f, 0.7f),
                0.9f,
                new AppearanceDescriptor(new float[]{1f, 0f}),
                0
        );
    }
}
