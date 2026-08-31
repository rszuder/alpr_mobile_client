package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.TargetSnapshot;
import com.example.alpr_v1.pipeline.TargetStateMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wyrównuje opóźnioną geometrię MT wyłącznie w obrębie jednej encji. */
public final class EntityAwareOverlayAlignment {
    private EntityAwareOverlayAlignment() {
    }

    public static long resolveSourceEntityId(
            List<PlateObservation> observations,
            long sourcePlateTrackId
    ) {
        if (sourcePlateTrackId <= 0L || observations == null) return 0L;
        PlateObservation newest = null;
        for (PlateObservation observation : observations) {
            if (observation == null
                    || observation.entityId <= 0L
                    || (observation.trackId != sourcePlateTrackId
                    && observation.plateTrackId != sourcePlateTrackId)) {
                continue;
            }
            if (newest == null
                    || observation.acquisitionDirectiveRevision
                    > newest.acquisitionDirectiveRevision
                    || (observation.acquisitionDirectiveRevision
                    == newest.acquisitionDirectiveRevision
                    && observation.sourceSequence > newest.sourceSequence)) {
                newest = observation;
            }
        }
        return newest == null ? 0L : newest.entityId;
    }

    public static List<OverlayItem> align(
            List<OverlayItem> items,
            TargetSnapshot liveTarget,
            long sourceEntityId
    ) {
        if (items == null
                || items.isEmpty()
                || liveTarget == null
                || !liveTarget.hasTrack()
                || liveTarget.trackingQuality < TargetStateMachine.QUALITY_TRACKING
                || liveTarget.normalizedBounds.width() <= 0f
                || liveTarget.normalizedBounds.height() <= 0f) {
            return items;
        }

        OverlayItem sourcePlate = null;
        for (OverlayItem item : items) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && !item.carriedPrediction
                    && item.trackId == liveTarget.trackId) {
                sourcePlate = item;
                break;
            }
        }
        if (sourcePlate == null
                || sourcePlate.normalizedBounds.width() <= 0f
                || sourcePlate.normalizedBounds.height() <= 0f) {
            return items;
        }

        RectF liveBounds = new RectF(liveTarget.normalizedBounds);
        float dx = liveBounds.centerX() - sourcePlate.normalizedBounds.centerX();
        float dy = liveBounds.centerY() - sourcePlate.normalizedBounds.centerY();
        List<OverlayItem> aligned = new ArrayList<>(items.size());

        for (OverlayItem item : items) {
            if (item == sourcePlate) {
                aligned.add(new OverlayItem(
                        OverlayItem.Kind.PLATE,
                        liveBounds,
                        remapPointsToBounds(
                                sourcePlate.normalizedKeypoints,
                                sourcePlate.normalizedBounds,
                                liveBounds,
                                liveTarget.overlayItem == null
                                        ? Collections.emptyList()
                                        : liveTarget.overlayItem.normalizedKeypoints
                        ),
                        sourcePlate.label,
                        liveTarget.trackId,
                        false
                ));
            } else if (sourceEntityId > 0L
                    && item.kind != OverlayItem.Kind.PLATE
                    && item.trackId == sourceEntityId) {
                aligned.add(new OverlayItem(
                        item.kind,
                        translatedBounds(item.normalizedBounds, dx, dy),
                        translatedPoints(item.normalizedKeypoints, dx, dy),
                        item.label,
                        item.trackId,
                        item.carriedPrediction
                ));
            } else {
                aligned.add(item);
            }
        }
        return Collections.unmodifiableList(aligned);
    }

    private static List<PointF> remapPointsToBounds(
            List<PointF> sourcePoints,
            RectF sourceBounds,
            RectF targetBounds,
            List<PointF> fallbackPoints
    ) {
        if (sourcePoints == null
                || sourcePoints.isEmpty()
                || sourceBounds.width() <= 0f
                || sourceBounds.height() <= 0f) {
            return fallbackPoints == null
                    ? Collections.emptyList() : fallbackPoints;
        }
        List<PointF> result = new ArrayList<>(sourcePoints.size());
        for (PointF point : sourcePoints) {
            float relativeX = (point.x - sourceBounds.left) / sourceBounds.width();
            float relativeY = (point.y - sourceBounds.top) / sourceBounds.height();
            result.add(new PointF(
                    targetBounds.left + relativeX * targetBounds.width(),
                    targetBounds.top + relativeY * targetBounds.height()
            ));
        }
        return result;
    }

    private static List<PointF> translatedPoints(
            List<PointF> points,
            float dx,
            float dy
    ) {
        if (points == null || points.isEmpty()) return Collections.emptyList();
        List<PointF> translated = new ArrayList<>(points.size());
        for (PointF point : points) {
            translated.add(new PointF(
                    clamp01(point.x + dx),
                    clamp01(point.y + dy)
            ));
        }
        return translated;
    }

    private static RectF translatedBounds(RectF source, float dx, float dy) {
        RectF result = new RectF(source);
        result.offset(dx, dy);
        if (result.left < 0f) result.offset(-result.left, 0f);
        if (result.right > 1f) result.offset(1f - result.right, 0f);
        if (result.top < 0f) result.offset(0f, -result.top);
        if (result.bottom > 1f) result.offset(0f, 1f - result.bottom);
        return result;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
