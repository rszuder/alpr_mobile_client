package com.example.alpr_v1.tracking;

/** Oddziela krótki dowód frame-to-frame od dłuższego stanu stabilizacji ruchu. */
public final class VisualMotionEvidenceDecay {
    public static final long EVIDENCE_RETENTION_NANOS = 500_000_000L;
    public static final long SETTLE_RETENTION_NANOS = 5_000_000_000L;

    public static final class Snapshot {
        public final boolean motionEstimated;
        public final boolean settling;
        public final boolean rapid;
        public final FrameMotionQuality quality;

        Snapshot(
                boolean motionEstimated,
                boolean settling,
                boolean rapid,
                FrameMotionQuality quality
        ) {
            this.motionEstimated = motionEstimated;
            this.settling = settling;
            this.rapid = rapid;
            this.quality = quality == null
                    ? FrameMotionQuality.unavailable(0) : quality;
        }
    }

    private long lastReliableMotionNanos;
    private boolean lastRapid;
    private FrameMotionQuality lastQuality = FrameMotionQuality.unavailable(0);

    public synchronized void record(
            long nowNanos,
            FrameMotionQuality quality,
            boolean rapid
    ) {
        if (nowNanos <= 0L
                || quality == null
                || !quality.reliableCameraMotion()) return;
        lastReliableMotionNanos = nowNanos;
        lastQuality = quality;
        lastRapid = rapid;
    }

    public synchronized Snapshot snapshot(long nowNanos) {
        long age = lastReliableMotionNanos <= 0L
                ? Long.MAX_VALUE
                : Math.max(0L, nowNanos - lastReliableMotionNanos);
        boolean estimated = age <= EVIDENCE_RETENTION_NANOS;
        return new Snapshot(
                estimated,
                !estimated && age <= SETTLE_RETENTION_NANOS,
                estimated && lastRapid,
                estimated ? lastQuality : FrameMotionQuality.unavailable(0)
        );
    }

    public synchronized void reset() {
        lastReliableMotionNanos = 0L;
        lastRapid = false;
        lastQuality = FrameMotionQuality.unavailable(0);
    }
}
