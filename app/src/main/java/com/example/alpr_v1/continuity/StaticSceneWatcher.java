package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Collections;

/** Exposure-compensated, anchored luma comparison. No object tracking or inference. */
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
    public static final Profile DEFAULT = new Profile(0.12f, 0.30f, 0.55f, 24, 3, 150_000_000L);
    public static final class Result {
        public final boolean changed;
        public final float localFraction, globalFraction;
        public final String reason;
        Result(boolean changed, float local, float global, String reason) {
            this.changed = changed; localFraction = local; globalFraction = global; this.reason = reason;
        }
    }
    private final Profile profile;
    private StaticSceneWatchRegions regions = new StaticSceneWatchRegions(null, null, 0f);
    private byte[] reference;
    private byte[] latestBase;
    private boolean zoomReferencePreserved;
    private StaticSceneWatcher zoomWatcher;
    private int width, height, consecutive;
    private long since = -1L;
    public StaticSceneWatcher() { this(DEFAULT); }
    public StaticSceneWatcher(Profile profile) { this.profile = profile; }
    public synchronized void arm(StaticSceneWatchRegions regions) {
        this.regions = regions;
        if (!zoomReferencePreserved && latestBase != null) reference = latestBase.clone();
        consecutive = 0; since = -1L;
    }
    public synchronized void prepareForZoom() {
        if (latestBase != null) reference = latestBase.clone();
        zoomReferencePreserved = true;
    }
    public synchronized void reset() {
        regions = new StaticSceneWatchRegions(Collections.emptyList(), Collections.emptyList(), 0f);
        latestBase = null; zoomWatcher = null; zoomReferencePreserved = false;
        resetReference();
    }
    private void resetReference() { reference = null; consecutive = 0; since = -1L; }
    public synchronized Result observe(byte[] gray, int width, int height, long now, boolean transform) {
        if (transform) {
            zoomReferencePreserved = true;
            if (zoomWatcher == null) zoomWatcher = new StaticSceneWatcher(profile);
            return zoomWatcher.observe(gray, width, height, now, false);
        }
        zoomWatcher = null;
        if (gray == null || width <= 0 || height <= 0 || gray.length < width * height)
            return new Result(false, 0f, 0f, "no_luma");
        latestBase = gray;
        if (reference == null || this.width != width || this.height != height) {
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
        boolean significant = local >= profile.localFraction || global >= profile.globalFraction;
        if (!significant || now < since) { consecutive = 0; since = -1L; }
        else { if (since < 0L) since = now; consecutive++; }
        boolean cut = significant && consecutive >= profile.confirmations && now - since >= profile.confirmationNanos;
        Result result = new Result(cut, local, global,
                local >= profile.localFraction ? "static_alpr_region_changed" : "static_global_changed");
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
