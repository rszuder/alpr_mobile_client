package com.example.alpr_v1.camera;

import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class AutoZoomControllerTest {
    @Test
    public void performsOnlyOneZoomAttemptForTrackAndReturnsAfterImprovement() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);

        AutoZoomController.Sample before = sample(7L, 0.48, false);
        AutoZoomController.Decision zoom = controller.evaluate(
                Collections.singletonList(before),
                1_000_000_000L
        );
        assertEquals(AutoZoomController.Action.REQUEST_ZOOM, zoom.action);

        controller.onZoomSettled(2_000_000_000L);
        AutoZoomController.Decision wait = controller.evaluate(
                Collections.singletonList(sample(7L, 0.57, false)),
                3_000_000_000L
        );
        assertEquals(AutoZoomController.Action.NONE, wait.action);

        AutoZoomController.Decision back = controller.evaluate(
                Collections.singletonList(sample(7L, 0.80, false)),
                4_000_000_000L
        );
        assertEquals(AutoZoomController.Action.RETURN_NORMAL, back.action);

        controller.onReturnSettled();
        assertEquals(
                AutoZoomController.Action.NONE,
                controller.evaluate(
                        Collections.singletonList(before),
                        5_000_000_000L
                ).action
        );
    }

    @Test
    public void ignoresUnstableTrackButAllowsSmallConfirmedPlate() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);

        assertEquals(
                AutoZoomController.Action.NONE,
                controller.evaluate(
                        Collections.singletonList(new AutoZoomController.Sample(
                                1L, 0.5f, 0.5f, 0.12f, 0.30, false, 1, true, true, true, "WA1234"
                        )),
                        1L
                ).action
        );
        assertEquals(
                AutoZoomController.Action.REQUEST_ZOOM,
                controller.evaluate(
                        Collections.singletonList(new AutoZoomController.Sample(
                                2L, 0.5f, 0.5f, 0.12f, 0.90, true, 4, true, true, true, "WA5678"
                        )),
                        2L
                ).action
        );
    }

    @Test
    public void sceneResetDoesNotChangeUsersEnabledSetting() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);
        controller.evaluate(
                Collections.singletonList(sample(8L, 0.30, false)),
                1L
        );

        controller.resetSession();

        assertEquals(true, controller.enabled());
        assertEquals(AutoZoomController.State.READY, controller.state());
    }

    @Test
    public void waitsForSecondConsistentResultWhenImprovementIsOnlyModerate() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);
        controller.evaluate(Collections.singletonList(sample(31L, 0.50, false)), 1L);
        controller.onZoomSettled(1_000_000_000L);

        assertEquals(
                AutoZoomController.Action.NONE,
                controller.evaluate(
                        Collections.singletonList(sample(31L, 0.63, false)),
                        2_000_000_000L
                ).action
        );
        assertEquals(
                AutoZoomController.Action.RETURN_NORMAL,
                controller.evaluate(
                        Collections.singletonList(sample(31L, 0.65, false)),
                        3_000_000_000L
                ).action
        );
    }

    @Test
    public void doesNotRepeatSamePlateWhenTrackerAssignsNewId() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);
        AutoZoomController.Sample first = sample(21L, 0.40, false);
        assertEquals(
                AutoZoomController.Action.REQUEST_ZOOM,
                controller.evaluate(Collections.singletonList(first), 1L).action
        );
        controller.onRequestFailed();

        AutoZoomController.Sample reassigned = sample(99L, 0.45, false);
        assertEquals(
                AutoZoomController.Action.NONE,
                controller.evaluate(Collections.singletonList(reassigned), 2L).action
        );
    }

    @Test
    public void exactTrackWinsOverEarlierNearbyCandidate() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);
        controller.evaluate(
                Collections.singletonList(sample(7L, 0.40, false)),
                1L
        );
        controller.onZoomSettled(1_000_000_000L);

        AutoZoomController.Sample nearbyWrong = new AutoZoomController.Sample(
                9L, 0.51f, 0.5f, 0.16f,
                0.95, true, 3, true, true, true, "OTHER"
        );
        AutoZoomController.Sample exactTrack = new AutoZoomController.Sample(
                7L, 0.58f, 0.5f, 0.16f,
                0.60, false, 3, true, true, true, "WA1234"
        );

        assertEquals(
                7L,
                controller.targetSample(
                        Arrays.asList(nearbyWrong, exactTrack)
                ).trackId
        );
    }

    @Test
    public void failedFirstMzAttemptStartsRescueWithoutConsensus() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);

        AutoZoomController.Decision decision = controller.evaluate(
                Collections.singletonList(new AutoZoomController.Sample(
                        41L,
                        0.5f,
                        0.5f,
                        0.30f,
                        0.0,
                        false,
                        0,
                        true,
                        true,
                        false,
                        ""
                )),
                1L
        );

        assertEquals(AutoZoomController.Action.REQUEST_ZOOM, decision.action);
        assertEquals("mz_no_detection", decision.reason);
    }

    @Test
    public void doesNotTreatSkippedMzAsFailedAttempt() {
        AutoZoomController controller = new AutoZoomController();
        controller.setEnabled(true);

        AutoZoomController.Decision decision = controller.evaluate(
                Collections.singletonList(new AutoZoomController.Sample(
                        42L,
                        0.5f,
                        0.5f,
                        0.30f,
                        0.0,
                        false,
                        0,
                        true,
                        false,
                        false,
                        ""
                )),
                1L
        );

        assertEquals(AutoZoomController.Action.NONE, decision.action);
    }

    private static AutoZoomController.Sample sample(
            long trackId,
            double confidence,
            boolean confirmed
    ) {
        return new AutoZoomController.Sample(
                trackId,
                0.5f,
                0.5f,
                0.16f,
                confidence,
                confirmed,
                3,
                true,
                true,
                true,
                "WA1234"
        );
    }
}
