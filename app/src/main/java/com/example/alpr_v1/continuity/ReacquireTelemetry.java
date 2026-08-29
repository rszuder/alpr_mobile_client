package com.example.alpr_v1.continuity;

/** Immutable research snapshot of the active or most recently completed recovery. */
public final class ReacquireTelemetry {
    public final boolean available;
    public final boolean active;
    public final String result;
    public final VisualChangeClassification triggerClassification;
    public final float triggerCutScore;
    public final float maximumCutScore;
    public final boolean activeTargetPresent;
    public final boolean vehiclePoolRecovered;
    public final boolean deadlineReached;
    public final long startedRuntimeNanos;
    public final long triggerSourceTimestampNanos;

    private ReacquireTelemetry(
            boolean available,
            boolean active,
            String result,
            VisualChangeClassification triggerClassification,
            float triggerCutScore,
            float maximumCutScore,
            boolean activeTargetPresent,
            boolean vehiclePoolRecovered,
            boolean deadlineReached,
            long startedRuntimeNanos,
            long triggerSourceTimestampNanos
    ) {
        this.available = available;
        this.active = active;
        this.result = result == null ? "" : result;
        this.triggerClassification = triggerClassification;
        this.triggerCutScore = Math.max(0f, Math.min(1f, triggerCutScore));
        this.maximumCutScore = Math.max(0f, Math.min(1f, maximumCutScore));
        this.activeTargetPresent = activeTargetPresent;
        this.vehiclePoolRecovered = vehiclePoolRecovered;
        this.deadlineReached = deadlineReached;
        this.startedRuntimeNanos = Math.max(0L, startedRuntimeNanos);
        this.triggerSourceTimestampNanos = Math.max(
                0L, triggerSourceTimestampNanos
        );
    }

    static ReacquireTelemetry none() {
        return new ReacquireTelemetry(
                false, false, "", null,
                0f, 0f, false, false, false, 0L, 0L
        );
    }

    static ReacquireTelemetry from(
            ReacquireContext context,
            boolean active,
            String result,
            boolean vehiclePoolRecovered,
            boolean deadlineReached
    ) {
        if (context == null) return none();
        return new ReacquireTelemetry(
                true,
                active,
                result,
                context.triggerClassification,
                context.triggerCutEvidenceScore,
                context.maximumCutEvidenceDuringRecovery,
                context.activeTargetPresent,
                vehiclePoolRecovered,
                deadlineReached,
                context.startedRuntimeNanos,
                context.triggerSourceTimestampNanos
        );
    }
}
