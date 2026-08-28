package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.vision.Detection;

import java.util.List;

/** Conservative geometry associator for MT detections produced by full-frame work. */
public final class PlateVehicleAssociator {
    public static final float MIN_ASSOCIATION_SCORE = 0.48f;
    public static final float MIN_ASSOCIATION_MARGIN = 0.12f;

    public PlateVehicleAssociation associate(
            Detection plate,
            int sourceWidth,
            int sourceHeight,
            List<VehicleCandidate> vehicles
    ) {
        if (plate == null || sourceWidth <= 0 || sourceHeight <= 0) {
            return PlateVehicleAssociation.unassigned("invalid_plate_geometry");
        }
        if (vehicles == null || vehicles.isEmpty()) {
            return PlateVehicleAssociation.unassigned("no_vehicle_candidates");
        }
        float plateX = plate.centerX() / sourceWidth;
        float plateY = plate.centerY() / sourceHeight;
        VehicleCandidate best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        float secondScore = Float.NEGATIVE_INFINITY;
        for (VehicleCandidate vehicle : vehicles) {
            float score = score(plateX, plateY, vehicle);
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = vehicle;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (best == null || bestScore < MIN_ASSOCIATION_SCORE) {
            return PlateVehicleAssociation.unassigned("association_score_below_threshold");
        }
        float margin = secondScore == Float.NEGATIVE_INFINITY
                ? 1f : bestScore - secondScore;
        if (margin < MIN_ASSOCIATION_MARGIN) {
            return PlateVehicleAssociation.ambiguous(
                    bestScore,
                    "association_margin=" + margin
            );
        }
        return PlateVehicleAssociation.associated(
                best.entityId,
                best.vehicleTrackId,
                bestScore,
                "full_frame_geometry_unique"
        );
    }

    /**
     * Validates a plate found in an expanded vehicle crop against the owner's
     * original (unexpanded) bounds and every other active entity.
     */
    public PlateVehicleAssociation validateDirectRoi(
            Detection plate,
            VehicleRoi directRoi,
            int sourceWidth,
            int sourceHeight,
            List<VehicleCandidate> vehicles
    ) {
        if (plate == null || directRoi == null
                || sourceWidth <= 0 || sourceHeight <= 0) {
            return PlateVehicleAssociation.unassigned("invalid_direct_roi_geometry");
        }
        VehicleCandidate owner = directRoi.candidate;
        float plateX = plate.centerX() / sourceWidth;
        float plateY = plate.centerY() / sourceHeight;
        float left = owner.bounds.left;
        float top = owner.bounds.top;
        float right = owner.bounds.right;
        float bottom = owner.bounds.bottom;
        boolean insideOriginal = plateX >= left && plateX <= right
                && plateY >= top && plateY <= bottom;
        if (!insideOriginal) {
            return PlateVehicleAssociation.unassigned(
                    "direct_roi_plate_outside_original_vehicle"
            );
        }
        boolean sensibleVerticalRegion = plateY
                >= top + owner.bounds.height() * 0.30f;
        if (!sensibleVerticalRegion) {
            return PlateVehicleAssociation.unassigned(
                    "direct_roi_plate_outside_sensible_region"
            );
        }

        float ownerScore = score(plateX, plateY, owner);
        float bestOtherScore = Float.NEGATIVE_INFINITY;
        if (vehicles != null) {
            for (VehicleCandidate vehicle : vehicles) {
                if (vehicle == null || vehicle.entityId == owner.entityId) continue;
                bestOtherScore = Math.max(
                        bestOtherScore,
                        score(plateX, plateY, vehicle)
                );
            }
        }
        if (ownerScore < MIN_ASSOCIATION_SCORE) {
            return PlateVehicleAssociation.unassigned(
                    "direct_roi_owner_score_below_threshold"
            );
        }
        if (bestOtherScore >= MIN_ASSOCIATION_SCORE
                && ownerScore - bestOtherScore < MIN_ASSOCIATION_MARGIN) {
            return PlateVehicleAssociation.ambiguous(
                    Math.max(ownerScore, bestOtherScore),
                    "direct_roi_competing_entity_margin="
                            + (ownerScore - bestOtherScore)
            );
        }
        return PlateVehicleAssociation.directValidated(
                owner.entityId,
                owner.vehicleTrackId,
                ownerScore,
                "direct_roi_geometry_validated"
        );
    }

    private static float score(float plateX, float plateY, VehicleCandidate vehicle) {
        float left = vehicle.bounds.left;
        float top = vehicle.bounds.top;
        float right = vehicle.bounds.right;
        float bottom = vehicle.bounds.bottom;
        boolean containsCenter = plateX >= left && plateX <= right
                && plateY >= top && plateY <= bottom;
        float targetX = vehicle.bounds.centerX();
        float targetY = top + vehicle.bounds.height() * 0.76f;
        float distance = (float) Math.hypot(plateX - targetX, plateY - targetY);
        float scale = Math.max(
                0.04f,
                (float) Math.hypot(vehicle.bounds.width(), vehicle.bounds.height())
        );
        float proximity = clamp01(1f - distance / scale);
        float lowerHalf = plateY >= top + vehicle.bounds.height() * 0.45f
                && plateY <= bottom ? 1f : 0f;
        float freshness = vehicle.effectiveConfidence;
        return (containsCenter ? 0.52f : 0f)
                + 0.28f * proximity
                + 0.12f * lowerHalf
                + 0.08f * freshness;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
