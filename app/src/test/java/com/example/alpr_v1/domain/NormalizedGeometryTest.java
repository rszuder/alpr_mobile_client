package com.example.alpr_v1.domain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NormalizedGeometryTest {
    @Test
    public void boundsClampOrderAndCalculateIou() {
        NormalizedBounds bounds = new NormalizedBounds(1.2f, 0.8f, -0.2f, 0.2f);

        assertEquals(0f, bounds.left, 0.0001f);
        assertEquals(0.2f, bounds.top, 0.0001f);
        assertEquals(1f, bounds.right, 0.0001f);
        assertEquals(0.8f, bounds.bottom, 0.0001f);
        assertTrue(bounds.valid());
        assertEquals(0.2f, bounds.iou(new NormalizedBounds(0f, 0.2f, 0.2f, 0.8f)), 0.0001f);
    }

    @Test
    public void quadUsesDefensiveCopies() {
        float[] source = {0.1f, 0.2f, 0.5f, 0.2f, 0.5f, 0.4f, 0.1f, 0.4f};
        NormalizedQuad quad = new NormalizedQuad(source);
        source[0] = 0.9f;
        float[] copy = quad.points();
        copy[1] = 0.9f;

        assertArrayEquals(
                new float[]{0.1f, 0.2f, 0.5f, 0.2f, 0.5f, 0.4f, 0.1f, 0.4f},
                quad.points(),
                0.0001f
        );
        assertEquals(new NormalizedBounds(0.1f, 0.2f, 0.5f, 0.4f), quad.bounds());
    }
}
