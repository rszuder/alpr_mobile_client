package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import com.example.alpr_v1.vision.GeometryUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Surowa geometria tablicy w układzie źródłowej klatki. */
public final class PlateGeometry {
    public final int sourceWidthPx;
    public final int sourceHeightPx;
    public final float bboxLeftPx;
    public final float bboxTopPx;
    public final float bboxRightPx;
    public final float bboxBottomPx;
    public final double bboxAreaRatio;
    public final double quadAreaRatio;
    public final List<Point2> cornersNorm;

    private PlateGeometry(
            int sourceWidthPx,
            int sourceHeightPx,
            float bboxLeftPx,
            float bboxTopPx,
            float bboxRightPx,
            float bboxBottomPx,
            double bboxAreaRatio,
            double quadAreaRatio,
            List<Point2> cornersNorm
    ) {
        this.sourceWidthPx = sourceWidthPx;
        this.sourceHeightPx = sourceHeightPx;
        this.bboxLeftPx = bboxLeftPx;
        this.bboxTopPx = bboxTopPx;
        this.bboxRightPx = bboxRightPx;
        this.bboxBottomPx = bboxBottomPx;
        this.bboxAreaRatio = bboxAreaRatio;
        this.quadAreaRatio = quadAreaRatio;
        this.cornersNorm = Collections.unmodifiableList(new ArrayList<>(cornersNorm));
    }

    public static PlateGeometry unavailable() {
        return new PlateGeometry(0, 0, 0f, 0f, 0f, 0f, 0.0, 0.0,
                Collections.emptyList());
    }

    public static PlateGeometry from(
            int sourceWidth,
            int sourceHeight,
            Detection detection,
            List<Point2> corners
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || detection == null) {
            return unavailable();
        }
        double frameArea = (double) sourceWidth * sourceHeight;
        List<Point2> orderedCorners = corners != null && corners.size() == 4
                ? GeometryUtils.orderQuad(corners)
                : corners;
        List<Point2> normalized = new ArrayList<>();
        if (orderedCorners != null) {
            for (Point2 point : orderedCorners) {
                normalized.add(new Point2(
                        clamp(point.x / sourceWidth),
                        clamp(point.y / sourceHeight)
                ));
            }
        }
        double quadAreaPx = polygonArea(orderedCorners);
        return new PlateGeometry(
                sourceWidth,
                sourceHeight,
                detection.left,
                detection.top,
                detection.right,
                detection.bottom,
                detection.width() * detection.height() / frameArea,
                quadAreaPx / frameArea,
                normalized
        );
    }

    public boolean available() {
        return sourceWidthPx > 0 && sourceHeightPx > 0;
    }

    public float bboxWidthPx() { return Math.max(0f, bboxRightPx - bboxLeftPx); }
    public float bboxHeightPx() { return Math.max(0f, bboxBottomPx - bboxTopPx); }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("available", available());
        if (!available()) return json;
        json.put("source_width_px", sourceWidthPx);
        json.put("source_height_px", sourceHeightPx);
        json.put("plate_bbox_left_px", bboxLeftPx);
        json.put("plate_bbox_top_px", bboxTopPx);
        json.put("plate_bbox_right_px", bboxRightPx);
        json.put("plate_bbox_bottom_px", bboxBottomPx);
        json.put("plate_bbox_width_px", bboxWidthPx());
        json.put("plate_bbox_height_px", bboxHeightPx());
        json.put("plate_bbox_area_ratio", bboxAreaRatio);
        json.put("plate_quad_area_ratio", quadAreaRatio);
        JSONArray points = new JSONArray();
        for (Point2 point : cornersNorm) {
            JSONArray pair = new JSONArray();
            pair.put(point.x);
            pair.put(point.y);
            points.put(pair);
        }
        json.put("plate_corners_norm", points);
        return json;
    }

    private static double polygonArea(List<Point2> points) {
        if (points == null || points.size() < 3) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            Point2 current = points.get(i);
            Point2 next = points.get((i + 1) % points.size());
            sum += current.x * next.y - next.x * current.y;
        }
        return Math.abs(sum) * 0.5;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
