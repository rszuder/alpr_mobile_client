package com.example.alpr_v1.metrics;

import com.example.alpr_v1.continuity.ContinuityStamp;
import java.util.HashMap;
import java.util.Map;

/** Last actual run of each model in the current image generation, independent of HUD polling. */
public final class LiveStageTimings {
    private ContinuityStamp stamp;
    private final Map<String, Long> timings = new HashMap<>();
    private final Map<String, Long> lastRuns = new HashMap<>();
    private final Map<String, String> outcomes = new HashMap<>();
    private boolean retainAcrossScenes;

    public void setRetainAcrossScenes(boolean retain) { retainAcrossScenes=retain; }
    public boolean retainAcrossScenes() { return retainAcrossScenes; }

    public void observe(ContinuityStamp next, Map<String, Long> durations) {
        observe(next,durations,0L,"");
    }
    public void observe(ContinuityStamp next, Map<String, Long> durations,long elapsedNanos,String outcome) {
        if (!retainAcrossScenes && (stamp == null || next.sceneGeneration != stamp.sceneGeneration
                || next.visualEpoch != stamp.visualEpoch
                || next.cameraTransformGeneration != stamp.cameraTransformGeneration)) reset();
        stamp = next;
        for (String stage : new String[]{"vehicle_inference", "plate_inference", "character_inference"}) {
            Long duration = durations.get(stage);
            if (duration != null && duration >= 0L) {
                timings.put(stage, duration);lastRuns.put(stage,elapsedNanos);outcomes.put(stage,outcome);
            }
        }
    }

    public double milliseconds(String stage) {
        Long duration = timings.get(stage);
        return duration == null ? Double.NaN : duration / 1_000_000.0;
    }

    public Map<String,Long> lastRuns() { return new HashMap<>(lastRuns); }
    public Map<String,String> outcomes() { return new HashMap<>(outcomes); }
    public void reset() { stamp = null; timings.clear();lastRuns.clear();outcomes.clear(); }
}
