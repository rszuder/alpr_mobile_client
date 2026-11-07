package com.example.alpr_v1.experiment;

import java.util.function.LongSupplier;

/** One session-owned deadline. Neither UI ticks nor Activity recreation extend it. */
public final class ExperimentDeadline {
    public interface Cancellation { void cancel(); }
    public interface Scheduler { Cancellation schedule(Runnable task, long delayNanos); }
    private final LongSupplier clock;
    private final Scheduler scheduler;
    private Run current;

    private static final class Run {
        final String id;
        final long at;
        final Runnable expired;
        Cancellation scheduled;
        boolean claimed;
        Run(String id, long at, Runnable expired) {
            this.id = id; this.at = at; this.expired = expired;
        }
    }

    public ExperimentDeadline(LongSupplier clock, Scheduler scheduler) {
        this.clock = clock; this.scheduler = scheduler;
    }

    public synchronized void arm(String id, long deadlineNanos, Runnable expired) {
        if (current != null && current.id.equals(id)) {
            // A resumed device may have advanced elapsed realtime while its executor was asleep.
            if (!current.claimed && clock.getAsLong() >= current.at) {
                Run overdue = current;
                if (overdue.scheduled != null) overdue.scheduled.cancel();
                overdue.scheduled = scheduler.schedule(() -> fire(overdue), 0L);
            }
            return;
        }
        cancel();
        if (deadlineNanos < 0L) return;
        Run run = new Run(id, deadlineNanos, expired);
        current = run;
        run.scheduled = scheduler.schedule(() -> fire(run), Math.max(0L, run.at - clock.getAsLong()));
    }

    private void fire(Run run) {
        synchronized (this) {
            if (current != run || run.claimed) return;
            long remaining = run.at - clock.getAsLong();
            if (remaining > 0L) {
                run.scheduled = scheduler.schedule(() -> fire(run), remaining);
                return;
            }
            run.claimed = true;
        }
        // Never execute session completion under the scheduling lock.
        run.expired.run();
    }

    public synchronized void cancel() {
        if (current != null && current.scheduled != null) current.scheduled.cancel();
        current = null;
    }

    public static boolean admits(long deadlineNanos, long nowNanos) {
        return deadlineNanos < 0L || nowNanos < deadlineNanos;
    }
}
