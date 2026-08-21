package com.example.alpr_v1.capture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CropSamplingPolicyTest {
    @Test
    public void capturesFirstConfirmationImprovementAndPeriodicEvidence() {
        CropSamplingPolicy.Previous previous = new CropSamplingPolicy.Previous(
                "WE12345", false, 0.4f, 1_000_000_000L
        );
        assertFalse(CropSamplingPolicy.shouldCapture(
                previous, "WE12345", false, 0.42f, 1_500_000_000L
        ));
        assertTrue(CropSamplingPolicy.shouldCapture(
                previous, "WE12345", true, 0.42f, 1_500_000_000L
        ));
        assertTrue(CropSamplingPolicy.shouldCapture(
                previous, "WE12345", false, 0.5f, 1_500_000_000L
        ));
        assertTrue(CropSamplingPolicy.shouldCapture(
                previous, "WE12345", false, 0.42f, 2_600_000_000L
        ));
    }
}
