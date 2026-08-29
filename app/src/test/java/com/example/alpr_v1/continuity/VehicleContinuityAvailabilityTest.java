package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VehicleContinuityAvailabilityTest {
    @Test
    public void unavailableAgreementWeightsAreNormalizedOut() {
        VehicleContinuityEvidence evidence = new VehicleContinuityEvidence(
                2, 1, 1, 0, 0,
                0.5f, 0f, 0f, 10L,
                false, false
        );

        assertEquals(0.5f, new VehicleContinuityEvaluator().evaluate(evidence), 0.0001f);
    }

    @Test
    public void predictionAloneCannotReachPoolRecoveryThreshold() {
        VehicleContinuityEvidence evidence = new VehicleContinuityEvidence(
                2, 2, 0, 2, 0,
                0f, 0f, 0f, 10L,
                false, false
        );

        assertEquals(0.35f, new VehicleContinuityEvaluator().evaluate(evidence), 0.0001f);
    }
}
