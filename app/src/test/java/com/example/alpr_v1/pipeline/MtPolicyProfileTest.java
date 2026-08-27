package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MtPolicyProfileTest {
    @Test
    public void experimentRestoresLegacyBurstAndSameCycleFallback() {
        assertEquals(
                MtExecutionPolicy.LEGACY_BURST,
                MtExecutionPolicy.forExperiment(true)
        );
        assertEquals(
                MtFallbackPolicy.SAME_CYCLE,
                MtFallbackPolicy.forExperiment(true)
        );
    }

    @Test
    public void userLiveUsesStaggeringAndDeferredFallback() {
        assertEquals(
                MtExecutionPolicy.LIVE_STAGGERED,
                MtExecutionPolicy.forExperiment(false)
        );
        assertEquals(
                MtFallbackPolicy.DEFERRED,
                MtFallbackPolicy.forExperiment(false)
        );
    }
}
