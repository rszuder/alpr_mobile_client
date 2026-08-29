package com.example.alpr_v1.continuity;

/** Outcome reported by runtime after a forced fresh MP/MT reacquisition attempt. */
public enum SoftReacquireResult {
    TARGET_RECOVERED,
    VEHICLE_POOL_RECOVERED,
    ACTIVE_TARGET_LOST,
    FAILED
}
