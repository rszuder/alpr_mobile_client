package com.example.alpr_v1.tracking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Estymuje lekki globalny ruch obrazu z kolejnych, małych klatek luminancji. */
public final class GlobalLumaMotionTracker {
    private static final int GRID_X = 6;
    private static final int GRID_Y = 5;
    private static final int MINIMUM_INLIERS = 6;
    private byte[] previous;
    private int previousWidth;
    private int previousHeight;

    public synchronized FrameMotionTransform update(
            byte[] gray,
            int width,
            int height
    ) {
        if (gray == null || width < 32 || height < 32
                || gray.length < width * height) {
            reset();
            return FrameMotionTransform.invalid();
        }
        byte[] current = Arrays.copyOf(gray, width * height);
        if (previous == null
                || previousWidth != width
                || previousHeight != height) {
            previous = current;
            previousWidth = width;
            previousHeight = height;
            return FrameMotionTransform.invalid();
        }

        byte[] reference = previous;
        List<SparsePyramidalFlow.Point> points = grid(width, height);
        SparsePyramidalFlow.Result flow = SparsePyramidalFlow.track(
                reference,
                current,
                width,
                height,
                points
        );
        previous = current;

        RobustAffineTransform.Result affine =
                RobustAffineTransform.estimate(flow.matches);
        if (affine.valid && affine.inlierCount >= MINIMUM_INLIERS) {
            float determinant = affine.determinant();
            if (Float.isFinite(determinant)
                    && determinant >= 0.72f
                    && determinant <= 1.38f) {
                FrameMotionTransform normalized = new FrameMotionTransform(
                        true,
                        affine.a,
                        affine.b * height / (float) width,
                        affine.tx / width,
                        affine.c * width / (float) height,
                        affine.d,
                        affine.ty / height,
                        affine.inlierCount,
                        affine.meanError
                );
                if (maximumCornerDisplacement(normalized) <= 0.28f) {
                    return normalized;
                }
            }
        }

        FrameMotionTransform sparseFallback = translationFromSparseFlow(
                flow.matches,
                width,
                height
        );
        if (sparseFallback.valid) return sparseFallback;

        return coarseTranslation(reference, current, width, height);
    }

    public synchronized void reset() {
        previous = null;
        previousWidth = 0;
        previousHeight = 0;
    }

    private static List<SparsePyramidalFlow.Point> grid(int width, int height) {
        List<SparsePyramidalFlow.Point> points = new ArrayList<>(GRID_X * GRID_Y);
        float marginX = Math.max(10f, width * 0.08f);
        float marginY = Math.max(10f, height * 0.08f);
        for (int y = 0; y < GRID_Y; y++) {
            float py = marginY + y * (height - 2f * marginY) / (GRID_Y - 1f);
            for (int x = 0; x < GRID_X; x++) {
                float px = marginX + x * (width - 2f * marginX) / (GRID_X - 1f);
                points.add(new SparsePyramidalFlow.Point(px, py));
            }
        }
        return points;
    }

    private static float maximumCornerDisplacement(FrameMotionTransform transform) {
        float maximum = 0f;
        float[][] points = new float[][]{
                {0f, 0f}, {1f, 0f}, {0f, 1f}, {1f, 1f}, {0.5f, 0.5f}
        };
        for (float[] point : points) {
            float dx = transform.mapX(point[0], point[1]) - point[0];
            float dy = transform.mapY(point[0], point[1]) - point[1];
            maximum = Math.max(maximum, (float) Math.sqrt(dx * dx + dy * dy));
        }
        return maximum;
    }

    private static FrameMotionTransform translationFromSparseFlow(
            List<SparsePyramidalFlow.Match> matches,
            int width,
            int height
    ) {
        if (matches == null || matches.size() < 3) {
            return FrameMotionTransform.invalid();
        }
        List<Float> horizontal = new ArrayList<>(matches.size());
        List<Float> vertical = new ArrayList<>(matches.size());
        for (SparsePyramidalFlow.Match match : matches) {
            horizontal.add(match.target.x - match.source.x);
            vertical.add(match.target.y - match.source.y);
        }
        Collections.sort(horizontal);
        Collections.sort(vertical);
        float dx = median(horizontal);
        float dy = median(vertical);
        int inliers = 0;
        float errorSum = 0f;
        for (SparsePyramidalFlow.Match match : matches) {
            float residualX = match.target.x - match.source.x - dx;
            float residualY = match.target.y - match.source.y - dy;
            float residual = (float) Math.sqrt(
                    residualX * residualX + residualY * residualY
            );
            if (residual <= 3.2f) {
                inliers++;
                errorSum += residual;
            }
        }
        FrameMotionTransform result = new FrameMotionTransform(
                inliers >= 3,
                1f, 0f, dx / width,
                0f, 1f, dy / height,
                inliers,
                inliers == 0 ? Float.POSITIVE_INFINITY : errorSum / inliers
        );
        return result.valid && maximumCornerDisplacement(result) <= 0.22f
                ? result : FrameMotionTransform.invalid();
    }

    private static float median(List<Float> sorted) {
        int size = sorted.size();
        int middle = size / 2;
        return size % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) * 0.5f
                : sorted.get(middle);
    }

    private static FrameMotionTransform coarseTranslation(
            byte[] previous,
            byte[] current,
            int width,
            int height
    ) {
        Translation forward = searchTranslation(previous, current, width, height);
        if (!forward.valid) return FrameMotionTransform.invalid();
        Translation backward = searchTranslation(current, previous, width, height);
        if (!backward.valid
                || Math.abs(forward.dx + backward.dx) > 2
                || Math.abs(forward.dy + backward.dy) > 2) {
            return FrameMotionTransform.invalid();
        }
        FrameMotionTransform result = new FrameMotionTransform(
                true,
                1f, 0f, forward.dx / (float) width,
                0f, 1f, forward.dy / (float) height,
                3,
                forward.error
        );
        return maximumCornerDisplacement(result) <= 0.22f
                ? result : FrameMotionTransform.invalid();
    }

    private static Translation searchTranslation(
            byte[] source,
            byte[] target,
            int width,
            int height
    ) {
        int maximumDx = Math.max(4, Math.round(width * 0.18f));
        int maximumDy = Math.max(3, Math.round(height * 0.055f));
        int startX = maximumDx + 4;
        int endX = width - maximumDx - 4;
        int startY = maximumDy + 4;
        int endY = height - maximumDy - 4;
        if (endX <= startX || endY <= startY) return Translation.invalid();

        float bestError = Float.POSITIVE_INFINITY;
        float zeroError = Float.POSITIVE_INFINITY;
        int bestDx = 0;
        int bestDy = 0;
        for (int dy = -maximumDy; dy <= maximumDy; dy++) {
            for (int dx = -maximumDx; dx <= maximumDx; dx++) {
                long difference = 0L;
                int samples = 0;
                for (int y = startY; y < endY; y += 4) {
                    int sourceRow = y * width;
                    int targetRow = (y + dy) * width;
                    for (int x = startX; x < endX; x += 4) {
                        difference += Math.abs(
                                (source[sourceRow + x] & 0xff)
                                        - (target[targetRow + x + dx] & 0xff)
                        );
                        samples++;
                    }
                }
                float error = difference / (float) Math.max(1, samples);
                if (dx == 0 && dy == 0) zeroError = error;
                if (error < bestError) {
                    bestError = error;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }
        if (!Float.isFinite(bestError) || bestError > 34f) {
            return Translation.invalid();
        }
        if (bestDx != 0 || bestDy != 0) {
            float requiredImprovement = Math.max(1.5f, zeroError * 0.08f);
            if (!Float.isFinite(zeroError)
                    || zeroError - bestError < requiredImprovement
                    || Math.abs(bestDx) == maximumDx
                    || Math.abs(bestDy) == maximumDy) {
                return Translation.invalid();
            }
        }
        return new Translation(true, bestDx, bestDy, bestError);
    }

    private static final class Translation {
        final boolean valid;
        final int dx;
        final int dy;
        final float error;

        Translation(boolean valid, int dx, int dy, float error) {
            this.valid = valid;
            this.dx = dx;
            this.dy = dy;
            this.error = error;
        }

        static Translation invalid() {
            return new Translation(false, 0, 0, Float.POSITIVE_INFINITY);
        }
    }
}
