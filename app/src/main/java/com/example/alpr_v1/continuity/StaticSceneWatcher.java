package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Collections;
import com.example.alpr_v1.tracking.RegionFeatureChangeDetector;

/** Anchored luma and region-corner evidence. No detector inference or identity recovery. */
public final class StaticSceneWatcher {
    public static final class Profile {
        public final float margin, localFraction, globalFraction;
        public final int pixelDelta, confirmations;
        public final long confirmationNanos;
        public Profile(float margin, float local, float global, int delta, int confirmations, long nanos) {
            this.margin = margin; localFraction = local; globalFraction = global;
            pixelDelta = delta; this.confirmations = confirmations; confirmationNanos = nanos;
        }
    }
    public static final Profile DEFAULT = new Profile(0.12f, 0.30f, 0.55f, 24, 2, 50_000_000L);
    public static final class Result {
        public final boolean changed;
        public final float localFraction, globalFraction;
        public final String reason;
        public final int featurePoints;
        public final float featureLostFraction, featureMovedFraction;
        Result(boolean changed, float local, float global, String reason) {
            this(changed, local, global, reason, null);
        }
        Result(boolean changed, float local, float global, String reason, RegionFeatureChangeDetector.Result features) {
            this.changed = changed; localFraction = local; globalFraction = global; this.reason = reason;
            featurePoints = features == null ? 0 : features.points;
            featureLostFraction = features == null ? 0f : features.lostFraction;
            featureMovedFraction = features == null ? 0f : features.movedFraction;
        }
    }
    private final Profile profile;
    private StaticSceneWatchRegions regions = new StaticSceneWatchRegions(null, null, 0f);
    private byte[] reference;
    private byte[] latestBase;
    private StaticSceneWatcher zoomWatcher;
    private float watchedZoom = 1f;
    private final RegionFeatureChangeDetector features = new RegionFeatureChangeDetector();
    private boolean featureAnchorDirty = true;
    private int featureConsecutive;
    private long featureSince = -1L;
    private long featureLastCheck = -1L;
    private RegionFeatureChangeDetector.Result latestFeatures;
    private int width, height, consecutive;
    private long since = -1L;
    public StaticSceneWatcher() { this(DEFAULT); }
    public StaticSceneWatcher(Profile profile) { this.profile = profile; }
    public synchronized void arm(StaticSceneWatchRegions regions) {
        if (regions == null) return;
        if (!this.regions.featureBounds.equals(regions.featureBounds)) {
            featureAnchorDirty = true; featureConsecutive = 0; featureSince = -1L;
        }
        this.regions = regions;
        // Region discovery must not replace the scene captured before a slow inference.
        if (reference == null && latestBase != null) reference = latestBase.clone();
    }
    public synchronized void prepareForZoom() {
        if (reference == null && latestBase != null) reference = latestBase.clone();
    }
    public synchronized void reset() {
        regions = new StaticSceneWatchRegions(Collections.emptyList(), Collections.emptyList(), 0f);
        latestBase = null; zoomWatcher = null;
        resetReference();
    }
    private void resetReference() {
        reference = null; consecutive = 0; since = -1L;
        features.reset(); featureAnchorDirty = true; featureConsecutive = 0; featureSince = -1L;
        featureLastCheck = -1L; latestFeatures = null;
    }
    public synchronized Result observe(byte[] gray, int width, int height, long now, boolean transform) {
        return observe(gray, width, height, now, transform, 1f);
    }
    public synchronized Result observe(byte[] gray, int width, int height, long now, boolean transform, float zoomRatio) {
        if (transform) {
            if (zoomWatcher == null || Math.abs(watchedZoom - zoomRatio) > .01f) {
                zoomWatcher = new StaticSceneWatcher(profile); watchedZoom = zoomRatio;
            }
            zoomWatcher.arm(regions.atZoom(zoomRatio));
            return zoomWatcher.observe(gray, width, height, now, false);
        }
        zoomWatcher = null;
        if (gray == null || width <= 0 || height <= 0 || gray.length < width * height)
            return new Result(false, 0f, 0f, "no_luma");
        latestBase = gray;
        if (reference == null || this.width != width || this.height != height) {
            resetReference();
            reference = gray.clone(); this.width = width; this.height = height;
            return new Result(false, 0f, 0f, "reference_armed");
        }
        int step = Math.max(1, width / 96), count = 0;
        long signed = 0;
        for (int y = 0; y < height; y += step) for (int x = 0; x < width; x += step) {
            int i = y * width + x;
            signed += (gray[i] & 255) - (reference[i] & 255); count++;
        }
        float exposure = count == 0 ? 0f : signed / (float) count;
        float global = fraction(gray, new NormalizedBounds(0f, 0f, 1f, 1f), step, exposure);
        float local = 0f;
        for (NormalizedBounds region : regions.bounds) local = Math.max(local, fraction(gray, region, step, exposure));
        boolean inspectFeatures = featureAnchorDirty || featureLastCheck < 0L
                || now < featureLastCheck || now - featureLastCheck >= 100_000_000L;
        if (featureAnchorDirty) {
            features.anchor(reference, width, height, regions.featureBounds);
            featureAnchorDirty = false;
        }
        if (inspectFeatures) {
            latestFeatures = features.observe(gray, exposure);
            if (featureLastCheck >= 0L && now - featureLastCheck > 500_000_000L) {
                featureConsecutive = 0; featureSince = -1L;
            }
            featureLastCheck = now;
            if (!latestFeatures.significant || now < featureSince) { featureConsecutive = 0; featureSince = -1L; }
            else { if (featureSince < 0L) featureSince = now; featureConsecutive++; }
        }
        RegionFeatureChangeDetector.Result featureResult = latestFeatures;
        boolean featureCut = featureConsecutive >= 3 && now - featureSince >= 120_000_000L;
        boolean significant = local >= profile.localFraction || global >= profile.globalFraction;
        if (!significant || now < since) { consecutive = 0; since = -1L; }
        else { if (since < 0L) since = now; consecutive++; }
        boolean abrupt = global >= Math.max(0.65f, profile.globalFraction)
                || local >= Math.max(0.65f, profile.localFraction);
        boolean lumaCut = abrupt || significant && consecutive >= profile.confirmations
                && now - since >= profile.confirmationNanos;
        boolean cut = lumaCut || featureCut;
        Result result = new Result(cut, local, global,
                !lumaCut && featureCut ? "static_alpr_features_changed"
                        : local >= profile.localFraction ? "static_alpr_region_changed" : "static_global_changed",
                featureResult);
        if (cut) resetReference();
        return result;
    }
    private float fraction(byte[] gray, NormalizedBounds b, int step, float exposure) {
        int count = 0, changed = 0;
        for (int y = (int)(b.top * height); y < Math.min(height, Math.ceil(b.bottom * height)); y += step)
            for (int x = (int)(b.left * width); x < Math.min(width, Math.ceil(b.right * width)); x += step) {
                int i = y * width + x;
                if (Math.abs((gray[i] & 255) - (reference[i] & 255) - exposure) >= profile.pixelDelta) changed++;
                count++;
            }
        return count == 0 ? 0f : changed / (float)count;
    }
}
