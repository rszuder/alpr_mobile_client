package com.example.alpr_v1.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoZoomRecognitionMemoryTest {
    @Test
    public void emptyMzResultDoesNotEraseRememberedPlate() {
        AutoZoomRecognitionMemory.Result result =
                AutoZoomRecognitionMemory.choose(
                        "WA1234", 0.84, "", 0.0, false
                );

        assertEquals("WA1234", result.text);
        assertEquals(0.84, result.confidence, 0.0001);
        assertFalse(result.acceptedFreshText);
    }

    @Test
    public void acceptsFirstPlausibleMzReading() {
        AutoZoomRecognitionMemory.Result result =
                AutoZoomRecognitionMemory.choose(
                        "", 0.0, "wa 1234", 0.67, false
                );

        assertEquals("WA1234", result.text);
        assertEquals(0.67, result.confidence, 0.0001);
        assertTrue(result.acceptedFreshText);
    }

    @Test
    public void weakDifferentReadingDoesNotReplaceStrongMemory() {
        AutoZoomRecognitionMemory.Result result =
                AutoZoomRecognitionMemory.choose(
                        "WA1234", 0.86, "WX9876", 0.70, false
                );

        assertEquals("WA1234", result.text);
        assertEquals(0.86, result.confidence, 0.0001);
        assertFalse(result.acceptedFreshText);
    }

    @Test
    public void clearlyStrongerReadingUpdatesMemory() {
        AutoZoomRecognitionMemory.Result result =
                AutoZoomRecognitionMemory.choose(
                        "WA1234", 0.58, "WX9876", 0.82, false
                );

        assertEquals("WX9876", result.text);
        assertEquals(0.82, result.confidence, 0.0001);
        assertTrue(result.acceptedFreshText);
    }

    @Test
    public void matchingTextCannotLowerRememberedConfidence() {
        AutoZoomRecognitionMemory.Result result =
                AutoZoomRecognitionMemory.choose(
                        "WA1234", 0.86, "wa-1234", 0.61, false
                );

        assertEquals("WA1234", result.text);
        assertEquals(0.86, result.confidence, 0.0001);
        assertTrue(result.acceptedFreshText);
    }

    @Test
    public void implausiblyShortReadingIsIgnoredEvenWhenConfident() {
        AutoZoomRecognitionMemory.Result result =
                AutoZoomRecognitionMemory.choose(
                        "WA1234", 0.72, "A1", 0.99, true
                );

        assertEquals("WA1234", result.text);
        assertFalse(result.acceptedFreshText);
    }
}
