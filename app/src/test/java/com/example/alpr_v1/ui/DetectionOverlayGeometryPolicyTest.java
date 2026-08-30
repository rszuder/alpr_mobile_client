package com.example.alpr_v1.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DetectionOverlayGeometryPolicyTest {
    @Test
    public void onlyPlateGeometryIsInterpolated() {
        assertTrue(DetectionOverlayView.shouldInterpolateGeometry(
                OverlayItem.Kind.PLATE
        ));
        assertFalse(DetectionOverlayView.shouldInterpolateGeometry(
                OverlayItem.Kind.VEHICLE
        ));
        assertFalse(DetectionOverlayView.shouldInterpolateGeometry(
                OverlayItem.Kind.VEHICLE_ROI
        ));
    }
}
