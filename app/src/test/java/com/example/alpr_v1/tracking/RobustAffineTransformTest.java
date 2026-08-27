package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RobustAffineTransformTest {
    @Test
    public void estimatesAffineDespiteOutliers() {
        List<SparsePyramidalFlow.Match> matches = new ArrayList<>();
        float angle = (float) Math.toRadians(7.0);
        float scale = 1.08f;
        float a = scale * (float) Math.cos(angle);
        float b = -scale * (float) Math.sin(angle) + 0.03f;
        float c = scale * (float) Math.sin(angle);
        float d = scale * (float) Math.cos(angle);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                SparsePyramidalFlow.Point source =
                        new SparsePyramidalFlow.Point(30f + x * 14f, 25f + y * 12f);
                SparsePyramidalFlow.Point target = new SparsePyramidalFlow.Point(
                        a * source.x + b * source.y + 6f,
                        c * source.x + d * source.y - 4f
                );
                matches.add(new SparsePyramidalFlow.Match(
                        matches.size(), source, target, 1f, 0.2f
                ));
            }
        }
        matches.add(outlier(10f, 10f, 100f, 70f, matches.size()));
        matches.add(outlier(90f, 70f, 20f, 15f, matches.size()));

        RobustAffineTransform.Result result = RobustAffineTransform.estimate(matches);

        assertTrue(result.valid);
        assertTrue(result.inlierCount >= 12);
        assertEquals(a, result.a, 0.015f);
        assertEquals(b, result.b, 0.015f);
        assertEquals(c, result.c, 0.015f);
        assertEquals(d, result.d, 0.015f);
        assertEquals(6f, result.tx, 0.5f);
        assertEquals(-4f, result.ty, 0.5f);
    }

    @Test
    public void rejectsMirroredQuad() {
        RobustAffineTransform.Result mirror = new RobustAffineTransform.Result(
                true, -1f, 0f, 100f, 0f, 1f, 0f, 4, 0f
        );
        List<SparsePyramidalFlow.Point> quad = Arrays.asList(
                new SparsePyramidalFlow.Point(20f, 20f),
                new SparsePyramidalFlow.Point(80f, 20f),
                new SparsePyramidalFlow.Point(80f, 40f),
                new SparsePyramidalFlow.Point(20f, 40f)
        );

        assertFalse(RobustAffineTransform.reasonableQuad(mirror, quad, 120, 90));
    }

    private static SparsePyramidalFlow.Match outlier(
            float x,
            float y,
            float targetX,
            float targetY,
            int index
    ) {
        return new SparsePyramidalFlow.Match(
                index,
                new SparsePyramidalFlow.Point(x, y),
                new SparsePyramidalFlow.Point(targetX, targetY),
                1f,
                0.1f
        );
    }
}
