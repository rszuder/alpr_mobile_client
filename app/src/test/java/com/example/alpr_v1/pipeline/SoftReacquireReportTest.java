package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SoftReacquireReportTest {
    @Test
    public void freshMpPreservesEntityIdentityAcrossTechnicalTrackChange() {
        Set<Long> before = new HashSet<>(Arrays.asList(15L, 16L));
        VehicleTrackingFrame fresh = new VehicleTrackingFrame(
                8L, 1_000L, 1_010L, 0L,
                Arrays.asList(candidate(15L, 301L), candidate(16L, 302L))
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 15L, fresh, 900L, 1_010L
        );

        assertFalse(report.attempted);
        assertEquals(null, report.result);
        assertEquals(2, report.vehicles.entitiesReassociated);
        assertEquals(1f, report.vehicles.reassociationRatio, 0.0001f);
    }

    @Test
    public void missingActiveTargetWithPreservedPoolRequestsTargetOnlyRelease() {
        Set<Long> before = new HashSet<>(Arrays.asList(15L, 16L));
        VehicleTrackingFrame fresh = new VehicleTrackingFrame(
                8L, 1_000L, 1_010L, 0L,
                java.util.Collections.singletonList(candidate(16L, 402L))
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 15L, fresh, 900L, 1_010L
        );

        assertEquals(SoftReacquireResult.ACTIVE_TARGET_LOST, report.result);
        assertEquals(1, report.vehicles.entitiesReassociated);
        assertEquals(0.5f, report.vehicles.reassociationRatio, 0.0001f);
    }

    @Test
    public void noActiveTargetAndRecoveredPoolProducesTerminalPoolOutcome() {
        Set<Long> before = new HashSet<>(Arrays.asList(15L, 16L));
        VehicleTrackingFrame fresh = new VehicleTrackingFrame(
                9L, 1_000L, 1_010L, 0L,
                Arrays.asList(candidate(15L, 501L), candidate(16L, 502L))
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 0L, fresh, 900L, 1_010L
        );

        assertTrue(report.attempted);
        assertEquals(SoftReacquireResult.VEHICLE_POOL_RECOVERED, report.result);
        assertEquals(2, report.vehicles.entitiesReassociated);
    }

    @Test
    public void noActiveTargetAndNoRecoveredPoolProducesFailed() {
        Set<Long> before = new HashSet<>(Arrays.asList(15L, 16L));
        VehicleTrackingFrame fresh = new VehicleTrackingFrame(
                10L, 1_000L, 1_010L, 0L,
                java.util.Collections.singletonList(candidate(99L, 601L))
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 0L, fresh, 900L, 1_010L
        );

        assertTrue(report.attempted);
        assertEquals(SoftReacquireResult.FAILED, report.result);
    }

    private static VehicleCandidate candidate(long entityId, long vehicleTrackId) {
        return new VehicleCandidate(
                entityId,
                vehicleTrackId,
                new NormalizedBounds(0.1f, 0.2f, 0.7f, 0.9f),
                0.90f,
                0.90f,
                0.10f,
                false,
                0,
                1_000L,
                1_010L
        );
    }
}
