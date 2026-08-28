package com.example.alpr_v1.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.alpr_v1.SettingsActivity;

import org.junit.Test;

public final class SceneContinuityTelemetryContractTest {
    @Test
    public void reportCollectorOwnsCurrentAndFrozenContinuityConfiguration()
            throws Exception {
        assertNotNull(MetricsCollector.class.getDeclaredField("sceneHandlingMode"));
        assertNotNull(MetricsCollector.class.getDeclaredField("sceneContinuityProfile"));
        assertNotNull(MetricsCollector.class.getDeclaredField("frozenSceneHandlingMode"));
        assertNotNull(MetricsCollector.class.getDeclaredField(
                "frozenSceneContinuityProfile"
        ));
        assertNotNull(MetricsCollector.class.getMethod(
                "setSceneContinuityConfiguration",
                String.class,
                String.class
        ));
    }

    @Test
    public void sceneModePreferenceHasStablePublicKey() {
        assertEquals("scene_handling_mode", SettingsActivity.KEY_SCENE_HANDLING_MODE);
    }
}
