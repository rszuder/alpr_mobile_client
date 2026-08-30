package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.ui.OverlayItem;

/** Entity-bound plate anchor owned by the active short Scan session. */
public final class PlateAnchor {
    public final long entityId;
    public final long vehicleTrackId;
    public final long plateTrackId;
    public final OverlayItem overlay;
    public final float[] appearance;
    public final ContinuityStamp continuityStamp;
    public final long acquisitionDirectiveRevision;

    public PlateAnchor(
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            OverlayItem overlay,
            float[] appearance,
            ContinuityStamp continuityStamp,
            long acquisitionDirectiveRevision
    ) {
        if (entityId <= 0L) throw new IllegalArgumentException("entityId");
        if (vehicleTrackId <= 0L) throw new IllegalArgumentException("vehicleTrackId");
        if (plateTrackId <= 0L) throw new IllegalArgumentException("plateTrackId");
        if (continuityStamp == null) throw new IllegalArgumentException("continuityStamp");
        this.entityId = entityId;
        this.vehicleTrackId = vehicleTrackId;
        this.plateTrackId = plateTrackId;
        this.overlay = overlay;
        this.appearance = appearance == null ? null : appearance.clone();
        this.continuityStamp = continuityStamp;
        this.acquisitionDirectiveRevision = Math.max(
                0L, acquisitionDirectiveRevision
        );
    }
}
