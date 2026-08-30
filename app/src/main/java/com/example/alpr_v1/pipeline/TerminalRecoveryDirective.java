package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;

/** Synchronous command returned to the engine that produced a terminal recovery result. */
public final class TerminalRecoveryDirective {
    public final SoftReacquireResult result;
    public final SceneTransitionDecision decision;
    public final boolean abortCurrentFrame;
    public final boolean requestImmediateFrame;

    public TerminalRecoveryDirective(
            SoftReacquireResult result,
            SceneTransitionDecision decision,
            boolean abortCurrentFrame,
            boolean requestImmediateFrame
    ) {
        if (result == null) throw new IllegalArgumentException("result");
        if (decision == null) throw new IllegalArgumentException("decision");
        this.result = result;
        this.decision = decision;
        this.abortCurrentFrame = abortCurrentFrame;
        this.requestImmediateFrame = requestImmediateFrame;
    }

    public static TerminalRecoveryDirective continueCurrentFrame(
            SoftReacquireResult result,
            SceneTransitionDecision decision
    ) {
        return new TerminalRecoveryDirective(result, decision, false, false);
    }

    public static TerminalRecoveryDirective abortAndRequestNext(
            SoftReacquireResult result,
            SceneTransitionDecision decision
    ) {
        return new TerminalRecoveryDirective(result, decision, true, true);
    }

    public static TerminalRecoveryDirective forTerminalResult(
            SoftReacquireResult result,
            SceneTransitionDecision decision
    ) {
        if (result == SoftReacquireResult.TARGET_RECOVERED) {
            return continueCurrentFrame(result, decision);
        }
        return abortAndRequestNext(result, decision);
    }
}
