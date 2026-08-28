package com.example.alpr_v1.continuity;

/** Pure scorer for continuity of the complete vehicle entity pool. */
public final class VehicleContinuityEvaluator {
    private static final float REASSOCIATION_WEIGHT = 0.50f;
    private static final float APPEARANCE_WEIGHT = 0.25f;
    private static final float TRAJECTORY_WEIGHT = 0.25f;
    private static final float PREDICTION_CREDIT = 0.35f;

    public float evaluate(VehicleContinuityEvidence evidence) {
        Contracts.required("evidence", evidence);
        if (evidence.entitiesBefore == 0) return 0f;

        float predictedRatio = Math.min(
                1f - evidence.reassociationRatio,
                evidence.entitiesStillPredicted / (float) evidence.entitiesBefore
        );
        float identityRetention = Math.min(
                1f,
                evidence.reassociationRatio + PREDICTION_CREDIT * predictedRatio
        );

        return clampUnit(
                REASSOCIATION_WEIGHT * identityRetention
                        + APPEARANCE_WEIGHT * evidence.appearanceAgreement
                        + TRAJECTORY_WEIGHT * evidence.trajectoryAgreement
        );
    }

    private static float clampUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
