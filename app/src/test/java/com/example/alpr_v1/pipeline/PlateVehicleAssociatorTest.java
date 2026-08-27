package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.vision.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PlateVehicleAssociatorTest {
    private final PlateVehicleAssociator associator = new PlateVehicleAssociator();

    @Test
    public void assignsPlateToUniqueContainingVehicle() {
        PlateVehicleAssociation result = associator.associate(
                plate(65, 60, 85, 72),
                200,
                100,
                Arrays.asList(
                        vehicle(1L, 11L, 0.05f, 0.1f, 0.45f, 0.9f),
                        vehicle(2L, 12L, 0.55f, 0.1f, 0.95f, 0.9f)
                )
        );

        assertEquals(VehicleAssociationStatus.ASSOCIATED_FULL_FRAME, result.status);
        assertEquals(1L, result.entityId);
        assertEquals(11L, result.vehicleTrackId);
    }

    @Test
    public void refusesAmbiguousOverlap() {
        PlateVehicleAssociation result = associator.associate(
                plate(90, 60, 110, 72),
                200,
                100,
                Arrays.asList(
                        vehicle(1L, 11L, 0.20f, 0.1f, 0.60f, 0.9f),
                        vehicle(2L, 12L, 0.40f, 0.1f, 0.80f, 0.9f)
                )
        );

        assertEquals(VehicleAssociationStatus.AMBIGUOUS, result.status);
        assertEquals(0L, result.entityId);
    }

    @Test
    public void leavesPlateUnassignedWithoutVehicles() {
        PlateVehicleAssociation result = associator.associate(
                plate(10, 10, 20, 20), 200, 100, Collections.emptyList()
        );

        assertEquals(VehicleAssociationStatus.UNASSIGNED, result.status);
    }

    private static VehicleCandidate vehicle(
            long entityId,
            long trackId,
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(left, top, right, bottom),
                0.9f, 0.9f, 0f, false, 0, 100L, 100L
        );
    }

    private static Detection plate(float left, float top, float right, float bottom) {
        return new Detection(
                0, 0.9f, left, top, right, bottom, Collections.emptyList()
        );
    }
}
