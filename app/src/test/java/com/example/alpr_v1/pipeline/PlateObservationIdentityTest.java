package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;

import org.junit.Test;

import java.util.Collections;

public class PlateObservationIdentityTest {
    @Test
    public void identityPropagatesFromCandidateThroughRoiToPlateObservation() {
        VehicleCandidate candidate = new VehicleCandidate(
                21L, 31L,
                new NormalizedBounds(0.1f, 0.2f, 0.7f, 0.9f),
                0.9f, 0.9f, 0f, false, 0, 100L, 100L
        );
        VehicleRoi roi = new VehicleRoi(candidate, 10, 20, 70, 90);
        PlateObservation observation = new PlateObservation(
                41L,
                PlateVehicleAssociation.direct(roi),
                MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,
                5L,
                null,
                "WE911GT",
                0.9,
                0.8,
                true,
                4,
                Collections.emptyList(),
                0L,
                200L,
                0.7f,
                null,
                null,
                PlateGeometry.unavailable(),
                true,
                true,
                "WE911GT",
                true,
                1,
                TemporalCharacterAggregator.LAYOUT_SINGLE_ROW,
                Collections.singletonList(7),
                "WE911G",
                "WE911GT"
        );

        assertEquals(21L, observation.entityId);
        assertEquals(31L, observation.vehicleTrackId);
        assertEquals(41L, observation.plateTrackId);
        assertEquals(VehicleAssociationStatus.DIRECT_ROI, observation.associationStatus);
        assertEquals(MtWorkKind.VEHICLE_ROI, observation.sourceRoiKind);
    }
}
