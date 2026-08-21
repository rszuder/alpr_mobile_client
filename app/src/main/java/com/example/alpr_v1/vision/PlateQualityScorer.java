package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lekki port geometrycznego fit_score używanego podczas autoanotacji MT. */
public final class PlateQualityScorer {
    public static final class Score {
        public final float total;
        public final float keypoints;
        public final float shape;
        public final float bboxAlignment;
        public final float size;
        public final boolean validQuad;

        private Score(
                float total,
                float keypoints,
                float shape,
                float bboxAlignment,
                float size,
                boolean validQuad
        ) {
            this.total = total;
            this.keypoints = keypoints;
            this.shape = shape;
            this.bboxAlignment = bboxAlignment;
            this.size = size;
            this.validQuad = validQuad;
        }
    }

    private PlateQualityScorer() {}

    public static Score compute(
            Detection detection,
            List<Point2> sourceCorners,
            int imageWidth,
            int imageHeight
    ) {
        List<Point2> ordered;
        try {
            ordered = GeometryUtils.orderQuad(sourceCorners);
        } catch (RuntimeException error) {
            ordered = new ArrayList<>(sourceCorners == null ? java.util.Collections.emptyList() : sourceCorners);
        }

        float keypointScore = keypointScore(detection.confidence, sourceCorners);
        boolean valid = isValidQuad(ordered);
        float polygonArea = polygonArea(ordered);
        float bboxArea = detection.width() * detection.height();
        float fillRatio = bboxArea <= 0f ? 0f : polygonArea / bboxArea;
        float fillScore = ratioScore(fillRatio, 0.62f, 1.02f, 0.30f, 1.15f);

        float sideConsistency = 0f;
        float aspectScore = 0f;
        if (ordered.size() == 4) {
            float top = distance(ordered.get(0), ordered.get(1));
            float right = distance(ordered.get(1), ordered.get(2));
            float bottom = distance(ordered.get(2), ordered.get(3));
            float left = distance(ordered.get(3), ordered.get(0));
            float widthConsistency = Math.min(top, bottom) / Math.max(0.0001f, Math.max(top, bottom));
            float heightConsistency = Math.min(left, right) / Math.max(0.0001f, Math.max(left, right));
            sideConsistency = clamp((widthConsistency + heightConsistency) * 0.5f);
            float averageWidth = (top + bottom) * 0.5f;
            float averageHeight = (left + right) * 0.5f;
            aspectScore = ratioScore(
                    averageWidth / Math.max(0.0001f, averageHeight),
                    1.7f, 7.5f, 1.0f, 10.0f
            );
        }
        float shapeScore = clamp(
                0.15f * (valid ? 1f : 0f)
                        + 0.35f * fillScore
                        + 0.25f * sideConsistency
                        + 0.25f * aspectScore
        );

        Detection polygonBounds = polygonBounds(ordered, detection.confidence);
        float alignmentScore = polygonBounds == null ? 0f : NonMaxSuppression.iou(detection, polygonBounds);
        float relativeArea = polygonArea / Math.max(1f, (float) imageWidth * imageHeight);
        float sizeScore = ratioScore(relativeArea, 0.0015f, 0.18f, 0.0002f, 0.45f);
        float total = clamp(
                0.45f * keypointScore
                        + 0.25f * shapeScore
                        + 0.20f * alignmentScore
                        + 0.10f * sizeScore
        );
        return new Score(total, keypointScore, shapeScore, alignmentScore, sizeScore, valid);
    }

    public static boolean isValidQuad(List<Point2> points) {
        if (points == null || points.size() != 4 || polygonArea(points) < 10f) return false;
        Set<String> unique = new HashSet<>();
        for (Point2 point : points) unique.add(Float.floatToIntBits(point.x) + ":" + Float.floatToIntBits(point.y));
        if (unique.size() != 4) return false;
        float sign = 0f;
        for (int i = 0; i < 4; i++) {
            Point2 a = points.get(i);
            Point2 b = points.get((i + 1) % 4);
            Point2 c = points.get((i + 2) % 4);
            float cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
            if (Math.abs(cross) < 0.001f) return false;
            if (sign == 0f) sign = Math.signum(cross);
            else if (Math.signum(cross) != sign) return false;
        }
        return true;
    }

    private static float keypointScore(float detectionConfidence, List<Point2> points) {
        if (points == null || points.isEmpty()) return clamp(detectionConfidence * 0.85f);
        float sum = 0f;
        float minimum = 1f;
        int count = 0;
        for (Point2 point : points) {
            if (count >= 4) break;
            float confidence = clamp(point.confidence);
            sum += confidence;
            minimum = Math.min(minimum, confidence);
            count++;
        }
        if (count == 0) return clamp(detectionConfidence * 0.85f);
        return clamp(0.7f * (sum / count) + 0.3f * minimum);
    }

    private static float polygonArea(List<Point2> points) {
        if (points == null || points.size() < 3) return 0f;
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            Point2 current = points.get(i);
            Point2 next = points.get((i + 1) % points.size());
            sum += current.x * next.y - current.y * next.x;
        }
        return (float) Math.abs(sum * 0.5);
    }

    private static Detection polygonBounds(List<Point2> points, float confidence) {
        if (points == null || points.size() != 4) return null;
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (Point2 point : points) {
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        return new Detection(0, confidence, left, top, right, bottom, java.util.Collections.emptyList());
    }

    private static float distance(Point2 first, Point2 second) {
        return (float) Math.hypot(first.x - second.x, first.y - second.y);
    }

    private static float ratioScore(
            float value,
            float idealMinimum,
            float idealMaximum,
            float hardMinimum,
            float hardMaximum
    ) {
        if (value <= 0f || value < hardMinimum || value > hardMaximum) return 0f;
        if (value >= idealMinimum && value <= idealMaximum) return 1f;
        if (value < idealMinimum) {
            return clamp((value - hardMinimum) / Math.max(0.000001f, idealMinimum - hardMinimum));
        }
        return clamp((hardMaximum - value) / Math.max(0.000001f, hardMaximum - idealMaximum));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
