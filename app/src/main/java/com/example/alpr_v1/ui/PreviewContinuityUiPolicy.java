package com.example.alpr_v1.ui;

import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;

/** Pure authority rule for preview presentation after continuity evaluation. */
public final class PreviewContinuityUiPolicy {
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
}
