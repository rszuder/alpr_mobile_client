package com.example.alpr_v1.acquisition;

/** Niezmienny wynik domknięcia jednej krótkiej sesji Scan. */
public final class AcquisitionRecord {
    public final String recordId;
    public final long scanRunId;
    public final long sessionId;
    public final long entityId;
    public final long plateTrackId;
    public final String text;
    public final String normalizedText;
    public final double confidence;
    public final int consensusObservations;
    public final long firstObservationRuntimeNanos;
    public final long finalizedRuntimeNanos;
    public final long acquisitionDurationNanos;
    public final String bestCropId;
    public final boolean uniqueSaved;
    public final String duplicateOfRecordId;

    AcquisitionRecord(
            String recordId,
            long scanRunId,
            long sessionId,
            long entityId,
            long plateTrackId,
            String text,
            String normalizedText,
            double confidence,
            int consensusObservations,
            long firstObservationRuntimeNanos,
            long finalizedRuntimeNanos,
            long acquisitionDurationNanos,
            String bestCropId,
            boolean uniqueSaved,
            String duplicateOfRecordId
    ) {
        this.recordId = safe(recordId);
        this.scanRunId = Math.max(0L, scanRunId);
        this.sessionId = Math.max(0L, sessionId);
        this.entityId = Math.max(0L, entityId);
        this.plateTrackId = Math.max(0L, plateTrackId);
        this.text = safe(text);
        this.normalizedText = safe(normalizedText);
        this.confidence = finite01(confidence);
        this.consensusObservations = Math.max(0, consensusObservations);
        this.firstObservationRuntimeNanos = Math.max(0L, firstObservationRuntimeNanos);
        this.finalizedRuntimeNanos = Math.max(0L, finalizedRuntimeNanos);
        this.acquisitionDurationNanos = Math.max(0L, acquisitionDurationNanos);
        this.bestCropId = safe(bestCropId);
        this.uniqueSaved = uniqueSaved;
        this.duplicateOfRecordId = safe(duplicateOfRecordId);
    }

    public boolean duplicateSuppressed() {
        return !uniqueSaved;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double finite01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
