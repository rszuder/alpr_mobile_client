package com.example.alpr_v1.acquisition;

/** Immutable, revisioned Scan command consumed by the live pipeline. */
public final class AcquisitionDirective {
    public final long revision;
    public final AcquisitionDirectiveAction action;
    public final long scanRunId;
    public final long sessionId;
    public final long entityId;
    public final String reason;

    public AcquisitionDirective(
            long revision,
            AcquisitionDirectiveAction action,
            long scanRunId,
            long sessionId,
            long entityId,
            String reason
    ) {
        if (revision < 0L) throw new IllegalArgumentException("revision");
        if (action == null) throw new IllegalArgumentException("action");
        this.revision = revision;
        this.action = action;
        this.scanRunId = Math.max(0L, scanRunId);
        this.sessionId = Math.max(0L, sessionId);
        this.entityId = Math.max(0L, entityId);
        this.reason = reason == null ? "" : reason.trim();
        if (targetsEntity(action) && this.entityId <= 0L) {
            throw new IllegalArgumentException("entityId required for " + action);
        }
    }

    public boolean requestsMt() {
        return action == AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT
                || action == AcquisitionDirectiveAction.REQUEST_EXPANDED_ENTITY_MT;
    }

    public boolean expandedRoi() {
        return action == AcquisitionDirectiveAction.REQUEST_EXPANDED_ENTITY_MT;
    }

    public static AcquisitionDirective none(long revision, long scanRunId) {
        return new AcquisitionDirective(
                revision,
                AcquisitionDirectiveAction.NONE,
                scanRunId,
                0L,
                0L,
                ""
        );
    }

    private static boolean targetsEntity(AcquisitionDirectiveAction action) {
        return action == AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT
                || action == AcquisitionDirectiveAction.REQUEST_EXPANDED_ENTITY_MT
                || action == AcquisitionDirectiveAction.CONTINUE_ACTIVE_SESSION;
    }
}
