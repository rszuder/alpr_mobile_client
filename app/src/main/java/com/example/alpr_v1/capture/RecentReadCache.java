package com.example.alpr_v1.capture;

import com.example.alpr_v1.pipeline.PlateObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Main-thread, bounded RAM buffer. Never writes files or changes the gallery. */
public final class RecentReadCache {
    private final int capacity;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    public RecentReadCache(int capacity) { this.capacity = Math.max(1, capacity); }

    public void remember(PlateObservation observation, ObservationTelemetry telemetry, String source) {
        if (observation == null || !observation.hasFreshMzRead()) return;
        String key = new RecognitionHistoryObservation(observation, source, telemetry).key();
        Entry previous = entries.get(key);
        if (previous != null && !(previous.observation.entityId == 0 && observation.entityId > 0)) return;
        PlateObservation copy = observation.copyForGallery();
        if (copy == null) return;
        if (previous != null) previous.close();
        entries.put(key, new Entry(copy, telemetry, source));
        while (entries.size() > capacity) {
            String oldest = entries.keySet().iterator().next();
            entries.remove(oldest).close();
        }
    }

    /** Borrowed entries: callers must consume synchronously, before the next mutation. */
    public List<Entry> entries() { return new ArrayList<>(entries.values()); }

    public void clear() {
        for (Entry entry : entries.values()) entry.close();
        entries.clear();
    }

    public static final class Entry {
        public final PlateObservation observation;
        public final ObservationTelemetry telemetry;
        public final String source;
        private Entry(PlateObservation observation, ObservationTelemetry telemetry, String source) {
            this.observation = observation; this.telemetry = telemetry; this.source = source;
        }
        private void close() { observation.previewBitmap.recycle(); }
    }
}
