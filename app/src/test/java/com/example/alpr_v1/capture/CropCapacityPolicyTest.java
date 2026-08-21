package com.example.alpr_v1.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CropCapacityPolicyTest {
    @Test
    public void respectsManualLimitAndCapsAutoOnLowRamDevice() {
        assertEquals(50, CropCapacityPolicy.resolve("50", 128L * 1024L * 1024L, true));
        assertEquals(25, CropCapacityPolicy.resolve("auto", 512L * 1024L * 1024L, true));
        assertEquals("auto", CropCapacityPolicy.normalizeSetting("17"));
    }
}
