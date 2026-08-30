package com.example.alpr_v1.continuity;

/** Immutable source identity plus the continuity generations active at observation. */
public final class SourceFrameStamp {
    public final long sourceSequence;
    public final long sourceTimestampNanos;
    public final SourceTimestampDomain domain;
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;

    public SourceFrameStamp(
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain domain,
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration
    ) {
        this.sourceSequence = Contracts.nonNegative(
                "sourceSequence", sourceSequence
        );
        this.sourceTimestampNanos = Contracts.nonNegative(
                "sourceTimestampNanos", sourceTimestampNanos
        );
        this.domain = domain == null ? SourceTimestampDomain.UNKNOWN : domain;
        this.sceneGeneration = Contracts.nonNegative(
                "sceneGeneration", sceneGeneration
        );
        this.visualEpoch = Contracts.nonNegative("visualEpoch", visualEpoch);
        this.cameraTransformGeneration = Contracts.nonNegative(
                "cameraTransformGeneration", cameraTransformGeneration
        );
    }

    public static SourceFrameStamp unknown(
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration
    ) {
        return new SourceFrameStamp(
                0L, 0L, SourceTimestampDomain.UNKNOWN,
                sceneGeneration, visualEpoch, cameraTransformGeneration
        );
    }

    public SourceFrameStamp withGenerations(
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration
    ) {
        return new SourceFrameStamp(
                sourceSequence,
                sourceTimestampNanos,
                domain,
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration
        );
    }

    public SourceFrameStamp inheritedByPreview() {
        return new SourceFrameStamp(
                sourceSequence,
                sourceTimestampNanos,
                sourceSequence > 0L
                        ? SourceTimestampDomain.PREVIEW_INHERITED_CAMERA
                        : SourceTimestampDomain.UNKNOWN,
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration
        );
    }

    public ContinuityStamp continuityStamp() {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceSequence,
                sourceTimestampNanos,
                domain
        );
    }
}
