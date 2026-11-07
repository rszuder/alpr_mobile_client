package com.example.alpr_v1.experiment;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExperimentSessionDeadlineTest {
    private ExperimentSession started(TimerConfig timer) {
        ExperimentSession session = new ExperimentSession();
        ExperimentSession.Prepared prepared = session.prepare("quality", "R0", timer,
                ThermalConfig.disabled(), ExperimentIdentity.defaults(), null);
        assertTrue(session.startPrepared(prepared, 10_000L, 1_000_000_000L));
        return session;
    }

    @Test public void lateUiStopCannotLengthenMeasurementOrChangeTimerReason() {
        ExperimentSession session = started(TimerConfig.of(true, 60));
        assertEquals(61_000_000_000L, session.timerDeadlineElapsedNanos());
        assertTrue(session.admitsWork(60_999_999_999L));
        assertFalse(session.admitsWork(61_000_000_000L));
        session.finishAt(ExperimentSession.CompletionReason.MANUAL, 75_000L, 66_000_000_000L);
        assertEquals(ExperimentSession.CompletionReason.TIMER, session.completionReason());
        assertEquals(60_000L, session.durationMillis());
        assertEquals(70_000L, session.finishedAtMillis());
        assertFalse(session.finishAt(ExperimentSession.CompletionReason.ERROR, 90_000L, 80_000_000_000L));
    }

    @Test public void earlyManualStopAndDisabledTimerKeepTheirActualDuration() {
        ExperimentSession session = started(TimerConfig.of(true, 60));
        session.finishAt(ExperimentSession.CompletionReason.MANUAL, 12_000L, 3_000_000_000L);
        assertEquals(2_000L, session.durationMillis());
        assertEquals(ExperimentSession.CompletionReason.MANUAL, session.completionReason());
        session = started(TimerConfig.disabled());
        assertEquals(-1L, session.timerDeadlineElapsedNanos());
        assertTrue(session.admitsWork(100_000_000_000L));
        session.finishAt(ExperimentSession.CompletionReason.MANUAL, 100_000L, 91_000_000_000L);
        assertEquals(90_000L, session.durationMillis());
    }

    @Test public void nextRepeatUsesNewT0AndCustomMinutesWithoutInheritingOldDeadline() {
        ExperimentSession session = started(TimerConfig.of(true, 60));
        session.finishAt(ExperimentSession.CompletionReason.TIMER, 70_000L, 61_000_000_000L);
        ExperimentSession.Prepared next = session.prepare("quality", "R0", TimerConfig.fromMinutes("2,5"),
                ThermalConfig.disabled(), ExperimentIdentity.defaults(), null);
        session.startPrepared(next, 100_000L, 90_000_000_000L);
        assertEquals(240_000_000_000L, session.timerDeadlineElapsedNanos());
        assertTrue(session.admitsWork(200_000_000_000L));
    }
}
