package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VehicleRoiSelectorTest {
    @Test
    public void expandsRegionAndClampsItToImage() {
        Detection vehicle = detection(0.9f, 0, 10, 100, 90);

        VehicleRoiSelector.Region region = VehicleRoiSelector.select(
                Collections.singletonList(vehicle), 120, 100, 1, 0.20f, 0.5f
        ).get(0);

        assertEquals(0, region.left);
        assertEquals(0, region.top);
        assertEquals(120, region.right);
        assertEquals(100, region.bottom);
    }

    @Test
    public void keepsOnlyDominantRegionsAndSuppressesDuplicates() {
        Detection dominant = detection(0.9f, 10, 10, 100, 90);
        Detection duplicate = detection(0.8f, 12, 12, 98, 88);
        Detection secondary = detection(0.7f, 120, 20, 180, 80);
        Detection third = detection(0.6f, 200, 20, 230, 60);

        List<VehicleRoiSelector.Region> regions = VehicleRoiSelector.select(
                Arrays.asList(third, duplicate, secondary, dominant),
                240, 120, 2, 0f, 0.5f
        );

        assertEquals(2, regions.size());
        assertTrue(regions.get(0).area() > regions.get(1).area());
        assertEquals(120, regions.get(1).left);
    }

    @Test
    public void convertsNormalizedAutoZoomRoiToSourcePixels() {
        VehicleRoiSelector.Region region = VehicleRoiSelector.normalizedRegion(
                1000,
                500,
                0.30f,
                0.20f,
                0.70f,
                0.80f
        );

        assertEquals(300, region.left);
        assertEquals(100, region.top);
        assertEquals(700, region.right);
        assertEquals(400, region.bottom);
    }

    @Test
    public void clampsAutoZoomRoiAtFrameEdges() {
        VehicleRoiSelector.Region region = VehicleRoiSelector.normalizedRegion(
                100,
                200,
                -0.20f,
                0.75f,
                1.30f,
                1.20f
        );

        assertEquals(0, region.left);
        assertEquals(150, region.top);
        assertEquals(100, region.right);
        assertEquals(200, region.bottom);
    }

    private static Detection detection(
            float confidence,
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new Detection(
                0, confidence, left, top, right, bottom, Collections.emptyList()
        );
    }
}
