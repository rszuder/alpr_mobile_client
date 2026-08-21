package com.example.alpr_v1.pipeline;

/** Znak wykryty przez MZ wraz z położeniem znormalizowanym w cropie tablicy. */
public final class PlateCharacter {
    public final String label;
    public final double confidence;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public PlateCharacter(
            String label,
            double confidence,
            float left,
            float top,
            float right,
            float bottom
    ) {
        this.label = label == null ? "" : label;
        this.confidence = confidence;
        this.left = clamp(left);
        this.top = clamp(top);
        this.right = clamp(right);
        this.bottom = clamp(bottom);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
