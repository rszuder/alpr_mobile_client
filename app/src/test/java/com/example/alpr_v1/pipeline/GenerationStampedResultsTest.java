package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.InferenceTrace;

import org.junit.Test;

import java.util.Collections;

public final class GenerationStampedResultsTest {
    private static final ContinuityStamp STAMP = new ContinuityStamp(2L, 7L, 4L, 900L);

    @Test
    public void pipelineResultCarriesCompleteContinuityStamp() {
        PipelineResult result = new PipelineResult(
                "empty", "test", Collections.emptyList(), Collections.emptyList(),
                1280, 720, Collections.emptyList(), false, STAMP
        );

        assertStamp(result.continuityStamp());
    }

    @Test
    public void targetAnchorCanBeRestampedWithoutLosingTargetIdentity() {
        TargetSnapshot target = TargetSnapshot.searching().withContinuityStamp(STAMP);

        assertStamp(target.continuityStamp());
        assertEquals(TargetSnapshot.State.SEARCHING, target.state);
    }

    @Test
    public void inferenceTraceExposesGenerationStampApi() throws Exception {
        InferenceTrace.class.getConstructor(long.class, ContinuityStamp.class);
        InferenceTrace.class.getMethod("sceneGeneration");
        InferenceTrace.class.getMethod("visualEpoch");
        InferenceTrace.class.getMethod("cameraTransformGeneration");
        InferenceTrace.class.getMethod("sourceTimestampNanos");
    }

    private static void assertStamp(ContinuityStamp stamp) {
        assertEquals(2L, stamp.sceneGeneration);
        assertEquals(7L, stamp.visualEpoch);
        assertEquals(4L, stamp.cameraTransformGeneration);
        assertEquals(900L, stamp.sourceTimestampNanos);
    }
}
