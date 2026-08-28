package com.example.alpr_v1.continuity;

/** One immutable observation submitted to the future central scene coordinator. */
public final class SceneEvidence {
    public final long sourceFrameId;
    public final long sourceTimestampNanos;
    public final boolean rawVisualChange;
    public final float rawVisualChangeScore;
    public final float changedFraction;
    public final float brightnessDelta;
    public final float anchorDriftScore;
    public final float anchorChangedFraction;
    public final TargetContinuityEvidence target;
    public final VehicleContinuityEvidence vehicles;
    public final MotionExplanationEvidence motion;
    public final boolean sourceDimensionsChanged;
    public final boolean cameraRestarted;
    public final boolean lensChanged;
    public final boolean orientationChanged;

    public SceneEvidence(
            long sourceFrameId,
            long sourceTimestampNanos,
            boolean rawVisualChange,
            float rawVisualChangeScore,
            float changedFraction,
            float brightnessDelta,
            float anchorDriftScore,
            float anchorChangedFraction,
            TargetContinuityEvidence target,
            VehicleContinuityEvidence vehicles,
            MotionExplanationEvidence motion,
            boolean sourceDimensionsChanged,
            boolean cameraRestarted,
            boolean lensChanged,
            boolean orientationChanged
    ) {
        this.sourceFrameId = Contracts.nonNegative("sourceFrameId", sourceFrameId);
        this.sourceTimestampNanos = Contracts.nonNegative(
                "sourceTimestampNanos", sourceTimestampNanos
        );
        this.rawVisualChange = rawVisualChange;
        this.rawVisualChangeScore = Contracts.unit(
                "rawVisualChangeScore", rawVisualChangeScore
        );
        this.changedFraction = Contracts.unit("changedFraction", changedFraction);
        this.brightnessDelta = Contracts.unit("brightnessDelta", brightnessDelta);
        this.anchorDriftScore = Contracts.unit("anchorDriftScore", anchorDriftScore);
        this.anchorChangedFraction = Contracts.unit(
                "anchorChangedFraction", anchorChangedFraction
        );
        this.target = Contracts.required("target", target);
        this.vehicles = Contracts.required("vehicles", vehicles);
        this.motion = Contracts.required("motion", motion);
        this.sourceDimensionsChanged = sourceDimensionsChanged;
        this.cameraRestarted = cameraRestarted;
        this.lensChanged = lensChanged;
        this.orientationChanged = orientationChanged;
    }

    public boolean structuralChange() {
        return sourceDimensionsChanged || cameraRestarted
                || lensChanged || orientationChanged;
    }
}
