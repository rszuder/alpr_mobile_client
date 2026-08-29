package com.example.alpr_v1.continuity;

/** Explicit starting thresholds; values require later device benchmarking. */
public final class SceneContinuityProfile {
    public static final SceneContinuityProfile INITIAL = new SceneContinuityProfile(
            180_000_000L,
            1_000_000_000L,
            450_000_000L,
            1_200_000_000L,
            0.60f,
            0.50f,
            0.50f,
            0.70f,
            3,
            0.30f,
            350_000_000L
    );

    public final long motionSettleNanos;
    public final long reacquireTimeoutNanos;
    public final long strongCutPersistenceNanos;
    public final long maximumSoftHoldNanos;
    public final float minimumTargetContinuityToPreserve;
    public final float minimumVehicleContinuityToPreserve;
    public final float minimumMotionExplanation;
    public final float continuityBreakThreshold;
    public final int minimumTrackerInliers;
    public final float localAppearanceContradictionThreshold;
    public final long maximumFocusedEvidenceAgeNanos;

    public SceneContinuityProfile(
            long motionSettleNanos,
            long reacquireTimeoutNanos,
            long strongCutPersistenceNanos,
            long maximumSoftHoldNanos,
            float minimumTargetContinuityToPreserve,
            float minimumVehicleContinuityToPreserve,
            float minimumMotionExplanation,
            float continuityBreakThreshold,
            int minimumTrackerInliers
    ) {
        this(
                motionSettleNanos,
                reacquireTimeoutNanos,
                strongCutPersistenceNanos,
                maximumSoftHoldNanos,
                minimumTargetContinuityToPreserve,
                minimumVehicleContinuityToPreserve,
                minimumMotionExplanation,
                continuityBreakThreshold,
                minimumTrackerInliers,
                0.30f,
                350_000_000L
        );
    }

    public SceneContinuityProfile(
            long motionSettleNanos,
            long reacquireTimeoutNanos,
            long strongCutPersistenceNanos,
            long maximumSoftHoldNanos,
            float minimumTargetContinuityToPreserve,
            float minimumVehicleContinuityToPreserve,
            float minimumMotionExplanation,
            float continuityBreakThreshold,
            int minimumTrackerInliers,
            float localAppearanceContradictionThreshold,
            long maximumFocusedEvidenceAgeNanos
    ) {
        this.motionSettleNanos = Contracts.positive(
                "motionSettleNanos", motionSettleNanos
        );
        this.reacquireTimeoutNanos = Contracts.positive(
                "reacquireTimeoutNanos", reacquireTimeoutNanos
        );
        this.strongCutPersistenceNanos = Contracts.positive(
                "strongCutPersistenceNanos", strongCutPersistenceNanos
        );
        this.maximumSoftHoldNanos = Contracts.positive(
                "maximumSoftHoldNanos", maximumSoftHoldNanos
        );
        if (maximumSoftHoldNanos < motionSettleNanos) {
            throw new IllegalArgumentException(
                    "maximumSoftHoldNanos must cover motionSettleNanos"
            );
        }
        this.minimumTargetContinuityToPreserve = Contracts.unit(
                "minimumTargetContinuityToPreserve",
                minimumTargetContinuityToPreserve
        );
        this.minimumVehicleContinuityToPreserve = Contracts.unit(
                "minimumVehicleContinuityToPreserve",
                minimumVehicleContinuityToPreserve
        );
        this.minimumMotionExplanation = Contracts.unit(
                "minimumMotionExplanation", minimumMotionExplanation
        );
        this.continuityBreakThreshold = Contracts.unit(
                "continuityBreakThreshold", continuityBreakThreshold
        );
        this.minimumTrackerInliers = Contracts.nonNegative(
                "minimumTrackerInliers", minimumTrackerInliers
        );
        this.localAppearanceContradictionThreshold = Contracts.unit(
                "localAppearanceContradictionThreshold",
                localAppearanceContradictionThreshold
        );
        this.maximumFocusedEvidenceAgeNanos = Contracts.positive(
                "maximumFocusedEvidenceAgeNanos",
                maximumFocusedEvidenceAgeNanos
        );
    }
}
