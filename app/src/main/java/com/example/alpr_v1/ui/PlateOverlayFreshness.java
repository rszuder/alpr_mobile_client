package com.example.alpr_v1.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Krótki TTL fizycznej geometrii PLATE, niezależny od życia sesji celu. */
public final class PlateOverlayFreshness {
    public static final long MAXIMUM_AGE_NANOS = 375_000_000L;

    private final Map<Long, Long> freshAtNanos = new HashMap<>();

    public synchronized void recordFresh(
            List<OverlayItem> items,
            long nowNanos
    ) {
        if (items == null) return;
        long safeNow = Math.max(0L, nowNanos);
        for (OverlayItem item : items) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && item.trackId > 0L
                    && !item.carriedPrediction) {
                freshAtNanos.put(item.trackId, safeNow);
            }
        }
    }

    public synchronized List<OverlayItem> retainDisplayable(
            List<OverlayItem> items,
            long nowNanos
    ) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        long safeNow = Math.max(0L, nowNanos);
        List<OverlayItem> retained = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item == null) continue;
            if (item.kind != OverlayItem.Kind.PLATE
                    || isFresh(item.trackId, safeNow)) {
                retained.add(item);
            }
        }
        return Collections.unmodifiableList(retained);
    }

    public synchronized void reset() {
        freshAtNanos.clear();
    }

    private boolean isFresh(long trackId, long nowNanos) {
        Long freshAt = freshAtNanos.get(trackId);
        return freshAt != null
                && nowNanos >= freshAt
                && nowNanos - freshAt <= MAXIMUM_AGE_NANOS;
    }
}
