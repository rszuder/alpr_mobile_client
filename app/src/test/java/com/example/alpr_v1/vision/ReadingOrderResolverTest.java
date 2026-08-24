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

    @Test
    public void keepsJitteredCharactersInSingleRow() {
        List<Detection> detections = Arrays.asList(
                box(2, 70, 15),
                box(0, 10, 10),
                box(3, 100, 12),
                box(1, 40, 17)
        );

        List<List<Detection>> rows =
                ReadingOrderResolver.rows(detections);

        assertEquals(1, rows.size());

        assertEquals(
                Arrays.asList(0, 1, 2, 3),
                Arrays.asList(
                        rows.get(0).get(0).classId,
                        rows.get(0).get(1).classId,
                        rows.get(0).get(2).classId,
                        rows.get(0).get(3).classId
                )
        );
    }

    @Test
    public void keepsTwoRowsWithDifferentCharacterHeights() {
        List<Detection> detections = Arrays.asList(
                new Detection(
                        3, 0.9f,
                        70, 82, 92, 132,
                        Collections.emptyList()
                ),
                new Detection(
                        0, 0.9f,
                        10, 10, 30, 40,
                        Collections.emptyList()
                ),
                new Detection(
                        2, 0.9f,
                        35, 78, 57, 128,
                        Collections.emptyList()
                ),
                new Detection(
                        1, 0.9f,
                        40, 13, 60, 43,
                        Collections.emptyList()
                )
        );

        List<List<Detection>> rows =
                ReadingOrderResolver.rows(detections);

        assertEquals(2, rows.size());

        assertEquals(
                Arrays.asList(0, 1),
                Arrays.asList(
                        rows.get(0).get(0).classId,
                        rows.get(0).get(1).classId
                )
        );

        assertEquals(
                Arrays.asList(2, 3),
                Arrays.asList(
                        rows.get(1).get(0).classId,
                        rows.get(1).get(1).classId
                )
        );
    }
    private static Detection box(int classId, float x, float y) {
        return new Detection(classId, 0.9f, x, y, x + 20, y + 30, Collections.emptyList());
    }
}
