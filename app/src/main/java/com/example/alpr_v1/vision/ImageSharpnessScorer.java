package com.example.alpr_v1.vision;

import android.graphics.Bitmap;

/** Bezwzorcowa ocena ostrości oparta na wariancji dyskretnego Laplasjanu. */
public final class ImageSharpnessScorer {
    private static final double NORMALIZATION = 1_000.0;

    private ImageSharpnessScorer() {}

    public static float score(Bitmap bitmap, Detection region) {
        if (bitmap == null || region == null) return 0f;
        int left = clamp((int) Math.floor(region.left), 0, bitmap.getWidth() - 1);
        int top = clamp((int) Math.floor(region.top), 0, bitmap.getHeight() - 1);
        int right = clamp((int) Math.ceil(region.right), left + 1, bitmap.getWidth());
        int bottom = clamp((int) Math.ceil(region.bottom), top + 1, bitmap.getHeight());
        int width = right - left;
        int height = bottom - top;
        if (width < 3 || height < 3) return 0f;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, left, top, width, height);
        int step = Math.max(1, Math.max(width, height) / 160);
        return score(pixels, width, height, step);
    }

    static float score(int[] pixels, int width, int height, int step) {
        if (pixels == null || pixels.length < width * height || width < 3 || height < 3) {
            return 0f;
        }
        int resolvedStep = Math.max(1, step);
        double sum = 0.0;
        double squareSum = 0.0;
        int count = 0;
        for (int y = resolvedStep; y < height - resolvedStep; y += resolvedStep) {
            for (int x = resolvedStep; x < width - resolvedStep; x += resolvedStep) {
                int center = luminance(pixels[y * width + x]);
                int laplacian = 4 * center
                        - luminance(pixels[y * width + x - resolvedStep])
                        - luminance(pixels[y * width + x + resolvedStep])
                        - luminance(pixels[(y - resolvedStep) * width + x])
                        - luminance(pixels[(y + resolvedStep) * width + x]);
                sum += laplacian;
                squareSum += (double) laplacian * laplacian;
                count++;
            }
        }
        if (count == 0) return 0f;
        double mean = sum / count;
        double variance = Math.max(0.0, squareSum / count - mean * mean);
        return (float) Math.max(0.0, Math.min(1.0, variance / (variance + NORMALIZATION)));
    }

    private static int luminance(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        return (77 * red + 150 * green + 29 * blue) >> 8;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
