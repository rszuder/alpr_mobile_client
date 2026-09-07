package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

/** Reuses one MP measurement only while fresh image evidence supports the same scene. */
public final class StableSceneVehicleCache {
    public static final long CONTROL_INTERVAL_NANOS = 10_000_000_000L;
    private static final long EVIDENCE_MAX_AGE_NANOS = 1_500_000_000L;
    private ContinuityStamp evidenceStamp;
    private long evidenceAt = -1L;
    private long revision;
    private VehicleTrackingFrame measured;
    private long measuredAt;

    public synchronized void observe(ContinuityStamp stamp, boolean stable, long now) {
        if (!stable || !sameScene(evidenceStamp, stamp)
                || now < evidenceAt || now - evidenceAt > EVIDENCE_MAX_AGE_NANOS) {
            invalidate();
        }
        evidenceStamp = stable ? stamp : null;
        evidenceAt = stable ? now : -1L;
    }

    public synchronized long revision() { return revision; }

    public synchronized void record(VehicleTrackingFrame frame, long startedRevision, long now) {
        if (startedRevision != revision || frame == null) return;
        measured = frame;
        measuredAt = now;
    }

    public synchronized VehicleTrackingFrame current(ContinuityStamp stamp, long now) {
        if (measured == null || !sameScene(evidenceStamp, stamp)
                || !sameScene(measured.continuityStamp(), stamp)
                || now < evidenceAt || now - evidenceAt > EVIDENCE_MAX_AGE_NANOS
                || now < measuredAt || now - measuredAt >= CONTROL_INTERVAL_NANOS) return null;
        return measured;
    }

    public synchronized void invalidate() {
        measured = null;
        revision++;
    }

    private static boolean sameScene(ContinuityStamp a, ContinuityStamp b) {
        return a != null && b != null && a.sceneGeneration == b.sceneGeneration
                && a.visualEpoch == b.visualEpoch
                && a.cameraTransformGeneration == b.cameraTransformGeneration;
    }
}
