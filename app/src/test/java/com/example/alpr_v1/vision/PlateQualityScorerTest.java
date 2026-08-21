package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlateQualityScorerTest {
    @Test
    public void scoresConsistentPlateGeometryHighly() {
        Detection detection = new Detection(
                0, 0.92f, 100, 100, 300, 150,
                Arrays.asList(
                        point(100, 100, 0.95f), point(300, 100, 0.94f),
                        point(300, 150, 0.96f), point(100, 150, 0.93f)
                )
        );

        PlateQualityScorer.Score score = PlateQualityScorer.compute(
                detection, detection.keypoints, 640, 480
        );

        assertTrue(score.validQuad);
        assertTrue(score.total >= 0.85f);
    }

    @Test
    public void rejectsCollapsedQuadrilateral() {
        Detection detection = new Detection(
                0, 0.9f, 100, 100, 300, 150,
                Arrays.asList(
                        point(100, 100, 0.9f), point(300, 100, 0.9f),
                        point(300, 100, 0.9f), point(100, 150, 0.9f)
                )
        );

        PlateQualityScorer.Score score = PlateQualityScorer.compute(
                detection, detection.keypoints, 640, 480
        );

        assertFalse(score.validQuad);
        assertTrue(score.total < 0.85f);
    }

    private static Point2 point(float x, float y, float confidence) {
        return new Point2(x, y, confidence);
    }
}
