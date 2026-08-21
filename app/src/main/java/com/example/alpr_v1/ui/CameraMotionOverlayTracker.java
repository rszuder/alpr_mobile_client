package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

import com.example.alpr_v1.tracking.MotionBoxTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Adapter trackera ruchu kamery do elementów rysowanych przez overlay. */
public final class CameraMotionOverlayTracker {
    private final MotionBoxTracker tracker = new MotionBoxTracker();
    private List<OverlayItem> previousItems = Collections.emptyList();

    public synchronized List<OverlayItem> update(
            List<OverlayItem> items,
            long observationNanos,
            long presentationNanos
    ) {
        List<MotionBoxTracker.Observation> observations = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OverlayItem item = items.get(i);
            RectF box = item.normalizedBounds;
            observations.add(new MotionBoxTracker.Observation(
                    new MotionBoxTracker.Box(box.left, box.top, box.right, box.bottom),
                    item.label,
                    i
            ));
        }

        List<OverlayItem> visible = new ArrayList<>();
        for (MotionBoxTracker.Result result : tracker.update(
                observations, observationNanos, presentationNanos
        )) {
            OverlayItem source = sourceItem(items, result.sourceIndex, result.label);
            RectF target = new RectF(
                    result.box.left, result.box.top, result.box.right, result.box.bottom
            );
            visible.add(new OverlayItem(
                    target,
                    remapPoints(source, target),
                    result.label,
                    result.trackId,
                    result.sourceIndex < 0
            ));
        }
        previousItems = Collections.unmodifiableList(new ArrayList<>(visible));
        return previousItems;
    }

    public synchronized void reset() {
        tracker.reset();
        previousItems = Collections.emptyList();
    }

    private OverlayItem sourceItem(List<OverlayItem> items, int index, String label) {
        if (index >= 0 && index < items.size()) return items.get(index);
        for (OverlayItem item : previousItems) {
            if (item.label.equals(label)) return item;
        }
        return new OverlayItem(new RectF(), Collections.emptyList(), label);
    }

    private static List<PointF> remapPoints(OverlayItem source, RectF target) {
        if (source.normalizedKeypoints.isEmpty()) return Collections.emptyList();
        RectF from = source.normalizedBounds;
        if (from.width() <= 0f || from.height() <= 0f) return source.normalizedKeypoints;
        List<PointF> points = new ArrayList<>(source.normalizedKeypoints.size());
        for (PointF point : source.normalizedKeypoints) {
            float relativeX = (point.x - from.left) / from.width();
            float relativeY = (point.y - from.top) / from.height();
            points.add(new PointF(
                    target.left + relativeX * target.width(),
                    target.top + relativeY * target.height()
            ));
        }
        return points;
    }
}
