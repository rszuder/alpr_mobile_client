package com.example.alpr_v1.domain;

public enum TargetSessionState {
    CREATED,
    PROVISIONAL_HOLD,
    ACQUIRING_PLATE,
    READING_REGISTRATION,
    TRACKING,
    LOCKED_IDENTIFIED,
    LOCKED_UNIDENTIFIED,
    RECOVERING,
    COMPLETED,
    CANCELLED,
    LOST;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == LOST;
    }
}
