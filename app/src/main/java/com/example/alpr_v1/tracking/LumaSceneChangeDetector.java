package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.NormalizedBounds;

import java.util.Collections;
import java.util.List;

/** Lekki frame-to-frame detector dużej zmiany poza świeżym foreground pojazdów. */
public final class LumaSceneChangeDetector {
    private static final int SAMPLE_STEP = 4;
    private static final int PIXEL_CHANGE_THRESHOLD = 30;
    private static final int MINIMUM_SAMPLES = 128;
    private static final float CHANGE_FRACTION_THRESHOLD = 0.45f;
    private static final float MEAN_DELTA_THRESHOLD = 24f;

    public static final class Result {
        public final boolean changed;
        public final float changedFraction;
        public final float meanDelta;
        public final int samples;

        private Result(
                boolean changed,
                float changedFraction,
                float meanDelta,
                int samples
        ) {
            this.changed = changed;
            this.changedFraction = changedFraction;
            this.meanDelta = meanDelta;
            this.samples = samples;
        }

        static Result referenceOnly() {
            return new Result(false, 0f, 0f, 0);
        }
    }

    private byte[] previous;
    private int previousWidth;
    private int previousHeight;

    public Result update(byte[] gray, int width, int height) {
        return update(gray, width, height, Collections.emptyList());
    }

    public synchronized Result update(
            byte[] gray,
            int width,
            int height,
            List<NormalizedBounds> foregroundMasks
    ) {
        if (gray == null || width <= 0 || height <= 0
                || gray.length < width * height) {
            return Result.referenceOnly();
        }
        if (previous == null || previousWidth != width || previousHeight != height) {
            remember(gray, width, height);
            return Result.referenceOnly();
        }

        List<NormalizedBounds> masks = foregroundMasks == null
                ? Collections.emptyList() : foregroundMasks;
        int samples = 0;
        int changed = 0;
        long deltaSum = 0L;
        for (int y = SAMPLE_STEP / 2; y < height; y += SAMPLE_STEP) {
            float normalizedY = y / (float) height;
            for (int x = SAMPLE_STEP / 2; x < width; x += SAMPLE_STEP) {
                float normalizedX = x / (float) width;
                if (insideAnyMask(normalizedX, normalizedY, masks)) continue;
                int index = y * width + x;
                int delta = Math.abs(
                        (gray[index] & 0xff) - (previous[index] & 0xff)
                );
                samples++;
                deltaSum += delta;
                if (delta >= PIXEL_CHANGE_THRESHOLD) changed++;
            }
        }
        remember(gray, width, height);
        if (samples < MINIMUM_SAMPLES) return Result.referenceOnly();
        float changedFraction = changed / (float) samples;
        float meanDelta = deltaSum / (float) samples;
        return new Result(
                changedFraction >= CHANGE_FRACTION_THRESHOLD
                        && meanDelta >= MEAN_DELTA_THRESHOLD,
                changedFraction,
                meanDelta,
                samples
        );
    }

    public synchronized void reset() {
        previous = null;
        previousWidth = 0;
        previousHeight = 0;
    }

    private void remember(byte[] gray, int width, int height) {
        previous = gray.clone();
        previousWidth = width;
        previousHeight = height;
    }

    private static boolean insideAnyMask(
            float x,
            float y,
            List<NormalizedBounds> masks
    ) {
        for (NormalizedBounds mask : masks) {
            if (mask != null && mask.valid()
                    && x >= mask.left && x <= mask.right
                    && y >= mask.top && y <= mask.bottom) {
                return true;
            }
        }
        return false;
    }
}
