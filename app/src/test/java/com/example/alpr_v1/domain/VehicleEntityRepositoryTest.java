package com.example.alpr_v1.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VehicleEntityRepositoryTest {
    private static final NormalizedBounds VEHICLE =
            new NormalizedBounds(0.1f, 0.2f, 0.7f, 0.8f);

    @Test
    public void repeatedMpUpdateUsesOneDurableEntity() {
        VehicleEntityRepository repository = new VehicleEntityRepository();

        VehicleEntity first = repository.updateFromMp(
                41L, VEHICLE, MotionState.STATIONARY, descriptor(1f), 100L
        );
        VehicleEntity second = repository.updateFromMp(
                41L,
                new NormalizedBounds(0.2f, 0.2f, 0.8f, 0.8f),
                new MotionState(0.1f, 0f, 0.8f),
                descriptor(0.5f),
                200L
        );

        assertSame(first, second);
        assertEquals(1, repository.size());
        assertEquals(200L, first.lastMpNanos());
        assertEquals(0.1f, first.motion().velocityX, 0.0001f);
    }

    @Test
    public void technicalTrackReassignmentPreservesRecognitionAndEntityId() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleEntity entity = repository.updateFromMp(
                11L, VEHICLE, MotionState.STATIONARY, descriptor(1f), 100L
        );
        repository.attachPlate(1L, 71L, plateQuad(), descriptor(0.8f), 150L);
        repository.updateRegistration(
                1L,
                new PlateTextConsensus("we 911gt", 0.92f, 4, true),
                180L
        );

        VehicleEntity rebound = repository.reassignVehicleTrack(
                1L, 12L, VEHICLE, MotionState.STATIONARY, descriptor(0.9f), 200L
        );

        assertSame(entity, rebound);
        assertEquals(1L, rebound.entityId());
        assertEquals(12L, rebound.vehicleTrackId());
        assertEquals("WE911GT", rebound.registration().text);
        assertNull(repository.findByVehicleTrackId(11L));
        assertSame(rebound, repository.findByVehicleTrackId(12L));
    }

    @Test(expected = IllegalStateException.class)
    public void technicalTrackCannotBelongToTwoEntities() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        repository.updateFromMp(1L, VEHICLE, MotionState.STATIONARY, null, 10L);
        repository.updateFromMp(2L, VEHICLE, MotionState.STATIONARY, null, 10L);

        repository.reassignVehicleTrack(
                1L, 2L, VEHICLE, MotionState.STATIONARY, null, 20L
        );
    }

    @Test(expected = IllegalStateException.class)
    public void createRejectsDuplicateTechnicalTrack() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        repository.create(5L, VEHICLE, null, 10L);

        repository.create(5L, VEHICLE, null, 20L);
    }

    @Test
    public void retainsBestCropAndExpiresOnlyInactiveUnfinishedEntity() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleEntity expiring = repository.updateFromMp(
                1L, VEHICLE, MotionState.STATIONARY, null, 10L
        );
        VehicleEntity active = repository.updateFromMp(
                2L, VEHICLE, MotionState.STATIONARY, null, 10L
        );
        VehicleEntity acquired = repository.updateFromMp(
                3L, VEHICLE, MotionState.STATIONARY, null, 10L
        );
        repository.considerCrop(
                expiring.entityId(),
                new CropReference("weak", CropReference.Kind.WIDE_PLATE, 0.4f, 20L)
        );
        repository.considerCrop(
                expiring.entityId(),
                new CropReference("best", CropReference.Kind.WIDE_PLATE, 0.9f, 30L)
        );
        repository.considerCrop(
                expiring.entityId(),
                new CropReference("late-weak", CropReference.Kind.WIDE_PLATE, 0.5f, 40L)
        );
        repository.markActiveTarget(active.entityId(), true);
        repository.markAcquired(acquired.entityId());

        assertEquals(1, repository.expireOldEntities(1_000L, 100L));
        assertEquals("best", expiring.bestWidePlateCrop().referenceId);
        assertEquals(EntityAcquisitionState.EXPIRED, expiring.acquisitionState());
        assertFalse(active.acquisitionCompleted());
        assertTrue(active.activeTarget());
        assertTrue(acquired.acquisitionCompleted());
        assertEquals(1, repository.activeEntities().size());
        assertEquals(1, repository.completedEntities().size());
        assertEquals(acquired.entityId(), repository.completedEntities().get(0).entityId);
        assertEquals(1, repository.size());
    }

    @Test
    public void entityIdsStayMonotonicAcrossSceneReset() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        long firstId = repository.create(1L, VEHICLE, null, 10L).entityId();

        repository.resetScene();
        long secondId = repository.create(1L, VEHICLE, null, 20L).entityId();

        assertTrue(secondId > firstId);
    }

    @Test
    public void plateTrackHasOneOwnerAndControlledReassignmentIsAtomic() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleEntity first = repository.create(1L, VEHICLE, null, 10L);
        VehicleEntity second = repository.create(2L, VEHICLE, null, 10L);

        assertEquals(
                PlateTrackAttachmentStatus.ATTACHED,
                repository.attachPlate(
                        first.entityId(), 77L, plateQuad(), descriptor(0.8f), 20L
                )
        );
        assertEquals(
                PlateTrackAttachmentStatus.CONFLICT_REJECTED,
                repository.attachPlate(
                        second.entityId(), 77L, plateQuad(), descriptor(0.9f), 30L
                )
        );
        assertSame(first, repository.findByPlateTrackId(77L));
        assertNull(second.plateTrackId());

        assertEquals(
                PlateTrackAttachmentStatus.REASSIGNED,
                repository.reassignPlateTrack(
                        77L,
                        first.entityId(),
                        second.entityId(),
                        plateQuad(),
                        descriptor(0.9f),
                        40L
                )
        );
        assertNull(first.plateTrackId());
        assertEquals(Long.valueOf(77L), second.plateTrackId());
        assertSame(second, repository.findByPlateTrackId(77L));

        repository.markAcquired(second.entityId());
        assertNull(repository.findByPlateTrackId(77L));
    }

    @Test
    public void sceneResetAndExpirationClearPlateOwnershipIndex() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleEntity expiring = repository.create(1L, VEHICLE, null, 10L);
        repository.attachPlate(
                expiring.entityId(), 91L, plateQuad(), null, 10L
        );

        repository.expireOldEntities(1_000L, 100L);
        assertNull(repository.findByPlateTrackId(91L));

        VehicleEntity reset = repository.create(2L, VEHICLE, null, 1_100L);
        repository.attachPlate(reset.entityId(), 92L, plateQuad(), null, 1_100L);
        repository.resetScene();
        assertNull(repository.findByPlateTrackId(92L));
    }

    @Test
    public void activeAndCompletedRepositoriesAreBounded() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        for (int index = 0; index < VehicleEntityRepository.MAX_ACTIVE_ENTITIES + 10; index++) {
            repository.create(index + 1L, VEHICLE, null, index + 1L);
        }
        assertEquals(VehicleEntityRepository.MAX_ACTIVE_ENTITIES, repository.size());

        repository.resetScene();
        for (int index = 0;
                index < VehicleEntityRepository.MAX_COMPLETED_ENTITIES + 10; index++) {
            VehicleEntity entity = repository.create(
                    index + 1L, VEHICLE, null, index + 1L
            );
            repository.markAcquired(entity.entityId());
        }
        assertEquals(
                VehicleEntityRepository.MAX_COMPLETED_ENTITIES,
                repository.completedEntities().size()
        );
    }

    private static AppearanceDescriptor descriptor(float value) {
        return new AppearanceDescriptor(new float[]{value});
    }

    private static NormalizedQuad plateQuad() {
        return new NormalizedQuad(
                new float[]{0.3f, 0.6f, 0.5f, 0.6f, 0.5f, 0.7f, 0.3f, 0.7f}
        );
    }
}
