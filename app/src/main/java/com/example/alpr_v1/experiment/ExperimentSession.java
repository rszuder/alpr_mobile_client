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

    public static final class Snapshot {

        public final String sessionId;
        public final String state;
        public final String experimentType;
        public final String variant;
        public final String seriesId;
        public final String scenarioId;
        public final int replicateIndex;
        public final String notes;
        public final boolean autoZoomEnabled;
        public final double maxZoomRatio;
        public final boolean thermalStartConditionEnabled;
        public final double maxStartBatteryTemperatureC;
        public final int maxStartThermalStatus;
        public final long thermalStabilizationMillis;

        public final long startedAtMillis;
        public final long finishedAtMillis;
        public final long durationMillis;

        public final String completionReason;
        public final String completionStatus;

        public final boolean timerEnabled;
        public final long timerDurationMillis;

        private Snapshot(
                String sessionId,
                String state,
                String experimentType,
                String variant,
                String seriesId,
                String scenarioId,
                int replicateIndex,
                String notes,
                boolean autoZoomEnabled,
                double maxZoomRatio,
                boolean thermalStartConditionEnabled,
                double maxStartBatteryTemperatureC,
                int maxStartThermalStatus,
                long thermalStabilizationMillis,
                long startedAtMillis,
                long finishedAtMillis,
                long durationMillis,
                String completionReason,
                String completionStatus,
                boolean timerEnabled,
                long timerDurationMillis
        ) {
            this.sessionId = sessionId;
            this.state = state;
            this.experimentType = experimentType;
            this.variant = variant;
            this.seriesId = seriesId;
            this.scenarioId = scenarioId;
            this.replicateIndex = replicateIndex;
            this.notes = notes;
            this.autoZoomEnabled = autoZoomEnabled;
            this.maxZoomRatio = maxZoomRatio;
            this.thermalStartConditionEnabled = thermalStartConditionEnabled;
            this.maxStartBatteryTemperatureC = maxStartBatteryTemperatureC;
            this.maxStartThermalStatus = maxStartThermalStatus;
            this.thermalStabilizationMillis = thermalStabilizationMillis;
            this.startedAtMillis = startedAtMillis;
            this.finishedAtMillis = finishedAtMillis;
            this.durationMillis = durationMillis;
            this.completionReason = completionReason;
            this.completionStatus = completionStatus;
            this.timerEnabled = timerEnabled;
            this.timerDurationMillis = timerDurationMillis;
        }

        public boolean hasSession() {
            return sessionId != null
                    && !sessionId.isEmpty()
                    && !"idle".equals(state);
        }
    }

    private State state = State.IDLE;

    private String sessionId = "";
    private String experimentType = "";
    private String variant = "";
    private ExperimentIdentity identity = ExperimentIdentity.defaults();
    private ThermalConfig thermalConfig = ThermalConfig.disabled();

    private long startedAtMillis = -1L;
    private long finishedAtMillis = -1L;

    private long startedElapsedNanos = -1L;
    private long finishedElapsedNanos = -1L;

    private CompletionReason completionReason;
    private boolean timerEnabled;
    private long timerDurationMillis;

    public synchronized boolean start(
            String experimentType,
            String variant
    ) {
        return start(
                experimentType,
                variant,
                TimerConfig.disabled()
        );
    }


    public synchronized boolean start(
            String experimentType,
            String variant,
            TimerConfig timerConfig
    ) {
        return start(
                experimentType,
                variant,
                timerConfig,
                ExperimentIdentity.defaults()
        );
    }

    public synchronized boolean start(
            String experimentType,
            String variant,
            TimerConfig timerConfig,
            ExperimentIdentity identity
    ) {
        return start(experimentType, variant, timerConfig, ThermalConfig.disabled(), identity);
    }

    public synchronized boolean start(
            String experimentType,
            String variant,
            TimerConfig timerConfig,
            ThermalConfig thermalConfig,
            ExperimentIdentity identity
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

        this.identity = identity == null
                ? ExperimentIdentity.defaults()
                : identity;
        this.thermalConfig = thermalConfig == null
                ? ThermalConfig.disabled()
                : thermalConfig;

        TimerConfig effectiveTimer =
                timerConfig == null
                        ? TimerConfig.disabled()
                        : timerConfig;

        timerEnabled =
                effectiveTimer.enabled();

        timerDurationMillis =
                timerEnabled
                        ? effectiveTimer.durationMillis()
                        : 0L;

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
        identity = ExperimentIdentity.defaults();
        thermalConfig = ThermalConfig.disabled();

        startedAtMillis = -1L;
        finishedAtMillis = -1L;

        startedElapsedNanos = -1L;
        finishedElapsedNanos = -1L;

        completionReason = null;

        timerEnabled = false;
        timerDurationMillis = 0L;
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

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                sessionId,
                stateWireName(),
                experimentType,
                variant,
                identity.seriesId,
                identity.scenarioId,
                identity.replicateIndex,
                identity.notes,
                identity.autoZoomEnabled,
                identity.maxZoomRatio,
                thermalConfig.enabled(),
                thermalConfig.maxBatteryTemperatureC(),
                thermalConfig.maxThermalStatus(),
                thermalConfig.stabilizationMillis(),
                startedAtMillis,
                finishedAtMillis,
                durationMillis(),
                completionReasonWireName(),
                completionStatusWireName(),
                timerEnabled,
                timerDurationMillis
        );
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

    public synchronized String completionStatusWireName() {
        if (state == State.RUNNING) return "running";
        if (state == State.IDLE) return "not_started";
        if (completionReason == CompletionReason.TIMER) return "timer";
        if (completionReason == CompletionReason.ERROR) return "error";
        return "stopped_manual";
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}
