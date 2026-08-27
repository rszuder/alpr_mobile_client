package com.example.alpr_v1.domain;

public final class MotionState {
    public static final MotionState STATIONARY = new MotionState(0f, 0f, 0f);

    public final float velocityX;
    public final float velocityY;
    public final float confidence;

    public MotionState(float velocityX, float velocityY, float confidence) {
        this.velocityX = finite(velocityX);
        this.velocityY = finite(velocityY);
        this.confidence = Math.max(0f, Math.min(1f, finite(confidence)));
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }
}
