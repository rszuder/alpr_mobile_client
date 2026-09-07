package com.example.alpr_v1.ui;

/** Short-lived visual evidence, never a replacement for a scene/transform boundary. */
public final class StationarySceneSupport {
    private static final long SETTLE_NANOS = 300_000_000L;
    private static final long EVIDENCE_MAX_AGE_NANOS = 1_500_000_000L;
    private long stableSince = -1L;
    private long lastEvidence = -1L;

    public synchronized void observe(long nowNanos, boolean stationary) {
        if (!stationary) { reset(); return; }
        if (lastEvidence < 0L || nowNanos < lastEvidence
                || nowNanos - lastEvidence > EVIDENCE_MAX_AGE_NANOS) stableSince = nowNanos;
        lastEvidence = nowNanos;
    }

    public synchronized boolean supported(long nowNanos) {
        return stableSince >= 0L && nowNanos >= lastEvidence
                && nowNanos - lastEvidence <= EVIDENCE_MAX_AGE_NANOS
                && nowNanos - stableSince >= SETTLE_NANOS;
    }

    /** Unknown/low-quality flow does not mean measured motion; let evidence age out. */
    public synchronized void observeUncertain(long nowNanos) {
        if (lastEvidence >= 0L && (nowNanos < lastEvidence
                || nowNanos - lastEvidence > EVIDENCE_MAX_AGE_NANOS)) reset();
    }

    public synchronized void reset() { stableSince = -1L; lastEvidence = -1L; }

    public static long maximumOverlayAge(long measuredIntervalNanos) {
        long interval = Math.max(0L, Math.min(15_000_000_000L, measuredIntervalNanos));
        return Math.max(15_000_000_000L, interval * 2L);
    }
}
