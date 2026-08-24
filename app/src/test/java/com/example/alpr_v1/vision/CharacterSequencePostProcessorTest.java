package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CharacterSequencePostProcessorTest {
    @Test
    public void removesDifferentClassPredictionFromSameCharacterSlot() {
        List<Detection> result = CharacterSequencePostProcessor.process(
                Arrays.asList(
                        detection(0, 0.90f, 10, 10, 30, 50),
                        detection(1, 0.72f, 11, 11, 31, 51),
                        detection(2, 0.88f, 40, 10, 60, 50)
                ),
                0
        );

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).classId);
        assertEquals(2, result.get(1).classId);
    }

    @Test
    public void removesVerticalGeometryOutlier() {
        List<Detection> result = CharacterSequencePostProcessor.process(
                Arrays.asList(
                        detection(0, 0.90f, 10, 10, 30, 50),
                        detection(1, 0.90f, 40, 11, 60, 51),
                        detection(2, 0.60f, 70, 70, 90, 90)
                ),
                0
        );

        assertEquals(2, result.size());
    }

    @Test
    public void preservesTwoRowReadingOrder() {
        List<Detection> result = CharacterSequencePostProcessor.process(
                Arrays.asList(
                        detection(2, 0.9f, 40, 60, 60, 100),
                        detection(0, 0.9f, 10, 10, 30, 50),
                        detection(3, 0.9f, 70, 60, 90, 100),
                        detection(1, 0.9f, 40, 10, 60, 50)
                ),
                0
        );

        assertEquals(Arrays.asList(0, 1, 2, 3), Arrays.asList(
                result.get(0).classId,
                result.get(1).classId,
                result.get(2).classId,
                result.get(3).classId
        ));
    }
    @Test
    public void expectedCountStillLimitsSingleRow() {
        List<Detection> result =
                CharacterSequencePostProcessor.process(
                        Arrays.asList(
                                detection(
                                        0, 0.95f,
                                        10, 10, 30, 50
                                ),
                                detection(
                                        1, 0.94f,
                                        40, 10, 60, 50
                                ),
                                detection(
                                        2, 0.93f,
                                        70, 10, 90, 50
                                ),
                                detection(
                                        3, 0.40f,
                                        100, 10, 120, 50
                                )
                        ),
                        3
                );

        assertEquals(3, result.size());
    }

    @Test
    public void expectedCountDoesNotFlattenTwoRows() {
        List<Detection> result =
                CharacterSequencePostProcessor.process(
                        Arrays.asList(
                                detection(
                                        0, 0.98f,
                                        10, 10, 30, 50
                                ),
                                detection(
                                        1, 0.97f,
                                        40, 10, 60, 50
                                ),
                                detection(
                                        2, 0.96f,
                                        70, 10, 90, 50
                                ),
                                detection(
                                        3, 0.82f,
                                        10, 70, 30, 110
                                ),
                                detection(
                                        4, 0.81f,
                                        40, 70, 60, 110
                                ),
                                detection(
                                        5, 0.80f,
                                        70, 70, 90, 110
                                )
                        ),
                        4
                );

        assertEquals(6, result.size());

        assertEquals(
                Arrays.asList(
                        0, 1, 2,
                        3, 4, 5
                ),
                Arrays.asList(
                        result.get(0).classId,
                        result.get(1).classId,
                        result.get(2).classId,
                        result.get(3).classId,
                        result.get(4).classId,
                        result.get(5).classId
                )
        );
    }
    private static Detection detection(
            int classId, float confidence, float left, float top, float right, float bottom
    ) {
        return new Detection(
                classId, confidence, left, top, right, bottom, Collections.emptyList()
        );
    }
}
