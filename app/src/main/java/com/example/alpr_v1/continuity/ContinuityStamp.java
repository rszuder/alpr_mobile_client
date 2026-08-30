package com.example.alpr_v1.continuity;

/** Immutable generation stamp attached to asynchronous observations and results. */
public final class ContinuityStamp {
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;
    public final long sourceSequence;
    public final long sourceTimestampNanos;
    public final SourceTimestampDomain sourceTimestampDomain;

    public ContinuityStamp(
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            long sourceTimestampNanos
    ) {
        this(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN
        );
    }

    public ContinuityStamp(
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain
    ) {
        this.sceneGeneration = Contracts.nonNegative("sceneGeneration", sceneGeneration);
        this.visualEpoch = Contracts.nonNegative("visualEpoch", visualEpoch);
        this.cameraTransformGeneration = Contracts.nonNegative(
                "cameraTransformGeneration", cameraTransformGeneration
        );
        this.sourceSequence = Contracts.nonNegative(
                "sourceSequence", sourceSequence
        );
        this.sourceTimestampNanos = Contracts.nonNegative(
                "sourceTimestampNanos", sourceTimestampNanos
        );
        this.sourceTimestampDomain = sourceTimestampDomain == null
                ? SourceTimestampDomain.UNKNOWN : sourceTimestampDomain;
    }

    public static ContinuityStamp initial(long sourceTimestampNanos) {
        return new ContinuityStamp(0L, 0L, 0L, sourceTimestampNanos);
    }

    public static ContinuityStamp initial(SourceFrameStamp sourceFrameStamp) {
        if (sourceFrameStamp == null) return initial(0L);
        return sourceFrameStamp.withGenerations(0L, 0L, 0L).continuityStamp();
    }

    public ContinuityStamp withSourceTimestamp(long sourceTimestampNanos) {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain
        );
    }


    public ContinuityStamp withSourceFrame(SourceFrameStamp sourceFrameStamp) {
        if (sourceFrameStamp == null) {
            return new ContinuityStamp(
                    sceneGeneration, visualEpoch, cameraTransformGeneration,
                    0L, 0L, SourceTimestampDomain.UNKNOWN
            );
        }
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceFrameStamp.sourceSequence,
                sourceFrameStamp.sourceTimestampNanos,
                sourceFrameStamp.domain
        );
    }

    public SourceFrameStamp sourceFrameStamp() {
        return new SourceFrameStamp(
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain,
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration
        );
    }
}
