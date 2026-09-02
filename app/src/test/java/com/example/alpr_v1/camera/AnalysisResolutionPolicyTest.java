package com.example.alpr_v1.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AnalysisResolutionPolicyTest {
    @Test
    public void scanKeepsReadableSourceOnConstrainedDevice() {
        assertEquals(1280, AnalysisResolutionPolicy.autoWidth(true, true));
        assertEquals(960, AnalysisResolutionPolicy.autoHeight(true, true));
    }

    @Test
    public void ordinaryAutoStillRespectsConstrainedDevice() {
        assertEquals(640, AnalysisResolutionPolicy.autoWidth(true, false));
        assertEquals(480, AnalysisResolutionPolicy.autoHeight(true, false));
    }
}
