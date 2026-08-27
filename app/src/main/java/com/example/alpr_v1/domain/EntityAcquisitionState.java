package com.example.alpr_v1.domain;

public enum EntityAcquisitionState {
    NEW,
    QUEUED,
    ACQUIRING,
    PLATE_LOCALIZED,
    READING_REGISTRATION,
    ACQUIRED,
    FAILED,
    EXPIRED
}
