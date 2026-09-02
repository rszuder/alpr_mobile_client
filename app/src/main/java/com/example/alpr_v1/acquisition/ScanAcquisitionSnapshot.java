package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.TargetSessionState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Immutable state used by telemetry and the minimal Phase 3B status UI. */
public final class ScanAcquisitionSnapshot {
    public final long scanRunId;
    public final ScanRunState runState;
    public final long runWallDurationNanos;
    public final long runActiveDurationNanos;
    public final AcquisitionQueueSnapshot queue;
    public final long activeSessionId;
    public final long activeEntityId;
    public final TargetSessionState activeSessionState;
    public final int mtAttempts;
    public final int freshMzAttempts;
    public final long activeSessionDurationNanos;
    public final long noProgressDurationNanos;
    public final AcquisitionDirective directive;
    public final boolean autoZoomAllowed;
    public final PlateAnchor plateAnchor;
    public final ScanAcquisitionStats stats;
    public final Set<Long> identifiedEntityIds;
    public final Set<Long> completedEntityIds;
    public final Map<Long, EntityRecognitionSnapshot> entityRecognitions;

    public ScanAcquisitionSnapshot(
            long scanRunId,
            ScanRunState runState,
            long runWallDurationNanos,
            long runActiveDurationNanos,
            AcquisitionQueueSnapshot queue,
            long activeSessionId,
            long activeEntityId,
            TargetSessionState activeSessionState,
            int mtAttempts,
            int freshMzAttempts,
            long activeSessionDurationNanos,
            long noProgressDurationNanos,
            AcquisitionDirective directive,
            boolean autoZoomAllowed,
            PlateAnchor plateAnchor,
            ScanAcquisitionStats stats
    ) {
        this(
                scanRunId, runState, runWallDurationNanos, runActiveDurationNanos,
                queue, activeSessionId, activeEntityId, activeSessionState,
                mtAttempts, freshMzAttempts, activeSessionDurationNanos,
                noProgressDurationNanos, directive, autoZoomAllowed, plateAnchor,
                stats, Collections.emptySet(), Collections.emptySet(),
                Collections.emptyMap()
        );
    }

    public ScanAcquisitionSnapshot(
            long scanRunId,
            ScanRunState runState,
            long runWallDurationNanos,
            long runActiveDurationNanos,
            AcquisitionQueueSnapshot queue,
            long activeSessionId,
            long activeEntityId,
            TargetSessionState activeSessionState,
            int mtAttempts,
            int freshMzAttempts,
            long activeSessionDurationNanos,
            long noProgressDurationNanos,
            AcquisitionDirective directive,
            boolean autoZoomAllowed,
            PlateAnchor plateAnchor,
            ScanAcquisitionStats stats,
            Set<Long> identifiedEntityIds,
            Set<Long> completedEntityIds,
            Map<Long, EntityRecognitionSnapshot> entityRecognitions
    ) {
        this.scanRunId = Math.max(0L, scanRunId);
        this.runState = runState == null ? ScanRunState.IDLE : runState;
        this.runWallDurationNanos = Math.max(0L, runWallDurationNanos);
        this.runActiveDurationNanos = Math.max(0L, runActiveDurationNanos);
        this.queue = queue == null ? AcquisitionQueueSnapshot.empty(0L) : queue;
        this.activeSessionId = Math.max(0L, activeSessionId);
        this.activeEntityId = Math.max(0L, activeEntityId);
        this.activeSessionState = activeSessionState;
        this.mtAttempts = Math.max(0, mtAttempts);
        this.freshMzAttempts = Math.max(0, freshMzAttempts);
        this.activeSessionDurationNanos = Math.max(0L, activeSessionDurationNanos);
        this.noProgressDurationNanos = Math.max(0L, noProgressDurationNanos);
        this.directive = directive == null
                ? AcquisitionDirective.none(0L, this.scanRunId) : directive;
        this.autoZoomAllowed = autoZoomAllowed;
        this.plateAnchor = plateAnchor;
        this.stats = stats == null ? ScanAcquisitionStats.empty() : stats;
        this.identifiedEntityIds = Collections.unmodifiableSet(new HashSet<>(
                identifiedEntityIds == null
                        ? Collections.emptySet() : identifiedEntityIds
        ));
        this.completedEntityIds = Collections.unmodifiableSet(new HashSet<>(
                completedEntityIds == null
                        ? Collections.emptySet() : completedEntityIds
        ));
        this.entityRecognitions = Collections.unmodifiableMap(new HashMap<>(
                entityRecognitions == null
                        ? Collections.emptyMap() : entityRecognitions
        ));
    }
}
