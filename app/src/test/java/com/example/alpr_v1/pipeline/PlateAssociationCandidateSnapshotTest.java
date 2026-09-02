package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlateAssociationCandidateSnapshotTest {
    @Test
    public void snapshotSurvivesConcurrentLatestFrameReplacement() {
        List<VehicleCandidate> latest = new ArrayList<>();
        latest.add(candidate(4L, 14L, 0.0f, 0.3f, 0.4f, 0.8f));
        latest.add(candidate(5L, 15L, 0.6f, 0.3f, 1.0f, 0.8f));

        List<VehicleCandidate> snapshot =
                MobileAlprEngine.snapshotPlateAssociationCandidates(
                        latest,
                        Collections.emptyList()
                );
        latest.clear();

        assertEquals(2, snapshot.size());
        assertEquals(4L, snapshot.get(0).entityId);
        assertEquals(5L, snapshot.get(1).entityId);
    }

    @Test
    public void scheduledRoiBackfillsCandidateMissingFromLatestFrame() {
        VehicleCandidate candidate = candidate(
                6L, 16L, 0.3f, 0.3f, 0.7f, 0.8f
        );
        VehicleRoi roi = new VehicleRoi(candidate, 20, 20, 180, 90);

        List<VehicleCandidate> snapshot =
                MobileAlprEngine.snapshotPlateAssociationCandidates(
                        Collections.emptyList(),
                        Collections.singletonList(roi)
                );

        assertEquals(1, snapshot.size());
        assertEquals(6L, snapshot.get(0).entityId);
    }

    @Test
    public void slowFullFrameInferenceCanUseRecentSceneSnapshot() {
        List<VehicleCandidate> retained = Collections.singletonList(
                candidate(7L, 17L, 0.0f, 0.3f, 0.4f, 0.8f)
        );
        long capturedAt = 1_000_000_000L;

        List<VehicleCandidate> selected =
                MobileAlprEngine.retainedPlateAssociationCandidates(
                        retained,
                        capturedAt,
                        capturedAt + 8_000_000_000L
                );

        assertEquals(1, selected.size());
        assertEquals(7L, selected.get(0).entityId);
    }

    @Test
    public void expiredSceneSnapshotCannotAssociateAPlate() {
        List<VehicleCandidate> retained = Collections.singletonList(
                candidate(7L, 17L, 0.0f, 0.3f, 0.4f, 0.8f)
        );
        long capturedAt = 1_000_000_000L;

        List<VehicleCandidate> selected =
                MobileAlprEngine.retainedPlateAssociationCandidates(
                        retained,
                        capturedAt,
                        capturedAt
                                + MobileAlprEngine.PLATE_ASSOCIATION_MEMORY_NANOS
                                + 1L
                );

        assertEquals(0, selected.size());
    }

    private static VehicleCandidate candidate(
            long entityId,
            long trackId,
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new VehicleCandidate(
                entityId,
                trackId,
                new NormalizedBounds(left, top, right, bottom),
                0.9f,
                0.9f,
                0f,
                false,
                0,
                100L,
                100L
        );
    }
}
