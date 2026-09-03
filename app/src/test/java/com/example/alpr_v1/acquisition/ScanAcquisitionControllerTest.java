package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityAssessment;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.VisualChangeClassification;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.ModeController;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.TargetPurpose;
import com.example.alpr_v1.pipeline.MtReason;
import com.example.alpr_v1.pipeline.MtWorkKind;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PlateRecognition;
import com.example.alpr_v1.pipeline.PlateVehicleAssociation;
import com.example.alpr_v1.pipeline.TemporalCharacterAggregator;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Collections;

public final class ScanAcquisitionControllerTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    public void runSupportsStartPauseResumeAndStop() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(7L, 100L);
        controller.pauseRun(200L);
        assertEquals(ScanRunState.PAUSED, controller.snapshot(500L).runState);
        assertEquals(100L, controller.snapshot(500L).runActiveDurationNanos);

        controller.resumeRun(600L);
        controller.stopRun(800L);

        ScanAcquisitionSnapshot snapshot = controller.snapshot(1_000L);
        assertEquals(ScanRunState.STOPPED, snapshot.runState);
        assertEquals(300L, snapshot.runActiveDurationNanos);
        assertEquals(700L, snapshot.runWallDurationNanos);
    }

    @Test
    public void firstSelectionStartsShortScanSessionAndMtAttemptOne() {
        ModeController modes = new ModeController();
        ScanAcquisitionController controller = new ScanAcquisitionController(
                modes,
                ScanAcquisitionProfile.DEFAULT
        );
        controller.startRun(1L, 0L);

        AcquisitionDirective directive = controller.onVehicleFrame(
                frame(candidate(2L, 22L)), continuity(), 10L
        );

        assertEquals(AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT,
                directive.action);
        assertEquals(2L, directive.entityId);
        assertEquals(1, controller.snapshot(10L).mtAttempts);
        assertNotNull(modes.activeSession());
        assertEquals(TargetPurpose.SCAN_ACQUISITION,
                modes.activeSession().purpose());
        assertFalse(modes.activeSession().persistent());
        ScanAcquisitionStats stats = controller.snapshot(10L).stats;
        assertEquals(1, stats.vehiclesSeen);
        assertEquals(1, stats.vehiclesQueued);
        assertEquals(1, stats.vehiclesSelected);
        assertEquals(1.0, stats.mtAttemptsPerEntity, 0.0001);
    }

    @Test
    public void missingFirstMtSchedulesExpandedRetryForSameEntity() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        AcquisitionDecision decision = controller.onPipelineResult(
                result(), continuity(), 100L
        );

        assertTrue(decision.accepted);
        assertEquals(AcquisitionDirectiveAction.REQUEST_EXPANDED_ENTITY_MT,
                decision.nextDirective.action);
        assertEquals(4L, decision.nextDirective.entityId);
        assertEquals(2, controller.snapshot(100L).mtAttempts);
    }

    @Test
    public void twoMissingMtAttemptsDeferAndReleaseTarget() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        controller.onPipelineResult(result(), continuity(), 100L);

        AcquisitionDecision decision = controller.onPipelineResult(
                result(), continuity(), 200L
        );

        assertEquals(AcquisitionSessionOutcome.DEFERRED, decision.outcome);
        assertEquals(AcquisitionDeferReason.MT_ATTEMPTS_EXHAUSTED,
                decision.deferReason);
        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                decision.nextDirective.action);
        assertEquals(0L, controller.snapshot(200L).activeEntityId);
    }

    @Test
    public void missingExactEntityRoiDefersWithoutBindingNeighbor() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        PipelineResult missing = new PipelineResult(
                "candidate_missing",
                "",
                "",
                0.0,
                Collections.emptyList(),
                100, 100,
                false,
                new ContinuityStamp(1L, 0L, 0L, 1L)
        );

        AcquisitionDecision decision = controller.onPipelineResult(
                missing,
                continuity(),
                100L
        );

        assertEquals(AcquisitionDeferReason.CANDIDATE_MISSING,
                decision.deferReason);
        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                decision.nextDirective.action);
    }

    @Test
    public void matchingFreshMzAdvancesRegistrationState() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(4L, 44L, false, true, "WX12")),
                continuity(),
                100L
        );

        assertTrue(decision.accepted);
        assertEquals(EntityAcquisitionState.READING_REGISTRATION,
                decision.entityState);
        assertEquals(1, controller.snapshot(100L).freshMzAttempts);
        assertTrue(controller.snapshot(100L).identifiedEntityIds.contains(4L));
        assertTrue(controller.snapshot(100L).completedEntityIds.isEmpty());
        EntityRecognitionSnapshot recognition =
                controller.snapshot(100L).entityRecognitions.get(4L);
        assertEquals("WX12", recognition.text);
        assertFalse(recognition.confirmed);
        assertEquals(AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                decision.nextDirective.action);
    }

    @Test
    public void plausibleFirstReadIsPublishedAsProvisionalEntityRecognition() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        controller.onPipelineResult(
                result(observation(4L, 44L, false, true, "WX1234")),
                continuity(),
                100L
        );

        EntityRecognitionSnapshot recognition =
                controller.snapshot(100L).entityRecognitions.get(4L);
        assertEquals("WX1234", recognition.text);
        assertFalse(recognition.confirmed);
    }

    @Test
    public void stableMatchingConsensusBecomesReadyAndScopedRelease() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(4L, 44L, true, true, "WX1234")),
                continuity(),
                100L
        );

        assertEquals(AcquisitionSessionOutcome.READY_TO_FINALIZE,
                decision.outcome);
        assertEquals(EntityAcquisitionState.READY_TO_FINALIZE,
                decision.entityState);
        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                decision.nextDirective.action);
        assertEquals(0L, controller.snapshot(100L).activeSessionId);
        assertEquals(
                1,
                controller.snapshot(100L).stats.entitiesReadyToFinalize
        );
        assertTrue(controller.snapshot(100L).completedEntityIds.contains(4L));
        assertTrue(controller.snapshot(100L).identifiedEntityIds.contains(4L));
        assertEquals(
                "WX1234",
                controller.snapshot(100L).entityRecognitions.get(4L).text
        );
        assertTrue(controller.snapshot(100L).entityRecognitions.get(4L).confirmed);
    }

    @Test
    public void finalizationCreatesDurableRecordAndSuppressesNormalizedDuplicate() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(5L, 0L);
        controller.onVehicleFrame(
                frame(candidate(1L, 11L), candidate(2L, 12L)),
                continuity(),
                1L
        );
        long firstEntity = controller.snapshot(1L).activeEntityId;
        controller.onPipelineResult(
                result(observation(firstEntity, firstEntity + 10L,
                        true, true, "WX-1234")),
                continuity(),
                10L * SECOND
        );
        controller.onVehicleFrame(
                frame(candidate(1L, 11L), candidate(2L, 12L)),
                continuity(),
                11L * SECOND
        );
        long secondEntity = controller.snapshot(11L * SECOND).activeEntityId;
        assertTrue(secondEntity != firstEntity);
        controller.onPipelineResult(
                result(observation(secondEntity, secondEntity + 10L,
                        true, true, "wx 1234")),
                continuity(),
                20L * SECOND
        );

        ScanAcquisitionSnapshot snapshot = controller.snapshot(60L * SECOND);
        assertEquals(2, snapshot.acquisitionRecords.size());
        AcquisitionRecord first = snapshot.acquisitionRecords.get(0);
        AcquisitionRecord duplicate = snapshot.acquisitionRecords.get(1);
        assertEquals("WX1234", first.normalizedText);
        assertTrue(first.uniqueSaved);
        assertFalse(duplicate.uniqueSaved);
        assertEquals(first.recordId, duplicate.duplicateOfRecordId);
        assertTrue(!first.bestCropId.isEmpty());
        assertEquals(2, snapshot.stats.acquisitionsFinalized);
        assertEquals(1, snapshot.stats.uniquePlatesSaved);
        assertEquals(1, snapshot.stats.duplicateAcquisitionsSuppressed);
        assertEquals(0.5, snapshot.stats.duplicateCaptureRate, 0.0001);
        assertEquals(1.0, snapshot.stats.uniquePlatesPerWallMinute, 0.0001);
    }

    @Test
    public void partialOcrFragmentDoesNotBecomeGreenVehicleRecognition() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        controller.onPipelineResult(
                result(observation(4L, 44L, false, true, "A")),
                continuity(),
                100L
        );

        assertTrue(controller.snapshot(100L).identifiedEntityIds.contains(4L));
        assertTrue(controller.snapshot(100L).entityRecognitions.isEmpty());
    }

    @Test
    public void stablePartialOcrDoesNotCompleteVehicle() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(4L, 44L, true, true, "A")),
                continuity(),
                100L
        );

        assertEquals(AcquisitionSessionOutcome.PROGRESS, decision.outcome);
        assertEquals(EntityAcquisitionState.READING_REGISTRATION,
                decision.entityState);
        assertFalse(controller.snapshot(100L).completedEntityIds.contains(4L));
        assertEquals(4L, controller.snapshot(100L).activeEntityId);
    }

    @Test
    public void lateCurrentRecognitionUpdatesVehicleAfterSessionRelease() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long sourceDirectiveRevision = controller.currentDirective().revision;
        long timeout = ScanAcquisitionProfile.DEFAULT.maximumActiveSessionNanos + 1L;
        controller.onVehicleFrame(
                frame(candidate(4L, 14L)),
                continuity(),
                timeout
        );
        assertEquals(0L, controller.snapshot(timeout).activeSessionId);

        AcquisitionDecision late = controller.onPipelineResult(
                result(observation(
                        4L, 14L, true, true, "WX9876", sourceDirectiveRevision
                )),
                continuity(),
                timeout + 1L
        );

        assertFalse(late.accepted);
        assertEquals(
                "WX9876",
                controller.snapshot(timeout + 1L).entityRecognitions.get(4L).text
        );
    }

    @Test
    public void lateProvisionalFromJustReleasedSessionIsStillPublished() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long sourceDirectiveRevision = controller.currentDirective().revision;
        long timeout = ScanAcquisitionProfile.DEFAULT.maximumActiveSessionNanos + 1L;
        controller.onVehicleFrame(
                frame(candidate(4L, 14L)),
                continuity(),
                timeout
        );
        assertEquals(0L, controller.snapshot(timeout).activeSessionId);

        controller.onPipelineResult(
                result(observation(
                        4L, 14L, false, true, "WX9876", sourceDirectiveRevision
                )),
                continuity(),
                timeout + 1L
        );

        EntityRecognitionSnapshot recognition = controller.snapshot(
                timeout + 1L
        ).entityRecognitions.get(4L);
        assertEquals("WX9876", recognition.text);
        assertFalse(recognition.confirmed);
    }

    @Test
    public void twoUnconfirmedFreshMzAttemptsDeferSession() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        controller.onPipelineResult(
                result(observation(4L, 44L, false, true, "WX")),
                continuity(),
                100L
        );

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(4L, 44L, false, true, "WY")),
                continuity(),
                200L
        );

        assertEquals(AcquisitionSessionOutcome.DEFERRED, decision.outcome);
        assertEquals(AcquisitionDeferReason.MZ_ATTEMPTS_EXHAUSTED,
                decision.deferReason);
        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                decision.nextDirective.action);
    }

    @Test
    public void readyEntityReleasesAndNextFrameSelectsAnotherEntity() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);
        controller.onVehicleFrame(
                frame(candidate(1L, 11L), candidate(2L, 12L)),
                continuity(),
                10L
        );
        long first = controller.snapshot(10L).activeEntityId;
        controller.onPipelineResult(
                result(observation(first, first + 10L, true, true, "WX1234")),
                continuity(),
                100L
        );

        AcquisitionDirective next = controller.onVehicleFrame(
                frame(candidate(1L, 11L), candidate(2L, 12L)),
                continuity(),
                200L
        );

        assertEquals(AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT,
                next.action);
        assertTrue(next.entityId != first);
    }

    @Test
    public void noProgressTimeoutUsesActiveProcessingTime() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        AcquisitionDirective directive = controller.onVehicleFrame(
                frame(candidate(4L, 44L)),
                continuity(),
                ScanAcquisitionProfile.DEFAULT.noProgressTimeoutNanos + 1L
        );

        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                directive.action);
        assertTrue(directive.reason.contains("no_progress_timeout"));
    }

    @Test
    public void sessionActiveTimeoutRemainsIndependentFromProgressClock() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        controller.onPipelineResult(
                result(observation(4L, 44L, false, false, "")),
                continuity(),
                2_900_000_000L
        );

        AcquisitionDirective directive = controller.onVehicleFrame(
                frame(candidate(4L, 44L)),
                continuity(),
                ScanAcquisitionProfile.DEFAULT.maximumActiveSessionNanos + 1L
        );

        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                directive.action);
        assertTrue(directive.reason.contains("active_session_timeout"));
    }

    @Test
    public void pausedRunDoesNotConsumeSessionActiveBudget() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        controller.pauseRun(1L * SECOND);
        controller.resumeRun(101L * SECOND);

        AcquisitionDirective directive = controller.onVehicleFrame(
                frame(candidate(4L, 44L)),
                continuity(),
                102L * SECOND
        );

        assertEquals(AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                directive.action);
        assertEquals(2L * SECOND - 1L,
                controller.snapshot(102L * SECOND).activeSessionDurationNanos);
    }

    @Test
    public void userStopCancelsSessionAndRequestsScopedRelease() {
        ScanAcquisitionController controller = startedWithCandidate(4L);

        controller.stopRun(100L);

        ScanAcquisitionSnapshot snapshot = controller.snapshot(100L);
        assertEquals(ScanRunState.STOPPED, snapshot.runState);
        assertEquals(0L, snapshot.activeSessionId);
        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                snapshot.directive.action);
    }

    @Test
    public void scanNeverAllowsAutoZoom() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);

        assertFalse(controller.autoZoomAllowed());
        assertFalse(controller.snapshot(0L).autoZoomAllowed);
    }

    @Test
    public void continuityHoldPreservesSessionAndPausesActiveTime() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long sessionId = controller.snapshot(1L).activeSessionId;
        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.SOFT_HOLD,
                        SceneContinuityState.MOTION_HOLD,
                        false
                ),
                SECOND
        );

        assertEquals(sessionId, controller.snapshot(101L * SECOND).activeSessionId);
        assertEquals(SECOND - 1L,
                controller.snapshot(101L * SECOND).activeSessionDurationNanos);

        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.NONE,
                        SceneContinuityState.STABLE,
                        false
                ),
                101L * SECOND
        );
        controller.onVehicleFrame(
                frame(candidate(4L, 44L)), continuity(), 102L * SECOND
        );
        assertEquals(sessionId, controller.snapshot(102L * SECOND).activeSessionId);
    }

    @Test
    public void targetRecoveredKeepsSameScanSessionId() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long sessionId = controller.snapshot(1L).activeSessionId;
        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.SOFT_REACQUIRE,
                        SceneContinuityState.REACQUIRING,
                        false
                ),
                100L
        );

        controller.onTerminalRecovery(
                SoftReacquireResult.TARGET_RECOVERED,
                transition(
                        SceneTransitionAction.NONE,
                        SceneContinuityState.STABLE,
                        false
                ),
                200L
        );

        ScanAcquisitionSnapshot snapshot = controller.snapshot(200L);
        assertEquals(sessionId, snapshot.activeSessionId);
        assertEquals(com.example.alpr_v1.domain.TargetSessionState.TRACKING,
                snapshot.activeSessionState);
        assertEquals(AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                snapshot.directive.action);
    }

    @Test
    public void coordinatorDeadlineReleaseCannotLeaveScanPausedWithNoneDirective() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.SOFT_REACQUIRE,
                        SceneContinuityState.REACQUIRING,
                        false
                ),
                100L
        );
        assertEquals(
                AcquisitionDirectiveAction.NONE,
                controller.currentDirective().action
        );

        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.RELEASE_ACTIVE_TARGET,
                        SceneContinuityState.STABLE,
                        false
                ),
                200L
        );

        ScanAcquisitionSnapshot snapshot = controller.snapshot(200L);
        assertEquals(0L, snapshot.activeSessionId);
        assertEquals(
                AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                snapshot.directive.action
        );
        assertFalse("scan_continuity_reacquire".equals(
                snapshot.directive.reason
        ));
    }

    @Test
    public void stableCoordinatorDecisionResumesPausedRecoverySession() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long sessionId = controller.snapshot(1L).activeSessionId;
        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.SOFT_REACQUIRE,
                        SceneContinuityState.REACQUIRING,
                        false
                ),
                100L
        );

        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.NONE,
                        SceneContinuityState.STABLE,
                        false
                ),
                200L
        );

        ScanAcquisitionSnapshot snapshot = controller.snapshot(200L);
        assertEquals(sessionId, snapshot.activeSessionId);
        assertEquals(
                AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                snapshot.directive.action
        );
        assertEquals(
                "scan_recovery_stable_finished",
                snapshot.directive.reason
        );
    }

    @Test
    public void activeTargetLostDefersOnlySessionAndKeepsQueuePool() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);
        controller.onVehicleFrame(
                frame(candidate(1L, 11L), candidate(2L, 12L)),
                continuity(),
                1L
        );
        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.SOFT_REACQUIRE,
                        SceneContinuityState.REACQUIRING,
                        false
                ),
                100L
        );

        controller.onTerminalRecovery(
                SoftReacquireResult.ACTIVE_TARGET_LOST,
                transition(
                        SceneTransitionAction.RELEASE_ACTIVE_TARGET,
                        SceneContinuityState.STABLE,
                        false
                ),
                200L
        );

        ScanAcquisitionSnapshot snapshot = controller.snapshot(200L);
        assertEquals(0L, snapshot.activeSessionId);
        assertEquals(2, snapshot.queue.size());
        assertEquals(AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                snapshot.directive.action);
    }

    @Test
    public void vehiclePoolRecoveryWithoutSessionResumesQueueRefresh() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);

        controller.onTerminalRecovery(
                SoftReacquireResult.VEHICLE_POOL_RECOVERED,
                transition(
                        SceneTransitionAction.RELEASE_ACTIVE_TARGET,
                        SceneContinuityState.STABLE,
                        false
                ),
                100L
        );

        assertEquals(AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                controller.currentDirective().action);
    }

    @Test
    public void hardResetDropsOldSceneQueueAndSession() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);
        controller.onVehicleFrame(
                frame(candidate(1L, 11L), candidate(2L, 12L)),
                continuity(),
                1L
        );

        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.HARD_RESET,
                        SceneContinuityState.HARD_RESETTING,
                        true
                ),
                100L
        );

        ScanAcquisitionSnapshot snapshot = controller.snapshot(100L);
        assertEquals(0L, snapshot.activeSessionId);
        assertEquals(0, snapshot.queue.size());
        assertEquals(AcquisitionDirectiveAction.REQUEST_FRESH_MP,
                snapshot.directive.action);
    }

    @Test
    public void backgroundRecognitionIsStoredButDoesNotAdvanceActiveEntity() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);
        controller.onVehicleFrame(
                frame(candidate(4L, 44L), candidate(8L, 88L)),
                continuity(),
                1L
        );

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(8L, 88L, true, true, "BAD999")),
                continuity(),
                100L
        );

        assertFalse(decision.accepted);
        assertEquals("pipeline_result_belongs_to_another_entity", decision.reason);
        assertEquals(4L, controller.snapshot(100L).activeEntityId);
        assertEquals(0, controller.snapshot(100L).freshMzAttempts);
        assertEquals(
                "BAD999",
                controller.snapshot(100L).entityRecognitions.get(8L).text
        );
    }

    @Test
    public void matchingCurrentDirectiveCreatesEntityBoundPlateAnchor() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long revision = controller.currentDirective().revision;

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(4L, 44L, false, true, "WX", revision)),
                continuity(),
                100L
        );

        assertTrue(decision.accepted);
        PlateAnchor anchor = controller.snapshot(100L).plateAnchor;
        assertNotNull(anchor);
        assertEquals(4L, anchor.entityId);
        assertEquals(44L, anchor.vehicleTrackId);
        assertEquals(104L, anchor.plateTrackId);
        assertEquals(revision, anchor.acquisitionDirectiveRevision);
    }

    @Test
    public void delayedMtRevisionCannotAnchorCurrentScanSession() {
        ScanAcquisitionController controller = startedWithCandidate(4L);
        long currentRevision = controller.currentDirective().revision;

        AcquisitionDecision decision = controller.onPipelineResult(
                result(observation(
                        4L, 44L, true, true, "STALE", currentRevision + 1L
                )),
                continuity(),
                100L
        );

        assertFalse(decision.accepted);
        assertEquals("stale_scan_mt_directive_revision", decision.reason);
        assertNull(controller.snapshot(100L).plateAnchor);
        assertEquals(4L, controller.snapshot(100L).activeEntityId);
    }

    private static ScanAcquisitionController startedWithCandidate(long entityId) {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);
        controller.onVehicleFrame(
                frame(candidate(entityId, entityId + 40L)),
                continuity(),
                1L
        );
        return controller;
    }

    private static SceneContinuitySnapshot continuity() {
        return new SceneContinuitySnapshot(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityState.STABLE,
                VisualChangeClassification.NONE,
                1L, 1L, 0L, 0L,
                false, false, 0L,
                ContinuityAssessment.none()
        );
    }

    private static SceneTransitionDecision transition(
            SceneTransitionAction action,
            SceneContinuityState state,
            boolean hardReset
    ) {
        return new SceneTransitionDecision(
                10L,
                action,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                state,
                ContinuityAssessment.none(),
                !hardReset,
                !hardReset,
                !hardReset,
                action != SceneTransitionAction.NONE,
                state != SceneContinuityState.STABLE,
                state != SceneContinuityState.STABLE,
                action == SceneTransitionAction.SOFT_REACQUIRE,
                action == SceneTransitionAction.SOFT_REACQUIRE,
                action == SceneTransitionAction.RELEASE_ACTIVE_TARGET,
                action == SceneTransitionAction.HARD_RESET,
                action == SceneTransitionAction.HARD_RESET,
                hardReset,
                hardReset,
                "test_transition"
        );
    }

    private static VehicleTrackingFrame frame(VehicleCandidate... candidates) {
        return new VehicleTrackingFrame(
                1L, 1L, 1L, 1L,
                java.util.Arrays.asList(candidates)
        );
    }

    private static VehicleCandidate candidate(long entityId, long vehicleTrackId) {
        return new VehicleCandidate(
                entityId,
                vehicleTrackId,
                new NormalizedBounds(0.2f, 0.2f, 0.8f, 0.8f),
                0.9f, 0.9f, 0.1f,
                false, 0,
                1L, 1L, 0,
                EntityAcquisitionState.NEW
        );
    }

    private static PipelineResult result(PlateObservation... observations) {
        return new PipelineResult(
                "ok",
                "",
                Collections.<PlateRecognition>emptyList(),
                Collections.emptyList(),
                100, 100,
                java.util.Arrays.asList(observations),
                false,
                new ContinuityStamp(1L, 0L, 0L, 1L)
        );
    }

    private static PlateObservation observation(
            long entityId,
            long vehicleTrackId,
            boolean confirmed,
            boolean freshMzAttempted,
            String text
    ) {
        return observation(
                entityId,
                vehicleTrackId,
                confirmed,
                freshMzAttempted,
                text,
                0L
        );
    }

    private static PlateObservation observation(
            long entityId,
            long vehicleTrackId,
            boolean confirmed,
            boolean freshMzAttempted,
            String text,
            long acquisitionDirectiveRevision
    ) {
        return new PlateObservation(
                entityId + 100L,
                PlateVehicleAssociation.direct(
                        entityId,
                        vehicleTrackId,
                        "scan_test"
                ),
                MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,
                1L,
                null,
                text,
                0.9,
                confirmed ? 0.9 : 0.4,
                confirmed,
                confirmed ? 3 : 1,
                Collections.emptyList(),
                0L,
                10L,
                0.8f,
                null,
                null,
                PlateGeometry.unavailable(),
                freshMzAttempted,
                freshMzAttempted && !text.isEmpty(),
                text,
                confirmed,
                freshMzAttempted ? 1 : 0,
                TemporalCharacterAggregator.LAYOUT_SINGLE_ROW,
                Collections.emptyList(),
                "",
                text,
                new ContinuityStamp(1L, 0L, 0L, 1L),
                acquisitionDirectiveRevision
        );
    }
}
