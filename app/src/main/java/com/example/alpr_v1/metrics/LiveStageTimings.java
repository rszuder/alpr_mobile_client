package com.example.alpr_v1.metrics;

import com.example.alpr_v1.continuity.ContinuityStamp;
import java.util.HashMap;
import java.util.Map;

/** Last actual run of each model in the current image generation, independent of HUD polling. */
public final class LiveStageTimings {
    private ContinuityStamp stamp;
    private final Map<String, Long> timings = new HashMap<>();

    public void observe(ContinuityStamp next, Map<String, Long> durations) {
        if (stamp == null || next.sceneGeneration != stamp.sceneGeneration
                || next.visualEpoch != stamp.visualEpoch
                || next.cameraTransformGeneration != stamp.cameraTransformGeneration) reset();
        stamp = next;
        for (String stage : new String[]{"vehicle_inference", "plate_inference", "character_inference"}) {
            Long duration = durations.get(stage);
            if (duration != null && duration >= 0L) timings.put(stage, duration);
        }
    }

    public double milliseconds(String stage) {
        Long duration = timings.get(stage);
        return duration == null ? Double.NaN : duration / 1_000_000.0;
    }

    public void reset() { stamp = null; timings.clear(); }
}
