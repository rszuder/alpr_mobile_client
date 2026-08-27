package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.MotionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight multi-vehicle tracker used between MP runs.
 * Technical track ids may expire; durable identity belongs to VehicleEntityRepository.
 */
public final class VehicleTrackManager {
    public static final int DEFAULT_MAX_TRACKED_VEHICLES = 16;
    public static final long DEFAULT_TRACK_TTL_NANOS = 1_800_000_000L;
    public static final long DEFAULT_ENTITY_TTL_NANOS = 5_000_000_000L;
    public static final float MIN_ACTIVE_ASSOCIATION_SCORE = 0.36f;
    public static final float MIN_REASSOCIATION_SCORE = 0.50f;
    public static final float MIN_ASSOCIATION_MARGIN = 0.035f;
    private static final float MIN_ACTIVE_RECOVERY_SCORE = 0.46f;

    public static final class Observation {
        public final NormalizedBounds bounds;
        public final float confidence;
        public final AppearanceDescriptor appearance;
        public final int sourceIndex;

        public Observation(
                NormalizedBounds bounds,
                float confidence,
                AppearanceDescriptor appearance,
                int sourceIndex
        ) {
            this.bounds = bounds;
            this.confidence = clamp01(confidence);
            this.appearance = appearance == null
                    ? new AppearanceDescriptor(null) : appearance;
            this.sourceIndex = sourceIndex;
        }
    }

    public static final class Snapshot {
        public final long entityId;
        public final long vehicleTrackId;
        public final NormalizedBounds bounds;
        public final MotionState motion;
        public final float confidence;
        public final float exitUrgency;
        public final long ageNanos;
        public final long lastMeasurementTimestampNanos;
        public final int missedUpdates;
        public final int sourceIndex;
        public final boolean predicted;

        private Snapshot(
                long entityId,
                long vehicleTrackId,
                NormalizedBounds bounds,
                MotionState motion,
                float confidence,
                float exitUrgency,
                long ageNanos,
                long lastMeasurementTimestampNanos,
                int missedUpdates,
                int sourceIndex,
                boolean predicted
        ) {
            this.entityId = entityId;
            this.vehicleTrackId = vehicleTrackId;
            this.bounds = bounds;
            this.motion = motion;
            this.confidence = confidence;
            this.exitUrgency = exitUrgency;
            this.ageNanos = ageNanos;
            this.lastMeasurementTimestampNanos = lastMeasurementTimestampNanos;
            this.missedUpdates = missedUpdates;
            this.sourceIndex = sourceIndex;
            this.predicted = predicted;
        }
    }

    private static final class Track {
        final long trackId;
        final long entityId;
        final BoxKalman kalman = new BoxKalman();
        final long firstSeenNanos;
        long lastSeenNanos;
        float confidence;
        AppearanceDescriptor appearance;
        int missedUpdates;
        int sourceIndex;

        Track(long trackId, long entityId, Observation observation, long nowNanos) {
            this.trackId = trackId;
            this.entityId = entityId;
            this.firstSeenNanos = nowNanos;
            this.lastSeenNanos = nowNanos;
            this.confidence = observation.confidence;
            this.appearance = observation.appearance;
            this.sourceIndex = observation.sourceIndex;
            kalman.correct(observation.bounds, nowNanos);
        }

        NormalizedBounds predicted(long nowNanos) {
            return kalman.estimate(nowNanos);
        }

        MotionState motion() {
            return new MotionState(kalman.velocityX(), kalman.velocityY(), confidence);
        }

        void update(Observation observation, long nowNanos) {
            kalman.correct(observation.bounds, nowNanos);
            confidence = 0.70f * confidence + 0.30f * observation.confidence;
            appearance = blend(appearance, observation.appearance, 0.18f);
            lastSeenNanos = nowNanos;
            missedUpdates = 0;
            sourceIndex = observation.sourceIndex;
        }
    }

    private static final class Assignment {
        final int trackIndex;
        final int observationIndex;
        final float score;

        Assignment(int trackIndex, int observationIndex, float score) {
            this.trackIndex = trackIndex;
            this.observationIndex = observationIndex;
            this.score = score;
        }
    }

    private final VehicleEntityRepository repository;
    private final int maxTrackedVehicles;
    private final long trackTtlNanos;
    private final long entityTtlNanos;
    private final List<Track> tracks = new ArrayList<>();
    private long nextTrackId = 1L;
    private long tracksCreated;
    private long tracksExpired;
    private long entitiesCreated;
    private long entitiesExpired;
    private long entityReassociations;
    private long entityDuplicatePreventions;
    private long observationsUnmatched;
    private long candidatesDroppedCapacity;
    private long trackingNanos;
    private long lastTrackingNanos;

    public VehicleTrackManager(VehicleEntityRepository repository) {
        this(
                repository,
                DEFAULT_MAX_TRACKED_VEHICLES,
                DEFAULT_TRACK_TTL_NANOS,
                DEFAULT_ENTITY_TTL_NANOS
        );
    }

    public VehicleTrackManager(
            VehicleEntityRepository repository,
            int maxTrackedVehicles,
            long trackTtlNanos,
            long entityTtlNanos
    ) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        if (maxTrackedVehicles <= 0) {
            throw new IllegalArgumentException("maxTrackedVehicles must be positive");
        }
        this.repository = repository;
        this.maxTrackedVehicles = maxTrackedVehicles;
        this.trackTtlNanos = Math.max(1L, trackTtlNanos);
        this.entityTtlNanos = Math.max(this.trackTtlNanos, entityTtlNanos);
    }

    public synchronized List<Snapshot> update(
            List<Observation> rawObservations,
            long nowNanos
    ) {
        long trackingStarted = System.nanoTime();
        long safeNow = Math.max(0L, nowNanos);
        tracksExpired += removeExpiredTracks(safeNow);
        List<Observation> observations = validObservations(rawObservations);
        boolean[] usedTracks = new boolean[tracks.size()];
        boolean[] usedObservations = new boolean[observations.size()];

        List<Assignment> assignments = new ArrayList<>();
        float[] bestByObservation = new float[observations.size()];
        float[] secondByObservation = new float[observations.size()];
        java.util.Arrays.fill(bestByObservation, Float.NEGATIVE_INFINITY);
        java.util.Arrays.fill(secondByObservation, Float.NEGATIVE_INFINITY);
        for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
            Track track = tracks.get(trackIndex);
            NormalizedBounds predicted = track.predicted(safeNow);
            for (int observationIndex = 0;
                    observationIndex < observations.size(); observationIndex++) {
                float score = associationScore(
                        predicted,
                        track.appearance,
                        observations.get(observationIndex)
                );
                if (score >= MIN_ACTIVE_ASSOCIATION_SCORE) {
                    assignments.add(new Assignment(trackIndex, observationIndex, score));
                    if (score > bestByObservation[observationIndex]) {
                        secondByObservation[observationIndex] =
                                bestByObservation[observationIndex];
                        bestByObservation[observationIndex] = score;
                    } else if (score > secondByObservation[observationIndex]) {
                        secondByObservation[observationIndex] = score;
                    }
                }
            }
        }
        assignments.removeIf(assignment -> !hasAssociationMargin(
                assignment.score,
                bestByObservation[assignment.observationIndex],
                secondByObservation[assignment.observationIndex]
        ));
        assignments.sort(Comparator.comparingDouble(
                (Assignment assignment) -> assignment.score
        ).reversed());

        for (Assignment assignment : assignments) {
            if (usedTracks[assignment.trackIndex]
                    || usedObservations[assignment.observationIndex]) continue;
            Track track = tracks.get(assignment.trackIndex);
            Observation observation = observations.get(assignment.observationIndex);
            track.update(observation, safeNow);
            repository.updateFromMp(
                    track.trackId,
                    track.predicted(safeNow),
                    track.motion(),
                    track.appearance,
                    safeNow
            );
            usedTracks[assignment.trackIndex] = true;
            usedObservations[assignment.observationIndex] = true;
        }

        recoverUnmatchedActiveTracks(
                observations, usedTracks, usedObservations, safeNow
        );

        for (int index = 0; index < tracks.size(); index++) {
            if (!usedTracks[index]) {
                tracks.get(index).missedUpdates++;
                tracks.get(index).sourceIndex = -1;
            }
        }

        Set<Long> assignedEntityIds = new HashSet<>();
        for (Track track : tracks) assignedEntityIds.add(track.entityId);
        for (int observationIndex = 0;
                observationIndex < observations.size(); observationIndex++) {
            if (usedObservations[observationIndex]) continue;
            observationsUnmatched++;
            if (tracks.size() >= maxTrackedVehicles) {
                candidatesDroppedCapacity++;
                continue;
            }
            Observation observation = observations.get(observationIndex);
            VehicleEntity reassociated = findDormantEntity(
                    observation, assignedEntityIds, safeNow
            );
            long trackId = nextTrackId++;
            VehicleEntity entity;
            if (reassociated != null) {
                entityReassociations++;
                entity = repository.reassignVehicleTrack(
                        reassociated.entityId(),
                        trackId,
                        observation.bounds,
                        MotionState.STATIONARY,
                        observation.appearance,
                        safeNow
                );
            } else {
                entitiesCreated++;
                entity = repository.create(
                        trackId,
                        observation.bounds,
                        observation.appearance,
                        safeNow
                );
            }
            Track track = new Track(trackId, entity.entityId(), observation, safeNow);
            tracksCreated++;
            tracks.add(track);
            assignedEntityIds.add(entity.entityId());
        }

        entitiesExpired += repository.expireOldEntities(safeNow, entityTtlNanos);
        List<Snapshot> result = snapshots(safeNow);
        lastTrackingNanos = Math.max(0L, System.nanoTime() - trackingStarted);
        trackingNanos += lastTrackingNanos;
        return result;
    }

    /** Returns time-projected tracks without treating the projection as an MP observation. */
    public synchronized List<Snapshot> predict(long nowNanos) {
        long trackingStarted = System.nanoTime();
        long safeNow = Math.max(0L, nowNanos);
        tracksExpired += removeExpiredTracks(safeNow);
        entitiesExpired += repository.expireOldEntities(safeNow, entityTtlNanos);
        List<Snapshot> result = snapshots(safeNow);
        lastTrackingNanos = Math.max(0L, System.nanoTime() - trackingStarted);
        trackingNanos += lastTrackingNanos;
        return result;
    }

    public synchronized void resetScene() {
        tracks.clear();
        nextTrackId = 1L;
        repository.resetScene();
    }

    public synchronized int trackedCount() { return tracks.size(); }
    public VehicleEntityRepository repository() { return repository; }

    public synchronized VehicleTrackingStats stats() {
        return new VehicleTrackingStats(
                tracksCreated,
                tracksExpired,
                entitiesCreated,
                entitiesExpired,
                entityReassociations,
                entityDuplicatePreventions,
                observationsUnmatched,
                candidatesDroppedCapacity,
                trackingNanos,
                lastTrackingNanos
        );
    }

    private int removeExpiredTracks(long nowNanos) {
        int before = tracks.size();
        tracks.removeIf(track -> nowNanos - track.lastSeenNanos > trackTtlNanos
                || repository.get(track.entityId) == null);
        return before - tracks.size();
    }

    private VehicleEntity findDormantEntity(
            Observation observation,
            Set<Long> assignedEntityIds,
            long nowNanos
    ) {
        VehicleEntity best = null;
        float bestScore = MIN_REASSOCIATION_SCORE;
        for (VehicleEntity entity : repository.activeEntities()) {
            if (assignedEntityIds.contains(entity.entityId())
                    || entity.acquisitionState() == EntityAcquisitionState.EXPIRED
                    || nowNanos - entity.lastSeenNanos() > entityTtlNanos
                    || entity.vehicleBounds() == null) continue;
            NormalizedBounds predicted = predictEntity(entity, nowNanos);
            float score = associationScore(
                    predicted,
                    entity.vehicleAppearance(),
                    observation
            );
            float appearance = similarity(entity.vehicleAppearance(), observation.appearance);
            if (entity.vehicleAppearance().available()
                    && observation.appearance.available()
                    && appearance < 0.55f) continue;
            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    private static NormalizedBounds predictEntity(VehicleEntity entity, long nowNanos) {
        NormalizedBounds bounds = entity.vehicleBounds();
        MotionState motion = entity.motion();
        float seconds = Math.max(0f, Math.min(
                2f,
                (nowNanos - entity.lastSeenNanos()) / 1_000_000_000f
        ));
        float dx = motion.velocityX * seconds;
        float dy = motion.velocityY * seconds;
        return new NormalizedBounds(
                bounds.left + dx,
                bounds.top + dy,
                bounds.right + dx,
                bounds.bottom + dy
        );
    }

    private List<Snapshot> snapshots(long nowNanos) {
        List<Snapshot> result = new ArrayList<>(tracks.size());
        for (Track track : tracks) {
            NormalizedBounds bounds = track.predicted(nowNanos);
            MotionState motion = track.motion();
            result.add(new Snapshot(
                    track.entityId,
                    track.trackId,
                    bounds,
                    motion,
                    track.confidence,
                    exitUrgency(bounds, motion),
                    Math.max(0L, nowNanos - track.firstSeenNanos),
                    track.lastSeenNanos,
                    track.missedUpdates,
                    track.sourceIndex,
                    track.sourceIndex < 0 || nowNanos > track.lastSeenNanos
            ));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Observation> validObservations(List<Observation> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<Observation> valid = new ArrayList<>();
        for (Observation observation : raw) {
            if (observation != null
                    && observation.bounds != null
                    && observation.bounds.valid()) valid.add(observation);
        }
        return valid;
    }

    private static float associationScore(
            NormalizedBounds predicted,
            AppearanceDescriptor stableAppearance,
            Observation observation
    ) {
        float overlap = predicted.iou(observation.bounds);
        float distance = (float) Math.hypot(
                predicted.centerX() - observation.bounds.centerX(),
                predicted.centerY() - observation.bounds.centerY()
        );
        float scale = Math.max(0.05f, 0.5f * (
                diagonal(predicted) + diagonal(observation.bounds)
        ));
        float sizeSimilarity = Math.min(
                ratio(predicted.width(), observation.bounds.width()),
                ratio(predicted.height(), observation.bounds.height())
        );
        if (sizeSimilarity < 0.38f) return -1f;
        if (overlap < 0.04f && distance > scale * 0.85f) return -1f;
        float proximity = clamp01(1f - distance / Math.max(0.001f, scale));
        boolean hasAppearance = stableAppearance != null
                && stableAppearance.available()
                && observation.appearance.available();
        float rawAppearanceSimilarity = hasAppearance
                ? similarity(stableAppearance, observation.appearance) : 0f;
        float appearance = hasAppearance
                ? 0.5f + 0.5f * rawAppearanceSimilarity
                : 0.5f;
        if (hasAppearance && rawAppearanceSimilarity < 0.20f) return -1f;
        if (hasAppearance && rawAppearanceSimilarity < 0.35f && overlap < 0.55f) {
            return -1f;
        }
        return 0.52f * overlap
                + 0.23f * proximity
                + 0.15f * sizeSimilarity
                + 0.08f * appearance
                + 0.02f * observation.confidence;
    }

    private void recoverUnmatchedActiveTracks(
            List<Observation> observations,
            boolean[] usedTracks,
            boolean[] usedObservations,
            long nowNanos
    ) {
        List<Assignment> recovery = new ArrayList<>();
        float[] bestByObservation = new float[observations.size()];
        float[] secondByObservation = new float[observations.size()];
        java.util.Arrays.fill(bestByObservation, Float.NEGATIVE_INFINITY);
        java.util.Arrays.fill(secondByObservation, Float.NEGATIVE_INFINITY);
        for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
            if (usedTracks[trackIndex]) continue;
            Track track = tracks.get(trackIndex);
            NormalizedBounds predicted = track.predicted(nowNanos);
            for (int observationIndex = 0;
                    observationIndex < observations.size(); observationIndex++) {
                if (usedObservations[observationIndex]) continue;
                float score = recoveryAssociationScore(
                        predicted, track.appearance, observations.get(observationIndex)
                );
                if (score < MIN_ACTIVE_RECOVERY_SCORE) continue;
                recovery.add(new Assignment(trackIndex, observationIndex, score));
                if (score > bestByObservation[observationIndex]) {
                    secondByObservation[observationIndex] =
                            bestByObservation[observationIndex];
                    bestByObservation[observationIndex] = score;
                } else if (score > secondByObservation[observationIndex]) {
                    secondByObservation[observationIndex] = score;
                }
            }
        }
        recovery.sort(Comparator.comparingDouble(
                (Assignment assignment) -> assignment.score
        ).reversed());
        for (Assignment assignment : recovery) {
            if (usedTracks[assignment.trackIndex]
                    || usedObservations[assignment.observationIndex]
                    || !hasAssociationMargin(
                            assignment.score,
                            bestByObservation[assignment.observationIndex],
                            secondByObservation[assignment.observationIndex]
                    )) continue;
            Track track = tracks.get(assignment.trackIndex);
            Observation observation = observations.get(assignment.observationIndex);
            track.update(observation, nowNanos);
            repository.updateFromMp(
                    track.trackId,
                    track.predicted(nowNanos),
                    track.motion(),
                    track.appearance,
                    nowNanos
            );
            usedTracks[assignment.trackIndex] = true;
            usedObservations[assignment.observationIndex] = true;
            entityDuplicatePreventions++;
        }
    }

    private static float recoveryAssociationScore(
            NormalizedBounds predicted,
            AppearanceDescriptor stableAppearance,
            Observation observation
    ) {
        float appearance = stableAppearance == null
                ? 0f : stableAppearance.cosineSimilarity(observation.appearance);
        if (stableAppearance == null || !stableAppearance.available()
                || !observation.appearance.available()
                || appearance < 0.72f) return -1f;
        float distance = (float) Math.hypot(
                predicted.centerX() - observation.bounds.centerX(),
                predicted.centerY() - observation.bounds.centerY()
        );
        float scale = Math.max(0.05f, 0.5f * (
                diagonal(predicted) + diagonal(observation.bounds)
        ));
        if (distance > scale * 1.65f) return -1f;
        float sizeSimilarity = Math.min(
                ratio(predicted.width(), observation.bounds.width()),
                ratio(predicted.height(), observation.bounds.height())
        );
        if (sizeSimilarity < 0.32f) return -1f;
        float proximity = clamp01(1f - distance / (scale * 1.65f));
        return 0.62f * (0.5f + 0.5f * appearance)
                + 0.23f * proximity
                + 0.15f * sizeSimilarity;
    }

    private static boolean hasAssociationMargin(
            float score,
            float bestScore,
            float secondScore
    ) {
        if (score < bestScore) return false;
        return secondScore == Float.NEGATIVE_INFINITY
                || bestScore - secondScore >= MIN_ASSOCIATION_MARGIN;
    }

    static float exitUrgency(NormalizedBounds bounds, MotionState motion) {
        float speed = (float) Math.hypot(motion.velocityX, motion.velocityY);
        if (speed < 0.005f) return 0f;
        float timeX = Float.POSITIVE_INFINITY;
        float timeY = Float.POSITIVE_INFINITY;
        if (motion.velocityX > 0f) timeX = (1f - bounds.right) / motion.velocityX;
        else if (motion.velocityX < 0f) timeX = bounds.left / -motion.velocityX;
        if (motion.velocityY > 0f) timeY = (1f - bounds.bottom) / motion.velocityY;
        else if (motion.velocityY < 0f) timeY = bounds.top / -motion.velocityY;
        float secondsToExit = Math.max(0f, Math.min(timeX, timeY));
        return clamp01(1f - secondsToExit / 2.5f);
    }

    private static float similarity(AppearanceDescriptor first, AppearanceDescriptor second) {
        if (first == null || second == null) return 0f;
        float[] left = first.values();
        float[] right = second.values();
        if (left.length == 0 || left.length != right.length) return 0f;
        float dot = 0f;
        float leftEnergy = 0f;
        float rightEnergy = 0f;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftEnergy += left[index] * left[index];
            rightEnergy += right[index] * right[index];
        }
        float denominator = (float) Math.sqrt(leftEnergy * rightEnergy);
        return denominator <= 1e-6f ? 0f : Math.max(-1f, Math.min(1f, dot / denominator));
    }

    private static AppearanceDescriptor blend(
            AppearanceDescriptor stable,
            AppearanceDescriptor fresh,
            float freshWeight
    ) {
        if (fresh == null || !fresh.available()) return stable;
        if (stable == null || !stable.available()) return fresh;
        float[] left = stable.values();
        float[] right = fresh.values();
        if (left.length != right.length) return fresh;
        float weight = clamp01(freshWeight);
        float[] values = new float[left.length];
        float energy = 0f;
        for (int index = 0; index < values.length; index++) {
            values[index] = (1f - weight) * left[index] + weight * right[index];
            energy += values[index] * values[index];
        }
        float norm = (float) Math.sqrt(Math.max(1e-6f, energy));
        for (int index = 0; index < values.length; index++) values[index] /= norm;
        return new AppearanceDescriptor(values);
    }

    private static float diagonal(NormalizedBounds bounds) {
        return (float) Math.hypot(bounds.width(), bounds.height());
    }

    private static float ratio(float first, float second) {
        float maximum = Math.max(first, second);
        return maximum <= 0f ? 0f : Math.min(first, second) / maximum;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class BoxKalman {
        private final Axis centerX = new Axis(0.012f, 0.010f);
        private final Axis centerY = new Axis(0.012f, 0.010f);
        private final Axis width = new Axis(0.006f, 0.015f);
        private final Axis height = new Axis(0.006f, 0.015f);
        private boolean initialized;
        private long stateNanos;

        void correct(NormalizedBounds measurement, long nowNanos) {
            if (!initialized) {
                centerX.initialize(measurement.centerX());
                centerY.initialize(measurement.centerY());
                width.initialize(measurement.width());
                height.initialize(measurement.height());
                stateNanos = nowNanos;
                initialized = true;
                return;
            }
            float dt = seconds(stateNanos, nowNanos);
            centerX.predict(dt);
            centerY.predict(dt);
            width.predict(dt);
            height.predict(dt);
            centerX.correct(measurement.centerX());
            centerY.correct(measurement.centerY());
            width.correct(measurement.width());
            height.correct(measurement.height());
            stateNanos = Math.max(stateNanos, nowNanos);
        }

        NormalizedBounds estimate(long nowNanos) {
            float dt = seconds(stateNanos, nowNanos);
            float cx = centerX.estimate(dt);
            float cy = centerY.estimate(dt);
            float w = Math.max(0.001f, width.estimate(dt));
            float h = Math.max(0.001f, height.estimate(dt));
            return new NormalizedBounds(
                    cx - w * 0.5f,
                    cy - h * 0.5f,
                    cx + w * 0.5f,
                    cy + h * 0.5f
            );
        }

        float velocityX() { return centerX.velocity; }
        float velocityY() { return centerY.velocity; }

        private static float seconds(long from, long to) {
            if (to <= from) return 0f;
            return Math.max(0f, Math.min(1f, (to - from) / 1_000_000_000f));
        }
    }

    private static final class Axis {
        final float processNoise;
        final float measurementNoise;
        float position;
        float velocity;
        float p00;
        float p01;
        float p10;
        float p11;

        Axis(float processNoise, float measurementNoise) {
            this.processNoise = processNoise;
            this.measurementNoise = measurementNoise;
        }

        void initialize(float value) {
            position = value;
            velocity = 0f;
            p00 = 0.08f;
            p11 = 0.12f;
        }

        void predict(float dt) {
            position += velocity * dt;
            float dt2 = dt * dt;
            float nextP00 = p00 + dt * (p01 + p10) + dt2 * p11
                    + processNoise * dt2;
            float nextP01 = p01 + dt * p11;
            float nextP10 = p10 + dt * p11;
            float nextP11 = p11 + processNoise * Math.max(0.01f, dt);
            p00 = nextP00;
            p01 = nextP01;
            p10 = nextP10;
            p11 = nextP11;
        }

        void correct(float measurement) {
            float innovation = measurement - position;
            float denominator = Math.max(1e-6f, p00 + measurementNoise);
            float gainPosition = p00 / denominator;
            float gainVelocity = p10 / denominator;
            float oldP00 = p00;
            float oldP01 = p01;
            position += gainPosition * innovation;
            velocity += gainVelocity * innovation;
            p00 -= gainPosition * oldP00;
            p01 -= gainPosition * oldP01;
            p10 -= gainVelocity * oldP00;
            p11 -= gainVelocity * oldP01;
            velocity = Math.max(-1.5f, Math.min(1.5f, velocity));
        }

        float estimate(float dt) { return position + velocity * dt; }
    }
}
