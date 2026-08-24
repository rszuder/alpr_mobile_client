package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ReadingOrderResolver {

    private static final float Y_TOLERANCE_RATIO = 0.68f;
    private static final float MIN_Y_TOLERANCE = 6f;

    private ReadingOrderResolver() {}

    public static List<Detection> sort(List<Detection> detections) {
        List<Detection> result = new ArrayList<>();

        for (List<Detection> row : rows(detections)) {
            result.addAll(row);
        }

        return result;
    }

    public static List<List<Detection>> rows(List<Detection> detections) {
        List<List<Detection>> result = new ArrayList<>();

        if (detections == null || detections.isEmpty()) {
            return result;
        }

        List<PositionedDetection> positioned = new ArrayList<>();

        for (int index = 0; index < detections.size(); index++) {
            positioned.add(
                    new PositionedDetection(
                            detections.get(index),
                            index
                    )
            );
        }

        if (positioned.size() == 1) {
            List<Detection> row = new ArrayList<>();
            row.add(positioned.get(0).detection);
            result.add(row);
            return result;
        }

        List<Float> heights = new ArrayList<>();

        for (PositionedDetection item : positioned) {
            heights.add(item.height);
        }

        float medianHeight = Math.max(
                1f,
                median(heights)
        );

        float globalTolerance = Math.max(
                MIN_Y_TOLERANCE,
                medianHeight * Y_TOLERANCE_RATIO
        );

        /*
         * Najpierw sprawdzamy, czy wszystkie znaki
         * mieszczą się w jednym pionowym paśmie.
         *
         * Chroni to pojedynczy wiersz przed sztucznym
         * rozdzieleniem z powodu niewielkiego jitteru centerY.
         */
        float minCenterY = Float.MAX_VALUE;
        float maxCenterY = -Float.MAX_VALUE;

        for (PositionedDetection item : positioned) {
            minCenterY = Math.min(minCenterY, item.centerY);
            maxCenterY = Math.max(maxCenterY, item.centerY);
        }

        if (maxCenterY - minCenterY <= globalTolerance) {
            positioned.sort(POSITION_IN_ROW);

            List<Detection> row = new ArrayList<>();

            for (PositionedDetection item : positioned) {
                row.add(item.detection);
            }

            result.add(row);
            return result;
        }

        /*
         * Potencjalna tablica wielowierszowa.
         * Znaki analizujemy od góry do dołu.
         */
        positioned.sort(POSITION_BY_Y);

        List<RowBucket> buckets = new ArrayList<>();

        for (PositionedDetection item : positioned) {

            RowBucket bestBucket = null;
            float bestDistance = Float.MAX_VALUE;

            for (RowBucket bucket : buckets) {

                float distance = Math.abs(
                        item.centerY - bucket.centerY
                );

                float bucketTolerance = Math.max(
                        globalTolerance,
                        bucket.medianHeight * Y_TOLERANCE_RATIO
                );

                if (distance > bucketTolerance) {
                    continue;
                }

                if (distance < bestDistance) {
                    bestBucket = bucket;
                    bestDistance = distance;
                }
            }

            if (bestBucket == null) {
                bestBucket = new RowBucket();
                buckets.add(bestBucket);
            }

            bestBucket.add(item);
        }

        /*
         * Kolejność czytania wierszy:
         * góra -> dół.
         */
        buckets.sort(
                Comparator.comparingDouble(
                        bucket -> bucket.centerY
                )
        );

        for (RowBucket bucket : buckets) {

            /*
             * Kolejność znaków w wierszu:
             * lewo -> prawo.
             */
            bucket.items.sort(POSITION_IN_ROW);

            List<Detection> row = new ArrayList<>();

            for (PositionedDetection item : bucket.items) {
                row.add(item.detection);
            }

            if (!row.isEmpty()) {
                result.add(row);
            }
        }

        return result;
    }

    public static String text(
            List<Detection> detections,
            List<String> labels
    ) {
        StringBuilder text = new StringBuilder();

        for (Detection detection : sort(detections)) {

            if (detection.classId >= 0
                    && detection.classId < labels.size()) {

                text.append(
                        labels.get(detection.classId)
                );
            }
        }

        return text.toString();
    }

    private static final Comparator<PositionedDetection>
            POSITION_IN_ROW =
            Comparator
                    .comparingDouble(
                            (PositionedDetection item) -> item.centerX
                    )
                    .thenComparingInt(
                            item -> item.originalIndex
                    );

    private static final Comparator<PositionedDetection>
            POSITION_BY_Y =
            Comparator
                    .comparingDouble(
                            (PositionedDetection item) -> item.centerY
                    )
                    .thenComparingDouble(
                            item -> item.centerX
                    )
                    .thenComparingInt(
                            item -> item.originalIndex
                    );

    private static float median(List<Float> values) {

        if (values == null || values.isEmpty()) {
            return 0f;
        }

        List<Float> ordered = new ArrayList<>(values);
        ordered.sort(Float::compare);

        int middle = ordered.size() / 2;

        if (ordered.size() % 2 == 1) {
            return ordered.get(middle);
        }

        return (
                ordered.get(middle - 1)
                        + ordered.get(middle)
        ) * 0.5f;
    }

    private static final class PositionedDetection {

        final Detection detection;
        final int originalIndex;
        final float centerX;
        final float centerY;
        final float height;

        PositionedDetection(
                Detection detection,
                int originalIndex
        ) {
            this.detection = detection;
            this.originalIndex = originalIndex;
            this.centerX = detection.centerX();
            this.centerY = detection.centerY();
            this.height = Math.max(
                    1f,
                    detection.height()
            );
        }
    }

    private static final class RowBucket {

        final List<PositionedDetection> items =
                new ArrayList<>();

        float centerY;
        float medianHeight;

        void add(PositionedDetection item) {

            items.add(item);

            List<Float> centers = new ArrayList<>();
            List<Float> heights = new ArrayList<>();

            for (PositionedDetection current : items) {
                centers.add(current.centerY);
                heights.add(current.height);
            }

            centerY = median(centers);

            medianHeight = Math.max(
                    1f,
                    median(heights)
            );
        }
    }
}