package com.example.alpr_v1.continuity;

/** Provenance of a source timestamp; it is not a substitute for source sequence. */
public enum SourceTimestampDomain {
    CAMERAX_SENSOR,
    PREVIEW_INHERITED_CAMERA,
    RUNTIME_UPTIME,
    UNKNOWN;

    public boolean cameraDerived() {
        return this == CAMERAX_SENSOR || this == PREVIEW_INHERITED_CAMERA;
    }

    public boolean freshnessComparableWith(SourceTimestampDomain other) {
        return other != null && cameraDerived() && other.cameraDerived();
    }
}
