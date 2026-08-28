package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.RegistrationConsensusSource;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackManager;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.tracking.VehicleTrackingEvent;

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

    @Test
    public void lateAssociationAdoptsStableTrackMemoryWithoutMzAttempt() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame frame = coordinator.updateFromMp(
                1L, 100L, 100L, java.util.Collections.singletonList(
                        observation(0.10f, 0)
                )
        );
        VehicleCandidate candidate = frame.candidates.get(0);
        PlateVehicleAssociation association = PlateVehicleAssociation.direct(
                candidate.entityId, candidate.vehicleTrackId, "late_association"
        );
        PlateEntityBinder binder = new PlateEntityBinder(coordinator.repository());
        binder.attachPlate(
                association, 77L, quad(), null, 200L
        );
        VehicleEntity entity = coordinator.repository().get(candidate.entityId);
        VehicleTrackingFrame publicSnapshot = coordinator.latestFrame();
        assertEquals(
                EntityAcquisitionState.PLATE_LOCALIZED,
                publicSnapshot.candidates.get(0).acquisitionState
        );
        int mzAttemptsBefore = entity.mzAttempts();

        binder.updateRegistration(
                association,
                new PlateTextConsensus("WE911GT", 0.93f, 4, true),
                210L,
                false,
                RegistrationConsensusSource.TRACK_MEMORY
        );

        assertEquals("WE911GT", entity.registration().text);
        assertEquals(mzAttemptsBefore, entity.mzAttempts());
        assertEquals(RegistrationConsensusSource.TRACK_MEMORY, entity.registrationSource());
    }

    @Test
    public void weakerAdoptedConsensusDoesNotReplaceStableRegistration() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame frame = coordinator.updateFromMp(
                1L, 100L, 100L, java.util.Collections.singletonList(
                        observation(0.10f, 0)
                )
        );
        VehicleCandidate candidate = frame.candidates.get(0);
        PlateVehicleAssociation association = PlateVehicleAssociation.direct(
                candidate.entityId, candidate.vehicleTrackId, "same_cycle"
        );
        PlateEntityBinder binder = new PlateEntityBinder(coordinator.repository());
        binder.attachPlate(association, 77L, quad(), null, 200L);
        binder.updateRegistration(
                association,
                new PlateTextConsensus("WE911GT", 0.95f, 5, true),
                210L
        );

        binder.updateRegistration(
                association,
                new PlateTextConsensus("WX111XX", 0.40f, 2, false),
                220L,
                false,
                RegistrationConsensusSource.TRACK_MEMORY
        );

        VehicleEntity entity = coordinator.repository().get(candidate.entityId);
        assertEquals("WE911GT", entity.registration().text);
        assertEquals(1, entity.mzAttempts());
    }

    @Test
    public void controlledPlateReassignmentEmitsLifecycleEvent() {
        VehicleTrackingCoordinator coordinator = new VehicleTrackingCoordinator();
        VehicleTrackingFrame frame = coordinator.updateFromMp(
                1L,
                100L,
                100L,
                Arrays.asList(observation(0.05f, 0), observation(0.55f, 1))
        );
        coordinator.drainEvents();
        VehicleCandidate first = frame.candidates.get(0);
        VehicleCandidate second = frame.candidates.get(1);
        PlateEntityBinder binder = new PlateEntityBinder(coordinator);
        binder.attachPlate(
                PlateVehicleAssociation.direct(
                        first.entityId, first.vehicleTrackId, "initial_owner"
                ),
                77L,
                quad(),
                null,
                200L
        );

        binder.reassignPlateTrack(
                77L,
                first.entityId,
                second.entityId,
                second.vehicleTrackId,
                quad(),
                null,
                300L,
                2L,
                "controlled_scene_recovery"
        );

        boolean found = false;
        for (VehicleTrackingEvent event : coordinator.drainEvents()) {
            if ("plate_track_reassigned".equals(event.eventType)) found = true;
        }
        assertEquals(true, found);
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
