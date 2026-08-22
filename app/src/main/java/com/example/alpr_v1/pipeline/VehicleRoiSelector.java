package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.DetectionDeduplicator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    static Region fullFrame(int width, int height) {
        return new Region(
                0,
                0,
                width,
                height,
                null
        );
    }

    private static double priority(Detection detection) {
        return detection.confidence
                * Math.sqrt(Math.max(1.0, detection.width() * detection.height()));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
