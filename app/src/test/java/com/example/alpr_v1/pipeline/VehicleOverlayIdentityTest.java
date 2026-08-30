package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.vision.Detection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class VehicleOverlayIdentityTest {
    @Test
    public void diagnosticVehicleUsesMatchingEntityId() {
        Detection detection = new Detection(
                0, 0.9f, 100f, 200f, 500f, 700f,
                Collections.emptyList()
        );
        VehicleCandidate other = candidate(
                11L, new NormalizedBounds(0.60f, 0.10f, 0.90f, 0.40f)
        );
        VehicleCandidate matching = candidate(
                22L, new NormalizedBounds(0.10f, 0.20f, 0.50f, 0.70f)
        );

        assertEquals(
                22L,
                MobileAlprEngine.stableVehicleOverlayId(
                        detection,
                        Arrays.asList(other, matching),
                        1000,
                        1000
                )
        );
    }

    @Test
    public void unrelatedDetectionDoesNotBorrowEntityId() {
        Detection detection = new Detection(
                0, 0.9f, 50f, 50f, 150f, 150f,
                Collections.emptyList()
        );

        assertEquals(
                0L,
                MobileAlprEngine.stableVehicleOverlayId(
                        detection,
                        Collections.singletonList(candidate(
                                33L,
                                new NormalizedBounds(0.70f, 0.70f, 0.90f, 0.90f)
                        )),
                        1000,
                        1000
                )
        );
    }

    private static VehicleCandidate candidate(
            long entityId,
            NormalizedBounds bounds
    ) {
        return new VehicleCandidate(
                entityId,
                entityId + 100L,
                bounds,
                0.9f,
                0.9f,
                0f,
                false,
                0,
                1L,
                1L
        );
    }
}
