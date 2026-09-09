package com.example.alpr_v1.continuity;

/** Nonblocking handoff from preview tracking to inference scene preflight. */
public final class RecentMotionEvidence {
    public static final long MAXIMUM_AGE_NANOS = 750_000_000L;
    private static final class Sample {
        final MotionExplanationEvidence evidence;
        final long at;
        Sample(MotionExplanationEvidence evidence, long at) { this.evidence = evidence; this.at = at; }
    }
    private final java.util.concurrent.atomic.AtomicReference<Sample> latest =
            new java.util.concurrent.atomic.AtomicReference<>();

    public void publish(MotionExplanationEvidence evidence, long nowNanos) {
        if (evidence == null || nowNanos < 0L) return;
        latest.accumulateAndGet(new Sample(evidence, nowNanos),
                (old, next) -> old != null && old.at > next.at ? old : next);
    }
    public MotionExplanationEvidence current(long nowNanos) {
        Sample sample = latest.get();
        return sample != null && nowNanos >= sample.at && nowNanos - sample.at <= MAXIMUM_AGE_NANOS
                ? sample.evidence : null;
    }
    public void reset() { latest.set(null); }
}
