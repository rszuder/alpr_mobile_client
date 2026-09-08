package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;
import com.example.alpr_v1.continuity.ContinuityStamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A separate, immutable pre-zoom plate anchor. Zoom detections cannot overwrite it. */
public final class ZoomPlateReturnGeometry {
    private OverlayItem baseline;
    private ContinuityStamp capturedStamp;
    private long owner;
    private List<OverlayItem> returnSource;
    private int targetIndex;
    private float startZoom;

    public void clear() { baseline = null; capturedStamp = null; returnSource = null; owner = 0L; }

    public void capture(OverlayItem plate, ContinuityStamp stamp, long entityId) {
        clear();
        if (plate == null || plate.kind != OverlayItem.Kind.PLATE || plate.normalizedBounds.isEmpty() || stamp == null) return;
        baseline = copy(plate); capturedStamp = stamp; owner = entityId;
    }

    public void discardForDifferentOwner(long entityId) {
        if (owner > 0L && entityId > 0L && owner != entityId) clear();
    }

    public boolean beginReturn(List<OverlayItem> baseItems, float zoom, ContinuityStamp stamp,
                               long currentTrack, long currentOwner) {
        returnSource = null;
        if (!sameScene(stamp) || zoom <= 1.01f || !Float.isFinite(zoom)
                || owner > 0L && currentOwner > 0L && owner != currentOwner) return false;
        if (currentTrack != baseline.trackId && !(owner > 0L && owner == currentOwner)) return false;
        int index = -1;
        for (int i = 0; i < baseItems.size(); i++) {
            OverlayItem item = baseItems.get(i);
            if (item.kind != OverlayItem.Kind.PLATE || !samePosition(item.normalizedBounds, baseline.normalizedBounds)) continue;
            if (item.trackId != currentTrack && item.trackId != baseline.trackId) continue;
            if (index >= 0) return false;
            index = i;
        }
        List<OverlayItem> source = new ArrayList<>();
        for (OverlayItem item : baseItems) source.add(copy(item));
        if (index < 0) {
            // Restore a temporarily missing layer only while the original technical target is still selected.
            if (currentTrack != baseline.trackId) return false;
            for (OverlayItem item : baseItems) if (item.kind == OverlayItem.Kind.PLATE
                    && item.trackId == currentTrack) return false; // A moved target is not missing.
            index = source.size(); source.add(copy(baseline));
        }
        targetIndex = index; startZoom = zoom;
        returnSource = Collections.unmodifiableList(source);
        return true;
    }

    /** Base-space geometry; optical scaling is applied by the caller exactly once. */
    public List<OverlayItem> atZoom(float zoom, ContinuityStamp stamp) {
        if (returnSource == null || !sameScene(stamp)) return null;
        float progress = Math.max(0f, Math.min(1f, (startZoom - zoom) / (startZoom - 1f)));
        progress = progress * progress * (3f - 2f * progress);
        List<OverlayItem> result = new ArrayList<>(returnSource);
        OverlayItem current = returnSource.get(targetIndex);
        RectF a = current.normalizedBounds, b = baseline.normalizedBounds;
        RectF bounds = new RectF(mix(a.left,b.left,progress), mix(a.top,b.top,progress),
                mix(a.right,b.right,progress), mix(a.bottom,b.bottom,progress));
        List<PointF> corners;
        if (progress >= 1f) corners = baseline.normalizedKeypoints;
        else if (progress <= 0f) corners = current.normalizedKeypoints;
        else {
            List<PointF> first = corners(current), last = corners(baseline);
            corners = new ArrayList<>();
            for (int i = 0; i < 4; i++) corners.add(new PointF(mix(first.get(i).x,last.get(i).x,progress),
                    mix(first.get(i).y,last.get(i).y,progress)));
        }
        result.set(targetIndex, new OverlayItem(OverlayItem.Kind.PLATE, bounds, corners,
                current.label, current.trackId, true));
        return Collections.unmodifiableList(result);
    }

    private boolean sameScene(ContinuityStamp stamp) {
        return baseline != null && stamp != null && stamp.sceneGeneration == capturedStamp.sceneGeneration
                && stamp.visualEpoch == capturedStamp.visualEpoch;
    }

    private static boolean samePosition(RectF a, RectF b) {
        return Math.abs(a.centerX()-b.centerX()) <= b.width()*.25f
                && Math.abs(a.centerY()-b.centerY()) <= b.height()*.8f
                && a.right > b.left && a.left < b.right && a.bottom > b.top && a.top < b.bottom;
    }

    private static float mix(float a, float b, float amount) { return a + (b-a)*amount; }
    private static List<PointF> corners(OverlayItem item) {
        if (item.normalizedKeypoints.size() == 4) return item.normalizedKeypoints;
        RectF b = item.normalizedBounds;
        return java.util.Arrays.asList(new PointF(b.left,b.top),new PointF(b.right,b.top),
                new PointF(b.right,b.bottom),new PointF(b.left,b.bottom));
    }
    private static OverlayItem copy(OverlayItem item) {
        List<PointF> points = new ArrayList<>();
        for (PointF p : item.normalizedKeypoints) points.add(new PointF(p.x,p.y));
        return new OverlayItem(item.kind,item.normalizedBounds,points,item.label,item.trackId,item.carriedPrediction);
    }
}
