package com.example.alpr_v1.pipeline;

/** Defines whether one cycle may execute every selected ROI or only one item. */
public enum MtExecutionPolicy {
    LEGACY_BURST("legacy_burst"),
    LIVE_STAGGERED("live_staggered");

    private final String wireName;

    MtExecutionPolicy(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static MtExecutionPolicy forExperiment(boolean experimentEnabled) {
        return experimentEnabled ? LEGACY_BURST : LIVE_STAGGERED;
    }
}
