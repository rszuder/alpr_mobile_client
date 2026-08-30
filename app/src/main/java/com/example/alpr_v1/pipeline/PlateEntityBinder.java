package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.PlateTrackAttachmentStatus;
import com.example.alpr_v1.domain.RegistrationConsensusSource;
import com.example.alpr_v1.domain.VehicleEntityRepository;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;

/** Applies an explicit plate/vehicle association to the domain repository. */
public final class PlateEntityBinder {
    private final VehicleEntityRepository repository;
    private final VehicleTrackingCoordinator coordinator;

    public PlateEntityBinder(VehicleEntityRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        this.repository = repository;
        this.coordinator = null;
    }

    public PlateEntityBinder(VehicleTrackingCoordinator coordinator) {
        if (coordinator == null) throw new IllegalArgumentException("coordinator is required");
        this.repository = coordinator.repository();
        this.coordinator = coordinator;
    }

    public PlateTrackAttachmentStatus attachPlate(
            PlateVehicleAssociation association,
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        if (association == null || !association.assigned() || plateTrackId <= 0L) {
            return PlateTrackAttachmentStatus.CONFLICT_REJECTED;
        }
        return repository.attachPlate(
                association.entityId,
                plateTrackId,
                quad,
                appearance,
                nowNanos
        );
    }

    public PlateTrackAttachmentStatus attachPlate(
            PlateVehicleAssociation association,
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long sourceSequence,
            long sourceTimestampNanos
    ) {
        if (association == null || !association.assigned() || plateTrackId <= 0L) {
            return PlateTrackAttachmentStatus.CONFLICT_REJECTED;
        }
        return repository.attachPlate(
                association.entityId,
                plateTrackId,
                quad,
                appearance,
                sourceSequence,
                sourceTimestampNanos
        );
    }

    public boolean updateRegistration(
            PlateVehicleAssociation association,
            PlateTextConsensus consensus,
            long nowNanos
    ) {
        if (association == null || !association.assigned() || consensus == null) {
            return false;
        }
        return repository.updateRegistration(
                association.entityId, consensus, nowNanos
        );
    }

    public boolean updateRegistration(
            PlateVehicleAssociation association,
            PlateTextConsensus consensus,
            long nowNanos,
            boolean freshMzAttempted,
            RegistrationConsensusSource source
    ) {
        if (association == null || !association.assigned() || consensus == null) {
            return false;
        }
        return repository.updateRegistration(
                association.entityId,
                consensus,
                nowNanos,
                freshMzAttempted,
                source
        );
    }

    public PlateTrackAttachmentStatus reassignPlateTrack(
            long plateTrackId,
            long fromEntityId,
            long toEntityId,
            long toVehicleTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos,
            long frameId,
            String reason
    ) {
        PlateTrackAttachmentStatus status = repository.reassignPlateTrack(
                plateTrackId,
                fromEntityId,
                toEntityId,
                quad,
                appearance,
                nowNanos
        );
        if (coordinator != null) {
            coordinator.recordEvent(
                    status == PlateTrackAttachmentStatus.REASSIGNED
                            ? "plate_track_reassigned"
                            : status == PlateTrackAttachmentStatus.CONFLICT_REJECTED
                                    ? "plate_track_conflict_rejected"
                                    : "plate_track_attached",
                    toEntityId,
                    toVehicleTrackId,
                    plateTrackId,
                    frameId,
                    nowNanos,
                    reason
            );
        }
        return status;
    }
}
