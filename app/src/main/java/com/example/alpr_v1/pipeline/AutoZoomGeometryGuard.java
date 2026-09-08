package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.GeometryUtils;
import com.example.alpr_v1.vision.PlateQualityScorer;
import com.example.alpr_v1.vision.Point2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps a pre-zoom quad independent of subsequent MT/OCR confidence. No raw detection is mutated. */
final class AutoZoomGeometryGuard {
    private static final long MAX_AGE_NANOS = 30_000_000_000L;
    private final Map<Long, Reference> baseline = new LinkedHashMap<>();
    private final List<Reference> frozen = new ArrayList<>();
    private float zoomScale = 1f;

    static final class Result {
        final Detection detection;
        final long referenceSourceSequence;
        Result(Detection detection, long sequence) {
            this.detection = detection;
            this.referenceSourceSequence = sequence;
        }
    }

    private static final class Reference {
        final long track, entity, recordedAt;
        final ContinuityStamp stamp;
        final List<Point2> normalized;
        Reference(long track, long entity, List<Point2> points, int width, int height,
                  ContinuityStamp stamp, long now) {
            this.track = track; this.entity = entity; this.stamp = stamp; recordedAt = now;
            normalized = new ArrayList<>();
            for (Point2 p : points) normalized.add(new Point2(p.x / width, p.y / height));
        }
    }

    synchronized void clear() {
        baseline.clear(); frozen.clear(); zoomScale = 1f;
    }

    synchronized String diagnosticState() {
        return "baseline=" + baseline.size() + " frozen=" + frozen.size() + " scale=" + zoomScale;
    }

    synchronized void releaseTarget(String reason) {
        // Scan completion hands the same measured plate to AZ; it is not loss of the target.
        if (!"scan_read_captured".equals(reason) && !"scan_ready_to_finalize".equals(reason)) clear();
    }

    synchronized void remember(long track, long entity, Detection raw, int width, int height,
                               ContinuityStamp stamp, long now) {
        if (zoomScale > 1.001f || track <= 0 || raw.confidence < .5f
                || width <= 0 || height <= 0 || raw.keypoints.size() != 4) return;
        List<Point2> points = GeometryUtils.orderQuad(raw.keypoints);
        if (!valid(points, width, height)) return;
        baseline.values().removeIf(r -> r.stamp.sceneGeneration != stamp.sceneGeneration
                || r.stamp.visualEpoch != stamp.visualEpoch || now - r.recordedAt > MAX_AGE_NANOS);
        baseline.remove(track);
        baseline.put(track, new Reference(track, entity, points, width, height, stamp, now));
        while (baseline.size() > 32) baseline.remove(baseline.keySet().iterator().next());
    }

    synchronized void cameraTransform(float ratio) {
        if (!Float.isFinite(ratio) || ratio < .999f) { clear(); return; }
        if (ratio <= 1.001f) return;
        if (zoomScale <= 1.001f) {
            frozen.clear(); frozen.addAll(baseline.values());
        }
        zoomScale *= ratio;
    }

    synchronized Result select(Detection raw, long track, long entity, int width, int height,
                               ContinuityStamp stamp, long now) {
        Result unchanged = new Result(raw, 0L);
        if (zoomScale <= 1.001f || raw.keypoints.size() != 4 || width <= 0 || height <= 0) return unchanged;
        List<Point2> measured = GeometryUtils.orderQuad(raw.keypoints);
        if (!valid(measured, width, height)) return unchanged;
        Reference match = null;
        List<Point2> expected = null;
        for (Reference r : frozen) {
            if (r.stamp.sceneGeneration != stamp.sceneGeneration || r.stamp.visualEpoch != stamp.visualEpoch
                    || stamp.cameraTransformGeneration <= r.stamp.cameraTransformGeneration
                    || now < r.recordedAt || now - r.recordedAt > MAX_AGE_NANOS) continue;
            // Track churn is allowed only with a known, unchanged vehicle owner.
            if (r.entity > 0 && entity > 0 ? r.entity != entity : r.track != track) continue;
            List<Point2> projected = new ArrayList<>();
            for (Point2 p : r.normalized) projected.add(new Point2(
                    (.5f + (p.x - .5f) * zoomScale) * width,
                    (.5f + (p.y - .5f) * zoomScale) * height));
            // Never manufacture a quad clipped at the sensor edge.
            if (!valid(projected, width, height) || !samePosition(projected, measured)) continue;
            if (match != null) return unchanged; // Two plausible plates: no geometric identity guess.
            match = r; expected = projected;
        }
        if (match == null || !lostCoverage(expected, measured)) return unchanged;
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = 0f, bottom = 0f;
        for (Point2 p : expected) {
            left = Math.min(left, p.x); top = Math.min(top, p.y);
            right = Math.max(right, p.x); bottom = Math.max(bottom, p.y);
        }
        return new Result(new Detection(raw.classId, raw.confidence, left, top, right, bottom, expected),
                match.stamp.sourceSequence);
    }

    private static boolean valid(List<Point2> points, int width, int height) {
        for (Point2 p : points) if (!Float.isFinite(p.x) || !Float.isFinite(p.y)
                || p.x < 0f || p.y < 0f || p.x > width || p.y > height) return false;
        return PlateQualityScorer.isValidQuad(points);
    }

    private static boolean samePosition(List<Point2> expected, List<Point2> measured) {
        float w = quadWidth(expected), h = GeometryUtils.estimatedHeight(expected);
        Point2 a = center(expected), b = center(measured);
        float widthRatio = quadWidth(measured) / w;
        float heightRatio = GeometryUtils.estimatedHeight(measured) / h;
        float ex = expected.get(1).x - expected.get(0).x, ey = expected.get(1).y - expected.get(0).y;
        float mx = measured.get(1).x - measured.get(0).x, my = measured.get(1).y - measured.get(0).y;
        double orientation = (ex * mx + ey * my) / (Math.hypot(ex, ey) * Math.hypot(mx, my));
        return widthRatio >= .65f && widthRatio <= 1.35f && heightRatio >= .65f && heightRatio <= 1.65f
                && Math.abs(a.x - b.x) <= w * .16f && Math.abs(a.y - b.y) <= h * .55f
                && orientation >= .985;
    }

    private static boolean lostCoverage(List<Point2> expected, List<Point2> measured) {
        // Require one stable side and a retreat on the opposite side. Uniform scale change can
        // be real vehicle motion; do not expand such a detection to an old size.
        float width = quadWidth(expected);
        if (quadWidth(measured) >= width * .94f) return false;
        float dx = expected.get(1).x - expected.get(0).x, dy = expected.get(1).y - expected.get(0).y;
        float length = (float) Math.hypot(dx, dy);
        float ux = dx / length, uy = dy / length;
        float leftInset = (projection(measured.get(0), ux, uy) + projection(measured.get(3), ux, uy)
                - projection(expected.get(0), ux, uy) - projection(expected.get(3), ux, uy)) * .5f;
        float rightInset = (projection(expected.get(1), ux, uy) + projection(expected.get(2), ux, uy)
                - projection(measured.get(1), ux, uy) - projection(measured.get(2), ux, uy)) * .5f;
        return leftInset > width * .065f && Math.abs(rightInset) <= width * .035f
                || rightInset > width * .065f && Math.abs(leftInset) <= width * .035f;
    }

    private static float projection(Point2 p, float ux, float uy) { return p.x * ux + p.y * uy; }

    private static float quadWidth(List<Point2> p) {
        return (float) ((Math.hypot(p.get(1).x - p.get(0).x, p.get(1).y - p.get(0).y)
                + Math.hypot(p.get(2).x - p.get(3).x, p.get(2).y - p.get(3).y)) * .5);
    }

    private static Point2 center(List<Point2> points) {
        float x = 0, y = 0;
        for (Point2 p : points) { x += p.x; y += p.y; }
        return new Point2(x * .25f, y * .25f);
    }
}
