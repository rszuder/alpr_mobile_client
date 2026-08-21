package com.example.alpr_v1.camera;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MotionIntensityFilterTest {
    @Test
    public void recognizesRapidRecentRotationAndExpiresOldSample() {
        MotionIntensityFilter filter = new MotionIntensityFilter();
        filter.update(0.1f, 0.1f, 0.1f, 1_000_000_000L);
        assertFalse(filter.isRapid(1_010_000_000L));

        filter.update(3f, 0f, 0f, 1_020_000_000L);
        assertTrue(filter.isRapid(1_030_000_000L));
        assertFalse(filter.isRapid(1_400_000_001L));
    }
}
