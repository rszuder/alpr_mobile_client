package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

/** Jedyny mapper współrzędnych obrazu analizy do widoku Preview FIT_CENTER. */
public final class OverlayViewportTransform {
    private OverlayViewportTransform() {
    }

    public static RectF mapNormalizedToView(
            RectF normalized,
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        if (normalized == null) return new RectF();
        PointF topLeft = mapNormalizedToView(
                normalized.left,
                normalized.top,
                sourceWidth,
                sourceHeight,
                viewWidth,
                viewHeight
        );
        PointF bottomRight = mapNormalizedToView(
                normalized.right,
                normalized.bottom,
                sourceWidth,
                sourceHeight,
                viewWidth,
                viewHeight
        );
        return new RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y);
    }

    public static PointF mapNormalizedToView(
            PointF normalized,
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        if (normalized == null) return new PointF();
        return mapNormalizedToView(
                normalized.x,
                normalized.y,
                sourceWidth,
                sourceHeight,
                viewWidth,
                viewHeight
        );
    }

    public static PointF mapNormalizedToView(
            float normalizedX,
            float normalizedY,
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        Geometry geometry = geometry(sourceWidth, sourceHeight, viewWidth, viewHeight);
        if (!geometry.valid) return new PointF();
        return new PointF(
                geometry.offsetX + normalizedX * sourceWidth * geometry.scale,
                geometry.offsetY + normalizedY * sourceHeight * geometry.scale
        );
    }

    public static PointF mapViewToNormalized(
            PointF viewPoint,
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        if (viewPoint == null) return new PointF();
        Geometry geometry = geometry(sourceWidth, sourceHeight, viewWidth, viewHeight);
        if (!geometry.valid) return new PointF();
        return new PointF(
                clamp01((viewPoint.x - geometry.offsetX)
                        / (sourceWidth * geometry.scale)),
                clamp01((viewPoint.y - geometry.offsetY)
                        / (sourceHeight * geometry.scale))
        );
    }

    private static Geometry geometry(
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0
                || viewWidth <= 0 || viewHeight <= 0) {
            return Geometry.invalid();
        }
        float scale = Math.min(
                viewWidth / (float) sourceWidth,
                viewHeight / (float) sourceHeight
        );
        return new Geometry(
                scale,
                (viewWidth - sourceWidth * scale) * 0.5f,
                (viewHeight - sourceHeight * scale) * 0.5f,
                true
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class Geometry {
        final float scale;
        final float offsetX;
        final float offsetY;
        final boolean valid;

        Geometry(float scale, float offsetX, float offsetY, boolean valid) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.valid = valid;
        }

        static Geometry invalid() {
            return new Geometry(0f, 0f, 0f, false);
        }
    }
}
