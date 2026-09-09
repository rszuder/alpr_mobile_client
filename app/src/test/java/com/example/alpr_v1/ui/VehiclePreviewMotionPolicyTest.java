package com.example.alpr_v1.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehiclePreviewMotionPolicyTest {
    @Test public void briefCalmGapsDoNotSwitchBackToLocalTracking() {
        VehiclePreviewMotionPolicy policy = new VehiclePreviewMotionPolicy();
        assertFalse(policy.update(false, 1L));
        assertTrue(policy.update(true, 100_000_000L));
        assertTrue(policy.update(false, 200_000_000L));
        assertTrue(policy.update(false, 500_000_000L));
        assertTrue(policy.update(true, 600_000_000L));
        assertTrue(policy.update(false, 1_100_000_000L));
        assertTrue(policy.globalOwnsGeometry());
        assertFalse(policy.update(false, 1_200_000_000L));
    }

    @Test public void resetDropsGlobalOwnershipImmediately() {
        VehiclePreviewMotionPolicy policy = new VehiclePreviewMotionPolicy();
        policy.update(true, 100L);
        policy.reset();
        assertFalse(policy.globalOwnsGeometry());
        assertFalse(policy.update(false, 110L));
    }

    @Test public void delayedTimestampCannotPrematurelyEndMotionOwnership() {
        VehiclePreviewMotionPolicy policy = new VehiclePreviewMotionPolicy();
        policy.update(true, 2_000_000_000L);
        assertTrue(policy.update(false, 1_000_000_000L));
        assertTrue(policy.update(false, 2_500_000_000L));
        assertFalse(policy.update(false, 2_600_000_000L));
    }
}
