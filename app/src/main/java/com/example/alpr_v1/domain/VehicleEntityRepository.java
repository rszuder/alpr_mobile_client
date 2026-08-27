package com.example.alpr_v1.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory source of truth for vehicle entities in the current scene. */
public final class VehicleEntityRepository {
    private final Map<Long, VehicleEntity> byEntityId = new LinkedHashMap<>();
    private final Map<Long, Long> entityIdByVehicleTrack = new LinkedHashMap<>();
    private long nextEntityId = 1L;

    public synchronized VehicleEntity create(
            long vehicleTrackId,
            NormalizedBounds bounds,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        if (vehicleTrackId > 0L && entityIdByVehicleTrack.containsKey(vehicleTrackId)) {
            throw new IllegalStateException(
                    "vehicleTrackId=" + vehicleTrackId + " already belongs to an entity"
            );
        }
        VehicleEntity entity = new VehicleEntity(
                nextEntityId++,
                vehicleTrackId,
                bounds,
                appearance,
                nowNanos
        );
        byEntityId.put(entity.entityId(), entity);
        if (vehicleTrackId > 0L) {
            entityIdByVehicleTrack.put(vehicleTrackId, entity.entityId());
        }
        return entity;
    }

    public synchronized VehicleEntity get(long entityId) {
        return byEntityId.get(entityId);
    }

    public synchronized VehicleEntity findByVehicleTrackId(long vehicleTrackId) {
        Long entityId = entityIdByVehicleTrack.get(vehicleTrackId);
        return entityId == null ? null : byEntityId.get(entityId);
    }

    public synchronized VehicleEntity findByPlateTrackId(long plateTrackId) {
        if (plateTrackId <= 0L) return null;
        for (VehicleEntity entity : byEntityId.values()) {
            Long candidate = entity.plateTrackId();
            if (candidate != null && candidate == plateTrackId) return entity;
        }
        return null;
    }

    public synchronized List<VehicleEntity> activeEntities() {
        List<VehicleEntity> active = new ArrayList<>();
        for (VehicleEntity entity : byEntityId.values()) {
            if (entity.acquisitionState() != EntityAcquisitionState.EXPIRED) {
                active.add(entity);
            }
        }
        return Collections.unmodifiableList(active);
    }

    public synchronized VehicleEntity updateFromMp(
            long vehicleTrackId,
            NormalizedBounds bounds,
            MotionState motion,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        VehicleEntity entity = findByVehicleTrackId(vehicleTrackId);
        if (entity == null) {
            entity = create(vehicleTrackId, bounds, appearance, nowNanos);
        }
        long previousTrackId = entity.vehicleTrackId();
        entity.updateFromMp(vehicleTrackId, bounds, motion, appearance, nowNanos);
        if (previousTrackId > 0L && previousTrackId != vehicleTrackId) {
            entityIdByVehicleTrack.remove(previousTrackId);
        }
        if (vehicleTrackId > 0L) {
            entityIdByVehicleTrack.put(vehicleTrackId, entity.entityId());
        }
        return entity;
    }

    /**
     * Rebinds an existing domain entity after the technical MP tracker changes its id.
     * The durable {@code entityId} and all accumulated recognition state are preserved.
     */
    public synchronized VehicleEntity reassignVehicleTrack(
            long entityId,
            long newVehicleTrackId,
            NormalizedBounds bounds,
            MotionState motion,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        if (newVehicleTrackId <= 0L) {
            throw new IllegalArgumentException("newVehicleTrackId must be positive");
        }
        VehicleEntity entity = required(entityId);
        Long owner = entityIdByVehicleTrack.get(newVehicleTrackId);
        if (owner != null && owner != entityId) {
            throw new IllegalStateException(
                    "vehicleTrackId=" + newVehicleTrackId + " belongs to entityId=" + owner
            );
        }
        long previousTrackId = entity.vehicleTrackId();
        if (previousTrackId > 0L) {
            entityIdByVehicleTrack.remove(previousTrackId);
        }
        entity.updateFromMp(
                newVehicleTrackId,
                bounds,
                motion,
                appearance,
                nowNanos
        );
        entityIdByVehicleTrack.put(newVehicleTrackId, entityId);
        return entity;
    }

    public synchronized void attachPlate(
            long entityId,
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        required(entityId).attachPlate(plateTrackId, quad, appearance, nowNanos);
    }

    public synchronized void recordMtAttempt(long entityId, long nowNanos) {
        required(entityId).recordMtAttempt(nowNanos);
    }

    public synchronized void updateRegistration(
            long entityId,
            PlateTextConsensus consensus,
            long nowNanos
    ) {
        required(entityId).updateRegistration(consensus, nowNanos);
    }

    public synchronized void considerCrop(long entityId, CropReference crop) {
        required(entityId).considerCrop(crop);
    }

    public synchronized void markQueued(long entityId) {
        required(entityId).setAcquisitionState(EntityAcquisitionState.QUEUED);
    }

    public synchronized void markActiveTarget(long entityId, boolean active) {
        required(entityId).setActiveTarget(active);
        if (active) required(entityId).setAcquisitionState(EntityAcquisitionState.ACQUIRING);
    }

    public synchronized void updateSearchMatch(long entityId, SearchMatchState state) {
        required(entityId).setSearchMatchState(state);
    }

    public synchronized void markAcquired(long entityId) {
        required(entityId).markAcquired();
    }

    public synchronized int expireOldEntities(long nowNanos, long ttlNanos) {
        int expired = 0;
        long safeTtl = Math.max(0L, ttlNanos);
        for (VehicleEntity entity : byEntityId.values()) {
            if (entity.activeTarget() || entity.acquisitionCompleted()) continue;
            if (nowNanos - entity.lastSeenNanos() > safeTtl
                    && entity.acquisitionState() != EntityAcquisitionState.EXPIRED) {
                entity.expire();
                entityIdByVehicleTrack.remove(entity.vehicleTrackId());
                expired++;
            }
        }
        return expired;
    }

    public synchronized void resetScene() {
        byEntityId.clear();
        entityIdByVehicleTrack.clear();
        nextEntityId = 1L;
    }

    public synchronized int size() {
        return byEntityId.size();
    }

    private VehicleEntity required(long entityId) {
        VehicleEntity entity = byEntityId.get(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("Unknown entityId=" + entityId);
        }
        return entity;
    }
}
