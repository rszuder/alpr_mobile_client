package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class OverlayViewportTransformInstrumentedTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void landscape169IsLetterboxedOnPortraitView() {
        assertPoint(540f, 1200f, map(0.5f, 0.5f, 1920, 1080, 1080, 2400));
        assertPoint(0f, 896.25f, map(0f, 0f, 1920, 1080, 1080, 2400));
        assertPoint(1080f, 1503.75f, map(1f, 1f, 1920, 1080, 1080, 2400));
    }

    @Test
    public void squareAndPortraitSourcesMapAllCorners() {
        assertCorners(1088, 1088, 1080, 2400);
        assertCorners(720, 1280, 1080, 1920);
    }

    @Test
    public void normalizedViewRoundTripStaysWithinEpsilon() {
        int[][] sizes = new int[][]{
                {1920, 1080, 1080, 2400},
                {1088, 1088, 1080, 2400},
                {720, 1280, 1080, 1920}
        };
        PointF[] points = new PointF[]{
                new PointF(0f, 0f),
                new PointF(1f, 0f),
                new PointF(1f, 1f),
                new PointF(0f, 1f),
                new PointF(0.37f, 0.62f)
        };
        for (int[] size : sizes) {
            for (PointF point : points) {
                PointF view = OverlayViewportTransform.mapNormalizedToView(
                        point, size[0], size[1], size[2], size[3]
                );
                PointF normalized = OverlayViewportTransform.mapViewToNormalized(
                        view, size[0], size[1], size[2], size[3]
                );
                assertPoint(point.x, point.y, normalized);
            }
        }
    }

    @Test
    public void boundingBoxUsesTheSameTransformAsPoints() {
        RectF mapped = OverlayViewportTransform.mapNormalizedToView(
                new RectF(0.25f, 0.25f, 0.75f, 0.75f),
                1920, 1080, 1080, 2400
        );
        assertEquals(270f, mapped.left, EPSILON);
        assertEquals(1048.125f, mapped.top, EPSILON);
        assertEquals(810f, mapped.right, EPSILON);
        assertEquals(1351.875f, mapped.bottom, EPSILON);
    }

    private static void assertCorners(
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        PointF topLeft = map(0f, 0f, sourceWidth, sourceHeight, viewWidth, viewHeight);
        PointF topRight = map(1f, 0f, sourceWidth, sourceHeight, viewWidth, viewHeight);
        PointF bottomRight = map(1f, 1f, sourceWidth, sourceHeight, viewWidth, viewHeight);
        PointF bottomLeft = map(0f, 1f, sourceWidth, sourceHeight, viewWidth, viewHeight);
        assertEquals(topLeft.y, topRight.y, EPSILON);
        assertEquals(bottomLeft.y, bottomRight.y, EPSILON);
        assertEquals(topLeft.x, bottomLeft.x, EPSILON);
        assertEquals(topRight.x, bottomRight.x, EPSILON);
    }

    private static PointF map(
            float x,
            float y,
            int sourceWidth,
            int sourceHeight,
            int viewWidth,
            int viewHeight
    ) {
        return OverlayViewportTransform.mapNormalizedToView(
                new PointF(x, y),
                sourceWidth,
                sourceHeight,
                viewWidth,
                viewHeight
        );
    }

    private static void assertPoint(float x, float y, PointF actual) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
    }
}
