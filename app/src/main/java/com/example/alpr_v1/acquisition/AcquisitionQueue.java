package com.example.alpr_v1.acquisition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, aging priority queue of VehicleEntity ids. */
public final class AcquisitionQueue {
    public static final int DEFAULT_MAX_CANDIDATES = 8;
    public static final long DEFAULT_TTL_NANOS = 1_800_000_000L;
    private static final long WAITING_BONUS_HORIZON_NANOS = 4_000_000_000L;

    public static final class Offer {
        public final long entityId;
        public final float noveltyScore;
        public final float expectedReadability;
        public final float exitUrgency;
        public final float searchPriority;
        public final float recentAttemptPenalty;
        public final float duplicatePenalty;
        public final float estimatedZoomCost;

        public Offer(
                long entityId,
                float noveltyScore,
                float expectedReadability,
                float exitUrgency,
                float searchPriority,
                float recentAttemptPenalty,
                float duplicatePenalty,
                float estimatedZoomCost
        ) {
            this.entityId = entityId;
            this.noveltyScore = clamp01(noveltyScore);
            this.expectedReadability = clamp01(expectedReadability);
            this.exitUrgency = clamp01(exitUrgency);
            this.searchPriority = clamp01(searchPriority);
            this.recentAttemptPenalty = clamp01(recentAttemptPenalty);
            this.duplicatePenalty = clamp01(duplicatePenalty);
            this.estimatedZoomCost = clamp01(estimatedZoomCost);
        }
    }

    private final int maximumCandidates;
    private final long ttlNanos;
    private final Map<Long, AcquisitionCandidate> candidates = new LinkedHashMap<>();

    public AcquisitionQueue() {
        this(DEFAULT_MAX_CANDIDATES, DEFAULT_TTL_NANOS);
    }

    public AcquisitionQueue(int maximumCandidates, long ttlNanos) {
        if (maximumCandidates <= 0) throw new IllegalArgumentException("maximumCandidates");
        this.maximumCandidates = maximumCandidates;
        this.ttlNanos = Math.max(1L, ttlNanos);
    }

    public synchronized void offer(Offer offer, long nowNanos) {
        if (offer == null || offer.entityId <= 0L) return;
        long safeNow = Math.max(0L, nowNanos);
        expire(safeNow);
        AcquisitionCandidate candidate = candidates.get(offer.entityId);
        if (candidate == null) {
            candidate = new AcquisitionCandidate(offer.entityId, safeNow);
            candidates.put(offer.entityId, candidate);
        }
        candidate.lastQueuedNanos = Math.max(candidate.lastQueuedNanos, safeNow);
        candidate.noveltyScore = offer.noveltyScore;
        candidate.expectedReadability = offer.expectedReadability;
        candidate.exitUrgency = offer.exitUrgency;
        candidate.searchPriority = offer.searchPriority;
        candidate.recentAttemptPenalty = offer.recentAttemptPenalty;
        candidate.duplicatePenalty = offer.duplicatePenalty;
        candidate.estimatedZoomCost = offer.estimatedZoomCost;
        recompute(candidate, safeNow);
        trim(safeNow);
    }

    public synchronized AcquisitionCandidate poll(long nowNanos) {
        AcquisitionCandidate best = best(nowNanos);
        if (best == null) return null;
        candidates.remove(best.entityId);
        return best.copy();
    }

    public synchronized AcquisitionCandidate peek(long nowNanos) {
        AcquisitionCandidate best = best(nowNanos);
        return best == null ? null : best.copy();
    }

    public synchronized void restore(AcquisitionCandidate candidate, long nowNanos) {
        if (candidate == null) return;
        AcquisitionCandidate restored = candidate.copy();
        restored.lastQueuedNanos = Math.max(restored.lastQueuedNanos, nowNanos);
        restored.recentAttemptPenalty = Math.min(1f, 0.20f * restored.mtAttempts);
        candidates.put(restored.entityId, restored);
        trim(Math.max(0L, nowNanos));
    }

    public synchronized void remove(long entityId) { candidates.remove(entityId); }
    public synchronized void clear() { candidates.clear(); }

    public synchronized int expire(long nowNanos) {
        int before = candidates.size();
        candidates.values().removeIf(
                candidate -> nowNanos - candidate.lastQueuedNanos > ttlNanos
        );
        return before - candidates.size();
    }

    public synchronized List<AcquisitionCandidate> snapshot(long nowNanos) {
        expire(nowNanos);
        List<AcquisitionCandidate> result = new ArrayList<>();
        for (AcquisitionCandidate candidate : candidates.values()) {
            recompute(candidate, nowNanos);
            result.add(candidate.copy());
        }
        result.sort(order());
        return java.util.Collections.unmodifiableList(result);
    }

    public synchronized int size() { return candidates.size(); }

    private AcquisitionCandidate best(long nowNanos) {
        expire(nowNanos);
        AcquisitionCandidate best = null;
        for (AcquisitionCandidate candidate : candidates.values()) {
            recompute(candidate, nowNanos);
            if (best == null || order().compare(candidate, best) < 0) best = candidate;
        }
        return best;
    }

    private void trim(long nowNanos) {
        while (candidates.size() > maximumCandidates) {
            AcquisitionCandidate worst = null;
            for (AcquisitionCandidate candidate : candidates.values()) {
                recompute(candidate, nowNanos);
                if (worst == null || order().compare(candidate, worst) > 0) worst = candidate;
            }
            if (worst == null) break;
            candidates.remove(worst.entityId);
        }
    }

    private static void recompute(AcquisitionCandidate candidate, long nowNanos) {
        float waitingAgeBonus = 0.35f * clamp01(
                (nowNanos - candidate.firstQueuedNanos)
                        / (float) WAITING_BONUS_HORIZON_NANOS
        );
        candidate.priority = candidate.noveltyScore
                + candidate.expectedReadability
                + waitingAgeBonus
                + candidate.exitUrgency
                + candidate.searchPriority
                - candidate.recentAttemptPenalty
                - candidate.duplicatePenalty
                - candidate.estimatedZoomCost;
    }

    private static Comparator<AcquisitionCandidate> order() {
        return Comparator.comparingDouble((AcquisitionCandidate item) -> item.priority)
                .reversed()
                .thenComparingLong(item -> item.firstQueuedNanos)
                .thenComparingLong(item -> item.entityId);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
