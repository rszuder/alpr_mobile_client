package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

import org.junit.Test;

public final class PipelineErrorStampTest {
    @Test
    public void pipelineErrorKeepsActiveContinuityEpochAndSourceIdentity() {
        ContinuityStamp stamp = new ContinuityStamp(
                3L, 9L, 2L,
                44L, 7_000_000_000L,
                SourceTimestampDomain.CAMERAX_SENSOR
        );

        PipelineResult result = PipelineResult.pipelineError("boom", stamp);

        assertEquals("pipeline_error", result.status);
        assertEquals(3L, result.sceneGeneration);
        assertEquals(9L, result.visualEpoch);
        assertEquals(2L, result.cameraTransformGeneration);
        assertEquals(44L, result.sourceSequence);
        assertEquals(SourceTimestampDomain.CAMERAX_SENSOR,
                result.sourceTimestampDomain);
    }
}
