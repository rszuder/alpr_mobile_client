package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GlobalLumaMotionTrackerTest {
    @Test
    public void estimatesFrameToFrameTranslationInNormalizedCoordinates() {
        int width = 180;
        int height = 240;
        byte[] first = textured(width, height);
        byte[] second = translated(first, width, height, 6, -4);
        GlobalLumaMotionTracker tracker = new GlobalLumaMotionTracker();

        assertFalse(tracker.update(first, width, height).valid);
        FrameMotionTransform motion = tracker.update(second, width, height);

        assertTrue(motion.valid);
        assertTrue(motion.inliers >= 6);
        assertEquals(6f / width,
                motion.mapX(0.5f, 0.5f) - 0.5f,
                0.008f);
        assertEquals(-4f / height,
                motion.mapY(0.5f, 0.5f) - 0.5f,
                0.008f);
    }

    @Test
    public void resetRequiresANewReferenceFrame() {
        GlobalLumaMotionTracker tracker = new GlobalLumaMotionTracker();
        byte[] frame = textured(96, 96);
        tracker.update(frame, 96, 96);
        tracker.reset();
        assertFalse(tracker.update(frame, 96, 96).valid);
    }

    @Test
    public void recoversLargeHorizontalStepWhenSparseAffineLosesSupport() {
        int width = 180;
        int height = 240;
        byte[] first = textured(width, height);
        byte[] second = translated(first, width, height, -24, 1);
        GlobalLumaMotionTracker tracker = new GlobalLumaMotionTracker();

        tracker.update(first, width, height);
        FrameMotionTransform motion = tracker.update(second, width, height);

        assertTrue(motion.valid);
        assertEquals(-24f / width,
                motion.mapX(0.5f, 0.5f) - 0.5f,
                0.018f);
        assertEquals(1f / height,
                motion.mapY(0.5f, 0.5f) - 0.5f,
                0.012f);
    }

    private static byte[] textured(int width, int height) {
        byte[] image = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = Math.round((float) (
                        128
                                + 42 * Math.sin(x * 0.19)
                                + 36 * Math.cos(y * 0.17)
                                + 27 * Math.sin((x + y) * 0.11)
                                + 18 * Math.cos(x * 0.07 - y * 0.13)
                ));
                image[y * width + x] = (byte) Math.max(0, Math.min(255, value));
            }
        }
        return image;
    }

    private static byte[] translated(
            byte[] source,
            int width,
            int height,
            int dx,
            int dy
    ) {
        byte[] target = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int targetX = x + dx;
                int targetY = y + dy;
                if (targetX >= 0 && targetX < width
                        && targetY >= 0 && targetY < height) {
                    target[targetY * width + targetX] = source[y * width + x];
                }
            }
        }
        return target;
    }
}
