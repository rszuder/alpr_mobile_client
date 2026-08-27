package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.VehicleEntityRepository;

/** Applies an explicit plate/vehicle association to the domain repository. */
public final class PlateEntityBinder {
    private final VehicleEntityRepository repository;

    public PlateEntityBinder(VehicleEntityRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        this.repository = repository;
    }

    public boolean attachPlate(
            PlateVehicleAssociation association,
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        if (association == null || !association.assigned() || plateTrackId <= 0L) {
            return false;
        }
        repository.attachPlate(
                association.entityId,
                plateTrackId,
                quad,
                appearance,
                nowNanos
        );
        return true;
    }

    public boolean updateRegistration(
            PlateVehicleAssociation association,
            PlateTextConsensus consensus,
            long nowNanos
    ) {
        if (association == null || !association.assigned() || consensus == null) {
            return false;
        }
        repository.updateRegistration(association.entityId, consensus, nowNanos);
        return true;
    }
}
