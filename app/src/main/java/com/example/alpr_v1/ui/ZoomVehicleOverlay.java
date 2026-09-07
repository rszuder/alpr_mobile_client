package com.example.alpr_v1.ui;

import android.graphics.RectF;
import com.example.alpr_v1.camera.CameraController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optical geometry always derives from the base frame, including when the plate is absorbed. */
public final class ZoomVehicleOverlay {
    private ZoomVehicleOverlay() { }

    public static List<OverlayItem> snapshot(List<OverlayItem> items) {
        List<OverlayItem> vehicles = new ArrayList<>();
        for (OverlayItem item : items) if (item.kind == OverlayItem.Kind.VEHICLE) {
            vehicles.add(new OverlayItem(item.kind, new RectF(item.normalizedBounds),
                    item.normalizedKeypoints, item.label, item.trackId, item.carriedPrediction));
        }
        return Collections.unmodifiableList(vehicles);
    }

    public static List<OverlayItem> atZoom(List<OverlayItem> current, List<OverlayItem> base, float ratio) {
        if (base.isEmpty()) return current;
        List<OverlayItem> result = new ArrayList<>();
        for (OverlayItem item : current) if (item.kind != OverlayItem.Kind.VEHICLE) result.add(item);
        for (OverlayItem item : base) {
            RectF b = item.normalizedBounds;
            RectF scaled = new RectF(CameraController.scaledCoordinate(b.left, ratio),
                    CameraController.scaledCoordinate(b.top, ratio),
                    CameraController.scaledCoordinate(b.right, ratio),
                    CameraController.scaledCoordinate(b.bottom, ratio));
            if (scaled.isEmpty()) continue;
            result.add(new OverlayItem(item.kind, scaled, Collections.emptyList(), item.label,
                    item.trackId, item.carriedPrediction));
        }
        return Collections.unmodifiableList(result);
    }
}
