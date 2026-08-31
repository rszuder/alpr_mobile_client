package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.ContinuityStamp;

/** Wersjonuje referencję globalnego motion wyłącznie trzema generacjami sceny. */
public final class PreviewMotionGenerationGate {
    private long sceneGeneration = -1L;
    private long visualEpoch = -1L;
    private long cameraTransformGeneration = -1L;

    /** Zwraca true, gdy przed przyjęciem klatki trzeba wyzerować motion state. */
    public synchronized boolean enter(ContinuityStamp stamp) {
        if (stamp == null) return false;
        boolean changed = sceneGeneration != stamp.sceneGeneration
                || visualEpoch != stamp.visualEpoch
                || cameraTransformGeneration != stamp.cameraTransformGeneration;
        sceneGeneration = stamp.sceneGeneration;
        visualEpoch = stamp.visualEpoch;
        cameraTransformGeneration = stamp.cameraTransformGeneration;
        return changed;
    }

    public synchronized void reset() {
        sceneGeneration = -1L;
        visualEpoch = -1L;
        cameraTransformGeneration = -1L;
    }
}
