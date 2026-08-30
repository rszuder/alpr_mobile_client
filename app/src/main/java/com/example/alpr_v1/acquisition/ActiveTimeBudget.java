package com.example.alpr_v1.acquisition;

/** Monotonic active-time clock whose elapsed value excludes pause intervals. */
public final class ActiveTimeBudget {
    private final long limitNanos;
    private long activeStartedRuntimeNanos;
    private long accumulatedActiveNanos;
    private boolean running;

    public ActiveTimeBudget(long limitNanos) {
        if (limitNanos <= 0L) throw new IllegalArgumentException("limitNanos");
        this.limitNanos = limitNanos;
    }

    public synchronized void start(long nowRuntimeNanos) {
        if (running) return;
        activeStartedRuntimeNanos = nonNegative(nowRuntimeNanos);
        running = true;
    }

    public synchronized void pause(long nowRuntimeNanos) {
        if (!running) return;
        accumulatedActiveNanos = saturatedAdd(
                accumulatedActiveNanos,
                elapsed(activeStartedRuntimeNanos, nowRuntimeNanos)
        );
        running = false;
    }

    public synchronized void resume(long nowRuntimeNanos) {
        start(nowRuntimeNanos);
    }

    public synchronized void reset(long nowRuntimeNanos, boolean startRunning) {
        accumulatedActiveNanos = 0L;
        activeStartedRuntimeNanos = nonNegative(nowRuntimeNanos);
        running = startRunning;
    }

    public synchronized long elapsedActiveNanos(long nowRuntimeNanos) {
        if (!running) return accumulatedActiveNanos;
        return saturatedAdd(
                accumulatedActiveNanos,
                elapsed(activeStartedRuntimeNanos, nowRuntimeNanos)
        );
    }

    public synchronized long remainingNanos(long nowRuntimeNanos) {
        return Math.max(0L, limitNanos - elapsedActiveNanos(nowRuntimeNanos));
    }

    public synchronized boolean exhausted(long nowRuntimeNanos) {
        return elapsedActiveNanos(nowRuntimeNanos) >= limitNanos;
    }

    public synchronized boolean running() {
        return running;
    }

    public long limitNanos() {
        return limitNanos;
    }

    private static long elapsed(long started, long now) {
        return Math.max(0L, nonNegative(now) - nonNegative(started));
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
