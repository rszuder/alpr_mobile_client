package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DetectionOverlayLabelTest {
    @Test
    public void separatesDetectionTextFromTrailingConfidence() {
        DetectionOverlayView.LabelParts parts = DetectionOverlayView.LabelParts.parse(
                "tablica ABC123 94%"
        );

        assertEquals("tablica ABC123", parts.detection);
        assertEquals("94%", parts.confidence);
    }

    @Test
    public void leavesLabelWithoutConfidenceUnchanged() {
        DetectionOverlayView.LabelParts parts = DetectionOverlayView.LabelParts.parse("pojazd");

        assertEquals("pojazd", parts.detection);
        assertEquals("", parts.confidence);
    }
}
