package com.example.alpr_v1.model;

import java.util.Locale;

public enum ModelRuntime {
    TFLITE("tflite"),
    ONNX("onnx"),
    NCNN("ncnn");

    private final String wireName;

    ModelRuntime(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ModelRuntime fromWire(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ModelRuntime runtime : values()) {
            if (runtime.wireName.equals(normalized)) {
                return runtime;
            }
        }
        throw new IllegalArgumentException("Nieobsługiwany runtime: " + value);
    }
}
