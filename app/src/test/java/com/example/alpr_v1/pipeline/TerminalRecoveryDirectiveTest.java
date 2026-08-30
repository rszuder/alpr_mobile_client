package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityAssessment;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;

import org.junit.Test;

public final class TerminalRecoveryDirectiveTest {
    @Test
    public void targetRecoveredMayContinueToMzForSameEntity() {
        TerminalRecoveryDirective directive = TerminalRecoveryDirective.forTerminalResult(
                SoftReacquireResult.TARGET_RECOVERED,
                decision(SceneTransitionAction.NONE)
        );

        assertFalse(directive.abortCurrentFrame);
        assertFalse(directive.requestImmediateFrame);
        assertFalse(MobileAlprEngine.shouldAbortAfterFreshMp(directive));
    }

    @Test
    public void everyMpTerminalOutcomeWithoutRecoveredTargetStopsBeforeMtAndMz() {
        SoftReacquireResult[] results = {
                SoftReacquireResult.VEHICLE_POOL_RECOVERED,
                SoftReacquireResult.ACTIVE_TARGET_LOST,
                SoftReacquireResult.FAILED
        };

        for (SoftReacquireResult result : results) {
            int mtRuns = 0;
            int mzRuns = 0;
            TerminalRecoveryDirective directive =
                    TerminalRecoveryDirective.forTerminalResult(
                            result,
                            decision(SceneTransitionAction.RELEASE_ACTIVE_TARGET)
                    );
            if (!MobileAlprEngine.shouldAbortAfterFreshMp(directive)) {
                mtRuns++;
                mzRuns++;
            }

            assertTrue(directive.abortCurrentFrame);
            assertTrue(directive.requestImmediateFrame);
            assertEquals(0, mtRuns);
            assertEquals(0, mzRuns);
        }
    }

    private static SceneTransitionDecision decision(SceneTransitionAction action) {
        return new SceneTransitionDecision(
                1L,
                action,
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityState.STABLE,
                ContinuityAssessment.none(),
                true, true, true,
                false, false, false,
                false, false, false,
                false, false,
                false, false,
                "test"
        );
    }
}
