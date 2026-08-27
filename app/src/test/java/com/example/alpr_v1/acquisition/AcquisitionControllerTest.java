package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.CropReference;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.MotionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.TargetPurpose;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;
import com.example.alpr_v1.tracking.VehicleTrackManager;

import org.junit.Test;

import java.util.Collections;

public class AcquisitionControllerTest {
    @Test
    public void scanUsesShortSessionAndReleasesAcquiredEntity() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackManager manager = manager(repository);
        VehicleTrackManager.Snapshot tracked = manager.update(Collections.singletonList(
                new VehicleTrackManager.Observation(bounds(), 0.9f, appearance(), 0)
        ), 100L).get(0);
        VehicleEntity entity = repository.get(tracked.entityId);
        AcquisitionController controller = new AcquisitionController(repository);
        controller.refresh(Collections.singletonList(tracked), 110L);

        assertEquals(TargetPurpose.SCAN_ACQUISITION,
                controller.startNext(120L).purpose());
        assertFalse(controller.activeSession().persistent());
        AcquisitionController.Outcome outcome = controller.completeAttempt(
                51L,
                quad(),
                appearance(),
                new PlateTextConsensus("WE 911GT", 0.94f, 4, true),
                new CropReference("best", CropReference.Kind.WIDE_PLATE, 0.9f, 130L),
                quality(0.9f),
                130L
        );

        assertEquals(AcquisitionController.Outcome.ACQUIRED, outcome);
        assertTrue(entity.acquisitionCompleted());
        assertFalse(entity.activeTarget());
        assertEquals("WE911GT", entity.registration().text);
        assertNull(controller.activeSession());
    }

    @Test
    public void failedAttemptRequeuesThenStopsAfterThirdAttempt() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackManager manager = manager(repository);
        VehicleTrackManager.Snapshot tracked = manager.update(Collections.singletonList(
                new VehicleTrackManager.Observation(bounds(), 0.9f, appearance(), 0)
        ), 100L).get(0);
        VehicleEntity entity = repository.get(tracked.entityId);
        AcquisitionController controller = new AcquisitionController(repository);

        for (int attempt = 1; attempt <= 3; attempt++) {
            controller.refresh(Collections.singletonList(tracked), attempt * 100L);
            controller.startNext(attempt * 100L + 10L);
            AcquisitionController.Outcome outcome = controller.completeAttempt(
                    0L, null, null, null, null, quality(0.1f), attempt * 100L + 20L
            );
            assertEquals(attempt < 3
                    ? AcquisitionController.Outcome.REQUEUED
                    : AcquisitionController.Outcome.FAILED, outcome);
        }

        assertEquals(3, entity.mtAttempts());
        assertEquals(EntityAcquisitionState.FAILED, entity.acquisitionState());
        assertFalse(entity.activeTarget());
    }

    @Test
    public void deduplicatorRejectsAlreadyAcquiredRegistration() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleEntity saved = repository.updateFromMp(
                1L, bounds(), MotionState.STATIONARY, appearance(), 10L
        );
        repository.updateRegistration(
                saved.entityId(), new PlateTextConsensus("KR1234A", 0.9f, 3, true), 20L
        );
        repository.markAcquired(saved.entityId());
        VehicleEntity candidate = repository.updateFromMp(
                2L, new NormalizedBounds(0.12f, 0.2f, 0.62f, 0.7f),
                MotionState.STATIONARY, appearance(), 30L
        );
        repository.updateRegistration(
                candidate.entityId(), new PlateTextConsensus("KR1234A", 0.7f, 1, false), 40L
        );

        AcquisitionDeduplicator.Result result = new AcquisitionDeduplicator().evaluate(
                candidate, repository.activeEntities()
        );

        assertTrue(result.duplicate);
        assertEquals("same_registration", result.reason);
        assertEquals(Long.valueOf(saved.entityId()), result.matchingEntityId);
    }

    private static VehicleTrackManager manager(VehicleEntityRepository repository) {
        return new VehicleTrackManager(repository, 2, 1_000L, 2_000L);
    }

    private static NormalizedBounds bounds() {
        return new NormalizedBounds(0.1f, 0.2f, 0.6f, 0.7f);
    }

    private static AppearanceDescriptor appearance() {
        return new AppearanceDescriptor(new float[]{1f, 0f, 0f});
    }

    private static NormalizedQuad quad() {
        return new NormalizedQuad(
                new float[]{0.2f, 0.5f, 0.4f, 0.5f, 0.4f, 0.6f, 0.2f, 0.6f}
        );
    }

    private static BestCropSelector.Quality quality(float value) {
        return new BestCropSelector.Quality(
                value, value, value, value, value, value, value, false
        );
    }
}
