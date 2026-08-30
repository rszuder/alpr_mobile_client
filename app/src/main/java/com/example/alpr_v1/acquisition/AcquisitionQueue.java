package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fair, bounded and deterministic entity-keyed Scan acquisition queue. */
public final class AcquisitionQueue {
    public static final class Selection {
        public final AcquisitionCandidate candidate;
        public final AcquisitionPriorityBreakdown priority;
        public final long queueRevision;

        private Selection(
                AcquisitionCandidate candidate,
                AcquisitionPriorityBreakdown priority,
                long queueRevision
        ) {
            this.candidate = candidate;
            this.priority = priority;
            this.queueRevision = queueRevision;
        }
    }

    private static final class QueueEntry {
        AcquisitionCandidate candidate;

        QueueEntry(AcquisitionCandidate candidate) {
            this.candidate = candidate;
        }
    }

    private final ScanAcquisitionProfile profile;
    private final Map<Long, QueueEntry> entries = new LinkedHashMap<>();
    private final Set<Long> terminalEntityIds = new HashSet<>();
    private long revision;
    private long sceneGeneration;
    private boolean sceneInitialized;
    private long activeEntityId;

    public AcquisitionQueue() {
        this(ScanAcquisitionProfile.DEFAULT);
    }

    public AcquisitionQueue(ScanAcquisitionProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile");
        this.profile = profile;
    }

    public synchronized AcquisitionQueueSnapshot update(
            VehicleTrackingFrame frame,
            long activeEntityId,
            long nowRuntimeNanos
    ) {
        if (frame == null) return snapshot(nowRuntimeNanos);
        long now = nonNegative(nowRuntimeNanos);
        if (!sceneInitialized || sceneGeneration != frame.sceneGeneration) {
            entries.clear();
            this.activeEntityId = 0L;
            sceneGeneration = frame.sceneGeneration;
            sceneInitialized = true;
            revision++;
        }
        this.activeEntityId = Math.max(0L, activeEntityId);

        Set<Long> observed = new HashSet<>();
        boolean changed = false;
        for (VehicleCandidate source : frame.candidates) {
            if (source == null || source.entityId <= 0L) continue;
            observed.add(source.entityId);
            if (source.entityId == this.activeEntityId) {
                QueueEntry active = entries.get(source.entityId);
                if (active != null && eligibleGeometryAndMeasurement(source)) {
                    AcquisitionCandidate previous = active.candidate;
                    AcquisitionCandidate refreshed = updateFromSource(
                            previous,
                            source,
                            now
                    ).withAttemptState(
                            previous.state,
                            previous.mtAttempts,
                            previous.freshMzAttempts,
                            previous.lastAttemptRuntimeNanos,
                            previous.cooldownUntilRuntimeNanos
                    );
                    changed |= differs(previous, refreshed);
                    active.candidate = refreshed;
                }
                continue;
            }
            if (terminalEntityIds.contains(source.entityId)
                    || terminalState(source.acquisitionState)) {
                changed |= entries.remove(source.entityId) != null;
                continue;
            }
            if (!eligibleGeometryAndMeasurement(source)) {
                changed |= entries.remove(source.entityId) != null;
                continue;
            }
            if (source.acquisitionState != EntityAcquisitionState.NEW
                    && source.acquisitionState != EntityAcquisitionState.QUEUED) {
                changed |= entries.remove(source.entityId) != null;
                continue;
            }
            QueueEntry entry = entries.get(source.entityId);
            if (entry == null) {
                entry = new QueueEntry(fromSource(source, now));
                entries.put(source.entityId, entry);
                changed = true;
            } else {
                AcquisitionCandidate updated = updateFromSource(
                        entry.candidate,
                        source,
                        now
                );
                changed |= differs(entry.candidate, updated);
                entry.candidate = updated;
            }
        }

        Iterator<Map.Entry<Long, QueueEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, QueueEntry> entry = iterator.next();
            if (!observed.contains(entry.getKey())) {
                iterator.remove();
                changed = true;
            }
        }
        if (trimToLimit(now)) changed = true;
        if (changed) revision++;
        return snapshot(now);
    }

    public synchronized Selection selectNext(long nowRuntimeNanos) {
        long now = nonNegative(nowRuntimeNanos);
        List<Ranked> ranked = ranked(now, true);
        if (ranked.isEmpty()) return null;
        Ranked selected = ranked.get(0);
        activeEntityId = selected.candidate.entityId;
        revision++;
        return new Selection(selected.candidate, selected.priority, revision);
    }

    public synchronized void recordMtAttempt(long entityId, long nowRuntimeNanos) {
        QueueEntry entry = entries.get(entityId);
        if (entry == null) return;
        AcquisitionCandidate current = entry.candidate;
        entry.candidate = current.withAttemptState(
                EntityAcquisitionState.ACQUIRING,
                current.mtAttempts + 1,
                current.freshMzAttempts,
                nowRuntimeNanos,
                current.cooldownUntilRuntimeNanos
        );
        revision++;
    }

    public synchronized void recordFreshMzAttempt(long entityId, long nowRuntimeNanos) {
        QueueEntry entry = entries.get(entityId);
        if (entry == null) return;
        AcquisitionCandidate current = entry.candidate;
        entry.candidate = current.withAttemptState(
                EntityAcquisitionState.READING_REGISTRATION,
                current.mtAttempts,
                current.freshMzAttempts + 1,
                nowRuntimeNanos,
                current.cooldownUntilRuntimeNanos
        );
        revision++;
    }

    public synchronized void defer(
            long entityId,
            long nowRuntimeNanos,
            long cooldownNanos
    ) {
        QueueEntry entry = entries.get(entityId);
        if (entry == null) {
            if (activeEntityId == entityId) activeEntityId = 0L;
            return;
        }
        AcquisitionCandidate current = entry.candidate;
        long until = saturatedAdd(
                nonNegative(nowRuntimeNanos),
                Math.max(0L, cooldownNanos)
        );
        entry.candidate = current.withAttemptState(
                EntityAcquisitionState.QUEUED,
                current.mtAttempts,
                current.freshMzAttempts,
                nowRuntimeNanos,
                until
        );
        if (activeEntityId == entityId) activeEntityId = 0L;
        revision++;
    }

    public synchronized void complete(long entityId) {
        terminalEntityIds.add(entityId);
        entries.remove(entityId);
        if (activeEntityId == entityId) activeEntityId = 0L;
        revision++;
    }

    public synchronized void expire(long entityId) {
        terminalEntityIds.add(entityId);
        entries.remove(entityId);
        if (activeEntityId == entityId) activeEntityId = 0L;
        revision++;
    }

    public synchronized void releaseActiveWithoutRetry(long entityId) {
        entries.remove(entityId);
        if (activeEntityId == entityId) activeEntityId = 0L;
        revision++;
    }

    public synchronized void hardReset(long nextSceneGeneration) {
        entries.clear();
        activeEntityId = 0L;
        sceneGeneration = Math.max(0L, nextSceneGeneration);
        sceneInitialized = true;
        revision++;
    }

    public synchronized AcquisitionQueueSnapshot snapshot(long nowRuntimeNanos) {
        List<Ranked> ranked = ranked(nonNegative(nowRuntimeNanos), false);
        List<AcquisitionCandidate> candidates = new ArrayList<>(ranked.size());
        Map<Long, AcquisitionPriorityBreakdown> priorities = new LinkedHashMap<>();
        for (Ranked item : ranked) {
            if (item.candidate.entityId == activeEntityId) continue;
            candidates.add(item.candidate);
            priorities.put(item.candidate.entityId, item.priority);
        }
        return new AcquisitionQueueSnapshot(
                revision,
                sceneGeneration,
                activeEntityId,
                candidates,
                priorities
        );
    }

    private List<Ranked> ranked(long now, boolean eligibleOnly) {
        List<Ranked> ranked = new ArrayList<>();
        for (QueueEntry entry : entries.values()) {
            AcquisitionCandidate dynamic = withScores(entry.candidate, now);
            entry.candidate = dynamic;
            AcquisitionPriorityBreakdown priority = priority(dynamic, now);
            if (!eligibleOnly || eligibleForSelection(dynamic, now)) {
                ranked.add(new Ranked(dynamic, priority));
            }
        }
        ranked.sort(RANKING);
        return ranked;
    }

    private boolean trimToLimit(long now) {
        if (entries.size() <= profile.maximumQueueSize) return false;
        List<Ranked> ranked = ranked(now, false);
        Set<Long> keep = new HashSet<>();
        for (int i = 0; i < Math.min(profile.maximumQueueSize, ranked.size()); i++) {
            keep.add(ranked.get(i).candidate.entityId);
        }
        boolean changed = false;
        Iterator<Long> iterator = entries.keySet().iterator();
        while (iterator.hasNext()) {
            if (!keep.contains(iterator.next())) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private AcquisitionCandidate fromSource(VehicleCandidate source, long now) {
        float readability = readability(source.bounds, source.effectiveConfidence);
        float freshness = freshness(source.predicted, source.predictionAgeNanos);
        return new AcquisitionCandidate(
                source.entityId,
                source.vehicleTrackId,
                source.bounds,
                EntityAcquisitionState.QUEUED,
                source.effectiveConfidence,
                source.exitUrgency,
                readability,
                0f,
                freshness,
                1f,
                source.predicted,
                source.predictionAgeNanos,
                0, 0,
                now, 0L, 0L
        );
    }

    private AcquisitionCandidate updateFromSource(
            AcquisitionCandidate current,
            VehicleCandidate source,
            long now
    ) {
        return current.withDynamicScores(
                waitingAge(current, now),
                freshness(source.predicted, source.predictionAgeNanos),
                source.vehicleTrackId,
                source.bounds,
                EntityAcquisitionState.QUEUED,
                source.effectiveConfidence,
                source.exitUrgency,
                readability(source.bounds, source.effectiveConfidence),
                novelty(current),
                source.predicted,
                source.predictionAgeNanos
        );
    }

    private AcquisitionCandidate withScores(AcquisitionCandidate current, long now) {
        return current.withDynamicScores(
                waitingAge(current, now),
                current.freshnessScore,
                current.vehicleTrackId,
                current.bounds,
                current.state,
                current.effectiveConfidence,
                current.exitUrgency,
                current.readabilityScore,
                novelty(current),
                current.predicted,
                current.predictionAgeNanos
        );
    }

    private AcquisitionPriorityBreakdown priority(
            AcquisitionCandidate candidate,
            long now
    ) {
        float center = centerScore(candidate.bounds);
        float predictedPenalty = candidate.predicted
                ? profile.predictionPenalty * (0.5f + 0.5f * predictionRatio(candidate))
                : 0f;
        float recentPenalty = recentAttemptPenalty(candidate, now);
        float cooldownPenalty = now < candidate.cooldownUntilRuntimeNanos
                ? profile.cooldownPenalty : 0f;
        float total = profile.readabilityWeight * candidate.readabilityScore
                + profile.waitingAgeWeight * candidate.waitingAgeScore
                + profile.exitUrgencyWeight * candidate.exitUrgency
                + profile.centerWeight * center
                + profile.freshnessWeight * candidate.freshnessScore
                + profile.noveltyWeight * candidate.noveltyScore
                - predictedPenalty
                - recentPenalty
                - cooldownPenalty;
        return new AcquisitionPriorityBreakdown(
                candidate.readabilityScore,
                candidate.waitingAgeScore,
                candidate.exitUrgency,
                center,
                candidate.freshnessScore,
                candidate.noveltyScore,
                predictedPenalty,
                recentPenalty,
                cooldownPenalty,
                total
        );
    }

    private boolean eligibleGeometryAndMeasurement(VehicleCandidate candidate) {
        return candidate.bounds != null
                && candidate.bounds.valid()
                && candidate.effectiveConfidence >= profile.minimumEffectiveConfidence
                && candidate.predictionAgeNanos <= profile.maximumPredictionAgeNanos;
    }

    private boolean eligibleForSelection(AcquisitionCandidate candidate, long now) {
        return candidate.entityId != activeEntityId
                && !terminalEntityIds.contains(candidate.entityId)
                && (candidate.state == EntityAcquisitionState.NEW
                || candidate.state == EntityAcquisitionState.QUEUED)
                && candidate.effectiveConfidence >= profile.minimumEffectiveConfidence
                && candidate.predictionAgeNanos <= profile.maximumPredictionAgeNanos
                && now >= candidate.cooldownUntilRuntimeNanos
                && candidate.bounds.valid();
    }

    private static boolean terminalState(EntityAcquisitionState state) {
        return state == EntityAcquisitionState.READY_TO_FINALIZE
                || state == EntityAcquisitionState.ACQUIRED
                || state == EntityAcquisitionState.EXPIRED
                || state == EntityAcquisitionState.FAILED;
    }

    private float waitingAge(AcquisitionCandidate candidate, long now) {
        long age = Math.max(0L, now - candidate.firstQueuedRuntimeNanos);
        return clamp01(age / (float) profile.waitingAgeSaturationNanos);
    }

    private float freshness(boolean predicted, long predictionAgeNanos) {
        if (!predicted) return 1f;
        if (profile.maximumPredictionAgeNanos <= 0L) return 0f;
        return clamp01(1f - predictionAgeNanos
                / (float) profile.maximumPredictionAgeNanos);
    }

    private static float novelty(AcquisitionCandidate candidate) {
        int attempts = candidate.mtAttempts + candidate.freshMzAttempts;
        return 1f / (1f + attempts);
    }

    private float predictionRatio(AcquisitionCandidate candidate) {
        if (profile.maximumPredictionAgeNanos <= 0L) return 1f;
        return clamp01(candidate.predictionAgeNanos
                / (float) profile.maximumPredictionAgeNanos);
    }

    private float recentAttemptPenalty(AcquisitionCandidate candidate, long now) {
        if (candidate.lastAttemptRuntimeNanos <= 0L
                || profile.recentAttemptPenaltyNanos <= 0L) return 0f;
        long age = Math.max(0L, now - candidate.lastAttemptRuntimeNanos);
        if (age >= profile.recentAttemptPenaltyNanos) return 0f;
        return profile.recentAttemptPenalty
                * (1f - age / (float) profile.recentAttemptPenaltyNanos);
    }

    private static float readability(NormalizedBounds bounds, float confidence) {
        float areaScore = clamp01((float) Math.sqrt(bounds.area() / 0.16f));
        float aspect = bounds.height() <= 0f ? 0f : bounds.width() / bounds.height();
        float aspectScore = clamp01(1f - Math.abs(aspect - 1.8f) / 1.8f);
        return clamp01(0.55f * confidence + 0.30f * areaScore + 0.15f * aspectScore);
    }

    private static float centerScore(NormalizedBounds bounds) {
        float dx = bounds.centerX() - 0.5f;
        float dy = bounds.centerY() - 0.5f;
        float normalizedDistance = (float) (Math.sqrt(dx * dx + dy * dy) / 0.70710678);
        return clamp01(1f - normalizedDistance);
    }

    private static boolean differs(
            AcquisitionCandidate before,
            AcquisitionCandidate after
    ) {
        return before.vehicleTrackId != after.vehicleTrackId
                || !before.bounds.equals(after.bounds)
                || before.state != after.state
                || Float.compare(before.effectiveConfidence,
                after.effectiveConfidence) != 0
                || Float.compare(before.exitUrgency, after.exitUrgency) != 0
                || before.predicted != after.predicted
                || before.predictionAgeNanos != after.predictionAgeNanos;
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class Ranked {
        final AcquisitionCandidate candidate;
        final AcquisitionPriorityBreakdown priority;

        Ranked(
                AcquisitionCandidate candidate,
                AcquisitionPriorityBreakdown priority
        ) {
            this.candidate = candidate;
            this.priority = priority;
        }
    }

    private static final Comparator<Ranked> RANKING = (left, right) -> {
        int byTotal = Float.compare(right.priority.total, left.priority.total);
        if (byTotal != 0) return byTotal;
        int byQueued = Long.compare(
                left.candidate.firstQueuedRuntimeNanos,
                right.candidate.firstQueuedRuntimeNanos
        );
        if (byQueued != 0) return byQueued;
        return Long.compare(left.candidate.entityId, right.candidate.entityId);
    };
}
