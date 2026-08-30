package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.continuity.ContinuityAssessment;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.VisualChangeClassification;
import com.example.alpr_v1.pipeline.TargetSnapshot;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(AndroidJUnit4.class)
public final class PreviewCoordinatorAuthorityInstrumentedTest {
    @Test
    public void poolOnlyCoordinatorDecisionKeepsVehicleOverlayAndUiGeneration() {
        List<OverlayItem> visibleOverlay = new ArrayList<>();
        visibleOverlay.add(vehicle(101L, 0.10f));
        visibleOverlay.add(vehicle(202L, 0.55f));
        AtomicLong uiSceneGeneration = new AtomicLong(7L);
        ContinuityAssessment poolPreserved = new ContinuityAssessment(
                VisualChangeClassification.MOTION_EXPLAINED_CHANGE,
                0f,
                0.85f,
                0.80f,
                0.10f,
                false,
                true,
                false,
                "vehicle_pool_explains_visual_change"
        );
        SceneTransitionDecision decision = SceneTransitionDecision.none(
                1L,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                poolPreserved
        );

        PreviewContinuityUiPolicy.Outcome outcome =
                PreviewContinuityUiPolicy.decide(
                        decision,
                        true,
                        false
                );
        if (outcome.legacySceneInvalidation
                || outcome.legacyTrackingLossInvalidation) {
            visibleOverlay.clear();
            uiSceneGeneration.incrementAndGet();
        }

        assertFalse(outcome.legacySceneInvalidation);
        assertFalse(outcome.legacyTrackingLossInvalidation);
        assertEquals(2, visibleOverlay.size());
        assertEquals(7L, uiSceneGeneration.get());
    }

    @Test
    public void recoveredPoolConsumesPreviewReferenceRebaseOnce() {
        SceneTransitionDecision reacquiring = new SceneTransitionDecision(
                2L,
                SceneTransitionAction.SOFT_REACQUIRE,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityState.REACQUIRING,
                ContinuityAssessment.none(),
                true, true, true,
                true, true, true,
                true, true,
                false, false, true,
                true, false,
                "pool_reacquire"
        );
        long requestedRevision = PreviewContinuityUiPolicy
                .requestsRecoveryRebase(reacquiring)
                ? reacquiring.revision : 0L;

        assertEquals(true,
                PreviewContinuityUiPolicy.shouldApplyRecoveredSceneRebase(
                        requestedRevision,
                        0L,
                        SceneContinuityState.STABLE
                ));
        assertFalse(
                PreviewContinuityUiPolicy.shouldApplyRecoveredSceneRebase(
                        requestedRevision,
                        requestedRevision,
                        SceneContinuityState.STABLE
                )
        );
        assertEquals(
                true,
                PreviewContinuityUiPolicy
                        .shouldClearFocusedTargetAfterRecovery(
                                true,
                                "VEHICLE_POOL_RECOVERED"
                        )
        );
        assertFalse(PreviewContinuityUiPolicy.isEstablishedFocusedTarget(
                TargetSnapshot.State.ACQUIRED,
                0L
        ));
        assertEquals(
                true,
                PreviewContinuityUiPolicy.isEstablishedFocusedTarget(
                        TargetSnapshot.State.TRACKING,
                        0L
                )
        );
    }

    private static OverlayItem vehicle(long trackId, float left) {
        return new OverlayItem(
                OverlayItem.Kind.VEHICLE,
                new RectF(left, 0.2f, left + 0.25f, 0.7f),
                Collections.emptyList(),
                "pojazd",
                trackId,
                false
        );
    }
}
