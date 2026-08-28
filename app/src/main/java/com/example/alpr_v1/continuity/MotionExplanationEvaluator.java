package com.example.alpr_v1.continuity;

/** Pure scorer for evidence that a raw visual delta is caused by coherent motion. */
public final class MotionExplanationEvaluator {
    private final float gyroWeight;
    private final float targetWeight;
    private final float vehicleWeight;
    private final float globalMotionWeight;

    public MotionExplanationEvaluator() {
        this(0.25f, 0.30f, 0.25f, 0.20f);
    }

    public MotionExplanationEvaluator(
            float gyroWeight,
            float targetWeight,
            float vehicleWeight,
            float globalMotionWeight
    ) {
        this.gyroWeight = weight("gyroWeight", gyroWeight);
        this.targetWeight = weight("targetWeight", targetWeight);
        this.vehicleWeight = weight("vehicleWeight", vehicleWeight);
        this.globalMotionWeight = weight("globalMotionWeight", globalMotionWeight);
        if (gyroWeight + targetWeight + vehicleWeight + globalMotionWeight <= 0f) {
            throw new IllegalArgumentException("at least one motion weight must be positive");
        }
    }

    public float evaluate(
            MotionExplanationEvidence evidence,
            float targetContinuityScore,
            boolean targetAvailable,
            float vehicleContinuityScore,
            boolean vehiclePoolAvailable
    ) {
        Contracts.required("evidence", evidence);
        Contracts.unit("targetContinuityScore", targetContinuityScore);
        Contracts.unit("vehicleContinuityScore", vehicleContinuityScore);

        float sum = 0f;
        float availableWeight = 0f;
        if (evidence.gyroAvailable || evidence.cameraTransformInProgress) {
            float gyroMotionScore = evidence.cameraTransformInProgress
                    || evidence.rapidCameraMotion
                    ? 1f
                    : evidence.cameraMoving ? 0.75f : 0f;
            sum += gyroWeight * gyroMotionScore;
            availableWeight += gyroWeight;
        }
        if (targetAvailable) {
            sum += targetWeight * targetContinuityScore;
            availableWeight += targetWeight;
        }
        if (vehiclePoolAvailable) {
            sum += vehicleWeight * vehicleContinuityScore;
            availableWeight += vehicleWeight;
        }
        if (evidence.dominantMotionEstimated) {
            sum += globalMotionWeight * evidence.globalMotionCoherence;
            availableWeight += globalMotionWeight;
        }

        return availableWeight > 0f ? clampUnit(sum / availableWeight) : 0f;
    }

    public float evaluate(
            SceneEvidence evidence,
            float targetContinuityScore,
            float vehicleContinuityScore
    ) {
        Contracts.required("evidence", evidence);
        boolean targetAvailable = evidence.target.level != TargetContinuityLevel.NO_TARGET
                && evidence.target.level != TargetContinuityLevel.LOST;
        return evaluate(
                evidence.motion,
                targetContinuityScore,
                targetAvailable,
                vehicleContinuityScore,
                evidence.vehicles.entitiesBefore > 0
        );
    }

    private static float weight(String name, float value) {
        return Contracts.nonNegativeFinite(name, value);
    }

    private static float clampUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
