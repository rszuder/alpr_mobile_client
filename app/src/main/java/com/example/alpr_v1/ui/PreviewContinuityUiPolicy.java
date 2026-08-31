package com.example.alpr_v1.ui;

import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.pipeline.TargetSnapshot;

/** Pure authority rule for preview presentation after continuity evaluation. */
public final class PreviewContinuityUiPolicy {
    private static final float DYNAMIC_OVERLAY_INVALIDATION_FRACTION = 0.12f;
    private static final float ABRUPT_UNEXPLAINED_CHANGE_FRACTION = 0.45f;
    private static final long MINIMUM_VEHICLE_OVERLAY_AGE_NANOS = 500_000_000L;
    private static final long MAXIMUM_VEHICLE_OVERLAY_AGE_NANOS = 1_500_000_000L;
    public enum Authority { COORDINATOR, LEGACY_FALLBACK }
    public enum DynamicOverlayDisposition {
        KEEP,
        KEEP_PREDICTED_VEHICLES,
        PLATE_ONLY,
        CLEAR
    }

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

    /**
     * Duża zmiana bez dowodu ruchu kamery musi zamknąć prezentację przed
     * wejściem do ciężkiego, potencjalnie zablokowanego koordynatora.
     */
    public static boolean shouldActivatePresentationBarrier(
            boolean sceneChanged,
            float changedFraction,
            boolean cameraMotion,
            boolean motionSettling
    ) {
        return sceneChanged
                && Float.isFinite(changedFraction)
                && changedFraction >= ABRUPT_UNEXPLAINED_CHANGE_FRACTION
                && !cameraMotion
                && !motionSettling;
    }

    /** Jawna decyzja warstwy UI, niezależna od życia encji domenowej. */
    public static DynamicOverlayDisposition dynamicOverlayDisposition(
            SceneHandlingMode mode,
            SceneTransitionDecision decision,
            boolean sceneChanged,
            float changedFraction
    ) {
        if (mode != SceneHandlingMode.DYNAMIC_CONTINUITY) {
            return DynamicOverlayDisposition.KEEP;
        }
        if (decision != null
                && (decision.action == SceneTransitionAction.HARD_RESET
                || decision.action == SceneTransitionAction.SOFT_REACQUIRE
                || decision.nextState == SceneContinuityState.REACQUIRING)) {
            return DynamicOverlayDisposition.CLEAR;
        }
        if (sceneChanged
                || (Float.isFinite(changedFraction)
                && changedFraction >= DYNAMIC_OVERLAY_INVALIDATION_FRACTION)) {
            return DynamicOverlayDisposition.KEEP_PREDICTED_VEHICLES;
        }
        return DynamicOverlayDisposition.KEEP;
    }

    public static long vehicleOverlayMaximumAgeNanos(long measuredMpIntervalNanos) {
        long doubled;
        if (measuredMpIntervalNanos <= 0L) {
            doubled = 0L;
        } else if (measuredMpIntervalNanos > Long.MAX_VALUE / 2L) {
            doubled = Long.MAX_VALUE;
        } else {
            doubled = measuredMpIntervalNanos * 2L;
        }
        return Math.min(
                MAXIMUM_VEHICLE_OVERLAY_AGE_NANOS,
                Math.max(MINIMUM_VEHICLE_OVERLAY_AGE_NANOS, doubled)
        );
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
