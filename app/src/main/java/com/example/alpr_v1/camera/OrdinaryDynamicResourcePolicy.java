package com.example.alpr_v1.camera;

import android.util.Size;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Limits live ordinary analysis; never changes a stored photographic/research selection. */
public final class OrdinaryDynamicResourcePolicy {
    public static final long MIB = 1024L * 1024L;
    public static final long PREVIEW_PIXELS = 921_600L;
    public final long analysisPixels;
    public final long bitmapBytes;

    public OrdinaryDynamicResourcePolicy(long totalRam, boolean lowRam, long heapMaximum) {
        analysisPixels = lowRam || totalRam <= 0 || totalRam <= 3L * 1024 * MIB
                || heapMaximum < 256L * MIB ? 921_600L
                : totalRam <= 6L * 1024 * MIB ? 2_073_600L : 3_686_400L;
        bitmapBytes = Math.max(0L, Math.min(96L * MIB, heapMaximum / 4));
    }

    public static long pixels(Size size) {
        return size == null ? 0 : (long) size.getWidth() * size.getHeight();
    }

    /** Orientation independent; closest aspect first, then highest area within the cap. */
    public static List<Size> bounded(List<Size> supported, Size desired, long maximumPixels) {
        List<Size> result = new ArrayList<>();
        for (Size size : supported) if (size != null && size.getWidth() > 0 && size.getHeight() > 0
                && pixels(size) <= maximumPixels) result.add(size);
        double ratio = aspect(desired);
        result.sort(Comparator.comparingDouble((Size size) -> Math.abs(aspect(size) - ratio))
                .thenComparing(Comparator.comparingLong(OrdinaryDynamicResourcePolicy::pixels).reversed()));
        return result;
    }

    public Size analysisSize(List<Size> supported, Size desired) {
        List<Size> sizes = bounded(supported, desired, analysisPixels);
        if (sizes.isEmpty()) throw new IllegalStateException("Brak rozdzielczości kamery w limicie pamięci RAM");
        return sizes.get(0);
    }

    private static double aspect(Size size) {
        if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) return 4.0 / 3;
        return (double) Math.max(size.getWidth(), size.getHeight()) / Math.min(size.getWidth(), size.getHeight());
    }
}
