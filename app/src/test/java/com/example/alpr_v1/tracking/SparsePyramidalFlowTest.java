package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class SparsePyramidalFlowTest {
    @Test
    public void tracksIntegerTranslationAcrossPyramid() {
        int width = 128;
        int height = 96;
        byte[] previous = textured(width, height);
        byte[] current = translated(previous, width, height, 7, -5);
        List<SparsePyramidalFlow.Point> points = Arrays.asList(
                new SparsePyramidalFlow.Point(35f, 35f),
                new SparsePyramidalFlow.Point(60f, 30f),
                new SparsePyramidalFlow.Point(85f, 55f),
                new SparsePyramidalFlow.Point(45f, 70f)
        );

        SparsePyramidalFlow.Result result = SparsePyramidalFlow.track(
                previous, current, width, height, points
        );

        assertTrue("matches=" + result.matches.size(), result.matches.size() >= 3);
        assertTrue(result.supportRatio >= 0.75f);
        for (SparsePyramidalFlow.Match match : result.matches) {
            assertEquals(7f, match.target.x - match.source.x, 0.8f);
            assertEquals(-5f, match.target.y - match.source.y, 0.8f);
            assertTrue(match.forwardBackwardError <= 2.2f);
        }
    }

    @Test
    public void rejectsFlatPatchesWithoutGradientSupport() {
        int width = 64;
        int height = 64;
        byte[] flat = new byte[width * height];
        Arrays.fill(flat, (byte) 100);

        SparsePyramidalFlow.Result result = SparsePyramidalFlow.track(
                flat,
                flat,
                width,
                height,
                Arrays.asList(
                        new SparsePyramidalFlow.Point(24f, 24f),
                        new SparsePyramidalFlow.Point(40f, 40f)
                )
        );

        assertTrue(result.matches.isEmpty());
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
                value = Math.max(0, Math.min(255, value));
                image[y * width + x] = (byte) value;
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
