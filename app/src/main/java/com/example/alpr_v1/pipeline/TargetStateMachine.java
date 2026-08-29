package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.PreviewTrackingFrame;
import com.example.alpr_v1.tracking.TrackedPlate;
import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Testable sticky-target state machine independent from Activity. */
public final class TargetStateMachine {
    public static final float QUALITY_TRACKING = 0.75f;
    public static final float QUALITY_DEGRADED = 0.45f;
    public static final int UPDATES_TO_LOCK = 3;

    private final TargetSelector targetSelector;
    private TargetSnapshot snapshot = TargetSnapshot.searching();
    private int stableUpdates;
    private long lockedTrackId;
    private long lastLockedTrackId;
    private int lockSwitches;
    private int lockLosses;
    private int acquisitionFrames;
    private long acquisitionStartedAtNanos;
    private int framesToLock;
    private long timeToLockMillis;
    private long lockRevision;
    private int lockReassociations;
    private float[] currentAppearance;
    private float[] lockedAppearance;

    public TargetStateMachine() {
        this(new TargetSelector());
    }

    TargetStateMachine(TargetSelector targetSelector) {
        this.targetSelector = targetSelector == null
                ? new TargetSelector() : targetSelector;
    }

    public synchronized TargetSnapshot snapshot() {
        return snapshot;
    }

    public synchronized TargetSnapshot onMtAnchor(List<OverlayItem> items) {
        return onMtAnchor(items, Collections.emptyMap());
    }

    public synchronized TargetSnapshot onMtAnchor(
            List<OverlayItem> items,
            Map<Long, float[]> appearanceByTrack
    ) {
        List<OverlayItem> plates = plateOverlays(items);
        List<TargetSelector.Candidate> candidates = overlayCandidates(
                plates,
                appearanceByTrack
        );
        TargetSelector.Selection selection = targetSelector.select(
                candidates,
                snapshot.trackId,
                lockedTrackId > 0L
        );
        boolean reassociated = false;
        if (!selection.found()) {
            if (lockedTrackId <= 0L) return snapshot;
            TargetSelector.Association association = targetSelector.associate(
                    new TargetSelector.Reference(
                            snapshot.normalizedBounds.left,
                            snapshot.normalizedBounds.top,
                            snapshot.normalizedBounds.right,
                            snapshot.normalizedBounds.bottom,
                            lockedAppearance
                    ),
                    candidates
            );
            if (!association.matched()) {
                return degradePreservingTarget(
                        "mt_anchor_locked_target_missing_" + association.reason
                );
            }
            long previousTrackId = lockedTrackId;
            lockedTrackId = association.candidate.trackId;
            lastLockedTrackId = lockedTrackId;
            lockReassociations++;
            reassociated = previousTrackId != lockedTrackId;
            selection = targetSelector.select(
                    candidates,
                    lockedTrackId,
                    true
            );
        }

        OverlayItem selected = overlayByTrackId(
                plates,
                selection.candidate.trackId
        );
        if (selected == null) return snapshot;

        long now = System.nanoTime();
        boolean targetChanged = snapshot.trackId != selected.trackId;
        if (targetChanged && !reassociated) beginAcquisition(selected.trackId, now);
        float[] freshAppearance = appearanceByTrack == null
                ? null : appearanceByTrack.get(selected.trackId);
        boolean localAppearanceValidated = lockedAppearance != null
                && freshAppearance != null
                && lockedAppearance.length == freshAppearance.length;
        float localAppearanceSimilarity = localAppearanceValidated
                ? Math.max(0f, PlateAppearanceDescriptor.similarity(
                        lockedAppearance,
                        freshAppearance
                ))
                : 0f;
        updateAppearance(
                freshAppearance,
                targetChanged && !reassociated
        );
        acquisitionFrames++;
        stableUpdates++;

        TargetSnapshot.State state;
        String reason;
        if (lockedTrackId == selected.trackId) {
            stableUpdates = Math.max(UPDATES_TO_LOCK, stableUpdates);
            state = TargetSnapshot.State.LOCKED;
            reason = reassociated
                    ? "lock_reassociated_appearance_geometry"
                    : "mt_reanchor_locked_target";
        } else if (stableUpdates >= UPDATES_TO_LOCK) {
            acquireLock(selected.trackId, now);
            state = TargetSnapshot.State.LOCKED;
            reason = "lock_acquired_mt";
        } else {
            state = TargetSnapshot.State.ACQUIRED;
            reason = targetChanged
                    ? "target_acquired_mt" : "target_confirmed_mt";
        }

        snapshot = snapshot(
                state,
                selected.trackId,
                selected,
                1f,
                0f,
                1f,
                4,
                0,
                0,
                0,
                reason,
                now
        ).withLocalAppearance(
                localAppearanceSimilarity,
                localAppearanceValidated
        );
        return snapshot;
    }

    public synchronized TargetSnapshot onTrackingFrame(PreviewTrackingFrame frame) {
        List<TrackedPlate> plates = frame == null
                ? Collections.emptyList() : frame.trackedPlates;
        TargetSelector.Selection selection = targetSelector.select(
                trackingCandidates(plates),
                snapshot.trackId,
                lockedTrackId > 0L
        );
        if (!selection.found()) {
            return onTrackingLost(
                    lockedTrackId > 0L
                            ? "locked_target_missing" : "tracking_candidate_missing"
            );
        }

        TrackedPlate selected = trackedById(
                plates,
                selection.candidate.trackId
        );
        if (selected == null) return onTrackingLost("tracking_candidate_missing");

        long now = selected.updatedAtNanos > 0L
                ? selected.updatedAtNanos : System.nanoTime();
        boolean targetChanged = snapshot.trackId != selected.trackId;
        if (targetChanged) beginAcquisition(selected.trackId, now);
        acquisitionFrames++;

        TargetSnapshot.State state;
        String reason;
        if (selected.trackingQuality < QUALITY_DEGRADED) {
            if (lockedTrackId == 0L) stableUpdates = 0;
            state = TargetSnapshot.State.DEGRADED;
            reason = "tracking_quality_invalid";
        } else if (selected.trackingQuality < QUALITY_TRACKING
                || selected.consecutiveFailures > 0) {
            if (lockedTrackId == 0L) {
                stableUpdates = Math.max(0, stableUpdates - 1);
            }
            state = TargetSnapshot.State.DEGRADED;
            reason = selected.consecutiveFailures > 0
                    ? "tracking_failure" : "tracking_quality_degraded";
        } else if (lockedTrackId == selected.trackId) {
            stableUpdates = Math.max(UPDATES_TO_LOCK, stableUpdates + 1);
            state = TargetSnapshot.State.LOCKED;
            reason = "locked_target_tracked";
        } else {
            stableUpdates++;
            if (stableUpdates >= UPDATES_TO_LOCK) {
                acquireLock(selected.trackId, now);
                state = TargetSnapshot.State.LOCKED;
                reason = "lock_acquired_tracking";
            } else {
                state = TargetSnapshot.State.TRACKING;
                reason = targetChanged
                        ? "target_selected_tracking" : "target_tracking";
            }
        }

        boolean lockedAppearanceValidated = lockedAppearance != null
                && selected.localAppearanceDescriptor != null
                && lockedAppearance.length == selected.localAppearanceDescriptor.length;
        float lockedAppearanceSimilarity = lockedAppearanceValidated
                ? Math.max(0f, PlateAppearanceDescriptor.similarity(
                        lockedAppearance,
                        selected.localAppearanceDescriptor
                ))
                : selected.localAppearanceSimilarity;
        snapshot = snapshot(
                state,
                selected.trackId,
                selected.overlayItem,
                selected.trackingQuality,
                1f - selected.trackingQuality,
                selected.supportRatio,
                selected.trackerInliers,
                selected.consecutiveFailures,
                selected.ageFrames,
                selected.framesSinceMtAnchor,
                reason,
                now
        ).withLocalAppearance(
                lockedAppearanceSimilarity,
                lockedAppearanceValidated || selected.localAppearanceValidated
        );
        return snapshot;
    }

    public synchronized TargetSnapshot onTrackingLost() {
        return onTrackingLost("tracking_lost");
    }

    public synchronized TargetSnapshot reset() {
        stableUpdates = 0;
        lockedTrackId = 0L;
        lastLockedTrackId = 0L;
        lockSwitches = 0;
        lockLosses = 0;
        acquisitionFrames = 0;
        acquisitionStartedAtNanos = 0L;
        framesToLock = 0;
        timeToLockMillis = 0L;
        lockRevision = 0L;
        lockReassociations = 0;
        currentAppearance = null;
        lockedAppearance = null;
        snapshot = TargetSnapshot.searching();
        return snapshot;
    }

    private TargetSnapshot onTrackingLost(String reason) {
        if (snapshot.trackId <= 0L) {
            snapshot = TargetSnapshot.searching();
            return snapshot;
        }
        if (lockedTrackId > 0L) {
            lockLosses++;
            lockedTrackId = 0L;
            currentAppearance = cloneDescriptor(lockedAppearance);
            lockedAppearance = null;
        }
        stableUpdates = 0;
        acquisitionFrames = 0;
        acquisitionStartedAtNanos = 0L;
        snapshot = snapshot(
                TargetSnapshot.State.LOST,
                snapshot.trackId,
                snapshot.overlayItem,
                0f,
                1f,
                0f,
                0,
                snapshot.consecutiveFailures + 1,
                snapshot.ageFrames,
                snapshot.framesSinceMtAnchor + 1,
                reason,
                System.nanoTime()
        );
        return snapshot;
    }

    private TargetSnapshot degradePreservingTarget(String reason) {
        snapshot = snapshot(
                TargetSnapshot.State.DEGRADED,
                snapshot.trackId,
                snapshot.overlayItem,
                Math.min(snapshot.trackingQuality, QUALITY_TRACKING - 0.01f),
                Math.max(snapshot.driftScore, 0.35f),
                snapshot.supportRatio,
                snapshot.trackerInliers,
                snapshot.consecutiveFailures + 1,
                snapshot.ageFrames,
                snapshot.framesSinceMtAnchor + 1,
                reason,
                System.nanoTime()
        );
        return snapshot;
    }

    private void beginAcquisition(long trackId, long now) {
        stableUpdates = 0;
        acquisitionFrames = 0;
        acquisitionStartedAtNanos = now;
        if (lockedTrackId > 0L && lockedTrackId != trackId) {
            // Defensive guard: the selector must never permit this path.
            return;
        }
    }

    private void acquireLock(long trackId, long now) {
        if (lastLockedTrackId > 0L && lastLockedTrackId != trackId) {
            lockSwitches++;
        }
        lockedTrackId = trackId;
        lastLockedTrackId = trackId;
        framesToLock = Math.max(1, acquisitionFrames);
        long started = acquisitionStartedAtNanos > 0L
                ? acquisitionStartedAtNanos : now;
        timeToLockMillis = Math.max(0L, (now - started) / 1_000_000L);
        lockRevision++;
        lockedAppearance = cloneDescriptor(currentAppearance);
    }

    private void updateAppearance(float[] fresh, boolean replace) {
        if (fresh == null) return;
        if (lockedTrackId > 0L) {
            if (lockedAppearance == null) {
                lockedAppearance = fresh.clone();
                currentAppearance = fresh.clone();
                return;
            }
            float similarity = PlateAppearanceDescriptor.similarity(
                    lockedAppearance,
                    fresh
            );
            if (similarity < 0.45f) {
                return;
            }
            lockedAppearance = PlateAppearanceDescriptor.blend(
                    lockedAppearance,
                    fresh,
                    0.08f
            );
            currentAppearance = cloneDescriptor(lockedAppearance);
            return;
        }
        currentAppearance = replace || currentAppearance == null
                ? fresh.clone()
                : PlateAppearanceDescriptor.blend(currentAppearance, fresh, 0.16f);
    }

    private TargetSnapshot snapshot(
            TargetSnapshot.State state,
            long trackId,
            OverlayItem overlay,
            float quality,
            float drift,
            float support,
            int inliers,
            int failures,
            int ageFrames,
            int framesSinceAnchor,
            String reason,
            long now
    ) {
        return new TargetSnapshot(
                state,
                trackId,
                overlay,
                quality,
                drift,
                support,
                inliers,
                failures,
                ageFrames,
                framesSinceAnchor,
                stableUpdates,
                now,
                lockedTrackId > 0L ? lockedAppearance : currentAppearance,
                lockedTrackId,
                reason,
                lockSwitches,
                lockLosses,
                framesToLock,
                timeToLockMillis,
                lockRevision,
                lockReassociations
        );
    }

    private static List<OverlayItem> plateOverlays(List<OverlayItem> items) {
        List<OverlayItem> plates = new ArrayList<>();
        for (OverlayItem item : items == null
                ? Collections.<OverlayItem>emptyList() : items) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && !item.carriedPrediction
                    && item.trackId > 0L) {
                plates.add(item);
            }
        }
        return plates;
    }

    private List<TargetSelector.Candidate> overlayCandidates(
            List<OverlayItem> items,
            Map<Long, float[]> appearanceByTrack
    ) {
        List<TargetSelector.Candidate> candidates = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            candidates.add(new TargetSelector.Candidate(
                    item.trackId,
                    item.normalizedBounds.left,
                    item.normalizedBounds.top,
                    item.normalizedBounds.right,
                    item.normalizedBounds.bottom,
                    0.5f,
                    0.5f,
                    item.trackId == snapshot.trackId ? snapshot.ageFrames + 1 : 0,
                    appearanceByTrack == null
                            ? null : appearanceByTrack.get(item.trackId)
            ));
        }
        return candidates;
    }

    private static List<TargetSelector.Candidate> trackingCandidates(
            List<TrackedPlate> plates
    ) {
        List<TargetSelector.Candidate> candidates = new ArrayList<>();
        for (TrackedPlate plate : plates == null
                ? Collections.<TrackedPlate>emptyList() : plates) {
            if (plate == null || plate.overlayItem == null || plate.trackId <= 0L) continue;
            candidates.add(new TargetSelector.Candidate(
                    plate.trackId,
                    plate.normalizedBounds.left,
                    plate.normalizedBounds.top,
                    plate.normalizedBounds.right,
                    plate.normalizedBounds.bottom,
                    plate.trackingQuality,
                    plate.supportRatio,
                    plate.ageFrames,
                    null
            ));
        }
        return candidates;
    }

    private static OverlayItem overlayByTrackId(List<OverlayItem> items, long trackId) {
        for (OverlayItem item : items) {
            if (item.trackId == trackId) return item;
        }
        return null;
    }

    private static TrackedPlate trackedById(List<TrackedPlate> plates, long trackId) {
        for (TrackedPlate plate : plates == null
                ? Collections.<TrackedPlate>emptyList() : plates) {
            if (plate != null && plate.trackId == trackId) return plate;
        }
        return null;
    }

    private static float[] cloneDescriptor(float[] descriptor) {
        return descriptor == null ? null : descriptor.clone();
    }
}
