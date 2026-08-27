package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackManager;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class EntityAwareMtBindingTest {
    @Test
    public void vehicleRoiBindsPlateAndRegistrationOnlyToSelectedEntity() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame frame = coordinator.updateFromMp(
                1L,
                100L,
                100L,
                Arrays.asList(
                        observation(0.05f, 0),
                        observation(0.55f, 1)
                )
        );
        VehicleCandidate candidateA = frame.candidates.get(0);
        VehicleCandidate candidateB = frame.candidates.get(1);
        List<VehicleRoi> rois = VehicleRoiSelector.selectTrackedCandidates(
                frame.candidates, 1000, 500, 2, 0f
        );
        VehicleRoi roiB = null;
        for (VehicleRoi roi : rois) {
            if (roi.entityId == candidateB.entityId) roiB = roi;
        }
        if (roiB == null) throw new AssertionError("ROI B missing");

        PlateVehicleAssociation association = PlateVehicleAssociation.direct(roiB);
        PlateEntityBinder binder = new PlateEntityBinder(coordinator.repository());
        binder.attachPlate(
                association, 77L, quad(), new AppearanceDescriptor(new float[]{0f, 1f}), 200L
        );
        binder.updateRegistration(
                association, new PlateTextConsensus("WE911GT", 0.93f, 4, true), 210L
        );

        VehicleEntity entityA = coordinator.repository().get(candidateA.entityId);
        VehicleEntity entityB = coordinator.repository().get(candidateB.entityId);
        assertNull(entityA.plateTrackId());
        assertEquals("", entityA.registration().text);
        assertEquals(Long.valueOf(77L), entityB.plateTrackId());
        assertEquals("WE911GT", entityB.registration().text);
    }

    private static VehicleTrackManager.Observation observation(float left, int sourceIndex) {
        return new VehicleTrackManager.Observation(
                new NormalizedBounds(left, 0.1f, left + 0.35f, 0.9f),
                0.9f,
                new AppearanceDescriptor(new float[]{sourceIndex == 0 ? 1f : 0f,
                        sourceIndex == 1 ? 1f : 0f}),
                sourceIndex
        );
    }

    private static NormalizedQuad quad() {
        return new NormalizedQuad(
                new float[]{0.6f, 0.6f, 0.75f, 0.6f, 0.75f, 0.7f, 0.6f, 0.7f}
        );
    }
}
