package com.example.alpr_v1.pipeline;

public enum VehicleTrackingPolicy {
    RAW_MP("raw_mp"),
    TRACKED_MP("tracked_mp");

    private final String wireName;

    VehicleTrackingPolicy(String wireName) { this.wireName = wireName; }

    public String wireName() { return wireName; }

    public static VehicleTrackingPolicy forExperiment(boolean experimentMode) {
        return experimentMode ? RAW_MP : TRACKED_MP;
    }
}
