package com.example.alpr_v1.vision;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DetectionCoordinateMapperTest {
    @Test
    public void removesLetterboxAndAddsRoiOffset() {
        PreparedInput input = new PreparedInput(
                ByteBuffer.allocate(0), 2f, 10f, 20f, 100, 50
        );
        Detection modelDetection = new Detection(
                0, 0.9f, 30f, 40f, 110f, 100f,
                Collections.singletonList(new Point2(50f, 60f, 0.8f))
        );

        Detection source = DetectionCoordinateMapper.toSource(
                modelDetection, input, 200, 300
        );

        assertEquals(210f, source.left, 0.001f);
        assertEquals(310f, source.top, 0.001f);
        assertEquals(250f, source.right, 0.001f);
        assertEquals(340f, source.bottom, 0.001f);
        assertEquals(220f, source.keypoints.get(0).x, 0.001f);
        assertEquals(320f, source.keypoints.get(0).y, 0.001f);
    }
}
