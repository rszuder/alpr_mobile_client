package com.example.alpr_v1.continuity;

/**
 * Central policy owner that converts merged evidence into one deduplicated runtime action.
 * This class owns policy state only; runtime side effects remain with its caller.
 */
public final class SceneTransitionCoordinator {
    private final SceneContinuityProfile profile;
    private final TargetContinuityEvaluator targetEvaluator;
    private final VehicleContinuityEvaluator vehicleEvaluator;
    private final MotionExplanationEvaluator motionEvaluator;
    private final ContinuityBreakEvaluator breakEvaluator;
    private final long transitionCooldownNanos;

    private SceneHandlingMode mode;
    private SceneContinuityState currentState = SceneContinuityState.STABLE;
    private ContinuityAssessment assessment = ContinuityAssessment.none();
    private long decisionRevision;
    private long sceneGeneration;
    private long visualEpoch;
    private long cameraTransformGeneration;
    private long hardResetRevision;
    private long visualEpochRevision;
    private long lastTransitionNanos;
    private long stateEnteredNanos;
    private long unexplainedSinceNanos = -1L;
    private long reacquireStartedRuntimeNanos = -1L;
    private long lastSourceFrameId = -1L;
    private long lastSourceTimestampNanos = -1L;
    private boolean finalizationSuspended;
    private boolean heavyInferenceSuspended;
    private boolean reacquireFailed;
    private boolean pendingActiveTargetRelease;
    private boolean lastActiveTargetPresent;
    private ReacquireContext reacquireContext;
    private ReacquireContext lastReacquireContext;
    private String lastReacquireResult = "";
    private boolean lastReacquireVehiclePoolRecovered;
    private boolean lastReacquireDeadlineReached;

    public SceneTransitionCoordinator(
            SceneHandlingMode mode,
            SceneContinuityProfile profile
    ) {
        this(
                mode,
                profile,
                new TargetContinuityEvaluator(),
                new VehicleContinuityEvaluator(),
                new MotionExplanationEvaluator(),
                new ContinuityBreakEvaluator(),
                profile == null ? 0L : profile.motionSettleNanos
        );
    }

    SceneTransitionCoordinator(
            SceneHandlingMode mode,
            SceneContinuityProfile profile,
            TargetContinuityEvaluator targetEvaluator,
            VehicleContinuityEvaluator vehicleEvaluator,
            MotionExplanationEvaluator motionEvaluator,
            ContinuityBreakEvaluator breakEvaluator,
            long transitionCooldownNanos
    ) {
        this.mode = Contracts.required("mode", mode);
        this.profile = Contracts.required("profile", profile);
        this.targetEvaluator = Contracts.required("targetEvaluator", targetEvaluator);
        this.vehicleEvaluator = Contracts.required("vehicleEvaluator", vehicleEvaluator);
        this.motionEvaluator = Contracts.required("motionEvaluator", motionEvaluator);
        this.breakEvaluator = Contracts.required("breakEvaluator", breakEvaluator);
        this.transitionCooldownNanos = Contracts.nonNegative(
                "transitionCooldownNanos", transitionCooldownNanos
        );
    }

    public synchronized SceneTransitionDecision observe(
            SceneEvidence evidence,
            long nowNanos
    ) {
        Contracts.required("evidence", evidence);
        Contracts.nonNegative("nowNanos", nowNanos);

        if (isDuplicate(evidence) && !pendingActiveTargetRelease && !reacquireFailed) {
            return idleDecision("duplicate_evidence");
        }
        rememberEvidence(evidence);

        if (evidence.structuralChange()) {
            return hardReset(structuralReason(evidence), nowNanos);
        }
        if (currentState == SceneContinuityState.HARD_RESETTING) {
            enterState(SceneContinuityState.STABLE, nowNanos);
        }

        assessment = assess(evidence);
        lastActiveTargetPresent = evidence.focusedTrackingLost
                || evidence.target.level != TargetContinuityLevel.NO_TARGET
                && evidence.target.level != TargetContinuityLevel.LOST;
        if (currentState == SceneContinuityState.REACQUIRING
                && reacquireContext != null) {
            reacquireContext = reacquireContext.observe(assessment);
        }
        /*
         * W trybie strict potwierdzona zmiana obrazu jest nadrzędna wobec
         * timeoutu rozpoczętego wcześniej soft reacquire. Na wolnym urządzeniu
         * pierwsza klatka po cięciu może zgłosić tylko utratę lokalnego trackera,
         * a dopiero kolejna rawVisualChange. Nie wolno wtedy zakończyć recovery
         * ścieżką dynamiczną tuż przed obsłużeniem granicy sceny.
         */
        if (mode == SceneHandlingMode.STRICT_SCENE_BOUNDARY
                && evidence.rawVisualChange) {
            return observeStrict(evidence, nowNanos);
        }
        if (currentState == SceneContinuityState.REACQUIRING
                && !pendingActiveTargetRelease
                && (reacquireFailed || reacquireDeadlineReached(nowNanos))) {
            return finishUnsuccessfulReacquire(nowNanos);
        }
        if (pendingActiveTargetRelease) {
            pendingActiveTargetRelease = false;
            resetRecoveryState();
            enterState(SceneContinuityState.STABLE, nowNanos);
            finalizationSuspended = false;
            heavyInferenceSuspended = false;
            return emitReleaseActiveTarget(
                    nowNanos,
                    "active_target_lost_pool_preserved"
            );
        }
        if (mode == SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
            return observeStrict(evidence, nowNanos);
        }
        return observeDynamic(evidence, nowNanos);
    }

    public synchronized void setMode(SceneHandlingMode mode, long nowNanos) {
        Contracts.required("mode", mode);
        Contracts.nonNegative("nowNanos", nowNanos);
        if (this.mode == mode) return;
        this.mode = mode;
        resetRecoveryState();
        enterState(SceneContinuityState.STABLE, nowNanos);
        assessment = ContinuityAssessment.none();
        finalizationSuspended = false;
        heavyInferenceSuspended = false;
        clearEvidenceDeduplication();
    }

    public synchronized void onSoftReacquireResult(
            SoftReacquireResult result,
            long nowNanos
    ) {
        Contracts.required("result", result);
        Contracts.nonNegative("nowNanos", nowNanos);
        if (currentState != SceneContinuityState.REACQUIRING) return;

        switch (result) {
            case TARGET_RECOVERED:
            case VEHICLE_POOL_RECOVERED:
                recordReacquireOutcome(
                        result.name(),
                        result == SoftReacquireResult.VEHICLE_POOL_RECOVERED,
                        false
                );
                resetRecoveryState();
                enterState(SceneContinuityState.STABLE, nowNanos);
                finalizationSuspended = false;
                heavyInferenceSuspended = false;
                break;
            case ACTIVE_TARGET_LOST:
                recordReacquireOutcome(result.name(), true, false);
                pendingActiveTargetRelease = true;
                reacquireFailed = true;
                break;
            case FAILED:
                recordReacquireOutcome(result.name(), false, false);
                reacquireFailed = true;
                break;
            default:
                throw new AssertionError("Unhandled reacquire result: " + result);
        }
    }

    public synchronized SceneContinuitySnapshot snapshot() {
        return new SceneContinuitySnapshot(
                mode,
                currentState,
                assessment.classification,
                decisionRevision,
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                hardResetRevision,
                visualEpochRevision,
                finalizationSuspended,
                heavyInferenceSuspended,
                lastTransitionNanos,
                assessment
        );
    }

    public synchronized ContinuityStamp stamp(long sourceTimestampNanos) {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceTimestampNanos
        );
    }

    public synchronized ReacquireTelemetry reacquireTelemetry() {
        ReacquireContext context = reacquireContext != null
                ? reacquireContext : lastReacquireContext;
        return ReacquireTelemetry.from(
                context,
                reacquireContext != null,
                lastReacquireResult,
                lastReacquireVehiclePoolRecovered,
                lastReacquireDeadlineReached
        );
    }

    /** Controlled zoom/transform changes geometry but do not create a new scene. */
    public synchronized long advanceCameraTransformGeneration(long nowNanos) {
        Contracts.nonNegative("nowNanos", nowNanos);
        cameraTransformGeneration++;
        lastTransitionNanos = nowNanos;
        return cameraTransformGeneration;
    }

    public synchronized SceneTransitionDecision requestStructuralReset(
            String reason,
            long nowNanos
    ) {
        Contracts.nonNegative("nowNanos", nowNanos);
        assessment = new ContinuityAssessment(
                VisualChangeClassification.CONTINUITY_BREAK,
                0f, 0f, 0f, 1f,
                false, false, false,
                Contracts.reason(reason).isEmpty() ? "structural_reset" : reason
        );
        return hardReset(assessment.reason, nowNanos);
    }

    public synchronized SceneTransitionDecision requestSoftReacquire(
            String reason,
            long nowNanos
    ) {
        return requestSoftReacquire(
                reason,
                nowNanos,
                Math.max(0L, lastSourceTimestampNanos)
        );
    }

    public synchronized SceneTransitionDecision requestSoftReacquire(
            String reason,
            long nowNanos,
            long triggerSourceTimestampNanos
    ) {
        Contracts.nonNegative("nowNanos", nowNanos);
        Contracts.nonNegative(
                "triggerSourceTimestampNanos", triggerSourceTimestampNanos
        );
        if (currentState == SceneContinuityState.HARD_RESETTING) {
            enterState(SceneContinuityState.STABLE, nowNanos);
        }
        return beginSoftReacquire(
                nowNanos,
                triggerSourceTimestampNanos,
                Contracts.reason(reason).isEmpty() ? "soft_reacquire_requested" : reason
        );
    }

    private ContinuityAssessment assess(SceneEvidence evidence) {
        float targetScore = targetEvaluator.evaluate(evidence.target, profile);
        float vehicleScore = vehicleEvaluator.evaluate(evidence.vehicles);
        float motionScore = motionEvaluator.evaluate(evidence, targetScore, vehicleScore);
        float cutScore = breakEvaluator.evaluate(
                evidence, targetScore, vehicleScore, motionScore
        );
        boolean stationaryLocalContradiction = evidence.rawVisualChange
                && !evidence.motion.cameraMoving
                && !evidence.motion.rapidCameraMotion
                && !evidence.motion.cameraTransformInProgress
                && evidence.target.localAppearanceValidated
                && evidence.target.plateAppearanceSimilarity
                < profile.localAppearanceContradictionThreshold;
        boolean stationaryStaleTargetEvidence = evidence.rawVisualChange
                && !evidence.motion.cameraMoving
                && !evidence.motion.rapidCameraMotion
                && !evidence.motion.cameraTransformInProgress
                && evidence.target.level != TargetContinuityLevel.NO_TARGET
                && evidence.target.measurementAgeNanos
                > profile.maximumFocusedEvidenceAgeNanos;
        boolean targetPreserved = targetScore >= profile.minimumTargetContinuityToPreserve
                && !stationaryLocalContradiction
                && !stationaryStaleTargetEvidence;
        boolean poolPreserved = vehicleScore >= profile.minimumVehicleContinuityToPreserve;
        boolean motionExplained = motionScore >= profile.minimumMotionExplanation
                && !stationaryLocalContradiction
                && !stationaryStaleTargetEvidence;

        VisualChangeClassification classification;
        String reason;
        if (!evidence.rawVisualChange) {
            classification = VisualChangeClassification.NONE;
            reason = "no_raw_visual_change";
        } else if (motionExplained && (targetPreserved || poolPreserved)) {
            classification = VisualChangeClassification.MOTION_EXPLAINED_CHANGE;
            reason = targetPreserved
                    ? "local_target_explains_visual_change"
                    : "vehicle_pool_explains_visual_change";
        } else if (stationaryLocalContradiction) {
            classification = VisualChangeClassification.UNEXPLAINED_CHANGE;
            reason = "stationary_local_appearance_contradiction";
        } else if (stationaryStaleTargetEvidence) {
            classification = VisualChangeClassification.UNEXPLAINED_CHANGE;
            reason = "stationary_target_evidence_predates_visual_change";
        } else if (!targetPreserved && !poolPreserved && !motionExplained) {
            classification = VisualChangeClassification.UNEXPLAINED_CHANGE;
            reason = "visual_change_has_no_continuity_explanation";
        } else {
            classification = VisualChangeClassification.RAW_VISUAL_CHANGE;
            reason = "visual_change_requires_more_evidence";
        }

        boolean freshValidatedTarget = targetPreserved
                && evidence.target.geometryValidated
                && (evidence.target.freshVehicleMeasurement
                || evidence.target.freshPlateMeasurement)
                && evidence.target.level != TargetContinuityLevel.PREDICTED_ONLY;
        boolean continuityAllowsFinalization = !evidence.rawVisualChange
                || freshValidatedTarget;
        return new ContinuityAssessment(
                classification,
                targetScore,
                vehicleScore,
                motionScore,
                cutScore,
                targetPreserved,
                poolPreserved,
                continuityAllowsFinalization,
                reason
        );
    }

    private SceneTransitionDecision observeStrict(
            SceneEvidence evidence,
            long nowNanos
    ) {
        if (currentState == SceneContinuityState.REACQUIRING
                && !evidence.rawVisualChange) {
            return observeNoRawChange(evidence, nowNanos);
        }
        if (currentState != SceneContinuityState.REACQUIRING
                && !evidence.rawVisualChange
                && (evidence.focusedTrackingLost
                || evidence.focusedTrackingDegraded)) {
            return beginSoftReacquire(
                    nowNanos,
                    evidence.sourceTimestampNanos,
                    evidence.focusedTrackingLost
                            ? "strict_focused_tracking_lost"
                            : "strict_focused_tracking_degraded"
            );
        }
        if (evidence.rawVisualChange) {
            assessment = withClassification(
                    assessment,
                    VisualChangeClassification.CONTINUITY_BREAK,
                    "strict_raw_visual_change"
            );
            return hardReset(assessment.reason, nowNanos);
        }
        resetRecoveryState();
        enterState(SceneContinuityState.STABLE, nowNanos);
        finalizationSuspended = !assessment.finalizationAllowed;
        heavyInferenceSuspended = false;
        return emitNone(nowNanos, assessment.reason);
    }

    private SceneTransitionDecision observeDynamic(
            SceneEvidence evidence,
            long nowNanos
    ) {
        if (currentState != SceneContinuityState.REACQUIRING
                && !evidence.rawVisualChange
                && (evidence.focusedTrackingLost
                || evidence.focusedTrackingDegraded)) {
            if (evidence.motion.cameraMoving
                    || evidence.motion.rapidCameraMotion
                    || evidence.motion.cameraTransformInProgress) {
                return beginSoftHold(nowNanos, "local_tracking_loss_during_motion");
            }
            return beginSoftReacquire(
                    nowNanos,
                    evidence.sourceTimestampNanos,
                    evidence.focusedTrackingLost
                            ? "focused_tracking_lost_without_global_change"
                            : "focused_tracking_degraded_without_global_change"
            );
        }

        if (!evidence.rawVisualChange) {
            return observeNoRawChange(evidence, nowNanos);
        }

        if (assessment.classification == VisualChangeClassification.UNEXPLAINED_CHANGE
                || assessment.classification == VisualChangeClassification.RAW_VISUAL_CHANGE) {
            if (unexplainedSinceNanos < 0L) unexplainedSinceNanos = nowNanos;
        } else {
            unexplainedSinceNanos = -1L;
        }

        if (assessment.classification
                == VisualChangeClassification.MOTION_EXPLAINED_CHANGE
                && assessment.focusedTargetPreserved
                && !evidence.motion.rapidCameraMotion) {
            resetRecoveryState();
            enterState(SceneContinuityState.STABLE, nowNanos);
            finalizationSuspended = !assessment.finalizationAllowed;
            heavyInferenceSuspended = false;
            return emitNone(nowNanos, assessment.reason);
        }

        if (evidence.motion.cameraMoving
                || evidence.motion.rapidCameraMotion
                || evidence.motion.cameraTransformInProgress) {
            return beginSoftHold(nowNanos, "motion_requires_stable_observation");
        }
        return beginSoftReacquire(
                nowNanos,
                evidence.sourceTimestampNanos,
                "stationary_visual_change_requires_reacquire"
        );
    }

    private SceneTransitionDecision observeNoRawChange(
            SceneEvidence evidence,
            long nowNanos
    ) {
        if (currentState == SceneContinuityState.MOTION_HOLD) {
            if (assessment.focusedTargetPreserved) {
                resetRecoveryState();
                enterState(SceneContinuityState.STABLE, nowNanos);
                finalizationSuspended = !assessment.finalizationAllowed;
                heavyInferenceSuspended = false;
                return emitNone(nowNanos, "target_continuity_restored_during_hold");
            }
            long holdDuration = elapsedSince(stateEnteredNanos, nowNanos);
            boolean motionSettled = !evidence.motion.cameraMoving
                    && !evidence.motion.rapidCameraMotion
                    && !evidence.motion.cameraTransformInProgress
                    && holdDuration >= profile.motionSettleNanos;
            if (motionSettled || holdDuration >= profile.maximumSoftHoldNanos) {
                return beginSoftReacquire(
                        nowNanos,
                        evidence.sourceTimestampNanos,
                        "motion_settled_force_fresh_reacquire"
                );
            }
            finalizationSuspended = true;
            heavyInferenceSuspended = true;
            return emitNone(nowNanos, "motion_hold_waiting_for_settle");
        }

        if (currentState == SceneContinuityState.REACQUIRING) {
            if (assessment.focusedTargetPreserved && assessment.finalizationAllowed) {
                resetRecoveryState();
                enterState(SceneContinuityState.STABLE, nowNanos);
                finalizationSuspended = false;
                heavyInferenceSuspended = false;
                return emitNone(nowNanos, "fresh_target_revalidated");
            }
            finalizationSuspended = true;
            heavyInferenceSuspended = false;
            return emitNone(nowNanos, "soft_reacquire_in_progress");
        }

        resetRecoveryState();
        enterState(SceneContinuityState.STABLE, nowNanos);
        finalizationSuspended = !assessment.finalizationAllowed;
        heavyInferenceSuspended = false;
        return emitNone(nowNanos, assessment.reason);
    }

    private boolean reacquireDeadlineReached(long nowNanos) {
        return reacquireContext != null
                && elapsedSince(reacquireContext.startedRuntimeNanos, nowNanos)
                >= profile.reacquireTimeoutNanos;
    }

    private SceneTransitionDecision finishUnsuccessfulReacquire(long nowNanos) {
        ReacquireContext context = reacquireContext;
        boolean deadlineReached = reacquireDeadlineReached(nowNanos);
        recordReacquireOutcome("FAILED", false, deadlineReached);
        boolean confirmedBreak = context != null
                && context.maximumCutEvidenceDuringRecovery
                >= profile.continuityBreakThreshold;
        if (confirmedBreak) {
            assessment = withClassification(
                    assessment,
                    VisualChangeClassification.CONTINUITY_BREAK,
                    "reacquire_failed_trigger_evidence_confirms_break"
            );
            return hardReset(assessment.reason, nowNanos);
        }
        boolean releaseTarget = context != null && context.activeTargetPresent;
        resetRecoveryState();
        enterState(SceneContinuityState.STABLE, nowNanos);
        finalizationSuspended = false;
        heavyInferenceSuspended = false;
        if (releaseTarget) {
            return emitReleaseActiveTarget(
                    nowNanos,
                    "reacquire_deadline_without_break_release_active_target"
            );
        }
        return emitNone(nowNanos, "reacquire_deadline_without_break_return_stable");
    }

    private SceneTransitionDecision beginSoftHold(long nowNanos, String reason) {
        if (currentState == SceneContinuityState.MOTION_HOLD) {
            finalizationSuspended = true;
            heavyInferenceSuspended = true;
            return emitNone(nowNanos, "motion_hold_already_active");
        }
        enterState(SceneContinuityState.MOTION_HOLD, nowNanos);
        finalizationSuspended = true;
        heavyInferenceSuspended = true;
        return emit(
                SceneTransitionAction.SOFT_HOLD,
                true, true, true,
                true, true, true,
                false, false, false,
                false, false,
                true, false,
                reason,
                nowNanos
        );
    }

    private SceneTransitionDecision beginSoftReacquire(
            long nowNanos,
            long triggerSourceTimestampNanos,
            String reason
    ) {
        if (currentState == SceneContinuityState.REACQUIRING) {
            finalizationSuspended = true;
            heavyInferenceSuspended = false;
            return emitNone(nowNanos, "soft_reacquire_already_active");
        }
        enterState(SceneContinuityState.REACQUIRING, nowNanos);
        reacquireStartedRuntimeNanos = nowNanos;
        reacquireContext = ReacquireContext.begin(
                assessment,
                nowNanos,
                triggerSourceTimestampNanos,
                lastActiveTargetPresent
        );
        lastReacquireContext = null;
        lastReacquireResult = "";
        lastReacquireVehiclePoolRecovered = false;
        lastReacquireDeadlineReached = false;
        reacquireFailed = false;
        finalizationSuspended = true;
        heavyInferenceSuspended = false;
        return emit(
                SceneTransitionAction.SOFT_REACQUIRE,
                true, true, true,
                true, false, true,
                true, true, false,
                true, true,
                true, false,
                reason,
                nowNanos
        );
    }

    private SceneTransitionDecision emitReleaseActiveTarget(
            long nowNanos,
            String reason
    ) {
        return emit(
                SceneTransitionAction.RELEASE_ACTIVE_TARGET,
                true, false, false,
                true, false, false,
                false, false, true,
                true, true,
                false, false,
                reason,
                nowNanos
        );
    }

    private SceneTransitionDecision hardReset(String reason, long nowNanos) {
        if (currentState == SceneContinuityState.HARD_RESETTING
                && elapsedSince(lastTransitionNanos, nowNanos) < transitionCooldownNanos) {
            return idleDecision("hard_reset_deduplicated");
        }
        resetRecoveryState();
        enterState(SceneContinuityState.HARD_RESETTING, nowNanos);
        finalizationSuspended = true;
        heavyInferenceSuspended = true;
        return emit(
                SceneTransitionAction.HARD_RESET,
                false, false, false,
                true, true, true,
                false, false, false,
                true, true,
                true, true,
                reason,
                nowNanos
        );
    }

    private SceneTransitionDecision emitNone(long nowNanos, String reason) {
        decisionRevision++;
        return new SceneTransitionDecision(
                decisionRevision,
                SceneTransitionAction.NONE,
                mode,
                currentState,
                assessment,
                true, true, true,
                false,
                heavyInferenceSuspended,
                finalizationSuspended,
                false, false, false,
                false, false,
                false, false,
                reason
        );
    }

    private SceneTransitionDecision idleDecision(String reason) {
        return new SceneTransitionDecision(
                decisionRevision,
                SceneTransitionAction.NONE,
                mode,
                currentState,
                assessment,
                true, true, true,
                false,
                heavyInferenceSuspended,
                finalizationSuspended,
                false, false, false,
                false, false,
                false, false,
                reason
        );
    }

    private SceneTransitionDecision emit(
            SceneTransitionAction action,
            boolean preserveVehicleEntities,
            boolean preserveTargetSession,
            boolean preserveDomainConsensus,
            boolean cancelInFlightInference,
            boolean suspendHeavyInference,
            boolean suspendFinalization,
            boolean forceMpRefresh,
            boolean forceMtRefresh,
            boolean releaseOnlyActiveTarget,
            boolean clearVehicleRoiCache,
            boolean resetFocusedTracker,
            boolean incrementVisualEpoch,
            boolean incrementSceneGeneration,
            String reason,
            long nowNanos
    ) {
        decisionRevision++;
        if (incrementSceneGeneration) {
            sceneGeneration++;
            hardResetRevision = decisionRevision;
        }
        if (incrementVisualEpoch) {
            visualEpoch++;
            visualEpochRevision = decisionRevision;
        }
        lastTransitionNanos = nowNanos;
        return new SceneTransitionDecision(
                decisionRevision,
                action,
                mode,
                currentState,
                assessment,
                preserveVehicleEntities,
                preserveTargetSession,
                preserveDomainConsensus,
                cancelInFlightInference,
                suspendHeavyInference,
                suspendFinalization,
                forceMpRefresh,
                forceMtRefresh,
                releaseOnlyActiveTarget,
                clearVehicleRoiCache,
                resetFocusedTracker,
                incrementVisualEpoch,
                incrementSceneGeneration,
                reason
        );
    }

    private void enterState(SceneContinuityState state, long nowNanos) {
        if (currentState != state) {
            currentState = state;
            stateEnteredNanos = nowNanos;
            lastTransitionNanos = nowNanos;
        }
    }

    private void resetRecoveryState() {
        unexplainedSinceNanos = -1L;
        reacquireStartedRuntimeNanos = -1L;
        reacquireFailed = false;
        pendingActiveTargetRelease = false;
        reacquireContext = null;
    }

    private void recordReacquireOutcome(
            String result,
            boolean vehiclePoolRecovered,
            boolean deadlineReached
    ) {
        if (reacquireContext != null) lastReacquireContext = reacquireContext;
        lastReacquireResult = result == null ? "" : result;
        lastReacquireVehiclePoolRecovered = vehiclePoolRecovered;
        lastReacquireDeadlineReached = deadlineReached;
    }

    private boolean isDuplicate(SceneEvidence evidence) {
        return evidence.sourceFrameId == lastSourceFrameId
                && evidence.sourceTimestampNanos == lastSourceTimestampNanos;
    }

    private void rememberEvidence(SceneEvidence evidence) {
        lastSourceFrameId = evidence.sourceFrameId;
        lastSourceTimestampNanos = evidence.sourceTimestampNanos;
    }

    private void clearEvidenceDeduplication() {
        lastSourceFrameId = -1L;
        lastSourceTimestampNanos = -1L;
    }

    private static long elapsedSince(long startNanos, long nowNanos) {
        return nowNanos >= startNanos ? nowNanos - startNanos : 0L;
    }

    private static String structuralReason(SceneEvidence evidence) {
        if (evidence.cameraRestarted) return "camera_restarted";
        if (evidence.lensChanged) return "physical_camera_changed";
        if (evidence.sourceDimensionsChanged) return "source_dimensions_changed";
        if (evidence.orientationChanged) return "unsupported_orientation_change";
        return "structural_change";
    }

    private static ContinuityAssessment withClassification(
            ContinuityAssessment source,
            VisualChangeClassification classification,
            String reason
    ) {
        return new ContinuityAssessment(
                classification,
                source.targetContinuityScore,
                source.vehicleContinuityScore,
                source.motionExplanationScore,
                source.cutEvidenceScore,
                source.focusedTargetPreserved,
                source.vehiclePoolPreserved,
                false,
                reason
        );
    }
}
