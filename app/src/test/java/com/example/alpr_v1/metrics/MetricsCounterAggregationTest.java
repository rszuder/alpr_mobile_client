package com.example.alpr_v1.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MetricsCounterAggregationTest {
    @Test
    public void summarySumsEventDeltasButNotGaugesTimestampsOrIdentifiers() {
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Long> trace1 = counters(2L, 8L, 1_000L, 77L);
        Map<String, Long> trace2 = counters(0L, 7L, 2_000L, 77L);
        Map<String, Long> trace3 = counters(1L, 6L, 3_000L, 88L);

        MetricsCollector.aggregateCounterTotals(totals, trace1);
        MetricsCollector.aggregateCounterTotals(totals, trace2);
        MetricsCollector.aggregateCounterTotals(totals, trace3);

        assertEquals(Long.valueOf(3L), totals.get("vehicle_entities_created"));
        assertFalse(totals.containsKey("vehicle_tracks_active"));
        assertFalse(totals.containsKey("source_timestamp_nanos"));
        assertFalse(totals.containsKey("vehicle_roi_entity_id"));
    }

    private static Map<String, Long> counters(
            long created,
            long active,
            long sourceTimestamp,
            long entityId
    ) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("vehicle_entities_created", created);
        result.put("vehicle_tracks_active", active);
        result.put("source_timestamp_nanos", sourceTimestamp);
        result.put("vehicle_roi_entity_id", entityId);
        return result;
    }
}
