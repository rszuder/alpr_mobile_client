package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.ui.EntityAwareOverlayAlignment;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class EntityAwareOverlayAlignmentTest {
    @Test
    public void sourceEntityComesFromMatchingPlateTrack() {
        assertEquals(
                7L,
                EntityAwareOverlayAlignment.resolveSourceEntityId(
                        Arrays.asList(
                                observation(8L, 808L, 12L),
                                observation(7L, 707L, 10L)
                        ),
                        707L
                )
        );
    }

    private static PlateObservation observation(
            long entityId,
            long plateTrackId,
            long directiveRevision
    ) {
        return new PlateObservation(
                plateTrackId,
                PlateVehicleAssociation.direct(entityId, entityId + 100L, "test"),
                MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,
                directiveRevision,
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
