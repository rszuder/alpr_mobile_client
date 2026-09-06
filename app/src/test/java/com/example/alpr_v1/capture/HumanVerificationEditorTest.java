package com.example.alpr_v1.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.metrics.ImageDifficultyMetrics;
import com.example.alpr_v1.pipeline.PlateGeometry;

import org.junit.Test;

import java.util.Collections;

public final class HumanVerificationEditorTest {
    @Test
    public void acceptedUsesPredictionAsEligibleGroundTruth() {
        CapturedPlateItem item = item();
        HumanVerificationEditor.applyStatus(
                item, CapturedPlateItem.VerificationStatus.ACCEPTED, "", 10L
        );
        assertEquals("WI1234A", item.groundTruthText);
        assertTrue(item.eligibleForTextMetrics());
        assertEquals(10L, item.verifiedAtMillis);
        assertEquals(1, item.verificationRevision);
    }

    @Test
    public void correctionNormalizesToUppercaseAndRemainsEligible() {
        CapturedPlateItem item = item();
        HumanVerificationEditor.applyStatus(
                item, CapturedPlateItem.VerificationStatus.CORRECTED, " wi 1234b ", 20L
        );
        assertEquals("WI 1234B", item.groundTruthText);
        assertTrue(item.eligibleForTextMetrics());
    }

    @Test
    public void rejectedClearsGroundTruthAndIsNotEligible() {
        CapturedPlateItem item = item();
        item.groundTruthText = "OLD";
        HumanVerificationEditor.applyStatus(
                item, CapturedPlateItem.VerificationStatus.REJECTED, "ignored", 30L
        );
        assertEquals("", item.groundTruthText);
        assertFalse(item.eligibleForTextMetrics());
    }

    private static CapturedPlateItem item() {
        return new CapturedPlateItem(
                "capture", "session", 1L, null, "WI1234A", 0.9, 0.8, true,
                Collections.emptyList(), 1L, 1L, 0.7f, null, 1f, "normal",
                PlateGeometry.unavailable(), ImageDifficultyMetrics.unavailable(), true,
                true, true, 3, 1, "single_row", Collections.emptyList(), "WI1234A"
        );
    }
}
