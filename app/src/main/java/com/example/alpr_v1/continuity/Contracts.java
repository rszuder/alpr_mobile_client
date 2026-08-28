package com.example.alpr_v1.continuity;

final class Contracts {
    private Contracts() {}

    static float unit(String name, float value) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
        return value;
    }

    static long nonNegative(String name, long value) {
        if (value < 0L) throw new IllegalArgumentException(name + " must be non-negative");
        return value;
    }

    static int nonNegative(String name, int value) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
        return value;
    }

    static long positive(String name, long value) {
        if (value <= 0L) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static float nonNegativeFinite(String name, float value) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    static <T> T required(String name, T value) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    static String reason(String value) {
        return value == null ? "" : value.trim();
    }
}
