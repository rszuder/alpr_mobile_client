package com.example.alpr_v1.acquisition;

public enum AcquisitionSessionOutcome {
    NONE,
    STARTED,
    PROGRESS,
    READ_CAPTURED,
    READY_TO_FINALIZE,
    DEFERRED,
    LOST,
    TIMED_OUT,
    CANCELLED
}
