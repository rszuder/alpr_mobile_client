package com.example.alpr_v1.pipeline;

import android.graphics.RectF;
import android.graphics.PointF;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.List;

/** Jeden snapshot celu współdzielony przez UI, scheduler MT i autozoom. */
public final class TargetSnapshot {
    public enum State { SEARCHING, ACQUIRED, TRACKING, LOCKED, DEGRADED, LOST }

    public final State state;
    public final long trackId;
    public final OverlayItem overlayItem;
    public final RectF normalizedBounds;
    public final float trackingQuality;
    public final float driftScore;
    public final float supportRatio;
    public final int trackerInliers;
    public final int consecutiveFailures;
    public final int ageFrames;
    public final int framesSinceMtAnchor;
    public final int stableUpdates;
    public final long updatedAtNanos;
    public final float[] appearanceDescriptor;
    public final float localAppearanceSimilarity;
    public final boolean localAppearanceValidated;
    public final long lockedTrackId;
    public final String transitionReason;
    public final int lockSwitches;
    public final int lockLosses;
    public final int framesToLock;
    public final long timeToLockMillis;
    public final long lockRevision;
    public final int lockReassociations;
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;
    public final long sourceTimestampNanos;

    TargetSnapshot(
            State state,
            long trackId,
            OverlayItem overlayItem,
            float trackingQuality,
            float driftScore,
            float supportRatio,
            int trackerInliers,
            int consecutiveFailures,
            int ageFrames,
            int framesSinceMtAnchor,
            int stableUpdates,
            long updatedAtNanos,
            float[] appearanceDescriptor,
            long lockedTrackId,
            String transitionReason,
            int lockSwitches,
            int lockLosses,
            int framesToLock,
            long timeToLockMillis,
            long lockRevision,
            int lockReassociations
    ) {
        this(
                state, trackId, overlayItem, trackingQuality, driftScore, supportRatio,
                trackerInliers, consecutiveFailures, ageFrames, framesSinceMtAnchor,
                stableUpdates, updatedAtNanos, appearanceDescriptor, lockedTrackId,
                transitionReason, lockSwitches, lockLosses, framesToLock,
                timeToLockMillis, lockRevision, lockReassociations,
                ContinuityStamp.initial(updatedAtNanos)
        );
    }

    TargetSnapshot(
            State state,
            long trackId,
            OverlayItem overlayItem,
            float trackingQuality,
            float driftScore,
            float supportRatio,
            int trackerInliers,
            int consecutiveFailures,
            int ageFrames,
            int framesSinceMtAnchor,
            int stableUpdates,
            long updatedAtNanos,
            float[] appearanceDescriptor,
            long lockedTrackId,
            String transitionReason,
            int lockSwitches,
            int lockLosses,
            int framesToLock,
            long timeToLockMillis,
            long lockRevision,
            int lockReassociations,
            ContinuityStamp continuityStamp
    ) {
        this(
                state, trackId, overlayItem, trackingQuality, driftScore, supportRatio,
                trackerInliers, consecutiveFailures, ageFrames, framesSinceMtAnchor,
                stableUpdates, updatedAtNanos, appearanceDescriptor, lockedTrackId,
                transitionReason, lockSwitches, lockLosses, framesToLock,
                timeToLockMillis, lockRevision, lockReassociations, continuityStamp,
                0f, false
        );
    }

    private TargetSnapshot(
            State state,
            long trackId,
            OverlayItem overlayItem,
            float trackingQuality,
            float driftScore,
            float supportRatio,
            int trackerInliers,
            int consecutiveFailures,
            int ageFrames,
            int framesSinceMtAnchor,
            int stableUpdates,
            long updatedAtNanos,
            float[] appearanceDescriptor,
            long lockedTrackId,
            String transitionReason,
            int lockSwitches,
            int lockLosses,
            int framesToLock,
            long timeToLockMillis,
            long lockRevision,
            int lockReassociations,
            ContinuityStamp continuityStamp,
            float localAppearanceSimilarity,
            boolean localAppearanceValidated
    ) {
        ContinuityStamp safeStamp = continuityStamp == null
                ? ContinuityStamp.initial(updatedAtNanos) : continuityStamp;
        this.state = state == null ? State.SEARCHING : state;
        this.trackId = trackId;
        this.overlayItem = copyOverlay(overlayItem);
        this.normalizedBounds = overlayItem == null
                ? new RectF()
                : new RectF(overlayItem.normalizedBounds);
        this.trackingQuality = clamp01(trackingQuality);
        this.driftScore = clamp01(driftScore);
        this.supportRatio = clamp01(supportRatio);
        this.trackerInliers = Math.max(0, trackerInliers);
        this.consecutiveFailures = Math.max(0, consecutiveFailures);
        this.ageFrames = Math.max(0, ageFrames);
        this.framesSinceMtAnchor = Math.max(0, framesSinceMtAnchor);
        this.stableUpdates = Math.max(0, stableUpdates);
        this.updatedAtNanos = Math.max(0L, updatedAtNanos);
        this.appearanceDescriptor = appearanceDescriptor == null
                ? null : appearanceDescriptor.clone();
        this.localAppearanceSimilarity = clamp01(localAppearanceSimilarity);
        this.localAppearanceValidated = localAppearanceValidated;
        this.lockedTrackId = Math.max(0L, lockedTrackId);
        this.transitionReason = transitionReason == null ? "" : transitionReason;
        this.lockSwitches = Math.max(0, lockSwitches);
        this.lockLosses = Math.max(0, lockLosses);
        this.framesToLock = Math.max(0, framesToLock);
        this.timeToLockMillis = Math.max(0L, timeToLockMillis);
        this.lockRevision = Math.max(0L, lockRevision);
        this.lockReassociations = Math.max(0, lockReassociations);
        this.sceneGeneration = safeStamp.sceneGeneration;
        this.visualEpoch = safeStamp.visualEpoch;
        this.cameraTransformGeneration = safeStamp.cameraTransformGeneration;
        this.sourceTimestampNanos = safeStamp.sourceTimestampNanos;
    }

    public static TargetSnapshot searching() {
        return new TargetSnapshot(
                State.SEARCHING, 0L, null, 0f, 1f, 0f, 0,
                0, 0, 0, 0, System.nanoTime(), null, 0L, "reset",
                0, 0, 0, 0L, 0L, 0
        );
    }

    public boolean hasTrack() {
        return trackId > 0L
                && overlayItem != null
                && state != State.SEARCHING
                && state != State.LOST;
    }

    public boolean locked() {
        return lockedTrackId > 0L;
    }

    public TargetSnapshot withState(State nextState) {
        return new TargetSnapshot(
                nextState, trackId, overlayItem, trackingQuality, driftScore,
                supportRatio, trackerInliers, consecutiveFailures, ageFrames, framesSinceMtAnchor,
                stableUpdates, System.nanoTime(), appearanceDescriptor,
                lockedTrackId, transitionReason, lockSwitches, lockLosses,
                framesToLock, timeToLockMillis, lockRevision, lockReassociations,
                continuityStamp().withSourceTimestamp(System.nanoTime()),
                localAppearanceSimilarity, localAppearanceValidated
        );
    }

    public TargetSnapshot withContinuityStamp(ContinuityStamp stamp) {
        if (stamp == null) throw new IllegalArgumentException("stamp");
        return new TargetSnapshot(
                state, trackId, overlayItem, trackingQuality, driftScore,
                supportRatio, trackerInliers, consecutiveFailures, ageFrames,
                framesSinceMtAnchor, stableUpdates, updatedAtNanos,
                appearanceDescriptor, lockedTrackId, transitionReason,
                lockSwitches, lockLosses, framesToLock, timeToLockMillis,
                lockRevision, lockReassociations, stamp,
                localAppearanceSimilarity, localAppearanceValidated
        );
    }

    TargetSnapshot withLocalAppearance(float similarity, boolean validated) {
        return new TargetSnapshot(
                state, trackId, overlayItem, trackingQuality, driftScore,
                supportRatio, trackerInliers, consecutiveFailures, ageFrames,
                framesSinceMtAnchor, stableUpdates, updatedAtNanos,
                appearanceDescriptor, lockedTrackId, transitionReason,
                lockSwitches, lockLosses, framesToLock, timeToLockMillis,
                lockRevision, lockReassociations, continuityStamp(),
                similarity, validated
        );
    }

    public ContinuityStamp continuityStamp() {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceTimestampNanos
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static OverlayItem copyOverlay(OverlayItem source) {
        if (source == null) return null;
        List<PointF> points = new ArrayList<>(source.normalizedKeypoints.size());
        for (PointF point : source.normalizedKeypoints) {
            points.add(new PointF(point.x, point.y));
        }
        return new OverlayItem(
                source.kind,
                source.normalizedBounds,
                points,
                source.label,
                source.trackId,
                source.carriedPrediction
        );
    }
}
