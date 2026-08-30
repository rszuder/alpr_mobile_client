package com.example.alpr_v1.ui;

import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.pipeline.TargetSnapshot;

/** Pure authority rule for preview presentation after continuity evaluation. */
public final class PreviewContinuityUiPolicy {
    private static final float DYNAMIC_OVERLAY_INVALIDATION_FRACTION = 0.12f;
    public enum Authority { COORDINATOR, LEGACY_FALLBACK }

    public static final class Outcome {
        public final Authority authority;
        public final boolean renderCoordinatorDecision;
        public final boolean presentTrackedOverlay;
        public final boolean rebaseAcceptedPreviewScene;
        public final boolean legacySceneInvalidation;
        public final boolean legacyTrackingLossInvalidation;

        private Outcome(
                Authority authority,
                boolean renderCoordinatorDecision,
                boolean presentTrackedOverlay,
                boolean rebaseAcceptedPreviewScene,
                boolean legacySceneInvalidation,
                boolean legacyTrackingLossInvalidation
        ) {
            this.authority = authority;
            this.renderCoordinatorDecision = renderCoordinatorDecision;
            this.presentTrackedOverlay = presentTrackedOverlay;
            this.rebaseAcceptedPreviewScene = rebaseAcceptedPreviewScene;
            this.legacySceneInvalidation = legacySceneInvalidation;
            this.legacyTrackingLossInvalidation =
                    legacyTrackingLossInvalidation;
        }
    }

    private PreviewContinuityUiPolicy() {}

    public static Outcome decide(
            SceneTransitionDecision decision,
            boolean rawPreviewChanged,
            boolean trackedOverlayAvailable
    ) {
        if (decision != null) {
            boolean acceptedStable = decision.action == SceneTransitionAction.NONE
                    && decision.nextState == SceneContinuityState.STABLE;
            return new Outcome(
                    Authority.COORDINATOR,
                    true,
                    acceptedStable && trackedOverlayAvailable,
                    acceptedStable && rawPreviewChanged,
                    false,
                    false
            );
        }
        return new Outcome(
                Authority.LEGACY_FALLBACK,
                false,
                !rawPreviewChanged && trackedOverlayAvailable,
                false,
                rawPreviewChanged,
                !rawPreviewChanged && !trackedOverlayAvailable
        );
    }

    /** Signals that Preview owns a reference captured before coordinator recovery. */
    public static boolean requestsRecoveryRebase(
            SceneTransitionDecision decision
    ) {
        return decision != null
                && decision.nextState == SceneContinuityState.REACQUIRING;
    }

    public static boolean shouldApplyRecoveredSceneRebase(
            long requestedRevision,
            long appliedRevision,
            SceneContinuityState coordinatorState
    ) {
        return requestedRevision > appliedRevision
                && coordinatorState == SceneContinuityState.STABLE;
    }

    /**
     * Direct-luma nie ma bitmapy potrzebnej do przestawienia referencji sceny.
     * Po zleceniu recovery musi ustapic monitorowi Preview do chwili, w ktorej
     * ten zakotwiczy nowa referencje; inaczej stara referencja natychmiast
     * uruchamia kolejne SOFT_REACQUIRE.
     */
    public static boolean shouldSuspendDirectLumaEvidence(
            long requestedRevision,
            long appliedRevision
    ) {
        return requestedRevision > appliedRevision;
    }

    /** UI safety gate independent from the stricter scene-boundary decision. */
    public static boolean shouldInvalidateDynamicOverlay(
            SceneHandlingMode mode,
            boolean sceneChanged,
            float changedFraction
    ) {
        return mode == SceneHandlingMode.DYNAMIC_CONTINUITY
                && (sceneChanged
                || (Float.isFinite(changedFraction)
                && changedFraction >= DYNAMIC_OVERLAY_INVALIDATION_FRACTION));
    }

    public static boolean shouldClearFocusedTargetAfterRecovery(
            boolean vehiclePoolRecovered,
            String recoveryResult
    ) {
        return vehiclePoolRecovered
                && "VEHICLE_POOL_RECOVERED".equals(recoveryResult);
    }

    public static boolean isEstablishedFocusedTarget(
            TargetSnapshot.State previousState,
            long previousLockedTrackId
    ) {
        return previousLockedTrackId > 0L
                || previousState == TargetSnapshot.State.TRACKING
                || previousState == TargetSnapshot.State.LOCKED;
    }
}
