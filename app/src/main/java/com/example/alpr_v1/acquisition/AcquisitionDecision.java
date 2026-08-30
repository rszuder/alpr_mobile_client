package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.EntityAcquisitionState;

/** Immutable result of applying one final pipeline result to the Scan session. */
public final class AcquisitionDecision {
    public final boolean accepted;
    public final AcquisitionSessionOutcome outcome;
    public final AcquisitionDeferReason deferReason;
    public final long scanRunId;
    public final long sessionId;
    public final long entityId;
    public final EntityAcquisitionState entityState;
    public final AcquisitionDirective nextDirective;
    public final String reason;

    public AcquisitionDecision(
            boolean accepted,
            AcquisitionSessionOutcome outcome,
            AcquisitionDeferReason deferReason,
            long scanRunId,
            long sessionId,
            long entityId,
            EntityAcquisitionState entityState,
            AcquisitionDirective nextDirective,
            String reason
    ) {
        this.accepted = accepted;
        this.outcome = outcome == null ? AcquisitionSessionOutcome.NONE : outcome;
        this.deferReason = deferReason == null
                ? AcquisitionDeferReason.NONE : deferReason;
        this.scanRunId = Math.max(0L, scanRunId);
        this.sessionId = Math.max(0L, sessionId);
        this.entityId = Math.max(0L, entityId);
        this.entityState = entityState == null
                ? EntityAcquisitionState.NEW : entityState;
        this.nextDirective = nextDirective == null
                ? AcquisitionDirective.none(0L, this.scanRunId) : nextDirective;
        this.reason = reason == null ? "" : reason.trim();
    }
}
