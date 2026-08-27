package com.example.alpr_v1.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable multi-vehicle result tied to one source frame and scene generation. */
public final class VehicleTrackingFrame {
    public final long sourceFrameId;
    public final long sourceTimestampNanos;
    public final long snapshotTimestampNanos;
    public final long sceneGeneration;
    public final List<VehicleCandidate> candidates;

    public VehicleTrackingFrame(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            long sceneGeneration,
            List<VehicleCandidate> candidates
    ) {
        if (sourceFrameId < 0L) throw new IllegalArgumentException("sourceFrameId");
        if (sceneGeneration < 0L) throw new IllegalArgumentException("sceneGeneration");
        this.sourceFrameId = sourceFrameId;
        this.sourceTimestampNanos = Math.max(0L, sourceTimestampNanos);
        this.snapshotTimestampNanos = Math.max(
                this.sourceTimestampNanos,
                snapshotTimestampNanos
        );
        this.sceneGeneration = sceneGeneration;
        List<VehicleCandidate> safe = candidates == null
                ? Collections.emptyList() : new ArrayList<>(candidates);
        for (VehicleCandidate candidate : safe) {
            if (candidate == null) throw new IllegalArgumentException("null candidate");
        }
        this.candidates = Collections.unmodifiableList(safe);
    }

    public static VehicleTrackingFrame empty(long sceneGeneration) {
        return new VehicleTrackingFrame(
                0L, 0L, 0L, sceneGeneration, Collections.emptyList()
        );
    }
}
