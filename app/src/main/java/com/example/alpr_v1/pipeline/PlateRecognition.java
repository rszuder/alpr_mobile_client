package com.example.alpr_v1.pipeline;

public final class PlateRecognition {
    public final String text;
    public final double confidence;
    public final boolean confirmed;
    public final int observations;

    public PlateRecognition(String text, double confidence) {
        this(text, confidence, true, 1);
    }

    public PlateRecognition(
            String text,
            double confidence,
            boolean confirmed,
            int observations
    ) {
        this.text = text == null ? "" : text.trim();
        this.confidence = confidence;
        this.confirmed = confirmed;
        this.observations = Math.max(1, observations);
    }
}
