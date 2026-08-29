package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;

/** Ensures secondary scene evidence is decided before any inference mutation. */
final class SecondaryScenePreflightGate {
    interface InferenceMutation<T> {
        T run() throws Exception;
    }

    private final boolean skipsInference;

    private SecondaryScenePreflightGate(boolean skipsInference) {
        this.skipsInference = skipsInference;
    }

    static SecondaryScenePreflightGate from(
            SceneTransitionDecision decision,
            boolean coordinatorSuspendsHeavyInference
    ) {
        boolean skip = coordinatorSuspendsHeavyInference
                || decision != null
                && (decision.action == SceneTransitionAction.HARD_RESET
                || decision.action == SceneTransitionAction.SOFT_REACQUIRE
                || decision.suspendHeavyInference);
        return new SecondaryScenePreflightGate(skip);
    }

    boolean skipsInference() {
        return skipsInference;
    }

    <T> T run(InferenceMutation<T> mutation) throws Exception {
        if (skipsInference || mutation == null) return null;
        return mutation.run();
    }
}
