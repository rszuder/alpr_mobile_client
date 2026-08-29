package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityAssessment;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionDecision;

import org.junit.Test;

public final class PreviewContinuityUiPolicyTest {
    @Test
    public void coordinatorNoneStablePreventsLegacyInvalidationOnChangedFrame() {
        SceneTransitionDecision decision = SceneTransitionDecision.none(
                1L,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                ContinuityAssessment.none()
        );

        PreviewContinuityUiPolicy.Outcome outcome =
                PreviewContinuityUiPolicy.decide(
                        decision,
                        true,
                        false
                );

        assertEquals(
                PreviewContinuityUiPolicy.Authority.COORDINATOR,
                outcome.authority
        );
        assertTrue(outcome.renderCoordinatorDecision);
        assertTrue(outcome.rebaseAcceptedPreviewScene);
        assertFalse(outcome.legacySceneInvalidation);
        assertFalse(outcome.legacyTrackingLossInvalidation);
    }

    @Test
    public void coordinatorStableDecisionPresentsTrackedOverlay() {
        SceneTransitionDecision decision = SceneTransitionDecision.none(
                2L,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                ContinuityAssessment.none()
        );

        PreviewContinuityUiPolicy.Outcome outcome =
                PreviewContinuityUiPolicy.decide(decision, false, true);

        assertTrue(outcome.presentTrackedOverlay);
        assertFalse(outcome.legacySceneInvalidation);
        assertFalse(outcome.legacyTrackingLossInvalidation);
    }

    @Test
    public void missingDecisionIsTheOnlyLegacyFallback() {
        PreviewContinuityUiPolicy.Outcome changed =
                PreviewContinuityUiPolicy.decide(null, true, false);
        PreviewContinuityUiPolicy.Outcome lost =
                PreviewContinuityUiPolicy.decide(null, false, false);

        assertEquals(
                PreviewContinuityUiPolicy.Authority.LEGACY_FALLBACK,
                changed.authority
        );
        assertTrue(changed.legacySceneInvalidation);
        assertTrue(lost.legacyTrackingLossInvalidation);
    }
}
