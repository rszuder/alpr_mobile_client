package com.example.alpr_v1.continuity;

/** Separates raw pixel evidence from an actual break in logical continuity. */
public enum VisualChangeClassification {
    NONE,
    RAW_VISUAL_CHANGE,
    MOTION_EXPLAINED_CHANGE,
    UNEXPLAINED_CHANGE,
    CONTINUITY_BREAK
}
