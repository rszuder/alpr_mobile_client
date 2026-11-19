package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.TargetSessionState;

/** Read-only projection of the existing TargetSession; this is not another mode controller. */
public final class TargetFocusSnapshot {
    public final long entityId, sessionId, revision;
    public final boolean awaitingFreshScanAnchor;
    public final TargetSessionState sessionState;
    public final boolean waitingForTarget;
    public final long displayNumber;

    TargetFocusSnapshot(long entityId, long sessionId, long revision, boolean awaitingFreshScanAnchor,
            TargetSessionState sessionState) {
        this(entityId,sessionId,revision,awaitingFreshScanAnchor,sessionState,entityId);
    }

    TargetFocusSnapshot(long entityId,long sessionId,long revision,boolean awaitingFreshScanAnchor,
            TargetSessionState sessionState,long displayNumber) {
        this.entityId = entityId;
        this.sessionId = sessionId;
        this.revision = revision;
        this.awaitingFreshScanAnchor = awaitingFreshScanAnchor;
        this.sessionState = sessionState;
        this.waitingForTarget = sessionState == TargetSessionState.WAITING_FOR_TARGET;
        this.displayNumber = displayNumber;
    }
}
