package com.example.alpr_v1.continuity;

/** Immutable generation stamp attached to asynchronous observations and results. */
public final class ContinuityStamp {
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;
    public final long sourceTimestampNanos;

    public ContinuityStamp(
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            long sourceTimestampNanos
    ) {
        this.sceneGeneration = Contracts.nonNegative("sceneGeneration", sceneGeneration);
        this.visualEpoch = Contracts.nonNegative("visualEpoch", visualEpoch);
        this.cameraTransformGeneration = Contracts.nonNegative(
                "cameraTransformGeneration", cameraTransformGeneration
        );
        this.sourceTimestampNanos = Contracts.nonNegative(
                "sourceTimestampNanos", sourceTimestampNanos
        );
    }

    public static ContinuityStamp initial(long sourceTimestampNanos) {
        return new ContinuityStamp(0L, 0L, 0L, sourceTimestampNanos);
    }

    public ContinuityStamp withSourceTimestamp(long sourceTimestampNanos) {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceTimestampNanos
        );
    }
}
