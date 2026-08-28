package com.example.alpr_v1.continuity;

/** Immutable command returned by the future central scene policy. */
public final class SceneTransitionDecision {
    public final long revision;
    public final SceneTransitionAction action;
    public final SceneHandlingMode mode;
    public final SceneContinuityState nextState;
    public final ContinuityAssessment assessment;
    public final boolean preserveVehicleEntities;
    public final boolean preserveTargetSession;
    public final boolean preserveDomainConsensus;
    public final boolean cancelInFlightInference;
    public final boolean suspendHeavyInference;
    public final boolean suspendFinalization;
    public final boolean forceMpRefresh;
    public final boolean forceMtRefresh;
    public final boolean releaseOnlyActiveTarget;
    public final boolean clearVehicleRoiCache;
    public final boolean resetFocusedTracker;
    public final boolean incrementVisualEpoch;
    public final boolean incrementSceneGeneration;
    public final String reason;

    public SceneTransitionDecision(
            long revision,
            SceneTransitionAction action,
            SceneHandlingMode mode,
            SceneContinuityState nextState,
            ContinuityAssessment assessment,
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
            String reason
    ) {
        this.revision = Contracts.nonNegative("revision", revision);
        this.action = Contracts.required("action", action);
        this.mode = Contracts.required("mode", mode);
        this.nextState = Contracts.required("nextState", nextState);
        this.assessment = Contracts.required("assessment", assessment);
        if (incrementSceneGeneration
                && (action != SceneTransitionAction.HARD_RESET
                || !incrementVisualEpoch)) {
            throw new IllegalArgumentException(
                    "scene generation may change only with a visual-epoch hard reset"
            );
        }
        if (releaseOnlyActiveTarget
                && action != SceneTransitionAction.RELEASE_ACTIVE_TARGET) {
            throw new IllegalArgumentException(
                    "releaseOnlyActiveTarget requires RELEASE_ACTIVE_TARGET"
            );
        }
        this.preserveVehicleEntities = preserveVehicleEntities;
        this.preserveTargetSession = preserveTargetSession;
        this.preserveDomainConsensus = preserveDomainConsensus;
        this.cancelInFlightInference = cancelInFlightInference;
        this.suspendHeavyInference = suspendHeavyInference;
        this.suspendFinalization = suspendFinalization;
        this.forceMpRefresh = forceMpRefresh;
        this.forceMtRefresh = forceMtRefresh;
        this.releaseOnlyActiveTarget = releaseOnlyActiveTarget;
        this.clearVehicleRoiCache = clearVehicleRoiCache;
        this.resetFocusedTracker = resetFocusedTracker;
        this.incrementVisualEpoch = incrementVisualEpoch;
        this.incrementSceneGeneration = incrementSceneGeneration;
        this.reason = Contracts.reason(reason);
    }

    public static SceneTransitionDecision none(
            long revision,
            SceneHandlingMode mode,
            ContinuityAssessment assessment
    ) {
        return new SceneTransitionDecision(
                revision,
                SceneTransitionAction.NONE,
                mode,
                SceneContinuityState.STABLE,
                assessment,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                assessment == null ? "" : assessment.reason
        );
    }
}
