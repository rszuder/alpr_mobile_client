package com.example.alpr_v1.acquisition;

/** Work requested by the Scan controller for the next pipeline cycle. */
public enum AcquisitionDirectiveAction {
    NONE,
    REQUEST_FRESH_MP,
    REQUEST_EXACT_ENTITY_MT,
    REQUEST_EXPANDED_ENTITY_MT,
    CONTINUE_ACTIVE_SESSION,
    RELEASE_ACTIVE_TARGET
}
