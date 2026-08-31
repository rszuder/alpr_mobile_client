package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Arrays;

public final class AnalysisViewportVehicleFrameTest {
    @Test
    public void scanFrameContainsOnlyVehiclesInsideWorkingViewport() {
        VehicleCandidate inside = candidate(
                1L,
                new NormalizedBounds(0.20f, 0.25f, 0.65f, 0.75f)
        );
        VehicleCandidate underHud = candidate(
                2L,
                new NormalizedBounds(0.20f, 0.01f, 0.65f, 0.15f)
        );
        VehicleTrackingFrame source = new VehicleTrackingFrame(
                12L,
                13L,
                14L,
                SourceTimestampDomain.CAMERAX_SENSOR,
                15L,
                2L,
                3L,
                4L,
                Arrays.asList(inside, underHud)
        );

        VehicleTrackingFrame filtered =
                AlprPipeline.analysisViewportVehicleFrame(source);

        assertEquals(1, filtered.candidates.size());
        assertEquals(1L, filtered.candidates.get(0).entityId);
        assertEquals(source.sourceSequence, filtered.sourceSequence);
        assertEquals(source.sourceTimestampNanos, filtered.sourceTimestampNanos);
        assertEquals(source.sceneGeneration, filtered.sceneGeneration);
        assertEquals(source.visualEpoch, filtered.visualEpoch);
        assertEquals(source.cameraTransformGeneration,
                filtered.cameraTransformGeneration);
        assertEquals(source.sourceTimestampDomain, filtered.sourceTimestampDomain);
    }

    private static VehicleCandidate candidate(long id, NormalizedBounds bounds) {
        return new VehicleCandidate(
                id,
                id,
                bounds,
                0.9f,
                0.9f,
                0f,
                false,
                0,
                14L,
                15L
        );
    }
}
