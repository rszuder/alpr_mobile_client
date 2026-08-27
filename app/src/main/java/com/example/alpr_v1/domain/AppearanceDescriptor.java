package com.example.alpr_v1.domain;

public final class AppearanceDescriptor {
    private final float[] values;

    public AppearanceDescriptor(float[] values) {
        this.values = values == null ? new float[0] : values.clone();
    }

    public float[] values() {
        return values.clone();
    }

    public boolean available() {
        return values.length > 0;
    }
}
