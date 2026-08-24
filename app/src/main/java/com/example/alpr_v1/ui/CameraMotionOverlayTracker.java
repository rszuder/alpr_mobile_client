package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

import com.example.alpr_v1.tracking.MotionBoxTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter trackera ruchu kamery do elementów rysowanych przez overlay.
 *
 * Tracker ruchu jest stosowany wyłącznie do ramek tablic.
 * Elementy diagnostyczne kaskady, takie jak pojazd i ROI MP→MT,
 * są przekazywane bezpośrednio do warstwy wizualizacji.
 */
public final class CameraMotionOverlayTracker {

    private final MotionBoxTracker tracker = new MotionBoxTracker();

    /*
     * Zapamiętujemy wyłącznie poprzednie elementy typu PLATE.
     * Są potrzebne do odtworzenia keypointów podczas krótkiej predykcji trackera.
     */
    private List<OverlayItem> previousPlateItems = Collections.emptyList();

    public synchronized List<OverlayItem> update(
            List<OverlayItem> items,
            long observationNanos,
            long presentationNanos
    ) {
        List<OverlayItem> plateItems = new ArrayList<>();
        List<OverlayItem> passthroughItems = new ArrayList<>();

        /*
         * Rozdzielamy elementy według ich roli.
         *
         * PLATE        -> MotionBoxTracker
         * VEHICLE      -> bez trackera
         * VEHICLE_ROI  -> bez trackera
         */
        for (OverlayItem item : items) {
            if (item.kind == OverlayItem.Kind.PLATE) {
                plateItems.add(item);
            } else {
                passthroughItems.add(item);
            }
        }

        /*
         * Do MotionBoxTracker trafiają już wyłącznie tablice.
         */
        List<MotionBoxTracker.Observation> observations = new ArrayList<>();

        for (int i = 0; i < plateItems.size(); i++) {
            OverlayItem item = plateItems.get(i);
            RectF box = item.normalizedBounds;

            observations.add(new MotionBoxTracker.Observation(
                    new MotionBoxTracker.Box(
                            box.left,
                            box.top,
                            box.right,
                            box.bottom
                    ),
                    item.label,
                    i
            ));
        }

        /*
         * Najpierw przekazujemy bieżące elementy diagnostyczne kaskady.
         */
        List<OverlayItem> visible = new ArrayList<>(passthroughItems);

        /*
         * Następnie dokładamy wygładzone / przewidziane ramki tablic.
         */
        List<MotionBoxTracker.Result> trackedResults =
                tracker.update(
                        observations,
                        observationNanos,
                        presentationNanos
                );

        android.util.Log.d(
                "ALPR_OVERLAY",
                "INPUT total=" + items.size()
                        + " plates=" + plateItems.size()
                        + " passthrough=" + passthroughItems.size()
                        + " tracked=" + trackedResults.size()
        );

        for (OverlayItem item : items) {

            android.util.Log.d(
                    "ALPR_OVERLAY",
                    "INPUT kind=" + item.kind
                            + " track=" + item.trackId
                            + " carried=" + item.carriedPrediction
                            + " label=" + item.label
                            + " box=" + item.normalizedBounds
            );
        }

        for (MotionBoxTracker.Result result : trackedResults) {

            OverlayItem source =
                    sourcePlateItem(
                            plateItems,
                            result.sourceIndex,
                            result.label
                    );

            RectF target =
                    new RectF(
                            result.box.left,
                            result.box.top,
                            result.box.right,
                            result.box.bottom
                    );

            boolean carried =
                    result.sourceIndex < 0;

            android.util.Log.d(
                    "ALPR_OVERLAY",
                    "TRACK track=" + result.trackId
                            + " sourceIndex=" + result.sourceIndex
                            + " predicted=" + result.predicted
                            + " carried=" + carried
                            + " box=" + target
            );

            visible.add(
                    new OverlayItem(
                            OverlayItem.Kind.PLATE,
                            target,
                            remapPoints(
                                    source,
                                    target
                            ),
                            result.label,
                            result.trackId,
                            carried
                    )
            );
        }

        for (OverlayItem item : visible) {

            android.util.Log.d(
                    "ALPR_OVERLAY",
                    "VISIBLE kind=" + item.kind
                            + " track=" + item.trackId
                            + " carried=" + item.carriedPrediction
                            + " label=" + item.label
                            + " box=" + item.normalizedBounds
            );
        }
        /*
         * Do następnego przebiegu zachowujemy tylko rzeczywiste
         * elementy tablic z bieżącej klatki.
         */
        previousPlateItems = Collections.unmodifiableList(
                new ArrayList<>(plateItems)
        );

        return Collections.unmodifiableList(visible);
    }

    public synchronized void reset() {
        tracker.reset();
        previousPlateItems = Collections.emptyList();
    }

    private OverlayItem sourcePlateItem(
            List<OverlayItem> items,
            int index,
            String label
    ) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }

        for (OverlayItem item : previousPlateItems) {
            if (item.label.equals(label)) {
                return item;
            }
        }

        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                new RectF(),
                Collections.emptyList(),
                label,
                0L,
                false
        );
    }

    private static List<PointF> remapPoints(
            OverlayItem source,
            RectF target
    ) {
        if (source.normalizedKeypoints.isEmpty()) {
            return Collections.emptyList();
        }

        RectF from = source.normalizedBounds;

        if (from.width() <= 0f || from.height() <= 0f) {
            return source.normalizedKeypoints;
        }

        List<PointF> points = new ArrayList<>(
                source.normalizedKeypoints.size()
        );

        for (PointF point : source.normalizedKeypoints) {
            float relativeX =
                    (point.x - from.left) / from.width();

            float relativeY =
                    (point.y - from.top) / from.height();

            points.add(new PointF(
                    target.left + relativeX * target.width(),
                    target.top + relativeY * target.height()
            ));
        }

        return points;
    }
}