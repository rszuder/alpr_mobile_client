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
    private static final float UNMASKED_HARD_CUT_FRACTION = 0.60f;
    private static final float UNMASKED_HARD_CUT_MEAN_DELTA = 30f;
    private static final float SUPPORTED_GLOBAL_CUT_FRACTION = 0.30f;
    private static final float SUPPORTED_GLOBAL_CUT_MEAN_DELTA = 18f;
    private static final float BACKGROUND_SUPPORT_FRACTION = 0.10f;
    private static final float BACKGROUND_SUPPORT_MEAN_DELTA = 12f;
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
     * Direct-luma może prewencyjnie zamknąć prezentację np. przy skoku
     * ekspozycji. Jeżeli bitmapa tej samej generacji oraz koordynator zgodnie
     * potwierdzają stabilną scenę, bariera nie ma właściciela recovery i musi
     * zostać domknięta przed następną klatką pipeline'u.
     */
    public static boolean shouldCommitStablePresentationBarrier(
            boolean barrierActive,
            boolean rawPreviewChanged,
            SceneTransitionDecision decision
    ) {
        return barrierActive
                && !rawPreviewChanged
                && decision != null
                && decision.action == SceneTransitionAction.NONE
                && decision.nextState == SceneContinuityState.STABLE;
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

    /**
     * Direct-luma is authoritative for an abrupt stationary cut. Unlike a
     * regular preview change this signal cannot be left for a later bitmap to
     * confirm, because that bitmap may already use the new scene as reference.
     */
    public static boolean shouldForceHardSceneBoundaryFromDirectLuma(
            boolean sceneChanged,
            float changedFraction,
            boolean cameraMotion,
            boolean motionSettling
    ) {
        return shouldActivatePresentationBarrier(
                sceneChanged,
                changedFraction,
                cameraMotion,
                motionSettling
        );
    }

    /**
     * A second unmasked channel protects scene cuts hidden under a large old
     * vehicle ROI. Its stricter thresholds keep ordinary foreground motion on
     * the masked continuity path.
     */
    public static boolean shouldForceHardSceneBoundaryFromDirectLuma(
            boolean maskedSceneChanged,
            float maskedChangedFraction,
            float maskedMeanDelta,
            boolean globalSceneChanged,
            float globalChangedFraction,
            float globalMeanDelta,
            boolean cameraMotion,
            boolean motionSettling
    ) {
        boolean maskedCut = maskedSceneChanged
                && Float.isFinite(maskedChangedFraction)
                && maskedChangedFraction
                >= ABRUPT_UNEXPLAINED_CHANGE_FRACTION;
        boolean unmaskedCut = globalSceneChanged
                && Float.isFinite(globalChangedFraction)
                && Float.isFinite(globalMeanDelta)
                && globalChangedFraction >= UNMASKED_HARD_CUT_FRACTION
                && globalMeanDelta >= UNMASKED_HARD_CUT_MEAN_DELTA;
        boolean backgroundSupportedCut =
                Float.isFinite(globalChangedFraction)
                        && Float.isFinite(globalMeanDelta)
                        && Float.isFinite(maskedChangedFraction)
                        && Float.isFinite(maskedMeanDelta)
                        && globalChangedFraction
                        >= SUPPORTED_GLOBAL_CUT_FRACTION
                        && globalMeanDelta
                        >= SUPPORTED_GLOBAL_CUT_MEAN_DELTA
                        && maskedChangedFraction
                        >= BACKGROUND_SUPPORT_FRACTION
                        && maskedMeanDelta
                        >= BACKGROUND_SUPPORT_MEAN_DELTA;
        return (maskedCut || unmaskedCut || backgroundSupportedCut)
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

    /** Chroni nowszy direct-luma overlay przed pustym, opĂłĹşnionym wynikiem pipeline'u. */
    public static boolean shouldPreserveDynamicVehiclePresentation(
            boolean dynamicMode,
            boolean freshResultHasVehicle,
            boolean previewHasVehicle,
            boolean directLumaFresh,
            boolean presentationBarrierActive,
            long previewGeometryAgeNanos,
            long maximumVehicleAgeNanos
    ) {
        return dynamicMode
                && !freshResultHasVehicle
                && previewHasVehicle
                && directLumaFresh
                && !presentationBarrierActive
                && previewGeometryAgeNanos >= 0L
                && previewGeometryAgeNanos
                <= Math.max(0L, maximumVehicleAgeNanos);
    }

    public static boolean shouldClearFocusedTargetAfterRecovery(
            boolean vehiclePoolRecovered,
            String recoveryResult
    ) {
        return vehiclePoolRecovered
                && "VEHICLE_POOL_RECOVERED".equals(recoveryResult);
    }

    /** Marker należy do całej aktywnej sesji pojazdu, nie tylko etapu MT. */
    public static long activeVehicleMarkerEntityId(
            long activeEntityId,
            long presentedPlateEntityId,
            boolean plateGeometryVisible
    ) {
        return Math.max(0L, activeEntityId);
    }

    /**
     * Opóźniona klatka KLT nie może ponownie opublikować tablicy po zwolnieniu
     * lub zmianie aktywnej sesji Scan.
     */
    public static boolean acceptsTrackedScanPlate(
            boolean scanRunActive,
            long activeEntityId,
            long presentedPlateEntityId,
            boolean presentedPlateAvailable
    ) {
        if (!scanRunActive) return true;
        long active = Math.max(0L, activeEntityId);
        return presentedPlateAvailable
                && active > 0L
                && presentedPlateEntityId == active;
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
