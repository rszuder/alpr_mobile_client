package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ContinuityGenerationsTest {
    @Test
    public void softTransitionsAdvanceVisualEpochWithoutChangingScene() {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityProfile.INITIAL
        );

        SceneTransitionDecision hold = coordinator.observe(
                weakMovingScene(1L, true),
                1_000L
        );
        SceneContinuitySnapshot afterHold = coordinator.snapshot();
        SceneTransitionDecision reacquire = coordinator.observe(
                weakMovingScene(2L, false),
                1_000L + SceneContinuityProfile.INITIAL.motionSettleNanos
        );
        SceneContinuitySnapshot afterReacquire = coordinator.snapshot();

        assertEquals(SceneTransitionAction.SOFT_HOLD, hold.action);
        assertEquals(0L, afterHold.sceneGeneration);
        assertEquals(1L, afterHold.visualEpoch);
        assertEquals(hold.revision, afterHold.visualEpochRevision);
        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, reacquire.action);
        assertEquals(0L, afterReacquire.sceneGeneration);
        assertEquals(2L, afterReacquire.visualEpoch);
        assertEquals(reacquire.revision, afterReacquire.visualEpochRevision);
    }

    @Test
    public void hardResetAdvancesSceneAndVisualGenerationsTogether() {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                SceneContinuityProfile.INITIAL
        );

        SceneTransitionDecision reset = coordinator.observe(
                unexplainedScene(1L),
                1_000L
        );
        SceneContinuitySnapshot snapshot = coordinator.snapshot();

        assertEquals(SceneTransitionAction.HARD_RESET, reset.action);
        assertEquals(1L, snapshot.sceneGeneration);
        assertEquals(1L, snapshot.visualEpoch);
        assertEquals(reset.revision, snapshot.hardResetRevision);
        assertEquals(reset.revision, snapshot.visualEpochRevision);
    }

    @Test
    public void controlledCameraTransformHasIndependentGeneration() {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityProfile.INITIAL
        );

        long transformGeneration = coordinator.advanceCameraTransformGeneration(5_000L);
        ContinuityStamp stamp = coordinator.stamp(4_900L);

        assertEquals(1L, transformGeneration);
        assertEquals(0L, stamp.sceneGeneration);
        assertEquals(0L, stamp.visualEpoch);
        assertEquals(1L, stamp.cameraTransformGeneration);
        assertEquals(4_900L, stamp.sourceTimestampNanos);
    }

    @Test
    public void generationGateRejectsOnlyTheInvalidatedResultLayers() {
        ContinuityGenerationGate gate = new ContinuityGenerationGate();
        ContinuityStamp current = new ContinuityStamp(3L, 8L, 5L, 1_000L);

        ContinuityResultDisposition oldScene = gate.evaluate(
                current,
                new ContinuityStamp(2L, 8L, 5L, 900L)
        );
        ContinuityResultDisposition oldEpoch = gate.evaluate(
                current,
                new ContinuityStamp(3L, 7L, 5L, 900L)
        );
        ContinuityResultDisposition oldTransform = gate.evaluate(
                current,
                new ContinuityStamp(3L, 8L, 4L, 900L)
        );
        ContinuityResultDisposition currentResult = gate.evaluate(
                current,
                new ContinuityStamp(3L, 8L, 5L, 900L)
        );

        assertEquals(ContinuityResultDisposition.REJECT_ALL, oldScene);
        assertFalse(oldScene.allowsDomainEvidence());
        assertEquals(
                ContinuityResultDisposition.REJECT_GEOMETRY_CROP_AND_FINALIZATION,
                oldEpoch
        );
        assertTrue(oldEpoch.allowsDomainEvidence());
        assertFalse(oldEpoch.allowsGeometry());
        assertEquals(ContinuityResultDisposition.REJECT_STALE_CAMERA_TRANSFORM,
                oldTransform);
        assertEquals(ContinuityResultDisposition.ACCEPT_ALL, currentResult);
        assertTrue(currentResult.allowsFinalization());
    }

    private static SceneEvidence weakMovingScene(long frameId, boolean moving) {
        return new SceneEvidence(
                frameId, frameId * 10L, moving,
                moving ? 0.85f : 0f,
                moving ? 0.80f : 0f,
                0f, 0.70f, 0.70f,
                new TargetContinuityEvidence(
                        15L, 31L, 0L, TargetContinuityLevel.PREDICTED_ONLY,
                        0.10f, 1, 0.10f, 1,
                        0.20f, 0.20f, 0.90f,
                        0f, 0f, 0.10f,
                        false, false, false, 10L
                ),
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, moving, moving, moving ? 1f : 0f,
                        false, false, 0f, 0f,
                        0.20f, 0f
                ),
                false, false, false, false
        );
    }

    private static SceneEvidence unexplainedScene(long frameId) {
        return new SceneEvidence(
                frameId, frameId * 10L, true,
                0.95f, 0.90f, 0.10f, 0.90f, 0.90f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, false, false, 0f,
                        false, false, 0f, 0f,
                        0f, 0f
                ),
                false, false, false, false
        );
    }
}
