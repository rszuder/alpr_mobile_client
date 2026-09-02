package com.example.alpr_v1.acquisition;

/** Immutable Phase 3B queue, attempt and active-session budgets. */
public final class ScanAcquisitionProfile {
    public static final ScanAcquisitionProfile DEFAULT = new ScanAcquisitionProfile(
            32,
            0.20f,
            500_000_000L,
            4_000_000_000L,
            1_250_000_000L,
            1_500_000_000L,
            0.28f, 0.22f, 0.18f, 0.12f, 0.10f, 0.10f,
            0.16f, 0.12f, 1.0f,
            2, 2,
            15_000_000_000L,
            8_000_000_000L
    );

    public final int maximumQueueSize;
    public final float minimumEffectiveConfidence;
    public final long maximumPredictionAgeNanos;
    public final long waitingAgeSaturationNanos;
    public final long recentAttemptPenaltyNanos;
    public final long defaultCooldownNanos;
    public final float readabilityWeight;
    public final float waitingAgeWeight;
    public final float exitUrgencyWeight;
    public final float centerWeight;
    public final float freshnessWeight;
    public final float noveltyWeight;
    public final float predictionPenalty;
    public final float recentAttemptPenalty;
    public final float cooldownPenalty;
    public final int maximumMtAttempts;
    public final int maximumFreshMzAttempts;
    public final long maximumActiveSessionNanos;
    public final long noProgressTimeoutNanos;

    public ScanAcquisitionProfile(
            int maximumQueueSize,
            float minimumEffectiveConfidence,
            long maximumPredictionAgeNanos,
            long waitingAgeSaturationNanos,
            long recentAttemptPenaltyNanos,
            long defaultCooldownNanos,
            float readabilityWeight,
            float waitingAgeWeight,
            float exitUrgencyWeight,
            float centerWeight,
            float freshnessWeight,
            float noveltyWeight,
            float predictionPenalty,
            float recentAttemptPenalty,
            float cooldownPenalty,
            int maximumMtAttempts,
            int maximumFreshMzAttempts,
            long maximumActiveSessionNanos,
            long noProgressTimeoutNanos
    ) {
        if (maximumQueueSize <= 0) throw new IllegalArgumentException("maximumQueueSize");
        if (maximumPredictionAgeNanos < 0L) throw new IllegalArgumentException("maximumPredictionAgeNanos");
        if (waitingAgeSaturationNanos <= 0L) throw new IllegalArgumentException("waitingAgeSaturationNanos");
        if (recentAttemptPenaltyNanos < 0L) throw new IllegalArgumentException("recentAttemptPenaltyNanos");
        if (defaultCooldownNanos < 0L) throw new IllegalArgumentException("defaultCooldownNanos");
        if (maximumMtAttempts <= 0 || maximumFreshMzAttempts <= 0) {
            throw new IllegalArgumentException("attempt budgets");
        }
        if (maximumActiveSessionNanos <= 0L || noProgressTimeoutNanos <= 0L) {
            throw new IllegalArgumentException("session budgets");
        }
        this.maximumQueueSize = maximumQueueSize;
        this.minimumEffectiveConfidence = clamp01(minimumEffectiveConfidence);
        this.maximumPredictionAgeNanos = maximumPredictionAgeNanos;
        this.waitingAgeSaturationNanos = waitingAgeSaturationNanos;
        this.recentAttemptPenaltyNanos = recentAttemptPenaltyNanos;
        this.defaultCooldownNanos = defaultCooldownNanos;
        this.readabilityWeight = nonNegative(readabilityWeight);
        this.waitingAgeWeight = nonNegative(waitingAgeWeight);
        this.exitUrgencyWeight = nonNegative(exitUrgencyWeight);
        this.centerWeight = nonNegative(centerWeight);
        this.freshnessWeight = nonNegative(freshnessWeight);
        this.noveltyWeight = nonNegative(noveltyWeight);
        this.predictionPenalty = nonNegative(predictionPenalty);
        this.recentAttemptPenalty = nonNegative(recentAttemptPenalty);
        this.cooldownPenalty = nonNegative(cooldownPenalty);
        this.maximumMtAttempts = maximumMtAttempts;
        this.maximumFreshMzAttempts = maximumFreshMzAttempts;
        this.maximumActiveSessionNanos = maximumActiveSessionNanos;
        this.noProgressTimeoutNanos = noProgressTimeoutNanos;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }
}
