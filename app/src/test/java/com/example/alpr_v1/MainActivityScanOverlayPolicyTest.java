package com.example.alpr_v1;

import static org.junit.Assert.assertEquals;

import android.graphics.RectF;

import com.example.alpr_v1.acquisition.AcquisitionDirective;
import com.example.alpr_v1.acquisition.AcquisitionDirectiveAction;
import com.example.alpr_v1.acquisition.AcquisitionQueueSnapshot;
import com.example.alpr_v1.acquisition.PlateAnchor;
import com.example.alpr_v1.acquisition.ScanAcquisitionSnapshot;
import com.example.alpr_v1.acquisition.ScanAcquisitionStats;
import com.example.alpr_v1.acquisition.ScanRunState;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.domain.TargetSessionState;
import com.example.alpr_v1.pipeline.MtReason;
import com.example.alpr_v1.pipeline.MtWorkKind;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.PlateVehicleAssociation;
import com.example.alpr_v1.pipeline.TemporalCharacterAggregator;
import com.example.alpr_v1.ui.OverlayItem;

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
    public void releasedResultCannotRecreateFloatingPlateOverlay() {
        ScanAcquisitionSnapshot scan = snapshot(31L, 0L);

        assertEquals(
                0L,
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
    public void releaseBarrierSeparatesPlateAFromFreshPlateB() {
        PlateObservation plateA = observation(1L, 101L, 20L);
        PlateObservation plateB = observation(2L, 202L, 23L);

        ScanAcquisitionSnapshot activeA = snapshot(20L, 1L);
        long entityA = MainActivity.scanPresentationEntityId(
                activeA,
                Collections.singletonList(plateA)
        );
        assertEquals(1L, entityA);
        assertEquals(
                Collections.singleton(101L),
                MainActivity.scanPlateTrackIds(
                        entityA,
                        null,
                        Collections.singletonList(plateA)
                )
        );

        ScanAcquisitionSnapshot releaseA = snapshot(21L, 0L);
        long releasedEntity = MainActivity.scanPresentationEntityId(
                releaseA,
                Collections.singletonList(plateA)
        );
        assertEquals(0L, releasedEntity);
        assertEquals(
                Collections.emptySet(),
                MainActivity.scanPlateTrackIds(
                        releasedEntity,
                        null,
                        Collections.singletonList(plateA)
                )
        );

        ScanAcquisitionSnapshot selectedB = snapshot(22L, 2L);
        long entityBWaitingForMt = MainActivity.scanPresentationEntityId(
                selectedB,
                Collections.singletonList(plateA)
        );
        assertEquals(2L, entityBWaitingForMt);
        assertEquals(
                Collections.emptySet(),
                MainActivity.scanPlateTrackIds(
                        entityBWaitingForMt,
                        null,
                        Collections.singletonList(plateA)
                )
        );

        long entityBAfterMt = MainActivity.scanPresentationEntityId(
                snapshot(23L, 2L),
                Arrays.asList(plateA, plateB)
        );
        assertEquals(2L, entityBAfterMt);
        assertEquals(
                Collections.singleton(202L),
                MainActivity.scanPlateTrackIds(
                        entityBAfterMt,
                        null,
                        Arrays.asList(plateA, plateB)
                )
        );
    }

    @Test
    public void releaseBarrierAppliesToDeferredAndLostReasons() {
        PlateObservation historical = observation(4L, 404L, 40L);
        assertReleaseReasonBlocksHistoricalPlate("session_deferred", historical);
        assertReleaseReasonBlocksHistoricalPlate("target_lost", historical);
    }

    @Test
    public void releaseFiltersHistoricalPlateButKeepsVehicleOverlay() {
        OverlayItem vehicle = overlay(OverlayItem.Kind.VEHICLE, 7L);
        OverlayItem historicalPlate = overlay(OverlayItem.Kind.PLATE, 701L);

        assertEquals(
                Collections.singletonList(vehicle),
                MainActivity.filterScanOverlayItems(
                        Arrays.asList(vehicle, historicalPlate),
                        Collections.emptySet()
                )
        );
    }

    @Test
    public void releaseKeepsOnlyFreshPlateFromSameTerminalPipelineResult() {
        ContinuityStamp currentStamp = new ContinuityStamp(
                1L, 8L, 0L, 359L, 123_000L,
                SourceTimestampDomain.CAMERAX_SENSOR
        );
        PlateObservation current = observation(
                4L, 404L, 40L, currentStamp
        );
        PipelineResult result = new PipelineResult(
                "recognized",
                "",
                Collections.emptyList(),
                Arrays.asList(
                        overlay(OverlayItem.Kind.VEHICLE, 4L),
                        overlay(OverlayItem.Kind.PLATE, 404L)
                ),
                480,
                640,
                Collections.singletonList(current),
                false,
                currentStamp
        );

        assertEquals(
                Collections.singleton(404L),
                MainActivity.terminalFreshReleasePlateTrackIds(
                        snapshot(41L, 0L),
                        result
                )
        );
    }

    @Test
    public void releaseStillRejectsHistoricalPlateFromPreviousSourceFrame() {
        ContinuityStamp resultStamp = new ContinuityStamp(
                1L, 8L, 0L, 359L, 123_000L,
                SourceTimestampDomain.CAMERAX_SENSOR
        );
        ContinuityStamp historicalStamp = new ContinuityStamp(
                1L, 8L, 0L, 358L, 122_000L,
                SourceTimestampDomain.CAMERAX_SENSOR
        );
        PipelineResult result = new PipelineResult(
                "recognized",
                "",
                Collections.emptyList(),
                Collections.singletonList(
                        overlay(OverlayItem.Kind.PLATE, 404L)
                ),
                480,
                640,
                Collections.singletonList(
                        observation(4L, 404L, 40L, historicalStamp)
                ),
                false,
                resultStamp
        );

        assertEquals(
                Collections.emptySet(),
                MainActivity.terminalFreshReleasePlateTrackIds(
                        snapshot(41L, 0L),
                        result
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

    @Test
    public void intermediateMtStageRequiresCurrentExactScanEntity() {
        assertEquals(true, MainActivity.shouldPresentScanMtStage(
                snapshot(50L, 7L)
        ));
        assertEquals(false, MainActivity.shouldPresentScanMtStage(
                snapshot(51L, 0L)
        ));
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

    private static void assertReleaseReasonBlocksHistoricalPlate(
            String reason,
            PlateObservation observation
    ) {
        ScanAcquisitionSnapshot base = snapshot(41L, 0L);
        ScanAcquisitionSnapshot release = new ScanAcquisitionSnapshot(
                base.scanRunId,
                base.runState,
                base.runWallDurationNanos,
                base.runActiveDurationNanos,
                base.queue,
                0L,
                0L,
                null,
                base.mtAttempts,
                base.freshMzAttempts,
                base.activeSessionDurationNanos,
                base.noProgressDurationNanos,
                new AcquisitionDirective(
                        41L,
                        AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET,
                        base.scanRunId,
                        0L,
                        0L,
                        reason
                ),
                false,
                null,
                base.stats
        );
        assertEquals(
                0L,
                MainActivity.scanPresentationEntityId(
                        release,
                        Collections.singletonList(observation)
                )
        );
    }

    private static PlateObservation observation(
            long entityId,
            long plateTrackId,
            long directiveRevision
    ) {
        return observation(
                entityId,
                plateTrackId,
                directiveRevision,
                ContinuityStamp.initial(1L)
        );
    }

    private static PlateObservation observation(
            long entityId,
            long plateTrackId,
            long directiveRevision,
            ContinuityStamp continuityStamp
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
                continuityStamp,
                directiveRevision
        );
    }

    private static OverlayItem overlay(OverlayItem.Kind kind, long trackId) {
        return new OverlayItem(
                kind,
                new RectF(0.1f, 0.1f, 0.4f, 0.4f),
                Collections.emptyList(),
                "test",
                trackId,
                false
        );
    }
}
