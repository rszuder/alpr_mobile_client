package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RecoveryFrameGateTest {
    @Test
    public void frameCapturedBeforeRecoveryIsSkipped() {
        ReacquireContext context = ReacquireContext.begin(
                new ContinuityAssessment(
                        VisualChangeClassification.UNEXPLAINED_CHANGE,
                        0f, 0f, 0f, 0.8f,
                        false, false, false, "test"
                ),
                1_000L,
                false
        );
        ReacquireTelemetry active = ReacquireTelemetry.from(
                context, true, "", false, false
        );

        assertTrue(RecoveryFrameGate.shouldSkip(active, 999L));
        assertFalse(RecoveryFrameGate.shouldSkip(active, 1_000L));
        assertFalse(RecoveryFrameGate.shouldSkip(
                ReacquireTelemetry.from(context, false, "FAILED", false, true),
                999L
        ));
    }
}
