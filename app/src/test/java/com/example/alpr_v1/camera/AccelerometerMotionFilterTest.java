package com.example.alpr_v1.camera;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AccelerometerMotionFilterTest {
    @Test
    public void stationaryGravityDoesNotCreateMotion() {
        AccelerometerMotionFilter filter = new AccelerometerMotionFilter();
        filter.update(0f, 0f, 9.81f, 1_000_000_000L);
        filter.update(0.05f, 0f, 9.80f, 1_020_000_000L);

        assertFalse(filter.isMoving(1_030_000_000L));
        assertFalse(filter.isRapid(1_030_000_000L));
    }

    @Test
    public void moderateImpulseCreatesMovingButNotRapid() {
        AccelerometerMotionFilter filter = initialized();
        filter.update(1.8f, 0f, 9.81f, 2_020_000_000L);

        assertTrue(filter.isMoving(2_030_000_000L));
        assertFalse(filter.isRapid(2_030_000_000L));
    }

    @Test
    public void strongImpulseCreatesRapidLatchAndExpires() {
        AccelerometerMotionFilter filter = initialized();
        filter.update(5.5f, 0f, 9.81f, 2_020_000_000L);

        assertTrue(filter.isMoving(2_030_000_000L));
        assertTrue(filter.isRapid(2_030_000_000L));
        assertFalse(filter.isRapid(2_720_000_001L));
    }

    private static AccelerometerMotionFilter initialized() {
        AccelerometerMotionFilter filter = new AccelerometerMotionFilter();
        filter.update(0f, 0f, 9.81f, 2_000_000_000L);
        return filter;
    }
}
