package com.example.alpr_v1.vision;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GeometryUtilsTest {
    @Test
    public void ordersCornersAsTlTrBrBl() {
        List<Point2> result = GeometryUtils.orderQuad(Arrays.asList(
                new Point2(90, 80),
                new Point2(10, 20),
                new Point2(15, 90),
                new Point2(100, 10)
        ));
        assertPoint(result.get(0), 10, 20);
        assertPoint(result.get(1), 100, 10);
        assertPoint(result.get(2), 90, 80);
        assertPoint(result.get(3), 15, 90);
    }

    private static void assertPoint(Point2 point, float x, float y) {
        assertEquals(x, point.x, 0.001f);
        assertEquals(y, point.y, 0.001f);
    }
}
