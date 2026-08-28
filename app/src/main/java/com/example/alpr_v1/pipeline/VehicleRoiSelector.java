package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.DetectionDeduplicator;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.domain.NormalizedBounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/** Wybiera ograniczoną liczbę dominujących i poszerzonych ROI pojazdów. */
final class VehicleRoiSelector {
    static final class Region {
        final int left;
        final int top;
        final int right;
        final int bottom;

        // Detekcja pojazdu, z której powstał ROI.
        // null oznacza obszar pełnej klatki.
        final Detection vehicle;

        Region(
                int left,
                int top,
                int right,
                int bottom,
                Detection vehicle
        ) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.vehicle = vehicle;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        long area() {
            return (long) width() * height();
        }
    }

    private VehicleRoiSelector() {}

    static List<Region> select(
            List<Detection> rawVehicles,
            int imageWidth,
            int imageHeight,
            int maximumRegions,
            float marginFraction,
            float iouThreshold
    ) {
        List<Detection> vehicles = new ArrayList<>(DetectionDeduplicator.suppress(
                rawVehicles, iouThreshold, 0.82f, false
        ));
        vehicles.sort(Comparator.comparingDouble(VehicleRoiSelector::priority).reversed());
        List<Region> regions = new ArrayList<>();
        for (Detection vehicle : vehicles) {
            if (regions.size() >= Math.max(0, maximumRegions)) break;
            float marginX = vehicle.width() * Math.max(0f, marginFraction);
            float marginY = vehicle.height() * Math.max(0f, marginFraction);
            int left = clamp((int) Math.floor(vehicle.left - marginX), 0, imageWidth - 1);
            int top = clamp((int) Math.floor(vehicle.top - marginY), 0, imageHeight - 1);
            int right = clamp((int) Math.ceil(vehicle.right + marginX), left + 1, imageWidth);
            int bottom = clamp((int) Math.ceil(vehicle.bottom + marginY), top + 1, imageHeight);
            Region region = new Region(
                    left,
                    top,
                    right,
                    bottom,
                    vehicle
            );
            if (region.width() >= 8 && region.height() >= 8) regions.add(region);
        }
        return regions;
    }

    /** Ranks already distinct entity candidates without applying detection NMS again. */
    static List<VehicleRoi> selectTrackedCandidates(
            List<VehicleCandidate> candidates,
            int imageWidth,
            int imageHeight,
            int maximumRegions,
            float marginFraction
    ) {
        List<VehicleCandidate> ranked = new ArrayList<>(
                candidates == null ? java.util.Collections.emptyList() : candidates
        );
        ranked.sort(Comparator.comparingDouble(
                VehicleRoiSelector::trackedPriority
        ).reversed());
        List<VehicleRoi> rois = new ArrayList<>();
        for (VehicleCandidate candidate : ranked) {
            if (candidate == null || rois.size() >= Math.max(0, maximumRegions)) break;
            float marginX = candidate.bounds.width() * Math.max(0f, marginFraction);
            float marginY = candidate.bounds.height() * Math.max(0f, marginFraction);
            int left = clamp((int) Math.floor(
                    (candidate.bounds.left - marginX) * imageWidth
            ), 0, imageWidth - 1);
            int top = clamp((int) Math.floor(
                    (candidate.bounds.top - marginY) * imageHeight
            ), 0, imageHeight - 1);
            int right = clamp((int) Math.ceil(
                    (candidate.bounds.right + marginX) * imageWidth
            ), left + 1, imageWidth);
            int bottom = clamp((int) Math.ceil(
                    (candidate.bounds.bottom + marginY) * imageHeight
            ), top + 1, imageHeight);
            if (right - left >= 8 && bottom - top >= 8) {
                rois.add(new VehicleRoi(candidate, left, top, right, bottom));
            }
        }
        return rois;
    }

    /** Uses historical raw MP geometry while retaining diagnostic entity identity. */
    static List<VehicleRoi> selectRawMpCandidates(
            List<Detection> rawVehicles,
            List<VehicleCandidate> trackedCandidates,
            int imageWidth,
            int imageHeight,
            int maximumRegions,
            float marginFraction,
            float iouThreshold
    ) {
        List<Region> rawRegions = select(
                rawVehicles,
                imageWidth,
                imageHeight,
                maximumRegions,
                marginFraction,
                iouThreshold
        );
        Map<Integer, VehicleCandidate> candidateBySource = new java.util.HashMap<>();
        for (VehicleCandidate candidate : trackedCandidates) {
            if (candidate.sourceIndex >= 0) {
                candidateBySource.put(candidate.sourceIndex, candidate);
            }
        }
        Map<Detection, Integer> sourceByDetection = new IdentityHashMap<>();
        for (int index = 0; index < rawVehicles.size(); index++) {
            sourceByDetection.put(rawVehicles.get(index), index);
        }
        List<VehicleRoi> result = new ArrayList<>();
        for (Region region : rawRegions) {
            Integer sourceIndex = sourceByDetection.get(region.vehicle);
            VehicleCandidate tracked = sourceIndex == null
                    ? null : candidateBySource.get(sourceIndex);
            if (tracked == null) continue;
            NormalizedBounds rawBounds = new NormalizedBounds(
                    region.vehicle.left / Math.max(1f, imageWidth),
                    region.vehicle.top / Math.max(1f, imageHeight),
                    region.vehicle.right / Math.max(1f, imageWidth),
                    region.vehicle.bottom / Math.max(1f, imageHeight)
            );
            VehicleCandidate rawCandidate = new VehicleCandidate(
                    tracked.entityId,
                    tracked.vehicleTrackId,
                    rawBounds,
                    region.vehicle.confidence,
                    region.vehicle.confidence,
                    tracked.exitUrgency,
                    false,
                    0,
                    tracked.lastMeasurementTimestampNanos,
                    tracked.lastMeasurementTimestampNanos,
                    sourceIndex,
                    tracked.acquisitionState
            );
            result.add(new VehicleRoi(
                    rawCandidate,
                    region.left,
                    region.top,
                    region.right,
                    region.bottom
            ));
        }
        return result;
    }

    /**
     * Single policy boundary used by the production engine. Keeping the branch here
     * makes it impossible for RAW_MP to accidentally receive a reconstructed track
     * list without a regression test noticing the changed geometry.
     */
    static List<VehicleRoi> selectForPolicy(
            VehicleTrackingPolicy policy,
            List<Detection> rawMpVehicles,
            List<VehicleCandidate> trackedCandidates,
            int imageWidth,
            int imageHeight,
            int maximumRegions,
            float marginFraction,
            float iouThreshold
    ) {
        if (policy == VehicleTrackingPolicy.RAW_MP) {
            return selectRawMpCandidates(
                    rawMpVehicles,
                    trackedCandidates,
                    imageWidth,
                    imageHeight,
                    maximumRegions,
                    marginFraction,
                    iouThreshold
            );
        }
        return selectTrackedCandidates(
                trackedCandidates,
                imageWidth,
                imageHeight,
                maximumRegions,
                marginFraction
        );
    }

    static Region region(VehicleRoi roi) {
        return new Region(roi.left, roi.top, roi.right, roi.bottom, null);
    }

    static Region fullFrame(int width, int height) {
        return new Region(
                0,
                0,
                width,
                height,
                null
        );
    }

    static Region normalizedRegion(
            int imageWidth,
            int imageHeight,
            float normalizedLeft,
            float normalizedTop,
            float normalizedRight,
            float normalizedBottom
    ) {
        int safeWidth = Math.max(1, imageWidth);
        int safeHeight = Math.max(1, imageHeight);
        int left = clamp(
                (int) Math.floor(normalizedLeft * safeWidth),
                0,
                safeWidth - 1
        );
        int top = clamp(
                (int) Math.floor(normalizedTop * safeHeight),
                0,
                safeHeight - 1
        );
        int right = clamp(
                (int) Math.ceil(normalizedRight * safeWidth),
                left + 1,
                safeWidth
        );
        int bottom = clamp(
                (int) Math.ceil(normalizedBottom * safeHeight),
                top + 1,
                safeHeight
        );
        return new Region(left, top, right, bottom, null);
    }

    private static double priority(Detection detection) {
        return detection.confidence
                * Math.sqrt(Math.max(1.0, detection.width() * detection.height()));
    }

    private static double trackedPriority(VehicleCandidate candidate) {
        return candidate.effectiveConfidence
                * Math.sqrt(Math.max(0.000001, candidate.bounds.area()))
                + 0.15 * candidate.exitUrgency;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
