package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SourceTimelineOffsetInstrumentedTest {
    @Test
    public void cameraSequenceRemainsFreshWithLargeRuntimeOffset() {
        long runtimeNow = SystemClock.elapsedRealtimeNanos();
        long arbitraryCameraTimestamp = runtimeNow > 20_000_000_000L
                ? runtimeNow - 20_000_000_000L
                : runtimeNow + 20_000_000_000L;
        ContinuityAssessment trigger = new ContinuityAssessment(
                VisualChangeClassification.RAW_VISUAL_CHANGE,
                0f, 0f, 0f, 0.7f,
                false, false, false,
                "device_offset"
        );
        ReacquireContext context = ReacquireContext.begin(
                trigger,
                runtimeNow,
                700L,
                arbitraryCameraTimestamp,
                SourceTimestampDomain.PREVIEW_INHERITED_CAMERA,
                false
        );
        ReacquireTelemetry recovery = ReacquireTelemetry.from(
                context, true, "", false, false
        );

        assertTrue(RecoveryFrameGate.shouldSkip(
                recovery,
                700L,
                runtimeNow + 99_000_000_000L,
                SourceTimestampDomain.CAMERAX_SENSOR
        ));
        assertFalse(RecoveryFrameGate.shouldSkip(
                recovery,
                701L,
                1L,
                SourceTimestampDomain.CAMERAX_SENSOR
        ));
    }
}
