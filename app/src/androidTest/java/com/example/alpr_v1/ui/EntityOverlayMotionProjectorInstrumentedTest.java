package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.tracking.FrameMotionTransform;

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
    public void missingVehicleCandidatesDropDiagnosticsWithoutFocusedProof() {
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

        assertEquals(1, result.size());
        assertEquals(OverlayItem.Kind.PLATE, result.get(0).kind);
        assertEquals(0.25f, result.get(0).normalizedBounds.left, EPSILON);
    }

    @Test
    public void vehiclePredictionDisappearsAfterOverlayDeadline() {
        OverlayItem vehicle = item(OverlayItem.Kind.VEHICLE, 4L, 0.10f, 0.30f);
        VehicleCandidate stale = new VehicleCandidate(
                4L,
                104L,
                new NormalizedBounds(0.12f, 0.10f, 0.32f, 0.50f),
                0.9f,
                0.9f,
                0f,
                true,
                3,
                1L,
                700_000_001L
        );

        List<OverlayItem> result = projector.project(
                Collections.singletonList(vehicle),
                Collections.emptyList(),
                0L,
                0L,
                frame(stale),
                500_000_000L
        );

        assertEquals(0, result.size());
    }

    @Test
    public void globalFrameMotionMovesEveryDiagnosticLayerWithoutPlateAnchor() {
        OverlayItem vehicle = item(OverlayItem.Kind.VEHICLE, 1L, 0.10f, 0.30f);
        OverlayItem roi = item(OverlayItem.Kind.VEHICLE_ROI, 1L, 0.08f, 0.32f);

        List<OverlayItem> result = projector.project(
                Arrays.asList(vehicle, roi),
                Collections.emptyList(),
                1L,
                0L,
                frame(candidate(1L, 0.10f, 0.30f, false)),
                500_000_000L,
                FrameMotionTransform.translation(0.06f, -0.04f)
        );

        assertEquals(0.16f, result.get(0).normalizedBounds.left, EPSILON);
        assertEquals(0.06f, result.get(0).normalizedBounds.top, EPSILON);
        assertEquals(0.14f, result.get(1).normalizedBounds.left, EPSILON);
        assertEquals(0.06f, result.get(1).normalizedBounds.top, EPSILON);
    }

    @Test
    public void cumulativeMotionUsesFreshCandidateAsVehicleAnchor() {
        OverlayItem vehicle = item(OverlayItem.Kind.VEHICLE, 1L, 0.10f, 0.30f);
        OverlayItem roi = item(OverlayItem.Kind.VEHICLE_ROI, 1L, 0.08f, 0.32f);

        List<OverlayItem> result = projector.project(
                Arrays.asList(vehicle, roi),
                Collections.emptyList(),
                1L,
                0L,
                frame(candidate(1L, 0.20f, 0.40f, false)),
                500_000_000L,
                FrameMotionTransform.translation(0.10f, 0f),
                FrameMotionTransform.translation(0.05f, 0f)
        );

        assertEquals(0.25f, result.get(0).normalizedBounds.left, EPSILON);
        assertEquals(0.18f, result.get(1).normalizedBounds.left, EPSILON);
    }

    @Test
    public void accumulatedMotionCompensatesDelayedInferenceIncludingNewPlate() {
        OverlayItem vehicle = item(OverlayItem.Kind.VEHICLE, 1L, 0.40f, 0.70f);
        OverlayItem plate = item(OverlayItem.Kind.PLATE, 101L, 0.50f, 0.58f);

        List<OverlayItem> result = projector.compensateInferenceLatency(
                Arrays.asList(vehicle, plate),
                FrameMotionTransform.translation(-0.18f, 0.03f)
        );

        assertEquals(0.22f, result.get(0).normalizedBounds.left, EPSILON);
        assertEquals(0.32f, result.get(1).normalizedBounds.left, EPSILON);
        assertEquals(0.13f, result.get(1).normalizedBounds.top, EPSILON);
        assertEquals(false, result.get(0).carriedPrediction);
        assertEquals(false, result.get(1).carriedPrediction);
    }

    @Test
    public void focusedVehicleWithoutCandidateCannotSurviveOnGlobalMotionAlone() {
        OverlayItem focusedVehicle = item(
                OverlayItem.Kind.VEHICLE, 7L, 0.30f, 0.60f
        );

        List<OverlayItem> result = projector.project(
                Collections.singletonList(focusedVehicle),
                Collections.emptyList(),
                7L,
                0L,
                frame(candidate(8L, 0.10f, 0.25f, false)),
                500_000_000L,
                FrameMotionTransform.translation(0.12f, 0f)
        );

        assertEquals(0, result.size());
    }

    @Test
    public void globalMotionWinsOverConflictingFocusedPlateDelta() {
        OverlayItem focusedVehicle = item(
                OverlayItem.Kind.VEHICLE, 7L, 0.30f, 0.60f
        );
        OverlayItem basePlate = item(
                OverlayItem.Kind.PLATE, 70L, 0.40f, 0.48f
        );
        OverlayItem locallyTrackedPlate = item(
                OverlayItem.Kind.PLATE, 70L, 0.40f, 0.48f
        );

        List<OverlayItem> result = projector.project(
                Arrays.asList(focusedVehicle, basePlate),
                Collections.singletonList(locallyTrackedPlate),
                7L,
                70L,
                frame(candidate(7L, 0.30f, 0.60f, false)),
                500_000_000L,
                FrameMotionTransform.translation(0.14f, 0f)
        );

        assertEquals(0.44f, result.get(0).normalizedBounds.left, EPSILON);
    }

    @Test
    public void staleEntityIsDroppedEvenWithValidGlobalMotion() {
        OverlayItem vehicle = item(OverlayItem.Kind.VEHICLE, 4L, 0.10f, 0.30f);
        VehicleCandidate stale = new VehicleCandidate(
                4L,
                104L,
                new NormalizedBounds(0.12f, 0.10f, 0.32f, 0.50f),
                0.9f,
                0.9f,
                0f,
                true,
                3,
                1L,
                700_000_001L
        );

        List<OverlayItem> result = projector.project(
                Collections.singletonList(vehicle),
                Collections.emptyList(),
                4L,
                0L,
                frame(stale),
                500_000_000L,
                FrameMotionTransform.translation(0.12f, 0f)
        );

        assertEquals(0, result.size());
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
