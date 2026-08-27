package com.example.alpr_v1.domain;

/** Lightweight completed-acquisition record retained outside the active scene. */
public final class VehicleEntitySummary {
    public final long entityId;
    public final long lastVehicleTrackId;
    public final Long lastPlateTrackId;
    public final String registration;
    public final float registrationConfidence;
    public final long firstSeenNanos;
    public final long lastSeenNanos;
    public final long completedAtNanos;

    VehicleEntitySummary(VehicleEntity entity, long completedAtNanos) {
        this.entityId = entity.entityId();
        this.lastVehicleTrackId = entity.vehicleTrackId();
        this.lastPlateTrackId = entity.plateTrackId();
        this.registration = entity.registration().text;
        this.registrationConfidence = entity.registration().confidence;
        this.firstSeenNanos = entity.firstSeenNanos();
        this.lastSeenNanos = entity.lastSeenNanos();
        this.completedAtNanos = Math.max(entity.lastSeenNanos(), completedAtNanos);
    }
}
