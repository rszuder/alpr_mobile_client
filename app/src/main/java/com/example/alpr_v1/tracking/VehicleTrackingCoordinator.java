package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

import com.example.alpr_v1.domain.VehicleEntityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime owner of vehicle-domain state. Its lifecycle is intentionally longer
 * than the model backends owned by MobileAlprEngine.
 */
public final class VehicleTrackingCoordinator {
    public static final long CONFIDENCE_DECAY_HORIZON_NANOS = 2_500_000_000L;
    public static final long MIN_ADAPTIVE_TRACK_TTL_NANOS = 3_000_000_000L;
    public static final long MAX_ADAPTIVE_TRACK_TTL_NANOS =
            VehicleTrackManager.DEFAULT_ENTITY_TTL_NANOS - 100_000_000L;
    public static final double TRACK_TTL_GAP_MULTIPLIER = 1.75;
    private static final float[] MISSED_UPDATE_PENALTIES = {1f, 0.75f, 0.50f, 0.25f};
    private final VehicleEntityRepository repository;
    private final VehicleTrackManager tracker;
    private long sceneGeneration;
    private long lastMpSourceTimestampNanos;
    private long lastMpObservationGapNanos;
    private VehicleTrackingFrame latestFrame = VehicleTrackingFrame.empty(0L);
    private VehicleTrackingStats lastReportedStats = VehicleTrackingStats.zero();
    private final ArrayDeque<VehicleTrackingEvent> events = new ArrayDeque<>();
    private static final int MAX_PENDING_EVENTS = 128;

    public VehicleTrackingCoordinator() {
        this(new VehicleEntityRepository());
    }

    VehicleTrackingCoordinator(VehicleEntityRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        this.repository = repository;
        this.tracker = new VehicleTrackManager(repository);
        this.tracker.setTrackTtlNanos(MIN_ADAPTIVE_TRACK_TTL_NANOS);
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations
    ) {
        return updateFromMp(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                observations,
                Collections.emptySet(),
                snapshotTimestampNanos
        );
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds
    ) {
        return updateFromMp(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                observations,
                protectedReassociationEntityIds,
                snapshotTimestampNanos
        );
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds,
            long eventRuntimeNanos
    ) {
        return updateFromMp(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                observations,
                protectedReassociationEntityIds,
                eventRuntimeNanos
        );
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            SourceFrameStamp sourceFrameStamp,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds,
            long eventRuntimeNanos
    ) {
        SourceFrameStamp safe = sourceFrameStamp == null
                ? SourceFrameStamp.unknown(sceneGeneration, 0L, 0L)
                : sourceFrameStamp;
        return updateFromMp(
                sourceFrameId,
                safe.sourceSequence,
                safe.sourceTimestampNanos,
                safe.domain,
                snapshotTimestampNanos,
                observations,
                protectedReassociationEntityIds,
                eventRuntimeNanos
        );
    }

    private VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds,
            long eventRuntimeNanos
    ) {
        Map<Long, VehicleCandidate> previousByEntity = candidatesByEntity(
                latestFrame.candidates
        );
        Set<Long> activeBefore = activeEntityIds();
        if (lastMpSourceTimestampNanos > 0L
                && sourceTimestampNanos >= lastMpSourceTimestampNanos) {
            lastMpObservationGapNanos = sourceTimestampNanos - lastMpSourceTimestampNanos;
            tracker.setTrackTtlNanos(adaptiveTrackTtl(lastMpObservationGapNanos));
        }
        lastMpSourceTimestampNanos = Math.max(
                lastMpSourceTimestampNanos, sourceTimestampNanos
        );
        List<VehicleTrackManager.Snapshot> measured = tracker.update(
                observations,
                sourceTimestampNanos,
                protectedReassociationEntityIds
        );
        List<VehicleTrackManager.Snapshot> snapshots = snapshotTimestampNanos
                > sourceTimestampNanos
                ? tracker.projectAfterMeasurement(snapshotTimestampNanos) : measured;
        latestFrame = frame(
                sourceFrameId,
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain,
                snapshotTimestampNanos,
                snapshots
        );
        emitLifecycleEvents(
                previousByEntity,
                activeBefore,
                latestFrame,
                eventRuntimeNanos
        );
        return latestFrame;
    }

    public synchronized VehicleTrackingFrame predict(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos
    ) {
        return predict(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                snapshotTimestampNanos
        );
    }

    public synchronized VehicleTrackingFrame predict(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            long eventRuntimeNanos
    ) {
        Map<Long, VehicleCandidate> previousByEntity = candidatesByEntity(
                latestFrame.candidates
        );
        Set<Long> activeBefore = activeEntityIds();
        List<VehicleTrackManager.Snapshot> snapshots = tracker.predict(
                snapshotTimestampNanos
        );
        latestFrame = frame(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                snapshots
        );
        emitLifecycleEvents(
                previousByEntity,
                activeBefore,
                latestFrame,
                eventRuntimeNanos
        );
        return latestFrame;
    }

    public synchronized VehicleTrackingFrame latestFrame() {
        latestFrame = withCurrentAcquisitionStates(latestFrame);
        return latestFrame;
    }
    public synchronized long lastMpObservationGapNanos() {
        return lastMpObservationGapNanos;
    }
    public synchronized long currentTrackTtlNanos() { return tracker.trackTtlNanos(); }
    public VehicleEntityRepository repository() { return repository; }
    public synchronized VehicleTrackingStats stats() { return tracker.stats(); }

    /** Event counters for one trace; gauges and durations remain current values. */
    public synchronized VehicleTrackingStats statsDelta() {
        VehicleTrackingStats current = tracker.stats();
        VehicleTrackingStats delta = current.deltaSince(lastReportedStats);
        lastReportedStats = current;
        return delta;
    }

    public synchronized void recordEvent(
            String eventType,
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            long frameId,
            long elapsedNanos,
            String reason
    ) {
        addEvent(new VehicleTrackingEvent(
                eventType,
                entityId,
                vehicleTrackId,
                plateTrackId,
                frameId,
                elapsedNanos,
                sceneGeneration,
                reason
        ));
    }

    public synchronized List<VehicleTrackingEvent> drainEvents() {
        List<VehicleTrackingEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    /** Explicit scene boundary; model-engine recreation does not call this method. */
    public synchronized long resetScene() {
        for (com.example.alpr_v1.domain.VehicleEntity entity : repository.activeEntities()) {
            addEvent(new VehicleTrackingEvent(
                    "vehicle_entity_expired",
                    entity.entityId(),
                    entity.vehicleTrackId(),
                    entity.plateTrackId() == null ? 0L : entity.plateTrackId(),
                    latestFrame.sourceFrameId,
                    latestFrame.snapshotTimestampNanos,
                    sceneGeneration,
                    "scene_reset"
            ));
        }
        tracker.resetScene();
        sceneGeneration++;
        lastMpSourceTimestampNanos = 0L;
        lastMpObservationGapNanos = 0L;
        latestFrame = VehicleTrackingFrame.empty(sceneGeneration);
        return sceneGeneration;
    }

    public synchronized long sceneGeneration() { return sceneGeneration; }

    private void emitLifecycleEvents(
            Map<Long, VehicleCandidate> previousByEntity,
            Set<Long> activeBefore,
            VehicleTrackingFrame current,
            long elapsedNanos
    ) {
        Set<Long> currentTrackIds = new HashSet<>();
        for (VehicleCandidate candidate : current.candidates) {
            currentTrackIds.add(candidate.vehicleTrackId);
            VehicleCandidate previous = previousByEntity.get(candidate.entityId);
            if (previous == null || previous.vehicleTrackId != candidate.vehicleTrackId) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_track_created",
                        candidate.entityId,
                        candidate.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        previous == null ? "new_or_returned_track" : "technical_track_changed"
                ));
            }
            if (!activeBefore.contains(candidate.entityId)) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_entity_created",
                        candidate.entityId,
                        candidate.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "new_mp_entity"
                ));
            } else if (previous == null
                    || previous.vehicleTrackId != candidate.vehicleTrackId) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_entity_reassociated",
                        candidate.entityId,
                        candidate.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "track_recovery"
                ));
            }
        }
        for (VehicleCandidate previous : previousByEntity.values()) {
            if (!currentTrackIds.contains(previous.vehicleTrackId)) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_track_expired",
                        previous.entityId,
                        previous.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "track_missing_from_frame"
                ));
            }
        }
        Set<Long> activeAfter = activeEntityIds();
        for (Long entityId : activeBefore) {
            if (!activeAfter.contains(entityId)) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_entity_expired",
                        entityId,
                        0L,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "entity_removed_from_active_repository"
                ));
            }
        }
    }

    private Map<Long, VehicleCandidate> candidatesByEntity(
            List<VehicleCandidate> candidates
    ) {
        Map<Long, VehicleCandidate> result = new HashMap<>();
        for (VehicleCandidate candidate : candidates) {
            result.put(candidate.entityId, candidate);
        }
        return result;
    }

    private Set<Long> activeEntityIds() {
        Set<Long> result = new HashSet<>();
        for (com.example.alpr_v1.domain.VehicleEntity entity : repository.activeEntities()) {
            result.add(entity.entityId());
        }
        return result;
    }

    private void addEvent(VehicleTrackingEvent event) {
        events.addLast(event);
        while (events.size() > MAX_PENDING_EVENTS) events.removeFirst();
    }

    private VehicleTrackingFrame frame(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Snapshot> snapshots
    ) {
        return frame(
                sourceFrameId,
                latestFrame.sourceSequence,
                sourceTimestampNanos,
                latestFrame.sourceTimestampDomain,
                snapshotTimestampNanos,
                snapshots
        );
    }

    private VehicleTrackingFrame frame(
            long sourceFrameId,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Snapshot> snapshots
    ) {
        List<VehicleCandidate> candidates = new ArrayList<>(snapshots.size());
        for (VehicleTrackManager.Snapshot snapshot : snapshots) {
            float effectiveConfidence = effectiveConfidence(snapshot, snapshotTimestampNanos);
            com.example.alpr_v1.domain.VehicleEntity entity = repository.get(
                    snapshot.entityId
            );
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
                    snapshot.sourceIndex,
                    entity == null ? null : entity.acquisitionState()
            ));
        }
        return new VehicleTrackingFrame(
                sourceFrameId,
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain,
                snapshotTimestampNanos,
                sceneGeneration,
                0L,
                0L,
                candidates
        );
    }

    private VehicleTrackingFrame withCurrentAcquisitionStates(
            VehicleTrackingFrame source
    ) {
        List<VehicleCandidate> refreshed = new ArrayList<>(source.candidates.size());
        boolean changed = false;
        for (VehicleCandidate candidate : source.candidates) {
            com.example.alpr_v1.domain.VehicleEntity entity = repository.get(
                    candidate.entityId
            );
            if (entity == null) {
                changed = true;
                continue;
            }
            com.example.alpr_v1.domain.EntityAcquisitionState currentState =
                    entity.acquisitionState();
            if (currentState != candidate.acquisitionState) changed = true;
            refreshed.add(new VehicleCandidate(
                    candidate.entityId,
                    candidate.vehicleTrackId,
                    candidate.bounds,
                    candidate.detectionConfidence,
                    candidate.effectiveConfidence,
                    candidate.exitUrgency,
                    candidate.predicted,
                    candidate.missedUpdates,
                    candidate.lastMeasurementTimestampNanos,
                    candidate.snapshotTimestampNanos,
                    candidate.sourceIndex,
                    currentState
            ));
        }
        if (!changed) return source;
        return new VehicleTrackingFrame(
                source.sourceFrameId,
                source.sourceSequence,
                source.sourceTimestampNanos,
                source.sourceTimestampDomain,
                source.snapshotTimestampNanos,
                source.sceneGeneration,
                source.visualEpoch,
                source.cameraTransformGeneration,
                refreshed
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

    private static long adaptiveTrackTtl(long observationGapNanos) {
        long proposed = (long) Math.ceil(
                Math.max(0L, observationGapNanos) * TRACK_TTL_GAP_MULTIPLIER
        );
        return Math.max(
                MIN_ADAPTIVE_TRACK_TTL_NANOS,
                Math.min(MAX_ADAPTIVE_TRACK_TTL_NANOS, proposed)
        );
    }
}
