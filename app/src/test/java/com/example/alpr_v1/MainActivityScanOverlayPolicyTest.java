package com.example.alpr_v1;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.acquisition.AcquisitionDirective;
import com.example.alpr_v1.acquisition.AcquisitionDirectiveAction;
import com.example.alpr_v1.acquisition.AcquisitionQueueSnapshot;
import com.example.alpr_v1.acquisition.PlateAnchor;
import com.example.alpr_v1.acquisition.ScanAcquisitionSnapshot;
import com.example.alpr_v1.acquisition.ScanAcquisitionStats;
import com.example.alpr_v1.acquisition.ScanRunState;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.TargetSessionState;
import com.example.alpr_v1.pipeline.MtReason;
import com.example.alpr_v1.pipeline.MtWorkKind;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PlateVehicleAssociation;
import com.example.alpr_v1.pipeline.TemporalCharacterAggregator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public final class MainActivityScanOverlayPolicyTest {
    @Test
    public void activeEntityWinsOverOlderTemporalObservation() {
        ScanAcquisitionSnapshot scan = snapshot(20L, 2L);

        assertEquals(
                2L,
                MainActivity.scanPresentationEntityId(
                        scan,
                        Arrays.asList(
                                observation(1L, 101L, 18L),
                                observation(2L, 202L, 19L)
                        )
                )
        );
    }

    @Test
    public void releasedResultUsesEntityFromNewestMtRevision() {
        ScanAcquisitionSnapshot scan = snapshot(31L, 0L);

        assertEquals(
                3L,
                MainActivity.scanPresentationEntityId(
                        scan,
                        Arrays.asList(
                                observation(1L, 101L, 22L),
                                observation(3L, 303L, 30L),
                                observation(2L, 202L, 27L)
                        )
                )
        );
    }

    @Test
    public void onlyNewestPlateTrackOfPresentationEntityIsAllowed() {
        Set<Long> tracks = MainActivity.scanPlateTrackIds(
                7L,
                null,
                Arrays.asList(
                        observation(7L, 701L, 40L),
                        observation(8L, 801L, 42L),
                        observation(7L, 702L, 43L)
                )
        );

        assertEquals(Collections.singleton(702L), tracks);
    }

    @Test
    public void activeAnchorIsTheSingleAllowedPlateTrack() {
        PlateAnchor anchor = new PlateAnchor(
                7L, 70L, 777L, null, null,
                ContinuityStamp.initial(1L), 50L
        );

        assertEquals(
                Collections.singleton(777L),
                MainActivity.scanPlateTrackIds(
                        7L,
                        anchor,
                        Arrays.asList(
                                observation(7L, 701L, 49L),
                                observation(7L, 702L, 50L)
                        )
                )
        );
    }

    private static ScanAcquisitionSnapshot snapshot(
            long directiveRevision,
            long activeEntityId
    ) {
        AcquisitionDirectiveAction action = activeEntityId > 0L
                ? AcquisitionDirectiveAction.REQUEST_EXACT_ENTITY_MT
                : AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET;
        return new ScanAcquisitionSnapshot(
                1L,
                ScanRunState.RUNNING,
                0L,
                0L,
                AcquisitionQueueSnapshot.empty(1L),
                activeEntityId > 0L ? 10L : 0L,
                activeEntityId,
                activeEntityId > 0L
                        ? TargetSessionState.ACQUIRING_PLATE : null,
                0,
                0,
                0L,
                0L,
                new AcquisitionDirective(
                        directiveRevision,
                        action,
                        1L,
                        activeEntityId > 0L ? 10L : 0L,
                        activeEntityId,
                        "test"
                ),
                false,
                null,
                ScanAcquisitionStats.empty()
        );
    }

    private static PlateObservation observation(
            long entityId,
            long plateTrackId,
            long directiveRevision
    ) {
        return new PlateObservation(
                plateTrackId,
                PlateVehicleAssociation.direct(entityId, entityId + 1000L, "test"),
                MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,
                1L,
                null,
                "TEST",
                0.9,
                0.8,
                false,
                1,
                Collections.emptyList(),
                0L,
                1L,
                0.5f,
                null,
                null,
                PlateGeometry.unavailable(),
                true,
                true,
                "TEST",
                true,
                1,
                TemporalCharacterAggregator.LAYOUT_SINGLE_ROW,
                Collections.emptyList(),
                "",
                "TEST",
                ContinuityStamp.initial(1L),
                directiveRevision
        );
    }
}
