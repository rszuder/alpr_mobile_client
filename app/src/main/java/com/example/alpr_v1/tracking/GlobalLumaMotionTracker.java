package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.NormalizedBounds;

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
    private List<NormalizedBounds> previousForegroundMasks = Collections.emptyList();

    public synchronized FrameMotionTransform update(
            byte[] gray,
            int width,
            int height
    ) {
        return update(gray, width, height, Collections.emptyList());
    }

    public synchronized FrameMotionTransform update(
            byte[] gray,
            int width,
            int height,
            List<NormalizedBounds> foregroundMasks
    ) {
        if (gray == null || width < 32 || height < 32
                || gray.length < width * height) {
            reset();
            return FrameMotionTransform.invalid();
        }
        byte[] current = Arrays.copyOf(gray, width * height);
        List<NormalizedBounds> currentForegroundMasks = immutableMasks(
                foregroundMasks
        );
        if (previous == null
                || previousWidth != width
                || previousHeight != height) {
            previous = current;
            previousWidth = width;
            previousHeight = height;
            previousForegroundMasks = currentForegroundMasks;
            return FrameMotionTransform.invalid();
        }

        byte[] reference = previous;
        List<NormalizedBounds> combinedForegroundMasks = new ArrayList<>(
                previousForegroundMasks.size() + currentForegroundMasks.size()
        );
        combinedForegroundMasks.addAll(previousForegroundMasks);
        combinedForegroundMasks.addAll(currentForegroundMasks);
        List<SparsePyramidalFlow.Point> points = grid(
                width,
                height,
                combinedForegroundMasks
        );
        if (points.size() < 8) {
            previous = current;
            previousForegroundMasks = currentForegroundMasks;
            return FrameMotionTransform.invalid();
        }
        SparsePyramidalFlow.Result flow = SparsePyramidalFlow.track(
                reference,
                current,
                width,
                height,
                points
        );
        previous = current;
        previousForegroundMasks = currentForegroundMasks;

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
                        affine.meanError,
                        qualityForTransform(
                                flow.matches,
                                points.size(),
                                width,
                                height,
                                affine.a,
                                affine.b,
                                affine.tx,
                                affine.c,
                                affine.d,
                                affine.ty
                        )
                );
                if (normalized.quality.reliableCameraMotion()
                        && maximumCornerDisplacement(normalized) <= 0.28f) {
                    return normalized;
                }
            }
        }

        FrameMotionTransform sparseFallback = translationFromSparseFlow(
                flow.matches,
                points.size(),
                width,
                height
        );
        if (sparseFallback.valid) return sparseFallback;

        return coarseTranslation(
                reference,
                current,
                width,
                height,
                points,
                combinedForegroundMasks
        );
    }

    public synchronized void reset() {
        previous = null;
        previousWidth = 0;
        previousHeight = 0;
        previousForegroundMasks = Collections.emptyList();
    }

    private static List<NormalizedBounds> immutableMasks(
            List<NormalizedBounds> masks
    ) {
        if (masks == null || masks.isEmpty()) return Collections.emptyList();
        List<NormalizedBounds> valid = new ArrayList<>();
        for (NormalizedBounds mask : masks) {
            if (mask != null && mask.valid()) valid.add(mask);
        }
        return Collections.unmodifiableList(valid);
    }

    private static List<SparsePyramidalFlow.Point> grid(
            int width,
            int height,
            List<NormalizedBounds> foregroundMasks
    ) {
        List<SparsePyramidalFlow.Point> points = new ArrayList<>(GRID_X * GRID_Y);
        float marginX = Math.max(10f, width * 0.08f);
        float marginY = Math.max(10f, height * 0.08f);
        for (int y = 0; y < GRID_Y; y++) {
            float py = marginY + y * (height - 2f * marginY) / (GRID_Y - 1f);
            for (int x = 0; x < GRID_X; x++) {
                float px = marginX + x * (width - 2f * marginX) / (GRID_X - 1f);
                if (!insideForeground(
                        px / width,
                        py / height,
                        foregroundMasks
                )) {
                    points.add(new SparsePyramidalFlow.Point(px, py));
                }
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
            int totalSamples,
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
        FrameMotionQuality quality = qualityForTransform(
                matches,
                totalSamples,
                width,
                height,
                1f, 0f, dx,
                0f, 1f, dy
        );
        FrameMotionTransform result = new FrameMotionTransform(
                quality.reliableCameraMotion(),
                1f, 0f, dx / width,
                0f, 1f, dy / height,
                inliers,
                inliers == 0 ? Float.POSITIVE_INFINITY : errorSum / inliers,
                quality
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
            int height,
            List<SparsePyramidalFlow.Point> points,
            List<NormalizedBounds> foregroundMasks
    ) {
        Translation forward = searchTranslation(
                previous, current, width, height, foregroundMasks
        );
        if (!forward.valid) return FrameMotionTransform.invalid();
        Translation backward = searchTranslation(
                current, previous, width, height, foregroundMasks
        );
        if (!backward.valid
                || Math.abs(forward.dx + backward.dx) > 2
                || Math.abs(forward.dy + backward.dy) > 2) {
            return FrameMotionTransform.invalid();
        }
        List<SparsePyramidalFlow.Match> matches = translationMatches(
                previous,
                current,
                width,
                height,
                points,
                forward.dx,
                forward.dy
        );
        FrameMotionQuality quality = qualityForTransform(
                matches,
                points.size(),
                width,
                height,
                1f, 0f, forward.dx,
                0f, 1f, forward.dy
        );
        FrameMotionTransform result = new FrameMotionTransform(
                quality.reliableCameraMotion(),
                1f, 0f, forward.dx / (float) width,
                0f, 1f, forward.dy / (float) height,
                quality.inliers,
                forward.error,
                quality
        );
        return maximumCornerDisplacement(result) <= 0.22f
                ? result : FrameMotionTransform.invalid();
    }

    private static Translation searchTranslation(
            byte[] source,
            byte[] target,
            int width,
            int height,
            List<NormalizedBounds> foregroundMasks
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
                        if (insideForeground(
                                x / (float) width,
                                y / (float) height,
                                foregroundMasks
                        )) continue;
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

    private static List<SparsePyramidalFlow.Match> translationMatches(
            byte[] source,
            byte[] target,
            int width,
            int height,
            List<SparsePyramidalFlow.Point> points,
            int dx,
            int dy
    ) {
        List<SparsePyramidalFlow.Match> matches = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            SparsePyramidalFlow.Point point = points.get(index);
            int x = Math.round(point.x);
            int y = Math.round(point.y);
            int targetX = x + dx;
            int targetY = y + dy;
            if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2
                    || targetX < 2 || targetY < 2
                    || targetX >= width - 2 || targetY >= height - 2) continue;
            float error = patchError(
                    source, target, width, x, y, targetX, targetY
            );
            if (error <= 32f) {
                matches.add(new SparsePyramidalFlow.Match(
                        index,
                        point,
                        new SparsePyramidalFlow.Point(targetX, targetY),
                        error,
                        0f
                ));
            }
        }
        return matches;
    }

    private static float patchError(
            byte[] source,
            byte[] target,
            int width,
            int sourceX,
            int sourceY,
            int targetX,
            int targetY
    ) {
        int difference = 0;
        int samples = 0;
        for (int y = -2; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                difference += Math.abs(
                        (source[(sourceY + y) * width + sourceX + x] & 0xff)
                                - (target[(targetY + y) * width + targetX + x] & 0xff)
                );
                samples++;
            }
        }
        return difference / (float) samples;
    }

    private static FrameMotionQuality qualityForTransform(
            List<SparsePyramidalFlow.Match> matches,
            int totalSamples,
            int width,
            int height,
            float a,
            float b,
            float tx,
            float c,
            float d,
            float ty
    ) {
        if (matches == null || matches.isEmpty() || totalSamples <= 0) {
            return FrameMotionQuality.unavailable(totalSamples);
        }
        int inliers = 0;
        float residualSum = 0f;
        float minimumX = Float.POSITIVE_INFINITY;
        float minimumY = Float.POSITIVE_INFINITY;
        float maximumX = Float.NEGATIVE_INFINITY;
        float maximumY = Float.NEGATIVE_INFINITY;
        boolean[] quadrants = new boolean[4];
        for (SparsePyramidalFlow.Match match : matches) {
            float expectedX = a * match.source.x + b * match.source.y + tx;
            float expectedY = c * match.source.x + d * match.source.y + ty;
            float residualX = expectedX - match.target.x;
            float residualY = expectedY - match.target.y;
            float residual = (float) Math.sqrt(
                    residualX * residualX + residualY * residualY
            );
            if (residual > 2.8f) continue;
            inliers++;
            residualSum += residual;
            float normalizedX = match.source.x / width;
            float normalizedY = match.source.y / height;
            minimumX = Math.min(minimumX, normalizedX);
            minimumY = Math.min(minimumY, normalizedY);
            maximumX = Math.max(maximumX, normalizedX);
            maximumY = Math.max(maximumY, normalizedY);
            int quadrant = (normalizedY >= 0.5f ? 2 : 0)
                    + (normalizedX >= 0.5f ? 1 : 0);
            quadrants[quadrant] = true;
        }
        int occupiedQuadrants = 0;
        for (boolean occupied : quadrants) if (occupied) occupiedQuadrants++;
        float coverage = inliers == 0 ? 0f
                : Math.max(0f, maximumX - minimumX)
                * Math.max(0f, maximumY - minimumY);
        float meanResidual = inliers == 0
                ? Float.POSITIVE_INFINITY : residualSum / inliers;
        float inlierRatio = inliers / (float) totalSamples;
        float texture = Math.min(1f, matches.size() / (float) totalSamples);
        float residualScore = Float.isFinite(meanResidual)
                ? Math.max(0f, 1f - meanResidual / 4f) : 0f;
        float coherence = 0.35f * Math.min(1f, inlierRatio / 0.55f)
                + 0.25f * Math.min(1f, coverage / 0.60f)
                + 0.20f * (occupiedQuadrants / 4f)
                + 0.20f * residualScore;
        return new FrameMotionQuality(
                totalSamples,
                inliers,
                coverage,
                occupiedQuadrants,
                meanResidual,
                texture,
                coherence
        );
    }

    private static boolean insideForeground(
            float normalizedX,
            float normalizedY,
            List<NormalizedBounds> masks
    ) {
        if (masks == null || masks.isEmpty()) return false;
        final float margin = 0.025f;
        for (NormalizedBounds mask : masks) {
            if (mask == null || !mask.valid()) continue;
            if (normalizedX >= mask.left - margin
                    && normalizedX <= mask.right + margin
                    && normalizedY >= mask.top - margin
                    && normalizedY <= mask.bottom + margin) {
                return true;
            }
        }
        return false;
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
