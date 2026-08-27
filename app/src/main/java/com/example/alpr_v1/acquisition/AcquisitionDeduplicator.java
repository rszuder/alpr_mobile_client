package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.VehicleEntity;

import java.util.List;

/** Prevents re-saving an already acquired vehicle/registration. */
public final class AcquisitionDeduplicator {
    public static final class Result {
        public final boolean duplicate;
        public final float penalty;
        public final Long matchingEntityId;
        public final String reason;

        private Result(boolean duplicate, float penalty, Long matchingEntityId, String reason) {
            this.duplicate = duplicate;
            this.penalty = penalty;
            this.matchingEntityId = matchingEntityId;
            this.reason = reason;
        }
    }

    public Result evaluate(VehicleEntity candidate, List<VehicleEntity> sceneEntities) {
        if (candidate == null || sceneEntities == null) return none();
        for (VehicleEntity other : sceneEntities) {
            if (other == null || other.entityId() == candidate.entityId()
                    || !other.acquisitionCompleted()) continue;
            String candidateText = candidate.registration().text;
            String otherText = other.registration().text;
            if (!candidateText.isEmpty() && candidateText.equals(otherText)) {
                return new Result(true, 1f, other.entityId(), "same_registration");
            }
            float appearance = cosine(
                    candidate.vehicleAppearance(), other.vehicleAppearance()
            );
            float overlap = candidate.vehicleBounds() == null || other.vehicleBounds() == null
                    ? 0f : candidate.vehicleBounds().iou(other.vehicleBounds());
            if (appearance >= 0.96f && overlap >= 0.55f) {
                return new Result(true, 0.9f, other.entityId(), "same_vehicle_appearance");
            }
            if (appearance >= 0.88f) {
                return new Result(false, 0.35f, other.entityId(), "similar_vehicle");
            }
        }
        return none();
    }

    private static Result none() { return new Result(false, 0f, null, "unique"); }

    private static float cosine(AppearanceDescriptor first, AppearanceDescriptor second) {
        if (first == null || second == null) return 0f;
        float[] left = first.values();
        float[] right = second.values();
        if (left.length == 0 || left.length != right.length) return 0f;
        float dot = 0f;
        float a = 0f;
        float b = 0f;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            a += left[index] * left[index];
            b += right[index] * right[index];
        }
        float denominator = (float) Math.sqrt(a * b);
        return denominator <= 1e-6f ? 0f : Math.max(-1f, Math.min(1f, dot / denominator));
    }
}
