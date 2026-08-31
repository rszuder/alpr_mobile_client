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
                Collections.singletonList(22L),
                MobileAlprEngine.stableVehicleOverlayIds(
                        Collections.singletonList(detection),
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
                Collections.singletonList(0L),
                MobileAlprEngine.stableVehicleOverlayIds(
                        Collections.singletonList(detection),
                        Collections.singletonList(candidate(
                                33L,
                                new NormalizedBounds(0.70f, 0.70f, 0.90f, 0.90f)
                        )),
                        1000,
                        1000
                )
        );
    }

    @Test
    public void overlappingMeasuredDetectionsKeepDistinctSourceIdentity() {
        Detection rawA = detection(100f, 100f, 650f, 700f);
        Detection rawB = detection(300f, 100f, 850f, 700f);
        VehicleCandidate entityA = candidate(
                71L,
                new NormalizedBounds(0.12f, 0.10f, 0.67f, 0.70f),
                0
        );
        VehicleCandidate entityB = candidate(
                72L,
                new NormalizedBounds(0.28f, 0.10f, 0.83f, 0.70f),
                1
        );

        assertEquals(
                Arrays.asList(71L, 72L),
                MobileAlprEngine.stableVehicleOverlayIds(
                        Arrays.asList(rawA, rawB),
                        Arrays.asList(entityB, entityA),
                        1000,
                        1000
                )
        );
    }

    @Test
    public void ambiguousFallbackDoesNotGuessEntity() {
        Detection raw = detection(200f, 100f, 800f, 700f);
        VehicleCandidate left = candidate(
                81L,
                new NormalizedBounds(0.18f, 0.10f, 0.78f, 0.70f),
                -1
        );
        VehicleCandidate right = candidate(
                82L,
                new NormalizedBounds(0.22f, 0.10f, 0.82f, 0.70f),
                -1
        );

        assertEquals(
                Collections.singletonList(0L),
                MobileAlprEngine.stableVehicleOverlayIds(
                        Collections.singletonList(raw),
                        Arrays.asList(left, right),
                        1000,
                        1000
                )
        );
    }

    private static VehicleCandidate candidate(
            long entityId,
            NormalizedBounds bounds
    ) {
        return candidate(entityId, bounds, -1);
    }

    private static VehicleCandidate candidate(
            long entityId,
            NormalizedBounds bounds,
            int sourceIndex
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
                1L,
                sourceIndex
        );
    }

    private static Detection detection(
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new Detection(
                0,
                0.9f,
                left,
                top,
                right,
                bottom,
                Collections.emptyList()
        );
    }
}
