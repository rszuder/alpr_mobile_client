package com.example.alpr_v1.metrics;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StatisticsTest {
    @Test
    public void summarizesLatencyDistribution() {
        Statistics.Summary summary = Statistics.summarize(Arrays.asList(10.0, 20.0, 30.0, 40.0));
        assertEquals(4, summary.count);
        assertEquals(25.0, summary.mean, 0.001);
        assertEquals(25.0, summary.median, 0.001);
        assertEquals(10.0, summary.min, 0.001);
        assertEquals(40.0, summary.max, 0.001);
    }
}
