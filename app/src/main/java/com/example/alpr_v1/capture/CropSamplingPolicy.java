package com.example.alpr_v1.capture;

/** Ogranicza kolejne niemal identyczne cropy tego samego tracku. */
public final class CropSamplingPolicy {
    private static final long PERIODIC_CAPTURE_NANOS = 1_500_000_000L;
    private static final float SHARPNESS_IMPROVEMENT = 0.08f;
    private static final double RECOGNITION_CONFIDENCE_IMPROVEMENT = 0.10;
    private static final double CONFIDENCE_COMPARISON_EPSILON = 1e-9;

    public static final class Previous {
        public final String text;
        public final boolean confirmed;
        public final double recognitionConfidence;
        public final float sharpness;
        public final long capturedElapsedNanos;

        public Previous(
                String text,
                boolean confirmed,
                double recognitionConfidence,
                float sharpness,
                long capturedElapsedNanos
        ) {
            this.text = text == null ? "" : text;
            this.confirmed = confirmed;
            this.recognitionConfidence = normalizeConfidence(
                    recognitionConfidence
            );
            this.sharpness = sharpness;
            this.capturedElapsedNanos = capturedElapsedNanos;
        }
    }

    private CropSamplingPolicy() {}

    public static boolean shouldCapture(
            Previous previous,
            String text,
            boolean confirmed,
            double recognitionConfidence,
            float sharpness,
            long capturedElapsedNanos
    ) {
        if (previous == null) return true;
        String normalized = text == null ? "" : text;
        if (!normalized.isEmpty() && !normalized.equalsIgnoreCase(previous.text)) return true;
        if (confirmed && !previous.confirmed) return true;
        double confidenceImprovement =
                normalizeConfidence(recognitionConfidence)
                        - previous.recognitionConfidence;
        if (confidenceImprovement
                + CONFIDENCE_COMPARISON_EPSILON
                >= RECOGNITION_CONFIDENCE_IMPROVEMENT) return true;
        if (sharpness >= previous.sharpness + SHARPNESS_IMPROVEMENT) return true;
        return capturedElapsedNanos - previous.capturedElapsedNanos >= PERIODIC_CAPTURE_NANOS;
    }

    private static double normalizeConfidence(double confidence) {
        if (Double.isNaN(confidence)) return 0.0;
        return Math.max(0.0, Math.min(1.0, confidence));
    }
}
