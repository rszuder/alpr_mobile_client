package com.example.alpr_v1.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lekka implementacja pyramidalnego Lucas–Kanade dla niewielkiej liczby punktów. */
final class SparsePyramidalFlow {
    static final class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class Match {
        final int sourceIndex;
        final Point source;
        final Point target;
        final float error;
        final float forwardBackwardError;

        Match(
                int sourceIndex,
                Point source,
                Point target,
                float error,
                float forwardBackwardError
        ) {
            this.sourceIndex = sourceIndex;
            this.source = source;
            this.target = target;
            this.error = error;
            this.forwardBackwardError = forwardBackwardError;
        }
    }

    static final class Result {
        final List<Match> matches;
        final float supportRatio;
        final float meanError;
        final float meanForwardBackwardError;

        Result(List<Match> matches, int requestedPoints) {
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
            this.supportRatio = requestedPoints <= 0
                    ? 0f : matches.size() / (float) requestedPoints;
            float errorSum = 0f;
            float fbSum = 0f;
            for (Match match : matches) {
                errorSum += match.error;
                fbSum += match.forwardBackwardError;
            }
            this.meanError = matches.isEmpty()
                    ? Float.POSITIVE_INFINITY : errorSum / matches.size();
            this.meanForwardBackwardError = matches.isEmpty()
                    ? Float.POSITIVE_INFINITY : fbSum / matches.size();
        }
    }

    private static final int PYRAMID_LEVELS = 3;
    private static final int WINDOW_RADIUS = 2;
    private static final int ITERATIONS = 4;
    private static final float MINIMUM_EIGENVALUE = 8f;
    private static final float MAXIMUM_MEAN_ERROR = 32f;
    private static final float MAXIMUM_FORWARD_BACKWARD_ERROR = 2.2f;
    private static final float MAXIMUM_UPDATE = 3.5f;

    private SparsePyramidalFlow() {}

    static Result track(
            byte[] previous,
            byte[] current,
            int width,
            int height,
            List<Point> points
    ) {
        return track(previous, current, width, height, points, true);
    }

    static Result track(
            byte[] previous,
            byte[] current,
            int width,
            int height,
            List<Point> points,
            boolean validateForwardBackward
    ) {
        if (previous == null || current == null || points == null
                || width < 16 || height < 16
                || previous.length < width * height
                || current.length < width * height) {
            return new Result(Collections.emptyList(), points == null ? 0 : points.size());
        }
        Pyramid first = Pyramid.build(previous, width, height, PYRAMID_LEVELS);
        Pyramid second = Pyramid.build(current, width, height, PYRAMID_LEVELS);
        List<Match> matches = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            Point source = points.get(index);
            Track forward = trackPoint(first, second, source, null);
            if (!forward.valid || forward.error > MAXIMUM_MEAN_ERROR) continue;
            float fbError = 0f;
            if (validateForwardBackward) {
                Track backward = trackPoint(
                        second,
                        first,
                        forward.point,
                        new Point(-forward.dx, -forward.dy)
                );
                if (!backward.valid) continue;
                float fbDx = backward.point.x - source.x;
                float fbDy = backward.point.y - source.y;
                fbError = (float) Math.sqrt(fbDx * fbDx + fbDy * fbDy);
                if (fbError > MAXIMUM_FORWARD_BACKWARD_ERROR) continue;
            }
            matches.add(new Match(index, source, forward.point, forward.error, fbError));
        }
        return new Result(matches, points.size());
    }

    private static Track trackPoint(
            Pyramid previous,
            Pyramid current,
            Point source,
            Point initialFlow
    ) {
        float flowX = initialFlow == null ? 0f : initialFlow.x;
        float flowY = initialFlow == null ? 0f : initialFlow.y;
        for (int level = previous.levels.size() - 1; level >= 0; level--) {
            Image first = previous.levels.get(level);
            Image second = current.levels.get(level);
            float scale = 1 << level;
            float sourceX = source.x / scale;
            float sourceY = source.y / scale;
            float levelFlowX = flowX / scale;
            float levelFlowY = flowY / scale;
            if (!inside(first, sourceX, sourceY, WINDOW_RADIUS + 1)) {
                return Track.invalid();
            }
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                float targetX = sourceX + levelFlowX;
                float targetY = sourceY + levelFlowY;
                if (!inside(second, targetX, targetY, WINDOW_RADIUS + 1)) {
                    return Track.invalid();
                }
                float gxx = 0f;
                float gxy = 0f;
                float gyy = 0f;
                float bx = 0f;
                float by = 0f;
                for (int wy = -WINDOW_RADIUS; wy <= WINDOW_RADIUS; wy++) {
                    for (int wx = -WINDOW_RADIUS; wx <= WINDOW_RADIUS; wx++) {
                        float oldValue = sample(first, sourceX + wx, sourceY + wy);
                        float newValue = sample(second, targetX + wx, targetY + wy);
                        float gradientX = 0.5f * (
                                sample(second, targetX + wx + 1f, targetY + wy)
                                        - sample(second, targetX + wx - 1f, targetY + wy)
                        );
                        float gradientY = 0.5f * (
                                sample(second, targetX + wx, targetY + wy + 1f)
                                        - sample(second, targetX + wx, targetY + wy - 1f)
                        );
                        float residual = oldValue - newValue;
                        gxx += gradientX * gradientX;
                        gxy += gradientX * gradientY;
                        gyy += gradientY * gradientY;
                        bx += gradientX * residual;
                        by += gradientY * residual;
                    }
                }
                float determinant = gxx * gyy - gxy * gxy;
                float trace = gxx + gyy;
                float discriminant = Math.max(0f, trace * trace - 4f * determinant);
                float minimumEigenvalue = 0.5f * (trace - (float) Math.sqrt(discriminant));
                if (determinant < 1e-4f || minimumEigenvalue < MINIMUM_EIGENVALUE) {
                    return Track.invalid();
                }
                float deltaX = (gyy * bx - gxy * by) / determinant;
                float deltaY = (gxx * by - gxy * bx) / determinant;
                if (!Float.isFinite(deltaX) || !Float.isFinite(deltaY)
                        || Math.abs(deltaX) > MAXIMUM_UPDATE
                        || Math.abs(deltaY) > MAXIMUM_UPDATE) {
                    return Track.invalid();
                }
                levelFlowX += deltaX;
                levelFlowY += deltaY;
                if (deltaX * deltaX + deltaY * deltaY < 0.0025f) break;
            }
            flowX = levelFlowX * scale;
            flowY = levelFlowY * scale;
        }
        Point target = new Point(source.x + flowX, source.y + flowY);
        Image first = previous.levels.get(0);
        Image second = current.levels.get(0);
        if (!inside(first, source.x, source.y, WINDOW_RADIUS)
                || !inside(second, target.x, target.y, WINDOW_RADIUS)) {
            return Track.invalid();
        }
        float error = 0f;
        int samples = 0;
        for (int wy = -WINDOW_RADIUS; wy <= WINDOW_RADIUS; wy++) {
            for (int wx = -WINDOW_RADIUS; wx <= WINDOW_RADIUS; wx++) {
                error += Math.abs(
                        sample(first, source.x + wx, source.y + wy)
                                - sample(second, target.x + wx, target.y + wy)
                );
                samples++;
            }
        }
        return new Track(true, target, flowX, flowY, error / Math.max(1, samples));
    }

    private static boolean inside(Image image, float x, float y, int margin) {
        return x >= margin && y >= margin
                && x < image.width - margin - 1
                && y < image.height - margin - 1;
    }

    private static float sample(Image image, float x, float y) {
        int left = (int) Math.floor(x);
        int top = (int) Math.floor(y);
        float dx = x - left;
        float dy = y - top;
        int right = Math.min(image.width - 1, left + 1);
        int bottom = Math.min(image.height - 1, top + 1);
        left = Math.max(0, left);
        top = Math.max(0, top);
        float topValue = value(image, left, top) * (1f - dx)
                + value(image, right, top) * dx;
        float bottomValue = value(image, left, bottom) * (1f - dx)
                + value(image, right, bottom) * dx;
        return topValue * (1f - dy) + bottomValue * dy;
    }

    private static float value(Image image, int x, int y) {
        return image.gray[y * image.width + x] & 0xff;
    }

    private static final class Track {
        final boolean valid;
        final Point point;
        final float dx;
        final float dy;
        final float error;

        Track(boolean valid, Point point, float dx, float dy, float error) {
            this.valid = valid;
            this.point = point;
            this.dx = dx;
            this.dy = dy;
            this.error = error;
        }

        static Track invalid() {
            return new Track(false, new Point(0f, 0f), 0f, 0f, Float.POSITIVE_INFINITY);
        }
    }

    private static final class Image {
        final byte[] gray;
        final int width;
        final int height;

        Image(byte[] gray, int width, int height) {
            this.gray = gray;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Pyramid {
        final List<Image> levels;

        Pyramid(List<Image> levels) {
            this.levels = levels;
        }

        static Pyramid build(byte[] gray, int width, int height, int requestedLevels) {
            List<Image> levels = new ArrayList<>();
            levels.add(new Image(gray, width, height));
            while (levels.size() < requestedLevels) {
                Image previous = levels.get(levels.size() - 1);
                if (previous.width < 24 || previous.height < 24) break;
                int nextWidth = Math.max(1, previous.width / 2);
                int nextHeight = Math.max(1, previous.height / 2);
                byte[] next = new byte[nextWidth * nextHeight];
                for (int y = 0; y < nextHeight; y++) {
                    for (int x = 0; x < nextWidth; x++) {
                        int sourceX = x * 2;
                        int sourceY = y * 2;
                        float sum = value(previous, sourceX, sourceY)
                                + value(previous, Math.min(previous.width - 1, sourceX + 1), sourceY)
                                + value(previous, sourceX, Math.min(previous.height - 1, sourceY + 1))
                                + value(previous,
                                Math.min(previous.width - 1, sourceX + 1),
                                Math.min(previous.height - 1, sourceY + 1));
                        next[y * nextWidth + x] = (byte) Math.round(sum / 4f);
                    }
                }
                levels.add(new Image(next, nextWidth, nextHeight));
            }
            return new Pyramid(Collections.unmodifiableList(levels));
        }
    }
}
