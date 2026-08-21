package com.example.alpr_v1.camera;

/** Profil rozdzielczości źródłowej CameraX, niezależny od wejść modeli. */
public enum AnalysisResolutionProfile {
    AUTO("auto", 0, 0),
    FAST("fast", 640, 480),
    DISTANT("distant", 1920, 1080);

    private final String wireName;
    private final int width;
    private final int height;

    AnalysisResolutionProfile(String wireName, int width, int height) {
        this.wireName = wireName;
        this.width = width;
        this.height = height;
    }

    public String wireName() { return wireName; }
    public int width() { return width; }
    public int height() { return height; }

    public static AnalysisResolutionProfile fromWireName(String value) {
        for (AnalysisResolutionProfile profile : values()) {
            if (profile.wireName.equalsIgnoreCase(value == null ? "" : value.trim())) {
                return profile;
            }
        }
        return AUTO;
    }
}
