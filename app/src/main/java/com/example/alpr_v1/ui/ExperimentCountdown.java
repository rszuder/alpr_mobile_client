package com.example.alpr_v1.ui;

/** Presentation follows elapsed realtime, never a queue of decremented seconds. */
public final class ExperimentCountdown {
    private ExperimentCountdown() { }
    public static long remainingSeconds(long deadlineMillis, long nowMillis) {
        return (Math.max(0L, deadlineMillis - nowMillis) + 999L) / 1000L;
    }
    public static long nextRefreshMillis(long deadlineMillis, long nowMillis) {
        long remaining = deadlineMillis - nowMillis;
        if (remaining <= 0L) return 250L;
        // One current value per second; never enqueue a replay of missed seconds.
        return (remaining - 1L) % 1000L + 1L;
    }
}
