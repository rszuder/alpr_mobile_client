package com.example.alpr_v1.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class AutoZoomOverlayTransformTest {
    @Test
    public void zoomedGeometryReturnsToSameSceneCoordinates() {
        float zoomRatio = 1.8f;
        float sceneCoordinate = 0.62f;

        float zoomed = CameraController.scaledCoordinate(sceneCoordinate, zoomRatio);
        float returned = CameraController.scaledCoordinate(zoomed, 1f / zoomRatio);

        assertEquals(sceneCoordinate, returned, 0.0001f);
    }

    @Test
    public void centeredGeometryDoesNotDriftDuringRoundTrip() {
        float zoomed = CameraController.scaledCoordinate(0.5f, 1.8f);
        float returned = CameraController.scaledCoordinate(zoomed, 1f / 1.8f);

        assertEquals(0.5f, zoomed, 0.0001f);
        assertEquals(0.5f, returned, 0.0001f);
    }

    @Test
    public void centeredPlateKeepsFullRequestedZoom() {
        float ratio = CameraController.centeredZoomKeepingBoundsVisible(
                1.8f,
                0.42f, 0.46f, 0.58f, 0.54f,
                0f, 0f, 1f, 1f,
                0.05f
        );

        assertEquals(1.8f, ratio, 0.0001f);
    }

    @Test
    public void peripheralPlateReducesZoomInsteadOfLeavingFrame() {
        float ratio = CameraController.centeredZoomKeepingBoundsVisible(
                1.8f,
                0.76f, 0.46f, 0.90f, 0.54f,
                0f, 0f, 1f, 1f,
                0.05f
        );

        float transformedRight = 0.5f + ratio * (0.90f - 0.5f);
        assertEquals(1.125f, ratio, 0.0001f);
        assertEquals(0.95f, transformedRight, 0.0001f);
    }

    @Test
    public void leftEdgeMustangPlateStaysFullyVisibleAfterRefinementZoom() {
        // Device reproduction: plate x=115..172 in a 720 px wide preview.
        float left = 115f / 720f;
        float right = 172f / 720f;
        float ratio = CameraController.centeredZoomKeepingBoundsVisible(
                AutoZoomController.REQUESTED_ZOOM_RATIO,
                left, 0.46f, right, 0.54f,
                0f, 0f, 1f, 1f,
                0.05f
        );

        org.junit.Assert.assertTrue(0.5f + 1.8f * (left - 0.5f) < 0f);
        assertEquals(1.322449f, ratio, 0.0001f);
        assertEquals(0.05f, 0.5f + ratio * (left - 0.5f), 0.0001f);
        org.junit.Assert.assertTrue(0.5f + ratio * (right - 0.5f) < 0.95f);
    }

    @Test
    public void previewCropIsIncludedInSafeZoomLimit() {
        float ratio = CameraController.centeredZoomKeepingBoundsVisible(
                1.8f,
                0.44f, 0.70f, 0.56f, 0.78f,
                0f, 0.10f, 1f, 0.90f,
                0.05f
        );

        float transformedBottom = 0.5f + ratio * (0.78f - 0.5f);
        assertEquals(1.2857143f, ratio, 0.0001f);
        assertEquals(0.86f, transformedBottom, 0.0001f);
    }
}
