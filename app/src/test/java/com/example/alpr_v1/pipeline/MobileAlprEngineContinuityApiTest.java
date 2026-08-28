package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.SceneHandlingMode;

import org.junit.Test;

public final class MobileAlprEngineContinuityApiTest {
    @Test
    public void engineSeparatesHardResetHoldReacquireAndTargetRelease() throws Exception {
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "hardResetScene", String.class
        ));
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "beginSoftHold", long.class, String.class
        ));
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "beginSoftReacquire", long.class, String.class
        ));
        assertNotNull(MobileAlprEngine.class.getDeclaredMethod(
                "releaseFocusedTarget", String.class
        ));
    }

    @Test
    public void internalDetectorIsEvidenceOnlyInDynamicMode() {
        assertTrue(MobileAlprEngine.shouldHardResetInternalScene(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                true,
                false
        ));
        assertFalse(MobileAlprEngine.shouldHardResetInternalScene(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                true,
                false
        ));
        assertFalse(MobileAlprEngine.shouldHardResetInternalScene(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                true,
                true
        ));
    }
}
