package com.example.alpr_v1.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProcessCpuUsageTest {
    @Test public void measuresAppShareAcrossAllCores() {
        ProcessCpuUsage usage = new ProcessCpuUsage();
        assertTrue(Double.isNaN(usage.sample(100L, 1_000L, 8)));
        assertEquals(25.0, usage.sample(2_100L, 2_000L, 8), 0.001);
        assertEquals(0.0, usage.sample(2_100L, 3_000L, 8), 0.001);
    }

    @Test public void resetAndInvalidClocksNeverDisplayFabricatedLoad() {
        ProcessCpuUsage usage = new ProcessCpuUsage();
        usage.sample(100L, 1_000L, 4);
        assertTrue(Double.isNaN(usage.sample(90L, 2_000L, 4)));
        assertTrue(Double.isNaN(usage.sample(100L, 2_000L, 4)));
        usage.reset();
        assertTrue(Double.isNaN(usage.sample(200L, 3_000L, 4)));
        assertTrue(Double.isNaN(usage.sample(300L, 4_000L, 0)));
        assertTrue(Double.isNaN(usage.sample(300L, 4_000L, 4)));
        assertEquals(100.0, usage.sample(10_000L, 5_000L, 4), 0.001);
    }
}
