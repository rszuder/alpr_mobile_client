package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.ArrayList;
import java.util.List;

/** Anchored corner evidence inside measured ALPR boxes; never updates object identity or geometry. */
public final class RegionFeatureChangeDetector {
    public static final class Result {
        public final int points;
        public final float lostFraction, movedFraction;
        public final boolean significant;
        Result(int points, float lost, float moved, boolean significant) {
            this.points = points; lostFraction = lost; movedFraction = moved; this.significant = significant;
        }
    }
    /** Appearance support at the selected anchor points; does not estimate motion. */
    public static final class AppearanceResult {
        public final int points, supportedPoints;
        public final float unsupportedFraction, supportedFraction;
        public final boolean novel, persistent;
        AppearanceResult(int points, int supported) {
            this.points = points; supportedPoints = supported;
            supportedFraction = points == 0 ? 0f : supported / (float) points;
            unsupportedFraction = points == 0 ? 0f : (points - supported) / (float) points;
            novel = points >= 4 && points - supported >= 3 && unsupportedFraction >= .65f;
            persistent = points >= 4 && supported >= 3 && supportedFraction >= .65f;
        }
    }
    private byte[] reference;
    private int width, height;
    private final List<SparsePyramidalFlow.Point> points = new ArrayList<>();
    private final List<int[]> groups = new ArrayList<>();

    public void reset() { reference = null; points.clear(); groups.clear(); }

    public void anchor(byte[] gray, int width, int height, List<NormalizedBounds> regions) {
        reset();
        if (gray == null || width < 32 || height < 32 || gray.length < (long) width * height) return;
        this.reference = gray; this.width = width; this.height = height;
        for (NormalizedBounds b : regions) {
            if (groups.size() >= 8) break;
            if (b == null || !b.valid()) continue;
            int start = points.size();
            int left = Math.max(13, (int) Math.ceil(b.left * width));
            int top = Math.max(13, (int) Math.ceil(b.top * height));
            int right = Math.min(width - 14, (int) (b.right * width));
            int bottom = Math.min(height - 14, (int) (b.bottom * height));
            if (left >= right || top >= bottom) continue;
            for (int cy = 0; cy < 4; cy++) for (int cx = 0; cx < 4; cx++) {
                float best = 80f; int px = -1, py = -1;
                int x0 = left + (right - left) * cx / 4, x1 = left + (right - left) * (cx + 1) / 4;
                int y0 = top + (bottom - top) * cy / 4, y1 = top + (bottom - top) * (cy + 1) / 4;
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    float score = cornerScore(gray, x, y);
                    if (score > best && separated(start, x, y)) { best = score; px = x; py = y; }
                }
                if (px >= 0) points.add(new SparsePyramidalFlow.Point(px, py));
            }
            if (points.size() > start) groups.add(new int[]{start, points.size()});
        }
        // Exclude corners that the existing pyramidal tracker cannot track even in the anchor itself.
        SparsePyramidalFlow.Result self = SparsePyramidalFlow.track(reference, reference, width, height, points);
        boolean[] reliable = new boolean[points.size()];
        for (SparsePyramidalFlow.Match match : self.matches) reliable[match.sourceIndex] = true;
        List<SparsePyramidalFlow.Point> selected = new ArrayList<>();
        List<int[]> selectedGroups = new ArrayList<>();
        for (int[] g : groups) {
            int begin = selected.size();
            for (int i = g[0]; i < g[1]; i++) if (reliable[i]) selected.add(points.get(i));
            if (selected.size() - begin >= 4) selectedGroups.add(new int[]{begin, selected.size()});
            else selected.subList(begin, selected.size()).clear();
        }
        points.clear(); points.addAll(selected); groups.clear(); groups.addAll(selectedGroups);
    }

    public Result observe(byte[] gray, float exposure) {
        if (reference == null || points.isEmpty()) return new Result(0, 0f, 0f, false);
        byte[] compensated = gray;
        if (Math.abs(exposure) >= .5f) {
            compensated = new byte[width * height];
            for (int i = 0; i < compensated.length; i++)
                compensated[i] = (byte) Math.max(0, Math.min(255, Math.round((gray[i] & 255) - exposure)));
        }
        SparsePyramidalFlow.Result flow = SparsePyramidalFlow.track(reference, compensated, width, height, points);
        boolean[] found = new boolean[points.size()], moved = new boolean[points.size()];
        float movement = Math.max(2.5f, Math.min(width, height) * .012f);
        for (SparsePyramidalFlow.Match match : flow.matches) {
            found[match.sourceIndex] = true;
            moved[match.sourceIndex] = Math.hypot(match.target.x - match.source.x,
                    match.target.y - match.source.y) >= movement;
        }
        float lostMaximum = 0f, movedMaximum = 0f;
        boolean significant = false;
        for (int[] g : groups) {
            int lost = 0, shifted = 0, count = g[1] - g[0];
            for (int i = g[0]; i < g[1]; i++) { if (!found[i]) lost++; if (moved[i]) shifted++; }
            float missing = lost / (float) count, displacement = shifted / (float) count;
            lostMaximum = Math.max(lostMaximum, missing); movedMaximum = Math.max(movedMaximum, displacement);
            significant |= lost >= 3 && missing >= .65f || shifted >= 3 && displacement >= .60f;
        }
        return new Result(points.size(), lostMaximum, movedMaximum, significant);
    }

    /**
     * Matches current-anchor patches to an older image. Zero-mean normalized correlation
     * tolerates exposure/contrast changes that reverse optical flow alone would mistake
     * for new structure. The caller owns the anchor image for this comparison.
     */
    public AppearanceResult compareAppearance(byte[] other) {
        if (reference == null || other == null || other.length < (long) width * height)
            return new AppearanceResult(0, 0);
        int supported = 0;
        for (SparsePyramidalFlow.Point point : points)
            if (patchSupported(other, Math.round(point.x), Math.round(point.y))) supported++;
        return new AppearanceResult(points.size(), supported);
    }

    private boolean patchSupported(byte[] other, int x, int y) {
        // Anchor selection leaves a 13 px border; radius 4 plus search radius 2 is safe.
        double sum = 0, squares = 0;
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int value = reference[(y + dy) * width + x + dx] & 255;
            sum += value; squares += value * value;
        }
        double variance = squares - sum * sum / 81.0;
        if (variance < 81.0) return false; // Standard deviation below 1 in 8-bit luma.
        if (correlationSupported(other, x, y, 0, 0, sum, variance)) return true;
        for (int sy = -2; sy <= 2; sy++) for (int sx = -2; sx <= 2; sx++) {
            if (sx == 0 && sy == 0) continue;
            if (correlationSupported(other, x, y, sx, sy, sum, variance)) return true;
        }
        return false;
    }

    private boolean correlationSupported(byte[] other, int x, int y, int sx, int sy,
                                         double sum, double variance) {
        double otherSum = 0, otherSquares = 0, product = 0;
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int a = reference[(y + dy) * width + x + dx] & 255;
            int b = other[(y + dy + sy) * width + x + dx + sx] & 255;
            otherSum += b; otherSquares += b * b; product += a * b;
        }
        double otherVariance = otherSquares - otherSum * otherSum / 81.0;
        return otherVariance >= 81.0
                && (product - sum * otherSum / 81.0) / Math.sqrt(variance * otherVariance) >= .85;
    }

    private boolean separated(int start, int x, int y) {
        for (int i = start; i < points.size(); i++) {
            SparsePyramidalFlow.Point p = points.get(i);
            if (Math.hypot(p.x - x, p.y - y) < 4f) return false;
        }
        return true;
    }

    private float cornerScore(byte[] gray, int x, int y) {
        float xx = 0, xy = 0, yy = 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int i = (y + dy) * width + x + dx;
            float gx = ((gray[i + 1] & 255) - (gray[i - 1] & 255)) * .5f;
            float gy = ((gray[i + width] & 255) - (gray[i - width] & 255)) * .5f;
            xx += gx * gx; xy += gx * gy; yy += gy * gy;
        }
        return .5f * (xx + yy - (float) Math.sqrt((xx - yy) * (xx - yy) + 4f * xy * xy));
    }
}
