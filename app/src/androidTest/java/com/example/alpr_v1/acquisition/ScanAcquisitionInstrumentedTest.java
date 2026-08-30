package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.continuity.ContinuityAssessment;
import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.VisualChangeClassification;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class ScanAcquisitionInstrumentedTest {
    @Test
    public void threeVehiclePoolSelectsOneDurableEntityWithoutAutoZoom() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);

        AcquisitionDirective directive = controller.onVehicleFrame(
                frame(candidate(3L, 33L), candidate(1L, 11L), candidate(2L, 22L)),
                continuity(),
                10L
        );

        assertEquals(AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT,
                directive.action);
        assertTrue(directive.entityId > 0L);
        assertEquals(directive.entityId,
                controller.snapshot(10L).activeEntityId);
        assertFalse(controller.snapshot(10L).autoZoomAllowed);
        assertEquals(3, controller.snapshot(10L).stats.vehiclesSeen);
    }

    @Test
    public void continuityRecoveryPreservesActiveScanSession() {
        ScanAcquisitionController controller = new ScanAcquisitionController();
        controller.startRun(1L, 0L);
        controller.onVehicleFrame(
                frame(candidate(1L, 11L)), continuity(), 10L
        );
        long sessionId = controller.snapshot(10L).activeSessionId;
        controller.onContinuityDecision(
                transition(
                        SceneTransitionAction.SOFT_REACQUIRE,
                        SceneContinuityState.REACQUIRING
                ),
                20L
        );
        controller.onTerminalRecovery(
                SoftReacquireResult.TARGET_RECOVERED,
                transition(SceneTransitionAction.NONE, SceneContinuityState.STABLE),
                30L
        );

        assertEquals(sessionId, controller.snapshot(30L).activeSessionId);
        assertEquals(AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION,
                controller.currentDirective().action);
    }

    private static VehicleTrackingFrame frame(VehicleCandidate... candidates) {
        return new VehicleTrackingFrame(
                1L, 1L, 1L, 1L, Arrays.asList(candidates)
        );
    }

    private static VehicleCandidate candidate(long entityId, long trackId) {
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(0.2f, 0.2f, 0.8f, 0.8f),
                0.9f, 0.9f, 0.1f,
                false, 0, 1L, 1L, 0,
                EntityAcquisitionState.NEW
        );
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
            SceneContinuityState state
    ) {
        return new SceneTransitionDecision(
                2L,
                action,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                state,
                ContinuityAssessment.none(),
                true, true, true,
                action != SceneTransitionAction.NONE,
                state != SceneContinuityState.STABLE,
                state != SceneContinuityState.STABLE,
                action == SceneTransitionAction.SOFT_REACQUIRE,
                action == SceneTransitionAction.SOFT_REACQUIRE,
                false,
                false, false,
                action == SceneTransitionAction.SOFT_REACQUIRE,
                false,
                "instrumented_scan_continuity"
        );
    }
}
