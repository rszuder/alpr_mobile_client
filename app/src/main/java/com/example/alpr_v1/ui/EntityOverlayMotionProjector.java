package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.tracking.FrameMotionTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Projekcja lekkiego overlayu, w której każda encja ma własny dowód ruchu. */
public final class EntityOverlayMotionProjector {
    private static final float MAXIMUM_LOCAL_PLATE_DELTA = 0.30f;

    public List<OverlayItem> project(
            List<OverlayItem> diagnostics,
            List<OverlayItem> trackedPlates,
            long focusedEntityId,
            long focusedPlateTrackId,
            VehicleTrackingFrame vehicleFrame
    ) {
        return project(
                diagnostics,
                trackedPlates,
                focusedEntityId,
                focusedPlateTrackId,
                vehicleFrame,
                500_000_000L,
                FrameMotionTransform.invalid()
        );
    }

    public List<OverlayItem> project(
            List<OverlayItem> diagnostics,
            List<OverlayItem> trackedPlates,
            long focusedEntityId,
            long focusedPlateTrackId,
            VehicleTrackingFrame vehicleFrame,
            long maximumVehicleAgeNanos
    ) {
        return project(
                diagnostics,
                trackedPlates,
                focusedEntityId,
                focusedPlateTrackId,
                vehicleFrame,
                maximumVehicleAgeNanos,
                FrameMotionTransform.invalid()
        );
    }

    public List<OverlayItem> project(
            List<OverlayItem> diagnostics,
            List<OverlayItem> trackedPlates,
            long focusedEntityId,
            long focusedPlateTrackId,
            VehicleTrackingFrame vehicleFrame,
            long maximumVehicleAgeNanos,
            FrameMotionTransform frameMotion
    ) {
        List<OverlayItem> base = diagnostics == null
                ? Collections.emptyList() : diagnostics;
        List<OverlayItem> plates = trackedPlates == null
                ? Collections.emptyList() : trackedPlates;

        OverlayItem baseFocusedPlate = findPlate(base, focusedPlateTrackId);
        OverlayItem trackedFocusedPlate = findPlate(plates, focusedPlateTrackId);
        LocalDelta focusedDelta = LocalDelta.between(
                baseFocusedPlate,
                trackedFocusedPlate
        );

        Map<Long, VehicleCandidate> candidates = candidatesByEntity(vehicleFrame);
        Map<Long, OverlayItem> measuredVehicles = vehiclesByEntity(base);
        List<OverlayItem> projected = new ArrayList<>(base.size() + plates.size());

        for (OverlayItem item : base) {
            if (item == null || item.kind == OverlayItem.Kind.PLATE) continue;

            OverlayItem globallyMoved = frameMotion != null && frameMotion.valid
                    ? transformed(item, frameMotion)
                    : item;

            VehicleCandidate candidate = candidates.get(item.trackId);
            boolean focusedEntityGeometry = focusedEntityId > 0L
                    && item.trackId == focusedEntityId;
            if (vehicleFrame != null && item.trackId > 0L) {
                if (candidate != null
                        && candidate.predictionAgeNanos
                        > Math.max(0L, maximumVehicleAgeNanos)) {
                    continue;
                }
                if (candidate == null) {
                    if (focusedEntityGeometry && focusedDelta.valid) {
                        projected.add(translated(
                                item,
                                focusedDelta.dx,
                                focusedDelta.dy,
                                true
                        ));
                    }
                    continue;
                }
            }

            // Ruch całego kadru jest wspólnym, bieżącym dowodem dla każdej
            // warstwy. Nie może zostać zastąpiony przez lokalny delta PLATE,
            // który przy chwilowo błędnej kotwicy zamroziłby aktywne ROI.
            if (frameMotion != null && frameMotion.valid) {
                projected.add(globallyMoved);
                continue;
            }

            if (focusedEntityGeometry && focusedDelta.valid) {
                projected.add(translated(item, focusedDelta.dx, focusedDelta.dy, true));
                continue;
            }

            if (candidate == null) {
                projected.add(globallyMoved);
                continue;
            }

            if (item.kind == OverlayItem.Kind.VEHICLE) {
                projected.add(withCandidateBounds(item, candidate));
                continue;
            }

            OverlayItem measuredVehicle = measuredVehicles.get(item.trackId);
            LocalDelta entityDelta = LocalDelta.between(
                    measuredVehicle == null ? null : measuredVehicle.normalizedBounds,
                    candidate.bounds
            );
            projected.add(entityDelta.valid
                    ? translated(
                    item,
                    entityDelta.dx,
                    entityDelta.dy,
                    candidate.predicted
            )
                    : item);
        }

        for (OverlayItem plate : plates) {
            if (plate != null && plate.kind == OverlayItem.Kind.PLATE) {
                projected.add(plate);
            }
        }
        return Collections.unmodifiableList(projected);
    }

    /** Przenosi kompletny wynik MP/MT z jego klatki źródłowej do bieżącego podglądu. */
    public List<OverlayItem> compensateInferenceLatency(
            List<OverlayItem> items,
            FrameMotionTransform accumulatedMotion
    ) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        if (accumulatedMotion == null
                || !accumulatedMotion.valid
                || !accumulatedMotion.significant()) {
            return items;
        }
        List<OverlayItem> compensated = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item != null) {
                compensated.add(transformed(item, accumulatedMotion, false));
            }
        }
        return Collections.unmodifiableList(compensated);
    }

    private static OverlayItem transformed(
            OverlayItem source,
            FrameMotionTransform transform
    ) {
        return transformed(source, transform, true);
    }

    private static OverlayItem transformed(
            OverlayItem source,
            FrameMotionTransform transform,
            boolean markAsPrediction
    ) {
        RectF bounds = source.normalizedBounds;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        float[][] corners = new float[][]{
                {bounds.left, bounds.top},
                {bounds.right, bounds.top},
                {bounds.right, bounds.bottom},
                {bounds.left, bounds.bottom}
        };
        for (float[] corner : corners) {
            float x = transform.mapX(corner[0], corner[1]);
            float y = transform.mapY(corner[0], corner[1]);
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }
        List<PointF> points = new ArrayList<>(source.normalizedKeypoints.size());
        for (PointF point : source.normalizedKeypoints) {
            points.add(new PointF(
                    transform.mapX(point.x, point.y),
                    transform.mapY(point.x, point.y)
            ));
        }
        return new OverlayItem(
                source.kind,
                new RectF(left, top, right, bottom),
                points,
                source.label,
                source.trackId,
                markAsPrediction || source.carriedPrediction
        );
    }

    private static Map<Long, VehicleCandidate> candidatesByEntity(
            VehicleTrackingFrame frame
    ) {
        if (frame == null || frame.candidates.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, VehicleCandidate> result = new HashMap<>();
        for (VehicleCandidate candidate : frame.candidates) {
            if (candidate != null && candidate.entityId > 0L) {
                result.put(candidate.entityId, candidate);
            }
        }
        return result;
    }

    private static Map<Long, OverlayItem> vehiclesByEntity(List<OverlayItem> items) {
        Map<Long, OverlayItem> result = new HashMap<>();
        for (OverlayItem item : items) {
            if (item != null
                    && item.kind == OverlayItem.Kind.VEHICLE
                    && item.trackId > 0L) {
                result.put(item.trackId, item);
            }
        }
        return result;
    }

    private static OverlayItem findPlate(List<OverlayItem> items, long trackId) {
        if (trackId <= 0L) return null;
        for (OverlayItem item : items) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && item.trackId == trackId) {
                return item;
            }
        }
        return null;
    }

    private static OverlayItem withCandidateBounds(
            OverlayItem source,
            VehicleCandidate candidate
    ) {
        NormalizedBounds bounds = candidate.bounds;
        return new OverlayItem(
                source.kind,
                new RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
                source.normalizedKeypoints,
                source.label,
                source.trackId,
                candidate.predicted
        );
    }

    private static OverlayItem translated(
            OverlayItem source,
            float dx,
            float dy,
            boolean predicted
    ) {
        return new OverlayItem(
                source.kind,
                translatedBounds(source.normalizedBounds, dx, dy),
                translatedPoints(source.normalizedKeypoints, dx, dy),
                source.label,
                source.trackId,
                predicted || source.carriedPrediction
        );
    }

    private static RectF translatedBounds(RectF source, float dx, float dy) {
        float width = source.right - source.left;
        float height = source.bottom - source.top;
        float left = clamp(source.left + dx, 0f, Math.max(0f, 1f - width));
        float top = clamp(source.top + dy, 0f, Math.max(0f, 1f - height));
        return new RectF(left, top, left + width, top + height);
    }

    private static List<PointF> translatedPoints(
            List<PointF> source,
            float dx,
            float dy
    ) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<PointF> result = new ArrayList<>(source.size());
        for (PointF point : source) {
            result.add(new PointF(
                    clamp(point.x + dx, 0f, 1f),
                    clamp(point.y + dy, 0f, 1f)
            ));
        }
        return result;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class LocalDelta {
        final float dx;
        final float dy;
        final boolean valid;

        private LocalDelta(float dx, float dy, boolean valid) {
            this.dx = dx;
            this.dy = dy;
            this.valid = valid;
        }

        static LocalDelta between(OverlayItem from, OverlayItem to) {
            return from == null || to == null
                    ? new LocalDelta(0f, 0f, false)
                    : between(from.normalizedBounds, to.normalizedBounds);
        }

        static LocalDelta between(RectF from, NormalizedBounds to) {
            if (from == null || to == null) return new LocalDelta(0f, 0f, false);
            return checked(
                    to.centerX() - centerX(from),
                    to.centerY() - centerY(from)
            );
        }

        private static LocalDelta between(RectF from, RectF to) {
            return checked(centerX(to) - centerX(from), centerY(to) - centerY(from));
        }

        private static LocalDelta checked(float dx, float dy) {
            boolean valid = Float.isFinite(dx)
                    && Float.isFinite(dy)
                    && Math.abs(dx) <= MAXIMUM_LOCAL_PLATE_DELTA
                    && Math.abs(dy) <= MAXIMUM_LOCAL_PLATE_DELTA;
            return new LocalDelta(dx, dy, valid);
        }

        private static float centerX(RectF bounds) {
            return (bounds.left + bounds.right) * 0.5f;
        }

        private static float centerY(RectF bounds) {
            return (bounds.top + bounds.bottom) * 0.5f;
        }
    }
}
