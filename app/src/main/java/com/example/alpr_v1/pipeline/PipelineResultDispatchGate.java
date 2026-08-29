package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;

/** Final freshness boundary before a completed result leaves the pipeline thread. */
public final class PipelineResultDispatchGate {
    private PipelineResultDispatchGate() {}

    public static boolean isCurrent(
            AlprPipeline pipeline,
            PipelineResult result
    ) {
        return pipeline != null
                && result != null
                && !result.isClosed()
                && pipeline.isCurrentContinuityStamp(result.continuityStamp());
    }

    static boolean isCurrent(
            ContinuityStamp current,
            PipelineResult result
    ) {
        if (current == null || result == null || result.isClosed()) return false;
        ContinuityStamp stamp = result.continuityStamp();
        return current.sceneGeneration == stamp.sceneGeneration
                && current.visualEpoch == stamp.visualEpoch
                && current.cameraTransformGeneration
                == stamp.cameraTransformGeneration;
    }
}
