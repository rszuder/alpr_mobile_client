package com.example.alpr_v1.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AnalysisViewportTest {
    @Test
    public void acceptsVehicleCenteredInsideWorkingFrame() {
        assertTrue(AnalysisViewport.accepts(
                new NormalizedBounds(0.20f, 0.25f, 0.70f, 0.75f)
        ));
    }

    @Test
    public void rejectsVehicleCenteredUnderTopHud() {
        assertFalse(AnalysisViewport.accepts(
                new NormalizedBounds(0.20f, 0.01f, 0.70f, 0.15f)
        ));
    }

    @Test
    public void rejectsMostlyOccludedEdgeVehicle() {
        NormalizedBounds candidate =
                new NormalizedBounds(0.00f, 0.00f, 0.12f, 0.32f);
        assertFalse(AnalysisViewport.accepts(candidate));
        assertEquals(0.2917f, AnalysisViewport.intersectionRatio(candidate), 0.001f);
    }
}
