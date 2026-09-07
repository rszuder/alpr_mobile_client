package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.TargetSessionState;

/** Read-only projection of the existing TargetSession; this is not another mode controller. */
public final class TargetFocusSnapshot {
    public final long entityId, sessionId, revision;
    public final boolean awaitingFreshScanAnchor;
    public final TargetSessionState sessionState;

    TargetFocusSnapshot(long entityId, long sessionId, long revision, boolean awaitingFreshScanAnchor,
            TargetSessionState sessionState) {
        this.entityId = entityId;
        this.sessionId = sessionId;
        this.revision = revision;
        this.awaitingFreshScanAnchor = awaitingFreshScanAnchor;
        this.sessionState = sessionState;
    }
}
