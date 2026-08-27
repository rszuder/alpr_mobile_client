package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.VehicleEntityRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime owner of vehicle-domain state. Its lifecycle is intentionally longer
 * than the model backends owned by MobileAlprEngine.
 */
public final class VehicleTrackingCoordinator {
    public static final long CONFIDENCE_DECAY_HORIZON_NANOS = 2_500_000_000L;
    private static final float[] MISSED_UPDATE_PENALTIES = {1f, 0.75f, 0.50f, 0.25f};
    private final VehicleEntityRepository repository;
    private final VehicleTrackManager tracker;
    private long sceneGeneration;
    private long lastMpSourceTimestampNanos;
    private long lastMpObservationGapNanos;
    private VehicleTrackingFrame latestFrame = VehicleTrackingFrame.empty(0L);

    public VehicleTrackingCoordinator() {
        this(new VehicleEntityRepository());
    }

    VehicleTrackingCoordinator(VehicleEntityRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        this.repository = repository;
        this.tracker = new VehicleTrackManager(repository);
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations
    ) {
        List<VehicleTrackManager.Snapshot> measured = tracker.update(
                observations,
                sourceTimestampNanos
        );
        List<VehicleTrackManager.Snapshot> snapshots = snapshotTimestampNanos
                > sourceTimestampNanos
                ? tracker.predict(snapshotTimestampNanos) : measured;
        if (lastMpSourceTimestampNanos > 0L
                && sourceTimestampNanos >= lastMpSourceTimestampNanos) {
            lastMpObservationGapNanos = sourceTimestampNanos - lastMpSourceTimestampNanos;
        }
        lastMpSourceTimestampNanos = Math.max(
                lastMpSourceTimestampNanos, sourceTimestampNanos
        );
        latestFrame = frame(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                snapshots
        );
        return latestFrame;
    }

    public synchronized VehicleTrackingFrame predict(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos
    ) {
        List<VehicleTrackManager.Snapshot> snapshots = tracker.predict(
                snapshotTimestampNanos
        );
        latestFrame = frame(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                snapshots
        );
        return latestFrame;
    }

    public synchronized VehicleTrackingFrame latestFrame() { return latestFrame; }
    public synchronized long lastMpObservationGapNanos() {
        return lastMpObservationGapNanos;
    }
    public VehicleEntityRepository repository() { return repository; }

    /** Explicit scene boundary; model-engine recreation does not call this method. */
    public synchronized long resetScene() {
        tracker.resetScene();
        sceneGeneration++;
        lastMpSourceTimestampNanos = 0L;
        lastMpObservationGapNanos = 0L;
        latestFrame = VehicleTrackingFrame.empty(sceneGeneration);
        return sceneGeneration;
    }

    public synchronized long sceneGeneration() { return sceneGeneration; }

    private VehicleTrackingFrame frame(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Snapshot> snapshots
    ) {
        List<VehicleCandidate> candidates = new ArrayList<>(snapshots.size());
        for (VehicleTrackManager.Snapshot snapshot : snapshots) {
            float effectiveConfidence = effectiveConfidence(snapshot, snapshotTimestampNanos);
            candidates.add(new VehicleCandidate(
                    snapshot.entityId,
                    snapshot.vehicleTrackId,
                    snapshot.bounds,
                    snapshot.confidence,
                    effectiveConfidence,
                    snapshot.exitUrgency,
                    snapshot.predicted,
                    snapshot.missedUpdates,
                    snapshot.lastMeasurementTimestampNanos,
                    snapshotTimestampNanos,
                    snapshot.sourceIndex
            ));
        }
        return new VehicleTrackingFrame(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                sceneGeneration,
                candidates
        );
    }

    static float effectiveConfidence(
            VehicleTrackManager.Snapshot snapshot,
            long snapshotTimestampNanos
    ) {
        long ageNanos = Math.max(
                0L, snapshotTimestampNanos - snapshot.lastMeasurementTimestampNanos
        );
        float agePenalty = Math.max(
                0.10f,
                1f - ageNanos / (float) CONFIDENCE_DECAY_HORIZON_NANOS
        );
        int missIndex = Math.min(
                snapshot.missedUpdates, MISSED_UPDATE_PENALTIES.length - 1
        );
        float missedPenalty = MISSED_UPDATE_PENALTIES[missIndex];
        float seconds = ageNanos / 1_000_000_000f;
        float speed = (float) Math.hypot(
                snapshot.motion.velocityX, snapshot.motion.velocityY
        );
        float motionPenalty = 1f / (1f + 1.5f * speed * seconds);
        return clamp01(
                snapshot.confidence * agePenalty * missedPenalty * motionPenalty
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
