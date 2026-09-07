package com.example.alpr_v1.continuity;

/** Dynamic continuity interprets object, pool and motion evidence together. */
public final class DynamicContinuityPolicy implements ScenePolicy {
    private final TargetContinuityEvaluator targetEvaluator;
    private final VehicleContinuityEvaluator vehicleEvaluator;
    private final MotionExplanationEvaluator motionEvaluator;
    private final ContinuityBreakEvaluator breakEvaluator;

    public DynamicContinuityPolicy(TargetContinuityEvaluator target, VehicleContinuityEvaluator vehicles,
            MotionExplanationEvaluator motion, ContinuityBreakEvaluator continuityBreak) {
        targetEvaluator = target;
        vehicleEvaluator = vehicles;
        motionEvaluator = motion;
        breakEvaluator = continuityBreak;
    }
    @Override
    public ContinuityAssessment assess(SceneEvidence evidence, SceneContinuityProfile profile) {
        float targetScore = targetEvaluator.evaluate(evidence.target, profile);
        float vehicleScore = vehicleEvaluator.evaluate(evidence.vehicles);
        float motionScore = motionEvaluator.evaluate(evidence, targetScore, vehicleScore);
        float cutScore = breakEvaluator.evaluate(
                evidence, targetScore, vehicleScore, motionScore
        );
        boolean stationaryLocalContradiction = evidence.rawVisualChange
                && !evidence.motion.cameraMoving
                && !evidence.motion.rapidCameraMotion
                && !evidence.motion.cameraTransformInProgress
                && !evidence.motion.motionSettling
                && evidence.target.localAppearanceValidated
                && evidence.target.plateAppearanceSimilarity
                < profile.localAppearanceContradictionThreshold;
        boolean stationaryStaleTargetEvidence = evidence.rawVisualChange
                && !evidence.motion.cameraMoving
                && !evidence.motion.rapidCameraMotion
                && !evidence.motion.cameraTransformInProgress
                && !evidence.motion.motionSettling
                && evidence.target.level != TargetContinuityLevel.NO_TARGET
                && evidence.target.measurementAgeNanos
                > profile.maximumFocusedEvidenceAgeNanos;
        boolean targetPreserved = targetScore >= profile.minimumTargetContinuityToPreserve
                && !stationaryLocalContradiction
                && !stationaryStaleTargetEvidence;
        boolean poolPreserved = vehicleScore >= profile.minimumVehicleContinuityToPreserve;
        boolean motionExplained = motionScore >= profile.minimumMotionExplanation
                && !stationaryLocalContradiction
                && !stationaryStaleTargetEvidence;

        VisualChangeClassification classification;
        String reason;
        if (!evidence.rawVisualChange) {
            classification = VisualChangeClassification.NONE;
            reason = "no_raw_visual_change";
        } else if (motionExplained && (targetPreserved || poolPreserved)) {
            classification = VisualChangeClassification.MOTION_EXPLAINED_CHANGE;
            reason = targetPreserved
                    ? "local_target_explains_visual_change"
                    : "vehicle_pool_explains_visual_change";
        } else if (stationaryLocalContradiction) {
            classification = VisualChangeClassification.UNEXPLAINED_CHANGE;
            reason = "stationary_local_appearance_contradiction";
        } else if (stationaryStaleTargetEvidence) {
            classification = VisualChangeClassification.UNEXPLAINED_CHANGE;
            reason = "stationary_target_evidence_predates_visual_change";
        } else if (!targetPreserved && !poolPreserved && !motionExplained) {
            classification = VisualChangeClassification.UNEXPLAINED_CHANGE;
            reason = "visual_change_has_no_continuity_explanation";
        } else {
            classification = VisualChangeClassification.RAW_VISUAL_CHANGE;
            reason = "visual_change_requires_more_evidence";
        }

        boolean freshValidatedTarget = targetPreserved
                && evidence.target.geometryValidated
                && (evidence.target.freshVehicleMeasurement
                || evidence.target.freshPlateMeasurement)
                && evidence.target.level != TargetContinuityLevel.PREDICTED_ONLY;
        boolean continuityAllowsFinalization = !evidence.rawVisualChange
                || freshValidatedTarget;
        return new ContinuityAssessment(
                classification,
                targetScore,
                vehicleScore,
                motionScore,
                cutScore,
                targetPreserved,
                poolPreserved,
                continuityAllowsFinalization,
                reason
        );
    }

}
