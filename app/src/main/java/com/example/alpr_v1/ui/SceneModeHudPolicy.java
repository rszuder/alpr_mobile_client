package com.example.alpr_v1.ui;

import com.example.alpr_v1.continuity.SceneHandlingMode;

/** Prosta, testowalna polityka przełącznika trybu sceny w pasku live. */
public final class SceneModeHudPolicy {
    private SceneModeHudPolicy() {
    }

    public static boolean isToggleEnabled(boolean experimentModeEnabled) {
        return !experimentModeEnabled;
    }

    public static SceneHandlingMode nextUserMode(SceneHandlingMode currentMode) {
        return currentMode == SceneHandlingMode.DYNAMIC_CONTINUITY
                ? SceneHandlingMode.STRICT_SCENE_BOUNDARY
                : SceneHandlingMode.DYNAMIC_CONTINUITY;
    }
}
