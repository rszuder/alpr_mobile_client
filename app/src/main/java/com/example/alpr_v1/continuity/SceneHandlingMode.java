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

    public static SceneHandlingMode fromWireName(String value) {
        if (value != null) {
            for (SceneHandlingMode mode : values()) {
                if (mode.wireName.equalsIgnoreCase(value.trim())) return mode;
            }
        }
        return DYNAMIC_CONTINUITY;
    }
}
