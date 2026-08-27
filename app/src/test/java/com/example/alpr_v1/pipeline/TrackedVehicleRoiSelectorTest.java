package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TrackedVehicleRoiSelectorTest {
    @Test
    public void overlappingEntitiesKeepIndependentIdentityWithoutSecondNms() {
        VehicleCandidate first = candidate(1L, 11L, 0.10f, 0.60f, 0.9f);
        VehicleCandidate second = candidate(2L, 12L, 0.12f, 0.62f, 0.8f);

        List<VehicleRoi> rois = VehicleRoiSelector.selectTrackedCandidates(
                Arrays.asList(first, second), 1000, 500, 8, 0f
        );

        assertEquals(2, rois.size());
        assertEquals(1L, rois.get(0).entityId);
        assertEquals(2L, rois.get(1).entityId);
    }

    private static VehicleCandidate candidate(
            long entityId,
            long trackId,
            float left,
            float right,
            float confidence
    ) {
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(left, 0.1f, right, 0.9f),
                confidence, confidence, 0f, false, 0, 100L, 100L
        );
    }
}
