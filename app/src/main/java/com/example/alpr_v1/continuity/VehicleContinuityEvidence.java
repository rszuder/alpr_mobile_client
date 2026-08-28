package com.example.alpr_v1.continuity;

/** Immutable evidence describing continuity of the complete vehicle entity pool. */
public final class VehicleContinuityEvidence {
    public final int entitiesBefore;
    public final int entitiesAfter;
    public final int entitiesReassociated;
    public final int entitiesStillPredicted;
    public final int newlyCreatedEntities;
    public final float reassociationRatio;
    public final float appearanceAgreement;
    public final float trajectoryAgreement;
    public final long newestMeasurementAgeNanos;

    public VehicleContinuityEvidence(
            int entitiesBefore,
            int entitiesAfter,
            int entitiesReassociated,
            int entitiesStillPredicted,
            int newlyCreatedEntities,
            float reassociationRatio,
            float appearanceAgreement,
            float trajectoryAgreement,
            long newestMeasurementAgeNanos
    ) {
        this.entitiesBefore = Contracts.nonNegative("entitiesBefore", entitiesBefore);
        this.entitiesAfter = Contracts.nonNegative("entitiesAfter", entitiesAfter);
        this.entitiesReassociated = Contracts.nonNegative(
                "entitiesReassociated", entitiesReassociated
        );
        this.entitiesStillPredicted = Contracts.nonNegative(
                "entitiesStillPredicted", entitiesStillPredicted
        );
        this.newlyCreatedEntities = Contracts.nonNegative(
                "newlyCreatedEntities", newlyCreatedEntities
        );
        if (entitiesReassociated > entitiesBefore) {
            throw new IllegalArgumentException(
                    "entitiesReassociated cannot exceed entitiesBefore"
            );
        }
        this.reassociationRatio = Contracts.unit("reassociationRatio", reassociationRatio);
        this.appearanceAgreement = Contracts.unit(
                "appearanceAgreement", appearanceAgreement
        );
        this.trajectoryAgreement = Contracts.unit(
                "trajectoryAgreement", trajectoryAgreement
        );
        this.newestMeasurementAgeNanos = Contracts.nonNegative(
                "newestMeasurementAgeNanos", newestMeasurementAgeNanos
        );
    }

    public static VehicleContinuityEvidence empty() {
        return new VehicleContinuityEvidence(0, 0, 0, 0, 0, 0f, 0f, 0f, 0L);
    }
}
