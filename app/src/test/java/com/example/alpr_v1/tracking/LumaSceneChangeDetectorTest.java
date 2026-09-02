package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.NormalizedBounds;

import org.junit.Test;

import java.util.Collections;

public final class LumaSceneChangeDetectorTest {
    @Test
    public void fullFrameReplacementTriggersAbruptChange() {
        LumaSceneChangeDetector detector = new LumaSceneChangeDetector();
        byte[] dark = filled(160, 120, 20);
        byte[] bright = filled(160, 120, 235);

        assertFalse(detector.update(dark, 160, 120).changed);
        LumaSceneChangeDetector.Result result = detector.update(
                bright, 160, 120
        );

        assertTrue(result.changed);
        assertTrue(result.changedFraction > 0.99f);
    }

    @Test
    public void movingVehiclePatchExcludedByForegroundMaskIsNotSceneChange() {
        int width = 160;
        int height = 120;
        byte[] first = filled(width, height, 90);
        byte[] second = first.clone();
        for (int y = 25; y < 100; y++) {
            for (int x = 30; x < 140; x++) {
                second[y * width + x] = (byte) 220;
            }
        }
        LumaSceneChangeDetector detector = new LumaSceneChangeDetector();
        NormalizedBounds vehicle = new NormalizedBounds(
                0.15f, 0.15f, 0.95f, 0.90f
        );

        detector.update(first, width, height,
                Collections.singletonList(vehicle));
        LumaSceneChangeDetector.Result result = detector.update(
                second,
                width,
                height,
                Collections.singletonList(vehicle)
        );

        assertFalse(result.changed);
        assertFalse(result.globalChanged);
    }

    @Test
    public void largeCutHiddenUnderVehicleMaskIsVisibleToGlobalChannel() {
        int width = 160;
        int height = 120;
        byte[] first = filled(width, height, 30);
        byte[] second = first.clone();
        for (int y = 10; y < 110; y++) {
            for (int x = 15; x < 145; x++) {
                second[y * width + x] = (byte) 220;
            }
        }
        LumaSceneChangeDetector detector = new LumaSceneChangeDetector();
        NormalizedBounds oldVehicle = new NormalizedBounds(
                0.05f, 0.05f, 0.95f, 0.95f
        );

        detector.update(first, width, height,
                Collections.singletonList(oldVehicle));
        LumaSceneChangeDetector.Result result = detector.update(
                second,
                width,
                height,
                Collections.singletonList(oldVehicle)
        );

        assertFalse(result.changed);
        assertTrue(result.globalChanged);
        assertTrue(result.globalChangedFraction > 0.60f);
        assertTrue(result.globalMeanDelta > 30f);
    }

    @Test
    public void stableFrameAfterReplacementDoesNotRepeatChange() {
        LumaSceneChangeDetector detector = new LumaSceneChangeDetector();
        byte[] dark = filled(160, 120, 20);
        byte[] bright = filled(160, 120, 235);
        detector.update(dark, 160, 120);

        assertTrue(detector.update(bright, 160, 120).changed);
        assertFalse(detector.update(bright, 160, 120).changed);
    }

    private static byte[] filled(int width, int height, int value) {
        byte[] frame = new byte[width * height];
        java.util.Arrays.fill(frame, (byte) value);
        return frame;
    }
}
