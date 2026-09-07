package com.example.alpr_v1.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StationarySceneSupportTest {
    @Test public void requiresRecentContinuousEvidenceAndDropsImmediatelyOnMotion() {
        StationarySceneSupport support = new StationarySceneSupport();
        support.observe(0L, true);
        assertFalse(support.supported(200_000_000L));
        support.observe(300_000_000L, true);
        assertTrue(support.supported(400_000_000L));
        support.observeUncertain(900_000_000L);
        assertTrue(support.supported(900_000_000L));
        assertFalse(support.supported(2_000_000_000L));
        support.observe(2_000_000_000L, true);
        assertFalse(support.supported(2_000_000_000L));
        support.observe(2_300_000_000L, true);
        assertTrue(support.supported(2_300_000_000L));
        support.observe(2_400_000_000L, false);
        assertFalse(support.supported(2_400_000_000L));
    }

    @Test public void holdBudgetCoversSlowInferenceAndRemainsBounded() {
        assertEquals(15_000_000_000L, StationarySceneSupport.maximumOverlayAge(0L));
        assertEquals(20_000_000_000L, StationarySceneSupport.maximumOverlayAge(10_000_000_000L));
        assertEquals(30_000_000_000L, StationarySceneSupport.maximumOverlayAge(Long.MAX_VALUE));
    }
}
