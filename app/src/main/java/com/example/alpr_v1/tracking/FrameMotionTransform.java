package com.example.alpr_v1.tracking;

/** Znormalizowana transformacja affine pomiędzy dwiema kolejnymi klatkami Y. */
public final class FrameMotionTransform {
    public final boolean valid;
    public final float a;
    public final float b;
    public final float tx;
    public final float c;
    public final float d;
    public final float ty;
    public final int inliers;
    public final float meanError;
    public final FrameMotionQuality quality;

    FrameMotionTransform(
            boolean valid,
            float a,
            float b,
            float tx,
            float c,
            float d,
            float ty,
            int inliers,
            float meanError
    ) {
        this(
                valid, a, b, tx, c, d, ty, inliers, meanError,
                valid
                        ? FrameMotionQuality.syntheticReliable()
                        : FrameMotionQuality.unavailable(0)
        );
    }

    FrameMotionTransform(
            boolean valid,
            float a,
            float b,
            float tx,
            float c,
            float d,
            float ty,
            int inliers,
            float meanError,
            FrameMotionQuality quality
    ) {
        this.valid = valid;
        this.a = a;
        this.b = b;
        this.tx = tx;
        this.c = c;
        this.d = d;
        this.ty = ty;
        this.inliers = Math.max(0, inliers);
        this.meanError = Float.isFinite(meanError)
                ? Math.max(0f, meanError) : Float.POSITIVE_INFINITY;
        this.quality = quality == null
                ? FrameMotionQuality.unavailable(0) : quality;
    }

    public float mapX(float normalizedX, float normalizedY) {
        return clamp01(a * normalizedX + b * normalizedY + tx);
    }

    public float mapY(float normalizedX, float normalizedY) {
        return clamp01(c * normalizedX + d * normalizedY + ty);
    }

    public boolean significant() {
        if (!valid) return false;
        float dx = mapX(0.5f, 0.5f) - 0.5f;
        float dy = mapY(0.5f, 0.5f) - 0.5f;
        float linearDelta = Math.abs(a - 1f) + Math.abs(b)
                + Math.abs(c) + Math.abs(d - 1f);
        return dx * dx + dy * dy >= 0.0015f * 0.0015f
                || linearDelta >= 0.003f;
    }

    public static FrameMotionTransform identity() {
        return new FrameMotionTransform(
                true, 1f, 0f, 0f, 0f, 1f, 0f,
                Integer.MAX_VALUE, 0f
        );
    }

    public static FrameMotionTransform translation(float dx, float dy) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return invalid();
        return new FrameMotionTransform(
                true, 1f, 0f, dx, 0f, 1f, dy,
                Integer.MAX_VALUE, 0f
        );
    }

    /**
     * Składa dwie transformacje w kolejności czasowej: najpierw {@code first},
     * następnie {@code second}.
     */
    public static FrameMotionTransform compose(
            FrameMotionTransform first,
            FrameMotionTransform second
    ) {
        if (first == null || second == null || !first.valid || !second.valid) {
            return invalid();
        }
        return new FrameMotionTransform(
                true,
                second.a * first.a + second.b * first.c,
                second.a * first.b + second.b * first.d,
                second.a * first.tx + second.b * first.ty + second.tx,
                second.c * first.a + second.d * first.c,
                second.c * first.b + second.d * first.d,
                second.c * first.tx + second.d * first.ty + second.ty,
                Math.min(first.inliers, second.inliers),
                Math.max(first.meanError, second.meanError),
                FrameMotionQuality.compose(first.quality, second.quality)
        );
    }

    public static FrameMotionTransform invalid() {
        return new FrameMotionTransform(
                false, 1f, 0f, 0f, 0f, 1f, 0f,
                0, Float.POSITIVE_INFINITY
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
