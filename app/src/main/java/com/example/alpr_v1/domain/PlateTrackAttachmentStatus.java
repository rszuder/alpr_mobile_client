package com.example.alpr_v1.domain;

/** Result of enforcing the one-active-owner invariant for an MT plate track. */
public enum PlateTrackAttachmentStatus {
    ATTACHED,
    REFRESHED,
    REASSIGNED,
    CONFLICT_REJECTED;

    public boolean accepted() {
        return this != CONFLICT_REJECTED;
    }
}
