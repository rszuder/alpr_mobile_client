package com.example.alpr_v1.metrics;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MetricsCollector {
    public static final String REPORT_SCHEMA = "alpr.mobile_benchmark_report.v1";
    private static final int MAX_TRACES = 5_000;
    private long sessionStartedMillis = System.currentTimeMillis();
    private long sessionStartedNanos = System.nanoTime();

    private long sessionFinishedMillis = -1L;
    private boolean measurementSessionActive;
    private final Deque<InferenceTrace> traces = new ArrayDeque<>();
    private long droppedFrames;
    private String recognitionProfile = "balanced";
    private boolean vehicleCascadeEnabled;

    private String roiBudgetPolicy = "r0_full_frame";

    private boolean experimentModeEnabled;
    private String experimentRoiBudgetPolicy = "r2_two_roi";
    private String captureProfile = "auto";
    private int requestedSourceWidth;
    private int requestedSourceHeight;
    private int actualSourceWidth;
    private int actualSourceHeight;
    private boolean motionSensorAvailable;
    private long firstPreliminaryResultNanos = -1L;
    private long firstConfirmedResultNanos = -1L;
    private String cropSessionId = "";
    private boolean cropCollectionActive;
    private int cropCapacity;
    private final List<JSONObject> capturedCropRecords = new ArrayList<>();

    public synchronized void startMeasurementSession() {
        traces.clear();

        droppedFrames = 0L;

        firstPreliminaryResultNanos = -1L;
        firstConfirmedResultNanos = -1L;

        actualSourceWidth = 0;
        actualSourceHeight = 0;

        sessionStartedMillis =
                System.currentTimeMillis();

        sessionStartedNanos =
                System.nanoTime();

        sessionFinishedMillis = -1L;

        measurementSessionActive = true;
    }


    public synchronized void finishMeasurementSession() {
        if (!measurementSessionActive) {
            return;
        }

        sessionFinishedMillis =
                System.currentTimeMillis();

        measurementSessionActive = false;
    }


    public synchronized boolean isMeasurementSessionActive() {
        return measurementSessionActive;
    }

    public synchronized void add(InferenceTrace trace) {
        if (!measurementSessionActive) {
            return;
        }

        while (traces.size() >= MAX_TRACES) {
            traces.removeFirst();
        }

        traces.addLast(trace);
    }
    public synchronized void frameDropped() {
        if (measurementSessionActive) {
            droppedFrames++;
        }
    }

    public synchronized int size() { return traces.size(); }
    public synchronized void setRecognitionProfile(String profile) {
        recognitionProfile = profile == null ? "balanced" : profile.trim();
    }

    public synchronized void recordRecognitionState(boolean hasResult, boolean hasConfirmedResult) {
        if (!measurementSessionActive) {
            return;
        }
        long now = System.nanoTime();
        if (hasResult && firstPreliminaryResultNanos < 0L) {
            firstPreliminaryResultNanos = Math.max(0L, now - sessionStartedNanos);
        }
        if (hasConfirmedResult && firstConfirmedResultNanos < 0L) {
            firstConfirmedResultNanos = Math.max(0L, now - sessionStartedNanos);
            if (firstPreliminaryResultNanos < 0L) firstPreliminaryResultNanos = firstConfirmedResultNanos;
        }
    }

    public synchronized void setVehicleCascadeEnabled(boolean enabled) {
        vehicleCascadeEnabled = enabled;
    }

    public synchronized void setRoiBudgetPolicy(String policy) {
        roiBudgetPolicy = policy == null || policy.trim().isEmpty()
                ? "r0_full_frame"
                : policy.trim();
    }

    public synchronized void setExperimentConfiguration(
            boolean enabled,
            String roiPolicy
    ) {
        experimentModeEnabled = enabled;
        experimentRoiBudgetPolicy =
                roiPolicy == null || roiPolicy.trim().isEmpty()
                        ? "r2_two_roi"
                        : roiPolicy.trim();
    }

    public synchronized void setCaptureConfiguration(String profile, int width, int height) {
        captureProfile = profile == null ? "auto" : profile;
        requestedSourceWidth = Math.max(0, width);
        requestedSourceHeight = Math.max(0, height);
    }

    public synchronized void observeSourceFrame(int width, int height) {
        actualSourceWidth = Math.max(0, width);
        actualSourceHeight = Math.max(0, height);
    }

    public synchronized void setMotionSensorAvailable(boolean available) {
        motionSensorAvailable = available;
    }

    public synchronized void startCropSession(String sessionId, int capacity) {
        cropSessionId = sessionId == null ? "" : sessionId;
        cropCapacity = Math.max(0, capacity);
        cropCollectionActive = true;
        capturedCropRecords.clear();
    }

    public synchronized void setCropCollectionActive(boolean active) {
        cropCollectionActive = active;
    }

    public synchronized void setCropCapacity(int capacity) {
        cropCapacity = Math.max(0, capacity);
    }

    public synchronized void clearCropSession() {
        cropSessionId = "";
        cropCollectionActive = false;
        capturedCropRecords.clear();
    }

    public synchronized void recordCapturedCrop(CapturedPlateItem item) {
        try {
            JSONObject record = new JSONObject();
            record.put("capture_id", item.captureId);
            record.put("session_id", item.sessionId);
            record.put("captured_at_ms", item.capturedAtMillis);
            record.put("track_id", item.trackId);
            record.put("text", item.text);
            record.put("confirmed", item.confirmed);
            record.put("plate_confidence", item.plateConfidence);
            record.put("recognition_confidence", item.recognitionConfidence);
            record.put("sharpness", item.sharpness);
            record.put("persisted", false);
            record.put("human_verification", verificationJson(item));
            JSONArray characters = new JSONArray();
            for (com.example.alpr_v1.pipeline.PlateCharacter character : item.characters) {
                JSONObject value = new JSONObject();
                value.put("label", character.label);
                value.put("confidence", character.confidence);
                value.put("left", character.left);
                value.put("top", character.top);
                value.put("right", character.right);
                value.put("bottom", character.bottom);
                characters.put(value);
            }
            record.put("characters", characters);
            if (item.timing != null) record.put("timing", item.timing.toJson());
            capturedCropRecords.add(record);
        } catch (JSONException ignored) {
            // Dane pochodzą z typów liczbowych i bezpiecznych ciągów aplikacji.
        }
    }

    public synchronized void markCropPersisted(String captureId, String imageUri, String reportUri) {
        for (JSONObject record : capturedCropRecords) {
            if (!record.optString("capture_id").equals(captureId)) continue;
            try {
                record.put("persisted", true);
                record.put("image_uri", imageUri == null ? "" : imageUri);
                record.put("report_uri", reportUri == null ? "" : reportUri);
            } catch (JSONException ignored) {
                // Rekord pozostanie poprawny bez opcjonalnych URI.
            }
            return;
        }
    }

    public synchronized void markHumanVerification(CapturedPlateItem item) {
        for (JSONObject record : capturedCropRecords) {
            if (!record.optString("capture_id").equals(item.captureId)) continue;
            try {
                record.put("human_verification", verificationJson(item));
            } catch (JSONException ignored) {
                // Rekord pozostanie poprawny bez opcjonalnej oceny człowieka.
            }
            return;
        }
    }

    public synchronized String createJsonReport(
            DeviceProfile device,
            ModelRegistry registry,
            AutoTuneManager autoTuneManager
    ) throws JSONException {
        long finishedMillis =
                sessionFinishedMillis > 0L
                        ? sessionFinishedMillis
                        : System.currentTimeMillis();
        InstalledModel plate = registry.getActive(ModelRole.PLATE);
        InstalledModel character = registry.getActive(ModelRole.CHARACTER);
        InstalledModel vehicle = registry.getActive(ModelRole.VEHICLE);
        InstalledAlprPackage activePackage = registry.getActivePackage();
        InstalledAlprPackage basePackage = registry.getBasePackage();

        JSONObject plateExecution = executionJson(plate, autoTuneManager);
        JSONObject characterExecution = executionJson(character, autoTuneManager);
        JSONObject vehicleExecution = executionJson(vehicle, autoTuneManager);
        String packageId = packageId(basePackage == null ? activePackage : basePackage, plate, character);
        String variantId = combinedVariantId(vehicleExecution, plateExecution, characterExecution);

        JSONObject report = new JSONObject();
        report.put("schema", REPORT_SCHEMA);
        report.put("report_id", safeId("r-" + finishedMillis + "-" + packageId));
        report.put("package_id", packageId);
        if (basePackage != null) {
            report.put("package_version", basePackage.manifest().version());
            report.put("package_created_at", basePackage.manifest().createdAt());
            report.put("package_source_sha256", basePackage.sourceSha256());
            report.put("base_package_fingerprint", basePackage.fingerprint());
        }
        report.put("variant_id", variantId);
        report.put("measured_at", Instant.ofEpochMilli(finishedMillis).toString());
        report.put("app_version", device.appVersion);
        report.put("device", device.toJson());
        report.put("runtime", commonValue(plateExecution, characterExecution, "runtime"));
        report.put("delegate", commonValue(plateExecution, characterExecution, "delegate"));
        report.put("warmup_runs", 0);
        report.put("measured_runs", traces.size());
        report.put("autotune_warmup_runs", AutoTuneManager.warmupRuns());
        report.put("autotune_measured_runs_per_candidate", AutoTuneManager.measuredRunsPerCandidate());
        report.put("session_started_ms", sessionStartedMillis);
        report.put("session_finished_ms", finishedMillis);
        report.put(
                "session_duration_ms",
                Math.max(
                        0L,
                        finishedMillis - sessionStartedMillis
                )
        );

        report.put(
                "measurement_session_active",
                measurementSessionActive
        );
        report.put("dropped_frames", droppedFrames);
        report.put("recognition_profile", recognitionProfile);

        /*
         * Zachowujemy efektywną politykę również na najwyższym poziomie
         * dla zgodności z wcześniejszymi raportami.
         */
        report.put("roi_budget_policy", roiBudgetPolicy);

        JSONObject normalConfiguration = new JSONObject();
        normalConfiguration.put(
                "vehicle_cascade_enabled",
                vehicleCascadeEnabled
        );
        report.put(
                "normal_configuration",
                normalConfiguration
        );

        JSONObject experiment = new JSONObject();
        experiment.put(
                "enabled",
                experimentModeEnabled
        );
        experiment.put(
                "type",
                "roi_budget"
        );
        experiment.put(
                "roi_budget_policy",
                experimentRoiBudgetPolicy
        );
        experiment.put(
                "effective_roi_budget_policy",
                roiBudgetPolicy
        );
        report.put(
                "experiment",
                experiment
        );
        JSONObject capture = new JSONObject();
        capture.put("profile", captureProfile);
        capture.put("requested_width", requestedSourceWidth);
        capture.put("requested_height", requestedSourceHeight);
        capture.put("actual_width", actualSourceWidth);
        capture.put("actual_height", actualSourceHeight);
        capture.put("pixel_format", "YUV_420_888");
        capture.put("gyroscope_available", motionSensorAvailable);
        report.put("capture", capture);

        JSONObject cropSession = new JSONObject();
        cropSession.put("session_id", cropSessionId);
        cropSession.put("active", cropCollectionActive);
        cropSession.put("capacity", cropCapacity);
        cropSession.put("collected_count", capturedCropRecords.size());
        JSONArray cropRecords = new JSONArray();
        for (JSONObject record : capturedCropRecords) {
            cropRecords.put(new JSONObject(record.toString()));
        }
        cropSession.put("records", cropRecords);
        report.put("crop_session", cropSession);

        JSONObject recognitionLatency = new JSONObject();
        recognitionLatency.put("preliminary_available", firstPreliminaryResultNanos >= 0L);
        recognitionLatency.put("confirmed_available", firstConfirmedResultNanos >= 0L);
        if (firstPreliminaryResultNanos >= 0L) {
            recognitionLatency.put(
                    "time_to_first_preliminary_ms",
                    firstPreliminaryResultNanos / 1_000_000.0
            );
            report.put("time_to_first_result_ms", firstPreliminaryResultNanos / 1_000_000.0);
        }
        if (firstConfirmedResultNanos >= 0L) {
            recognitionLatency.put(
                    "time_to_first_confirmed_ms",
                    firstConfirmedResultNanos / 1_000_000.0
            );
        }
        report.put("recognition_latency", recognitionLatency);

        JSONObject execution = new JSONObject();
        if (vehicleExecution != null) execution.put("vehicle", vehicleExecution);
        if (plateExecution != null) execution.put("plate", plateExecution);
        if (characterExecution != null) execution.put("character", characterExecution);
        report.put("execution", execution);

        JSONObject modelIds = new JSONObject();
        if (vehicle != null) modelIds.put("vehicle", vehicle.manifest().modelId());
        if (plate != null) modelIds.put("plate", plate.manifest().modelId());
        if (character != null) modelIds.put("character", character.manifest().modelId());
        report.put("model_ids", modelIds);
        report.put("models", execution);
        report.put("autotune_profiles", autoTuneManager.exportProfiles());
        report.put(
                "runtime_composition",
                runtimeCompositionJson(
                        registry,
                        autoTuneManager,
                        basePackage,
                        vehicle,
                        plate,
                        character
                )
        );

        Map<String, List<Double>> stageValues = new LinkedHashMap<>();
        Map<String, Integer> statuses = new LinkedHashMap<>();
        Map<String, Long> counterTotals = new LinkedHashMap<>();
        JSONArray traceArray = new JSONArray();
        long peakPssKb = -1L;
        int recognizedFrames = 0;
        for (InferenceTrace trace : traces) {
            traceArray.put(trace.toJson());
            statuses.put(trace.status(), statuses.getOrDefault(trace.status(), 0) + 1);
            if ("recognized".equals(trace.status())) recognizedFrames++;
            peakPssKb = Math.max(peakPssKb, Math.max(trace.pssStartKb(), trace.pssEndKb()));
            for (Map.Entry<String, Long> entry : trace.durationsNanos().entrySet()) {
                stageValues.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(entry.getValue() / 1_000_000.0);
            }
            for (Map.Entry<String, Long> entry : trace.counters().entrySet()) {
                counterTotals.put(
                        entry.getKey(),
                        counterTotals.getOrDefault(entry.getKey(), 0L) + entry.getValue()
                );
            }
        }

        JSONObject summary = new JSONObject();
        summary.put("processed_frames", traces.size());
        summary.put("statuses", new JSONObject(statuses));
        summary.put("stages", stageSummaryJson(stageValues));
        summary.put("counters", new JSONObject(counterTotals));
        report.put("summary", summary);
        report.put("latency", latencyJson(stageValues));

        JSONObject memory = new JSONObject();
        if (peakPssKb >= 0L) memory.put("ram_peak_mb", peakPssKb / 1024.0);
        long packageBytes = packageSizeBytes(activePackage, vehicle, plate, character);
        if (packageBytes > 0L) memory.put("package_size_mb", packageBytes / (1024.0 * 1024.0));
        report.put("memory", memory);

        JSONObject quality = qualityJson(capturedCropRecords);
        quality.put("observed_recognition_yield", traces.isEmpty() ? 0.0 : recognizedFrames / (double) traces.size());
        report.put("quality", quality);

        JSONObject errors = new JSONObject();
        errors.put("crash_count", 0);
        errors.put("pipeline_error_count", statuses.getOrDefault("pipeline_error", 0));
        errors.put("runtime_failure_count", statuses.getOrDefault("pipeline_error", 0));
        errors.put("status_counts", new JSONObject(statuses));
        report.put("errors", errors);
        report.put("traces", traceArray);
        return report.toString(2);
    }

    private static JSONObject executionJson(InstalledModel model, AutoTuneManager autoTuneManager)
            throws JSONException {
        if (model == null) return null;
        ModelVariant variant = autoTuneManager.chosenVariant(model);
        ExecutionProfile profile = autoTuneManager.chosenProfile(model);
        ModelInputSpec input = variant.input(model.manifest().input());
        ModelOutputSpec output = variant.output(model.manifest().output());

        JSONObject json = new JSONObject();
        json.put("role", model.manifest().role().wireName());
        json.put("task", model.manifest().task());
        json.put("model_id", model.manifest().modelId());
        json.put("model_name", model.manifest().name());
        json.put("model_version", model.manifest().version());
        json.put("fingerprint", model.fingerprint());
        json.put("variant_id", variant.id());
        json.put("runtime", variant.runtime().wireName());
        json.put("precision", variant.precision());
        json.put("variant_mode", autoTuneManager.isVariantPinned(model) ? "pinned" : "auto");
        json.put("files", new JSONArray(variant.files()));
        json.put("sha256", new JSONObject(variant.sha256()));
        long artifactBytes = 0L;
        for (String file : variant.files()) {
            try {
                artifactBytes += Files.size(model.resolve(file).toPath());
            } catch (IOException ignored) {
                // Rozmiar jest polem diagnostycznym; checksum z manifestu pozostaje źródłem prawdy.
            }
        }
        json.put("artifact_size_bytes", artifactBytes);
        json.put("delegate", profile.gpu ? "gpu" : "cpu");
        json.put("cpu_threads", profile.cpuThreads);
        json.put("input", inputJson(input));
        json.put("output", outputJson(output));
        if (!model.manifest().yoloFamily().isEmpty()) {
            json.put("yolo_family", model.manifest().yoloFamily());
        }
        if (model.manifest().parameterCount() > 0L) {
            json.put("parameter_count", model.manifest().parameterCount());
        }
        if (!model.manifest().exportedAt().isEmpty()) {
            json.put("exported_at", model.manifest().exportedAt());
        }
        return json;
    }

    private static JSONObject inputJson(ModelInputSpec input) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("width", input.width());
        json.put("height", input.height());
        json.put("channels", input.channels());
        json.put("layout", input.layout());
        json.put("color", input.colorSpace());
        json.put("data_type", input.dataType());
        json.put("scale", input.scale());
        json.put("offset", input.offset());
        return json;
    }

    private static JSONObject outputJson(ModelOutputSpec output) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("decoder", output.decoder());
        json.put("output_format", output.outputFormat());
        json.put("box_format", output.boxFormat());
        json.put("nms_required", output.nmsRequired());
        json.put("class_count", output.classCount());
        json.put("keypoint_count", output.keypointCount());
        json.put("confidence_threshold", output.confidenceThreshold());
        json.put("iou_threshold", output.iouThreshold());
        return json;
    }

    private static JSONObject runtimeCompositionJson(
            ModelRegistry registry,
            AutoTuneManager autoTuneManager,
            InstalledAlprPackage basePackage,
            InstalledModel vehicle,
            InstalledModel plate,
            InstalledModel character
    ) throws JSONException {
        JSONObject composition = new JSONObject();
        composition.put("schema", "alpr.runtime_composition.v1");
        composition.put(
                "runtime_set_id",
                safeId("runtime-" + compositionPart(vehicle, autoTuneManager)
                        + "-" + compositionPart(plate, autoTuneManager)
                        + "-" + compositionPart(character, autoTuneManager))
        );
        composition.put("modified", registry.isCompositionModified()
                || autoTuneManager.isVariantPinned(vehicle)
                || autoTuneManager.isVariantPinned(plate)
                || autoTuneManager.isVariantPinned(character));
        if (basePackage != null) {
            composition.put("base_package_id", basePackage.manifest().packageId());
            composition.put("base_package_version", basePackage.manifest().version());
            composition.put("base_package_created_at", basePackage.manifest().createdAt());
            composition.put("base_package_sha256", basePackage.sourceSha256());
        }
        JSONObject models = new JSONObject();
        putCompositionModel(models, "vehicle", ModelRole.VEHICLE, vehicle, registry, autoTuneManager);
        putCompositionModel(models, "plate", ModelRole.PLATE, plate, registry, autoTuneManager);
        putCompositionModel(models, "character", ModelRole.CHARACTER, character, registry, autoTuneManager);
        composition.put("models", models);
        return composition;
    }

    private static String compositionPart(
            InstalledModel model,
            AutoTuneManager autoTuneManager
    ) {
        if (model == null) return "none";
        return model.fingerprint() + "-" + autoTuneManager.chosenVariant(model).id();
    }

    private static void putCompositionModel(
            JSONObject destination,
            String key,
            ModelRole role,
            InstalledModel model,
            ModelRegistry registry,
            AutoTuneManager autoTuneManager
    ) throws JSONException {
        if (model == null) return;
        ModelVariant variant = autoTuneManager.chosenVariant(model);
        JSONObject value = new JSONObject();
        value.put("model_id", model.manifest().modelId());
        value.put("storage_id", model.storageId());
        value.put("fingerprint", model.fingerprint());
        value.put("source", registry.isModelFromBase(role) ? "base_package" : "replacement");
        value.put("variant_mode", autoTuneManager.isVariantPinned(model) ? "pinned" : "auto");
        value.put("variant_id", variant.id());
        value.put("runtime", variant.runtime().wireName());
        value.put("precision", variant.precision());
        destination.put(key, value);
    }

    private static JSONObject stageSummaryJson(Map<String, List<Double>> stageValues) throws JSONException {
        JSONObject stages = new JSONObject();
        for (Map.Entry<String, List<Double>> entry : stageValues.entrySet()) {
            stages.put(entry.getKey(), statisticsJson(Statistics.summarize(entry.getValue())));
        }
        return stages;
    }

    private static JSONObject latencyJson(Map<String, List<Double>> stageValues) throws JSONException {
        JSONObject latency = new JSONObject();
        addLatency(latency, "mt", stageValues.get("plate_inference"));
        addLatency(latency, "mz", stageValues.get("character_inference"));
        addLatency(latency, "pipeline", stageValues.get("total"));
        return latency;
    }

    private static void addLatency(JSONObject target, String name, List<Double> values) throws JSONException {
        if (values == null || values.isEmpty()) return;
        Statistics.Summary stats = Statistics.summarize(values);
        target.put(name, statisticsJson(stats));
        target.put(name + "_ms_p50", stats.median);
        target.put(name + "_ms_p90", stats.p90);
        target.put(name + "_ms_p95", stats.p95);
        target.put("latency_" + name + "_ms_p50", stats.median);
        target.put("latency_" + name + "_ms_p90", stats.p90);
        target.put("latency_" + name + "_ms_p95", stats.p95);
    }

    private static JSONObject statisticsJson(Statistics.Summary stats) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("count", stats.count);
        item.put("mean_ms", stats.mean);
        item.put("median_ms", stats.median);
        item.put("p50_ms", stats.median);
        item.put("p90_ms", stats.p90);
        item.put("p95_ms", stats.p95);
        item.put("p99_ms", stats.p99);
        item.put("min_ms", stats.min);
        item.put("max_ms", stats.max);
        item.put("stddev_ms", stats.standardDeviation);
        return item;
    }

    private static JSONObject verificationJson(CapturedPlateItem item) throws JSONException {
        JSONObject verification = new JSONObject();
        verification.put("status", item.verificationStatus.wireName());
        verification.put("revision", item.verificationRevision);
        verification.put("verified_at_ms", item.verifiedAtMillis);
        verification.put("ground_truth_text", item.groundTruthText);
        verification.put("original_prediction", item.text);
        return verification;
    }

    private static JSONObject qualityJson(List<JSONObject> cropRecords) throws JSONException {
        Map<String, JSONObject> qualityUnits = new LinkedHashMap<>();
        for (JSONObject record : cropRecords) {
            long trackId = record.optLong("track_id", 0L);
            String key = trackId > 0L
                    ? record.optString("session_id", "") + ":track:" + trackId
                    : "capture:" + record.optString("capture_id", "");
            JSONObject previous = qualityUnits.get(key);
            if (previous == null || preferForQuality(record, previous)) {
                qualityUnits.put(key, record);
            }
        }
        int groundTruthSamples = 0;
        int exactMatches = 0;
        int reviewed = 0;
        int acceptedOriginal = 0;
        int rejectedWithoutGroundTruth = 0;
        int notReviewed = 0;
        int totalDistance = 0;
        int totalGroundTruthCharacters = 0;
        double normalizedDistanceSum = 0.0;
        JSONArray samples = new JSONArray();
        for (JSONObject record : qualityUnits.values()) {
            JSONObject verification = record.optJSONObject("human_verification");
            String status = verification == null
                    ? "not_reviewed"
                    : verification.optString("status", "not_reviewed");
            if ("not_reviewed".equals(status)) {
                notReviewed++;
                continue;
            }
            reviewed++;
            if ("accepted".equals(status)) acceptedOriginal++;
            String groundTruth = verification == null
                    ? ""
                    : verification.optString("ground_truth_text", "");
            if (groundTruth.isEmpty()) {
                rejectedWithoutGroundTruth++;
                continue;
            }
            String prediction = normalizeSequence(record.optString("text", ""));
            String normalizedGroundTruth = normalizeSequence(groundTruth);
            int distance = levenshtein(prediction, normalizedGroundTruth);
            boolean exact = prediction.equals(normalizedGroundTruth);
            groundTruthSamples++;
            if (exact) exactMatches++;
            totalDistance += distance;
            totalGroundTruthCharacters += normalizedGroundTruth.length();
            normalizedDistanceSum += distance / (double) Math.max(1, normalizedGroundTruth.length());
            JSONObject sample = new JSONObject();
            sample.put("capture_id", record.optString("capture_id", ""));
            sample.put("track_id", record.optLong("track_id", 0L));
            sample.put("prediction", prediction);
            sample.put("ground_truth", normalizedGroundTruth);
            sample.put("exact_match", exact);
            sample.put("edit_distance", distance);
            sample.put(
                    "normalized_edit_distance",
                    distance / (double) Math.max(1, normalizedGroundTruth.length())
            );
            samples.put(sample);
        }
        JSONObject quality = new JSONObject();
        quality.put("available", groundTruthSamples > 0);
        quality.put("unit", "unique_session_track");
        quality.put("unit_count", qualityUnits.size());
        quality.put("normalization", "uppercase_remove_whitespace");
        quality.put("ground_truth_samples", groundTruthSamples);
        quality.put("reviewed_samples", reviewed);
        quality.put("not_reviewed_samples", notReviewed);
        quality.put("rejected_without_ground_truth", rejectedWithoutGroundTruth);
        quality.put(
                "accepted_original_rate",
                reviewed == 0 ? JSONObject.NULL : acceptedOriginal / (double) reviewed
        );
        if (groundTruthSamples > 0) {
            quality.put("exact_match_count", exactMatches);
            quality.put("exact_match_rate", exactMatches / (double) groundTruthSamples);
            quality.put(
                    "cer",
                    totalDistance / (double) Math.max(1, totalGroundTruthCharacters)
            );
            quality.put(
                    "normalized_edit_distance_mean",
                    normalizedDistanceSum / groundTruthSamples
            );
        } else {
            quality.put(
                    "reason",
                    "Brak rekordów accepted/corrected z ground truth; confidence nie jest jakością"
            );
        }
        quality.put("samples", samples);
        return quality;
    }

    private static boolean preferForQuality(JSONObject candidate, JSONObject current) {
        JSONObject candidateVerification = candidate.optJSONObject("human_verification");
        JSONObject currentVerification = current.optJSONObject("human_verification");
        boolean candidateReviewed = candidateVerification != null
                && !"not_reviewed".equals(candidateVerification.optString("status", "not_reviewed"));
        boolean currentReviewed = currentVerification != null
                && !"not_reviewed".equals(currentVerification.optString("status", "not_reviewed"));
        if (candidateReviewed != currentReviewed) return candidateReviewed;
        long candidateVerified = candidateVerification == null
                ? 0L
                : candidateVerification.optLong("verified_at_ms", 0L);
        long currentVerified = currentVerification == null
                ? 0L
                : currentVerification.optLong("verified_at_ms", 0L);
        if (candidateVerified != currentVerified) return candidateVerified > currentVerified;
        return candidate.optLong("captured_at_ms", 0L) > current.optLong("captured_at_ms", 0L);
    }

    private static String normalizeSequence(String value) {
        return value == null
                ? ""
                : value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) previous[column] = column;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(
                        Math.min(previous[column] + 1, current[column - 1] + 1),
                        substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static String commonValue(JSONObject plate, JSONObject character, String key) {
        String plateValue = plate == null ? "" : plate.optString(key, "");
        String characterValue = character == null ? "" : character.optString(key, "");
        if (plateValue.isEmpty()) return characterValue;
        if (characterValue.isEmpty()) return plateValue;
        return plateValue.equals(characterValue) ? plateValue : "mixed";
    }

    private static String packageId(
            InstalledAlprPackage activePackage,
            InstalledModel plate,
            InstalledModel character
    ) {
        if (activePackage != null) return activePackage.manifest().packageId();
        String plateId = plate == null ? "no-mt" : plate.fingerprint();
        String characterId = character == null ? "no-mz" : character.fingerprint();
        return safeId("unbundled-" + plateId + "-" + characterId);
    }

    private static String combinedVariantId(
            JSONObject vehicle,
            JSONObject plate,
            JSONObject character
    ) {
        String vehicleId = vehicle == null ? "no-mp" : vehicle.optString("variant_id", "no-mp");
        String plateId = plate == null ? "no-mt" : plate.optString("variant_id", "no-mt");
        String characterId = character == null ? "no-mz" : character.optString("variant_id", "no-mz");
        return safeId("mp-" + vehicleId + "-mt-" + plateId + "-mz-" + characterId);
    }

    private static String safeId(String value) {
        String safe = value == null ? "report" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        safe = safe.replaceAll("^[^A-Za-z0-9]+", "");
        if (safe.isEmpty()) safe = "report";
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static long packageSizeBytes(
            InstalledAlprPackage activePackage,
            InstalledModel vehicle,
            InstalledModel plate,
            InstalledModel character
    ) {
        if (activePackage != null && activePackage.sourceSizeBytes() > 0L) {
            return activePackage.sourceSizeBytes()
                    + (activePackage.vehicleModel() == null ? directorySize(vehicle) : 0L);
        }
        return directorySize(vehicle) + directorySize(plate) + directorySize(character);
    }

    private static long directorySize(InstalledModel model) {
        if (model == null || !model.directory().isDirectory()) return 0L;
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(model.directory().toPath())) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException ignored) {
                    return 0L;
                }
            }).sum();
        } catch (IOException ignored) {
            return 0L;
        }
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
        csv.append(",vehicle_confidence,vehicle_roi_area_ratio,plate_confidence,plate_fit,plate_sharpness,characters_min,characters_mean")
                .append(",mz_runs,mz_skipped,invalid_plate_geometry")
                .append(",vehicle_runs,vehicle_skipped,vehicle_unavailable")
                .append(",plate_roi_runs,plate_full_frame_runs,full_frame_fallbacks")
                .append(",source_width,source_height")
                .append(",rapid_motion_frames")
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
            appendConfidence(csv, trace, "vehicle_roi_area_ratio");
            appendConfidence(csv, trace, "plate");
            appendConfidence(csv, trace, "plate_fit");
            appendConfidence(csv, trace, "plate_sharpness");
            appendConfidence(csv, trace, "characters_min");
            appendConfidence(csv, trace, "characters_mean");
            appendCount(csv, trace, "mz_runs");
            appendCount(csv, trace, "mz_skipped");
            appendCount(csv, trace, "invalid_plate_geometry");
            appendCount(csv, trace, "vehicle_runs");
            appendCount(csv, trace, "vehicle_skipped");
            appendCount(csv, trace, "vehicle_unavailable");
            appendCount(csv, trace, "plate_roi_runs");
            appendCount(csv, trace, "plate_full_frame_runs");
            appendCount(csv, trace, "full_frame_fallbacks");
            appendCount(csv, trace, "source_width");
            appendCount(csv, trace, "source_height");
            appendCount(csv, trace, "rapid_motion_frames");
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

    private static void appendCount(StringBuilder csv, InferenceTrace trace, String key) {
        csv.append(',');
        Long value = trace.counters().get(key);
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
