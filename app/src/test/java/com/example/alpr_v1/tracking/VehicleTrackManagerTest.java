package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.MotionState;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VehicleTrackManagerTest {
    @Test
    public void keepsTwoEntityIdsWhenVehiclesApproachEachOther() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackManager manager = manager(repository);
        List<VehicleTrackManager.Snapshot> first = manager.update(
                Arrays.asList(
                        observation(0.05f, 0.2f, 0.25f, 0.5f, red(), 0),
                        observation(0.75f, 0.2f, 0.95f, 0.5f, blue(), 1)
                ),
                1_000_000_000L
        );
        long redEntity = bySource(first, 0).entityId;
        long blueEntity = bySource(first, 1).entityId;

        List<VehicleTrackManager.Snapshot> second = manager.update(
                Arrays.asList(
                        observation(0.20f, 0.2f, 0.40f, 0.5f, red(), 0),
                        observation(0.60f, 0.2f, 0.80f, 0.5f, blue(), 1)
                ),
                1_300_000_000L
        );
        List<VehicleTrackManager.Snapshot> third = manager.update(
                Arrays.asList(
                        observation(0.38f, 0.2f, 0.58f, 0.5f, red(), 0),
                        observation(0.42f, 0.2f, 0.62f, 0.5f, blue(), 1)
                ),
                1_600_000_000L
        );

        assertEquals(redEntity, bySource(second, 0).entityId);
        assertEquals(blueEntity, bySource(second, 1).entityId);
        assertEquals(redEntity, bySource(third, 0).entityId);
        assertEquals(blueEntity, bySource(third, 1).entityId);
        assertNotEquals(redEntity, blueEntity);
        assertEquals(2, repository.size());
    }

    @Test
    public void predictsMotionDuringShortMpGapAndDropsTechnicalTrackAfterTtl() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackManager manager = manager(repository);
        manager.update(
                Collections.singletonList(
                        observation(0.10f, 0.2f, 0.30f, 0.5f, red(), 0)
                ),
                1_000_000_000L
        );
        manager.update(
                Collections.singletonList(
                        observation(0.20f, 0.2f, 0.40f, 0.5f, red(), 0)
                ),
                1_300_000_000L
        );

        VehicleTrackManager.Snapshot predicted = manager.predict(1_600_000_000L).get(0);

        assertTrue(predicted.predicted);
        assertTrue(predicted.bounds.centerX() > 0.30f);
        assertTrue(predicted.motion.velocityX > 0f);
        assertTrue(manager.predict(2_400_000_001L).isEmpty());
        assertEquals(0, manager.trackedCount());
    }

    @Test
    public void reassociatesNewTechnicalTrackWithDormantEntity() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackManager manager = manager(repository);
        VehicleTrackManager.Snapshot initial = manager.update(
                Collections.singletonList(
                        observation(0.20f, 0.2f, 0.50f, 0.6f, red(), 0)
                ),
                1_000_000_000L
        ).get(0);
        VehicleEntity entity = repository.get(initial.entityId);

        assertTrue(manager.predict(2_100_000_001L).isEmpty());
        VehicleTrackManager.Snapshot returned = manager.update(
                Collections.singletonList(
                        observation(0.22f, 0.2f, 0.52f, 0.6f, red(), 0)
                ),
                2_200_000_000L
        ).get(0);

        assertEquals(initial.entityId, returned.entityId);
        assertNotEquals(initial.vehicleTrackId, returned.vehicleTrackId);
        assertSame(entity, repository.get(returned.entityId));
        assertEquals(1, repository.size());
    }

    @Test
    public void exitUrgencyIncreasesForVehicleMovingTowardNearbyEdge() {
        NormalizedBounds nearRightEdge = new NormalizedBounds(0.75f, 0.2f, 0.95f, 0.6f);

        float outward = VehicleTrackManager.exitUrgency(
                nearRightEdge, new MotionState(0.25f, 0f, 1f)
        );
        float inward = VehicleTrackManager.exitUrgency(
                nearRightEdge, new MotionState(-0.25f, 0f, 1f)
        );
        float stationary = VehicleTrackManager.exitUrgency(
                nearRightEdge, MotionState.STATIONARY
        );

        assertTrue(outward > 0.85f);
        assertTrue(outward > inward);
        assertEquals(0f, stationary, 0.0001f);
    }

    @Test
    public void enforcesConfiguredTrackLimit() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        VehicleTrackManager manager = new VehicleTrackManager(
                repository, 3, 1_000_000_000L, 4_000_000_000L
        );
        List<VehicleTrackManager.Observation> observations = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            float left = 0.02f + index * 0.19f;
            observations.add(observation(
                    left, 0.2f, left + 0.15f, 0.5f, descriptor(index + 1f, 1f), index
            ));
        }

        assertEquals(3, manager.update(observations, 1_000_000_000L).size());
        assertEquals(3, repository.size());
    }

    private static VehicleTrackManager manager(VehicleEntityRepository repository) {
        return new VehicleTrackManager(
                repository,
                16,
                1_000_000_000L,
                5_000_000_000L
        );
    }

    private static VehicleTrackManager.Observation observation(
            float left,
            float top,
            float right,
            float bottom,
            AppearanceDescriptor appearance,
            int sourceIndex
    ) {
        return new VehicleTrackManager.Observation(
                new NormalizedBounds(left, top, right, bottom),
                0.9f,
                appearance,
                sourceIndex
        );
    }

    private static VehicleTrackManager.Snapshot bySource(
            List<VehicleTrackManager.Snapshot> snapshots,
            int sourceIndex
    ) {
        for (VehicleTrackManager.Snapshot snapshot : snapshots) {
            if (snapshot.sourceIndex == sourceIndex) return snapshot;
        }
        throw new AssertionError("Missing sourceIndex=" + sourceIndex);
    }

    private static AppearanceDescriptor red() { return descriptor(1f, 0f); }
    private static AppearanceDescriptor blue() { return descriptor(0f, 1f); }

    private static AppearanceDescriptor descriptor(float first, float second) {
        return new AppearanceDescriptor(new float[]{first, second});
    }
}
