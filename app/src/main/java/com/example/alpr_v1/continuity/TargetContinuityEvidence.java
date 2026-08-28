package com.example.alpr_v1.continuity;

/** Immutable local-anchor evidence for the currently focused logical target. */
public final class TargetContinuityEvidence {
    public final long entityId;
    public final long vehicleTrackId;
    public final long plateTrackId;
    public final TargetContinuityLevel level;
    public final float focusedTrackingQuality;
    public final int trackerInliers;
    public final float supportRatio;
    public final int consecutiveFailures;
    public final float kalmanInnovationScore;
    public final float geometryConsistency;
    public final float scaleConsistency;
    public final float vehicleAppearanceSimilarity;
    public final float plateAppearanceSimilarity;
    public final float registrationConsistency;
    public final boolean freshVehicleMeasurement;
    public final boolean freshPlateMeasurement;
    public final boolean geometryValidated;
    public final long measurementAgeNanos;

    public TargetContinuityEvidence(
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            TargetContinuityLevel level,
            float focusedTrackingQuality,
            int trackerInliers,
            float supportRatio,
            int consecutiveFailures,
            float kalmanInnovationScore,
            float geometryConsistency,
            float scaleConsistency,
            float vehicleAppearanceSimilarity,
            float plateAppearanceSimilarity,
            float registrationConsistency,
            boolean freshVehicleMeasurement,
            boolean freshPlateMeasurement,
            boolean geometryValidated,
            long measurementAgeNanos
    ) {
        this.entityId = Contracts.nonNegative("entityId", entityId);
        this.vehicleTrackId = Contracts.nonNegative("vehicleTrackId", vehicleTrackId);
        this.plateTrackId = Contracts.nonNegative("plateTrackId", plateTrackId);
        this.level = Contracts.required("level", level);
        this.focusedTrackingQuality = Contracts.unit(
                "focusedTrackingQuality", focusedTrackingQuality
        );
        this.trackerInliers = Contracts.nonNegative("trackerInliers", trackerInliers);
        this.supportRatio = Contracts.unit("supportRatio", supportRatio);
        this.consecutiveFailures = Contracts.nonNegative(
                "consecutiveFailures", consecutiveFailures
        );
        this.kalmanInnovationScore = Contracts.unit(
                "kalmanInnovationScore", kalmanInnovationScore
        );
        this.geometryConsistency = Contracts.unit(
                "geometryConsistency", geometryConsistency
        );
        this.scaleConsistency = Contracts.unit("scaleConsistency", scaleConsistency);
        this.vehicleAppearanceSimilarity = Contracts.unit(
                "vehicleAppearanceSimilarity", vehicleAppearanceSimilarity
        );
        this.plateAppearanceSimilarity = Contracts.unit(
                "plateAppearanceSimilarity", plateAppearanceSimilarity
        );
        this.registrationConsistency = Contracts.unit(
                "registrationConsistency", registrationConsistency
        );
        this.freshVehicleMeasurement = freshVehicleMeasurement;
        this.freshPlateMeasurement = freshPlateMeasurement;
        this.geometryValidated = geometryValidated;
        this.measurementAgeNanos = Contracts.nonNegative(
                "measurementAgeNanos", measurementAgeNanos
        );
    }

    public static TargetContinuityEvidence noTarget() {
        return new TargetContinuityEvidence(
                0L, 0L, 0L, TargetContinuityLevel.NO_TARGET,
                0f, 0, 0f, 0, 0f, 0f, 0f, 0f, 0f, 0f,
                false, false, false, 0L
        );
    }
}
