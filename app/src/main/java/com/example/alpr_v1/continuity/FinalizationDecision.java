package com.example.alpr_v1.continuity;

/** Immutable result of the runtime finalization safety gate. */
public final class FinalizationDecision {
    public final boolean allowed;
    public final String reason;

    private FinalizationDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = Contracts.reason(reason);
    }

    public static FinalizationDecision allow() {
        return new FinalizationDecision(true, "validated_current_visual_epoch");
    }

    public static FinalizationDecision deny(String reason) {
        return new FinalizationDecision(false, reason);
    }
}
