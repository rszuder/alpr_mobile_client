package com.example.alpr_v1.domain;

/** Owns the selected application mode and at most one foreground target session. */
public final class ModeController {
    private ApplicationMode mode;
    private TargetSession activeSession;
    private long nextSessionId = 1L;

    public ModeController() {
        this(ApplicationMode.SCAN_ACQUIRE);
    }

    public ModeController(ApplicationMode initialMode) {
        if (initialMode == null) throw new IllegalArgumentException("initialMode is required");
        mode = initialMode;
    }

    public synchronized ApplicationMode mode() { return mode; }

    public synchronized TargetSession activeSession() {
        return activeSession != null && !activeSession.state().terminal()
                ? activeSession : null;
    }

    /** Changes mode and cancels a foreground session that is incompatible with it. */
    public synchronized ModeChange switchMode(ApplicationMode nextMode, long nowNanos) {
        if (nextMode == null) throw new IllegalArgumentException("nextMode is required");
        ApplicationMode previousMode = mode;
        Long cancelledSessionId = null;
        if (activeSession != null
                && !activeSession.state().terminal()
                && !supports(nextMode, activeSession.purpose())) {
            cancelledSessionId = activeSession.sessionId();
            activeSession.transitionTo(TargetSessionState.CANCELLED, nowNanos);
            activeSession = null;
        }
        mode = nextMode;
        return new ModeChange(previousMode, nextMode, cancelledSessionId);
    }

    /** Starts foreground work and explicitly replaces any previous active session. */
    public synchronized TargetSession startSession(
            long entityId,
            TargetPurpose purpose,
            long nowNanos
    ) {
        if (!supports(mode, purpose)) {
            throw new IllegalStateException(mode + " does not support purpose " + purpose);
        }
        if (activeSession != null && !activeSession.state().terminal()) {
            activeSession.transitionTo(TargetSessionState.CANCELLED, nowNanos);
        }
        activeSession = new TargetSession(nextSessionId++, entityId, purpose, nowNanos);
        return activeSession;
    }

    /** Converts a short search verification into a persistent pursuit of the same entity. */
    public synchronized TargetSession promoteSearchToPursuit(long nowNanos) {
        if (mode != ApplicationMode.SEARCH_VERIFY_PURSUIT
                || activeSession == null
                || activeSession.state().terminal()
                || activeSession.purpose() != TargetPurpose.SEARCH_VERIFICATION) {
            throw new IllegalStateException("No active search verification to promote");
        }
        long entityId = activeSession.entityId();
        activeSession.transitionTo(TargetSessionState.COMPLETED, nowNanos);
        activeSession = new TargetSession(
                nextSessionId++,
                entityId,
                TargetPurpose.SEARCH_PURSUIT,
                nowNanos
        );
        return activeSession;
    }

    public synchronized void finishSession(
            long sessionId,
            TargetSessionState terminalState,
            long nowNanos
    ) {
        if (terminalState == null || !terminalState.terminal()) {
            throw new IllegalArgumentException("A terminal state is required");
        }
        if (activeSession == null || activeSession.sessionId() != sessionId) {
            throw new IllegalArgumentException("Session is not active: " + sessionId);
        }
        activeSession.transitionTo(terminalState, nowNanos);
        activeSession = null;
    }

    public static boolean supports(ApplicationMode mode, TargetPurpose purpose) {
        if (mode == null || purpose == null) return false;
        switch (mode) {
            case SCAN_ACQUIRE:
                return purpose == TargetPurpose.SCAN_ACQUISITION;
            case PICK_ACQUIRE_LOCK:
                return purpose == TargetPurpose.USER_PICK;
            case SEARCH_VERIFY_PURSUIT:
                return purpose == TargetPurpose.SEARCH_VERIFICATION
                        || purpose == TargetPurpose.SEARCH_PURSUIT;
            default:
                return false;
        }
    }

    public static final class ModeChange {
        public final ApplicationMode previousMode;
        public final ApplicationMode currentMode;
        public final Long cancelledSessionId;

        private ModeChange(
                ApplicationMode previousMode,
                ApplicationMode currentMode,
                Long cancelledSessionId
        ) {
            this.previousMode = previousMode;
            this.currentMode = currentMode;
            this.cancelledSessionId = cancelledSessionId;
        }
    }
}
