package com.example.alpr_v1.experiment;

/**
 * Opcjonalny warunek czasowy eksperymentu.
 *
 * TimerConfig nie uruchamia ani nie zatrzymuje eksperymentu.
 * Jest wyłącznie konfiguracją warunku zakończenia.
 */
public final class TimerConfig {

    public static final int DEFAULT_DURATION_SECONDS = 60;

    private static final int MIN_DURATION_SECONDS = 5;
    private static final int MAX_DURATION_SECONDS = 3600;

    private final boolean enabled;
    private final int durationSeconds;

    private TimerConfig(
            boolean enabled,
            int durationSeconds
    ) {
        this.enabled = enabled;
        this.durationSeconds =
                normalizeDurationSeconds(durationSeconds);
    }

    public static TimerConfig of(
            boolean enabled,
            int durationSeconds
    ) {
        return new TimerConfig(
                enabled,
                durationSeconds
        );
    }

    public static TimerConfig disabled() {
        return new TimerConfig(
                false,
                DEFAULT_DURATION_SECONDS
        );
    }

    public boolean enabled() {
        return enabled;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public long durationMillis() {
        return durationSeconds * 1000L;
    }

    public static int normalizeDurationSeconds(
            int seconds
    ) {
        return Math.max(
                MIN_DURATION_SECONDS,
                Math.min(
                        MAX_DURATION_SECONDS,
                        seconds
                )
        );
    }
}