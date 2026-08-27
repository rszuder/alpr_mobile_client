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

    public float cosineSimilarity(AppearanceDescriptor other) {
        if (other == null) return 0f;
        float[] right = other.values;
        if (values.length == 0 || values.length != right.length) return 0f;
        float dot = 0f;
        float leftEnergy = 0f;
        float rightEnergy = 0f;
        for (int index = 0; index < values.length; index++) {
            dot += values[index] * right[index];
            leftEnergy += values[index] * values[index];
            rightEnergy += right[index] * right[index];
        }
        float denominator = (float) Math.sqrt(leftEnergy * rightEnergy);
        if (!Float.isFinite(denominator) || denominator <= 1e-6f) return 0f;
        return Math.max(-1f, Math.min(1f, dot / denominator));
    }
}
