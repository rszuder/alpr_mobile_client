package com.example.alpr_v1.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Estymuje transformację affine z inlierów sparse flow i pilnuje geometrii quada. */
final class RobustAffineTransform {
    static final class Result {
        final boolean valid;
        final float a;
        final float b;
        final float tx;
        final float c;
        final float d;
        final float ty;
        final int inlierCount;
        final float meanError;

        Result(
                boolean valid,
                float a,
                float b,
                float tx,
                float c,
                float d,
                float ty,
                int inlierCount,
                float meanError
        ) {
            this.valid = valid;
            this.a = a;
            this.b = b;
            this.tx = tx;
            this.c = c;
            this.d = d;
            this.ty = ty;
            this.inlierCount = Math.max(0, inlierCount);
            this.meanError = Math.max(0f, meanError);
        }

        SparsePyramidalFlow.Point apply(SparsePyramidalFlow.Point point) {
            return new SparsePyramidalFlow.Point(
                    a * point.x + b * point.y + tx,
                    c * point.x + d * point.y + ty
            );
        }

        Result compose(Result previous) {
            if (!valid || previous == null || !previous.valid) return invalid();
            return new Result(
                    true,
                    a * previous.a + b * previous.c,
                    a * previous.b + b * previous.d,
                    a * previous.tx + b * previous.ty + tx,
                    c * previous.a + d * previous.c,
                    c * previous.b + d * previous.d,
                    c * previous.tx + d * previous.ty + ty,
                    Math.min(inlierCount, previous.inlierCount),
                    Math.max(meanError, previous.meanError)
            );
        }

        float determinant() {
            return a * d - b * c;
        }

        static Result identity() {
            return new Result(true, 1f, 0f, 0f, 0f, 1f, 0f, Integer.MAX_VALUE, 0f);
        }

        static Result invalid() {
            return new Result(false, 1f, 0f, 0f, 0f, 1f, 0f, 0, Float.POSITIVE_INFINITY);
        }
    }

    private static final float INLIER_ERROR = 2.6f;
    private static final int MINIMUM_INLIERS = 4;

    private RobustAffineTransform() {}

    static Result estimate(List<SparsePyramidalFlow.Match> matches) {
        if (matches == null || matches.size() < 3) return Result.invalid();
        Result best = Result.invalid();
        int bestInliers = 0;
        float bestError = Float.POSITIVE_INFINITY;
        int evaluated = 0;
        for (int first = 0; first < matches.size() - 2; first++) {
            for (int second = first + 1; second < matches.size() - 1; second++) {
                for (int third = second + 1; third < matches.size(); third++) {
                    if (evaluated++ >= 320) break;
                    Result candidate = fit(matches, new int[]{first, second, third});
                    if (!plausible(candidate)) continue;
                    Score score = score(candidate, matches);
                    if (score.inliers > bestInliers
                            || (score.inliers == bestInliers && score.error < bestError)) {
                        best = candidate;
                        bestInliers = score.inliers;
                        bestError = score.error;
                    }
                }
                if (evaluated >= 320) break;
            }
            if (evaluated >= 320) break;
        }
        if (!best.valid || bestInliers < Math.min(MINIMUM_INLIERS, matches.size())) {
            return Result.invalid();
        }
        List<Integer> inliers = inlierIndices(best, matches);
        Result refined = fit(matches, toArray(inliers));
        if (!plausible(refined)) return Result.invalid();
        Score refinedScore = score(refined, matches);
        if (refinedScore.inliers < Math.min(MINIMUM_INLIERS, matches.size())) {
            return Result.invalid();
        }
        return new Result(
                true,
                refined.a, refined.b, refined.tx,
                refined.c, refined.d, refined.ty,
                refinedScore.inliers,
                refinedScore.error
        );
    }

    static boolean reasonableQuad(
            Result transform,
            List<SparsePyramidalFlow.Point> quad,
            int width,
            int height
    ) {
        if (!plausible(transform) || quad == null || quad.size() < 4) return false;
        List<SparsePyramidalFlow.Point> source = quad.subList(0, 4);
        List<SparsePyramidalFlow.Point> target = new ArrayList<>(4);
        for (SparsePyramidalFlow.Point point : source) target.add(transform.apply(point));
        float sourceArea = signedArea(source);
        float targetArea = signedArea(target);
        if (sourceArea == 0f || targetArea == 0f || sourceArea * targetArea <= 0f) return false;
        float areaRatio = Math.abs(targetArea / sourceArea);
        if (areaRatio < 0.35f || areaRatio > 2.8f) return false;
        float marginX = width * 0.08f;
        float marginY = height * 0.08f;
        for (SparsePyramidalFlow.Point point : target) {
            if (point.x < -marginX || point.x > width + marginX
                    || point.y < -marginY || point.y > height + marginY) return false;
        }
        float sourceCenterX = centerX(source);
        float sourceCenterY = centerY(source);
        float targetCenterX = centerX(target);
        float targetCenterY = centerY(target);
        float centerDx = targetCenterX - sourceCenterX;
        float centerDy = targetCenterY - sourceCenterY;
        float maximumJump = Math.max(width, height) * 0.30f;
        return centerDx * centerDx + centerDy * centerDy <= maximumJump * maximumJump;
    }

    private static Result fit(
            List<SparsePyramidalFlow.Match> matches,
            int[] indices
    ) {
        if (indices == null || indices.length < 3) return Result.invalid();
        double sxx = 0.0;
        double sxy = 0.0;
        double sx = 0.0;
        double syy = 0.0;
        double sy = 0.0;
        double txX = 0.0;
        double tyX = 0.0;
        double tX = 0.0;
        double txY = 0.0;
        double tyY = 0.0;
        double tY = 0.0;
        for (int index : indices) {
            SparsePyramidalFlow.Match match = matches.get(index);
            double x = match.source.x;
            double y = match.source.y;
            double targetX = match.target.x;
            double targetY = match.target.y;
            sxx += x * x;
            sxy += x * y;
            sx += x;
            syy += y * y;
            sy += y;
            txX += x * targetX;
            tyX += y * targetX;
            tX += targetX;
            txY += x * targetY;
            tyY += y * targetY;
            tY += targetY;
        }
        double[][] normal = {
                {sxx, sxy, sx},
                {sxy, syy, sy},
                {sx, sy, indices.length}
        };
        double[] horizontal = solve3(normal, new double[]{txX, tyX, tX});
        double[] vertical = solve3(normal, new double[]{txY, tyY, tY});
        if (horizontal == null || vertical == null) return Result.invalid();
        return new Result(
                true,
                (float) horizontal[0],
                (float) horizontal[1],
                (float) horizontal[2],
                (float) vertical[0],
                (float) vertical[1],
                (float) vertical[2],
                indices.length,
                0f
        );
    }

    private static double[] solve3(double[][] source, double[] rhs) {
        double[][] matrix = new double[3][4];
        for (int row = 0; row < 3; row++) {
            System.arraycopy(source[row], 0, matrix[row], 0, 3);
            matrix[row][3] = rhs[row];
        }
        for (int pivot = 0; pivot < 3; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < 3; row++) {
                if (Math.abs(matrix[row][pivot]) > Math.abs(matrix[best][pivot])) best = row;
            }
            if (Math.abs(matrix[best][pivot]) < 1e-8) return null;
            double[] swap = matrix[pivot];
            matrix[pivot] = matrix[best];
            matrix[best] = swap;
            double divisor = matrix[pivot][pivot];
            for (int column = pivot; column < 4; column++) matrix[pivot][column] /= divisor;
            for (int row = 0; row < 3; row++) {
                if (row == pivot) continue;
                double factor = matrix[row][pivot];
                for (int column = pivot; column < 4; column++) {
                    matrix[row][column] -= factor * matrix[pivot][column];
                }
            }
        }
        return new double[]{matrix[0][3], matrix[1][3], matrix[2][3]};
    }

    private static boolean plausible(Result result) {
        if (result == null || !result.valid) return false;
        float determinant = result.determinant();
        if (!Float.isFinite(determinant) || determinant < 0.30f || determinant > 3.0f) {
            return false;
        }
        float firstScale = (float) Math.sqrt(result.a * result.a + result.c * result.c);
        float secondScale = (float) Math.sqrt(result.b * result.b + result.d * result.d);
        if (firstScale < 0.50f || firstScale > 1.85f
                || secondScale < 0.50f || secondScale > 1.85f) return false;
        float normalizedShear = Math.abs(result.a * result.b + result.c * result.d)
                / Math.max(0.001f, firstScale * secondScale);
        return normalizedShear <= 0.60f;
    }

    private static Score score(Result result, List<SparsePyramidalFlow.Match> matches) {
        int inliers = 0;
        float errorSum = 0f;
        for (SparsePyramidalFlow.Match match : matches) {
            float error = error(result, match);
            if (error <= INLIER_ERROR) {
                inliers++;
                errorSum += error;
            }
        }
        return new Score(inliers, inliers == 0 ? Float.POSITIVE_INFINITY : errorSum / inliers);
    }

    private static List<Integer> inlierIndices(
            Result result,
            List<SparsePyramidalFlow.Match> matches
    ) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            if (error(result, matches.get(index)) <= INLIER_ERROR) indices.add(index);
        }
        return Collections.unmodifiableList(indices);
    }

    private static float error(Result result, SparsePyramidalFlow.Match match) {
        SparsePyramidalFlow.Point predicted = result.apply(match.source);
        float dx = predicted.x - match.target.x;
        float dy = predicted.y - match.target.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int[] toArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) result[index] = values.get(index);
        return result;
    }

    private static float signedArea(List<SparsePyramidalFlow.Point> points) {
        float area = 0f;
        for (int index = 0; index < points.size(); index++) {
            SparsePyramidalFlow.Point current = points.get(index);
            SparsePyramidalFlow.Point next = points.get((index + 1) % points.size());
            area += current.x * next.y - next.x * current.y;
        }
        return area * 0.5f;
    }

    private static float centerX(List<SparsePyramidalFlow.Point> points) {
        float sum = 0f;
        for (SparsePyramidalFlow.Point point : points) sum += point.x;
        return sum / Math.max(1, points.size());
    }

    private static float centerY(List<SparsePyramidalFlow.Point> points) {
        float sum = 0f;
        for (SparsePyramidalFlow.Point point : points) sum += point.y;
        return sum / Math.max(1, points.size());
    }

    private static final class Score {
        final int inliers;
        final float error;

        Score(int inliers, float error) {
            this.inliers = inliers;
            this.error = error;
        }
    }
}
