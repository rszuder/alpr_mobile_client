package com.example.alpr_v1.continuity;

/** Pure classification result; it contains no runtime side effects. */
public final class ContinuityAssessment {
    public final VisualChangeClassification classification;
    public final float targetContinuityScore;
    public final float vehicleContinuityScore;
    public final float motionExplanationScore;
    public final float cutEvidenceScore;
    public final boolean focusedTargetPreserved;
    public final boolean vehiclePoolPreserved;
    public final boolean finalizationAllowed;
    public final String reason;

    public ContinuityAssessment(
            VisualChangeClassification classification,
            float targetContinuityScore,
            float vehicleContinuityScore,
            float motionExplanationScore,
            float cutEvidenceScore,
            boolean focusedTargetPreserved,
            boolean vehiclePoolPreserved,
            boolean finalizationAllowed,
            String reason
    ) {
        this.classification = Contracts.required("classification", classification);
        this.targetContinuityScore = Contracts.unit(
                "targetContinuityScore", targetContinuityScore
        );
        this.vehicleContinuityScore = Contracts.unit(
                "vehicleContinuityScore", vehicleContinuityScore
        );
        this.motionExplanationScore = Contracts.unit(
                "motionExplanationScore", motionExplanationScore
        );
        this.cutEvidenceScore = Contracts.unit("cutEvidenceScore", cutEvidenceScore);
        this.focusedTargetPreserved = focusedTargetPreserved;
        this.vehiclePoolPreserved = vehiclePoolPreserved;
        this.finalizationAllowed = finalizationAllowed;
        this.reason = Contracts.reason(reason);
    }

    public static ContinuityAssessment none() {
        return new ContinuityAssessment(
                VisualChangeClassification.NONE,
                0f, 0f, 0f, 0f,
                false, false, true, ""
        );
    }
}
