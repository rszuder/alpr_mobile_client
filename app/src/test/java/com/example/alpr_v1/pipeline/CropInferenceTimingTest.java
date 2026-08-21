package com.example.alpr_v1.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CropInferenceTimingTest {
    @Test
    public void keepsPerCropDurationsAndConvertsTotalToMilliseconds() {
        CropInferenceTiming timing = new CropInferenceTiming(
                17, 1_000_000, 2_000_000, 3_000_000, 4_000_000,
                5_000_000, 6_000_000, 7_000_000, 30_000_000
        );

        assertEquals(17, timing.frameId);
        assertEquals(6_000_000L, timing.characterInferenceNanos);
        assertEquals(30.0, timing.totalMilliseconds(), 0.0001);
    }
}
