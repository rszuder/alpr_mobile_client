package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;

import com.example.alpr_v1.vision.Detection;

/** Lekki, znormalizowany deskryptor jasności cropa tablicy. */
final class PlateAppearanceDescriptor {
    private static final int GRID_X = 20;
    private static final int GRID_Y = 8;

    private PlateAppearanceDescriptor() {}

    static float[] from(Bitmap frame, Detection detection) {
        if (frame == null || frame.isRecycled() || detection == null) return null;
        int left = clamp((int) Math.floor(detection.left), 0, frame.getWidth() - 1);
        int top = clamp((int) Math.floor(detection.top), 0, frame.getHeight() - 1);
        int right = clamp((int) Math.ceil(detection.right), left + 1, frame.getWidth());
        int bottom = clamp((int) Math.ceil(detection.bottom), top + 1, frame.getHeight());
        int width = right - left;
        int height = bottom - top;
        if (width < 4 || height < 2) return null;

        float[] descriptor = new float[GRID_X * GRID_Y];
        float sum = 0f;
        int index = 0;
        for (int gy = 0; gy < GRID_Y; gy++) {
            int y = clamp(
                    top + Math.round((gy + 0.5f) * height / GRID_Y),
                    top,
                    bottom - 1
            );
            for (int gx = 0; gx < GRID_X; gx++) {
                int x = clamp(
                        left + Math.round((gx + 0.5f) * width / GRID_X),
                        left,
                        right - 1
                );
                int pixel = frame.getPixel(x, y);
                float luminance = 0.2126f * ((pixel >> 16) & 0xff)
                        + 0.7152f * ((pixel >> 8) & 0xff)
                        + 0.0722f * (pixel & 0xff);
                descriptor[index++] = luminance;
                sum += luminance;
            }
        }

        float mean = sum / descriptor.length;
        float energy = 0f;
        for (int i = 0; i < descriptor.length; i++) {
            descriptor[i] -= mean;
            energy += descriptor[i] * descriptor[i];
        }
        float norm = (float) Math.sqrt(Math.max(1e-6f, energy));
        for (int i = 0; i < descriptor.length; i++) descriptor[i] /= norm;
        return descriptor;
    }

    static float similarity(float[] first, float[] second) {
        if (first == null || second == null || first.length != second.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < first.length; i++) dot += first[i] * second[i];
        return Math.max(-1f, Math.min(1f, dot));
    }

    static float[] blend(float[] stable, float[] fresh, float freshWeight) {
        if (stable == null) return fresh == null ? null : fresh.clone();
        if (fresh == null || stable.length != fresh.length) return stable.clone();
        float weight = Math.max(0f, Math.min(1f, freshWeight));
        float[] result = new float[stable.length];
        float energy = 0f;
        for (int i = 0; i < result.length; i++) {
            result[i] = (1f - weight) * stable[i] + weight * fresh[i];
            energy += result[i] * result[i];
        }
        float norm = (float) Math.sqrt(Math.max(1e-6f, energy));
        for (int i = 0; i < result.length; i++) result[i] /= norm;
        return result;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
