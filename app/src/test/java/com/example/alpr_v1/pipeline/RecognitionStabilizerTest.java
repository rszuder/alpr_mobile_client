package com.example.alpr_v1.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RecognitionStabilizerTest {
    @Test
    public void requiresTwoMatchingFrames() {
        RecognitionStabilizer stabilizer = new RecognitionStabilizer(2);
        assertNull(stabilizer.accept("WE12345", 0.8));
        RecognitionStabilizer.StableResult result = stabilizer.accept("we12345", 0.9);
        assertEquals("WE12345", result.text);
        assertEquals(0.85, result.confidence, 0.0001);
    }

    @Test
    public void differentTextStartsNewCandidate() {
        RecognitionStabilizer stabilizer = new RecognitionStabilizer(2);
        assertNull(stabilizer.accept("WE12345", 0.8));
        assertNull(stabilizer.accept("WE12346", 0.8));
        assertEquals("WE12346", stabilizer.accept("WE12346", 0.8).text);
    }
}
