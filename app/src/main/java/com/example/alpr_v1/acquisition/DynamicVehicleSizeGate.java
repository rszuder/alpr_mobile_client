package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Admission to MT, never ownership or lifetime of a VehicleEntity. */
public final class DynamicVehicleSizeGate {
    public static final class Config {
        public static final Config INITIAL = new Config(120, 80, 100, 64);
        public final int enterWidth, enterHeight, keepWidth, keepHeight;
        public Config(int enterWidth, int enterHeight, int keepWidth, int keepHeight) {
            if (keepWidth <= 0 || keepHeight <= 0 || enterWidth < keepWidth || enterHeight < keepHeight)
                throw new IllegalArgumentException("Invalid vehicle size thresholds");
            this.enterWidth = enterWidth; this.enterHeight = enterHeight;
            this.keepWidth = keepWidth; this.keepHeight = keepHeight;
        }
    }

    public enum Reason { ALLOWED, VEHICLE_TOO_SMALL, VEHICLE_MEASUREMENT_REQUIRED }
    public static final class Decision {
        public final Reason reason;
        public final float widthPx, heightPx;
        public final int requiredWidthPx, requiredHeightPx;
        private Decision(Reason reason, float width, float height, int requiredWidth, int requiredHeight) {
            this.reason = reason; widthPx = width; heightPx = height;
            requiredWidthPx = requiredWidth; requiredHeightPx = requiredHeight;
        }
        public boolean allowed() { return reason == Reason.ALLOWED; }
    }

    private Config config;
    private final Map<Long, Decision> measured = new HashMap<>();
    private final Set<Long> admitted = new HashSet<>();
    private ContinuityStamp measurementStamp;
    private int sourceWidth, sourceHeight;
    private boolean enabled;

    public DynamicVehicleSizeGate() { this(Config.INITIAL); }
    public DynamicVehicleSizeGate(Config config) { this.config = java.util.Objects.requireNonNull(config); }
    public synchronized Config config() { return config; }
    public synchronized void setConfig(Config next) {
        java.util.Objects.requireNonNull(next);
        if (config.enterWidth == next.enterWidth && config.enterHeight == next.enterHeight
                && config.keepWidth == next.keepWidth && config.keepHeight == next.keepHeight) return;
        config = next;
        reset();
    }
    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled != enabled) reset();
        this.enabled = enabled;
    }
    public synchronized boolean enabled() { return enabled; }
    public synchronized void reset() { measured.clear(); admitted.clear(); measurementStamp = null; }

    /** Only raw detections from this positive MP measurement belong in boundsByEntity. */
    public synchronized void observe(ContinuityStamp stamp, int width, int height,
            Map<Long, NormalizedBounds> boundsByEntity) {
        if (!enabled) return;
        if (stamp == null || width <= 0 || height <= 0) throw new IllegalArgumentException("MP frame");
        if (!sameContext(measurementStamp, stamp) || width != sourceWidth || height != sourceHeight)
            admitted.clear();
        measured.clear();
        measurementStamp = stamp; sourceWidth = width; sourceHeight = height;
        admitted.retainAll(boundsByEntity.keySet());
        for (Map.Entry<Long, NormalizedBounds> item : boundsByEntity.entrySet()) {
            NormalizedBounds bounds = item.getValue();
            if (item.getKey() <= 0L || bounds == null || !bounds.valid()) continue;
            boolean keeping = admitted.contains(item.getKey());
            int minWidth = keeping ? config.keepWidth : config.enterWidth;
            int minHeight = keeping ? config.keepHeight : config.enterHeight;
            float pixelsWide = bounds.width() * width, pixelsHigh = bounds.height() * height;
            boolean allowed = pixelsWide + 0.0001f >= minWidth && pixelsHigh + 0.0001f >= minHeight;
            if (allowed) admitted.add(item.getKey()); else admitted.remove(item.getKey());
            measured.put(item.getKey(), new Decision(allowed ? Reason.ALLOWED : Reason.VEHICLE_TOO_SMALL,
                    pixelsWide, pixelsHigh, minWidth, minHeight));
        }
    }

    /** Queue uses the latest actual MP; invocation must recheck against its own source image. */
    public synchronized boolean canSchedule(long entityId) {
        return !enabled || measured.containsKey(entityId) && measured.get(entityId).allowed();
    }
    public synchronized boolean isTooSmall(long entityId) {
        return enabled && measured.containsKey(entityId)
                && measured.get(entityId).reason == Reason.VEHICLE_TOO_SMALL;
    }
    public synchronized boolean hasCurrentMeasurement(ContinuityStamp stamp, int width, int height) {
        return sameContext(measurementStamp, stamp) && sourceWidth == width && sourceHeight == height
                && stamp.sourceTimestampDomain == measurementStamp.sourceTimestampDomain
                && stamp.sourceSequence == measurementStamp.sourceSequence
                && stamp.sourceTimestampNanos == measurementStamp.sourceTimestampNanos;
    }
    public synchronized Decision check(long entityId, ContinuityStamp stamp, int width, int height) {
        if (!enabled) return new Decision(Reason.ALLOWED, 0f, 0f, 0, 0);
        if (!hasCurrentMeasurement(stamp, width, height) || !measured.containsKey(entityId))
            return new Decision(Reason.VEHICLE_MEASUREMENT_REQUIRED, 0f, 0f, config.enterWidth, config.enterHeight);
        return measured.get(entityId);
    }
    public synchronized Set<Long> measuredEntityIds() { return new HashSet<>(measured.keySet()); }
    private static boolean sameContext(ContinuityStamp first, ContinuityStamp second) {
        return first != null && second != null && first.sceneGeneration == second.sceneGeneration
                && first.visualEpoch == second.visualEpoch
                && first.cameraTransformGeneration == second.cameraTransformGeneration;
    }
}
