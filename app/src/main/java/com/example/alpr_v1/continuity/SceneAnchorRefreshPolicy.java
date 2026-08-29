package com.example.alpr_v1.continuity;

/** Decides when a logical target may replace the fixed preview scene anchor. */
public final class SceneAnchorRefreshPolicy {
    private SceneAnchorRefreshPolicy() {}

    public static boolean shouldRefresh(
            boolean targetPresent,
            boolean anchorPresent,
            long currentLockRevision,
            long anchoredLockRevision
    ) {
        return targetPresent
                && (!anchorPresent || currentLockRevision != anchoredLockRevision);
    }
}
