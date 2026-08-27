package com.example.alpr_v1.pipeline;

/** Immutable result of assigning one MT plate to a vehicle entity. */
public final class PlateVehicleAssociation {
    public final VehicleAssociationStatus status;
    public final long entityId;
    public final long vehicleTrackId;
    public final float confidence;
    public final String reason;

    private PlateVehicleAssociation(
            VehicleAssociationStatus status,
            long entityId,
            long vehicleTrackId,
            float confidence,
            String reason
    ) {
        this.status = status == null ? VehicleAssociationStatus.UNASSIGNED : status;
        this.entityId = Math.max(0L, entityId);
        this.vehicleTrackId = Math.max(0L, vehicleTrackId);
        this.confidence = clamp01(confidence);
        this.reason = reason == null ? "" : reason;
        boolean assigned = this.status == VehicleAssociationStatus.DIRECT_ROI
                || this.status == VehicleAssociationStatus.ASSOCIATED_FULL_FRAME;
        if (assigned && (this.entityId <= 0L || this.vehicleTrackId <= 0L)) {
            throw new IllegalArgumentException("assigned association requires vehicle ids");
        }
    }

    public static PlateVehicleAssociation direct(VehicleRoi roi) {
        if (roi == null) return unassigned("missing_vehicle_roi");
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.DIRECT_ROI,
                roi.entityId,
                roi.vehicleTrackId,
                1f,
                "direct_vehicle_roi"
        );
    }

    public static PlateVehicleAssociation direct(
            long entityId,
            long vehicleTrackId,
            String reason
    ) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.DIRECT_ROI,
                entityId,
                vehicleTrackId,
                1f,
                reason
        );
    }

    public static PlateVehicleAssociation associated(
            long entityId,
            long vehicleTrackId,
            float confidence,
            String reason
    ) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.ASSOCIATED_FULL_FRAME,
                entityId,
                vehicleTrackId,
                confidence,
                reason
        );
    }

    public static PlateVehicleAssociation ambiguous(float confidence, String reason) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.AMBIGUOUS, 0L, 0L, confidence, reason
        );
    }

    public static PlateVehicleAssociation unassigned(String reason) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.UNASSIGNED, 0L, 0L, 0f, reason
        );
    }

    public boolean assigned() {
        return status == VehicleAssociationStatus.DIRECT_ROI
                || status == VehicleAssociationStatus.ASSOCIATED_FULL_FRAME;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
