package com.example.alpr_v1.tracking;

/** Cumulative bounded-runtime counters for research telemetry. */
public final class VehicleTrackingStats {
    public final long tracksCreated;
    public final long tracksExpired;
    public final long entitiesCreated;
    public final long entitiesExpired;
    public final long entityReassociations;
    public final long entityDuplicatePreventions;
    public final long observationsUnmatched;
    public final long candidatesDroppedCapacity;
    public final long trackingNanos;
    public final long lastTrackingNanos;

    VehicleTrackingStats(
            long tracksCreated,
            long tracksExpired,
            long entitiesCreated,
            long entitiesExpired,
            long entityReassociations,
            long entityDuplicatePreventions,
            long observationsUnmatched,
            long candidatesDroppedCapacity,
            long trackingNanos,
            long lastTrackingNanos
    ) {
        this.tracksCreated = tracksCreated;
        this.tracksExpired = tracksExpired;
        this.entitiesCreated = entitiesCreated;
        this.entitiesExpired = entitiesExpired;
        this.entityReassociations = entityReassociations;
        this.entityDuplicatePreventions = entityDuplicatePreventions;
        this.observationsUnmatched = observationsUnmatched;
        this.candidatesDroppedCapacity = candidatesDroppedCapacity;
        this.trackingNanos = trackingNanos;
        this.lastTrackingNanos = lastTrackingNanos;
    }
}
