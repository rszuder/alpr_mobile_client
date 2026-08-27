package com.example.alpr_v1.tracking;

/** Immutable event drained synchronously into MetricsCollector.events.jsonl. */
public final class VehicleTrackingEvent {
    public final String eventType;
    public final long entityId;
    public final long vehicleTrackId;
    public final long plateTrackId;
    public final long frameId;
    public final long elapsedNanos;
    public final long sceneGeneration;
    public final String reason;

    VehicleTrackingEvent(
            String eventType,
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            long frameId,
            long elapsedNanos,
            long sceneGeneration,
            String reason
    ) {
        this.eventType = eventType == null ? "unknown" : eventType;
        this.entityId = Math.max(0L, entityId);
        this.vehicleTrackId = Math.max(0L, vehicleTrackId);
        this.plateTrackId = Math.max(0L, plateTrackId);
        this.frameId = Math.max(0L, frameId);
        this.elapsedNanos = Math.max(0L, elapsedNanos);
        this.sceneGeneration = Math.max(0L, sceneGeneration);
        this.reason = reason == null ? "" : reason;
    }
}
