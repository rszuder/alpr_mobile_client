package com.example.alpr_v1.continuity;

/** One immutable observation submitted to the future central scene coordinator. */
public final class SceneEvidence {
    public final LocalVehiclePoolEvidence localVehicles;
    public final long sourceFrameId;
    public final long sourceSequence;
    public final long sourceTimestampNanos;
    public final SourceTimestampDomain sourceTimestampDomain;
    public final boolean rawVisualChange;
    public final float rawVisualChangeScore;
    public final float changedFraction;
    public final float brightnessDelta;
    public final float anchorDriftScore;
    public final float anchorChangedFraction;
    public final TargetContinuityEvidence target;
    public final VehicleContinuityEvidence vehicles;
    public final MotionExplanationEvidence motion;
    public final boolean focusedTrackingLost;
    public final boolean focusedTrackingDegraded;
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
        this(
                sourceFrameId, 0L, sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN, rawVisualChange,
                rawVisualChangeScore, changedFraction, brightnessDelta,
                anchorDriftScore, anchorChangedFraction, target, vehicles, motion,
                false, false,
                sourceDimensionsChanged, cameraRestarted, lensChanged, orientationChanged
        );
    }

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
            boolean focusedTrackingLost,
            boolean focusedTrackingDegraded,
            boolean sourceDimensionsChanged,
            boolean cameraRestarted,
            boolean lensChanged,
            boolean orientationChanged
    ) {
        this(
                sourceFrameId, 0L, sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN, rawVisualChange,
                rawVisualChangeScore, changedFraction, brightnessDelta,
                anchorDriftScore, anchorChangedFraction, target, vehicles, motion,
                focusedTrackingLost, focusedTrackingDegraded,
                sourceDimensionsChanged, cameraRestarted, lensChanged,
                orientationChanged
        );
    }

    public SceneEvidence(
            long sourceFrameId,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain,
            boolean rawVisualChange,
            float rawVisualChangeScore,
            float changedFraction,
            float brightnessDelta,
            float anchorDriftScore,
            float anchorChangedFraction,
            TargetContinuityEvidence target,
            VehicleContinuityEvidence vehicles,
            MotionExplanationEvidence motion,
            boolean focusedTrackingLost,
            boolean focusedTrackingDegraded,
            boolean sourceDimensionsChanged,
            boolean cameraRestarted,
            boolean lensChanged,
            boolean orientationChanged
    ) {
        this.sourceFrameId = Contracts.nonNegative("sourceFrameId", sourceFrameId);
        this.sourceSequence = Contracts.nonNegative(
                "sourceSequence", sourceSequence
        );
        this.sourceTimestampNanos = Contracts.nonNegative(
                "sourceTimestampNanos", sourceTimestampNanos
        );
        this.sourceTimestampDomain = sourceTimestampDomain == null
                ? SourceTimestampDomain.UNKNOWN : sourceTimestampDomain;
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
        this.focusedTrackingLost = focusedTrackingLost;
        this.focusedTrackingDegraded = focusedTrackingDegraded;
        this.sourceDimensionsChanged = sourceDimensionsChanged;
        this.cameraRestarted = cameraRestarted;
        this.lensChanged = lensChanged;
        this.orientationChanged = orientationChanged;
        this.localVehicles = LocalVehiclePoolEvidence.unavailable();
    }

    private SceneEvidence(SceneEvidence original,LocalVehiclePoolEvidence local) {
        sourceFrameId=original.sourceFrameId;sourceSequence=original.sourceSequence;
        sourceTimestampNanos=original.sourceTimestampNanos;sourceTimestampDomain=original.sourceTimestampDomain;
        rawVisualChange=original.rawVisualChange;rawVisualChangeScore=original.rawVisualChangeScore;
        changedFraction=original.changedFraction;brightnessDelta=original.brightnessDelta;
        anchorDriftScore=original.anchorDriftScore;anchorChangedFraction=original.anchorChangedFraction;
        target=original.target;vehicles=original.vehicles;motion=original.motion;
        focusedTrackingLost=original.focusedTrackingLost;focusedTrackingDegraded=original.focusedTrackingDegraded;
        sourceDimensionsChanged=original.sourceDimensionsChanged;cameraRestarted=original.cameraRestarted;
        lensChanged=original.lensChanged;orientationChanged=original.orientationChanged;
        localVehicles=local!=null&&local.matches(sourceSequence,sourceTimestampNanos,sourceTimestampDomain)
                ?local:LocalVehiclePoolEvidence.unavailable();
    }

    public SceneEvidence withLocalVehicles(LocalVehiclePoolEvidence local){return new SceneEvidence(this,local);}

    public boolean structuralChange() {
        return sourceDimensionsChanged || cameraRestarted
                || lensChanged || orientationChanged;
    }
}
