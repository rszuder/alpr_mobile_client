package com.example.alpr_v1.vision;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SceneChangeDetectorTest {
    @Test
    public void luminanceSamplesDetectAbruptSceneWithoutBitmapConversion() {
        SceneChangeDetector detector = new SceneChangeDetector();
        float[] stable = new float[400];
        float[] changed = new float[400];
        for (int index = 0; index < changed.length; index++) {
            changed[index] = index % 2 == 0 ? 0f : 255f;
        }

        assertFalse(detector.updateSamples(stable, 1920, 1080).sceneChanged);
        assertTrue(detector.updateSamples(stable, 1920, 1080).armed);
        assertTrue(detector.updateSamples(changed, 1920, 1080).sceneChanged);
    }
}
