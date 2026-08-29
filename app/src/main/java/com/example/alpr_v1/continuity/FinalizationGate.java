package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.pipeline.PlateVehicleAssociation;

/** Pure gate; recovery never regresses the entity's monotonic acquisition state. */
public final class FinalizationGate {
    public FinalizationDecision evaluate(
            VehicleEntity entity,
            SceneContinuitySnapshot continuity,
            PlateVehicleAssociation association,
            long observationVisualEpoch
    ) {
        if (entity == null) return FinalizationDecision.deny("missing_entity");
        if (continuity == null) return FinalizationDecision.deny("missing_continuity");
        if (association == null) return FinalizationDecision.deny("missing_association");
        if (entity.acquisitionState() != EntityAcquisitionState.READY_TO_FINALIZE) {
            return FinalizationDecision.deny("entity_not_ready_to_finalize");
        }
        if (continuity.finalizationSuspended) {
            return FinalizationDecision.deny("finalization_suspended");
        }
        if (continuity.state != SceneContinuityState.STABLE) {
            return FinalizationDecision.deny("continuity_not_stable");
        }
        if (!association.geometryValidated) {
            return FinalizationDecision.deny("association_geometry_not_validated");
        }
        if (observationVisualEpoch != continuity.visualEpoch) {
            return FinalizationDecision.deny("stale_visual_epoch");
        }
        return FinalizationDecision.allow();
    }
}
