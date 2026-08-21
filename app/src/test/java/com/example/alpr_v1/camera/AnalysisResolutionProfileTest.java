package com.example.alpr_v1.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AnalysisResolutionProfileTest {
    @Test
    public void parsesPersistedValueAndFallsBackToAuto() {
        assertEquals(
                AnalysisResolutionProfile.DISTANT,
                AnalysisResolutionProfile.fromWireName("distant")
        );
        assertEquals(
                AnalysisResolutionProfile.AUTO,
                AnalysisResolutionProfile.fromWireName("unsupported")
        );
    }
}
