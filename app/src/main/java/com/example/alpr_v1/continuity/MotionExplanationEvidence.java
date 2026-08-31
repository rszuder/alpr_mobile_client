package com.example.alpr_v1.continuity;

/** Evidence that a large pixel delta is explained by physical/global motion. */
public final class MotionExplanationEvidence {
    public final boolean gyroAvailable;
    public final boolean cameraMoving;
    public final boolean rapidCameraMotion;
    public final float angularMotionMagnitude;
    public final boolean cameraTransformInProgress;
    public final boolean dominantMotionEstimated;
    public final float globalMotionCoherence;
    public final float compensatedFrameResidual;
    public final float targetContinuityScore;
    public final float vehicleContinuityScore;
    public final boolean motionSettling;

    public MotionExplanationEvidence(
            boolean gyroAvailable,
            boolean cameraMoving,
            boolean rapidCameraMotion,
            float angularMotionMagnitude,
            boolean cameraTransformInProgress,
            boolean dominantMotionEstimated,
            float globalMotionCoherence,
            float compensatedFrameResidual,
            float targetContinuityScore,
            float vehicleContinuityScore
    ) {
        this(
                gyroAvailable,
                cameraMoving,
                rapidCameraMotion,
                angularMotionMagnitude,
                cameraTransformInProgress,
                dominantMotionEstimated,
                globalMotionCoherence,
                compensatedFrameResidual,
                targetContinuityScore,
                vehicleContinuityScore,
                false
        );
    }

    public MotionExplanationEvidence(
            boolean gyroAvailable,
            boolean cameraMoving,
            boolean rapidCameraMotion,
            float angularMotionMagnitude,
            boolean cameraTransformInProgress,
            boolean dominantMotionEstimated,
            float globalMotionCoherence,
            float compensatedFrameResidual,
            float targetContinuityScore,
            float vehicleContinuityScore,
            boolean motionSettling
    ) {
        this.gyroAvailable = gyroAvailable;
        this.cameraMoving = cameraMoving;
        this.rapidCameraMotion = rapidCameraMotion;
        this.angularMotionMagnitude = Contracts.nonNegativeFinite(
                "angularMotionMagnitude", angularMotionMagnitude
        );
        this.cameraTransformInProgress = cameraTransformInProgress;
        this.dominantMotionEstimated = dominantMotionEstimated;
        this.globalMotionCoherence = Contracts.unit(
                "globalMotionCoherence", globalMotionCoherence
        );
        this.compensatedFrameResidual = Contracts.unit(
                "compensatedFrameResidual", compensatedFrameResidual
        );
        this.targetContinuityScore = Contracts.unit(
                "targetContinuityScore", targetContinuityScore
        );
        this.vehicleContinuityScore = Contracts.unit(
                "vehicleContinuityScore", vehicleContinuityScore
        );
        this.motionSettling = motionSettling;
    }

    public static MotionExplanationEvidence none() {
        return new MotionExplanationEvidence(
                false, false, false, 0f, false, false,
                0f, 0f, 0f, 0f
        );
    }
}
