package com.example.alpr_v1.tracking;

/** Jawna jakość i przestrzenne wsparcie estymacji globalnego ruchu klatki. */
public final class FrameMotionQuality {
    public final int totalSamples;
    public final int inliers;
    public final float inlierRatio;
    public final float spatialCoverage;
    public final int occupiedQuadrants;
    public final float meanResidual;
    public final float textureConfidence;
    public final float coherenceScore;

    public FrameMotionQuality(
            int totalSamples,
            int inliers,
            float spatialCoverage,
            int occupiedQuadrants,
            float meanResidual,
            float textureConfidence,
            float coherenceScore
    ) {
        this.totalSamples = Math.max(0, totalSamples);
        this.inliers = Math.max(0, inliers);
        this.inlierRatio = this.totalSamples == 0
                ? 0f : clamp01(this.inliers / (float) this.totalSamples);
        this.spatialCoverage = clamp01(spatialCoverage);
        this.occupiedQuadrants = Math.max(0, Math.min(4, occupiedQuadrants));
        this.meanResidual = Float.isFinite(meanResidual)
                ? Math.max(0f, meanResidual) : Float.POSITIVE_INFINITY;
        this.textureConfidence = clamp01(textureConfidence);
        this.coherenceScore = clamp01(coherenceScore);
    }

    public boolean reliableCameraMotion() {
        return inliers >= 8
                && inlierRatio >= 0.35f
                && occupiedQuadrants >= 3
                && spatialCoverage >= 0.45f
                && coherenceScore >= 0.55f;
    }

    public static FrameMotionQuality unavailable(int totalSamples) {
        return new FrameMotionQuality(
                totalSamples, 0, 0f, 0,
                Float.POSITIVE_INFINITY, 0f, 0f
        );
    }

    public static FrameMotionQuality syntheticReliable() {
        return new FrameMotionQuality(8, 8, 1f, 4, 0f, 1f, 1f);
    }

    public static FrameMotionQuality compose(
            FrameMotionQuality first,
            FrameMotionQuality second
    ) {
        if (first == null) return second == null ? unavailable(0) : second;
        if (second == null) return first;
        return new FrameMotionQuality(
                Math.min(first.totalSamples, second.totalSamples),
                Math.min(first.inliers, second.inliers),
                Math.min(first.spatialCoverage, second.spatialCoverage),
                Math.min(first.occupiedQuadrants, second.occupiedQuadrants),
                Math.max(first.meanResidual, second.meanResidual),
                Math.min(first.textureConfidence, second.textureConfidence),
                Math.min(first.coherenceScore, second.coherenceScore)
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
