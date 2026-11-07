package com.example.alpr_v1.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExperimentCountdownTest {
    @Test public void lateUiRefreshReadsCurrentTimeWithoutReplayingMissedTicks() {
        assertEquals(10L, ExperimentCountdown.remainingSeconds(10_000L, 0L));
        assertEquals(3L, ExperimentCountdown.remainingSeconds(10_000L, 7_400L));
        assertEquals(0L, ExperimentCountdown.remainingSeconds(10_000L, 11_000L));
        assertEquals(600L, ExperimentCountdown.nextRefreshMillis(10_000L, 7_400L));
    }
    @Test public void refreshAlignsToSecondBoundariesAndAlwaysYieldsTheUiQueue() {
        assertEquals(1L, ExperimentCountdown.nextRefreshMillis(10_000L, 7_999L));
        assertEquals(2L, ExperimentCountdown.remainingSeconds(10_000L, 8_000L));
        assertEquals(1000L, ExperimentCountdown.nextRefreshMillis(10_000L, 8_000L));
        assertEquals(250L, ExperimentCountdown.nextRefreshMillis(-1L, 8_000L));
    }
}
