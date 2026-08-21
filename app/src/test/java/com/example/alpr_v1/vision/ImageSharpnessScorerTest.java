package com.example.alpr_v1.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImageSharpnessScorerTest {
    @Test
    public void uniformImageHasNoSharpness() {
        int[] pixels = new int[64];
        java.util.Arrays.fill(pixels, 0xff808080);

        assertEquals(0f, ImageSharpnessScorer.score(pixels, 8, 8, 1), 0.0001f);
    }

    @Test
    public void alternatingPatternScoresHigherThanSmoothGradient() {
        int[] checkerboard = new int[64];
        int[] gradient = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int index = y * 8 + x;
                checkerboard[index] = ((x + y) & 1) == 0 ? 0xff000000 : 0xffffffff;
                int value = x * 24;
                gradient[index] = 0xff000000 | value << 16 | value << 8 | value;
            }
        }

        float detailed = ImageSharpnessScorer.score(checkerboard, 8, 8, 1);
        float smooth = ImageSharpnessScorer.score(gradient, 8, 8, 1);

        assertTrue(detailed > 0.9f);
        assertTrue(detailed > smooth);
    }
}
