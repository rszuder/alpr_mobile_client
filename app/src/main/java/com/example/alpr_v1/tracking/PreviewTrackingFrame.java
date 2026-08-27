package com.example.alpr_v1.tracking;

import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wynik jednej aktualizacji trackera, używany równocześnie przez UI i scheduler. */
public final class PreviewTrackingFrame {
    public final List<TrackedPlate> trackedPlates;
    public final List<OverlayItem> overlayItems;
    public final long updatedAtNanos;

    PreviewTrackingFrame(List<TrackedPlate> trackedPlates, long updatedAtNanos) {
        List<TrackedPlate> safe = trackedPlates == null
                ? Collections.emptyList()
                : new ArrayList<>(trackedPlates);
        this.trackedPlates = Collections.unmodifiableList(safe);
        List<OverlayItem> overlays = new ArrayList<>(safe.size());
        for (TrackedPlate plate : safe) {
            if (plate != null && plate.overlayItem != null) overlays.add(plate.overlayItem);
        }
        this.overlayItems = Collections.unmodifiableList(overlays);
        this.updatedAtNanos = Math.max(0L, updatedAtNanos);
    }
}
