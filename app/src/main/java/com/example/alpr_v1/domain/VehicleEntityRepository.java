package com.example.alpr_v1.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory source of truth for vehicle entities in the current scene. */
public final class VehicleEntityRepository {
    public static final int MAX_ACTIVE_ENTITIES = 64;
    public static final int MAX_COMPLETED_ENTITIES = 256;
    private final Map<Long, VehicleEntity> byEntityId = new LinkedHashMap<>();
    private final Map<Long, Long> entityIdByVehicleTrack = new LinkedHashMap<>();
    private final Map<Long, Long> entityIdByPlateTrack = new LinkedHashMap<>();
    private final Map<Long, VehicleEntitySummary> completedByEntityId =
            new LinkedHashMap<>();
    private long nextEntityId = 1L;

    public synchronized VehicleEntity create(
            long vehicleTrackId,
            NormalizedBounds bounds,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        ensureActiveCapacity();
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
        Long entityId = entityIdByPlateTrack.get(plateTrackId);
        return entityId == null ? null : byEntityId.get(entityId);
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

    public synchronized List<VehicleEntitySummary> completedEntities() {
        return Collections.unmodifiableList(
                new ArrayList<>(completedByEntityId.values())
        );
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

    public synchronized PlateTrackAttachmentStatus attachPlate(
            long entityId,
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        return attachPlate(
                entityId, plateTrackId, quad, appearance, 0L, nowNanos
        );
    }

    public synchronized PlateTrackAttachmentStatus attachPlate(
            long entityId,
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long sourceSequence,
            long sourceTimestampNanos
    ) {
        if (plateTrackId <= 0L) {
            throw new IllegalArgumentException("plateTrackId must be positive");
        }
        VehicleEntity entity = required(entityId);
        Long owner = entityIdByPlateTrack.get(plateTrackId);
        if (owner != null && owner != entityId) {
            return PlateTrackAttachmentStatus.CONFLICT_REJECTED;
        }
        Long previousTrackId = entity.plateTrackId();
        if (previousTrackId != null && previousTrackId != plateTrackId) {
            entityIdByPlateTrack.remove(previousTrackId);
            entity.detachPlateTrack(previousTrackId);
        }
        entity.attachPlate(
                plateTrackId,
                quad,
                appearance,
                sourceSequence,
                sourceTimestampNanos
        );
        entityIdByPlateTrack.put(plateTrackId, entityId);
        return owner == null
                ? PlateTrackAttachmentStatus.ATTACHED
                : PlateTrackAttachmentStatus.REFRESHED;
    }

    public synchronized PlateTrackAttachmentStatus reassignPlateTrack(
            long plateTrackId,
            long fromEntityId,
            long toEntityId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        if (plateTrackId <= 0L) {
            throw new IllegalArgumentException("plateTrackId must be positive");
        }
        Long owner = entityIdByPlateTrack.get(plateTrackId);
        if (owner == null || owner != fromEntityId) {
            return PlateTrackAttachmentStatus.CONFLICT_REJECTED;
        }
        VehicleEntity from = required(fromEntityId);
        VehicleEntity to = required(toEntityId);
        if (fromEntityId == toEntityId) {
            to.attachPlate(plateTrackId, quad, appearance, nowNanos);
            return PlateTrackAttachmentStatus.REFRESHED;
        }
        Long displacedTrackId = to.plateTrackId();
        if (displacedTrackId != null && displacedTrackId != plateTrackId) {
            entityIdByPlateTrack.remove(displacedTrackId);
            to.detachPlateTrack(displacedTrackId);
        }
        from.detachPlateTrack(plateTrackId);
        to.attachPlate(plateTrackId, quad, appearance, nowNanos);
        entityIdByPlateTrack.put(plateTrackId, toEntityId);
        return PlateTrackAttachmentStatus.REASSIGNED;
    }

    public synchronized void recordMtAttempt(long entityId, long nowNanos) {
        required(entityId).recordMtAttempt(nowNanos);
    }

    public synchronized boolean updateRegistration(
            long entityId,
            PlateTextConsensus consensus,
            long nowNanos
    ) {
        return updateRegistration(
                entityId,
                consensus,
                nowNanos,
                true,
                RegistrationConsensusSource.FRESH_MZ
        );
    }

    public synchronized boolean updateRegistration(
            long entityId,
            PlateTextConsensus consensus,
            long nowNanos,
            boolean freshMzAttempted,
            RegistrationConsensusSource source
    ) {
        return required(entityId).updateRegistration(
                consensus, nowNanos, freshMzAttempted, source
        );
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

    public synchronized void deferAcquisition(long entityId) {
        required(entityId).deferAcquisition();
    }

    public synchronized void updateSearchMatch(long entityId, SearchMatchState state) {
        required(entityId).setSearchMatchState(state);
    }

    public synchronized VehicleEntitySummary finalizeAcquisition(
            long entityId,
            long completedAtNanos
    ) {
        VehicleEntity entity = required(entityId);
        entity.markAcquired();
        VehicleEntitySummary summary = new VehicleEntitySummary(
                entity,
                Math.max(entity.lastSeenNanos(), completedAtNanos)
        );
        completedByEntityId.put(entityId, summary);
        trimCompleted();
        return summary;
    }

    /** Compatibility wrapper; Scan code should pass its explicit completion time. */
    public synchronized void markAcquired(long entityId) {
        VehicleEntity entity = required(entityId);
        finalizeAcquisition(entityId, entity.lastSeenNanos());
    }

    public synchronized int expireOldEntities(long nowNanos, long ttlNanos) {
        int expired = 0;
        long safeTtl = Math.max(0L, ttlNanos);
        java.util.Iterator<Map.Entry<Long, VehicleEntity>> iterator =
                byEntityId.entrySet().iterator();
        while (iterator.hasNext()) {
            VehicleEntity entity = iterator.next().getValue();
            if (entity.activeTarget()) continue;
            if (nowNanos - entity.lastSeenNanos() > safeTtl
                    && entity.acquisitionState() != EntityAcquisitionState.EXPIRED) {
                entity.expire();
                entityIdByVehicleTrack.remove(entity.vehicleTrackId());
                removePlateOwnership(entity);
                iterator.remove();
                expired++;
            }
        }
        return expired;
    }

    public synchronized int purgeExpired(long nowNanos) {
        int purged = 0;
        java.util.Iterator<Map.Entry<Long, VehicleEntity>> iterator =
                byEntityId.entrySet().iterator();
        while (iterator.hasNext()) {
            VehicleEntity entity = iterator.next().getValue();
            if (entity.acquisitionState() != EntityAcquisitionState.EXPIRED) continue;
            entityIdByVehicleTrack.remove(entity.vehicleTrackId());
            removePlateOwnership(entity);
            iterator.remove();
            purged++;
        }
        return purged;
    }

    public synchronized void resetScene() {
        byEntityId.clear();
        entityIdByVehicleTrack.clear();
        entityIdByPlateTrack.clear();
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

    public synchronized void applyCameraMotion(
            long vehicleTrackId,
            NormalizedBounds bounds,
            MotionState motion
    ) {
        VehicleEntity entity = findByVehicleTrackId(vehicleTrackId);
        if (entity != null) entity.applyCameraMotion(bounds, motion);
    }

    private void ensureActiveCapacity() {
        while (byEntityId.size() >= MAX_ACTIVE_ENTITIES) {
            Long removableId = null;
            for (Map.Entry<Long, VehicleEntity> entry : byEntityId.entrySet()) {
                VehicleEntity entity = entry.getValue();
                if (!entity.activeTarget() && !entity.acquisitionCompleted()) {
                    removableId = entry.getKey();
                    break;
                }
            }
            if (removableId == null) {
                for (Map.Entry<Long, VehicleEntity> entry : byEntityId.entrySet()) {
                    if (!entry.getValue().activeTarget()) {
                        removableId = entry.getKey();
                        break;
                    }
                }
            }
            if (removableId == null) {
                throw new IllegalStateException("Active vehicle entity capacity exhausted");
            }
            VehicleEntity removed = byEntityId.remove(removableId);
            if (removed != null) {
                removed.expire();
                entityIdByVehicleTrack.remove(removed.vehicleTrackId());
                removePlateOwnership(removed);
            }
        }
    }

    private void removePlateOwnership(VehicleEntity entity) {
        if (entity == null) return;
        Long plateTrackId = entity.plateTrackId();
        if (plateTrackId != null) entityIdByPlateTrack.remove(plateTrackId);
    }

    private void trimCompleted() {
        while (completedByEntityId.size() > MAX_COMPLETED_ENTITIES) {
            Long oldest = completedByEntityId.keySet().iterator().next();
            completedByEntityId.remove(oldest);
        }
    }
}
