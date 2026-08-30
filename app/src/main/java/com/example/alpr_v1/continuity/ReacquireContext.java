package com.example.alpr_v1.continuity;

/** Immutable evidence retained from the trigger until recovery reaches a terminal state. */
public final class ReacquireContext {
    public final VisualChangeClassification triggerClassification;
    public final float triggerCutEvidenceScore;
    public final float triggerTargetContinuityScore;
    public final float triggerVehicleContinuityScore;
    public final float triggerMotionExplanationScore;
    public final float maximumCutEvidenceDuringRecovery;
    public final long startedRuntimeNanos;
    public final long triggerSourceSequence;
    public final long triggerSourceTimestampNanos;
    public final SourceTimestampDomain triggerSourceTimestampDomain;
    public final boolean activeTargetPresent;

    private ReacquireContext(
            VisualChangeClassification triggerClassification,
            float triggerCutEvidenceScore,
            float triggerTargetContinuityScore,
            float triggerVehicleContinuityScore,
            float triggerMotionExplanationScore,
            float maximumCutEvidenceDuringRecovery,
            long startedRuntimeNanos,
            long triggerSourceSequence,
            long triggerSourceTimestampNanos,
            SourceTimestampDomain triggerSourceTimestampDomain,
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
        this.startedRuntimeNanos = Contracts.nonNegative(
                "startedRuntimeNanos", startedRuntimeNanos
        );
        this.triggerSourceSequence = Contracts.nonNegative(
                "triggerSourceSequence", triggerSourceSequence
        );
        this.triggerSourceTimestampNanos = Contracts.nonNegative(
                "triggerSourceTimestampNanos", triggerSourceTimestampNanos
        );
        this.triggerSourceTimestampDomain = triggerSourceTimestampDomain == null
                ? SourceTimestampDomain.UNKNOWN : triggerSourceTimestampDomain;
        this.activeTargetPresent = activeTargetPresent;
    }

    static ReacquireContext begin(
            ContinuityAssessment trigger,
            long startedRuntimeNanos,
            long triggerSourceTimestampNanos,
            boolean activeTargetPresent
    ) {
        return begin(
                trigger,
                startedRuntimeNanos,
                0L,
                triggerSourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                activeTargetPresent
        );
    }

    static ReacquireContext begin(
            ContinuityAssessment trigger,
            long startedRuntimeNanos,
            long triggerSourceSequence,
            long triggerSourceTimestampNanos,
            SourceTimestampDomain triggerSourceTimestampDomain,
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
                startedRuntimeNanos,
                triggerSourceSequence,
                triggerSourceTimestampNanos,
                triggerSourceTimestampDomain,
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
                startedRuntimeNanos,
                triggerSourceSequence,
                triggerSourceTimestampNanos,
                triggerSourceTimestampDomain,
                activeTargetPresent
        );
    }
}
