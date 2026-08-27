package com.example.alpr_v1.tracking;

import android.graphics.RectF;

import com.example.alpr_v1.ui.OverlayItem;

/** Techniczny, niemutowalny wynik lekkiego trackera PreviewView. */
public final class TrackedPlate {
    public final long trackId;
    public final OverlayItem overlayItem;
    public final RectF normalizedBounds;
    public final float trackingQuality;
    public final float supportRatio;
    public final float meanMatchError;
    public final int trackerInliers;
    public final int consecutiveFailures;
    public final int ageFrames;
    public final int framesSinceMtAnchor;
    public final long updatedAtNanos;

    TrackedPlate(
            OverlayItem overlayItem,
            float trackingQuality,
            float supportRatio,
            float meanMatchError,
            int trackerInliers,
            int consecutiveFailures,
            int ageFrames,
            int framesSinceMtAnchor,
            long updatedAtNanos
    ) {
        this.overlayItem = overlayItem;
        this.trackId = overlayItem == null ? 0L : overlayItem.trackId;
        this.normalizedBounds = overlayItem == null
                ? new RectF()
                : new RectF(overlayItem.normalizedBounds);
        this.trackingQuality = clamp01(trackingQuality);
        this.supportRatio = clamp01(supportRatio);
        this.meanMatchError = Math.max(0f, meanMatchError);
        this.trackerInliers = Math.max(0, trackerInliers);
        this.consecutiveFailures = Math.max(0, consecutiveFailures);
        this.ageFrames = Math.max(0, ageFrames);
        this.framesSinceMtAnchor = Math.max(0, framesSinceMtAnchor);
        this.updatedAtNanos = Math.max(0L, updatedAtNanos);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
