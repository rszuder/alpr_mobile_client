package com.example.alpr_v1.continuity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SceneAnchorRefreshPolicyTest {
    @Test
    public void periodicMtForSameLockCannotReplaceSceneAnchor() {
        assertFalse(SceneAnchorRefreshPolicy.shouldRefresh(
                true, true, 4L, 4L
        ));
    }

    @Test
    public void newlyAcquiredLockCreatesNewSceneAnchor() {
        assertTrue(SceneAnchorRefreshPolicy.shouldRefresh(
                true, true, 5L, 4L
        ));
    }

    @Test
    public void missingAnchorIsRestoredForExistingTarget() {
        assertTrue(SceneAnchorRefreshPolicy.shouldRefresh(
                true, false, 4L, 4L
        ));
    }
}
