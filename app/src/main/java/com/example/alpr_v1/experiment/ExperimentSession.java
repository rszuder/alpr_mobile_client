package com.example.alpr_v1.experiment;

import android.os.SystemClock;

import java.util.Locale;
import java.util.UUID;

/**
 * Reprezentuje jeden konkretny przebieg eksperymentu.
 *
 * Nie steruje kamerą, pipeline'em ani MetricsCollector.
 * Przechowuje wyłącznie tożsamość i cykl życia eksperymentu.
 */
public final class ExperimentSession {

    public enum State {
        IDLE,
        RUNNING,
        FINISHED
    }

    public enum CompletionReason {
        MANUAL,
        TIMER,
        ERROR
    }

    private State state = State.IDLE;

    private String sessionId = "";
    private String experimentType = "";
    private String variant = "";

    private long startedAtMillis = -1L;
    private long finishedAtMillis = -1L;

    private long startedElapsedNanos = -1L;
    private long finishedElapsedNanos = -1L;

    private CompletionReason completionReason;

    public synchronized boolean start(
            String experimentType,
            String variant
    ) {
        if (state == State.RUNNING) {
            return false;
        }

        long nowMillis = System.currentTimeMillis();

        sessionId =
                "exp-"
                        + nowMillis
                        + "-"
                        + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        this.experimentType =
                normalize(experimentType);

        this.variant =
                normalize(variant);

        startedAtMillis = nowMillis;
        finishedAtMillis = -1L;

        startedElapsedNanos =
                SystemClock.elapsedRealtimeNanos();

        finishedElapsedNanos = -1L;

        completionReason = null;

        state = State.RUNNING;

        return true;
    }

    public synchronized boolean finish(
            CompletionReason reason
    ) {
        if (state != State.RUNNING) {
            return false;
        }

        finishedAtMillis =
                System.currentTimeMillis();

        finishedElapsedNanos =
                SystemClock.elapsedRealtimeNanos();

        completionReason =
                reason == null
                        ? CompletionReason.MANUAL
                        : reason;

        state = State.FINISHED;

        return true;
    }

    public synchronized void reset() {
        state = State.IDLE;

        sessionId = "";
        experimentType = "";
        variant = "";

        startedAtMillis = -1L;
        finishedAtMillis = -1L;

        startedElapsedNanos = -1L;
        finishedElapsedNanos = -1L;

        completionReason = null;
    }

    public synchronized boolean isRunning() {
        return state == State.RUNNING;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized String stateWireName() {
        return state.name().toLowerCase(Locale.ROOT);
    }

    public synchronized String sessionId() {
        return sessionId;
    }

    public synchronized String experimentType() {
        return experimentType;
    }

    public synchronized String variant() {
        return variant;
    }

    public synchronized long startedAtMillis() {
        return startedAtMillis;
    }

    public synchronized long finishedAtMillis() {
        return finishedAtMillis;
    }

    public synchronized CompletionReason completionReason() {
        return completionReason;
    }

    public synchronized String completionReasonWireName() {
        if (completionReason == null) {
            return "";
        }

        return completionReason
                .name()
                .toLowerCase(Locale.ROOT);
    }

    public synchronized long durationMillis() {
        if (startedElapsedNanos < 0L) {
            return 0L;
        }

        long endNanos;

        if (state == State.RUNNING) {
            endNanos =
                    SystemClock.elapsedRealtimeNanos();
        } else {
            endNanos =
                    finishedElapsedNanos;
        }

        if (endNanos < startedElapsedNanos) {
            return 0L;
        }

        return (endNanos - startedElapsedNanos)
                / 1_000_000L;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}