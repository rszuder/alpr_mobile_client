package com.example.alpr_v1.vision;

public final class Point2 {
    public final float x;
    public final float y;
    public final float confidence;

    public Point2(float x, float y) {
        this(x, y, 1.0f);
    }

    public Point2(float x, float y, float confidence) {
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }
}
