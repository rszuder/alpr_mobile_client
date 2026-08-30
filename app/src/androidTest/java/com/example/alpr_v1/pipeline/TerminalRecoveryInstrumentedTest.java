package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.continuity.SceneContinuityProfile;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TerminalRecoveryInstrumentedTest {
    @Test
    public void failedRecoveryCompletesAndAbortsWithoutAnotherEvidenceFrame() {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityProfile.INITIAL
        );
        SceneTransitionDecision started = coordinator.requestSoftReacquire(
                "instrumented_terminal_recovery",
                1_000L,
                10L
        );

        SceneTransitionDecision terminal = coordinator.completeSoftReacquire(
                SoftReacquireResult.FAILED,
                2_000L
        );
        TerminalRecoveryDirective directive =
                TerminalRecoveryDirective.forTerminalResult(
                        SoftReacquireResult.FAILED,
                        terminal
                );
        SceneTransitionDecision duplicate = coordinator.completeSoftReacquire(
                SoftReacquireResult.FAILED,
                2_001L
        );

        assertEquals(SceneTransitionAction.SOFT_REACQUIRE, started.action);
        assertEquals(SceneTransitionAction.NONE, terminal.action);
        assertEquals(SceneContinuityState.STABLE, terminal.nextState);
        assertTrue(directive.abortCurrentFrame);
        assertTrue(directive.requestImmediateFrame);
        assertTrue(MobileAlprEngine.shouldAbortAfterFreshMp(directive));
        assertEquals(SceneTransitionAction.NONE, duplicate.action);
        assertEquals(terminal.revision, duplicate.revision);
        assertFalse(coordinator.snapshot().finalizationSuspended);
    }
}
