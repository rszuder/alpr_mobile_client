package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ContinuityEvaluatorsTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void targetScoreUsesDocumentedStartingWeights() {
        TargetContinuityEvidence evidence = target(
                TargetContinuityLevel.VEHICLE_AND_PLATE,
                0.80f, 3, 0.80f,
                0.60f, 0.70f, 0.90f, 0.50f, 0.80f
        );

        float score = new TargetContinuityEvaluator().evaluate(
                evidence,
                SceneContinuityProfile.INITIAL
        );

        assertEquals(0.76f, score, EPSILON);
    }

    @Test
    public void targetScoreRenormalizesMissingPlateComponent() {
        TargetContinuityEvidence evidence = target(
                TargetContinuityLevel.VEHICLE_ONLY,
                0.80f, 3, 0.80f,
                0.60f, 0.70f, 0.90f, 0f, 0.80f
        );

        float score = new TargetContinuityEvaluator().evaluate(
                evidence,
                SceneContinuityProfile.INITIAL
        );

        assertEquals(0.71f / 0.90f, score, EPSILON);
    }

    @Test
    public void missingOrLostTargetHasNoContinuity() {
        TargetContinuityEvaluator evaluator = new TargetContinuityEvaluator();

        assertEquals(
                0f,
                evaluator.evaluate(TargetContinuityEvidence.noTarget(), SceneContinuityProfile.INITIAL),
                EPSILON
        );
        assertEquals(
                0f,
                evaluator.evaluate(
                        target(TargetContinuityLevel.LOST, 1f, 7, 1f, 1f, 1f, 1f, 1f, 1f),
                        SceneContinuityProfile.INITIAL
                ),
                EPSILON
        );
    }

    @Test
    public void reassociationAndPredictionPreserveVehiclePoolScore() {
        VehicleContinuityEvidence evidence = new VehicleContinuityEvidence(
                4, 4, 3, 1, 0,
                0.75f, 0.82f, 0.76f, 10L
        );

        float score = new VehicleContinuityEvaluator().evaluate(evidence);

        assertEquals(0.81375f, score, EPSILON);
        assertTrue(score > SceneContinuityProfile.INITIAL.minimumVehicleContinuityToPreserve);
    }

    @Test
    public void vehicleScoreDoesNotTreatNewEntitiesAsReassociated() {
        VehicleContinuityEvidence evidence = new VehicleContinuityEvidence(
                3, 3, 0, 0, 3,
                0f, 0f, 0f, 10L
        );

        assertEquals(0f, new VehicleContinuityEvaluator().evaluate(evidence), EPSILON);
    }

    @Test
    public void motionScoreRenormalizesWhenGlobalFlowAndGyroAreUnavailable() {
        MotionExplanationEvidence motion = new MotionExplanationEvidence(
                false, false, false, 0f,
                false, false, 0f, 0f,
                0.84f, 0f
        );

        float score = new MotionExplanationEvaluator().evaluate(
                motion,
                0.84f, true,
                0f, false
        );

        assertEquals(0.84f, score, EPSILON);
    }

    @Test
    public void reassociatedVehiclePoolCanExplainMotionWithoutTarget() {
        MotionExplanationEvidence motion = new MotionExplanationEvidence(
                false, false, false, 0f,
                false, false, 0f, 0f,
                0f, 0.80f
        );

        float score = new MotionExplanationEvaluator().evaluate(
                motion,
                0f, false,
                0.80f, true
        );

        assertEquals(0.80f, score, EPSILON);
        assertTrue(score > SceneContinuityProfile.INITIAL.minimumMotionExplanation);
    }

    @Test
    public void rapidGyroMotionCanExplainTemporaryTargetQualityDrop() {
        MotionExplanationEvidence motion = new MotionExplanationEvidence(
                true, true, true, 1.5f,
                false, false, 0f, 0f,
                0.20f, 0f
        );

        float score = new MotionExplanationEvaluator().evaluate(
                motion,
                0.20f, true,
                0f, false
        );

        assertEquals((0.25f + 0.30f * 0.20f) / 0.55f, score, EPSILON);
        assertTrue(score > SceneContinuityProfile.INITIAL.minimumMotionExplanation);
    }

    @Test
    public void unexplainedStationaryChangeProducesStrongCutEvidence() {
        SceneEvidence evidence = scene(
                0.90f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, false, false, 0f,
                        false, false, 0f, 0f,
                        0f, 0f
                )
        );

        float score = new ContinuityBreakEvaluator().evaluate(evidence, 0f, 0f, 0f);

        assertEquals(0.972f, score, EPSILON);
        assertTrue(score > SceneContinuityProfile.INITIAL.continuityBreakThreshold);
    }

    @Test
    public void noRawVisualChangeCannotBecomeCutEvidence() {
        SceneEvidence evidence = new SceneEvidence(
                1L, 10L, false,
                1f, 1f, 0f, 1f, 1f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, false, false, false
        );

        assertEquals(
                0f,
                new ContinuityBreakEvaluator().evaluate(evidence, 0f, 0f, 0f),
                EPSILON
        );
    }

    private static TargetContinuityEvidence target(
            TargetContinuityLevel level,
            float focusedQuality,
            int inliers,
            float supportRatio,
            float kalman,
            float geometry,
            float vehicleAppearance,
            float plateAppearance,
            float registration
    ) {
        return new TargetContinuityEvidence(
                level == TargetContinuityLevel.NO_TARGET ? 0L : 15L,
                31L, 71L, level,
                focusedQuality, inliers, supportRatio, 0,
                kalman, geometry, 0.90f,
                vehicleAppearance, plateAppearance, registration,
                true, true, true, 10L
        );
    }

    private static SceneEvidence scene(
            float rawVisualChangeScore,
            TargetContinuityEvidence target,
            VehicleContinuityEvidence vehicles,
            MotionExplanationEvidence motion
    ) {
        return new SceneEvidence(
                1L, 10L, true,
                rawVisualChangeScore, rawVisualChangeScore,
                0f, 0f, 0f,
                target, vehicles, motion,
                false, false, false, false
        );
    }
}
