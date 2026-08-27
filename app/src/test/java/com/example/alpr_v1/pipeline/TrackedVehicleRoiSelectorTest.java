package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import com.example.alpr_v1.vision.Detection;

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

    @Test
    public void rawPolicyUsesUnchangedMpBoxWhileTrackedPolicyUsesCandidateBox() {
        Detection raw = new Detection(
                0, 0.9f, 100f, 50f, 300f, 250f, Collections.emptyList()
        );
        VehicleCandidate tracked = new VehicleCandidate(
                1L,
                11L,
                new NormalizedBounds(0.20f, 0.20f, 0.60f, 0.70f),
                0.9f, 0.9f, 0f, false, 0, 100L, 100L, 0
        );

        VehicleRoi rawRoi = VehicleRoiSelector.selectForPolicy(
                VehicleTrackingPolicy.RAW_MP,
                Collections.singletonList(raw),
                Collections.singletonList(tracked),
                1000, 500, 1, 0f, 0.5f
        ).get(0);
        VehicleRoi trackedRoi = VehicleRoiSelector.selectForPolicy(
                VehicleTrackingPolicy.TRACKED_MP,
                Collections.singletonList(raw),
                Collections.singletonList(tracked),
                1000, 500, 1, 0f, 0.5f
        ).get(0);

        assertEquals(100, rawRoi.left);
        assertEquals(50, rawRoi.top);
        assertEquals(300, rawRoi.right);
        assertEquals(250, rawRoi.bottom);
        assertEquals(200, trackedRoi.left);
        assertEquals(100, trackedRoi.top);
        assertEquals(600, trackedRoi.right);
        assertEquals(350, trackedRoi.bottom);
        assertEquals(rawRoi.entityId, trackedRoi.entityId);
    }

    @Test
    public void experimentPolicyIsAlwaysRawAndUserLiveIsTracked() {
        assertEquals(VehicleTrackingPolicy.RAW_MP,
                VehicleTrackingPolicy.forExperiment(true));
        assertEquals(VehicleTrackingPolicy.TRACKED_MP,
                VehicleTrackingPolicy.forExperiment(false));
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
