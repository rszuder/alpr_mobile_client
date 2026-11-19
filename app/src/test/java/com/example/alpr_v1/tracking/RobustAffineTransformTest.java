package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RobustAffineTransformTest {
    @Test public void fullConsensusUsesTheSameAllPointRefinement()throws Exception {
        List<SparsePyramidalFlow.Match> matches=new ArrayList<>();
        for(int y=0;y<5;y++)for(int x=0;x<6;x++) {
            int index=matches.size();float px=12+11*x,py=14+16*y;
            matches.add(new SparsePyramidalFlow.Match(index,new SparsePyramidalFlow.Point(px,py),
                    new SparsePyramidalFlow.Point(1.01f*px+.02f*py+3+((index%7)-3)*.01f,
                            -.01f*px+.99f*py-2+((index%5)-2)*.01f),1,.1f));
        }
        int[] indices=new int[matches.size()];for(int i=0;i<indices.length;i++)indices[i]=i;
        java.lang.reflect.Method fit=RobustAffineTransform.class.getDeclaredMethod("fit",List.class,int[].class);fit.setAccessible(true);
        RobustAffineTransform.Result reference=(RobustAffineTransform.Result)fit.invoke(null,matches,indices);
        RobustAffineTransform.Result result=RobustAffineTransform.estimate(matches);
        assertTrue(result.valid);assertEquals(30,result.inlierCount);
        assertEquals(reference.a,result.a,0f);assertEquals(reference.b,result.b,0f);assertEquals(reference.tx,result.tx,0f);
        assertEquals(reference.c,result.c,0f);assertEquals(reference.d,result.d,0f);assertEquals(reference.ty,result.ty,0f);
        assertEquals(1.01f,result.a,.002f);assertEquals(3f,result.tx,.1f);assertEquals(-2f,result.ty,.1f);
    }
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
