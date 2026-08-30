package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SourceSequenceRecoveryGateTest {
    @Test
    public void sequenceControlsFreshnessAcrossArbitraryClockOffset() {
        ReacquireTelemetry recovery = recovery(
                10L,
                8_000_000_000L,
                SourceTimestampDomain.PREVIEW_INHERITED_CAMERA
        );

        assertTrue(RecoveryFrameGate.shouldSkip(
                recovery, 10L, 99_000_000_000L,
                SourceTimestampDomain.CAMERAX_SENSOR
        ));
        assertFalse(RecoveryFrameGate.shouldSkip(
                recovery, 11L, 1L,
                SourceTimestampDomain.CAMERAX_SENSOR
        ));
    }

    @Test
    public void missingSourceIsNeverInventedAsFresh() {
        ReacquireTelemetry recovery = recovery(
                0L,
                0L,
                SourceTimestampDomain.UNKNOWN
        );

        assertTrue(RecoveryFrameGate.shouldSkip(
                recovery, 0L, 0L, SourceTimestampDomain.UNKNOWN
        ));
        assertFalse(RecoveryFrameGate.shouldSkip(
                recovery, 1L, 0L, SourceTimestampDomain.CAMERAX_SENSOR
        ));
    }

    private static ReacquireTelemetry recovery(
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain domain
    ) {
        ContinuityAssessment trigger = new ContinuityAssessment(
                VisualChangeClassification.RAW_VISUAL_CHANGE,
                0f, 0f, 0f, 0.7f,
                false, false, false,
                "test_trigger"
        );
        ReacquireContext context = ReacquireContext.begin(
                trigger,
                1_000_000_000L,
                sourceSequence,
                sourceTimestampNanos,
                domain,
                false
        );
        return ReacquireTelemetry.from(context, true, "", false, false);
    }
}
