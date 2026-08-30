package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.SceneHandlingMode;

import org.junit.Test;

public final class SceneModeHudPolicyTest {
    @Test
    public void dynamicMode_togglesToStaticMode() {
        assertEquals(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                SceneModeHudPolicy.nextUserMode(SceneHandlingMode.DYNAMIC_CONTINUITY)
        );
    }

    @Test
    public void staticMode_togglesToDynamicMode() {
        assertEquals(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneModeHudPolicy.nextUserMode(SceneHandlingMode.STRICT_SCENE_BOUNDARY)
        );
    }

    @Test
    public void experimentMode_disablesUserToggle() {
        assertTrue(SceneModeHudPolicy.isToggleEnabled(false));
        assertFalse(SceneModeHudPolicy.isToggleEnabled(true));
    }
}
