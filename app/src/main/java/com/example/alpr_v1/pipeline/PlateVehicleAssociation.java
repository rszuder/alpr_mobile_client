package com.example.alpr_v1.pipeline;

/** Immutable result of assigning one MT plate to a vehicle entity. */
public final class PlateVehicleAssociation {
    public final VehicleAssociationStatus status;
    public final long entityId;
    public final long vehicleTrackId;
    public final float confidence;
    public final String reason;
    public final boolean geometryValidated;

    private PlateVehicleAssociation(
            VehicleAssociationStatus status,
            long entityId,
            long vehicleTrackId,
            float confidence,
            String reason,
            boolean geometryValidated
    ) {
        this.status = status == null ? VehicleAssociationStatus.UNASSIGNED : status;
        this.entityId = Math.max(0L, entityId);
        this.vehicleTrackId = Math.max(0L, vehicleTrackId);
        this.confidence = clamp01(confidence);
        this.reason = reason == null ? "" : reason;
        this.geometryValidated = geometryValidated;
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
                "direct_vehicle_roi",
                false
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
                reason,
                false
        );
    }

    public static PlateVehicleAssociation directValidated(
            long entityId,
            long vehicleTrackId,
            float confidence,
            String reason
    ) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.DIRECT_ROI,
                entityId,
                vehicleTrackId,
                confidence,
                reason,
                true
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
                reason,
                true
        );
    }

    public static PlateVehicleAssociation ambiguous(float confidence, String reason) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.AMBIGUOUS,
                0L,
                0L,
                confidence,
                reason,
                false
        );
    }

    public static PlateVehicleAssociation unassigned(String reason) {
        return new PlateVehicleAssociation(
                VehicleAssociationStatus.UNASSIGNED,
                0L,
                0L,
                0f,
                reason,
                false
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
