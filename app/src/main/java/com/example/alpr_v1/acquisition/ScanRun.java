package com.example.alpr_v1.acquisition;

/** Mutable lifecycle owner for one user-visible Scan run. */
public final class ScanRun {
    public final long scanRunId;
    public final long startedRuntimeNanos;
    private final ActiveTimeBudget activeClock = new ActiveTimeBudget(Long.MAX_VALUE);
    private ScanRunState state = ScanRunState.RUNNING;
    private long stoppedRuntimeNanos;

    public ScanRun(long scanRunId, long nowRuntimeNanos) {
        if (scanRunId <= 0L) throw new IllegalArgumentException("scanRunId");
        this.scanRunId = scanRunId;
        this.startedRuntimeNanos = Math.max(0L, nowRuntimeNanos);
        activeClock.start(this.startedRuntimeNanos);
    }

    public synchronized ScanRunState state() {
        return state;
    }

    public synchronized void pause(long nowRuntimeNanos) {
        if (state != ScanRunState.RUNNING) return;
        activeClock.pause(nowRuntimeNanos);
        state = ScanRunState.PAUSED;
    }

    public synchronized void resume(long nowRuntimeNanos) {
        if (state != ScanRunState.PAUSED) return;
        activeClock.resume(nowRuntimeNanos);
        state = ScanRunState.RUNNING;
    }

    public synchronized void stop(long nowRuntimeNanos) {
        if (!state.active()) return;
        activeClock.pause(nowRuntimeNanos);
        stoppedRuntimeNanos = Math.max(startedRuntimeNanos, nowRuntimeNanos);
        state = ScanRunState.STOPPED;
    }

    public synchronized long activeDurationNanos(long nowRuntimeNanos) {
        return activeClock.elapsedActiveNanos(nowRuntimeNanos);
    }

    public synchronized long wallDurationNanos(long nowRuntimeNanos) {
        long end = state == ScanRunState.STOPPED
                ? stoppedRuntimeNanos : Math.max(startedRuntimeNanos, nowRuntimeNanos);
        return Math.max(0L, end - startedRuntimeNanos);
    }
}
