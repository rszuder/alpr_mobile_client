package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NonMaxSuppression {
    private NonMaxSuppression() {}

    public static List<Detection> apply(List<Detection> detections, float iouThreshold, boolean classAware) {
        List<Detection> remaining = new ArrayList<>(detections);
        remaining.sort(Comparator.comparingDouble((Detection item) -> item.confidence).reversed());
        List<Detection> selected = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Detection best = remaining.remove(0);
            selected.add(best);
            remaining.removeIf(candidate ->
                    (!classAware || candidate.classId == best.classId)
                            && iou(best, candidate) > iouThreshold
            );
        }
        return selected;
    }

    public static float iou(Detection a, Detection b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }
}
