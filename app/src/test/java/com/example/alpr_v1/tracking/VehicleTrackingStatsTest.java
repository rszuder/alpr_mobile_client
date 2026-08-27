package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VehicleTrackingStatsTest {
    @Test
    public void cumulativeSnapshotsProducePerTraceDeltas() {
        VehicleTrackingStats previous = VehicleTrackingStats.zero();
        VehicleTrackingStats frame1 = statsWithEntitiesCreated(2L);
        VehicleTrackingStats frame2 = statsWithEntitiesCreated(2L);
        VehicleTrackingStats frame3 = statsWithEntitiesCreated(3L);

        VehicleTrackingStats delta1 = frame1.deltaSince(previous);
        VehicleTrackingStats delta2 = frame2.deltaSince(frame1);
        VehicleTrackingStats delta3 = frame3.deltaSince(frame2);

        assertEquals(2L, delta1.entitiesCreated);
        assertEquals(0L, delta2.entitiesCreated);
        assertEquals(1L, delta3.entitiesCreated);
        assertEquals(3L, delta1.entitiesCreated
                + delta2.entitiesCreated + delta3.entitiesCreated);
    }

    private static VehicleTrackingStats statsWithEntitiesCreated(long value) {
        return new VehicleTrackingStats(
                value, 0L, value, 0L, 0L, 0L, 0L, 0L, value * 10L, 10L
        );
    }
}
