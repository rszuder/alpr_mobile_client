package com.example.alpr_v1.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExperimentDeadlineTest {
    private final AtomicLong now = new AtomicLong(100L);
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final ExperimentDeadline timer = new ExperimentDeadline(now::get, (task, delay) -> {
        tasks.add(task); delays.add(delay); return () -> { };
    });

    @Test public void recreationKeepsOriginalDeadlineAndExpiresExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        timer.arm("r1", 200L, calls::incrementAndGet);
        now.set(150L);
        timer.arm("r1", 900L, () -> fail("rearmed"));
        assertEquals(1, tasks.size());
        now.set(200L);
        tasks.get(0).run(); tasks.get(0).run();
        assertEquals(1, calls.get());
    }

    @Test public void earlyCallbackReschedulesAgainstClock() {
        AtomicInteger calls = new AtomicInteger();
        timer.arm("r1", 200L, calls::incrementAndGet);
        now.set(190L); tasks.get(0).run();
        assertEquals(0, calls.get()); assertEquals(Long.valueOf(10L), delays.get(1));
        now.set(205L); tasks.get(1).run();
        assertEquals(1, calls.get());
    }

    @Test public void delayedCallbackCannotExtendAdmissionWindow() {
        AtomicInteger calls = new AtomicInteger();
        timer.arm("r1", 200L, calls::incrementAndGet);
        now.set(900L);
        assertFalse(ExperimentDeadline.admits(200L, now.get()));
        assertEquals(0, calls.get()); // UI and scheduler can both be late.
        tasks.get(0).run(); assertEquals(1, calls.get());
    }

    @Test public void cancelledAndPreviousRepeatCallbacksCannotStopNextRepeat() {
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        timer.arm("r1", 200L, first::incrementAndGet);
        Runnable old = tasks.get(0);
        timer.cancel(); timer.arm("r2", 400L, second::incrementAndGet);
        now.set(500L); old.run(); tasks.get(1).run();
        assertEquals(0, first.get()); assertEquals(1, second.get());
    }

    @Test public void disabledAndExactBoundary() {
        timer.arm("disabled", -1L, () -> fail("disabled"));
        assertTrue(tasks.isEmpty());
        assertTrue(ExperimentDeadline.admits(-1L, Long.MAX_VALUE));
        assertTrue(ExperimentDeadline.admits(200L, 199L));
        assertFalse(ExperimentDeadline.admits(200L, 200L));
    }

    @Test public void resumeAfterElapsedTimeJumpRechecksOverdueDeadlineWithoutExtendingIt() {
        AtomicInteger calls = new AtomicInteger();
        timer.arm("r1", 200L, calls::incrementAndGet);
        now.set(10_000L);
        timer.arm("r1", 20_000L, () -> fail("deadline changed"));
        assertEquals(Long.valueOf(0L), delays.get(1));
        tasks.get(1).run(); tasks.get(0).run();
        assertEquals(1, calls.get());
    }
}
