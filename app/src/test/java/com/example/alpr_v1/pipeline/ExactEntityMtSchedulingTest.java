package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;

import org.junit.Test;

import java.util.Arrays;

public final class ExactEntityMtSchedulingTest {
    @Test
    public void requestedEntitySurvivesRoiIndexReordering() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();
        scheduler.requestVehicleEntity(
                22L,
                MtReason.SCAN_NEXT_CANDIDATE,
                7L
        );

        MtInferenceScheduler.Decision decision = scheduler.plan(searchInput(2));
        VehicleRoi selected = ExactEntityRoiResolver.findByEntityId(
                Arrays.asList(roi(11L, 101L), roi(22L, 202L)),
                decision.vehicleEntityId
        );

        assertEquals(MtInferenceScheduler.Kind.VEHICLE_ROI, decision.kind);
        assertEquals(22L, decision.vehicleEntityId);
        assertEquals(7L, decision.acquisitionDirectiveRevision);
        assertEquals(MtReason.SCAN_NEXT_CANDIDATE, decision.mtReason);
        assertNotNull(selected);
        assertEquals(22L, selected.entityId);
    }

    @Test
    public void expandedRetryKeepsSameEntityAndCarriesMargin() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();
        scheduler.requestVehicleEntity(
                22L,
                MtReason.SCAN_EXPANDED_ENTITY_ROI,
                8L
        );

        MtInferenceScheduler.Decision decision = scheduler.plan(searchInput(3));

        assertEquals(22L, decision.vehicleEntityId);
        assertEquals(MtReason.SCAN_EXPANDED_ENTITY_ROI, decision.mtReason);
        assertEquals(0.18f, decision.targetMargin, 0.0001f);
    }

    @Test
    public void missingExactRoiNeverResolvesNeighbor() {
        VehicleRoi selected = ExactEntityRoiResolver.findByEntityId(
                Arrays.asList(roi(11L, 101L), roi(33L, 303L)),
                22L
        );

        assertNull(selected);
    }

    @Test
    public void exactFailureDoesNotArmLegacyFullFrameFallback() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();
        scheduler.requestVehicleEntity(22L, MtReason.SCAN_RETRY_ENTITY, 9L);
        MtInferenceScheduler.Decision exact = scheduler.plan(searchInput(2));
        scheduler.onMtResult(exact, 1L, false);

        MtInferenceScheduler.Decision next = scheduler.plan(searchInput(2));

        assertEquals(MtInferenceScheduler.Kind.VEHICLE_ROI, next.kind);
        assertEquals(0L, next.vehicleEntityId);
        assertFalse(next.reason.contains("full_frame"));
    }

    @Test
    public void staleDirectiveRevisionCannotRetargetScheduler() {
        MtInferenceScheduler scheduler = new MtInferenceScheduler();
        scheduler.requestVehicleEntity(22L, MtReason.SCAN_NEXT_CANDIDATE, 10L);
        scheduler.requestVehicleEntity(11L, MtReason.SCAN_RETRY_ENTITY, 9L);

        MtInferenceScheduler.Decision decision = scheduler.plan(searchInput(2));

        assertEquals(22L, decision.vehicleEntityId);
        assertEquals(10L, decision.acquisitionDirectiveRevision);
    }

    @Test
    public void entityOutsideLegacyRoiBudgetCanBeBuiltExactlyFromTrackingPool() {
        VehicleCandidate third = candidate(33L, 303L);

        VehicleRoi exact = ExactEntityRoiResolver.buildFromTrackedEntity(
                Arrays.asList(
                        candidate(11L, 101L),
                        candidate(22L, 202L),
                        third
                ),
                1000,
                500,
                33L,
                0.18f
        );

        assertNotNull(exact);
        assertEquals(33L, exact.entityId);
        assertEquals(303L, exact.vehicleTrackId);
    }

    private static MtInferenceScheduler.Input searchInput(int regionCount) {
        return new MtInferenceScheduler.Input(
                1L,
                false,
                TargetSnapshot.State.SEARCHING,
                0f, 0,
                false, false, false,
                regionCount
        );
    }

    private static VehicleRoi roi(long entityId, long vehicleTrackId) {
        return new VehicleRoi(
                candidate(entityId, vehicleTrackId),
                10, 10, 100, 100
        );
    }

    private static VehicleCandidate candidate(long entityId, long vehicleTrackId) {
        return new VehicleCandidate(
                entityId,
                vehicleTrackId,
                new NormalizedBounds(0.1f, 0.1f, 0.5f, 0.7f),
                0.9f, 0.9f, 0f,
                false, 0,
                10L, 10L
        );
    }
}
