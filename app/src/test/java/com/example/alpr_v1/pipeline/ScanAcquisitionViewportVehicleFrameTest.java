package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class ScanAcquisitionViewportVehicleFrameTest {
    @Test
    public void scanQueueFilterDoesNotRemoveEdgeTrackFromFullFrameSource() {
        VehicleCandidate inside = candidate(
                1L,
                new NormalizedBounds(0.20f, 0.25f, 0.65f, 0.75f)
        );
        VehicleCandidate underHud = candidate(
                2L,
                new NormalizedBounds(0.20f, 0.01f, 0.65f, 0.15f)
        );
        VehicleTrackingFrame source = frame(12L, Arrays.asList(inside, underHud));

        VehicleTrackingFrame filtered =
                AlprPipeline.scanWorkingViewportVehicleFrame(source);

        assertEquals(2, source.candidates.size());
        assertEquals(1, filtered.candidates.size());
        assertEquals(1L, filtered.candidates.get(0).entityId);
        assertFrameIdentityPreserved(source, filtered);
    }

    @Test
    public void sameTrackedEntityBecomesEligibleAfterEnteringWorkingViewport() {
        VehicleTrackingFrame edgeFrame = frame(
                21L,
                Collections.singletonList(candidate(
                        7L,
                        new NormalizedBounds(0.20f, 0.01f, 0.65f, 0.15f)
                ))
        );
        VehicleTrackingFrame enteredFrame = frame(
                22L,
                Collections.singletonList(candidate(
                        7L,
                        new NormalizedBounds(0.20f, 0.20f, 0.65f, 0.45f)
                ))
        );

        assertEquals(
                0,
                AlprPipeline.scanWorkingViewportVehicleFrame(edgeFrame).candidates.size()
        );
        VehicleTrackingFrame eligible =
                AlprPipeline.scanWorkingViewportVehicleFrame(enteredFrame);
        assertEquals(1, eligible.candidates.size());
        assertEquals(7L, eligible.candidates.get(0).entityId);
        assertEquals(7L, eligible.candidates.get(0).vehicleTrackId);
    }

    private static VehicleTrackingFrame frame(
            long sourceFrameId,
            java.util.List<VehicleCandidate> candidates
    ) {
        return new VehicleTrackingFrame(
                sourceFrameId,
                sourceFrameId + 1L,
                sourceFrameId + 2L,
                SourceTimestampDomain.CAMERAX_SENSOR,
                sourceFrameId + 3L,
                2L,
                3L,
                4L,
                candidates
        );
    }

    private static void assertFrameIdentityPreserved(
            VehicleTrackingFrame source,
            VehicleTrackingFrame filtered
    ) {
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
