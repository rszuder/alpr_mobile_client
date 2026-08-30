package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.NormalizedBounds;

/** Immutable entity-keyed input and state retained by the Scan queue. */
public final class AcquisitionCandidate {
    public final long entityId;
    public final long vehicleTrackId;
    public final NormalizedBounds bounds;
    public final EntityAcquisitionState state;
    public final float effectiveConfidence;
    public final float exitUrgency;
    public final float readabilityScore;
    public final float waitingAgeScore;
    public final float freshnessScore;
    public final float noveltyScore;
    public final boolean predicted;
    public final long predictionAgeNanos;
    public final int mtAttempts;
    public final int freshMzAttempts;
    public final long firstQueuedRuntimeNanos;
    public final long lastAttemptRuntimeNanos;
    public final long cooldownUntilRuntimeNanos;

    public AcquisitionCandidate(
            long entityId,
            long vehicleTrackId,
            NormalizedBounds bounds,
            EntityAcquisitionState state,
            float effectiveConfidence,
            float exitUrgency,
            float readabilityScore,
            float waitingAgeScore,
            float freshnessScore,
            float noveltyScore,
            boolean predicted,
            long predictionAgeNanos,
            int mtAttempts,
            int freshMzAttempts,
            long firstQueuedRuntimeNanos,
            long lastAttemptRuntimeNanos,
            long cooldownUntilRuntimeNanos
    ) {
        if (entityId <= 0L) throw new IllegalArgumentException("entityId");
        if (vehicleTrackId <= 0L) throw new IllegalArgumentException("vehicleTrackId");
        if (bounds == null || !bounds.valid()) throw new IllegalArgumentException("bounds");
        this.entityId = entityId;
        this.vehicleTrackId = vehicleTrackId;
        this.bounds = bounds;
        this.state = state == null ? EntityAcquisitionState.NEW : state;
        this.effectiveConfidence = clamp01(effectiveConfidence);
        this.exitUrgency = clamp01(exitUrgency);
        this.readabilityScore = clamp01(readabilityScore);
        this.waitingAgeScore = clamp01(waitingAgeScore);
        this.freshnessScore = clamp01(freshnessScore);
        this.noveltyScore = clamp01(noveltyScore);
        this.predicted = predicted;
        this.predictionAgeNanos = Math.max(0L, predictionAgeNanos);
        this.mtAttempts = Math.max(0, mtAttempts);
        this.freshMzAttempts = Math.max(0, freshMzAttempts);
        this.firstQueuedRuntimeNanos = Math.max(0L, firstQueuedRuntimeNanos);
        this.lastAttemptRuntimeNanos = Math.max(0L, lastAttemptRuntimeNanos);
        this.cooldownUntilRuntimeNanos = Math.max(0L, cooldownUntilRuntimeNanos);
    }

    public AcquisitionCandidate withDynamicScores(
            float waitingAge,
            float freshness,
            long vehicleTrackId,
            NormalizedBounds bounds,
            EntityAcquisitionState state,
            float effectiveConfidence,
            float exitUrgency,
            float readability,
            float novelty,
            boolean predicted,
            long predictionAgeNanos
    ) {
        return new AcquisitionCandidate(
                entityId,
                vehicleTrackId,
                bounds,
                state,
                effectiveConfidence,
                exitUrgency,
                readability,
                waitingAge,
                freshness,
                novelty,
                predicted,
                predictionAgeNanos,
                mtAttempts,
                freshMzAttempts,
                firstQueuedRuntimeNanos,
                lastAttemptRuntimeNanos,
                cooldownUntilRuntimeNanos
        );
    }

    public AcquisitionCandidate withAttemptState(
            EntityAcquisitionState state,
            int mtAttempts,
            int freshMzAttempts,
            long lastAttemptRuntimeNanos,
            long cooldownUntilRuntimeNanos
    ) {
        return new AcquisitionCandidate(
                entityId, vehicleTrackId, bounds, state,
                effectiveConfidence, exitUrgency, readabilityScore,
                waitingAgeScore, freshnessScore, noveltyScore,
                predicted, predictionAgeNanos, mtAttempts, freshMzAttempts,
                firstQueuedRuntimeNanos, lastAttemptRuntimeNanos,
                cooldownUntilRuntimeNanos
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
