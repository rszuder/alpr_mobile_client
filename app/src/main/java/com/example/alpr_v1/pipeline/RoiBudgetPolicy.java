package com.example.alpr_v1.pipeline;

import java.util.Locale;

/**
 * Badawcza polityka wykorzystania ROI pojazdów przed modelem MT.
 *
 * R0 - MT analizuje pełną klatkę, MP nie jest wykonywany.
 * R1 - MP wybiera maksymalnie jeden ROI pojazdu.
 * R2 - MP wybiera maksymalnie dwa ROI pojazdów.
 */
public enum RoiBudgetPolicy {
    FULL_FRAME("r0_full_frame", 0, false),
    ONE_ROI("r1_one_roi", 1, true),
    TWO_ROI("r2_two_roi", 2, true);

    private final String wireName;
    private final int maximumRegions;
    private final boolean vehicleCascade;

    RoiBudgetPolicy(
            String wireName,
            int maximumRegions,
            boolean vehicleCascade
    ) {
        this.wireName = wireName;
        this.maximumRegions = maximumRegions;
        this.vehicleCascade = vehicleCascade;
    }

    public String wireName() {
        return wireName;
    }

    public int maximumRegions() {
        return maximumRegions;
    }

    public boolean usesVehicleCascade() {
        return vehicleCascade;
    }

    public static RoiBudgetPolicy fromWireName(String value) {
        if (value == null) return FULL_FRAME;

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        for (RoiBudgetPolicy policy : values()) {
            if (policy.wireName.equals(normalized)) {
                return policy;
            }
        }

        return FULL_FRAME;
    }
}