package com.example.alpr_v1.acquisition;

/** Mutable queue record owned exclusively by AcquisitionQueue. */
public final class AcquisitionCandidate {
    public final long entityId;
    public final long firstQueuedNanos;
    public long lastQueuedNanos;
    public float priority;
    public int mtAttempts;
    public int mzAttempts;
    public boolean plateLocalized;
    public boolean registrationStable;
    public boolean zoomAttempted;

    float noveltyScore;
    float expectedReadability;
    float exitUrgency;
    float searchPriority;
    float recentAttemptPenalty;
    float duplicatePenalty;
    float estimatedZoomCost;

    AcquisitionCandidate(long entityId, long nowNanos) {
        if (entityId <= 0L) throw new IllegalArgumentException("entityId must be positive");
        this.entityId = entityId;
        this.firstQueuedNanos = Math.max(0L, nowNanos);
        this.lastQueuedNanos = this.firstQueuedNanos;
    }

    AcquisitionCandidate copy() {
        AcquisitionCandidate copy = new AcquisitionCandidate(entityId, firstQueuedNanos);
        copy.lastQueuedNanos = lastQueuedNanos;
        copy.priority = priority;
        copy.mtAttempts = mtAttempts;
        copy.mzAttempts = mzAttempts;
        copy.plateLocalized = plateLocalized;
        copy.registrationStable = registrationStable;
        copy.zoomAttempted = zoomAttempted;
        copy.noveltyScore = noveltyScore;
        copy.expectedReadability = expectedReadability;
        copy.exitUrgency = exitUrgency;
        copy.searchPriority = searchPriority;
        copy.recentAttemptPenalty = recentAttemptPenalty;
        copy.duplicatePenalty = duplicatePenalty;
        copy.estimatedZoomCost = estimatedZoomCost;
        return copy;
    }
}
