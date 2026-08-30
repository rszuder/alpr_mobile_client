package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
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
                before, 15L, fresh, 900L
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
                before, 15L, fresh, 900L
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
                before, 0L, fresh, 900L
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
                before, 0L, fresh, 900L
        );

        assertTrue(report.attempted);
        assertEquals(SoftReacquireResult.FAILED, report.result);
    }

    @Test
    public void predictedEntityIsNotFreshReassociation() {
        Set<Long> before = new HashSet<>(java.util.Collections.singletonList(15L));
        VehicleTrackingFrame predicted = new VehicleTrackingFrame(
                11L, 1_100L, 1_200L, 0L,
                java.util.Collections.singletonList(
                        candidate(15L, 701L, true, 1_100L)
                )
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 0L, predicted, 1_000L
        );

        assertEquals(SoftReacquireResult.FAILED, report.result);
        assertEquals(0, report.vehicles.entitiesReassociated);
        assertEquals(1, report.vehicles.entitiesStillPredicted);
        assertEquals(0f, report.vehicles.reassociationRatio, 0.0001f);
    }

    @Test
    public void measuredEntityAfterRecoveryStartIsReassociated() {
        Set<Long> before = new HashSet<>(java.util.Collections.singletonList(15L));
        VehicleTrackingFrame measured = new VehicleTrackingFrame(
                12L, 1_100L, 1_200L, 0L,
                java.util.Collections.singletonList(
                        candidate(15L, 702L, false, 1_100L)
                )
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 0L, measured, 1_000L
        );

        assertEquals(SoftReacquireResult.VEHICLE_POOL_RECOVERED, report.result);
        assertEquals(1, report.vehicles.entitiesReassociated);
        assertEquals(100L, report.vehicles.newestMeasurementAgeNanos);
        assertEquals(1, report.vehicles.freshMeasuredEntities);
        assertFalse(report.vehicles.appearanceAgreementAvailable);
        assertFalse(report.vehicles.trajectoryAgreementAvailable);
    }

    @Test
    public void measurementBeforeRecoveryStartIsNotFresh() {
        Set<Long> before = new HashSet<>(java.util.Collections.singletonList(15L));
        VehicleTrackingFrame stale = new VehicleTrackingFrame(
                13L, 900L, 1_200L, 0L,
                java.util.Collections.singletonList(
                        candidate(15L, 703L, false, 900L)
                )
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                before, 0L, stale, 1_000L
        );

        assertFalse(report.attempted);
        assertEquals(null, report.result);
        assertEquals(0, report.vehicles.entitiesReassociated);
        assertEquals("mp_source_frame_predates_recovery", report.reason);
    }

    @Test
    public void freshMpUsesSourceClockWhenRuntimeClockHasDifferentOffset() {
        long triggerSourceTimestampNanos = 8_000_000_000L;
        VehicleTrackingFrame fresh = new VehicleTrackingFrame(
                14L,
                8_001_000_000L,
                8_002_000_000L,
                0L,
                java.util.Collections.singletonList(
                        candidate(15L, 704L, false, 8_001_000_000L)
                )
        );

        SoftReacquireReport report = SoftReacquireReport.fromFreshMp(
                new HashSet<>(java.util.Collections.singletonList(15L)),
                0L,
                fresh,
                triggerSourceTimestampNanos
        );

        assertEquals(SoftReacquireResult.VEHICLE_POOL_RECOVERED, report.result);
        assertEquals(1, report.vehicles.freshMeasuredEntities);
        assertEquals(1_000_000L, report.vehicles.newestMeasurementAgeNanos);
    }

    @Test
    public void freshMpRequiresSequenceAfterRecoveryTrigger() {
        Set<Long> before = new HashSet<>(
                java.util.Collections.singletonList(15L)
        );
        VehicleTrackingFrame sameSourceFrame = new VehicleTrackingFrame(
                15L,
                50L,
                99_000_000_000L,
                SourceTimestampDomain.CAMERAX_SENSOR,
                99_000_000_100L,
                0L, 0L, 0L,
                java.util.Collections.singletonList(
                        candidate(15L, 705L, false, 99_000_000_000L)
                )
        );
        VehicleTrackingFrame nextSourceFrame = new VehicleTrackingFrame(
                16L,
                51L,
                1L,
                SourceTimestampDomain.CAMERAX_SENSOR,
                2L,
                0L, 0L, 0L,
                java.util.Collections.singletonList(
                        candidate(15L, 706L, false, 1L)
                )
        );

        SoftReacquireReport stale = SoftReacquireReport.fromFreshMp(
                before, 0L, sameSourceFrame, 50L, 8_000_000_000L
        );
        SoftReacquireReport fresh = SoftReacquireReport.fromFreshMp(
                before, 0L, nextSourceFrame, 50L, 8_000_000_000L
        );

        assertFalse(stale.attempted);
        assertEquals("mp_source_sequence_not_after_recovery", stale.reason);
        assertEquals(SoftReacquireResult.VEHICLE_POOL_RECOVERED, fresh.result);
    }

    private static VehicleCandidate candidate(long entityId, long vehicleTrackId) {
        return candidate(entityId, vehicleTrackId, false, 1_000L);
    }

    private static VehicleCandidate candidate(
            long entityId,
            long vehicleTrackId,
            boolean predicted,
            long measurementNanos
    ) {
        return new VehicleCandidate(
                entityId,
                vehicleTrackId,
                new NormalizedBounds(0.1f, 0.2f, 0.7f, 0.9f),
                0.90f,
                0.90f,
                0.10f,
                predicted,
                0,
                measurementNanos,
                Math.max(measurementNanos, 1_200L)
        );
    }
}
