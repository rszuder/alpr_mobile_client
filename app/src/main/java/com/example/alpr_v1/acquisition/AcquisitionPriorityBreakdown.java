package com.example.alpr_v1.acquisition;

/** Immutable, telemetry-ready explanation of one queue ranking decision. */
public final class AcquisitionPriorityBreakdown {
    public final float readability;
    public final float waitingAge;
    public final float exitUrgency;
    public final float center;
    public final float freshness;
    public final float novelty;
    public final float predictionPenalty;
    public final float recentAttemptPenalty;
    public final float cooldownPenalty;
    public final float total;

    public AcquisitionPriorityBreakdown(
            float readability,
            float waitingAge,
            float exitUrgency,
            float center,
            float freshness,
            float novelty,
            float predictionPenalty,
            float recentAttemptPenalty,
            float cooldownPenalty,
            float total
    ) {
        this.readability = finite(readability);
        this.waitingAge = finite(waitingAge);
        this.exitUrgency = finite(exitUrgency);
        this.center = finite(center);
        this.freshness = finite(freshness);
        this.novelty = finite(novelty);
        this.predictionPenalty = nonNegative(predictionPenalty);
        this.recentAttemptPenalty = nonNegative(recentAttemptPenalty);
        this.cooldownPenalty = nonNegative(cooldownPenalty);
        this.total = finite(total);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    private static float nonNegative(float value) {
        return Math.max(0f, finite(value));
    }
}
