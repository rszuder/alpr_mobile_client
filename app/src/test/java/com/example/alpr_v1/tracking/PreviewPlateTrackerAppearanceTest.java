package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PreviewPlateTrackerAppearanceTest {
    @Test
    public void identicalNormalizedDescriptorHasFullSimilarity() {
        float[] descriptor = {0.5f, -0.5f, 0.5f, -0.5f};

        assertEquals(
                1f,
                PreviewPlateTracker.localAppearanceSimilarity(descriptor, descriptor),
                0.0001f
        );
    }

    @Test
    public void contradictoryDescriptorCannotSupportContinuity() {
        float[] anchor = {0.5f, -0.5f, 0.5f, -0.5f};
        float[] unrelated = {-0.5f, -0.5f, 0.5f, 0.5f};

        assertEquals(
                0f,
                PreviewPlateTracker.localAppearanceSimilarity(anchor, unrelated),
                0.0001f
        );
    }
}
