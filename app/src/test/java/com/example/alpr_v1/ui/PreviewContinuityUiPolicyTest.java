package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityAssessment;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.pipeline.TargetSnapshot;

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

    @Test
    public void recoveryKeepsPreviewRebasePendingUntilCoordinatorIsStable() {
        SceneTransitionDecision reacquiring = new SceneTransitionDecision(
                3L,
                SceneTransitionAction.SOFT_REACQUIRE,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityState.REACQUIRING,
                ContinuityAssessment.none(),
                true, true, true,
                true, true, true,
                true, true,
                false, false, true,
                true, false,
                "test_reacquire"
        );

        long requestedRevision = PreviewContinuityUiPolicy
                .requestsRecoveryRebase(reacquiring)
                ? reacquiring.revision : 0L;

        assertEquals(3L, requestedRevision);
        assertFalse(PreviewContinuityUiPolicy.shouldApplyRecoveredSceneRebase(
                requestedRevision,
                0L,
                SceneContinuityState.REACQUIRING
        ));
        assertTrue(PreviewContinuityUiPolicy.shouldApplyRecoveredSceneRebase(
                requestedRevision,
                0L,
                SceneContinuityState.STABLE
        ));
        assertFalse(PreviewContinuityUiPolicy.shouldApplyRecoveredSceneRebase(
                requestedRevision,
                requestedRevision,
                SceneContinuityState.STABLE
        ));
    }

    @Test
    public void stableDecisionCannotRequestRecoveryRebase() {
        SceneTransitionDecision stable = SceneTransitionDecision.none(
                4L,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                ContinuityAssessment.none()
        );

        assertFalse(PreviewContinuityUiPolicy.requestsRecoveryRebase(
                stable
        ));
    }

    @Test
    public void poolOnlyRecoveryClearsGhostFocusedTarget() {
        assertTrue(PreviewContinuityUiPolicy
                .shouldClearFocusedTargetAfterRecovery(
                        true,
                        "VEHICLE_POOL_RECOVERED"
                ));
        assertFalse(PreviewContinuityUiPolicy
                .shouldClearFocusedTargetAfterRecovery(
                        false,
                        "VEHICLE_POOL_RECOVERED"
                ));
        assertFalse(PreviewContinuityUiPolicy
                .shouldClearFocusedTargetAfterRecovery(
                        true,
                        "TARGET_RECOVERED"
                ));
    }

    @Test
    public void onlyEstablishedTargetCanEmitFocusedTrackingFailure() {
        assertFalse(PreviewContinuityUiPolicy.isEstablishedFocusedTarget(
                TargetSnapshot.State.ACQUIRED,
                0L
        ));
        assertFalse(PreviewContinuityUiPolicy.isEstablishedFocusedTarget(
                TargetSnapshot.State.DEGRADED,
                0L
        ));
        assertTrue(PreviewContinuityUiPolicy.isEstablishedFocusedTarget(
                TargetSnapshot.State.TRACKING,
                0L
        ));
        assertTrue(PreviewContinuityUiPolicy.isEstablishedFocusedTarget(
                TargetSnapshot.State.DEGRADED,
                44L
        ));
    }
}
