package com.example.alpr_v1.continuity;

/** Immutable state exposed to runtime consumers and, later, the UI. */
public final class SceneContinuitySnapshot {
    public final SceneHandlingMode mode;
    public final SceneContinuityState state;
    public final VisualChangeClassification classification;
    public final long decisionRevision;
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;
    public final long hardResetRevision;
    public final long visualEpochRevision;
    public final boolean finalizationSuspended;
    public final boolean heavyInferenceSuspended;
    public final long lastTransitionNanos;
    public final ContinuityAssessment assessment;

    public SceneContinuitySnapshot(
            SceneHandlingMode mode,
            SceneContinuityState state,
            VisualChangeClassification classification,
            long decisionRevision,
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            boolean finalizationSuspended,
            boolean heavyInferenceSuspended,
            long lastTransitionNanos,
            ContinuityAssessment assessment
    ) {
        this(
                mode, state, classification, decisionRevision,
                sceneGeneration, visualEpoch, cameraTransformGeneration,
                0L, 0L,
                finalizationSuspended, heavyInferenceSuspended,
                lastTransitionNanos, assessment
        );
    }

    public SceneContinuitySnapshot(
            SceneHandlingMode mode,
            SceneContinuityState state,
            VisualChangeClassification classification,
            long decisionRevision,
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            long hardResetRevision,
            long visualEpochRevision,
            boolean finalizationSuspended,
            boolean heavyInferenceSuspended,
            long lastTransitionNanos,
            ContinuityAssessment assessment
    ) {
        this.mode = Contracts.required("mode", mode);
        this.state = Contracts.required("state", state);
        this.classification = Contracts.required("classification", classification);
        this.decisionRevision = Contracts.nonNegative(
                "decisionRevision", decisionRevision
        );
        this.sceneGeneration = Contracts.nonNegative(
                "sceneGeneration", sceneGeneration
        );
        this.visualEpoch = Contracts.nonNegative("visualEpoch", visualEpoch);
        this.cameraTransformGeneration = Contracts.nonNegative(
                "cameraTransformGeneration", cameraTransformGeneration
        );
        this.hardResetRevision = Contracts.nonNegative(
                "hardResetRevision", hardResetRevision
        );
        this.visualEpochRevision = Contracts.nonNegative(
                "visualEpochRevision", visualEpochRevision
        );
        this.finalizationSuspended = finalizationSuspended;
        this.heavyInferenceSuspended = heavyInferenceSuspended;
        this.lastTransitionNanos = Contracts.nonNegative(
                "lastTransitionNanos", lastTransitionNanos
        );
        this.assessment = Contracts.required("assessment", assessment);
    }

    public static SceneContinuitySnapshot initial(SceneHandlingMode mode) {
        return new SceneContinuitySnapshot(
                mode,
                SceneContinuityState.STABLE,
                VisualChangeClassification.NONE,
                0L, 0L, 0L, 0L,
                false, false, 0L,
                ContinuityAssessment.none()
        );
    }
}
