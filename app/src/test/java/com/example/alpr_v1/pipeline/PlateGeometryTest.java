package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PlateGeometryTest {
    @Test
    public void preservesPixelBoxAndNormalizesOrderedQuad() {
        Detection detection = new Detection(
                0, 0.9f, 20f, 10f, 80f, 30f, java.util.Collections.emptyList()
        );
        PlateGeometry geometry = PlateGeometry.from(
                100,
                50,
                detection,
                Arrays.asList(
                        new Point2(80f, 30f),
                        new Point2(20f, 10f),
                        new Point2(20f, 30f),
                        new Point2(80f, 10f)
                )
        );

        assertTrue(geometry.available());
        assertEquals(60.0, geometry.bboxWidthPx(), 0.0001);
        assertEquals(20.0, geometry.bboxHeightPx(), 0.0001);
        assertEquals(0.24, geometry.bboxAreaRatio, 0.0001);
        assertEquals(0.24, geometry.quadAreaRatio, 0.0001);
        assertEquals(0.2, geometry.cornersNorm.get(0).x, 0.0001);
        assertEquals(0.2, geometry.cornersNorm.get(0).y, 0.0001);
        assertEquals(4, geometry.cornersNorm.size());
    }
}
