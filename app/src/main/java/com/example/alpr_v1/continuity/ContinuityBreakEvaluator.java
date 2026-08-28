package com.example.alpr_v1.continuity;

/** Pure scorer for evidence that the current visual stream lost scene continuity. */
public final class ContinuityBreakEvaluator {
    private static final float RAW_VISUAL_RESIDUAL_WEIGHT = 0.28f;
    private static final float LACK_OF_MOTION_MODEL_WEIGHT = 0.22f;
    private static final float LACK_OF_TARGET_CONTINUITY_WEIGHT = 0.24f;
    private static final float LACK_OF_VEHICLE_CONTINUITY_WEIGHT = 0.18f;
    private static final float NO_GYRO_MOTION_WEIGHT = 0.08f;

    public float evaluate(
            SceneEvidence evidence,
            float targetContinuityScore,
            float vehicleContinuityScore,
            float motionExplanationScore
    ) {
        Contracts.required("evidence", evidence);
        Contracts.unit("targetContinuityScore", targetContinuityScore);
        Contracts.unit("vehicleContinuityScore", vehicleContinuityScore);
        Contracts.unit("motionExplanationScore", motionExplanationScore);
        if (!evidence.rawVisualChange) return 0f;

        float lackOfTargetContinuity = targetAvailable(evidence)
                ? 1f - targetContinuityScore
                : 1f;
        float lackOfVehicleContinuity = evidence.vehicles.entitiesBefore > 0
                ? 1f - vehicleContinuityScore
                : 1f;
        float noGyroMotion = evidence.motion.gyroAvailable
                && !evidence.motion.cameraMoving
                && !evidence.motion.rapidCameraMotion
                ? 1f : 0f;

        return clampUnit(
                RAW_VISUAL_RESIDUAL_WEIGHT * evidence.rawVisualChangeScore
                        + LACK_OF_MOTION_MODEL_WEIGHT * (1f - motionExplanationScore)
                        + LACK_OF_TARGET_CONTINUITY_WEIGHT * lackOfTargetContinuity
                        + LACK_OF_VEHICLE_CONTINUITY_WEIGHT * lackOfVehicleContinuity
                        + NO_GYRO_MOTION_WEIGHT * noGyroMotion
        );
    }

    private static boolean targetAvailable(SceneEvidence evidence) {
        return evidence.target.level != TargetContinuityLevel.NO_TARGET
                && evidence.target.level != TargetContinuityLevel.LOST;
    }

    private static float clampUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
