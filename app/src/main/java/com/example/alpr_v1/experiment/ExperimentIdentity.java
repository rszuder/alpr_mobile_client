package com.example.alpr_v1.experiment;

/** Zamrażana przed startem tożsamość przebiegu w kampanii pomiarowej. */
public final class ExperimentIdentity {
    public final String seriesId;
    public final String scenarioId;
    public final int replicateIndex;
    public final String notes;
    public final boolean autoZoomEnabled;
    public final double maxZoomRatio;

    public ExperimentIdentity(
            String seriesId,
            String scenarioId,
            int replicateIndex,
            String notes
    ) {
        this(seriesId, scenarioId, replicateIndex, notes, false, 1.0);
    }

    public ExperimentIdentity(
            String seriesId,
            String scenarioId,
            int replicateIndex,
            String notes,
            boolean autoZoomEnabled,
            double maxZoomRatio
    ) {
        this.seriesId = normalize(seriesId, "unassigned_series");
        this.scenarioId = normalize(scenarioId, "live_camera");
        this.replicateIndex = Math.max(1, replicateIndex);
        this.notes = notes == null ? "" : notes.trim();
        this.autoZoomEnabled = autoZoomEnabled;
        this.maxZoomRatio = Math.max(1.0, maxZoomRatio);
    }

    public static ExperimentIdentity defaults() {
        return new ExperimentIdentity("unassigned_series", "live_camera", 1, "");
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }
}
