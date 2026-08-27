package com.example.alpr_v1.domain;

/** Top-level functional mode. UI integration is intentionally deferred. */
public enum ApplicationMode {
    SCAN_ACQUIRE,
    PICK_ACQUIRE_LOCK,
    SEARCH_VERIFY_PURSUIT
}
