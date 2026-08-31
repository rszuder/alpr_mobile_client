package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SceneTransitionCoordinatorTest {
    private static final SceneContinuityProfile PROFILE = SceneContinuityProfile.INITIAL;

    @Test
    public void strictModeTurnsConfirmedRawChangeIntoHardReset() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY
        );

        SceneTransitionDecision decision = coordinator.observe(
                explainedTargetScene(1L, true, false),
                1_000L
        );

        assertEquals(SceneTransitionAction.HARD_RESET, decision.action);
        assertEquals(VisualChangeClassification.CONTINUITY_BREAK,
                decision.assessment.classification);
        assertTrue(decision.incrementSceneGeneration);
        assertFalse(decision.preserveVehicleEntities);
    }

    @Test
    public void strictRawChangePreemptsExpiredSoftReacquire() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY
        );
        SceneEvidence localLossBeforeRawChange = new SceneEvidence(
                1L, 10L, false,
                0f, 0f, 0f, 0f, 0f,
                targetEvidence(
                        TargetContinuityLevel.PREDICTED_ONLY,
                        0.20f, 1, 0.20f,
                        0.20f, 0.20f, 0f, 0f, 0.20f,
                        false
                ),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, true,
                false, false, false, false
        );

        SceneTransitionDecision reacquire = coordinator.observe(
                localLossBeforeRawChange,
                1_000L
        );
        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, reacquire.action);

        SceneEvidence delayedRawChange = new SceneEvidence(
                2L, 20L, true,
                0.17f, 0.58f, 0f, 0f, 0f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, false, false, false
        );
        SceneTransitionDecision decision = coordinator.observe(
                delayedRawChange,
                2_000_000_000L
        );

        assertEquals(SceneTransitionAction.HARD_RESET, decision.action);
        assertEquals("strict_raw_visual_change", decision.reason);
        assertTrue(decision.incrementSceneGeneration);
        assertTrue(decision.incrementVisualEpoch);
    }

    @Test
    public void dynamicModeKeepsStrongTargetThroughChangingBackground() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );

        SceneTransitionDecision decision = coordinator.observe(
                explainedTargetScene(1L, true, false),
                1_000L
        );

        assertEquals(SceneTransitionAction.NONE, decision.action);
        assertEquals(VisualChangeClassification.MOTION_EXPLAINED_CHANGE,
                decision.assessment.classification);
        assertEquals(SceneContinuityState.STABLE, decision.nextState);
        assertTrue(decision.preserveTargetSession);
        assertFalse(decision.incrementVisualEpoch);
    }

    @Test
    public void stationaryCutCannotBeExplainedByKltWhenLocalAppearanceContradicts() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        TargetContinuityEvidence staleKlt = new TargetContinuityEvidence(
                15L, 31L, 71L, TargetContinuityLevel.VEHICLE_AND_PLATE,
                0.95f, 8, 1f, 0,
                0.95f, 0.95f, 0.95f,
                0f, 0.08f, 0f,
                false, false, true, 2_000_000_000L,
                true
        );
        SceneEvidence cut = new SceneEvidence(
                1L, 10L, true,
                0.95f, 0.90f, 0f, 0.90f, 0.90f,
                staleKlt,
                new VehicleContinuityEvidence(
                        1, 1, 1, 0, 0,
                        1f, 1f, 1f, 2_000_000_000L
                ),
                new MotionExplanationEvidence(
                        true, false, false, 0f,
                        false, false, 0f, 0f,
                        0.95f, 0.95f
                ),
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(cut, 3_000_000_000L);

        assertEquals(VisualChangeClassification.UNEXPLAINED_CHANGE,
                decision.assessment.classification);
        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, decision.action);
        assertFalse(decision.assessment.focusedTargetPreserved);
    }

    @Test
    public void stalePerfectTargetEvidenceCannotExplainStationaryCut() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        TargetContinuityEvidence stalePerfectTarget = new TargetContinuityEvidence(
                15L, 31L, 71L, TargetContinuityLevel.VEHICLE_AND_PLATE,
                0.98f, 8, 1f, 0,
                0.98f, 0.98f, 0.98f,
                1f, 1f, 1f,
                true, true, true, 2_000_000_000L,
                true
        );
        SceneEvidence cut = new SceneEvidence(
                2L, 20L, true,
                0.90f, 0.80f, 0f, 0.90f, 0.80f,
                stalePerfectTarget,
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, false, false, 0f,
                        false, false, 0f, 0f,
                        1f, 0f
                ),
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(cut, 3_000_000_000L);

        assertEquals(VisualChangeClassification.UNEXPLAINED_CHANGE,
                decision.assessment.classification);
        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, decision.action);
        assertEquals("stationary_target_evidence_predates_visual_change",
                decision.assessment.reason);
    }

    @Test
    public void partialEvidenceRemainsRawVisualChangeUntilPolicyCanDecide() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        TargetContinuityEvidence moderate = targetEvidence(
                TargetContinuityLevel.PLATE_ONLY,
                0.55f, 2, 0.55f,
                0.55f, 0.55f, 0f, 0.55f, 0.55f,
                true
        );
        SceneEvidence evidence = new SceneEvidence(
                1L, 10L, true,
                0.80f, 0.80f, 0f, 0.40f, 0.40f,
                moderate,
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        false, false, false, 0f,
                        false, false, 0f, 0f,
                        0.55f, 0f
                ),
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(evidence, 1_000L);

        assertEquals(VisualChangeClassification.RAW_VISUAL_CHANGE,
                decision.assessment.classification);
    }

    @Test
    public void gyroAndStrongTargetExplainChangeButBlurStillStartsHold() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );

        SceneTransitionDecision decision = coordinator.observe(
                explainedTargetScene(1L, true, true),
                1_000L
        );

        assertEquals(VisualChangeClassification.MOTION_EXPLAINED_CHANGE,
                decision.assessment.classification);
        assertEquals(SceneTransitionAction.SOFT_HOLD, decision.action);
    }

    @Test
    public void settlingStateDoesNotPretendCurrentMotionOrStartReacquire() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(
                weakTargetScene(1L, true, true, true),
                1_000L
        );
        SceneEvidence settling = new SceneEvidence(
                2L, 20L, true,
                0.80f, 0.80f, 0f, 0.70f, 0.70f,
                targetEvidence(
                        TargetContinuityLevel.PREDICTED_ONLY,
                        0.10f, 1, 0.10f,
                        0.20f, 0.20f, 0f, 0f, 0.10f,
                        false
                ),
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, false, false, 0f,
                        false, false, 0f, 0f,
                        0.20f, 0f,
                        true
                ),
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(
                settling,
                2_000_000_000L
        );

        assertFalse(settling.motion.cameraMoving);
        assertTrue(settling.motion.motionSettling);
        assertFalse(decision.action == SceneTransitionAction.SOFT_REACQUIRE);
        assertEquals(SceneContinuityState.MOTION_HOLD, decision.nextState);
    }

    @Test
    public void reassociatedVehiclePoolExplainsChangeWithoutFocusedTarget() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );

        SceneTransitionDecision decision = coordinator.observe(
                vehiclePoolScene(1L),
                1_000L
        );

        assertEquals(VisualChangeClassification.MOTION_EXPLAINED_CHANGE,
                decision.assessment.classification);
        assertTrue(decision.assessment.vehiclePoolPreserved);
        assertFalse(decision.incrementSceneGeneration);
    }

    @Test
    public void rapidCameraMotionStartsOneSoftHoldAndDeduplicatesEvidence() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        SceneEvidence evidence = weakTargetScene(7L, true, true, true);

        SceneTransitionDecision first = coordinator.observe(evidence, 1_000L);
        SceneTransitionDecision duplicate = coordinator.observe(evidence, 1_010L);

        assertEquals(SceneTransitionAction.SOFT_HOLD, first.action);
        assertTrue(first.incrementVisualEpoch);
        assertTrue(first.preserveVehicleEntities);
        assertEquals(SceneTransitionAction.NONE, duplicate.action);
        assertEquals(first.revision, duplicate.revision);
        assertEquals(SceneContinuityState.MOTION_HOLD, duplicate.nextState);
    }

    @Test
    public void settledMotionForcesFreshReacquireInAnotherVisualEpoch() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(weakTargetScene(1L, true, true, true), 1_000L);

        SceneTransitionDecision decision = coordinator.observe(
                weakTargetScene(2L, false, false, false),
                1_000L + PROFILE.motionSettleNanos
        );

        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, decision.action);
        assertEquals(SceneContinuityState.REACQUIRING, decision.nextState);
        assertTrue(decision.forceMpRefresh);
        assertTrue(decision.forceMtRefresh);
        assertTrue(decision.clearVehicleRoiCache);
        assertTrue(decision.incrementVisualEpoch);
    }

    @Test
    public void failedReacquireAndPersistentCutEvidenceCauseHardReset() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(unexplainedScene(1L), 1_000L);
        SceneTransitionDecision decision = coordinator.completeSoftReacquire(
                SoftReacquireResult.FAILED,
                2_000L
        );

        assertEquals(SceneTransitionAction.HARD_RESET, decision.action);
        assertEquals(VisualChangeClassification.CONTINUITY_BREAK,
                decision.assessment.classification);
        assertTrue(decision.incrementSceneGeneration);
    }

    @Test
    public void reacquireTimeoutUsesTriggerEvidenceAfterRawChangeDisappears() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(unexplainedScene(1L), 1_000L);

        SceneTransitionDecision decision = coordinator.observe(
                stableNoTargetScene(2L),
                1_000L + PROFILE.reacquireTimeoutNanos
        );

        assertEquals(SceneTransitionAction.HARD_RESET, decision.action);
        assertEquals(SceneContinuityState.HARD_RESETTING, decision.nextState);
        assertEquals(VisualChangeClassification.CONTINUITY_BREAK,
                decision.assessment.classification);
        ReacquireTelemetry telemetry = coordinator.reacquireTelemetry();
        assertTrue(telemetry.available);
        assertEquals("FAILED", telemetry.result);
        assertTrue(telemetry.deadlineReached);
        assertEquals(
                VisualChangeClassification.UNEXPLAINED_CHANGE,
                telemetry.triggerClassification
        );
    }

    @Test
    public void reacquireCannotRemainNonTerminalPastDeadline() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        long runtimeStartNanos = 1_000_000_000L;
        long triggerSourceTimestampNanos = 8_000_000_000L;
        coordinator.requestSoftReacquire(
                "low_evidence_refresh",
                runtimeStartNanos,
                triggerSourceTimestampNanos
        );

        ReacquireTelemetry active = coordinator.reacquireTelemetry();
        assertEquals(runtimeStartNanos, active.startedRuntimeNanos);
        assertEquals(
                triggerSourceTimestampNanos,
                active.triggerSourceTimestampNanos
        );

        SceneTransitionDecision decision = coordinator.observe(
                stableNoTargetScene(3L),
                runtimeStartNanos + PROFILE.reacquireTimeoutNanos
        );

        assertEquals(SceneTransitionAction.NONE, decision.action);
        assertEquals(SceneContinuityState.STABLE, decision.nextState);
        assertFalse(coordinator.snapshot().finalizationSuspended);
    }

    @Test
    public void localTrackingLossWithoutGlobalChangeDoesNotBecomeRawVisualChange() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        SceneEvidence localLoss = new SceneEvidence(
                4L, 40L, false,
                0f, 0f, 0f, 0f, 0f,
                new TargetContinuityEvidence(
                        1L, 11L, 21L, TargetContinuityLevel.LOST,
                        0f, 0, 0f, 2,
                        0f, 0f, 0f, 0f, 0f, 0f,
                        false, false, false, 100L
                ),
                new VehicleContinuityEvidence(
                        2, 2, 2, 0, 0,
                        1f, 0f, 0f, 10L,
                        false, false
                ),
                MotionExplanationEvidence.none(),
                true, false,
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(localLoss, 1_000L);

        assertFalse(localLoss.rawVisualChange);
        assertEquals(VisualChangeClassification.NONE,
                decision.assessment.classification);
        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, decision.action);
        assertEquals(0L, coordinator.snapshot().sceneGeneration);
    }

    @Test
    public void physicalMotionDuringZoomRequestsSoftRecoveryInDynamicMode() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        SceneEvidence zoomMotion = new SceneEvidence(
                5L, 50L, false,
                0f, 0f, 0f, 0f, 0f,
                targetEvidence(
                        TargetContinuityLevel.PLATE_ONLY,
                        0.4f, 1, 0.3f,
                        0.3f, 0.3f, 0f, 0f, 0f,
                        false
                ),
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, true, true, 1f,
                        true, false, 0f, 0f, 0f, 0f
                ),
                false, true,
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(zoomMotion, 1_000L);

        assertEquals(SceneTransitionAction.SOFT_HOLD, decision.action);
        assertEquals(0L, coordinator.snapshot().sceneGeneration);
    }

    @Test
    public void returnedSceneDifferenceUsesCoordinatorDecision() {
        SceneTransitionCoordinator dynamic = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        SceneTransitionCoordinator strict = coordinator(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY
        );

        SceneTransitionDecision dynamicDecision = dynamic.observe(
                unexplainedScene(6L), 1_000L
        );
        SceneTransitionDecision strictDecision = strict.observe(
                unexplainedScene(6L), 1_000L
        );

        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, dynamicDecision.action);
        assertEquals(SceneTransitionAction.HARD_RESET, strictDecision.action);
    }

    @Test
    public void lostActiveTargetReleasesOnlyTargetWhenPoolSurvives() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(unexplainedScene(1L), 1_000L);
        SceneTransitionDecision decision = coordinator.completeSoftReacquire(
                SoftReacquireResult.ACTIVE_TARGET_LOST,
                2_000L
        );

        assertEquals(SceneTransitionAction.RELEASE_ACTIVE_TARGET, decision.action);
        assertTrue(decision.releaseOnlyActiveTarget);
        assertTrue(decision.preserveVehicleEntities);
        assertFalse(decision.incrementSceneGeneration);
    }

    @Test
    public void successfulFreshReacquireReturnsToStableWithoutSceneReset() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(unexplainedScene(1L), 1_000L);

        SceneTransitionDecision decision = coordinator.completeSoftReacquire(
                SoftReacquireResult.TARGET_RECOVERED,
                2_000L
        );

        assertEquals(SceneTransitionAction.NONE, decision.action);
        SceneContinuitySnapshot snapshot = coordinator.snapshot();
        assertEquals(SceneContinuityState.STABLE, snapshot.state);
        assertEquals(0L, snapshot.sceneGeneration);
        assertFalse(snapshot.finalizationSuspended);
    }

    @Test
    public void noTargetRecoveredVehiclePoolReturnsToStable() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(unexplainedScene(1L), 1_000L);
        long visualEpoch = coordinator.snapshot().visualEpoch;

        SceneTransitionDecision decision = coordinator.completeSoftReacquire(
                SoftReacquireResult.VEHICLE_POOL_RECOVERED,
                2_000L
        );

        assertEquals(SceneTransitionAction.RELEASE_ACTIVE_TARGET, decision.action);
        assertTrue(decision.releaseOnlyActiveTarget);
        assertTrue(decision.preserveVehicleEntities);
        SceneContinuitySnapshot snapshot = coordinator.snapshot();
        assertEquals(SceneContinuityState.STABLE, snapshot.state);
        assertEquals(0L, snapshot.sceneGeneration);
        assertEquals(visualEpoch, snapshot.visualEpoch);
        assertFalse(snapshot.finalizationSuspended);
        assertFalse(snapshot.heavyInferenceSuspended);
        ReacquireTelemetry telemetry = coordinator.reacquireTelemetry();
        assertEquals("VEHICLE_POOL_RECOVERED", telemetry.result);
        assertTrue(telemetry.vehiclePoolRecovered);
    }

    @Test
    public void failedReacquireWithoutConfirmedBreakReleasesActiveTargetImmediately() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(explainedTargetScene(1L, false, false), 1_000L);
        coordinator.requestSoftReacquire("controlled_refresh", 1_100L, 10L);

        SceneTransitionDecision decision = coordinator.completeSoftReacquire(
                SoftReacquireResult.FAILED,
                2_000L
        );

        assertEquals(SceneTransitionAction.RELEASE_ACTIVE_TARGET, decision.action);
        assertEquals(SceneContinuityState.STABLE, decision.nextState);
        assertTrue(decision.releaseOnlyActiveTarget);
        assertFalse(decision.incrementSceneGeneration);
    }

    @Test
    public void failedReacquireWithoutBreakOrTargetReturnsStableImmediately() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.requestSoftReacquire("controlled_refresh", 1_000L, 10L);

        SceneTransitionDecision decision = coordinator.completeSoftReacquire(
                SoftReacquireResult.FAILED,
                2_000L
        );

        assertEquals(SceneTransitionAction.NONE, decision.action);
        assertEquals(SceneContinuityState.STABLE, decision.nextState);
        assertFalse(coordinator.snapshot().finalizationSuspended);
        assertFalse(coordinator.snapshot().heavyInferenceSuspended);
    }

    @Test
    public void terminalOutcomeEmitsExactlyOneTransitionDecision() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(unexplainedScene(1L), 1_000L);

        SceneTransitionDecision terminal = coordinator.completeSoftReacquire(
                SoftReacquireResult.ACTIVE_TARGET_LOST,
                2_000L
        );
        SceneTransitionDecision duplicate = coordinator.completeSoftReacquire(
                SoftReacquireResult.ACTIVE_TARGET_LOST,
                2_001L
        );

        assertEquals(SceneTransitionAction.RELEASE_ACTIVE_TARGET, terminal.action);
        assertEquals(SceneTransitionAction.NONE, duplicate.action);
        assertEquals(terminal.revision, duplicate.revision);
    }

    @Test
    public void structuralEventBypassesDynamicRecovery() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        SceneEvidence structural = new SceneEvidence(
                4L, 40L, false,
                0f, 0f, 0f, 0f, 0f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, true, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(structural, 1_000L);

        assertEquals(SceneTransitionAction.HARD_RESET, decision.action);
        assertEquals("camera_restarted", decision.reason);
    }

    @Test
    public void modeChangeClearsRecoveryState() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        coordinator.observe(weakTargetScene(1L, true, true, true), 1_000L);

        coordinator.setMode(SceneHandlingMode.STRICT_SCENE_BOUNDARY, 2_000L);

        SceneContinuitySnapshot snapshot = coordinator.snapshot();
        assertEquals(SceneHandlingMode.STRICT_SCENE_BOUNDARY, snapshot.mode);
        assertEquals(SceneContinuityState.STABLE, snapshot.state);
        assertFalse(snapshot.finalizationSuspended);
    }

    @Test
    public void stableFrameWithoutExistingTargetAllowsFirstAcquisition() {
        SceneTransitionCoordinator coordinator = coordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY
        );
        SceneEvidence stable = new SceneEvidence(
                1L, 10L, false,
                0f, 0f, 0f, 0f, 0f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, false, false, false
        );

        SceneTransitionDecision decision = coordinator.observe(stable, 1_000L);

        assertEquals(SceneTransitionAction.NONE, decision.action);
        assertTrue(decision.assessment.finalizationAllowed);
        assertFalse(coordinator.snapshot().finalizationSuspended);
    }

    private static SceneTransitionCoordinator coordinator(SceneHandlingMode mode) {
        return new SceneTransitionCoordinator(mode, PROFILE);
    }

    private static SceneEvidence explainedTargetScene(
            long frameId,
            boolean rawChange,
            boolean rapidMotion
    ) {
        TargetContinuityEvidence target = targetEvidence(
                TargetContinuityLevel.VEHICLE_AND_PLATE,
                0.90f, 7, 0.90f,
                0.90f, 0.90f, 0.90f, 0.90f, 0.90f,
                true
        );
        return new SceneEvidence(
                frameId, frameId * 10L, rawChange,
                rawChange ? 0.85f : 0f,
                rawChange ? 0.80f : 0f,
                0f, 0.10f, 0.10f,
                target,
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, rapidMotion, rapidMotion, rapidMotion ? 1f : 0f,
                        false, false, 0f, 0f,
                        0.90f, 0f
                ),
                false, false, false, false
        );
    }

    private static SceneEvidence weakTargetScene(
            long frameId,
            boolean rawChange,
            boolean cameraMoving,
            boolean rapidMotion
    ) {
        TargetContinuityEvidence target = targetEvidence(
                TargetContinuityLevel.PREDICTED_ONLY,
                0.10f, 1, 0.10f,
                0.20f, 0.20f, 0f, 0f, 0.10f,
                false
        );
        return new SceneEvidence(
                frameId, frameId * 10L, rawChange,
                rawChange ? 0.80f : 0f,
                rawChange ? 0.80f : 0f,
                0f, 0.70f, 0.70f,
                target,
                VehicleContinuityEvidence.empty(),
                new MotionExplanationEvidence(
                        true, cameraMoving, rapidMotion, rapidMotion ? 1f : 0f,
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

    private static SceneEvidence stableNoTargetScene(long frameId) {
        return new SceneEvidence(
                frameId, frameId * 10L, false,
                0f, 0f, 0f, 0f, 0f,
                TargetContinuityEvidence.noTarget(),
                VehicleContinuityEvidence.empty(),
                MotionExplanationEvidence.none(),
                false, false, false, false
        );
    }

    private static SceneEvidence vehiclePoolScene(long frameId) {
        return new SceneEvidence(
                frameId, frameId * 10L, true,
                0.80f, 0.80f, 0f, 0.70f, 0.70f,
                TargetContinuityEvidence.noTarget(),
                new VehicleContinuityEvidence(
                        3, 2, 2, 0, 0,
                        0.67f, 0.90f, 0.90f, 10L
                ),
                new MotionExplanationEvidence(
                        false, false, false, 0f,
                        false, false, 0f, 0f,
                        0f, 0.80f
                ),
                false, false, false, false
        );
    }

    private static TargetContinuityEvidence targetEvidence(
            TargetContinuityLevel level,
            float focusedQuality,
            int inliers,
            float supportRatio,
            float kalman,
            float geometry,
            float vehicleAppearance,
            float plateAppearance,
            float registration,
            boolean fresh
    ) {
        return new TargetContinuityEvidence(
                15L, 31L, 71L, level,
                focusedQuality, inliers, supportRatio, 0,
                kalman, geometry, 0.90f,
                vehicleAppearance, plateAppearance, registration,
                fresh, fresh, fresh, 10L
        );
    }
}
