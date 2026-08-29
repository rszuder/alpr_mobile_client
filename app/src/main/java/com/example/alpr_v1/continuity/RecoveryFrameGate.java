package com.example.alpr_v1.continuity;

/** Rejects queued source frames captured before the currently active recovery. */
public final class RecoveryFrameGate {
    private RecoveryFrameGate() {}

    public static boolean shouldSkip(
            ReacquireTelemetry recovery,
            long sourceTimestampNanos
    ) {
        return recovery != null
                && recovery.available
                && recovery.active
                && recovery.triggerSourceTimestampNanos > 0L
                && sourceTimestampNanos
                < recovery.triggerSourceTimestampNanos;
    }
}
