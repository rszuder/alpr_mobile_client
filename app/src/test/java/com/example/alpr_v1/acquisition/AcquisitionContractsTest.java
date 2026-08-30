package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.NormalizedBounds;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcquisitionContractsTest {
    @Test
    public void candidateIdentityDoesNotDependOnVehicleTrackId() {
        AcquisitionCandidate original = candidate(7L, 11L);
        AcquisitionCandidate rebound = original.withDynamicScores(
                0.5f, 1f, 99L, original.bounds,
                EntityAcquisitionState.QUEUED,
                0.8f, 0.2f, 0.7f, 0.6f,
                false, 0L
        );

        assertEquals(7L, rebound.entityId);
        assertEquals(99L, rebound.vehicleTrackId);
        assertEquals(original.firstQueuedRuntimeNanos,
                rebound.firstQueuedRuntimeNanos);
    }

    @Test
    public void queueSnapshotDefensivelyCopiesCollections() {
        List<AcquisitionCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(1L, 11L));
        Map<Long, AcquisitionPriorityBreakdown> priorities = new LinkedHashMap<>();
        priorities.put(1L, priority(0.5f));

        AcquisitionQueueSnapshot snapshot = new AcquisitionQueueSnapshot(
                2L, 3L, 0L, candidates, priorities
        );
        candidates.clear();
        priorities.clear();

        assertEquals(1, snapshot.size());
        assertEquals(1, snapshot.prioritiesByEntityId.size());
        try {
            snapshot.candidates.add(candidate(2L, 12L));
            fail("snapshot list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void activeTimeBudgetExcludesPausedWallTime() {
        ActiveTimeBudget budget = new ActiveTimeBudget(100L);
        budget.start(10L);
        budget.pause(40L);

        assertEquals(30L, budget.elapsedActiveNanos(1_000L));
        assertFalse(budget.exhausted(1_000L));

        budget.resume(2_000L);
        assertEquals(80L, budget.elapsedActiveNanos(2_050L));
        assertTrue(budget.exhausted(2_070L));
    }

    @Test
    public void onlyEntityTargetedDirectivesRequireIdentity() {
        AcquisitionDirective none = AcquisitionDirective.none(1L, 2L);
        AcquisitionDirective mt = new AcquisitionDirective(
                2L,
                AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT,
                2L, 3L, 4L,
                "next_candidate"
        );

        assertFalse(none.requestsMt());
        assertTrue(mt.requestsMt());
        assertFalse(mt.expandedRoi());
    }

    private static AcquisitionCandidate candidate(long entityId, long trackId) {
        return new AcquisitionCandidate(
                entityId,
                trackId,
                new NormalizedBounds(0.1f, 0.2f, 0.7f, 0.9f),
                EntityAcquisitionState.NEW,
                0.9f, 0.1f, 0.8f, 0f, 1f, 1f,
                false, 0L, 0, 0,
                100L, 0L, 0L
        );
    }

    private static AcquisitionPriorityBreakdown priority(float total) {
        return new AcquisitionPriorityBreakdown(
                0.2f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f,
                0f, 0f, 0f, total
        );
    }
}
