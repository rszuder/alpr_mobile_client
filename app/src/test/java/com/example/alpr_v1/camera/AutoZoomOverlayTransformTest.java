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
}
