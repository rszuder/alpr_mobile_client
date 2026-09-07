package com.example.alpr_v1.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CropInferenceTimingTest {
    @Test
    public void keepsPerCropDurationsAndConvertsTotalToMilliseconds() {
        CropInferenceTiming timing = new CropInferenceTiming(
                17, 1_000_000, 2_000_000,
                3_000_000, 4_000_000, 5_000_000,
                6_000_000, 7_000_000, 8_000_000, 9_000_000, 40_000_000
        );

        assertEquals(17, timing.frameId);
        assertEquals(4_000_000L, timing.plateInferenceNanos);
        assertEquals(12_000_000L, timing.plateStagesNanos);
        assertEquals(8_000_000L, timing.characterInferenceNanos);
        assertEquals(4.0, timing.plateInferenceMilliseconds(), 0.0001);
        assertEquals(8.0, timing.characterInferenceMilliseconds(), 0.0001);
        assertEquals(40.0, timing.totalMilliseconds(), 0.0001);
    }
}
