package com.example.alpr_v1.acquisition;

public enum ScanRunState {
    IDLE,
    RUNNING,
    PAUSED,
    STOPPED;

    public boolean active() {
        return this == RUNNING || this == PAUSED;
    }
}
