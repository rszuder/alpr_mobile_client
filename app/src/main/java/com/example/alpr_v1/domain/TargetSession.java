package com.example.alpr_v1.domain;

import java.util.EnumSet;

/**
 * Lifecycle and quality state of work focused on one {@link VehicleEntity}.
 * A session can be short-lived (scan/verification) or persistent (pick/pursuit).
 */
public final class TargetSession {
    private final long sessionId;
    private final long entityId;
    private final TargetPurpose purpose;
    private final long startedAtNanos;
    private TargetSessionState state = TargetSessionState.CREATED;
    private long lastUpdateNanos;
    private float trackingQuality;
    private float identityConfidence;
    private int recoveryLevel;
    private boolean cameraAttentionOwned;

    public TargetSession(
            long sessionId,
            long entityId,
            TargetPurpose purpose,
            long startedAtNanos
    ) {
        if (sessionId <= 0L) throw new IllegalArgumentException("sessionId must be positive");
        if (entityId <= 0L) throw new IllegalArgumentException("entityId must be positive");
        if (purpose == null) throw new IllegalArgumentException("purpose is required");
        this.sessionId = sessionId;
        this.entityId = entityId;
        this.purpose = purpose;
        this.startedAtNanos = Math.max(0L, startedAtNanos);
        this.lastUpdateNanos = this.startedAtNanos;
    }

    public synchronized long sessionId() { return sessionId; }
    public synchronized long entityId() { return entityId; }
    public synchronized TargetPurpose purpose() { return purpose; }
    public synchronized TargetSessionState state() { return state; }
    public synchronized long startedAtNanos() { return startedAtNanos; }
    public synchronized long lastUpdateNanos() { return lastUpdateNanos; }
    public synchronized float trackingQuality() { return trackingQuality; }
    public synchronized float identityConfidence() { return identityConfidence; }
    public synchronized int recoveryLevel() { return recoveryLevel; }
    public synchronized boolean persistent() { return purpose.persistent(); }
    public synchronized boolean cameraAttentionOwned() { return cameraAttentionOwned; }

    public synchronized void transitionTo(TargetSessionState next, long nowNanos) {
        if (next == null) throw new IllegalArgumentException("next state is required");
        if (state.terminal()) {
            throw new IllegalStateException("Session is already terminal: " + state);
        }
        if (next == state) {
            touch(nowNanos);
            return;
        }
        if (!allowedFrom(state).contains(next)) {
            throw new IllegalStateException("Illegal session transition " + state + " -> " + next);
        }
        if ((next == TargetSessionState.LOCKED_IDENTIFIED
                || next == TargetSessionState.LOCKED_UNIDENTIFIED)
                && !purpose.persistent()) {
            throw new IllegalStateException("A non-persistent session cannot enter " + next);
        }
        state = next;
        if (next == TargetSessionState.RECOVERING) {
            recoveryLevel = Math.max(1, recoveryLevel);
        } else if (next == TargetSessionState.TRACKING
                || next == TargetSessionState.LOCKED_IDENTIFIED
                || next == TargetSessionState.LOCKED_UNIDENTIFIED) {
            recoveryLevel = 0;
        }
        if (next.terminal()) cameraAttentionOwned = false;
        touch(nowNanos);
    }

    public synchronized void updateQuality(
            float trackingQuality,
            float identityConfidence,
            int recoveryLevel,
            long nowNanos
    ) {
        ensureActive();
        this.trackingQuality = clamp01(trackingQuality);
        this.identityConfidence = clamp01(identityConfidence);
        this.recoveryLevel = Math.max(0, recoveryLevel);
        touch(nowNanos);
    }

    public synchronized void setCameraAttentionOwned(boolean owned, long nowNanos) {
        ensureActive();
        cameraAttentionOwned = owned;
        touch(nowNanos);
    }

    private void ensureActive() {
        if (state.terminal()) {
            throw new IllegalStateException("Session is already terminal: " + state);
        }
    }

    private void touch(long nowNanos) {
        lastUpdateNanos = Math.max(lastUpdateNanos, nowNanos);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static EnumSet<TargetSessionState> allowedFrom(TargetSessionState state) {
        switch (state) {
            case CREATED:
                return EnumSet.of(
                        TargetSessionState.PROVISIONAL_HOLD,
                        TargetSessionState.ACQUIRING_PLATE,
                        TargetSessionState.TRACKING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case PROVISIONAL_HOLD:
                return EnumSet.of(
                        TargetSessionState.ACQUIRING_PLATE,
                        TargetSessionState.TRACKING,
                        TargetSessionState.RECOVERING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case ACQUIRING_PLATE:
                return EnumSet.of(
                        TargetSessionState.READING_REGISTRATION,
                        TargetSessionState.TRACKING,
                        TargetSessionState.RECOVERING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case READING_REGISTRATION:
                return EnumSet.of(
                        TargetSessionState.TRACKING,
                        TargetSessionState.LOCKED_IDENTIFIED,
                        TargetSessionState.LOCKED_UNIDENTIFIED,
                        TargetSessionState.RECOVERING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case TRACKING:
                return EnumSet.of(
                        TargetSessionState.READING_REGISTRATION,
                        TargetSessionState.LOCKED_IDENTIFIED,
                        TargetSessionState.LOCKED_UNIDENTIFIED,
                        TargetSessionState.RECOVERING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case LOCKED_UNIDENTIFIED:
                return EnumSet.of(
                        TargetSessionState.LOCKED_IDENTIFIED,
                        TargetSessionState.TRACKING,
                        TargetSessionState.RECOVERING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case LOCKED_IDENTIFIED:
                return EnumSet.of(
                        TargetSessionState.TRACKING,
                        TargetSessionState.RECOVERING,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case RECOVERING:
                return EnumSet.of(
                        TargetSessionState.TRACKING,
                        TargetSessionState.LOCKED_IDENTIFIED,
                        TargetSessionState.LOCKED_UNIDENTIFIED,
                        TargetSessionState.COMPLETED,
                        TargetSessionState.CANCELLED,
                        TargetSessionState.LOST
                );
            case COMPLETED:
            case CANCELLED:
            case LOST:
            default:
                return EnumSet.noneOf(TargetSessionState.class);
        }
    }
}
