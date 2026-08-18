package com.example.alpr_v1.metrics;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MetricsCollector {
    private static final int MAX_TRACES = 5_000;
    private final long sessionStartedMillis = System.currentTimeMillis();
    private final Deque<InferenceTrace> traces = new ArrayDeque<>();
    private long droppedFrames;

    public synchronized void add(InferenceTrace trace) {
        while (traces.size() >= MAX_TRACES) traces.removeFirst();
        traces.addLast(trace);
    }

    public synchronized void frameDropped() {
        droppedFrames++;
    }

    public synchronized int size() {
        return traces.size();
    }

    public synchronized String createJsonReport(
            DeviceProfile device,
            ModelRegistry registry,
            JSONObject autotuneProfiles
    ) throws JSONException {
        JSONObject report = new JSONObject();
        report.put("schema", "alpr.mobile.report.v1");
        report.put("session_started_ms", sessionStartedMillis);
        report.put("session_finished_ms", System.currentTimeMillis());
        report.put("device", device.toJson());
        report.put("dropped_frames", droppedFrames);

        JSONObject models = new JSONObject();
        for (ModelRole role : ModelRole.values()) {
            InstalledModel model = registry.getActive(role);
            if (model == null) continue;
            JSONObject item = new JSONObject();
            item.put("model_id", model.manifest().modelId());
            item.put("name", model.manifest().name());
            item.put("version", model.manifest().version());
            item.put("fingerprint", model.fingerprint());
            models.put(role.wireName(), item);
        }
        report.put("models", models);
        report.put("autotune_profiles", autotuneProfiles);

        Map<String, List<Double>> stageValues = new LinkedHashMap<>();
        Map<String, Integer> statuses = new LinkedHashMap<>();
        JSONArray traceArray = new JSONArray();
        for (InferenceTrace trace : traces) {
            traceArray.put(trace.toJson());
            statuses.put(trace.status(), statuses.getOrDefault(trace.status(), 0) + 1);
            for (Map.Entry<String, Long> entry : trace.durationsNanos().entrySet()) {
                stageValues.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(entry.getValue() / 1_000_000.0);
            }
        }

        JSONObject summary = new JSONObject();
        summary.put("processed_frames", traces.size());
        summary.put("statuses", new JSONObject(statuses));
        JSONObject stages = new JSONObject();
        for (Map.Entry<String, List<Double>> entry : stageValues.entrySet()) {
            Statistics.Summary stats = Statistics.summarize(entry.getValue());
            JSONObject item = new JSONObject();
            item.put("count", stats.count);
            item.put("mean_ms", stats.mean);
            item.put("median_ms", stats.median);
            item.put("p90_ms", stats.p90);
            item.put("p95_ms", stats.p95);
            item.put("p99_ms", stats.p99);
            item.put("min_ms", stats.min);
            item.put("max_ms", stats.max);
            item.put("stddev_ms", stats.standardDeviation);
            stages.put(entry.getKey(), item);
        }
        summary.put("stages", stages);
        report.put("summary", summary);
        report.put("traces", traceArray);
        return report.toString(2);
    }

    public synchronized String createCsvReport() {
        String[] stages = new String[]{
                "total", "camera_conversion", "vehicle_preprocess", "vehicle_inference", "vehicle_postprocess",
                "plate_preprocess", "plate_inference", "plate_postprocess",
                "rectification", "character_preprocess", "character_inference", "character_postprocess"
        };
        StringBuilder csv = new StringBuilder();
        csv.append("frame_id,timestamp_ms,status,text");
        for (String stage : stages) csv.append(',').append(stage).append("_ms");
        csv.append(",vehicle_confidence,plate_confidence,characters_min,characters_mean")
                .append(",pss_start_kb,pss_end_kb,pss_delta_kb")
                .append(",native_heap_start_bytes,native_heap_end_bytes,native_heap_delta_bytes\n");
        for (InferenceTrace trace : traces) {
            csv.append(trace.frameId()).append(',')
                    .append(trace.timestampMillis()).append(',')
                    .append(csvCell(trace.status())).append(',')
                    .append(csvCell(trace.recognizedText()));
            for (String stage : stages) {
                Long nanos = trace.durationsNanos().get(stage);
                csv.append(',');
                if (nanos != null) csv.append(nanos / 1_000_000.0);
            }
            appendConfidence(csv, trace, "vehicle");
            appendConfidence(csv, trace, "plate");
            appendConfidence(csv, trace, "characters_min");
            appendConfidence(csv, trace, "characters_mean");
            csv.append(',').append(trace.pssStartKb())
                    .append(',').append(trace.pssEndKb())
                    .append(',').append(trace.pssEndKb() - trace.pssStartKb())
                    .append(',').append(trace.nativeHeapStartBytes())
                    .append(',').append(trace.nativeHeapEndBytes())
                    .append(',').append(trace.nativeHeapEndBytes() - trace.nativeHeapStartBytes());
            csv.append('\n');
        }
        return csv.toString();
    }

    private static void appendConfidence(StringBuilder csv, InferenceTrace trace, String key) {
        csv.append(',');
        Double value = trace.confidences().get(key);
        if (value != null) csv.append(value);
    }

    private static String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
