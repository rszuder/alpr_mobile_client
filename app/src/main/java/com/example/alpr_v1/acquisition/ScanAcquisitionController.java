package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.domain.ApplicationMode;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.ModeController;
import com.example.alpr_v1.domain.RegistrationTextPolicy;
import com.example.alpr_v1.domain.TargetPurpose;
import com.example.alpr_v1.domain.TargetSession;
import com.example.alpr_v1.domain.TargetSessionState;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Single owner of the Phase 3B queue, short session and attempt budgets. */
public final class ScanAcquisitionController {
    /** Lets an already-started OCR job finish just after its vehicle session is released. */
    private static final long RELEASED_RESULT_GRACE_NANOS = 5_000_000_000L;

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
    private boolean continuityPaused;
    private boolean recoveryPaused;
    private long recentlyReleasedEntityId;
    private long recentlyReleasedDirectiveRevision;
    private long recentlyReleasedAtRuntimeNanos;
    private final Set<Long> vehiclesSeen = new HashSet<>();
    private final Set<Long> vehiclesQueued = new HashSet<>();
    private final Set<Long> identifiedEntityIds = new HashSet<>();
    private final Set<Long> completedEntityIds = new HashSet<>();
    private final Map<Long, EntityRecognitionSnapshot> entityRecognitions =
            new HashMap<>();
    private final List<Long> queueWaitNanos = new ArrayList<>();
    private final List<Long> activeSessionNanos = new ArrayList<>();
    private int vehiclesSelected;
    private int vehiclesDeferred;
    private int vehiclesLost;
    private int entitiesReadyToFinalize;
    private int totalMtAttempts;
    private int totalFreshMzAttempts;

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
        continuityPaused = false;
        recoveryPaused = false;
        resetStatistics();
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

        for (com.example.alpr_v1.tracking.VehicleCandidate candidate
                : frame.candidates) {
            vehiclesSeen.add(candidate.entityId);
        }
        AcquisitionQueueSnapshot updatedQueue = queue.update(
                frame,
                activeEntityId(),
                nowRuntimeNanos
        );
        for (AcquisitionCandidate candidate : updatedQueue.candidates) {
            vehiclesQueued.add(candidate.entityId);
        }
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
        vehiclesSelected++;
        queueWaitNanos.add(Math.max(
                0L,
                nowRuntimeNanos
                        - selection.candidate.firstQueuedRuntimeNanos
        ));
        plateAnchor = null;
        activeSession.transitionTo(TargetSessionState.ACQUIRING_PLATE,
                nowRuntimeNanos);
        activeSessionBudget = new ActiveTimeBudget(profile.maximumActiveSessionNanos);
        noProgressBudget = new ActiveTimeBudget(profile.noProgressTimeoutNanos);
        activeSessionBudget.start(nowRuntimeNanos);
        noProgressBudget.start(nowRuntimeNanos);
        mtAttempts = 1;
        totalMtAttempts++;
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
        if (!running() || result == null) {
            return ignored("scan_has_no_active_session");
        }
        if (continuity == null
                || result.sceneGeneration != continuity.sceneGeneration
                || result.visualEpoch != continuity.visualEpoch
                || result.cameraTransformGeneration
                != continuity.cameraTransformGeneration) {
            return ignored("stale_scan_pipeline_result");
        }
        rememberCurrentEntityRecognitions(result);
        if (activeSession == null) {
            return ignored("scan_has_no_active_session");
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
                    totalMtAttempts++;
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

        identifiedEntityIds.add(matching.entityId);
        rememberEntityRecognition(matching);
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
            totalFreshMzAttempts++;
            queue.recordFreshMzAttempt(activeSession.entityId(), nowRuntimeNanos);
        }
        if (matching.confirmed
                && matching.cropSupportsConsensus
                && RegistrationTextPolicy.displayable(matching.text)) {
            long entityId = activeSession.entityId();
            long sessionId = activeSession.sessionId();
            modeController.finishSession(
                    sessionId,
                    TargetSessionState.COMPLETED,
                    nowRuntimeNanos
            );
            activeSession = null;
            queue.complete(entityId);
            completedEntityIds.add(entityId);
            recordActiveSessionDuration(nowRuntimeNanos);
            entitiesReadyToFinalize++;
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
        if (decision.action == SceneTransitionAction.SOFT_HOLD) {
            continuityPaused = true;
            pauseSessionBudgets(nowRuntimeNanos);
            setDirective(
                    AcquisitionDirectiveAction.NONE,
                    activeSessionId(),
                    activeEntityId(),
                    "scan_continuity_hold"
            );
            return;
        }
        if (decision.action == SceneTransitionAction.SOFT_REACQUIRE) {
            recoveryPaused = true;
            pauseSessionBudgets(nowRuntimeNanos);
            if (activeSession != null
                    && activeSession.state() != TargetSessionState.RECOVERING) {
                activeSession.transitionTo(
                        TargetSessionState.RECOVERING,
                        nowRuntimeNanos
                );
            }
            setDirective(
                    AcquisitionDirectiveAction.NONE,
                    activeSessionId(),
                    activeEntityId(),
                    "scan_continuity_reacquire"
            );
            return;
        }
        if (decision.action == SceneTransitionAction.HARD_RESET) {
            if (activeSession != null) vehiclesLost++;
            recordActiveSessionDuration(nowRuntimeNanos);
            cancelActiveSession(TargetSessionState.LOST, nowRuntimeNanos);
            identifiedEntityIds.clear();
            completedEntityIds.clear();
            entityRecognitions.clear();
            queue.hardReset(Math.max(0L,
                    decision.incrementSceneGeneration
                            ? queue.snapshot(nowRuntimeNanos).sceneGeneration + 1L
                            : queue.snapshot(nowRuntimeNanos).sceneGeneration));
            resetSessionBudgets();
            continuityPaused = false;
            recoveryPaused = false;
            releasePending = false;
            setDirective(
                    AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                    0L, 0L,
                    "scan_scene_hard_reset"
            );
            return;
        }
        if (decision.action == SceneTransitionAction.RELEASE_ACTIVE_TARGET
                && recoveryPaused) {
            recoveryPaused = false;
            continuityPaused = false;
            if (activeSession != null) {
                deferActive(
                        AcquisitionDeferReason.CONTINUITY_TARGET_LOST,
                        nowRuntimeNanos
                );
            } else {
                setDirective(
                        AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                        0L, 0L,
                        "scan_recovery_release_finished"
                );
            }
            return;
        }
        if (decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.STABLE
                && recoveryPaused) {
            recoveryPaused = false;
            continuityPaused = false;
            if (activeSession != null) {
                if (activeSession.state() == TargetSessionState.RECOVERING) {
                    activeSession.transitionTo(
                            TargetSessionState.TRACKING,
                            nowRuntimeNanos
                    );
                }
                resumeSessionBudgets(nowRuntimeNanos);
                setDirective(
                        AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                        activeSession.sessionId(),
                        activeSession.entityId(),
                        "scan_recovery_stable_finished"
                );
            } else {
                setDirective(
                        AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                        0L, 0L,
                        "scan_recovery_stable_refresh"
                );
            }
            return;
        }
        if (decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.STABLE
                && continuityPaused && !recoveryPaused) {
            continuityPaused = false;
            resumeSessionBudgets(nowRuntimeNanos);
            if (activeSession != null) {
                setDirective(
                        AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                        activeSession.sessionId(),
                        activeSession.entityId(),
                        "scan_continuity_hold_finished"
                );
            }
        }
    }

    public synchronized void onTerminalRecovery(
            SoftReacquireResult result,
            SceneTransitionDecision appliedDecision,
            long nowRuntimeNanos
    ) {
        rememberRuntime(nowRuntimeNanos);
        if (result == null || !running()) return;
        switch (result) {
            case TARGET_RECOVERED:
                recoveryPaused = false;
                continuityPaused = false;
                if (activeSession != null) {
                    if (activeSession.state() == TargetSessionState.RECOVERING) {
                        activeSession.transitionTo(
                                TargetSessionState.TRACKING,
                                nowRuntimeNanos
                        );
                    }
                    resumeSessionBudgets(nowRuntimeNanos);
                    setDirective(
                            AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                            activeSession.sessionId(),
                            activeSession.entityId(),
                            "scan_continuity_target_recovered"
                    );
                }
                break;
            case VEHICLE_POOL_RECOVERED:
                recoveryPaused = false;
                continuityPaused = false;
                if (activeSession != null) {
                    deferActive(
                            AcquisitionDeferReason.CONTINUITY_TARGET_LOST,
                            nowRuntimeNanos
                    );
                } else {
                    setDirective(
                            AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                            0L, 0L,
                            "scan_vehicle_pool_recovered"
                    );
                }
                break;
            case ACTIVE_TARGET_LOST:
                recoveryPaused = false;
                continuityPaused = false;
                if (activeSession != null) {
                    deferActive(
                            AcquisitionDeferReason.CONTINUITY_TARGET_LOST,
                            nowRuntimeNanos
                    );
                }
                break;
            case FAILED:
                recoveryPaused = false;
                continuityPaused = false;
                if (appliedDecision != null
                        && appliedDecision.action
                        == SceneTransitionAction.HARD_RESET) {
                    // The applied HARD_RESET has already cleared this controller.
                } else if (activeSession != null) {
                    deferActive(
                            AcquisitionDeferReason.CONTINUITY_TARGET_LOST,
                            nowRuntimeNanos
                    );
                }
                break;
            default:
                throw new AssertionError("Unhandled recovery result " + result);
        }
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
                plateAnchor,
                statistics(),
                identifiedEntityIds,
                completedEntityIds,
                entityRecognitions
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
        recentlyReleasedEntityId = entityId;
        recentlyReleasedDirectiveRevision = directive.revision;
        recentlyReleasedAtRuntimeNanos = nowRuntimeNanos;
        modeController.finishSession(sessionId, TargetSessionState.LOST,
                nowRuntimeNanos);
        activeSession = null;
        recordActiveSessionDuration(nowRuntimeNanos);
        vehiclesDeferred++;
        if (reason == AcquisitionDeferReason.CONTINUITY_TARGET_LOST) {
            vehiclesLost++;
        }
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

    private void recordActiveSessionDuration(long nowRuntimeNanos) {
        if (activeSessionBudget == null) return;
        activeSessionNanos.add(
                activeSessionBudget.elapsedActiveNanos(nowRuntimeNanos)
        );
    }

    private void resetStatistics() {
        vehiclesSeen.clear();
        vehiclesQueued.clear();
        identifiedEntityIds.clear();
        completedEntityIds.clear();
        entityRecognitions.clear();
        recentlyReleasedEntityId = 0L;
        recentlyReleasedDirectiveRevision = 0L;
        recentlyReleasedAtRuntimeNanos = 0L;
        queueWaitNanos.clear();
        activeSessionNanos.clear();
        vehiclesSelected = 0;
        vehiclesDeferred = 0;
        vehiclesLost = 0;
        entitiesReadyToFinalize = 0;
        totalMtAttempts = 0;
        totalFreshMzAttempts = 0;
    }

    private void rememberEntityRecognition(PlateObservation observation) {
        if (observation == null
                || observation.entityId <= 0L
                || !RegistrationTextPolicy.displayable(observation.text)) return;
        EntityRecognitionSnapshot candidate = new EntityRecognitionSnapshot(
                observation.entityId,
                observation.plateTrackId,
                observation.text,
                observation.recognitionConfidence,
                observation.confirmed,
                observation.observations
        );
        EntityRecognitionSnapshot previous = entityRecognitions.get(
                observation.entityId
        );
        boolean replace = previous == null
                || candidate.confirmed && !previous.confirmed
                || candidate.confirmed == previous.confirmed
                && (candidate.observations > previous.observations
                || candidate.observations == previous.observations
                && candidate.confidence >= previous.confidence);
        if (replace) entityRecognitions.put(observation.entityId, candidate);
    }

    private void rememberCurrentEntityRecognitions(PipelineResult result) {
        if (result == null) return;
        for (PlateObservation observation : result.plateObservations) {
            if (observation == null
                    || observation.entityId <= 0L
                    || !vehiclesSeen.contains(observation.entityId)) continue;
            identifiedEntityIds.add(observation.entityId);
            boolean activeOwner = activeSession != null
                    && activeSession.entityId() == observation.entityId;
            boolean recentlyReleasedOwner = observation.entityId
                    == recentlyReleasedEntityId
                    && recentlyReleasedAtRuntimeNanos > 0L
                    && lastRuntimeNanos >= recentlyReleasedAtRuntimeNanos
                    && lastRuntimeNanos - recentlyReleasedAtRuntimeNanos
                    <= RELEASED_RESULT_GRACE_NANOS
                    && (observation.acquisitionDirectiveRevision <= 0L
                    || observation.acquisitionDirectiveRevision
                    <= recentlyReleasedDirectiveRevision);
            if (observation.confirmed || activeOwner || recentlyReleasedOwner) {
                rememberEntityRecognition(observation);
            }
        }
    }

    private ScanAcquisitionStats statistics() {
        double divisor = Math.max(1, vehiclesSelected);
        return new ScanAcquisitionStats(
                vehiclesSeen.size(),
                vehiclesQueued.size(),
                vehiclesSelected,
                vehiclesDeferred,
                vehiclesLost,
                entitiesReadyToFinalize,
                meanMillis(queueWaitNanos),
                p95Millis(queueWaitNanos),
                meanMillis(activeSessionNanos),
                p95Millis(activeSessionNanos),
                totalMtAttempts / divisor,
                totalFreshMzAttempts / divisor
        );
    }

    private static double meanMillis(List<Long> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Long value : values) sum += Math.max(0L, value);
        return sum / values.size() / 1_000_000.0;
    }

    private static double p95Millis(List<Long> values) {
        if (values.isEmpty()) return 0.0;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.max(
                0,
                (int) Math.ceil(sorted.size() * 0.95) - 1
        );
        return sorted.get(index) / 1_000_000.0;
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
