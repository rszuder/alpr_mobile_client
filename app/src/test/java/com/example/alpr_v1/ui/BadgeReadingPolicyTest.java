package com.example.alpr_v1.ui;

import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BadgeReadingPolicyTest {
    @Test public void weakerDifferentTextCannotReplaceEvenWhenItIsConfirmed() {
        EntityRecognitionSnapshot previous = reading("WX1234", 0.95, false);
        assertSame(previous, BadgeReadingPolicy.retainBest(previous, reading("WX1284", 0.65, true)));
        assertSame(previous, BadgeReadingPolicy.retainBest(previous, reading("WX1284", 0.65, false)));
    }

    @Test public void strongerTextReplacesAndEqualConfidenceKeepsExistingText() {
        EntityRecognitionSnapshot previous = reading("WX1234", 0.8, true);
        EntityRecognitionSnapshot stronger = reading("WX1284", 0.96, false);
        assertSame(stronger, BadgeReadingPolicy.retainBest(previous, stronger));
        assertSame(previous, BadgeReadingPolicy.retainBest(previous, reading("WX1284", 0.8, true)));
    }

    @Test public void matchingTextCanBeConfirmedWithoutReducingConfidence() {
        EntityRecognitionSnapshot result = BadgeReadingPolicy.retainBest(
                reading("WX1234", 0.95, false), reading("WX1234", 0.7, true));
        assertEquals("WX1234", result.text);
        assertEquals(0.95, result.confidence, 0.00001);
        assertTrue(result.confirmed);
    }

    @Test public void strongerMatchingTextDoesNotLoseConfirmation() {
        EntityRecognitionSnapshot result = BadgeReadingPolicy.retainBest(
                reading("WX1234", 0.8, true), reading("WX1234", 0.96, false));
        assertEquals(0.96, result.confidence, 0.00001);
        assertTrue(result.confirmed);
    }

    @Test public void missingOrInvalidConfidenceDoesNotDisplaceAnExistingReading() {
        EntityRecognitionSnapshot previous = reading("WX1234", 0.95, false);
        assertSame(previous, BadgeReadingPolicy.retainBest(previous, null));
        assertSame(previous, BadgeReadingPolicy.retainBest(previous, reading("", 1.0, true)));
        assertSame(previous, BadgeReadingPolicy.retainBest(previous, reading("WX1284", Double.NaN, true)));
    }

    private static EntityRecognitionSnapshot reading(String text, double confidence, boolean confirmed) {
        return new EntityRecognitionSnapshot(7L, 77L, text, confidence, confirmed, 1);
    }
}
