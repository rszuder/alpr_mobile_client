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
        public final ResearchExecutionConfig frozenExecutionConfig;

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
                long timerDurationMillis,
                ResearchExecutionConfig frozenExecutionConfig
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
            this.frozenExecutionConfig = frozenExecutionConfig;
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
    private ResearchExecutionConfig frozenExecutionConfig;

    private long startedAtMillis = -1L;
    private long finishedAtMillis = -1L;

    private long startedElapsedNanos = -1L;
    private long finishedElapsedNanos = -1L;

    private CompletionReason completionReason;
    private boolean timerEnabled;
    private long timerDurationMillis;

    /** Identity/configuration allocated without starting clocks or the measurement. */
    public static final class Prepared {
        public final String sessionId, experimentType, variant;
        public final TimerConfig timer;
        public final ThermalConfig thermal;
        public final ExperimentIdentity identity;
        public final ResearchExecutionConfig execution;
        public final long createdAtMillis = System.currentTimeMillis();
        private boolean consumed;
        private Prepared(String type, String variant, TimerConfig timer, ThermalConfig thermal,
                         ExperimentIdentity identity, ResearchExecutionConfig execution) {
            sessionId = "exp-" + createdAtMillis + "-" + UUID.randomUUID().toString().substring(0,8);
            experimentType = normalize(type); this.variant = normalize(variant);
            this.timer = timer == null ? TimerConfig.disabled() : timer;
            this.thermal = thermal == null ? ThermalConfig.disabled() : thermal;
            this.identity = identity == null ? ExperimentIdentity.defaults() : identity;
            this.execution = execution;
        }
    }

    public synchronized Prepared prepare(String type, String variant, TimerConfig timer,
                                         ThermalConfig thermal, ExperimentIdentity identity,
                                         ResearchExecutionConfig execution) {
        if (isRunning()) throw new IllegalStateException("Eksperyment już trwa");
        return new Prepared(type,variant,timer,thermal,identity,execution);
    }

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
        return start(
                experimentType,
                variant,
                timerConfig,
                thermalConfig,
                identity,
                null
        );
    }

    public synchronized boolean start(
            String experimentType,
            String variant,
            TimerConfig timerConfig,
            ThermalConfig thermalConfig,
            ExperimentIdentity identity,
            ResearchExecutionConfig frozenExecutionConfig
    ) {
        if (isRunning()) return false;
        return startPrepared(prepare(experimentType,variant,timerConfig,thermalConfig,identity,
                frozenExecutionConfig),System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
    }

    public synchronized boolean startPrepared(Prepared prepared, long nowMillis, long nowElapsedNanos) {
        if (prepared == null || prepared.consumed) throw new IllegalStateException("Nieprawidłowe przygotowanie sesji");
        if (state == State.RUNNING) {
            return false;
        }
        prepared.consumed = true;
        sessionId = prepared.sessionId;
        String experimentType = prepared.experimentType, variant = prepared.variant;
        ExperimentIdentity identity = prepared.identity;
        ThermalConfig thermalConfig = prepared.thermal;
        TimerConfig timerConfig = prepared.timer;
        ResearchExecutionConfig frozenExecutionConfig = prepared.execution;

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
        this.frozenExecutionConfig = frozenExecutionConfig;

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

        startedElapsedNanos = nowElapsedNanos;

        finishedElapsedNanos = -1L;

        completionReason = null;

        state = State.RUNNING;

        return true;
    }

    public synchronized boolean finish(
            CompletionReason reason
    ) {
        return finishAt(reason,System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
    }

    public synchronized boolean finishAt(CompletionReason reason,long wallMillis,long elapsedNanos) {
        if (state != State.RUNNING) {
            return false;
        }

        finishedAtMillis = wallMillis;

        finishedElapsedNanos = elapsedNanos;

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
        frozenExecutionConfig = null;

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

    public synchronized ResearchExecutionConfig frozenExecutionConfig() {
        return frozenExecutionConfig;
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
                timerDurationMillis,
                frozenExecutionConfig
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
