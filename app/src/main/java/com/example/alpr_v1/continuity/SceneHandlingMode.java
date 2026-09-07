package com.example.alpr_v1.continuity;

/** Independent policy axis describing how visual change affects domain identity. */
public enum SceneHandlingMode {
    STRICT_SCENE_BOUNDARY("strict_scene_boundary"),
    DYNAMIC_CONTINUITY("dynamic_continuity");

    private final String wireName;

    SceneHandlingMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public String analysisMode() {
        return this == STRICT_SCENE_BOUNDARY ? "static" : "dynamic";
    }

    public static SceneHandlingMode fromWireName(String value) {
        if (value != null) {
            if ("static".equalsIgnoreCase(value.trim())) return STRICT_SCENE_BOUNDARY;
            if ("dynamic".equalsIgnoreCase(value.trim())) return DYNAMIC_CONTINUITY;
            for (SceneHandlingMode mode : values()) {
                if (mode.wireName.equalsIgnoreCase(value.trim())) return mode;
            }
        }
        return DYNAMIC_CONTINUITY;
    }
}
