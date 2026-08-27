package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;

import org.junit.Test;

public class VehicleRoiTest {
    @Test
    public void preservesCandidateIdentityAndPixelGeometry() {
        VehicleCandidate candidate = new VehicleCandidate(
                8L,
                13L,
                new NormalizedBounds(0.1f, 0.2f, 0.5f, 0.8f),
                0.9f,
                0.8f,
                0.3f,
                false,
                0,
                100L,
                100L
        );

        VehicleRoi roi = new VehicleRoi(candidate, 10, 20, 110, 80);

        assertEquals(8L, roi.entityId);
        assertEquals(13L, roi.vehicleTrackId);
        assertSame(candidate, roi.candidate);
        assertEquals(100, roi.width());
        assertEquals(60, roi.height());
        assertEquals(6_000L, roi.area());
    }
}
