package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.vision.Detection;

/** Low-cost colour-layout descriptor used only to disambiguate MP vehicle tracks. */
final class VehicleAppearanceDescriptor {
    private static final int GRID_X = 6;
    private static final int GRID_Y = 4;
    static final int DESCRIPTOR_SIZE = GRID_X * GRID_Y * 3 + 3;

    private VehicleAppearanceDescriptor() {}

    static AppearanceDescriptor from(Bitmap frame, Detection detection) {
        if (frame == null || frame.isRecycled() || detection == null) {
            return new AppearanceDescriptor(null);
        }
        int left = clamp((int) Math.floor(detection.left), 0, frame.getWidth() - 1);
        int top = clamp((int) Math.floor(detection.top), 0, frame.getHeight() - 1);
        int right = clamp((int) Math.ceil(detection.right), left + 1, frame.getWidth());
        int bottom = clamp((int) Math.ceil(detection.bottom), top + 1, frame.getHeight());
        int width = right - left;
        int height = bottom - top;
        if (width < GRID_X || height < GRID_Y) return new AppearanceDescriptor(null);

        float[] sampledRgb = new float[GRID_X * GRID_Y * 3];
        int cell = 0;
        for (int gy = 0; gy < GRID_Y; gy++) {
            int y = clamp(
                    top + Math.round((gy + 0.5f) * height / GRID_Y), top, bottom - 1
            );
            for (int gx = 0; gx < GRID_X; gx++) {
                int x = clamp(
                        left + Math.round((gx + 0.5f) * width / GRID_X), left, right - 1
                );
                int pixel = frame.getPixel(x, y);
                float red = ((pixel >> 16) & 0xff) / 255f;
                float green = ((pixel >> 8) & 0xff) / 255f;
                float blue = (pixel & 0xff) / 255f;
                int offset = cell++ * 3;
                sampledRgb[offset] = red;
                sampledRgb[offset + 1] = green;
                sampledRgb[offset + 2] = blue;
            }
        }

        return fromSampledRgb(sampledRgb);
    }

    static AppearanceDescriptor fromSampledRgb(float[] sampledRgb) {
        if (sampledRgb == null || sampledRgb.length != GRID_X * GRID_Y * 3) {
            return new AppearanceDescriptor(null);
        }
        float[] values = new float[DESCRIPTOR_SIZE];
        float redMean = 0f;
        float greenMean = 0f;
        float blueMean = 0f;
        for (int index = 0; index < sampledRgb.length; index += 3) {
            float red = finite01(sampledRgb[index]);
            float green = finite01(sampledRgb[index + 1]);
            float blue = finite01(sampledRgb[index + 2]);
            values[index] = red;
            values[index + 1] = green;
            values[index + 2] = blue;
            redMean += red;
            greenMean += green;
            blueMean += blue;
        }
        float cells = GRID_X * GRID_Y;
        redMean /= cells;
        greenMean /= cells;
        blueMean /= cells;
        float energy = 0f;
        for (int index = 0; index < GRID_X * GRID_Y * 3; index += 3) {
            values[index] -= redMean;
            values[index + 1] -= greenMean;
            values[index + 2] -= blueMean;
            energy += values[index] * values[index]
                    + values[index + 1] * values[index + 1]
                    + values[index + 2] * values[index + 2];
        }
        if (energy > 1e-6f) {
            float norm = (float) Math.sqrt(energy);
            for (int index = 0; index < GRID_X * GRID_Y * 3; index++) {
                values[index] /= norm;
            }
        }
        values[DESCRIPTOR_SIZE - 3] = redMean * 3f;
        values[DESCRIPTOR_SIZE - 2] = greenMean * 3f;
        values[DESCRIPTOR_SIZE - 1] = blueMean * 3f;
        return new AppearanceDescriptor(values);
    }

    private static float finite01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
