package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class EntityOverlayMotionProjectorInstrumentedTest {
    private static final float EPSILON = 0.0001f;
    private final EntityOverlayMotionProjector projector =
            new EntityOverlayMotionProjector();

    @Test
    public void eachVehicleUsesOnlyItsOwnMotionEvidence() {
        OverlayItem vehicleA = item(OverlayItem.Kind.VEHICLE, 1L, 0.10f, 0.30f);
        OverlayItem vehicleB = item(OverlayItem.Kind.VEHICLE, 2L, 0.50f, 0.70f);
        OverlayItem vehicleC = item(OverlayItem.Kind.VEHICLE, 3L, 0.75f, 0.90f);
        OverlayItem basePlateA = item(OverlayItem.Kind.PLATE, 101L, 0.15f, 0.22f);
        OverlayItem trackedPlateA = item(OverlayItem.Kind.PLATE, 101L, 0.25f, 0.32f);

        List<OverlayItem> result = projector.project(
                Arrays.asList(vehicleA, vehicleB, vehicleC, basePlateA),
                Collections.singletonList(trackedPlateA),
                1L,
                101L,
                frame(
                        candidate(1L, 0.20f, 0.40f, true),
                        candidate(2L, 0.47f, 0.67f, true),
                        candidate(3L, 0.77f, 0.92f, true)
                )
        );

        assertEquals(0.20f, result.get(0).normalizedBounds.left, EPSILON);
        assertEquals(0.47f, result.get(1).normalizedBounds.left, EPSILON);
        assertEquals(0.77f, result.get(2).normalizedBounds.left, EPSILON);
        assertEquals(0.25f, result.get(3).normalizedBounds.left, EPSILON);
    }

    @Test
    public void trackedPlateWithoutFocusedIdentityCannotMoveVehiclesGlobally() {
        OverlayItem vehicleA = item(OverlayItem.Kind.VEHICLE, 1L, 0.10f, 0.30f);
        OverlayItem vehicleB = item(OverlayItem.Kind.VEHICLE, 2L, 0.50f, 0.70f);
        OverlayItem basePlate = item(OverlayItem.Kind.PLATE, 101L, 0.15f, 0.22f);
        OverlayItem trackedPlate = item(OverlayItem.Kind.PLATE, 101L, 0.25f, 0.32f);

        List<OverlayItem> result = projector.project(
                Arrays.asList(vehicleA, vehicleB, basePlate),
                Collections.singletonList(trackedPlate),
                0L,
                101L,
                VehicleTrackingFrame.empty(1L)
        );

        assertSame(vehicleA, result.get(0));
        assertSame(vehicleB, result.get(1));
        assertEquals(0.25f, result.get(2).normalizedBounds.left, EPSILON);
    }

    private static VehicleTrackingFrame frame(VehicleCandidate... candidates) {
        return new VehicleTrackingFrame(
                1L,
                1L,
                1L,
                1L,
                Arrays.asList(candidates)
        );
    }

    private static VehicleCandidate candidate(
            long entityId,
            float left,
            float right,
            boolean predicted
    ) {
        return new VehicleCandidate(
                entityId,
                entityId + 100L,
                new NormalizedBounds(left, 0.10f, right, 0.50f),
                0.9f,
                0.9f,
                0f,
                predicted,
                predicted ? 1 : 0,
                1L,
                predicted ? 2L : 1L
        );
    }

    private static OverlayItem item(
            OverlayItem.Kind kind,
            long trackId,
            float left,
            float right
    ) {
        return new OverlayItem(
                kind,
                new RectF(left, 0.10f, right, 0.50f),
                Collections.emptyList(),
                "",
                trackId,
                false
        );
    }
}
