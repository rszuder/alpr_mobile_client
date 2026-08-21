package com.example.alpr_v1.capture;

/** Ogranicza kolejne niemal identyczne cropy tego samego tracku. */
public final class CropSamplingPolicy {
    private static final long PERIODIC_CAPTURE_NANOS = 1_500_000_000L;
    private static final float SHARPNESS_IMPROVEMENT = 0.08f;

    public static final class Previous {
        public final String text;
        public final boolean confirmed;
        public final float sharpness;
        public final long capturedElapsedNanos;

        public Previous(String text, boolean confirmed, float sharpness, long capturedElapsedNanos) {
            this.text = text == null ? "" : text;
            this.confirmed = confirmed;
            this.sharpness = sharpness;
            this.capturedElapsedNanos = capturedElapsedNanos;
        }
    }

    private CropSamplingPolicy() {}

    public static boolean shouldCapture(
            Previous previous,
            String text,
            boolean confirmed,
            float sharpness,
            long capturedElapsedNanos
    ) {
        if (previous == null) return true;
        String normalized = text == null ? "" : text;
        if (!normalized.isEmpty() && !normalized.equalsIgnoreCase(previous.text)) return true;
        if (confirmed && !previous.confirmed) return true;
        if (sharpness >= previous.sharpness + SHARPNESS_IMPROVEMENT) return true;
        return capturedElapsedNanos - previous.capturedElapsedNanos >= PERIODIC_CAPTURE_NANOS;
    }
}
