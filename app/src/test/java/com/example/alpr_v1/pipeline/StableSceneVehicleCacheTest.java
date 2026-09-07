package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.acquisition.AcquisitionQueue;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class StableSceneVehicleCacheTest {
    private static final long SECOND = 1_000_000_000L;
    private static final ContinuityStamp STAMP = new ContinuityStamp(1L, 2L, 3L, SECOND);

    @Test public void stablePoolKeepsRemainingVehiclesSelectableAfterFirstOcrFinishes() {
        StableSceneVehicleCache cache = new StableSceneVehicleCache();
        cache.observe(STAMP, true, SECOND);
        VehicleTrackingFrame frame = frame(vehicle(1L), vehicle(2L));
        cache.record(frame, cache.revision(), SECOND);
        AcquisitionQueue queue = new AcquisitionQueue();
        queue.update(cache.current(STAMP, SECOND), 0L, SECOND);
        long first = queue.selectNext(SECOND).candidate.entityId;
        queue.complete(first);
        for (int second = 2; second <= 7; second++) cache.observe(STAMP, true, second * SECOND);
        VehicleTrackingFrame retained = cache.current(STAMP, 7L * SECOND);
        assertSame(frame, retained);
        assertEquals(SECOND, retained.sourceTimestampNanos);
        queue.update(retained, 0L, 7L * SECOND);
        assertNotEquals(first, queue.selectNext(7L * SECOND).candidate.entityId);
    }

    @Test public void fullScanBecomesDueAfterTenSecondsEvenWithContinuousStableEvidence() {
        StableSceneVehicleCache cache = populated();
        for (int second = 2; second <= 11; second++) cache.observe(STAMP, true, second * SECOND);
        assertNull(cache.current(STAMP, 11L * SECOND));
    }

    @Test public void emptySceneIsAlsoCachedUntilControlScan() {
        StableSceneVehicleCache cache = new StableSceneVehicleCache();
        cache.observe(STAMP, true, SECOND);
        cache.record(frame(), cache.revision(), SECOND);
        assertTrue(cache.current(STAMP, SECOND).candidates.isEmpty());
    }

    @Test public void motionInvalidatesPoolAndOldInFlightMeasurement() {
        StableSceneVehicleCache cache = populated();
        long beforeMotion = cache.revision();
        cache.observe(STAMP, false, 2L * SECOND);
        cache.observe(STAMP, true, 3L * SECOND);
        cache.record(frame(vehicle(2L)), beforeMotion, 3L * SECOND);
        assertNull(cache.current(STAMP, 3L * SECOND));
        cache.record(frame(vehicle(2L)), cache.revision(), 3L * SECOND);
        assertEquals(2L, cache.current(STAMP, 3L * SECOND).candidates.get(0).entityId);
    }

    @Test public void expiredEvidenceAndClockRollbackCannotHoldOldGeometry() {
        StableSceneVehicleCache cache = populated();
        assertNull(cache.current(STAMP, 3L * SECOND));
        assertNull(cache.current(STAMP, SECOND - 1L));
        cache.observe(STAMP, true, 4L * SECOND);
        assertNull(cache.current(STAMP, 4L * SECOND));
    }

    @Test public void sceneEpochAndTransformChangesRejectOldVehicles() {
        for (ContinuityStamp changed : Arrays.asList(
                new ContinuityStamp(2L, 2L, 3L, SECOND),
                new ContinuityStamp(1L, 3L, 3L, SECOND),
                new ContinuityStamp(1L, 2L, 4L, SECOND))) {
            StableSceneVehicleCache cache = populated();
            assertNull(cache.current(changed, SECOND));
            cache.observe(changed, true, SECOND);
            assertNull(cache.current(changed, SECOND));
        }
    }

    @Test public void explicitTrackingLossRequiresAnotherFullMeasurement() {
        StableSceneVehicleCache cache = populated();
        cache.invalidate();
        assertNull(cache.current(STAMP, SECOND));
    }

    private static StableSceneVehicleCache populated() {
        StableSceneVehicleCache cache = new StableSceneVehicleCache();
        cache.observe(STAMP, true, SECOND);
        cache.record(frame(vehicle(1L)), cache.revision(), SECOND);
        return cache;
    }

    private static VehicleCandidate vehicle(long id) {
        return new VehicleCandidate(id, id, new NormalizedBounds(0.1f, 0.2f, 0.4f, 0.7f),
                0.8f, 0.08f, 0f, false, 0, SECOND, SECOND);
    }

    private static VehicleTrackingFrame frame(VehicleCandidate... vehicles) {
        return new VehicleTrackingFrame(1L, SECOND, SECOND, 1L, Arrays.asList(vehicles))
                .withContinuityStamp(STAMP);
    }
}
