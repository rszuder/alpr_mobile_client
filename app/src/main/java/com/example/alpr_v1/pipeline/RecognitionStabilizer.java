package com.example.alpr_v1.pipeline;

public final class RecognitionStabilizer {
    public static final class StableResult {
        public final String text;
        public final double confidence;
        public final int confirmations;

        StableResult(String text, double confidence, int confirmations) {
            this.text = text;
            this.confidence = confidence;
            this.confirmations = confirmations;
        }
    }

    private final int requiredConfirmations;
    private String candidate = "";
    private int confirmations;
    private double confidenceSum;

    public RecognitionStabilizer(int requiredConfirmations) {
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    public synchronized StableResult accept(String text, double confidence) {
        String normalized = text == null ? "" : text.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (!normalized.equals(candidate)) {
            candidate = normalized;
            confirmations = 1;
            confidenceSum = confidence;
        } else {
            confirmations++;
            confidenceSum += confidence;
        }
        if (confirmations < requiredConfirmations) return null;
        return new StableResult(candidate, confidenceSum / confirmations, confirmations);
    }

    public synchronized void reset() {
        candidate = "";
        confirmations = 0;
        confidenceSum = 0.0;
    }
}
