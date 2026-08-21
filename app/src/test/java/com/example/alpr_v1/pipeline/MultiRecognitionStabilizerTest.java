package com.example.alpr_v1.pipeline;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultiRecognitionStabilizerTest {
    @Test
    public void stabilizesEveryVisiblePlateIndependently() {
        MultiRecognitionStabilizer stabilizer = new MultiRecognitionStabilizer(2);
        List<PlateRecognition> frame = Arrays.asList(
                new PlateRecognition("WE12345", 0.8),
                new PlateRecognition("KR9876A", 0.7)
        );

        assertTrue(stabilizer.accept(frame).isEmpty());
        List<RecognitionStabilizer.StableResult> stable = stabilizer.accept(frame);

        assertEquals(2, stable.size());
        assertEquals("WE12345", stable.get(0).text);
        assertEquals("KR9876A", stable.get(1).text);
    }

    @Test
    public void removesNumberThatDisappearedFromCurrentFrame() {
        MultiRecognitionStabilizer stabilizer = new MultiRecognitionStabilizer(2);
        List<PlateRecognition> first = Collections.singletonList(
                new PlateRecognition("WE12345", 0.8)
        );
        stabilizer.accept(first);
        assertEquals(1, stabilizer.accept(first).size());

        assertTrue(stabilizer.accept(Collections.emptyList()).isEmpty());
        List<PlateRecognition> second = Collections.singletonList(
                new PlateRecognition("PO54321", 0.9)
        );
        assertTrue(stabilizer.accept(second).isEmpty());
        assertEquals("PO54321", stabilizer.accept(second).get(0).text);
    }

    @Test
    public void brieflyKeepsStableResultAcrossEmptyOcrFrame() {
        MultiRecognitionStabilizer stabilizer = new MultiRecognitionStabilizer(2, 1);
        List<PlateRecognition> frame = Collections.singletonList(
                new PlateRecognition("WE12345", 0.8)
        );
        stabilizer.accept(frame);
        stabilizer.accept(frame);

        assertEquals("WE12345", stabilizer.accept(Collections.emptyList()).get(0).text);
        assertTrue(stabilizer.accept(Collections.emptyList()).isEmpty());
    }
}
