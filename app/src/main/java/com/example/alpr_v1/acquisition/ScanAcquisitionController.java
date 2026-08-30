package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.domain.ApplicationMode;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.ModeController;
import com.example.alpr_v1.domain.TargetPurpose;
import com.example.alpr_v1.domain.TargetSession;
import com.example.alpr_v1.domain.TargetSessionState;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.ui.OverlayItem;

/** Single owner of the Phase 3B queue, short session and attempt budgets. */
public final class ScanAcquisitionController {
    private final ModeController modeController;
    private final ScanAcquisitionProfile profile;
    private final AcquisitionQueue queue;
    private ScanRun run;
    private TargetSession activeSession;
    private ActiveTimeBudget activeSessionBudget;
    private ActiveTimeBudget noProgressBudget;
    private AcquisitionDirective directive = AcquisitionDirective.none(0L, 0L);
    private long directiveRevision;
    private int mtAttempts;
    private int freshMzAttempts;
    private boolean releasePending;
    private long lastRuntimeNanos;
    private PlateAnchor plateAnchor;

    public ScanAcquisitionController() {
        this(new ModeController(), ScanAcquisitionProfile.DEFAULT);
    }

    public ScanAcquisitionController(
            ModeController modeController,
            ScanAcquisitionProfile profile
    ) {
        if (modeController == null) throw new IllegalArgumentException("modeController");
        if (profile == null) throw new IllegalArgumentException("profile");
        this.modeController = modeController;
        this.profile = profile;
        this.queue = new AcquisitionQueue(profile);
    }

    public synchronized void startRun(long scanRunId, long nowRuntimeNanos) {
        rememberRuntime(nowRuntimeNanos);
        cancelActiveSession(TargetSessionState.CANCELLED, nowRuntimeNanos);
        if (run != null && run.state().active()) run.stop(nowRuntimeNanos);
        modeController.switchMode(ApplicationMode.SCAN_ACQUIRE, nowRuntimeNanos);
        run = new ScanRun(scanRunId, nowRuntimeNanos);
        queue.hardReset(0L);
        resetSessionBudgets();
        releasePending = false;
        setDirective(
                AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                0L, 0L,
                "scan_run_started"
        );
    }

    public synchronized void pauseRun(long nowRuntimeNanos) {
        rememberRuntime(nowRuntimeNanos);
        if (run == null || run.state() != ScanRunState.RUNNING) return;
        run.pause(nowRuntimeNanos);
        pauseSessionBudgets(nowRuntimeNanos);
        setDirective(AcquisitionDirectiveAction.NONE, 0L, 0L, "scan_run_paused");
    }

    public synchronized void resumeRun(long nowRuntimeNanos) {
        rememberRuntime(nowRuntimeNanos);
        if (run == null || run.state() != ScanRunState.PAUSED) return;
        run.resume(nowRuntimeNanos);
        resumeSessionBudgets(nowRuntimeNanos);
        if (activeSession != null) {
            setDirective(
                    AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                    activeSession.sessionId(),
                    activeSession.entityId(),
                    "scan_run_resumed"
            );
        } else {
            setDirective(
                    AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                    0L, 0L,
                    "scan_run_resumed"
            );
        }
    }

    public synchronized void stopRun(long nowRuntimeNanos) {
        rememberRuntime(nowRuntimeNanos);
        if (run == null || !run.state().active()) return;
        long entityId = activeSession == null ? 0L : activeSession.entityId();
        cancelActiveSession(TargetSessionState.CANCELLED, nowRuntimeNanos);
        run.stop(nowRuntimeNanos);
        resetSessionBudgets();
        releasePending = entityId > 0L;
        setDirective(
                entityId > 0L
                        ? AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET
                        : AcquisitionDirectiveAction.NONE,
                0L, 0L,
                "scan_run_stopped"
        );
    }

    public synchronized AcquisitionDirective onVehicleFrame(
            VehicleTrackingFrame frame,
            SceneContinuitySnapshot continuity,
            long nowRuntimeNanos
    ) {
        rememberRuntime(nowRuntimeNanos);
        if (!running()) return currentDirective();
        if (continuity == null || frame == null
                || frame.sceneGeneration != continuity.sceneGeneration) {
            return setDirective(
                    AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                    activeSessionId(), activeEntityId(),
                    "scan_waiting_for_current_vehicle_frame"
            );
        }
        if (continuity.heavyInferenceSuspended) return currentDirective();

        if (releasePending) {
            releasePending = false;
            setDirective(AcquisitionDirectiveAction.NONE, 0L, 0L, "release_applied");
        }

        queue.update(frame, activeEntityId(), nowRuntimeNanos);
        AcquisitionDirective timeout = enforceSessionBudgets(nowRuntimeNanos);
        if (timeout != null) return timeout;
        if (activeSession != null) return currentDirective();

        AcquisitionQueue.Selection selection = queue.selectNext(nowRuntimeNanos);
        if (selection == null) {
            return setDirective(
                    AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                    0L, 0L,
                    "scan_queue_waiting_for_candidate"
            );
        }
        activeSession = modeController.startSession(
                selection.candidate.entityId,
                TargetPurpose.SCAN_ACQUISITION,
                nowRuntimeNanos
        );
        plateAnchor = null;
        activeSession.transitionTo(TargetSessionState.ACQUIRING_PLATE,
                nowRuntimeNanos);
        activeSessionBudget = new ActiveTimeBudget(profile.maximumActiveSessionNanos);
        noProgressBudget = new ActiveTimeBudget(profile.noProgressTimeoutNanos);
        activeSessionBudget.start(nowRuntimeNanos);
        noProgressBudget.start(nowRuntimeNanos);
        mtAttempts = 1;
        freshMzAttempts = 0;
        queue.recordMtAttempt(selection.candidate.entityId, nowRuntimeNanos);
        return setDirective(
                AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT,
                activeSession.sessionId(),
                activeSession.entityId(),
                "scan_next_candidate"
        );
    }

    public synchronized AcquisitionDecision onPipelineResult(
            PipelineResult result,
            SceneContinuitySnapshot continuity,
            long nowRuntimeNanos
    ) {
        rememberRuntime(nowRuntimeNanos);
        if (!running() || activeSession == null || result == null) {
            return ignored("scan_has_no_active_session");
        }
        if (continuity == null
                || result.sceneGeneration != continuity.sceneGeneration
                || result.visualEpoch != continuity.visualEpoch
                || result.cameraTransformGeneration
                != continuity.cameraTransformGeneration) {
            return ignored("stale_scan_pipeline_result");
        }
        if ("candidate_missing".equals(result.status)) {
            return deferActive(
                    AcquisitionDeferReason.CANDIDATE_MISSING,
                    nowRuntimeNanos
            );
        }

        PlateObservation matching = bestMatchingObservation(
                result,
                activeSession.entityId()
        );
        if (matching == null) {
            if (containsDifferentAssignedEntity(result, activeSession.entityId())) {
                return ignored("pipeline_result_belongs_to_another_entity");
            }
            if (directive.requestsMt()) {
                if (mtAttempts < profile.maximumMtAttempts) {
                    mtAttempts++;
                    queue.recordMtAttempt(activeSession.entityId(), nowRuntimeNanos);
                    AcquisitionDirective retry = setDirective(
                            AcquisitionDirectiveAction.REQUEST_EXPANDED_ENTITY_MT,
                            activeSession.sessionId(),
                            activeSession.entityId(),
                            "scan_expanded_entity_retry"
                    );
                    return decision(
                            true,
                            AcquisitionSessionOutcome.PROGRESS,
                            AcquisitionDeferReason.NONE,
                            EntityAcquisitionState.ACQUIRING,
                            retry,
                            "plate_not_found_expand_same_entity"
                    );
                }
                return deferActive(
                        AcquisitionDeferReason.MT_ATTEMPTS_EXHAUSTED,
                        nowRuntimeNanos
                );
            }
            return ignored("no_scan_entity_observation");
        }
        if (matching.acquisitionDirectiveRevision > 0L
                && (!directive.requestsMt()
                || matching.acquisitionDirectiveRevision != directive.revision)) {
            return ignored("stale_scan_mt_directive_revision");
        }

        if (activeSession.state() == TargetSessionState.ACQUIRING_PLATE) {
            activeSession.transitionTo(
                    TargetSessionState.READING_REGISTRATION,
                    nowRuntimeNanos
            );
        }
        plateAnchor = new PlateAnchor(
                matching.entityId,
                matching.vehicleTrackId,
                matching.plateTrackId,
                findPlateOverlay(result, matching.plateTrackId),
                matching.appearanceDescriptor,
                matching.continuityStamp(),
                matching.acquisitionDirectiveRevision
        );
        resetNoProgressBudget(nowRuntimeNanos);
        if (matching.freshMzAttempted) {
            freshMzAttempts++;
            queue.recordFreshMzAttempt(activeSession.entityId(), nowRuntimeNanos);
        }
        if (matching.confirmed
                && matching.cropSupportsConsensus
                && !matching.text.trim().isEmpty()) {
            long entityId = activeSession.entityId();
            long sessionId = activeSession.sessionId();
            modeController.finishSession(
                    sessionId,
                    TargetSessionState.COMPLETED,
                    nowRuntimeNanos
            );
            activeSession = null;
            queue.complete(entityId);
            resetSessionBudgets();
            plateAnchor = null;
            releasePending = true;
            AcquisitionDirective release = setDirective(
                    AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                    0L, 0L,
                    "scan_ready_to_finalize"
            );
            return new AcquisitionDecision(
                    true,
                    AcquisitionSessionOutcome.READY_TO_FINALIZE,
                    AcquisitionDeferReason.NONE,
                    scanRunId(),
                    sessionId,
                    entityId,
                    EntityAcquisitionState.READY_TO_FINALIZE,
                    release,
                    "stable_registration_consensus"
            );
        }
        if (freshMzAttempts >= profile.maximumFreshMzAttempts) {
            return deferActive(
                    AcquisitionDeferReason.MZ_ATTEMPTS_EXHAUSTED,
                    nowRuntimeNanos
            );
        }
        AcquisitionDirective next = setDirective(
                AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                activeSession.sessionId(),
                activeSession.entityId(),
                "scan_registration_in_progress"
        );
        return decision(
                true,
                AcquisitionSessionOutcome.PROGRESS,
                AcquisitionDeferReason.NONE,
                EntityAcquisitionState.READING_REGISTRATION,
                next,
                "matching_entity_progress"
        );
    }

    public synchronized void onContinuityDecision(
            SceneTransitionDecision decision,
            long nowRuntimeNanos
    ) {
        rememberRuntime(nowRuntimeNanos);
        if (decision == null || !running()) return;
        if (decision.action == SceneTransitionAction.HARD_RESET) {
            cancelActiveSession(TargetSessionState.LOST, nowRuntimeNanos);
            queue.hardReset(Math.max(0L,
                    decision.incrementSceneGeneration
                            ? queue.snapshot(nowRuntimeNanos).sceneGeneration + 1L
                            : queue.snapshot(nowRuntimeNanos).sceneGeneration));
            resetSessionBudgets();
            releasePending = false;
            setDirective(
                    AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                    0L, 0L,
                    "scan_scene_hard_reset"
            );
        }
    }

    public synchronized void onTerminalRecovery(
            SoftReacquireResult result,
            SceneTransitionDecision appliedDecision,
            long nowRuntimeNanos
    ) {
        rememberRuntime(nowRuntimeNanos);
        // Full recovery/session policy is integrated in the dedicated continuity commit.
    }

    public synchronized AcquisitionDirective currentDirective() {
        return directive;
    }

    public synchronized ScanAcquisitionSnapshot snapshot() {
        return snapshot(lastRuntimeNanos);
    }

    public synchronized ScanAcquisitionSnapshot snapshot(long nowRuntimeNanos) {
        rememberRuntime(nowRuntimeNanos);
        ScanRunState state = run == null ? ScanRunState.IDLE : run.state();
        return new ScanAcquisitionSnapshot(
                scanRunId(),
                state,
                run == null ? 0L : run.wallDurationNanos(nowRuntimeNanos),
                run == null ? 0L : run.activeDurationNanos(nowRuntimeNanos),
                queue.snapshot(nowRuntimeNanos),
                activeSessionId(),
                activeEntityId(),
                activeSession == null ? null : activeSession.state(),
                mtAttempts,
                freshMzAttempts,
                activeSessionBudget == null
                        ? 0L : activeSessionBudget.elapsedActiveNanos(nowRuntimeNanos),
                noProgressBudget == null
                        ? 0L : noProgressBudget.elapsedActiveNanos(nowRuntimeNanos),
                directive,
                false,
                plateAnchor
        );
    }

    public synchronized boolean autoZoomAllowed() {
        return false;
    }

    private AcquisitionDirective enforceSessionBudgets(long nowRuntimeNanos) {
        if (activeSession == null) return null;
        if (activeSessionBudget != null
                && activeSessionBudget.exhausted(nowRuntimeNanos)) {
            return deferActive(
                    AcquisitionDeferReason.ACTIVE_SESSION_TIMEOUT,
                    nowRuntimeNanos
            ).nextDirective;
        }
        if (noProgressBudget != null
                && noProgressBudget.exhausted(nowRuntimeNanos)) {
            return deferActive(
                    AcquisitionDeferReason.NO_PROGRESS_TIMEOUT,
                    nowRuntimeNanos
            ).nextDirective;
        }
        return null;
    }

    private AcquisitionDecision deferActive(
            AcquisitionDeferReason reason,
            long nowRuntimeNanos
    ) {
        if (activeSession == null) return ignored("no_active_session_to_defer");
        long entityId = activeSession.entityId();
        long sessionId = activeSession.sessionId();
        modeController.finishSession(sessionId, TargetSessionState.LOST,
                nowRuntimeNanos);
        activeSession = null;
        queue.defer(
                entityId,
                nowRuntimeNanos,
                profile.defaultCooldownNanos
        );
        resetSessionBudgets();
        plateAnchor = null;
        releasePending = true;
        AcquisitionDirective release = setDirective(
                AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                0L, 0L,
                "scan_deferred_" + reason.name().toLowerCase(java.util.Locale.ROOT)
        );
        return new AcquisitionDecision(
                true,
                reason == AcquisitionDeferReason.ACTIVE_SESSION_TIMEOUT
                        || reason == AcquisitionDeferReason.NO_PROGRESS_TIMEOUT
                        ? AcquisitionSessionOutcome.TIMED_OUT
                        : AcquisitionSessionOutcome.DEFERRED,
                reason,
                scanRunId(),
                sessionId,
                entityId,
                EntityAcquisitionState.QUEUED,
                release,
                release.reason
        );
    }

    private AcquisitionDecision ignored(String reason) {
        return new AcquisitionDecision(
                false,
                AcquisitionSessionOutcome.NONE,
                AcquisitionDeferReason.NONE,
                scanRunId(),
                activeSessionId(),
                activeEntityId(),
                activeSession == null
                        ? EntityAcquisitionState.NEW
                        : entityStateFor(activeSession.state()),
                directive,
                reason
        );
    }

    private AcquisitionDecision decision(
            boolean accepted,
            AcquisitionSessionOutcome outcome,
            AcquisitionDeferReason deferReason,
            EntityAcquisitionState entityState,
            AcquisitionDirective next,
            String reason
    ) {
        return new AcquisitionDecision(
                accepted, outcome, deferReason,
                scanRunId(), activeSessionId(), activeEntityId(),
                entityState, next, reason
        );
    }

    private AcquisitionDirective setDirective(
            AcquisitionDirectiveAction action,
            long sessionId,
            long entityId,
            String reason
    ) {
        if (directive.action == action
                && directive.sessionId == sessionId
                && directive.entityId == entityId
                && directive.reason.equals(reason == null ? "" : reason.trim())) {
            return directive;
        }
        directive = new AcquisitionDirective(
                ++directiveRevision,
                action,
                scanRunId(),
                sessionId,
                entityId,
                reason
        );
        return directive;
    }

    private void cancelActiveSession(
            TargetSessionState terminal,
            long nowRuntimeNanos
    ) {
        if (activeSession == null) return;
        modeController.finishSession(
                activeSession.sessionId(),
                terminal,
                nowRuntimeNanos
        );
        activeSession = null;
        plateAnchor = null;
    }

    private void pauseSessionBudgets(long nowRuntimeNanos) {
        if (activeSessionBudget != null) activeSessionBudget.pause(nowRuntimeNanos);
        if (noProgressBudget != null) noProgressBudget.pause(nowRuntimeNanos);
    }

    private void resumeSessionBudgets(long nowRuntimeNanos) {
        if (activeSessionBudget != null) activeSessionBudget.resume(nowRuntimeNanos);
        if (noProgressBudget != null) noProgressBudget.resume(nowRuntimeNanos);
    }

    private void resetNoProgressBudget(long nowRuntimeNanos) {
        if (noProgressBudget != null) noProgressBudget.reset(nowRuntimeNanos, true);
    }

    private void resetSessionBudgets() {
        activeSessionBudget = null;
        noProgressBudget = null;
        mtAttempts = 0;
        freshMzAttempts = 0;
    }

    private boolean running() {
        return run != null && run.state() == ScanRunState.RUNNING;
    }

    private long scanRunId() {
        return run == null ? 0L : run.scanRunId;
    }

    private long activeSessionId() {
        return activeSession == null ? 0L : activeSession.sessionId();
    }

    private long activeEntityId() {
        return activeSession == null ? 0L : activeSession.entityId();
    }

    private void rememberRuntime(long nowRuntimeNanos) {
        lastRuntimeNanos = Math.max(lastRuntimeNanos, nowRuntimeNanos);
    }

    private static PlateObservation bestMatchingObservation(
            PipelineResult result,
            long entityId
    ) {
        PlateObservation best = null;
        for (PlateObservation observation : result.plateObservations) {
            if (observation.entityId != entityId) continue;
            if (best == null) {
                best = observation;
                continue;
            }
            if (observation.confirmed != best.confirmed) {
                if (observation.confirmed) best = observation;
                continue;
            }
            if (observation.observations > best.observations
                    || observation.observations == best.observations
                    && observation.recognitionConfidence
                    > best.recognitionConfidence) best = observation;
        }
        return best;
    }

    private static boolean containsDifferentAssignedEntity(
            PipelineResult result,
            long entityId
    ) {
        for (PlateObservation observation : result.plateObservations) {
            if (observation.entityId > 0L && observation.entityId != entityId) {
                return true;
            }
        }
        return false;
    }

    private static OverlayItem findPlateOverlay(
            PipelineResult result,
            long plateTrackId
    ) {
        for (OverlayItem item : result.overlayItems) {
            if (item.kind == OverlayItem.Kind.PLATE
                    && item.trackId == plateTrackId) return item;
        }
        return null;
    }

    private static EntityAcquisitionState entityStateFor(TargetSessionState state) {
        if (state == TargetSessionState.READING_REGISTRATION) {
            return EntityAcquisitionState.READING_REGISTRATION;
        }
        if (state == TargetSessionState.TRACKING) {
            return EntityAcquisitionState.PLATE_LOCALIZED;
        }
        return EntityAcquisitionState.ACQUIRING;
    }
}
