package com.example.alpr_v1.domain;

public enum EntityAcquisitionState {
    NEW,
    QUEUED,
    ACQUIRING,
    PLATE_LOCALIZED,
    READING_REGISTRATION,
    READY_TO_FINALIZE,
    ACQUIRED,
    FAILED,
    EXPIRED;

    /** Ordinary runtime updates may advance progress, but never regress it. */
    public static EntityAcquisitionState advance(
            EntityAcquisitionState current,
            EntityAcquisitionState proposed
    ) {
        EntityAcquisitionState safeCurrent = current == null ? NEW : current;
        if (proposed == null || proposed == NEW) return safeCurrent;
        if (safeCurrent == ACQUIRED || safeCurrent == EXPIRED) return safeCurrent;
        if (proposed == EXPIRED) return EXPIRED;
        if (safeCurrent == FAILED) return safeCurrent;
        if (proposed == FAILED) return FAILED;
        return progressRank(proposed) >= progressRank(safeCurrent)
                ? proposed : safeCurrent;
    }

    private static int progressRank(EntityAcquisitionState state) {
        switch (state) {
            case QUEUED: return 1;
            case ACQUIRING: return 2;
            case PLATE_LOCALIZED: return 3;
            case READING_REGISTRATION: return 4;
            case READY_TO_FINALIZE: return 5;
            case ACQUIRED: return 6;
            case NEW:
            default: return 0;
        }
    }
}
