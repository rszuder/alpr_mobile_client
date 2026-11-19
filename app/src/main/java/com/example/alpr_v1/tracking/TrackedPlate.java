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
    public final float localAppearanceSimilarity;
    public final boolean localAppearanceValidated;
    public final float[] localAppearanceDescriptor;
    public final long updatedAtNanos;
    public final DynamicVehicleIdentity.Stamp identityOwner;
    public final long anchorSourceTimestampNanos;
    public final com.example.alpr_v1.domain.NormalizedQuad fullReferenceQuad;

    TrackedPlate(
            OverlayItem overlayItem,
            float trackingQuality,
            float supportRatio,
            float meanMatchError,
            int trackerInliers,
            int consecutiveFailures,
            int ageFrames,
            int framesSinceMtAnchor,
            float localAppearanceSimilarity,
            boolean localAppearanceValidated,
            float[] localAppearanceDescriptor,
            long updatedAtNanos
    ) {
        this(overlayItem,trackingQuality,supportRatio,meanMatchError,trackerInliers,consecutiveFailures,
                ageFrames,framesSinceMtAnchor,localAppearanceSimilarity,localAppearanceValidated,
                localAppearanceDescriptor,updatedAtNanos,null);
    }
    TrackedPlate(OverlayItem overlayItem,float trackingQuality,float supportRatio,float meanMatchError,
            int trackerInliers,int consecutiveFailures,int ageFrames,int framesSinceMtAnchor,
            float localAppearanceSimilarity,boolean localAppearanceValidated,float[] localAppearanceDescriptor,
            long updatedAtNanos,DynamicVehicleIdentity.Stamp identityOwner) {
        this(overlayItem,trackingQuality,supportRatio,meanMatchError,trackerInliers,consecutiveFailures,
                ageFrames,framesSinceMtAnchor,localAppearanceSimilarity,localAppearanceValidated,localAppearanceDescriptor,
                updatedAtNanos,identityOwner,0L);
    }
    TrackedPlate(OverlayItem overlayItem,float trackingQuality,float supportRatio,float meanMatchError,
            int trackerInliers,int consecutiveFailures,int ageFrames,int framesSinceMtAnchor,
            float localAppearanceSimilarity,boolean localAppearanceValidated,float[] localAppearanceDescriptor,
            long updatedAtNanos,DynamicVehicleIdentity.Stamp identityOwner,long anchorSourceTimestampNanos) {
        this(overlayItem,trackingQuality,supportRatio,meanMatchError,trackerInliers,consecutiveFailures,ageFrames,
                framesSinceMtAnchor,localAppearanceSimilarity,localAppearanceValidated,localAppearanceDescriptor,
                updatedAtNanos,identityOwner,anchorSourceTimestampNanos,null);
    }
    TrackedPlate(OverlayItem overlayItem,float trackingQuality,float supportRatio,float meanMatchError,
            int trackerInliers,int consecutiveFailures,int ageFrames,int framesSinceMtAnchor,
            float localAppearanceSimilarity,boolean localAppearanceValidated,float[] localAppearanceDescriptor,
            long updatedAtNanos,DynamicVehicleIdentity.Stamp identityOwner,long anchorSourceTimestampNanos,
            com.example.alpr_v1.domain.NormalizedQuad fullReferenceQuad) {
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
        this.localAppearanceSimilarity = clamp01(localAppearanceSimilarity);
        this.localAppearanceValidated = localAppearanceValidated;
        this.localAppearanceDescriptor = localAppearanceDescriptor == null
                ? null : localAppearanceDescriptor.clone();
        this.updatedAtNanos = Math.max(0L, updatedAtNanos);
        this.identityOwner=identityOwner;
        this.anchorSourceTimestampNanos=anchorSourceTimestampNanos;
        this.fullReferenceQuad=fullReferenceQuad;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
