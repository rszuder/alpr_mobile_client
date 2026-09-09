package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.PlateQualityScorer;
import com.example.alpr_v1.vision.Point2;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-space MP/MT boxes from phone log, 2026-09-09 frame 174 (960x1280). */
public class DynamicPlateOwnershipRegressionTest {
    private final PlateVehicleAssociator dynamic = new PlateVehicleAssociator(true);
    private final VehicleCandidate front = vehicle(4, 501.50885f, 382.72345f, 959.959f, 722.8128f);
    private final VehicleCandidate behind = vehicle(5, 342.73138f, 401.7582f, 787.2702f, 618.4586f);
    private final VehicleCandidate back = vehicle(6, 190.88702f, 411.54236f, 575.53265f, 590.46204f);
    private final List<VehicleCandidate> vehicles = Arrays.asList(front, behind, back);
    private final Detection plate = plate(534.6f, 593.7f, 604.9f, 633.8f);

    @Test public void visibleFrontPlateSurvivesAssociationAndTop1DespiteOverlappingCars() {
        VehicleRoi roi = new VehicleRoi(front, 400, 300, 960, 800);
        assertEquals(VehicleAssociationStatus.AMBIGUOUS,
                new PlateVehicleAssociator().associateVehicleRoi(plate, roi, 960, 1280, vehicles).status);
        PlateVehicleAssociation association = dynamic.associateVehicleRoi(plate, roi, 960, 1280, vehicles);
        assertEquals(4, association.entityId);
        PlateQualityScorer.Score quality = PlateQualityScorer.compute(plate, plate.keypoints, 960, 1280);
        assertTrue(quality.validQuad);
        assertEquals(Collections.singletonList(0), EntityPlateTop1.select(Collections.singletonList(
                new EntityPlateTop1.Candidate(0, association.entityId, quality.validQuad,
                        quality.total, plate.confidence, .8f, 0, 1, .9f)), 4));
    }

    @Test public void expandedNeighborCropDoesNotStealFrontPlate() {
        PlateVehicleAssociation association = dynamic.associateVehicleRoi(plate,
                new VehicleRoi(behind, 200, 300, 960, 800), 960, 1280, vehicles);
        assertEquals(4, association.entityId);
        assertTrue(EntityPlateTop1.select(Collections.singletonList(new EntityPlateTop1.Candidate(
                0, association.entityId, true, 1, 1, 1, 0, 1, 1)), 5).isEmpty());
    }

    @Test public void fullFrameUsesSameExtentEvidenceAsRoi() {
        assertEquals(4, dynamic.associate(plate, 960, 1280, vehicles).entityId);
    }

    @Test public void actualModelOutputWithSmallScoreMarginUsesClearExtentEvidence() {
        VehicleCandidate near = vehicle(1, 376.78116f, 215.21141f, 903.991f, 452.13126f);
        VehicleCandidate neighbor = vehicle(2, 245.92984f, 225.83739f, 562.28735f, 379.20557f);
        Detection detected = plate(391.5f, 358.8f, 443.2f, 388.9f);
        assertEquals(1, dynamic.associateVehicleRoi(detected, new VehicleRoi(near, 300, 200, 960, 500),
                960, 1280, Arrays.asList(near, neighbor)).entityId);
    }

    @Test public void smallBoundaryJitterDoesNotResolveRealOverlap() {
        VehicleCandidate neighbor = vehicle(7, 501.50885f, 382.72345f, 959.959f, 632.5f);
        assertEquals(VehicleAssociationStatus.AMBIGUOUS, dynamic.associateVehicleRoi(plate,
                new VehicleRoi(front, 400, 300, 960, 800), 960, 1280, Arrays.asList(front, neighbor)).status);
    }

    @Test public void fullyContainedAmbiguousPlateStillWaitsForOwnership() {
        VehicleCandidate duplicate = vehicle(7, 501.50885f, 382.72345f, 959.959f, 722.8128f);
        assertEquals(VehicleAssociationStatus.AMBIGUOUS, dynamic.associateVehicleRoi(plate,
                new VehicleRoi(front, 400, 300, 960, 800), 960, 1280, Arrays.asList(front, duplicate)).status);
    }

    private static VehicleCandidate vehicle(long id, float left, float top, float right, float bottom) {
        return new VehicleCandidate(id, id, new NormalizedBounds(left / 960, top / 1280,
                right / 960, bottom / 1280), .9f, .9f, 0, false, 0, 100, 100);
    }
    private static Detection plate(float left, float top, float right, float bottom) {
        return new Detection(0, .918f, left, top, right, bottom, Arrays.asList(
                new Point2(left, top), new Point2(right, top), new Point2(right, bottom), new Point2(left, bottom)));
    }
}
