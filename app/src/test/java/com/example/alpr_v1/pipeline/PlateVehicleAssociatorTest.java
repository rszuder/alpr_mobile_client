package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void validatesDirectRoiAgainstOriginalVehicleBounds() {
        VehicleCandidate owner = vehicle(1L, 11L, 0.05f, 0.1f, 0.45f, 0.9f);
        VehicleCandidate other = vehicle(2L, 12L, 0.55f, 0.1f, 0.95f, 0.9f);
        VehicleRoi expandedOwnerRoi = new VehicleRoi(owner, 0, 0, 120, 100);

        PlateVehicleAssociation result = associator.validateDirectRoi(
                plate(65, 60, 85, 72),
                expandedOwnerRoi,
                200,
                100,
                Arrays.asList(owner, other)
        );

        assertEquals(VehicleAssociationStatus.DIRECT_ROI, result.status);
        assertEquals(owner.entityId, result.entityId);
        assertTrue(result.geometryValidated);
    }

    @Test
    public void refusesNeighborsPlateInsideExpandedButOutsideOriginalRoi() {
        VehicleCandidate owner = vehicle(1L, 11L, 0.05f, 0.1f, 0.45f, 0.9f);
        VehicleCandidate neighbor = vehicle(2L, 12L, 0.50f, 0.1f, 0.95f, 0.9f);
        VehicleRoi expandedOwnerRoi = new VehicleRoi(owner, 0, 0, 160, 100);

        PlateVehicleAssociation result = associator.validateDirectRoi(
                plate(120, 60, 140, 72),
                expandedOwnerRoi,
                200,
                100,
                Arrays.asList(owner, neighbor)
        );

        assertEquals(VehicleAssociationStatus.UNASSIGNED, result.status);
        assertEquals("direct_roi_plate_outside_original_vehicle", result.reason);
    }

    @Test
    public void refusesDirectRoiWhenNeighborHasSimilarGeometryScore() {
        VehicleCandidate owner = vehicle(1L, 11L, 0.20f, 0.1f, 0.65f, 0.9f);
        VehicleCandidate neighbor = vehicle(2L, 12L, 0.45f, 0.1f, 0.85f, 0.9f);
        VehicleRoi expandedOwnerRoi = new VehicleRoi(owner, 20, 0, 150, 100);

        PlateVehicleAssociation result = associator.validateDirectRoi(
                plate(100, 60, 120, 72),
                expandedOwnerRoi,
                200,
                100,
                Arrays.asList(owner, neighbor)
        );

        assertEquals(VehicleAssociationStatus.AMBIGUOUS, result.status);
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
