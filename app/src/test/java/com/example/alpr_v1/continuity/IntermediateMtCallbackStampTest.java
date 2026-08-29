package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class IntermediateMtCallbackStampTest {
    @Test
    public void intermediateMtCallbackFromOldVisualEpochIsRejectedBeforeSideEffects() {
        ContinuityStamp current = new ContinuityStamp(2L, 8L, 1L, 2_000L);
        ContinuityStamp staleMt = new ContinuityStamp(2L, 7L, 1L, 1_000L);
        ContinuityGenerationGate gate = new ContinuityGenerationGate();
        boolean[] overlayPublished = {false};
        boolean[] targetAnchored = {false};
        boolean[] snapshotUpdated = {false};

        if (gate.evaluate(current, staleMt) == ContinuityResultDisposition.ACCEPT_ALL) {
            overlayPublished[0] = true;
            targetAnchored[0] = true;
            snapshotUpdated[0] = true;
        }

        assertFalse(overlayPublished[0]);
        assertFalse(targetAnchored[0]);
        assertFalse(snapshotUpdated[0]);
    }
}
