package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.VehicleCandidate;

/** Entity-aware pixel crop selected for an MT vehicle-ROI run. */
public final class VehicleRoi {
    public final long entityId;
    public final long vehicleTrackId;
    public final VehicleCandidate candidate;
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public VehicleRoi(
            VehicleCandidate candidate,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            throw new IllegalArgumentException("invalid ROI geometry");
        }
        this.entityId = candidate.entityId;
        this.vehicleTrackId = candidate.vehicleTrackId;
        this.candidate = candidate;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public long area() { return (long) width() * height(); }
}
