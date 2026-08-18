package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReadingOrderResolverTest {
    @Test
    public void readsTwoRowsTopThenBottom() {
        List<String> labels = Arrays.asList("A", "B", "1", "2");
        List<Detection> detections = Arrays.asList(
                box(3, 60, 60),
                box(1, 60, 10),
                box(2, 10, 60),
                box(0, 10, 10)
        );
        assertEquals("AB12", ReadingOrderResolver.text(detections, labels));
    }

    private static Detection box(int classId, float x, float y) {
        return new Detection(classId, 0.9f, x, y, x + 20, y + 30, Collections.emptyList());
    }
}
