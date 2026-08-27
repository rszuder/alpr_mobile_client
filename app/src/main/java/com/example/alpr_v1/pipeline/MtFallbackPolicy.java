package com.example.alpr_v1.pipeline;

/** Defines whether a failed ROI may trigger full-frame MT in the same cycle. */
public enum MtFallbackPolicy {
    SAME_CYCLE("same_cycle"),
    DEFERRED("deferred");

    private final String wireName;

    MtFallbackPolicy(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static MtFallbackPolicy forExperiment(boolean experimentEnabled) {
        return experimentEnabled ? SAME_CYCLE : DEFERRED;
    }
}
