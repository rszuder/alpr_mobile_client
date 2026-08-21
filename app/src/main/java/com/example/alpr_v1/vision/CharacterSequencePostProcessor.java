package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Geometryczna deduplikacja i filtr spójności znaków po inferencji MZ. */
public final class CharacterSequencePostProcessor {
    private static final float OVERLAP_THRESHOLD = 0.70f;
    private static final float CENTER_Y_TOLERANCE = 0.60f;
    private static final float MIN_HEIGHT_RATIO = 0.55f;
    private static final float MAX_HEIGHT_RATIO = 1.80f;
    private static final float MAX_WIDTH_RATIO = 2.60f;
    private static final float SOFT_NEIGHBOR_OVERLAP = 0.18f;
    private static final float HARD_NEIGHBOR_OVERLAP = 0.30f;

    private CharacterSequencePostProcessor() {}

    public static List<Detection> process(List<Detection> detections, int expectedCount) {
        List<Detection> unique = suppressDuplicates(detections);
        if (unique.size() <= 1) return unique;
        List<List<Detection>> rows = ReadingOrderResolver.rows(unique);
        List<Detection> dominantRow = rows.stream()
                .max(Comparator.comparingInt((List<Detection> row) -> row.size()))
                .orElse(java.util.Collections.emptyList());
        Stats dominantStats = stats(referenceSubset(dominantRow));
        List<Detection> filtered = new ArrayList<>();
        for (List<Detection> row : rows) {
            if (row.size() == 1 && dominantRow.size() >= 2
                    && isGeometryOutlier(row.get(0), dominantStats)) {
                continue;
            }
            filtered.addAll(filterRow(row, 0));
        }
        if (expectedCount > 0 && filtered.size() > expectedCount) {
            filtered = bestCountCandidates(filtered, expectedCount);
        }
        return ReadingOrderResolver.sort(filtered);
    }

    public static List<Detection> suppressDuplicates(List<Detection> detections) {
        List<Detection> ordered = new ArrayList<>(detections);
        ordered.sort(Comparator.comparingDouble((Detection item) -> item.confidence).reversed());
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : ordered) {
            boolean duplicate = false;
            for (Detection selected : kept) {
                float containment = DetectionDeduplicator.overlapOverSmaller(candidate, selected);
                float iou = NonMaxSuppression.iou(candidate, selected);
                float widthNorm = Math.max(1f, Math.min(candidate.width(), selected.width()));
                float heightNorm = Math.max(1f, Math.min(candidate.height(), selected.height()));
                float dx = Math.abs(candidate.centerX() - selected.centerX()) / widthNorm;
                float dy = Math.abs(candidate.centerY() - selected.centerY()) / heightNorm;
                boolean closeCenters = dx <= 0.35f && dy <= 0.45f;
                boolean moderateOverlap = containment >= Math.max(0.18f, OVERLAP_THRESHOLD * 0.45f);
                boolean moderateIou = iou >= Math.max(0.12f, OVERLAP_THRESHOLD * 0.35f);
                if (containment >= OVERLAP_THRESHOLD
                        || (closeCenters && (moderateOverlap || moderateIou))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) kept.add(candidate);
        }
        return ReadingOrderResolver.sort(kept);
    }

    private static List<Detection> filterRow(List<Detection> row, int expectedCount) {
        if (row.size() <= 1) return new ArrayList<>(row);
        Stats initial = stats(referenceSubset(row));
        List<Detection> geometry = new ArrayList<>();
        for (Detection detection : row) {
            if (!isGeometryOutlier(detection, initial)) geometry.add(detection);
        }
        if (expectedCount > 0 && geometry.size() < expectedCount && row.size() >= expectedCount) {
            geometry = bestCountCandidates(row, expectedCount);
        }
        if (geometry.size() <= 1) return geometry;

        geometry.sort(Comparator.comparingDouble(Detection::centerX));
        Stats current = stats(referenceSubset(geometry));
        List<Detection> result = new ArrayList<>();
        for (Detection candidate : geometry) {
            boolean keep = true;
            while (!result.isEmpty() && conflicts(result.get(result.size() - 1), candidate, current)) {
                Detection previous = result.get(result.size() - 1);
                if (candidateScore(candidate, current) > candidateScore(previous, current) + 0.000001f) {
                    result.remove(result.size() - 1);
                } else {
                    keep = false;
                    break;
                }
            }
            if (keep) result.add(candidate);
        }
        return result;
    }

    private static List<Detection> referenceSubset(List<Detection> detections) {
        if (detections.size() <= 2) return new ArrayList<>(detections);
        List<Detection> best = new ArrayList<>(detections);
        float bestScore = subsetScore(best);
        List<Detection> anchors = new ArrayList<>(detections);
        anchors.sort(Comparator
                .comparingDouble((Detection item) -> item.confidence).reversed()
                .thenComparingDouble(item -> -item.height()));
        for (Detection anchor : anchors) {
            List<Detection> cluster = new ArrayList<>();
            for (Detection detection : detections) {
                float heightSimilarity = Math.min(anchor.height(), detection.height())
                        / Math.max(1f, Math.max(anchor.height(), detection.height()));
                float centerGap = Math.abs(anchor.centerY() - detection.centerY())
                        / Math.max(1f, Math.max(anchor.height(), detection.height()));
                if (heightSimilarity >= MIN_HEIGHT_RATIO && centerGap <= CENTER_Y_TOLERANCE) {
                    cluster.add(detection);
                }
            }
            float score = subsetScore(cluster);
            if (!cluster.isEmpty() && score > bestScore + 0.000001f) {
                best = cluster;
                bestScore = score;
            }
        }
        return best;
    }

    private static float subsetScore(List<Detection> detections) {
        if (detections.isEmpty()) return 0f;
        List<Float> heights = new ArrayList<>();
        float confidence = 0f;
        for (Detection detection : detections) {
            heights.add(Math.max(1f, detection.height()));
            confidence += Math.max(0f, detection.confidence);
        }
        float medianHeight = median(heights);
        return detections.size() * medianHeight + 0.35f * confidence;
    }

    private static boolean isGeometryOutlier(Detection detection, Stats stats) {
        float widthRatio = detection.width() / stats.medianWidth;
        float heightRatio = detection.height() / stats.medianHeight;
        float centerOffset = Math.abs(detection.centerY() - stats.medianCenterY) / stats.medianHeight;
        return centerOffset > CENTER_Y_TOLERANCE
                || heightRatio < MIN_HEIGHT_RATIO
                || heightRatio > MAX_HEIGHT_RATIO
                || widthRatio > MAX_WIDTH_RATIO
                || (widthRatio < 0.12f && detection.confidence < 0.60f);
    }

    private static boolean conflicts(Detection left, Detection right, Stats stats) {
        float overlap = left.right - right.left;
        if (overlap <= 0f) return false;
        float overlapRatio = overlap / Math.max(1f, Math.min(left.width(), right.width()));
        if (overlapRatio >= HARD_NEIGHBOR_OVERLAP) return true;
        float centerGap = Math.abs(left.centerY() - right.centerY()) / stats.medianHeight;
        float heightSimilarity = Math.min(left.height(), right.height())
                / Math.max(1f, Math.max(left.height(), right.height()));
        return overlapRatio >= SOFT_NEIGHBOR_OVERLAP
                && centerGap <= CENTER_Y_TOLERANCE
                && heightSimilarity >= MIN_HEIGHT_RATIO;
    }

    private static float candidateScore(Detection detection, Stats stats) {
        float centerPenalty = Math.min(1.5f,
                Math.abs(detection.centerY() - stats.medianCenterY) / stats.medianHeight);
        float heightPenalty = Math.min(1.5f,
                Math.abs(detection.height() - stats.medianHeight) / stats.medianHeight);
        float widthRatio = detection.width() / stats.medianWidth;
        float widthPenalty = 0f;
        if (widthRatio > 1.90f) widthPenalty += Math.min(1.5f, widthRatio - 1.90f);
        if (widthRatio < 0.18f) widthPenalty += Math.min(1f, (0.18f - widthRatio) / 0.18f);
        return detection.confidence
                - 0.20f * centerPenalty
                - 0.12f * heightPenalty
                - 0.06f * widthPenalty;
    }

    private static List<Detection> bestCountCandidates(List<Detection> detections, int count) {
        if (count <= 0 || detections.size() <= count) return ReadingOrderResolver.sort(detections);
        Stats stats = stats(referenceSubset(detections));
        List<Detection> ranked = new ArrayList<>(detections);
        ranked.sort(Comparator.comparingDouble((Detection item) -> candidateScore(item, stats)).reversed());
        return ReadingOrderResolver.sort(new ArrayList<>(ranked.subList(0, count)));
    }

    private static Stats stats(List<Detection> detections) {
        List<Float> widths = new ArrayList<>();
        List<Float> heights = new ArrayList<>();
        List<Float> centers = new ArrayList<>();
        for (Detection detection : detections) {
            widths.add(Math.max(1f, detection.width()));
            heights.add(Math.max(1f, detection.height()));
            centers.add(detection.centerY());
        }
        return new Stats(median(widths), median(heights), median(centers));
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) return 1f;
        values.sort(Float::compare);
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return Math.max(1f, values.get(middle));
        return Math.max(1f, (values.get(middle - 1) + values.get(middle)) * 0.5f);
    }

    private static final class Stats {
        final float medianWidth;
        final float medianHeight;
        final float medianCenterY;

        Stats(float medianWidth, float medianHeight, float medianCenterY) {
            this.medianWidth = Math.max(1f, medianWidth);
            this.medianHeight = Math.max(1f, medianHeight);
            this.medianCenterY = medianCenterY;
        }
    }
}
