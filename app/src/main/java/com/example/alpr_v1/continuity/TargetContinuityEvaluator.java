package com.example.alpr_v1.continuity;

/** Pure, configurable scorer for the focused target's local continuity. */
public final class TargetContinuityEvaluator {
    private final float focusedTrackingWeight;
    private final float inlierWeight;
    private final float geometryWeight;
    private final float kalmanWeight;
    private final float vehicleAppearanceWeight;
    private final float plateAppearanceWeight;
    private final float registrationWeight;

    public TargetContinuityEvaluator() {
        this(0.24f, 0.14f, 0.14f, 0.12f, 0.14f, 0.10f, 0.12f);
    }

    public TargetContinuityEvaluator(
            float focusedTrackingWeight,
            float inlierWeight,
            float geometryWeight,
            float kalmanWeight,
            float vehicleAppearanceWeight,
            float plateAppearanceWeight,
            float registrationWeight
    ) {
        this.focusedTrackingWeight = weight("focusedTrackingWeight", focusedTrackingWeight);
        this.inlierWeight = weight("inlierWeight", inlierWeight);
        this.geometryWeight = weight("geometryWeight", geometryWeight);
        this.kalmanWeight = weight("kalmanWeight", kalmanWeight);
        this.vehicleAppearanceWeight = weight(
                "vehicleAppearanceWeight", vehicleAppearanceWeight
        );
        this.plateAppearanceWeight = weight("plateAppearanceWeight", plateAppearanceWeight);
        this.registrationWeight = weight("registrationWeight", registrationWeight);
        if (totalWeight() <= 0f) {
            throw new IllegalArgumentException("at least one target weight must be positive");
        }
    }

    public float evaluate(
            TargetContinuityEvidence evidence,
            SceneContinuityProfile profile
    ) {
        Contracts.required("evidence", evidence);
        Contracts.required("profile", profile);
        if (evidence.level == TargetContinuityLevel.NO_TARGET
                || evidence.level == TargetContinuityLevel.LOST) {
            return 0f;
        }

        float minimumInliers = Math.max(1, profile.minimumTrackerInliers);
        float inlierCountScore = Math.min(1f, evidence.trackerInliers / minimumInliers);
        float inlierScore = 0.5f * inlierCountScore + 0.5f * evidence.supportRatio;

        float sum = focusedTrackingWeight * evidence.focusedTrackingQuality
                + inlierWeight * inlierScore
                + geometryWeight * evidence.geometryConsistency
                + kalmanWeight * evidence.kalmanInnovationScore
                + registrationWeight * evidence.registrationConsistency;
        float availableWeight = focusedTrackingWeight
                + inlierWeight
                + geometryWeight
                + kalmanWeight
                + registrationWeight;

        if (hasVehicleAnchor(evidence.level)) {
            sum += vehicleAppearanceWeight * evidence.vehicleAppearanceSimilarity;
            availableWeight += vehicleAppearanceWeight;
        }
        if (hasPlateAnchor(evidence.level)) {
            sum += plateAppearanceWeight * evidence.plateAppearanceSimilarity;
            availableWeight += plateAppearanceWeight;
        }

        return availableWeight > 0f ? clampUnit(sum / availableWeight) : 0f;
    }

    private float totalWeight() {
        return focusedTrackingWeight + inlierWeight + geometryWeight + kalmanWeight
                + vehicleAppearanceWeight + plateAppearanceWeight + registrationWeight;
    }

    private static boolean hasVehicleAnchor(TargetContinuityLevel level) {
        return level == TargetContinuityLevel.VEHICLE_AND_PLATE
                || level == TargetContinuityLevel.VEHICLE_ONLY;
    }

    private static boolean hasPlateAnchor(TargetContinuityLevel level) {
        return level == TargetContinuityLevel.VEHICLE_AND_PLATE
                || level == TargetContinuityLevel.PLATE_ONLY;
    }

    private static float weight(String name, float value) {
        return Contracts.nonNegativeFinite(name, value);
    }

    private static float clampUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
