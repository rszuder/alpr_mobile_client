package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class SceneContinuityContractsTest {
    @Test
    public void contractsAreImmutableDataOnlyTypes() {
        Class<?>[] types = {
                TargetContinuityEvidence.class,
                VehicleContinuityEvidence.class,
                MotionExplanationEvidence.class,
                SceneEvidence.class,
                ContinuityAssessment.class,
                SceneTransitionDecision.class,
                SceneContinuitySnapshot.class,
                SceneContinuityProfile.class
        };

        for (Class<?> type : types) {
            assertTrue(type.getName(), Modifier.isFinal(type.getModifiers()));
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                assertTrue(field.toString(), Modifier.isPublic(field.getModifiers()));
                assertTrue(field.toString(), Modifier.isFinal(field.getModifiers()));
            }
        }
    }

    @Test
    public void initialProfileMatchesExplicitV2StartingValues() {
        SceneContinuityProfile profile = SceneContinuityProfile.INITIAL;

        assertEquals(180_000_000L, profile.motionSettleNanos);
        assertEquals(1_000_000_000L, profile.reacquireTimeoutNanos);
        assertEquals(450_000_000L, profile.strongCutPersistenceNanos);
        assertEquals(1_200_000_000L, profile.maximumSoftHoldNanos);
        assertEquals(0.60f, profile.minimumTargetContinuityToPreserve, 0.0001f);
        assertEquals(0.50f, profile.minimumVehicleContinuityToPreserve, 0.0001f);
        assertEquals(0.50f, profile.minimumMotionExplanation, 0.0001f);
        assertEquals(0.70f, profile.continuityBreakThreshold, 0.0001f);
        assertEquals(3, profile.minimumTrackerInliers);
        assertEquals(0.90f, profile.accelerometerGravityAlpha, 0.0001f);
        assertEquals(0.55f, profile.accelerometerMagnitudeAlpha, 0.0001f);
        assertEquals(0.65f, profile.accelerometerMovingThreshold, 0.0001f);
        assertEquals(2.0f, profile.accelerometerRapidThreshold, 0.0001f);
        assertEquals(700_000_000L, profile.accelerometerEventRetentionNanos);
        assertEquals(
                "strict_scene_boundary",
                SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName()
        );
        assertEquals(
                "dynamic_continuity",
                SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()
        );
        assertEquals(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                SceneHandlingMode.fromWireName("strict_scene_boundary")
        );
        assertEquals(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneHandlingMode.fromWireName("unknown")
        );
    }

    @Test
    public void sceneEvidenceKeepsTargetVehicleAndMotionAxesSeparate() {
        TargetContinuityEvidence target = targetEvidence();
        VehicleContinuityEvidence vehicles = new VehicleContinuityEvidence(
                4, 3, 3, 1, 0, 0.75f, 0.82f, 0.76f, 50L
        );
        MotionExplanationEvidence motion = new MotionExplanationEvidence(
                true, true, false, 0.4f, false,
                false, 0f, 0f, 0.84f, 0.75f
        );
        SceneEvidence evidence = new SceneEvidence(
                7L, 1_000L, true,
                0.88f, 0.80f, 0.10f, 0.20f, 0.15f,
                target, vehicles, motion,
                false, false, false, false
        );

        assertTrue(evidence.rawVisualChange);
        assertEquals(TargetContinuityLevel.VEHICLE_AND_PLATE, evidence.target.level);
        assertEquals(0.75f, evidence.vehicles.reassociationRatio, 0.0001f);
        assertTrue(evidence.motion.cameraMoving);
        assertFalse(evidence.structuralChange());
    }

    @Test
    public void decisionSeparatesVisualEpochFromHardSceneGeneration() {
        ContinuityAssessment assessment = assessment(
                VisualChangeClassification.UNEXPLAINED_CHANGE
        );
        SceneTransitionDecision hold = new SceneTransitionDecision(
                2L,
                SceneTransitionAction.SOFT_HOLD,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityState.MOTION_HOLD,
                assessment,
                true, true, true,
                true, true, true,
                false, false, false,
                false, false,
                true, false,
                "rapid_camera_motion"
        );

        assertTrue(hold.incrementVisualEpoch);
        assertFalse(hold.incrementSceneGeneration);
        assertTrue(hold.preserveVehicleEntities);
        assertTrue(hold.suspendFinalization);
    }

    @Test(expected = IllegalArgumentException.class)
    public void evidenceRejectsScoresOutsideUnitRange() {
        new VehicleContinuityEvidence(
                1, 1, 1, 0, 0,
                1.1f, 1f, 1f, 0L
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void profileRejectsSoftHoldShorterThanSettleWindow() {
        new SceneContinuityProfile(
                200L, 1_000L, 450L, 100L,
                0.6f, 0.5f, 0.5f, 0.7f, 3
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void sceneGenerationCannotIncrementWithoutHardReset() {
        new SceneTransitionDecision(
                1L,
                SceneTransitionAction.SOFT_HOLD,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityState.MOTION_HOLD,
                assessment(VisualChangeClassification.UNEXPLAINED_CHANGE),
                true, true, true,
                true, true, true,
                false, false, false,
                false, false,
                true, true,
                "invalid"
        );
    }

    private static TargetContinuityEvidence targetEvidence() {
        return new TargetContinuityEvidence(
                15L, 31L, 71L,
                TargetContinuityLevel.VEHICLE_AND_PLATE,
                0.85f, 7, 0.80f, 0,
                0.90f, 0.88f, 0.92f,
                0.86f, 0.84f, 1f,
                true, true, true, 10L
        );
    }

    private static ContinuityAssessment assessment(
            VisualChangeClassification classification
    ) {
        return new ContinuityAssessment(
                classification,
                0.84f, 0.75f, 0.79f, 0.20f,
                true, true, false,
                "contract_test"
        );
    }
}
