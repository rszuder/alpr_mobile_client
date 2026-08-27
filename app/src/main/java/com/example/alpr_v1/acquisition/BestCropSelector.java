package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.CropReference;

/** Scores crop metadata; bitmap ownership remains in the existing capture layer. */
public final class BestCropSelector {
    public static final class Quality {
        public final float sharpness;
        public final float platePixelArea;
        public final float perspective;
        public final float exposure;
        public final float motionStability;
        public final float mtConfidence;
        public final float mzConfidence;
        public final boolean zoomSource;

        public Quality(
                float sharpness,
                float platePixelArea,
                float perspective,
                float exposure,
                float motionStability,
                float mtConfidence,
                float mzConfidence,
                boolean zoomSource
        ) {
            this.sharpness = clamp01(sharpness);
            this.platePixelArea = clamp01(platePixelArea);
            this.perspective = clamp01(perspective);
            this.exposure = clamp01(exposure);
            this.motionStability = clamp01(motionStability);
            this.mtConfidence = clamp01(mtConfidence);
            this.mzConfidence = clamp01(mzConfidence);
            this.zoomSource = zoomSource;
        }
    }

    public float score(Quality quality) {
        if (quality == null) return 0f;
        float score = 0.22f * quality.sharpness
                + 0.14f * quality.platePixelArea
                + 0.13f * quality.perspective
                + 0.09f * quality.exposure
                + 0.10f * quality.motionStability
                + 0.14f * quality.mtConfidence
                + 0.18f * quality.mzConfidence;
        // Zoom is not quality by itself; only a small tie-breaker after useful detail exists.
        if (quality.zoomSource && quality.platePixelArea >= 0.45f) score += 0.02f;
        return clamp01(score);
    }

    public CropReference candidate(
            String referenceId,
            CropReference.Kind kind,
            Quality quality,
            long capturedAtNanos
    ) {
        return new CropReference(referenceId, kind, score(quality), capturedAtNanos);
    }

    public CropReference choose(CropReference current, CropReference candidate) {
        return candidate != null && candidate.betterThan(current) ? candidate : current;
    }

    public boolean veryGood(Quality quality) { return score(quality) >= 0.86f; }

    public boolean sufficientMixed(Quality quality) {
        return score(quality) >= 0.68f
                && quality != null
                && quality.mzConfidence >= 0.72f
                && quality.mtConfidence >= 0.55f;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
