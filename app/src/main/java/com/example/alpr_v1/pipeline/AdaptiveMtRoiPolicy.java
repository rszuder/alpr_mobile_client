package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.acquisition.DynamicMtConfig;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.HashMap;
import java.util.Map;

/** Camera-source geometry only; selecting a crop does not assign its detections to its owner. */
final class AdaptiveMtRoiPolicy {
    static final long LOCAL_MAX_AGE_NANOS = DynamicMtConfig.LOCAL_MAX_AGE_NANOS;
    enum Kind { LOCAL_PLATE, PRIMARY_LOWER_VEHICLE, EXPANDED_VEHICLE, FULL_FRAME }
    static final class Plan {
        final Kind kind;
        final VehicleRoiSelector.Region region;
        Plan(Kind kind, VehicleRoiSelector.Region region) { this.kind = kind; this.region = region; }
    }
    private static final class Anchor {
        final ContinuityStamp stamp;
        final NormalizedBounds vehicle, plate;
        Anchor(ContinuityStamp stamp, NormalizedBounds vehicle, NormalizedBounds plate) {
            this.stamp = stamp; this.vehicle = vehicle; this.plate = plate;
        }
    }
    private final Map<Long, Anchor> anchors = new HashMap<>();
    private final Map<Long, Kind> retries = new HashMap<>();
    private ContinuityStamp context;

    private void enter(ContinuityStamp stamp) {
        if (context != null && (context.sceneGeneration != stamp.sceneGeneration
                || context.visualEpoch != stamp.visualEpoch
                || context.cameraTransformGeneration != stamp.cameraTransformGeneration)) reset();
        context = stamp;
    }
    void reset() { anchors.clear(); retries.clear(); context = null; }
    void remember(long entityId, NormalizedBounds vehicle, NormalizedBounds plate, ContinuityStamp stamp) {
        enter(stamp);
        if (entityId > 0 && vehicle != null && vehicle.valid() && plate != null && plate.valid())
            anchors.put(entityId, new Anchor(stamp, vehicle, plate));
    }
    NormalizedBounds predictedPlate(long entityId, NormalizedBounds vehicle, ContinuityStamp stamp) {
        enter(stamp);
        Anchor old = anchors.get(entityId);
        if (old == null || vehicle == null || !vehicle.valid()
                || old.stamp.sourceTimestampDomain != stamp.sourceTimestampDomain
                || stamp.sourceTimestampNanos < old.stamp.sourceTimestampNanos
                || stamp.sourceTimestampNanos - old.stamp.sourceTimestampNanos > LOCAL_MAX_AGE_NANOS) return null;
        float scaleX = vehicle.width() / old.vehicle.width(), scaleY = vehicle.height() / old.vehicle.height();
        if (scaleX < .5f || scaleX > 2f || scaleY < .5f || scaleY > 2f) return null;
        NormalizedBounds mapped = new NormalizedBounds(
                vehicle.left + (old.plate.left - old.vehicle.left) * scaleX,
                vehicle.top + (old.plate.top - old.vehicle.top) * scaleY,
                vehicle.left + (old.plate.right - old.vehicle.left) * scaleX,
                vehicle.top + (old.plate.bottom - old.vehicle.top) * scaleY);
        return mapped.valid() ? mapped : null;
    }
    Plan select(VehicleRoi owner, NormalizedBounds freshVehicle, VehicleRoiSelector.Region expanded,
            ContinuityStamp stamp, int width, int height, boolean forceExpanded, DynamicMtConfig config) {
        enter(stamp);
        if (owner == null || freshVehicle == null || !freshVehicle.valid())
            return new Plan(Kind.FULL_FRAME, expanded);
        Kind retry = retries.get(owner.entityId);
        if (forceExpanded || retry == Kind.EXPANDED_VEHICLE) return new Plan(Kind.EXPANDED_VEHICLE, expanded);
        NormalizedBounds plate = retry == Kind.PRIMARY_LOWER_VEHICLE ? null : predictedPlate(owner.entityId, freshVehicle, stamp);
        if (plate != null) {
            VehicleRoiSelector.Region local = region(plate.left - plate.width() * DynamicMtConfig.LOCAL_MARGIN_X,
                    plate.top - plate.height() * DynamicMtConfig.LOCAL_MARGIN_Y, plate.right + plate.width() * DynamicMtConfig.LOCAL_MARGIN_X,
                    plate.bottom + plate.height() * DynamicMtConfig.LOCAL_MARGIN_Y, width, height);
            if (local.width() >= 8 && local.height() >= 8) return new Plan(Kind.LOCAL_PLATE, local);
        }
        return new Plan(Kind.PRIMARY_LOWER_VEHICLE, region(
                freshVehicle.left - freshVehicle.width() * DynamicMtConfig.PRIMARY_MARGIN_X,
                freshVehicle.top + freshVehicle.height() * config.primaryTopFraction,
                freshVehicle.right + freshVehicle.width() * DynamicMtConfig.PRIMARY_MARGIN_X,
                freshVehicle.bottom + freshVehicle.height() * DynamicMtConfig.PRIMARY_MARGIN_BOTTOM, width, height));
    }
    void result(long entityId, Kind kind, boolean foundOwnedValidPlate) {
        if (foundOwnedValidPlate) retries.remove(entityId);
        else retries.put(entityId, kind == Kind.LOCAL_PLATE ? Kind.PRIMARY_LOWER_VEHICLE : Kind.EXPANDED_VEHICLE);
    }
    private static VehicleRoiSelector.Region region(float left, float top, float right, float bottom, int width, int height) {
        return new VehicleRoiSelector.Region(Math.max(0, (int)Math.floor(left * width)),
                Math.max(0, (int)Math.floor(top * height)), Math.min(width, (int)Math.ceil(right * width)),
                Math.min(height, (int)Math.ceil(bottom * height)), null);
    }
}
