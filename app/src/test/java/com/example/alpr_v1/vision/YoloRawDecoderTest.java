package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class YoloRawDecoderTest {
    @Test
    public void decodesChannelsFirstDetection() {
        // [cx, cy, w, h, class0, class1] x one anchor
        float[] tensor = new float[]{50, 40, 20, 10, 0.1f, 0.9f};
        YoloOutputSpec spec = new YoloOutputSpec(2, 0, false, true, false, 100, 100, 0.25f, 0.45f);
        List<Detection> detections = YoloRawDecoder.decode(tensor, 6, 1, spec);
        assertEquals(1, detections.size());
        Detection detection = detections.get(0);
        assertEquals(1, detection.classId);
        assertEquals(40, detection.left, 0.001f);
        assertEquals(35, detection.top, 0.001f);
        assertEquals(60, detection.right, 0.001f);
        assertEquals(45, detection.bottom, 0.001f);
    }
}
