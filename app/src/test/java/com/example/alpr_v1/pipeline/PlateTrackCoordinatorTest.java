package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.MotionBoxTracker;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.PlateTrackAttachmentStatus;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;
import com.example.alpr_v1.vision.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class PlateTrackCoordinatorTest {
    @Test
    public void reacquireDoesNotReusePlateOwnedByAnotherRetainedVehicle() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        VehicleEntityRepository repository = new VehicleEntityRepository();
        NormalizedBounds bounds = new NormalizedBounds(0.1f, 0.1f, 0.8f, 0.9f);
        VehicleEntity previous = repository.create(11L, bounds, null, 1L);
        PlateTrackCoordinator.Decision before = update(coordinator, 1L, 0.8f);
        repository.attachPlate(previous.entityId(), before.trackId, null, null, 1L);
        repository.updateRegistration(previous.entityId(),
                new PlateTextConsensus("AB123", 0.9f, 3, true), 1L);
        coordinator.recordRecognition(before.trackId, 0.8f, 1L, characters(), labels());

        // Soft scene recovery resets the plate tracker while retaining entities.
        coordinator.reset();
        VehicleEntity next = repository.create(22L, bounds, null, 2L);
        PlateTrackCoordinator.Decision after = update(coordinator, 2L, 0.8f);
        assertEquals(PlateTrackAttachmentStatus.ATTACHED,
                repository.attachPlate(next.entityId(), after.trackId, null, null, 2L));
        assertTrue(after.trackId > before.trackId);
        assertEquals(next.entityId(), repository.findByPlateTrackId(after.trackId).entityId());
        assertEquals("AB123", previous.registration().text);
        assertTrue(after.recognize);
        assertTrue(after.currentResult == null || after.currentResult.text.isEmpty());

        // A real collision in one tracker lifetime must still be rejected.
        assertEquals(PlateTrackAttachmentStatus.CONFLICT_REJECTED,
                repository.attachPlate(previous.entityId(), after.trackId, null, null, 3L));
    }

    @Test
    public void profileChangeAlsoKeepsPlateIdsUnique() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        long before = update(coordinator, 1L, 0.8f).trackId;
        coordinator.setProfile(RecognitionProfile.FAST);
        assertTrue(update(coordinator, 2L, 0.8f).trackId > before);
    }

    @Test
    public void stableRecognitionStillAllowsLaterMzRetry() {

        PlateTrackCoordinator coordinator =
                new PlateTrackCoordinator();


        PlateTrackCoordinator.Decision first =
                update(
                        coordinator,
                        1,
                        0.8f
                );

        assertTrue(
                first.recognize
        );
        assertEquals(1, first.mzAttemptIndex);


        coordinator.recordRecognition(
                first.trackId,
                0.8f,
                1,
                characters(),
                labels()
        );


        PlateTrackCoordinator.Decision second =
                update(
                        coordinator,
                        2,
                        0.8f
                );

        assertTrue(
                second.recognize
        );
        assertEquals(2, second.mzAttemptIndex);


        coordinator.recordRecognition(
                second.trackId,
                0.8f,
                2,
                characters(),
                labels()
        );


        /*
         * Po dwóch zgodnych wynikach konsensus
         * jest stabilny.
         *
         * Nie uruchamiamy jednak MZ natychmiast
         * w każdej kolejnej klatce.
         */
        PlateTrackCoordinator.Decision third =
                update(
                        coordinator,
                        3,
                        0.8f
                );

        assertFalse(
                third.recognize
        );

        assertTrue(
                third.currentResult.stable
        );


        /*
         * BALANCED ma retryGapFrames = 2.
         *
         * Po odpowiednim odstępie MZ powinien
         * dostać kolejną szansę mimo stable=true.
         */
        PlateTrackCoordinator.Decision fourth =
                update(
                        coordinator,
                        4,
                        0.8f
                );

        assertTrue(
                fourth.recognize
        );

        assertTrue(
                fourth.currentResult.stable
        );
    }

    @Test
    public void rejectsInvalidGeometryBeforeCharacterStage() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        List<PlateTrackCoordinator.Decision> decisions = coordinator.update(
                Collections.singletonList(new PlateTrackCoordinator.Observation(
                        0,
                        new MotionBoxTracker.Box(0.1f, 0.2f, 0.4f, 0.3f),
                        0.9f,
                        false
                )),
                1,
                1_000_000_000L
        );

        assertFalse(decisions.get(0).recognize);
    }

    @Test
    public void zoomForcesFreshMzWithoutClearingExistingConsensus() {
        PlateTrackCoordinator coordinator =
                new PlateTrackCoordinator(RecognitionProfile.FAST);

        PlateTrackCoordinator.Decision first = update(coordinator, 1, 0.8f);
        assertTrue(first.recognize);
        coordinator.recordRecognition(
                first.trackId,
                0.8f,
                1,
                characters(),
                labels()
        );

        PlateTrackCoordinator.Decision second = update(coordinator, 2, 0.8f);
        assertTrue(second.recognize);
        coordinator.recordRecognition(
                second.trackId,
                0.8f,
                2,
                characters(),
                labels()
        );

        assertFalse(update(coordinator, 3, 0.8f).recognize);

        coordinator.requestFreshRecognitionAfterZoom();
        PlateTrackCoordinator.Decision zoomed = update(coordinator, 4, 0.10f);

        assertTrue(zoomed.recognize);
        assertTrue(zoomed.currentResult.stable);
    }

    @Test
    public void noMtRunDoesNotClearConsensus() {
        PlateTrackCoordinator coordinator = stableCoordinator();

        coordinator.onMtEvent(
                PlateTrackCoordinator.MtStateEvent.NO_MT_RUN,
                250_000_000L
        );
        PlateTrackCoordinator.Decision resumed = update(coordinator, 3, 0.8f);

        assertTrue(resumed.currentResult.stable);
        assertEquals("AB123", resumed.currentResult.text);
    }

    @Test
    public void singleMtMissDoesNotClearConsensus() {
        PlateTrackCoordinator coordinator = stableCoordinator();

        assertTrue(coordinator.update(
                Collections.emptyList(),
                3,
                300_000_000L
        ).isEmpty());
        assertEquals(1, coordinator.retainedStateCount());

        PlateTrackCoordinator.Decision resumed = update(coordinator, 4, 0.8f);
        assertTrue(resumed.currentResult.stable);
        assertEquals("AB123", resumed.currentResult.text);
    }

    @Test
    public void targetLossExpiresConsensusOnlyAfterTtl() {
        PlateTrackCoordinator coordinator = stableCoordinator();
        long lostAt = 300_000_000L;

        coordinator.onMtEvent(
                PlateTrackCoordinator.MtStateEvent.TARGET_LOST,
                lostAt
        );
        assertEquals(1, coordinator.retainedStateCount());

        coordinator.onMtEvent(
                PlateTrackCoordinator.MtStateEvent.TARGET_LOST,
                lostAt + PlateTrackCoordinator.MZ_STATE_TTL_NANOS + 1L
        );
        assertEquals(0, coordinator.retainedStateCount());
    }

    @Test
    public void scanEntityKeepsOcrConsensusAcrossRoundRobinPlateTracks() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();

        PlateTrackCoordinator.Decision firstEntityOne = updateEntity(
                coordinator, 1, 11L, 0.10f
        );
        coordinator.bindEntityState(firstEntityOne.trackId, 11L);
        coordinator.recordRecognition(
                firstEntityOne.trackId,
                0.8f,
                1,
                characters(),
                labels()
        );

        PlateTrackCoordinator.Decision entityTwo = updateEntity(
                coordinator, 2, 22L, 0.70f
        );
        coordinator.bindEntityState(entityTwo.trackId, 22L);
        coordinator.recordRecognition(
                entityTwo.trackId,
                0.8f,
                2,
                characters(),
                labels()
        );

        PlateTrackCoordinator.Decision secondEntityOne = updateEntity(
                coordinator, 4, 11L, 0.10f
        );
        assertTrue(secondEntityOne.recognize);
        coordinator.bindEntityState(secondEntityOne.trackId, 11L);
        TemporalCharacterAggregator.Result confirmed = coordinator.recordRecognition(
                secondEntityOne.trackId,
                0.8f,
                4,
                characters(),
                labels()
        );
        assertTrue(confirmed.stable);
        assertEquals("AB123", confirmed.text);
    }

    private static PlateTrackCoordinator stableCoordinator() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        PlateTrackCoordinator.Decision first = update(coordinator, 1, 0.8f);
        coordinator.recordRecognition(
                first.trackId, 0.8f, 1, characters(), labels()
        );
        PlateTrackCoordinator.Decision second = update(coordinator, 2, 0.8f);
        coordinator.recordRecognition(
                second.trackId, 0.8f, 2, characters(), labels()
        );
        return coordinator;
    }

    private static PlateTrackCoordinator.Decision update(
            PlateTrackCoordinator coordinator, long frame, float quality
    ) {
        return coordinator.update(
                Collections.singletonList(new PlateTrackCoordinator.Observation(
                        0,
                        new MotionBoxTracker.Box(0.1f, 0.2f, 0.4f, 0.3f),
                        quality,
                        true
                )),
                frame,
                frame * 100_000_000L
        ).get(0);
    }

    private static PlateTrackCoordinator.Decision updateEntity(
            PlateTrackCoordinator coordinator,
            long frame,
            long entityId,
            float left
    ) {
        return coordinator.update(
                Collections.singletonList(new PlateTrackCoordinator.Observation(
                        0,
                        new MotionBoxTracker.Box(left, 0.2f, left + 0.2f, 0.3f),
                        0.8f,
                        true
                )),
                frame,
                frame * 100_000_000L
        ).get(0);
    }

    private static List<Detection> characters() {
        return Arrays.asList(
                new Detection(0, 0.9f, 0, 0, 10, 20, Collections.emptyList()),
                new Detection(1, 0.9f, 15, 0, 25, 20, Collections.emptyList()),
                new Detection(2, 0.9f, 30, 0, 40, 20, Collections.emptyList()),
                new Detection(3, 0.9f, 45, 0, 55, 20, Collections.emptyList()),
                new Detection(4, 0.9f, 60, 0, 70, 20, Collections.emptyList())
        );
    }

    private static List<String> labels() {
        return Arrays.asList("A", "B", "1", "2", "3");
    }
}
