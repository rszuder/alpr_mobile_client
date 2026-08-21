package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class YoloEndToEndDecoderTest {
    @Test
    public void decodesAnchorsFirstPoseWithoutAdditionalNms() {
        // Dwa rekordy [x1, y1, x2, y2, confidence, class_id, keypoint x/y/confidence].
        float[] tensor = new float[]{
                10, 20, 60, 40, 0.90f, 0, 12, 22, 0.80f,
                11, 21, 61, 41, 0.75f, 0, 13, 23, 0.70f
        };
        YoloOutputSpec spec = new YoloOutputSpec(
                1, 1, false, false, false, 100, 80, 0.25f, 0.45f
        );

        List<Detection> detections = YoloEndToEndDecoder.decode(tensor, 2, 9, spec);

        assertEquals(2, detections.size());
        Detection first = detections.get(0);
        assertEquals(10, first.left, 0.001f);
        assertEquals(20, first.top, 0.001f);
        assertEquals(60, first.right, 0.001f);
        assertEquals(40, first.bottom, 0.001f);
        assertEquals(12, first.keypoints.get(0).x, 0.001f);
        assertEquals(22, first.keypoints.get(0).y, 0.001f);
        assertEquals(0.80f, first.keypoints.get(0).confidence, 0.001f);
    }

    @Test
    public void decodesNormalizedChannelsFirstDetectionAndFiltersConfidence() {
        // Kanały [x1, y1, x2, y2, confidence, class_id], po dwa rekordy w każdym kanale.
        float[] tensor = new float[]{
                0.1f, 0.2f,
                0.25f, 0.3f,
                0.6f, 0.7f,
                0.75f, 0.8f,
                0.8f, 0.1f,
                1, 0
        };
        YoloOutputSpec spec = new YoloOutputSpec(
                2, 0, false, true, true, 200, 100, 0.25f, 0.45f
        );

        List<Detection> detections = YoloEndToEndDecoder.decode(tensor, 6, 2, spec);

        assertEquals(1, detections.size());
        Detection detection = detections.get(0);
        assertEquals(1, detection.classId);
        assertEquals(20, detection.left, 0.001f);
        assertEquals(25, detection.top, 0.001f);
        assertEquals(120, detection.right, 0.001f);
        assertEquals(75, detection.bottom, 0.001f);
    }

    @Test
    public void decodesDetectionsFirstPoseWithTwoDimensionalKeypoints() {
        // Rzeczywisty kontrakt eksportera: 6 pól detekcji + 4 punkty po x/y.
        float[] tensor = new float[]{
                10, 20, 60, 40, 0.90f, 0,
                12, 22,
                58, 22,
                58, 38,
                12, 38
        };
        YoloOutputSpec spec = new YoloOutputSpec(
                1, 4, 2, false, false, false,
                100, 80, 0.25f, 0.45f, 4, 5
        );

        List<Detection> detections = YoloEndToEndDecoder.decode(tensor, 1, 14, spec);

        assertEquals(1, detections.size());
        Detection detection = detections.get(0);
        assertEquals(4, detection.keypoints.size());
        assertEquals(12, detection.keypoints.get(0).x, 0.001f);
        assertEquals(22, detection.keypoints.get(0).y, 0.001f);
        assertEquals(1f, detection.keypoints.get(0).confidence, 0.001f);
        assertEquals(58, detection.keypoints.get(2).x, 0.001f);
        assertEquals(38, detection.keypoints.get(2).y, 0.001f);
    }
}
