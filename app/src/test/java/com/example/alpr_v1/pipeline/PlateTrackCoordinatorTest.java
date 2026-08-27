package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.MotionBoxTracker;
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
                Arrays.asList(
                        "A",
                        "1"
                )
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
                Arrays.asList(
                        "A",
                        "1"
                )
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
                Arrays.asList("A", "1")
        );

        PlateTrackCoordinator.Decision second = update(coordinator, 2, 0.8f);
        assertTrue(second.recognize);
        coordinator.recordRecognition(
                second.trackId,
                0.8f,
                2,
                characters(),
                Arrays.asList("A", "1")
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
        assertEquals("A1", resumed.currentResult.text);
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
        assertEquals("A1", resumed.currentResult.text);
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

    private static PlateTrackCoordinator stableCoordinator() {
        PlateTrackCoordinator coordinator = new PlateTrackCoordinator();
        PlateTrackCoordinator.Decision first = update(coordinator, 1, 0.8f);
        coordinator.recordRecognition(
                first.trackId, 0.8f, 1, characters(), Arrays.asList("A", "1")
        );
        PlateTrackCoordinator.Decision second = update(coordinator, 2, 0.8f);
        coordinator.recordRecognition(
                second.trackId, 0.8f, 2, characters(), Arrays.asList("A", "1")
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

    private static List<Detection> characters() {
        return Arrays.asList(
                new Detection(0, 0.9f, 0, 0, 10, 20, Collections.emptyList()),
                new Detection(1, 0.9f, 15, 0, 25, 20, Collections.emptyList())
        );
    }
}
