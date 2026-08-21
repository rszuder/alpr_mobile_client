package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dodatkowa deduplikacja odporniejsza na ramki zagnieżdżone niż samo IoU. */
public final class DetectionDeduplicator {
    private DetectionDeduplicator() {}

    public static List<Detection> suppress(
            List<Detection> detections,
            float iouThreshold,
            float containmentThreshold,
            boolean classAware
    ) {
        List<Detection> ordered = new ArrayList<>(detections);
        ordered.sort(Comparator
                .comparingDouble((Detection item) -> item.confidence).reversed()
                .thenComparingDouble(item -> -(item.width() * item.height())));
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : ordered) {
            boolean duplicate = false;
            for (Detection selected : kept) {
                if (classAware && candidate.classId != selected.classId) continue;
                if (NonMaxSuppression.iou(candidate, selected) >= iouThreshold
                        || overlapOverSmaller(candidate, selected) >= containmentThreshold) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) kept.add(candidate);
        }
        return kept;
    }

    public static float overlapOverSmaller(Detection first, Detection second) {
        float left = Math.max(first.left, second.left);
        float top = Math.max(first.top, second.top);
        float right = Math.min(first.right, second.right);
        float bottom = Math.min(first.bottom, second.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float smaller = Math.min(first.width() * first.height(), second.width() * second.height());
        return smaller <= 0f ? 0f : intersection / smaller;
    }
}
