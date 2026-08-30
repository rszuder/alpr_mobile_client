package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable multi-vehicle result tied to one source frame and scene generation. */
public final class VehicleTrackingFrame {
    public final long sourceFrameId;
    public final long sourceSequence;
    public final long sourceTimestampNanos;
    public final SourceTimestampDomain sourceTimestampDomain;
    public final long snapshotTimestampNanos;
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;
    public final List<VehicleCandidate> candidates;

    public VehicleTrackingFrame(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            long sceneGeneration,
            List<VehicleCandidate> candidates
    ) {
        this(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                sceneGeneration,
                0L,
                0L,
                candidates
        );
    }

    public VehicleTrackingFrame(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            List<VehicleCandidate> candidates
    ) {
        this(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                candidates
        );
    }

    public VehicleTrackingFrame(
            long sourceFrameId,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain,
            long snapshotTimestampNanos,
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration,
            List<VehicleCandidate> candidates
    ) {
        if (sourceFrameId < 0L) throw new IllegalArgumentException("sourceFrameId");
        if (sceneGeneration < 0L) throw new IllegalArgumentException("sceneGeneration");
        if (visualEpoch < 0L) throw new IllegalArgumentException("visualEpoch");
        if (cameraTransformGeneration < 0L) {
            throw new IllegalArgumentException("cameraTransformGeneration");
        }
        this.sourceFrameId = sourceFrameId;
        this.sourceSequence = Math.max(0L, sourceSequence);
        this.sourceTimestampNanos = Math.max(0L, sourceTimestampNanos);
        this.sourceTimestampDomain = sourceTimestampDomain == null
                ? SourceTimestampDomain.UNKNOWN : sourceTimestampDomain;
        this.snapshotTimestampNanos = Math.max(
                this.sourceTimestampNanos,
                snapshotTimestampNanos
        );
        this.sceneGeneration = sceneGeneration;
        this.visualEpoch = visualEpoch;
        this.cameraTransformGeneration = cameraTransformGeneration;
        List<VehicleCandidate> safe = candidates == null
                ? Collections.emptyList() : new ArrayList<>(candidates);
        for (VehicleCandidate candidate : safe) {
            if (candidate == null) throw new IllegalArgumentException("null candidate");
        }
        this.candidates = Collections.unmodifiableList(safe);
    }

    public ContinuityStamp continuityStamp() {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain
        );
    }

    public VehicleTrackingFrame withContinuityStamp(ContinuityStamp stamp) {
        if (stamp == null) throw new IllegalArgumentException("stamp");
        return new VehicleTrackingFrame(
                sourceFrameId,
                stamp.sourceSequence,
                stamp.sourceTimestampNanos,
                stamp.sourceTimestampDomain,
                snapshotTimestampNanos,
                stamp.sceneGeneration,
                stamp.visualEpoch,
                stamp.cameraTransformGeneration,
                candidates
        );
    }

    public static VehicleTrackingFrame empty(long sceneGeneration) {
        return new VehicleTrackingFrame(
                0L, 0L, 0L, sceneGeneration, Collections.emptyList()
        );
    }
}
