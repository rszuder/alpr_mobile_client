package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TemporalCharacterAggregatorTest {
    private static final java.util.List<String> LABELS = Arrays.asList("A", "B", "1", "2");

    @Test
    public void becomesStableAfterEveryPositionAgreesTwice() {
        TemporalCharacterAggregator aggregator = new TemporalCharacterAggregator();

        TemporalCharacterAggregator.Result first = aggregator.accept(sequence(0, 1, 2), LABELS);
        assertEquals(0, aggregator.expectedCount());
        TemporalCharacterAggregator.Result second = aggregator.accept(sequence(0, 1, 2), LABELS);

        assertFalse(first.stable);
        assertTrue(second.stable);
        assertEquals("AB1", second.text);
        assertEquals(3, aggregator.expectedCount());
    }

    @Test
    public void doesNotConfirmDisputedCharacterAfterTwoFrames() {
        TemporalCharacterAggregator aggregator = new TemporalCharacterAggregator();
        aggregator.accept(sequence(0, 1, 2), LABELS);

        TemporalCharacterAggregator.Result result = aggregator.accept(sequence(0, 3, 2), LABELS);

        assertFalse(result.stable);
    }

    @Test
    public void selectsLengthSupportedByMoreFrames() {
        TemporalCharacterAggregator aggregator = new TemporalCharacterAggregator();
        aggregator.accept(sequence(0, 1), LABELS);
        aggregator.accept(sequence(0, 1, 2), LABELS);
        aggregator.accept(sequence(0, 1, 2), LABELS);

        assertEquals(3, aggregator.expectedCount());
        assertEquals("AB1", aggregator.current().text);
    }

    @Test
    public void keepsDifferentLayoutsWithSameLengthSeparate() {
        TemporalCharacterAggregator aggregator =
                new TemporalCharacterAggregator();

        /*
         * Ta sama całkowita liczba znaków:
         *
         * pierwsza obserwacja:
         * A B 1 2
         *
         * drugi wariant:
         *
         * A B
         * 1 2
         *
         * Nie mogą uczestniczyć w jednym stanie konsensusu.
         */
        aggregator.accept(
                sequence(
                        0, 1, 2, 3
                ),
                LABELS
        );

        aggregator.accept(
                twoRowSequence(
                        0, 1,
                        2, 3
                ),
                LABELS
        );

        TemporalCharacterAggregator.Result result =
                aggregator.accept(
                        twoRowSequence(
                                0, 1,
                                2, 3
                        ),
                        LABELS
                );

        assertEquals(
                "two_row",
                result.layout
        );

        assertEquals(
                2,
                result.rowCount
        );

        assertEquals(
                Arrays.asList(
                        2,
                        2
                ),
                result.rowCounts
        );

        assertEquals(
                2,
                result.observations
        );

        assertTrue(
                result.stable
        );
    }


    @Test
    public void exposesExpectedTwoRowStructureAfterTwoObservations() {
        TemporalCharacterAggregator aggregator =
                new TemporalCharacterAggregator();

        aggregator.accept(
                twoRowSequence(
                        0, 1,
                        2, 3
                ),
                LABELS
        );

        assertEquals(
                Collections.emptyList(),
                aggregator.expectedRowCounts()
        );

        aggregator.accept(
                twoRowSequence(
                        0, 1,
                        2, 3
                ),
                LABELS
        );

        assertEquals(
                4,
                aggregator.expectedCount()
        );

        assertEquals(
                "two_row",
                aggregator.expectedLayout()
        );

        assertEquals(
                Arrays.asList(
                        2,
                        2
                ),
                aggregator.expectedRowCounts()
        );
    }
    private static java.util.List<Detection> sequence(int... classIds) {
        java.util.List<Detection> result = new java.util.ArrayList<>();
        for (int i = 0; i < classIds.length; i++) {
            result.add(new Detection(
                    classIds[i], 0.9f,
                    i * 20f, 0, i * 20f + 15f, 30,
                    Collections.emptyList()
            ));
        }
        return result;
    }

    private static java.util.List<Detection> twoRowSequence(
            int topLeft,
            int topRight,
            int bottomLeft,
            int bottomRight
    ) {
        return Arrays.asList(
                new Detection(
                        topLeft,
                        0.9f,
                        10,
                        10,
                        25,
                        40,
                        Collections.emptyList()
                ),
                new Detection(
                        topRight,
                        0.9f,
                        40,
                        10,
                        55,
                        40,
                        Collections.emptyList()
                ),
                new Detection(
                        bottomLeft,
                        0.9f,
                        10,
                        70,
                        25,
                        100,
                        Collections.emptyList()
                ),
                new Detection(
                        bottomRight,
                        0.9f,
                        40,
                        70,
                        55,
                        100,
                        Collections.emptyList()
                )
        );
    }
}
