package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;
import com.example.alpr_v1.pipeline.PlateVehicleAssociation;

import org.junit.Test;

public final class FinalizationGateTest {
    private final FinalizationGate gate = new FinalizationGate();

    @Test
    public void stableValidatedCurrentEpochAllowsReadyEntity() {
        assertTrue(gate.evaluate(
                readyEntity(),
                snapshot(SceneContinuityState.STABLE, false, 5L),
                PlateVehicleAssociation.directValidated(1L, 11L, 0.9f, "test"),
                5L
        ).allowed);
    }

    @Test
    public void softHoldDeniesWithoutRegressingReadyEntity() {
        VehicleEntity entity = readyEntity();

        FinalizationDecision decision = gate.evaluate(
                entity,
                snapshot(SceneContinuityState.MOTION_HOLD, true, 5L),
                PlateVehicleAssociation.directValidated(1L, 11L, 0.9f, "test"),
                5L
        );

        assertFalse(decision.allowed);
        assertEquals("finalization_suspended", decision.reason);
        assertEquals(
                com.example.alpr_v1.domain.EntityAcquisitionState.READY_TO_FINALIZE,
                entity.acquisitionState()
        );
    }

    @Test
    public void oldVisualEpochDeniesGeometryAndFinalization() {
        FinalizationDecision decision = gate.evaluate(
                readyEntity(),
                snapshot(SceneContinuityState.STABLE, false, 6L),
                PlateVehicleAssociation.directValidated(1L, 11L, 0.9f, "test"),
                5L
        );

        assertFalse(decision.allowed);
        assertEquals("stale_visual_epoch", decision.reason);
    }

    @Test
    public void ambiguousAssociationCannotFinalize() {
        FinalizationDecision decision = gate.evaluate(
                readyEntity(),
                snapshot(SceneContinuityState.STABLE, false, 5L),
                PlateVehicleAssociation.ambiguous(0.5f, "two_candidates"),
                5L
        );

        assertFalse(decision.allowed);
        assertEquals("association_geometry_not_validated", decision.reason);
    }

    private static VehicleEntity readyEntity() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleEntity entity = repository.create(
                11L,
                new NormalizedBounds(0.1f, 0.1f, 0.8f, 0.8f),
                new AppearanceDescriptor(new float[]{1f, 0f}),
                100L
        );
        repository.updateRegistration(
                entity.entityId(),
                new PlateTextConsensus("WE12345", 0.9f, 3, true),
                200L
        );
        return entity;
    }

    private static SceneContinuitySnapshot snapshot(
            SceneContinuityState state,
            boolean suspended,
            long visualEpoch
    ) {
        return new SceneContinuitySnapshot(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                state,
                VisualChangeClassification.NONE,
                1L,
                0L,
                visualEpoch,
                0L,
                0L,
                1L,
                suspended,
                state == SceneContinuityState.MOTION_HOLD,
                100L,
                ContinuityAssessment.none()
        );
    }
}
