package com.example.alpr_v1.continuity;

/** Policy for applying an asynchronous result against the current generation stamp. */
public enum ContinuityResultDisposition {
    ACCEPT_ALL(true, true, true),
    REJECT_GEOMETRY_CROP_AND_FINALIZATION(true, false, false),
    REJECT_STALE_CAMERA_TRANSFORM(true, false, false),
    REJECT_ALL(false, false, false);

    private final boolean domainEvidenceAllowed;
    private final boolean geometryAllowed;
    private final boolean finalizationAllowed;

    ContinuityResultDisposition(
            boolean domainEvidenceAllowed,
            boolean geometryAllowed,
            boolean finalizationAllowed
    ) {
        this.domainEvidenceAllowed = domainEvidenceAllowed;
        this.geometryAllowed = geometryAllowed;
        this.finalizationAllowed = finalizationAllowed;
    }

    public boolean allowsDomainEvidence() {
        return domainEvidenceAllowed;
    }

    public boolean allowsGeometry() {
        return geometryAllowed;
    }

    public boolean allowsFinalization() {
        return finalizationAllowed;
    }
}
