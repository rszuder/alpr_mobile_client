package com.example.alpr_v1.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    @Test
    public void renderOrderIsExplicitAndStableByTrackId() {
        OverlayItem plate = item(OverlayItem.Kind.PLATE, 9L);
        OverlayItem roi = item(OverlayItem.Kind.VEHICLE_ROI, 2L);
        OverlayItem vehicleB = item(OverlayItem.Kind.VEHICLE, 7L);
        OverlayItem vehicleA = item(OverlayItem.Kind.VEHICLE, 3L);

        List<OverlayItem> ordered = DetectionOverlayView.orderedForRendering(
                Arrays.asList(plate, roi, vehicleB, vehicleA)
        );

        assertEquals(
                Arrays.asList(vehicleA, vehicleB, roi, plate),
                ordered
        );
    }

    private static OverlayItem item(OverlayItem.Kind kind, long trackId) {
        return new OverlayItem(
                kind,
                new RectF(0f, 0f, 1f, 1f),
                Collections.emptyList(),
                "",
                trackId,
                false
        );
    }
}
