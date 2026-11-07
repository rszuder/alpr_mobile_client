package com.example.alpr_v1.ui;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.vision.GeometryUtils;
import com.example.alpr_v1.vision.PlateQualityScorer;
import com.example.alpr_v1.vision.Point2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owned presentation geometry. Raw MT boxes and recognition geometry are never modified. */
final class PlateDisplayGeometry {
    final int sourceWidth, sourceHeight;
    final NormalizedBounds bounds;
    final List<Point2> cornersNorm;

    private PlateDisplayGeometry(int width, int height, NormalizedBounds bounds, List<Point2> corners) {
        sourceWidth = width; sourceHeight = height; this.bounds = bounds;
        cornersNorm = Collections.unmodifiableList(new ArrayList<>(corners));
    }

    static PlateDisplayGeometry fromRecognition(PlateGeometry raw) {
        PlateGeometry crop = raw == null ? PlateGeometry.unavailable() : raw.forRecognition();
        float width = Math.max(1, crop.sourceWidthPx), height = Math.max(1, crop.sourceHeightPx);
        return fromNormalized(crop.cornersNorm, new NormalizedBounds(
                crop.bboxLeftPx / width, crop.bboxTopPx / height,
                crop.bboxRightPx / width, crop.bboxBottomPx / height),
                crop.sourceWidthPx, crop.sourceHeightPx);
    }

    static PlateDisplayGeometry fromNormalized(List<Point2> corners, NormalizedBounds fallback,
                                               int width, int height) {
        List<Point2> pixels = new ArrayList<>(4);
        if (width > 0 && height > 0 && corners != null && corners.size() == 4) {
            for (Point2 point : corners) {
                if (point == null || !Float.isFinite(point.x) || !Float.isFinite(point.y)
                        || point.x < 0f || point.x > 1f || point.y < 0f || point.y > 1f) break;
                pixels.add(new Point2(point.x * width, point.y * height, point.confidence));
            }
        }
        if (pixels.size() == 4) {
            List<Point2> ordered = GeometryUtils.orderQuad(pixels);
            if (PlateQualityScorer.isValidQuad(ordered)) {
                List<Point2> normalized = new ArrayList<>(4);
                float left = 1f, top = 1f, right = 0f, bottom = 0f;
                for (Point2 point : ordered) {
                    float x = point.x / width, y = point.y / height;
                    normalized.add(new Point2(x, y, point.confidence));
                    left = Math.min(left, x); top = Math.min(top, y);
                    right = Math.max(right, x); bottom = Math.max(bottom, y);
                }
                return new PlateDisplayGeometry(width, height,
                        new NormalizedBounds(left, top, right, bottom), normalized);
            }
        }
        NormalizedBounds box = fallback == null ? new NormalizedBounds(0f, 0f, 0f, 0f)
                : new NormalizedBounds(fallback.left, fallback.top, fallback.right, fallback.bottom);
        return new PlateDisplayGeometry(width, height, box, Collections.emptyList());
    }

    boolean hasQuad() { return cornersNorm.size() == 4; }
    boolean available() { return sourceWidth > 0 && sourceHeight > 0 && bounds.valid(); }

    /** A new invalid result ends the polygon immediately; a box never becomes an animation endpoint. */
    static List<Point2> interpolate(PlateDisplayGeometry previous, PlateDisplayGeometry target, float amount) {
        if (target == null || !target.hasQuad()) return Collections.emptyList();
        if (previous == null || !previous.hasQuad() || previous.sourceWidth != target.sourceWidth
                || previous.sourceHeight != target.sourceHeight) return target.cornersNorm;
        float t = Math.max(0f, Math.min(1f, amount));
        List<Point2> points = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            Point2 a = previous.cornersNorm.get(i), b = target.cornersNorm.get(i);
            points.add(new Point2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t));
        }
        return fromNormalized(points, target.bounds, target.sourceWidth, target.sourceHeight).cornersNorm;
    }

    /** A bbox-only refresh cannot erase a measured outline or extend its geometry lifetime. */
    boolean mayReplaceWith(PlateDisplayGeometry next) {
        return next != null && next.available() && (!hasQuad() || next.hasQuad());
    }
}
