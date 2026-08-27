package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.VehicleEntityRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime owner of vehicle-domain state. Its lifecycle is intentionally longer
 * than the model backends owned by MobileAlprEngine.
 */
public final class VehicleTrackingCoordinator {
    private final VehicleEntityRepository repository;
    private final VehicleTrackManager tracker;
    private long sceneGeneration;
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
        List<VehicleTrackManager.Snapshot> snapshots = tracker.update(
                observations,
                sourceTimestampNanos
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
    public VehicleEntityRepository repository() { return repository; }

    /** Explicit scene boundary; model-engine recreation does not call this method. */
    public synchronized long resetScene() {
        tracker.resetScene();
        sceneGeneration++;
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
            candidates.add(new VehicleCandidate(
                    snapshot.entityId,
                    snapshot.vehicleTrackId,
                    snapshot.bounds,
                    snapshot.confidence,
                    snapshot.confidence,
                    snapshot.exitUrgency,
                    snapshot.predicted,
                    snapshot.missedUpdates,
                    snapshot.lastMeasurementTimestampNanos,
                    snapshotTimestampNanos
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
}
