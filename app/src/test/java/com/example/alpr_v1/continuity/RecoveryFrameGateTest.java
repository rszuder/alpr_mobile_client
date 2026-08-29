package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RecoveryFrameGateTest {
    @Test
    public void sourceGateDoesNotAssumeRuntimeAndSourceClockOffsetsMatch() {
        long runtimeStartNanos = 1_000_000_000L;
        long triggerSourceTimestampNanos = 8_000_000_000L;
        ReacquireContext context = ReacquireContext.begin(
                new ContinuityAssessment(
                        VisualChangeClassification.UNEXPLAINED_CHANGE,
                        0f, 0f, 0f, 0.8f,
                        false, false, false, "test"
                ),
                runtimeStartNanos,
                triggerSourceTimestampNanos,
                false
        );
        ReacquireTelemetry active = ReacquireTelemetry.from(
                context, true, "", false, false
        );

        assertTrue(RecoveryFrameGate.shouldSkip(active, 7_999_000_000L));
        assertFalse(RecoveryFrameGate.shouldSkip(active, 8_000_000_000L));
        assertFalse(RecoveryFrameGate.shouldSkip(active, 8_001_000_000L));
        assertTrue(active.startedRuntimeNanos != active.triggerSourceTimestampNanos);
        assertFalse(RecoveryFrameGate.shouldSkip(
                ReacquireTelemetry.from(context, false, "FAILED", false, true),
                7_999_000_000L
        ));
    }
}
