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

    public static boolean shouldSkip(
            ReacquireTelemetry recovery,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain
    ) {
        if (recovery == null || !recovery.available || !recovery.active) {
            return false;
        }
        if (recovery.triggerSourceSequence > 0L) {
            return sourceSequence <= recovery.triggerSourceSequence;
        }
        if (sourceSequence > 0L) return false;
        if (recovery.triggerSourceTimestampNanos <= 0L
                || sourceTimestampNanos <= 0L) {
            return true;
        }
        SourceTimestampDomain currentDomain = sourceTimestampDomain == null
                ? SourceTimestampDomain.UNKNOWN : sourceTimestampDomain;
        if (!recovery.triggerSourceTimestampDomain
                .freshnessComparableWith(currentDomain)) {
            return true;
        }
        return sourceTimestampNanos <= recovery.triggerSourceTimestampNanos;
    }
}
