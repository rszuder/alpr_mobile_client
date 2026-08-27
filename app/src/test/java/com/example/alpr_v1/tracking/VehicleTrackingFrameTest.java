package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.NormalizedBounds;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class VehicleTrackingFrameTest {
    @Test
    public void preservesIdentityTimestampsAndDefensiveCandidateList() {
        VehicleCandidate candidate = candidate(7L, 11L, 100L, 160L);
        List<VehicleCandidate> source = new ArrayList<>();
        source.add(candidate);

        VehicleTrackingFrame frame = new VehicleTrackingFrame(
                3L, 100L, 160L, 2L, source
        );
        source.clear();

        assertEquals(3L, frame.sourceFrameId);
        assertEquals(2L, frame.sceneGeneration);
        assertEquals(1, frame.candidates.size());
        assertEquals(7L, frame.candidates.get(0).entityId);
        assertEquals(60L, frame.candidates.get(0).predictionAgeNanos);
        assertTrue(frame.candidates.get(0).predicted);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void candidateListIsImmutable() {
        VehicleTrackingFrame frame = new VehicleTrackingFrame(
                1L,
                10L,
                10L,
                0L,
                java.util.Collections.singletonList(candidate(1L, 2L, 10L, 10L))
        );

        frame.candidates.clear();
    }

    private static VehicleCandidate candidate(
            long entityId,
            long trackId,
            long measurementNanos,
            long snapshotNanos
    ) {
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(0.1f, 0.2f, 0.5f, 0.8f),
                0.9f,
                0.7f,
                0.2f,
                snapshotNanos > measurementNanos,
                snapshotNanos > measurementNanos ? 1 : 0,
                measurementNanos,
                snapshotNanos
        );
    }
}
