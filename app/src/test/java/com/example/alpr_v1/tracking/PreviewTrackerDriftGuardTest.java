package com.example.alpr_v1.tracking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PreviewTrackerDriftGuardTest {
    @Test
    public void rejectsLargeJitterJumpThatDisagreesWithMtAnchor() {
        PreviewTrackerDriftGuard.Decision decision =
                PreviewTrackerDriftGuard.reconcile(
                        1,
                        0,
                        motion(-6, -11, 4),
                        motion(0, 0, 4)
                );

        assertFalse(decision.valid);
    }

    @Test
    public void acceptsMotionConfirmedByIncrementalAndAnchorMatches() {
        PreviewTrackerDriftGuard.Decision decision =
                PreviewTrackerDriftGuard.reconcile(
                        2,
                        1,
                        motion(3, 1, 4),
                        motion(5, 2, 3)
                );

        assertTrue(decision.valid);
        assertTrue(decision.anchored);
        assertEquals(5, decision.absoluteDx);
        assertEquals(2, decision.absoluteDy);
    }

    @Test
    public void rejectsWeakTwoCornerMatch() {
        PreviewTrackerDriftGuard.Decision decision =
                PreviewTrackerDriftGuard.reconcile(
                        0,
                        0,
                        motion(3, 0, 2),
                        motion(3, 0, 2)
                );

        assertFalse(decision.valid);
    }

    @Test
    public void boundsIncrementalFallbackWhenAnchorIsUnavailable() {
        PreviewTrackerDriftGuard.Decision decision =
                PreviewTrackerDriftGuard.reconcile(
                        22,
                        0,
                        motion(4, 0, 4),
                        new PreviewTrackerDriftGuard.Motion(false, 0, 0, 0)
                );

        assertFalse(decision.valid);
    }

    private static PreviewTrackerDriftGuard.Motion motion(
            int dx,
            int dy,
            int support
    ) {
        return new PreviewTrackerDriftGuard.Motion(true, dx, dy, support);
    }
}
