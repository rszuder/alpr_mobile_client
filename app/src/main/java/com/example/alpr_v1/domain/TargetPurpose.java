package com.example.alpr_v1.domain;

public enum TargetPurpose {
    SCAN_ACQUISITION(false),
    SEARCH_VERIFICATION(false),
    SEARCH_PURSUIT(true),
    USER_PICK(true);

    private final boolean persistent;

    TargetPurpose(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean persistent() {
        return persistent;
    }
}
