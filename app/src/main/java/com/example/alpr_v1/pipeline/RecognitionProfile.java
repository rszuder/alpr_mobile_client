package com.example.alpr_v1.pipeline;

/** Konfigurowalny kompromis między czasem pierwszego wyniku a liczbą prób MZ. */
public enum RecognitionProfile {
    FAST("fast", 2, 0.45f, 0.08f, 3L, 10L),
    BALANCED("balanced", 4, 0.35f, 0.04f, 2L, 6L),
    ACCURATE("accurate", 6, 0.25f, 0.02f, 1L, 4L);

    final String wireName;
    final int burstAttempts;
    final float minimumQuality;
    final float qualityImprovement;
    final long retryGapFrames;
    final long periodicRetryGapFrames;

    RecognitionProfile(
            String wireName,
            int burstAttempts,
            float minimumQuality,
            float qualityImprovement,
            long retryGapFrames,
            long periodicRetryGapFrames
    ) {
        this.wireName = wireName;
        this.burstAttempts = burstAttempts;
        this.minimumQuality = minimumQuality;
        this.qualityImprovement = qualityImprovement;
        this.retryGapFrames = retryGapFrames;
        this.periodicRetryGapFrames = periodicRetryGapFrames;
    }

    public String wireName() { return wireName; }

    public RecognitionProfile next() {
        RecognitionProfile[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static RecognitionProfile fromWireName(String value) {
        for (RecognitionProfile profile : values()) {
            if (profile.wireName.equalsIgnoreCase(value == null ? "" : value.trim())) {
                return profile;
            }
        }
        return BALANCED;
    }
}
