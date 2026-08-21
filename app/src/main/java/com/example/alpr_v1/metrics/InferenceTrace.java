package com.example.alpr_v1.metrics;

import android.os.SystemClock;
import android.os.Debug;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InferenceTrace {
    private final long frameId;
    private final long timestampMillis;
    private final Map<String, Long> stageStarts = new LinkedHashMap<>();
    private final Map<String, Long> durationsNanos = new LinkedHashMap<>();
    private final Map<String, Double> confidences = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private String status = "started";
    private String recognizedText = "";
    private final long pssStartKb;
    private final long nativeHeapStartBytes;
    private long pssEndKb;
    private long nativeHeapEndBytes;
    private final boolean memorySampled;

    public InferenceTrace(long frameId) {
        this.frameId = frameId;
        this.timestampMillis = System.currentTimeMillis();
        this.memorySampled = frameId % 30 == 1;
        this.pssStartKb = memorySampled ? Debug.getPss() : -1;
        this.nativeHeapStartBytes = memorySampled ? Debug.getNativeHeapAllocatedSize() : -1;
        this.pssEndKb = -1;
        this.nativeHeapEndBytes = -1;
    }

    public void start(String stage) {
        stageStarts.put(stage, SystemClock.elapsedRealtimeNanos());
    }

    public void stop(String stage) {
        Long started = stageStarts.remove(stage);
        if (started != null) {
            durationsNanos.put(stage, Math.max(0L, SystemClock.elapsedRealtimeNanos() - started));
        }
    }

    public void putDurationNanos(String stage, long durationNanos) {
        durationsNanos.put(stage, Math.max(0L, durationNanos));
    }

    public void putConfidence(String name, double value) {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            confidences.put(name, value);
        }
    }

    public void putCount(String name, long value) {
        counters.put(name, Math.max(0L, value));
    }

    public void finish(String status, String recognizedText) {
        this.status = status == null ? "unknown" : status;
        this.recognizedText = recognizedText == null ? "" : recognizedText;
    }

    public void captureMemoryAfterMeasurement() {
        if (!memorySampled) return;
        this.pssEndKb = Debug.getPss();
        this.nativeHeapEndBytes = Debug.getNativeHeapAllocatedSize();
    }

    public long frameId() { return frameId; }
    public long timestampMillis() { return timestampMillis; }
    public String status() { return status; }
    public String recognizedText() { return recognizedText; }
    public Map<String, Long> durationsNanos() { return Collections.unmodifiableMap(durationsNanos); }
    public long durationNanos(String stage) { return durationsNanos.getOrDefault(stage, 0L); }
    public long elapsedSinceStageStart(String stage) {
        Long started = stageStarts.get(stage);
        return started == null ? 0L : Math.max(0L, SystemClock.elapsedRealtimeNanos() - started);
    }
    public Map<String, Double> confidences() { return Collections.unmodifiableMap(confidences); }
    public Map<String, Long> counters() { return Collections.unmodifiableMap(counters); }
    public long pssStartKb() { return pssStartKb; }
    public long pssEndKb() { return pssEndKb; }
    public long nativeHeapStartBytes() { return nativeHeapStartBytes; }
    public long nativeHeapEndBytes() { return nativeHeapEndBytes; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("frame_id", frameId);
        json.put("timestamp_ms", timestampMillis);
        json.put("status", status);
        json.put("text", recognizedText);
        JSONObject stages = new JSONObject();
        for (Map.Entry<String, Long> entry : durationsNanos.entrySet()) {
            stages.put(entry.getKey(), entry.getValue() / 1_000_000.0);
        }
        json.put("stage_ms", stages);
        json.put("confidence", new JSONObject(confidences));
        json.put("counters", new JSONObject(counters));
        JSONObject memory = new JSONObject();
        memory.put("sampled", memorySampled);
        memory.put("pss_start_kb", pssStartKb);
        memory.put("pss_end_kb", pssEndKb);
        memory.put("pss_delta_kb", pssEndKb - pssStartKb);
        memory.put("native_heap_start_bytes", nativeHeapStartBytes);
        memory.put("native_heap_end_bytes", nativeHeapEndBytes);
        memory.put("native_heap_delta_bytes", nativeHeapEndBytes - nativeHeapStartBytes);
        json.put("memory", memory);
        return json;
    }
}
