package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void resultTimeProjectionKeepsCurrentMpObservationMeasured() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();

        VehicleTrackingFrame measured = coordinator.updateFromMp(
                4L,
                1_000_000_000L,
                1_250_000_000L,
                Collections.singletonList(observation(0.2f))
        );

        assertFalse(measured.candidates.get(0).predicted);
        assertEquals(
                1_000_000_000L,
                measured.candidates.get(0).lastMeasurementTimestampNanos
        );
        assertEquals(250_000_000L, measured.candidates.get(0).predictionAgeNanos);

        VehicleTrackingFrame nextFramePrediction = coordinator.predict(
                5L, 1_300_000_000L, 1_300_000_000L
        );
        assertTrue(nextFramePrediction.candidates.get(0).predicted);
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
    public void predictionMovesBackgroundVehicleWhileFocusedTargetSkipsMt() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        coordinator.updateFromMp(
                1L,
                1_000_000_000L,
                1_000_000_000L,
                java.util.Arrays.asList(observation(0.10f), observation(0.55f))
        );
        VehicleTrackingFrame measured = coordinator.updateFromMp(
                2L,
                1_100_000_000L,
                1_100_000_000L,
                java.util.Arrays.asList(observation(0.10f), observation(0.60f))
        );
        VehicleCandidate backgroundBefore = rightmost(measured.candidates);

        VehicleTrackingFrame predicted = coordinator.predict(
                3L,
                1_200_000_000L,
                1_200_000_000L
        );
        VehicleCandidate backgroundAfter = candidateByEntity(
                predicted.candidates, backgroundBefore.entityId
        );

        assertTrue(backgroundAfter.predicted);
        assertTrue(backgroundAfter.bounds.left > backgroundBefore.bounds.left);
        assertEquals(measured.candidates.size(), predicted.candidates.size());
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

    @Test
    public void predictionExpiresTrackButMeasuredUpdateExpiresEntityExactlyOnce() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        coordinator.updateFromMp(
                1L,
                1_000_000_000L,
                1_000_000_000L,
                Collections.singletonList(observation(0.2f))
        );
        coordinator.drainEvents();

        coordinator.predict(2L, 17_000_000_001L, 17_000_000_001L);
        List<VehicleTrackingEvent> predictedExpiration = coordinator.drainEvents();

        assertEquals(1, eventCount(predictedExpiration, "vehicle_track_expired"));
        assertEquals(0, eventCount(predictedExpiration, "vehicle_entity_expired"));
        assertEquals(1, coordinator.repository().size());

        coordinator.updateFromMp(
                3L,
                17_100_000_000L,
                17_100_000_000L,
                Collections.emptyList()
        );
        List<VehicleTrackingEvent> measuredExpiration = coordinator.drainEvents();

        assertEquals(0, eventCount(measuredExpiration, "vehicle_track_expired"));
        assertEquals(1, eventCount(measuredExpiration, "vehicle_entity_expired"));
        assertEquals(0, coordinator.repository().size());

        coordinator.predict(4L, 18_000_000_000L, 18_000_000_000L);
        assertEquals(0, coordinator.drainEvents().size());
    }

    @Test
    public void trackTtlAdaptsToMeasuredMpCadenceWithinEntityTtl() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame first = coordinator.updateFromMp(
                1L,
                1_000_000_000L,
                1_100_000_000L,
                Collections.singletonList(observation(0.2f))
        );
        long entityId = first.candidates.get(0).entityId;
        assertEquals(
                VehicleTrackingCoordinator.MIN_ADAPTIVE_TRACK_TTL_NANOS,
                coordinator.currentTrackTtlNanos()
        );

        VehicleTrackingFrame second = coordinator.updateFromMp(
                2L,
                3_600_000_000L,
                3_700_000_000L,
                Collections.singletonList(observation(0.22f))
        );

        assertEquals(entityId, second.candidates.get(0).entityId);
        assertEquals(4_550_000_000L, coordinator.currentTrackTtlNanos());
        assertTrue(coordinator.currentTrackTtlNanos()
                < VehicleTrackManager.DEFAULT_ENTITY_TTL_NANOS);
    }

    @Test
    public void slowDeviceMpCadencePreservesStandingVehicleIdentity() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame first = coordinator.updateFromMp(
                1L,
                1_000_000_000L,
                1_250_000_000L,
                Collections.singletonList(observation(0.2f))
        );

        VehicleTrackingFrame second = coordinator.updateFromMp(
                2L,
                6_800_000_000L,
                7_050_000_000L,
                Collections.singletonList(observation(0.2f))
        );

        assertEquals(first.candidates.get(0).entityId, second.candidates.get(0).entityId);
        assertEquals(
                10_150_000_000L,
                coordinator.currentTrackTtlNanos()
        );
        assertFalse(second.candidates.get(0).predicted);
        assertEquals(1, coordinator.repository().size());
    }

    @Test
    public void recoveryProtectionKeepsOldEntityEligibleForMeasuredReassociation() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame before = coordinator.updateFromMp(
                1L,
                1_000_000_000L,
                1_250_000_000L,
                Collections.singletonList(observation(0.2f))
        );
        long protectedEntityId = before.candidates.get(0).entityId;

        VehicleTrackingFrame recovered = coordinator.updateFromMp(
                2L,
                17_000_000_000L,
                17_250_000_000L,
                Collections.singletonList(observation(0.2f)),
                Collections.singleton(protectedEntityId)
        );

        assertEquals(protectedEntityId, recovered.candidates.get(0).entityId);
        assertFalse(recovered.candidates.get(0).predicted);
        assertEquals(
                17_000_000_000L,
                recovered.candidates.get(0).lastMeasurementTimestampNanos
        );
        assertEquals(1, coordinator.repository().size());
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

    private static int eventCount(
            List<VehicleTrackingEvent> events,
            String eventType
    ) {
        int count = 0;
        for (VehicleTrackingEvent event : events) {
            if (eventType.equals(event.eventType)) count++;
        }
        return count;
    }

    private static VehicleTrackManager.Observation observation(float left) {
        return new VehicleTrackManager.Observation(
                new NormalizedBounds(left, 0.2f, left + 0.3f, 0.7f),
                0.9f,
                new AppearanceDescriptor(new float[]{1f, 0f}),
                0
        );
    }

    private static VehicleCandidate rightmost(List<VehicleCandidate> candidates) {
        VehicleCandidate result = candidates.get(0);
        for (VehicleCandidate candidate : candidates) {
            if (candidate.bounds.left > result.bounds.left) result = candidate;
        }
        return result;
    }

    private static VehicleCandidate candidateByEntity(
            List<VehicleCandidate> candidates,
            long entityId
    ) {
        for (VehicleCandidate candidate : candidates) {
            if (candidate.entityId == entityId) return candidate;
        }
        throw new AssertionError("Missing entityId=" + entityId);
    }
}
