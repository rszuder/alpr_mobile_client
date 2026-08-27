package com.example.alpr_v1.domain;

import java.util.Objects;

/** Android-independent rectangle in normalized image coordinates. */
public final class NormalizedBounds {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public NormalizedBounds(float left, float top, float right, float bottom) {
        this.left = clamp(Math.min(left, right));
        this.top = clamp(Math.min(top, bottom));
        this.right = clamp(Math.max(left, right));
        this.bottom = clamp(Math.max(top, bottom));
    }

    public float width() { return Math.max(0f, right - left); }
    public float height() { return Math.max(0f, bottom - top); }
    public float area() { return width() * height(); }
    public float centerX() { return (left + right) * 0.5f; }
    public float centerY() { return (top + bottom) * 0.5f; }
    public boolean valid() { return width() > 0f && height() > 0f; }

    public float iou(NormalizedBounds other) {
        if (other == null) return 0f;
        float intersection = Math.max(0f, Math.min(right, other.right)
                - Math.max(left, other.left))
                * Math.max(0f, Math.min(bottom, other.bottom)
                - Math.max(top, other.top));
        float union = area() + other.area() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof NormalizedBounds)) return false;
        NormalizedBounds other = (NormalizedBounds) value;
        return Float.compare(left, other.left) == 0
                && Float.compare(top, other.top) == 0
                && Float.compare(right, other.right) == 0
                && Float.compare(bottom, other.bottom) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top, right, bottom);
    }
}
