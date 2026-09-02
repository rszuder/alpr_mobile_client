package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Arrays;

public final class AcquisitionQueueTest {
    @Test
    public void entityIdOccursOnlyOnceEvenWhenSourceDuplicatesIt() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L), candidate(1L, 11L)), 0L, 100L);

        assertEquals(1, queue.snapshot(100L).size());
    }

    @Test
    public void changedVehicleTrackIdUpdatesExistingEntityEntry() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L)), 0L, 100L);
        queue.update(frame(1L, candidate(1L, 99L)), 0L, 200L);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(200L);
        assertEquals(1, snapshot.size());
        assertEquals(99L, snapshot.find(1L).vehicleTrackId);
        assertEquals(100L, snapshot.find(1L).firstQueuedRuntimeNanos);
    }

    @Test
    public void waitingAgePreventsNewerComparableCandidateFromStarvingOlderOne() {
        AcquisitionQueue queue = new AcquisitionQueue();
        VehicleCandidate older = candidate(1L, 11L, 0.72f, 0f, false, 0L);
        queue.update(frame(1L, older), 0L, 0L);
        VehicleCandidate newer = candidate(2L, 12L, 0.78f, 0f, false, 0L);
        queue.update(
                frame(1L, older, newer),
                0L,
                ScanAcquisitionProfile.DEFAULT.waitingAgeSaturationNanos
        );

        AcquisitionQueue.Selection selected = queue.selectNext(
                ScanAcquisitionProfile.DEFAULT.waitingAgeSaturationNanos
        );

        assertNotNull(selected);
        assertEquals(1L, selected.candidate.entityId);
        assertEquals(1f, selected.priority.waitingAge, 0.0001f);
    }

    @Test
    public void predictedCandidateReceivesExplicitPenalty() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.9f, 0f, true, 100_000_000L),
                candidate(2L, 12L, 0.9f, 0f, false, 0L)
        ), 0L, 100L);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(100L);
        assertTrue(snapshot.prioritiesByEntityId.get(1L).predictionPenalty > 0f);
        assertEquals(2L, queue.selectNext(100L).candidate.entityId);
    }

    @Test
    public void stalePredictionStaysQueuedButCannotStartNewSession() {
        AcquisitionQueue queue = new AcquisitionQueue();
        long olderThanPredictionLimit =
                ScanAcquisitionProfile.DEFAULT.maximumPredictionAgeNanos + 1L;
        queue.update(frame(
                1L,
                candidate(
                        1L, 11L, 0.8f, 0f,
                        false, olderThanPredictionLimit
                ),
                candidate(
                        2L, 12L, 0.8f, 0f,
                        true, olderThanPredictionLimit
                )
        ), 0L, 100L);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(100L);
        assertNotNull(snapshot.find(1L));
        assertNotNull(snapshot.find(2L));
        assertEquals(1L, queue.selectNext(100L).candidate.entityId);

        queue.complete(1L);
        assertEquals(1, queue.snapshot(101L).size());
        assertNull(queue.selectNext(101L));
    }

    @Test
    public void stalePredictionDoesNotResetWaitingAge() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.72f, 0f, false, 0L),
                candidate(2L, 12L, 0.78f, 0f, false, 0L)
        ), 0L, 100L);

        long staleAge = ScanAcquisitionProfile.DEFAULT.maximumPredictionAgeNanos + 1L;
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.72f, 0f, true, staleAge),
                candidate(2L, 12L, 0.78f, 0f, true, staleAge)
        ), 0L, 2_000_000_000L);
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.72f, 0f, false, 0L),
                candidate(2L, 12L, 0.78f, 0f, false, 0L)
        ), 0L, ScanAcquisitionProfile.DEFAULT.waitingAgeSaturationNanos);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(
                ScanAcquisitionProfile.DEFAULT.waitingAgeSaturationNanos
        );
        assertEquals(100L, snapshot.find(1L).firstQueuedRuntimeNanos);
        assertEquals(100L, snapshot.find(2L).firstQueuedRuntimeNanos);
    }

    @Test
    public void temporarilyMissingCandidateKeepsQueueAgeButIsNotSelectable() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L)), 0L, 100L);

        long missingAt = ScanAcquisitionProfile.DEFAULT.maximumPredictionAgeNanos
                + 1_000L;
        queue.update(frame(1L), 0L, missingAt);

        AcquisitionQueueSnapshot missing = queue.snapshot(missingAt);
        assertNotNull(missing.find(1L));
        assertTrue(missing.find(1L).predicted);
        assertNull(queue.selectNext(missingAt));

        queue.update(frame(1L, candidate(1L, 99L)), 0L, missingAt + 1_000L);
        AcquisitionCandidate returned = queue.snapshot(missingAt + 1_000L).find(1L);
        assertNotNull(returned);
        assertEquals(100L, returned.firstQueuedRuntimeNanos);
        assertEquals(99L, returned.vehicleTrackId);
    }

    @Test
    public void lowConfidencePredictionRetainsAttemptsUntilFreshMp() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L)), 0L, 100L);
        queue.selectNext(100L);
        queue.recordMtAttempt(1L, 100L);
        queue.defer(1L, 100L, 0L);

        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.05f, 0f, true, 600_000_000L)
        ), 0L, 200L);

        AcquisitionCandidate predicted = queue.snapshot(200L).find(1L);
        assertNotNull(predicted);
        assertEquals(1, predicted.mtAttempts);
        assertNull(queue.selectNext(200L));

        queue.update(frame(1L, candidate(1L, 99L)), 0L, 300L);
        AcquisitionCandidate measured = queue.snapshot(300L).find(1L);
        assertNotNull(measured);
        assertEquals(1, measured.mtAttempts);
        assertEquals(99L, measured.vehicleTrackId);
    }

    @Test
    public void exitUrgencyRaisesPriority() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.8f, 0f, false, 0L),
                candidate(2L, 12L, 0.8f, 1f, false, 0L)
        ), 0L, 100L);

        assertEquals(2L, queue.selectNext(100L).candidate.entityId);
    }

    @Test
    public void completedEntityNeverReturnsFromNewTechnicalObservation() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L)), 0L, 100L);
        queue.complete(1L);
        queue.update(frame(1L, candidate(1L, 99L)), 0L, 200L);

        assertEquals(0, queue.snapshot(200L).size());
        assertNull(queue.selectNext(200L));
    }

    @Test
    public void readingRegistrationEntityReturnsForAnotherOcrAttempt() {
        AcquisitionQueue queue = new AcquisitionQueue();
        VehicleCandidate reading = candidateWithState(
                1L,
                11L,
                EntityAcquisitionState.READING_REGISTRATION
        );

        queue.update(frame(1L, reading), 0L, 100L);

        assertNotNull(queue.snapshot(100L).find(1L));
        assertEquals(1L, queue.selectNext(100L).candidate.entityId);
    }

    @Test
    public void activeEntityIsNotQueuedOrSelected() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(1L, 11L),
                candidate(2L, 12L)
        ), 1L, 100L);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(100L);
        assertNull(snapshot.find(1L));
        assertEquals(2L, queue.selectNext(100L).candidate.entityId);
    }

    @Test
    public void cooldownBlocksRetryUntilDeadline() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L)), 0L, 100L);
        queue.selectNext(100L);
        queue.defer(1L, 100L, 50L);

        assertEquals(100L, queue.snapshot(100L).find(1L).firstQueuedRuntimeNanos);
        assertNull(queue.selectNext(149L));
        assertEquals(1L, queue.selectNext(150L).candidate.entityId);
    }

    @Test
    public void deferredReadableCandidateMovesBehindNeverAttemptedPeer() {
        AcquisitionQueue queue = new AcquisitionQueue();
        long saturated = ScanAcquisitionProfile.DEFAULT.waitingAgeSaturationNanos;
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.82f, 0f, false, 0L),
                candidate(2L, 12L, 0.80f, 0f, false, 0L)
        ), 0L, 100L);

        assertEquals(1L, queue.selectNext(saturated).candidate.entityId);
        queue.defer(1L, saturated, 0L);

        AcquisitionQueue.Selection next = queue.selectNext(saturated + 1L);
        assertNotNull(next);
        assertEquals(2L, next.candidate.entityId);
        assertEquals(saturated, queue.snapshot(saturated + 1L)
                .find(1L).firstQueuedRuntimeNanos);
    }

    @Test
    public void firstPassBarrierVisitsSmallVehicleBeforeReadableRetry() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.95f, 0f, false, 0L),
                candidate(2L, 12L, 0.70f, 0f, false, 0L),
                candidate(3L, 13L, 0.25f, 0f, false, 0L)
        ), 0L, 100L);

        long first = queue.selectNext(100L).candidate.entityId;
        assertEquals(1L, first);
        queue.recordMtAttempt(first, 100L);
        queue.defer(first, 100L, 0L);

        long second = queue.selectNext(101L).candidate.entityId;
        assertEquals(2L, second);
        queue.recordMtAttempt(second, 101L);
        queue.defer(second, 101L, 0L);

        assertEquals(3L, queue.selectNext(102L).candidate.entityId);
    }

    @Test
    public void repeatedRetriesStayBalancedAcrossAllQueuedEntities() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.95f, 0f, false, 0L),
                candidate(2L, 12L, 0.70f, 0f, false, 0L),
                candidate(3L, 13L, 0.55f, 0f, false, 0L)
        ), 0L, 100L);

        long[] order = new long[6];
        for (int index = 0; index < order.length; index++) {
            long now = 100L + index;
            AcquisitionQueue.Selection selection = queue.selectNext(now);
            order[index] = selection.candidate.entityId;
            queue.recordMtAttempt(order[index], now);
            queue.defer(order[index], now, 0L);
        }

        assertEquals(1L, order[0]);
        assertEquals(2L, order[1]);
        assertEquals(3L, order[2]);
        assertEquals(1L, order[3]);
        assertEquals(2L, order[4]);
        assertEquals(3L, order[5]);
    }

    @Test
    public void queueLimitKeepsHighestRankedCandidates() {
        AcquisitionQueue queue = new AcquisitionQueue(profileWithLimit(2));
        queue.update(frame(
                1L,
                candidate(1L, 11L, 0.40f, 0f, false, 0L),
                candidate(2L, 12L, 0.70f, 0f, false, 0L),
                candidate(3L, 13L, 0.95f, 0f, false, 0L)
        ), 0L, 100L);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(100L);
        assertEquals(2, snapshot.size());
        assertNull(snapshot.find(1L));
    }

    @Test
    public void equalPriorityUsesFirstQueuedThenEntityIdTieBreak() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(
                1L,
                candidate(9L, 19L),
                candidate(3L, 13L)
        ), 0L, 100L);

        assertEquals(3L, queue.selectNext(100L).candidate.entityId);
    }

    @Test
    public void sceneGenerationChangeDropsOldQueue() {
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(frame(1L, candidate(1L, 11L)), 0L, 100L);
        queue.update(frame(2L, candidate(2L, 12L)), 0L, 200L);

        AcquisitionQueueSnapshot snapshot = queue.snapshot(200L);
        assertEquals(2L, snapshot.sceneGeneration);
        assertNull(snapshot.find(1L));
        assertNotNull(snapshot.find(2L));
    }

    private static ScanAcquisitionProfile profileWithLimit(int limit) {
        ScanAcquisitionProfile base = ScanAcquisitionProfile.DEFAULT;
        return new ScanAcquisitionProfile(
                limit,
                base.minimumEffectiveConfidence,
                base.maximumPredictionAgeNanos,
                base.waitingAgeSaturationNanos,
                base.recentAttemptPenaltyNanos,
                base.defaultCooldownNanos,
                base.readabilityWeight,
                base.waitingAgeWeight,
                base.exitUrgencyWeight,
                base.centerWeight,
                base.freshnessWeight,
                base.noveltyWeight,
                base.predictionPenalty,
                base.recentAttemptPenalty,
                base.cooldownPenalty,
                base.maximumMtAttempts,
                base.maximumFreshMzAttempts,
                base.maximumActiveSessionNanos,
                base.noProgressTimeoutNanos
        );
    }

    private static VehicleTrackingFrame frame(
            long sceneGeneration,
            VehicleCandidate... candidates
    ) {
        return new VehicleTrackingFrame(
                1L, 10L, 10L, sceneGeneration,
                Arrays.asList(candidates)
        );
    }

    private static VehicleCandidate candidate(long entityId, long trackId) {
        return candidate(entityId, trackId, 0.8f, 0f, false, 0L);
    }

    private static VehicleCandidate candidate(
            long entityId,
            long trackId,
            float confidence,
            float exitUrgency,
            boolean predicted,
            long predictionAgeNanos
    ) {
        long snapshot = 1_000_000_000L;
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(0.25f, 0.20f, 0.75f, 0.80f),
                confidence,
                confidence,
                exitUrgency,
                predicted,
                predicted ? 1 : 0,
                snapshot - predictionAgeNanos,
                snapshot,
                0,
                EntityAcquisitionState.NEW
        );
    }

    private static VehicleCandidate candidateWithState(
            long entityId,
            long trackId,
            EntityAcquisitionState state
    ) {
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(0.25f, 0.20f, 0.75f, 0.80f),
                0.8f,
                0.8f,
                0f,
                false,
                0,
                1_000_000_000L,
                1_000_000_000L,
                0,
                state
        );
    }
}
