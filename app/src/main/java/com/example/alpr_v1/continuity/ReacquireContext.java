package com.example.alpr_v1.continuity;

/** Immutable evidence retained from the trigger until recovery reaches a terminal state. */
public final class ReacquireContext {
    public final VisualChangeClassification triggerClassification;
    public final float triggerCutEvidenceScore;
    public final float triggerTargetContinuityScore;
    public final float triggerVehicleContinuityScore;
    public final float triggerMotionExplanationScore;
    public final float maximumCutEvidenceDuringRecovery;
    public final long startedNanos;
    public final boolean activeTargetPresent;

    private ReacquireContext(
            VisualChangeClassification triggerClassification,
            float triggerCutEvidenceScore,
            float triggerTargetContinuityScore,
            float triggerVehicleContinuityScore,
            float triggerMotionExplanationScore,
            float maximumCutEvidenceDuringRecovery,
            long startedNanos,
            boolean activeTargetPresent
    ) {
        this.triggerClassification = Contracts.required(
                "triggerClassification", triggerClassification
        );
        this.triggerCutEvidenceScore = Contracts.unit(
                "triggerCutEvidenceScore", triggerCutEvidenceScore
        );
        this.triggerTargetContinuityScore = Contracts.unit(
                "triggerTargetContinuityScore", triggerTargetContinuityScore
        );
        this.triggerVehicleContinuityScore = Contracts.unit(
                "triggerVehicleContinuityScore", triggerVehicleContinuityScore
        );
        this.triggerMotionExplanationScore = Contracts.unit(
                "triggerMotionExplanationScore", triggerMotionExplanationScore
        );
        this.maximumCutEvidenceDuringRecovery = Contracts.unit(
                "maximumCutEvidenceDuringRecovery", maximumCutEvidenceDuringRecovery
        );
        this.startedNanos = Contracts.nonNegative("startedNanos", startedNanos);
        this.activeTargetPresent = activeTargetPresent;
    }

    static ReacquireContext begin(
            ContinuityAssessment trigger,
            long startedNanos,
            boolean activeTargetPresent
    ) {
        Contracts.required("trigger", trigger);
        return new ReacquireContext(
                trigger.classification,
                trigger.cutEvidenceScore,
                trigger.targetContinuityScore,
                trigger.vehicleContinuityScore,
                trigger.motionExplanationScore,
                trigger.cutEvidenceScore,
                startedNanos,
                activeTargetPresent
        );
    }

    ReacquireContext observe(ContinuityAssessment current) {
        Contracts.required("current", current);
        float maximum = Math.max(
                maximumCutEvidenceDuringRecovery,
                current.cutEvidenceScore
        );
        if (maximum == maximumCutEvidenceDuringRecovery) return this;
        return new ReacquireContext(
                triggerClassification,
                triggerCutEvidenceScore,
                triggerTargetContinuityScore,
                triggerVehicleContinuityScore,
                triggerMotionExplanationScore,
                maximum,
                startedNanos,
                activeTargetPresent
        );
    }
}
