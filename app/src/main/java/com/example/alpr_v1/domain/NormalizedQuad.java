package com.example.alpr_v1.domain;

/** Four ordered normalized corner points: x0,y0,...,x3,y3. */
public final class NormalizedQuad {
    private final float[] points;

    public NormalizedQuad(float[] points) {
        if (points == null || points.length != 8) {
            throw new IllegalArgumentException("NormalizedQuad requires 8 values");
        }
        this.points = points.clone();
        for (int index = 0; index < this.points.length; index++) {
            float value = this.points[index];
            this.points[index] = Float.isFinite(value)
                    ? Math.max(0f, Math.min(1f, value)) : 0f;
        }
    }

    public float[] points() {
        return points.clone();
    }

    public NormalizedBounds bounds() {
        float left = 1f;
        float top = 1f;
        float right = 0f;
        float bottom = 0f;
        for (int index = 0; index < points.length; index += 2) {
            left = Math.min(left, points[index]);
            right = Math.max(right, points[index]);
            top = Math.min(top, points[index + 1]);
            bottom = Math.max(bottom, points[index + 1]);
        }
        return new NormalizedBounds(left, top, right, bottom);
    }
}
