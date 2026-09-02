package com.example.alpr_v1.acquisition;

/** Spokojny, użytkowy opis najlepszego odczytu przypisanego do encji pojazdu. */
public final class EntityRecognitionSnapshot {
    public final long entityId;
    public final long plateTrackId;
    public final String text;
    public final double confidence;
    public final boolean confirmed;
    public final int observations;

    public EntityRecognitionSnapshot(
            long entityId,
            long plateTrackId,
            String text,
            double confidence,
            boolean confirmed,
            int observations
    ) {
        this.entityId = Math.max(0L, entityId);
        this.plateTrackId = Math.max(0L, plateTrackId);
        this.text = text == null ? "" : text.trim();
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.confirmed = confirmed;
        this.observations = Math.max(0, observations);
    }
}
