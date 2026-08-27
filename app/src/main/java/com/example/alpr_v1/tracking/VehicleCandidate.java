package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.NormalizedBounds;

/** Immutable entity-aware vehicle snapshot passed between runtime layers. */
public final class VehicleCandidate {
    public final long entityId;
    public final long vehicleTrackId;
    public final NormalizedBounds bounds;
    public final float detectionConfidence;
    public final float effectiveConfidence;
    public final float exitUrgency;
    public final boolean predicted;
    public final int missedUpdates;
    public final long lastMeasurementTimestampNanos;
    public final long snapshotTimestampNanos;
    public final long predictionAgeNanos;
    public final int sourceIndex;

    public VehicleCandidate(
            long entityId,
            long vehicleTrackId,
            NormalizedBounds bounds,
            float detectionConfidence,
            float effectiveConfidence,
            float exitUrgency,
            boolean predicted,
            int missedUpdates,
            long lastMeasurementTimestampNanos,
            long snapshotTimestampNanos
    ) {
        this(
                entityId, vehicleTrackId, bounds, detectionConfidence,
                effectiveConfidence, exitUrgency, predicted, missedUpdates,
                lastMeasurementTimestampNanos, snapshotTimestampNanos, -1
        );
    }

    public VehicleCandidate(
            long entityId,
            long vehicleTrackId,
            NormalizedBounds bounds,
            float detectionConfidence,
            float effectiveConfidence,
            float exitUrgency,
            boolean predicted,
            int missedUpdates,
            long lastMeasurementTimestampNanos,
            long snapshotTimestampNanos,
            int sourceIndex
    ) {
        if (entityId <= 0L) throw new IllegalArgumentException("entityId must be positive");
        if (vehicleTrackId <= 0L) {
            throw new IllegalArgumentException("vehicleTrackId must be positive");
        }
        if (bounds == null || !bounds.valid()) {
            throw new IllegalArgumentException("valid bounds are required");
        }
        this.entityId = entityId;
        this.vehicleTrackId = vehicleTrackId;
        this.bounds = bounds;
        this.detectionConfidence = clamp01(detectionConfidence);
        this.effectiveConfidence = clamp01(effectiveConfidence);
        this.exitUrgency = clamp01(exitUrgency);
        this.predicted = predicted;
        this.missedUpdates = Math.max(0, missedUpdates);
        this.lastMeasurementTimestampNanos = Math.max(0L, lastMeasurementTimestampNanos);
        this.snapshotTimestampNanos = Math.max(
                this.lastMeasurementTimestampNanos,
                snapshotTimestampNanos
        );
        this.predictionAgeNanos = Math.max(
                0L,
                this.snapshotTimestampNanos - this.lastMeasurementTimestampNanos
        );
        this.sourceIndex = sourceIndex;
    }

    public double predictionAgeMillis() {
        return predictionAgeNanos / 1_000_000.0;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
