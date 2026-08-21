package com.example.alpr_v1.pipeline;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PipelineResultTest {
    @Test
    public void distinguishesPreliminaryAndConfirmedRecognitions() {
        PipelineResult preliminary = new PipelineResult(
                "preliminary",
                "wstępny",
                Collections.singletonList(new PlateRecognition("WE12345", 0.8, false, 1)),
                Collections.emptyList(),
                1280,
                720
        );
        PipelineResult mixed = new PipelineResult(
                "recognized",
                "potwierdzony",
                Arrays.asList(
                        new PlateRecognition("WE12345", 0.9, true, 2),
                        new PlateRecognition("KR1234", 0.7, false, 1)
                ),
                Collections.emptyList(),
                1280,
                720
        );

        assertFalse(preliminary.hasConfirmedRecognition());
        assertTrue(mixed.hasConfirmedRecognition());
    }
}
