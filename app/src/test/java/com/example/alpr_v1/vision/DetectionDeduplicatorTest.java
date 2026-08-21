package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DetectionDeduplicatorTest {
    @Test
    public void removesContainedPlateEvenWhenIouIsBelowThreshold() {
        Detection large = detection(0, 0.90f, 10, 10, 110, 50);
        Detection nested = detection(0, 0.70f, 20, 15, 80, 45);

        List<Detection> result = DetectionDeduplicator.suppress(
                Arrays.asList(large, nested), 0.58f, 0.82f, false
        );

        assertEquals(1, result.size());
        assertEquals(0.90f, result.get(0).confidence, 0.001f);
    }

    @Test
    public void preservesSeparatePlates() {
        List<Detection> result = DetectionDeduplicator.suppress(
                Arrays.asList(
                        detection(0, 0.9f, 0, 0, 40, 20),
                        detection(0, 0.8f, 50, 0, 90, 20)
                ),
                0.58f,
                0.82f,
                false
        );
        assertEquals(2, result.size());
    }

    private static Detection detection(
            int classId, float confidence, float left, float top, float right, float bottom
    ) {
        return new Detection(
                classId, confidence, left, top, right, bottom, Collections.emptyList()
        );
    }
}
