package com.example.alpr_v1.domain;

public final class CropReference {
    public enum Kind { WIDE_PLATE, ZOOM_PLATE, VEHICLE_CONTEXT }

    public final String referenceId;
    public final Kind kind;
    public final float qualityScore;
    public final long capturedAtNanos;

    public CropReference(
            String referenceId,
            Kind kind,
            float qualityScore,
            long capturedAtNanos
    ) {
        this.referenceId = referenceId == null ? "" : referenceId;
        this.kind = kind == null ? Kind.WIDE_PLATE : kind;
        this.qualityScore = Float.isFinite(qualityScore)
                ? Math.max(0f, Math.min(1f, qualityScore)) : 0f;
        this.capturedAtNanos = Math.max(0L, capturedAtNanos);
    }

    public boolean betterThan(CropReference other) {
        return other == null || qualityScore > other.qualityScore;
    }
}
