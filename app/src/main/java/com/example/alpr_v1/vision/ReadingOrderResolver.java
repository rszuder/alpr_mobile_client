package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ReadingOrderResolver {
    private ReadingOrderResolver() {}

    public static List<Detection> sort(List<Detection> detections) {
        if (detections.size() < 2) return new ArrayList<>(detections);
        List<Float> heights = new ArrayList<>();
        for (Detection detection : detections) heights.add(Math.max(1f, detection.height()));
        heights.sort(Float::compare);
        float medianHeight = heights.get(heights.size() / 2);
        float tolerance = Math.max(6f, medianHeight * 0.68f);

        List<Detection> byY = new ArrayList<>(detections);
        byY.sort(Comparator.comparingDouble(Detection::centerY).thenComparingDouble(Detection::centerX));
        List<Row> rows = new ArrayList<>();
        for (Detection detection : byY) {
            Row best = null;
            float bestDistance = Float.MAX_VALUE;
            for (Row row : rows) {
                float distance = Math.abs(detection.centerY() - row.centerY());
                if (distance <= tolerance && distance < bestDistance) {
                    best = row;
                    bestDistance = distance;
                }
            }
            if (best == null) {
                best = new Row();
                rows.add(best);
            }
            best.items.add(detection);
        }
        rows.sort(Comparator.comparingDouble(Row::centerY));
        List<Detection> result = new ArrayList<>();
        for (Row row : rows) {
            row.items.sort(Comparator.comparingDouble(Detection::centerX));
            result.addAll(row.items);
        }
        return result;
    }

    public static String text(List<Detection> detections, List<String> labels) {
        StringBuilder text = new StringBuilder();
        for (Detection detection : sort(detections)) {
            if (detection.classId >= 0 && detection.classId < labels.size()) {
                text.append(labels.get(detection.classId));
            }
        }
        return text.toString();
    }

    private static final class Row {
        final List<Detection> items = new ArrayList<>();

        float centerY() {
            if (items.isEmpty()) return 0f;
            float sum = 0f;
            for (Detection item : items) sum += item.centerY();
            return sum / items.size();
        }
    }
}
