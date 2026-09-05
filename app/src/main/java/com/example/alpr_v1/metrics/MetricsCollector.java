package com.example.alpr_v1.metrics;

import android.os.SystemClock;

import com.example.alpr_v1.BuildConfig;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.acquisition.ScanAcquisitionSnapshot;
import com.example.alpr_v1.acquisition.AcquisitionRecord;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRefResolver;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.experiment.ExperimentSession;
import com.example.alpr_v1.experiment.ResearchExecutionConfig;
import com.example.alpr_v1.experiment.ThermalMonitor;

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
    private static final class FrameFlowBucket {
        final long elapsedMs;
        long framesReceived;
        long framesProcessed;
        long framesSkippedGate;
        long framesSkippedCameraTransform;
        long framesSkippedHardSceneReset;
        long framesSkippedContinuityHold;
        long framesSkippedContinuityReacquire;
        long estimatedUpstreamGaps;

        FrameFlowBucket(long elapsedMs) {
            this.elapsedMs = elapsedMs;
        }
    }
    public static final class LiveSnapshot {

        public final int sourceWidth;

        public final int sourceHeight;

        public final long droppedFrames;

        public final double vehicleInferenceMs;

        public final double plateInferenceMs;

        public final double characterInferenceMs;

        public final double pipelineMs;

        public final double inferenceSumMs;

        public final double auxiliarySumMs;



        private LiveSnapshot(
                int sourceWidth,
                int sourceHeight,
                long droppedFrames,
                double vehicleInferenceMs,
                double plateInferenceMs,
                double characterInferenceMs,
                double pipelineMs,
                double inferenceSumMs,
                double auxiliarySumMs
        ) {
            this.sourceWidth =
                    sourceWidth;

            this.sourceHeight =
                    sourceHeight;

            this.droppedFrames =
                    droppedFrames;

            this.vehicleInferenceMs =
                    vehicleInferenceMs;

            this.plateInferenceMs =
                    plateInferenceMs;

            this.characterInferenceMs =
                    characterInferenceMs;

            this.pipelineMs =
                    pipelineMs;
            this.inferenceSumMs =
                    inferenceSumMs;

            this.auxiliarySumMs =
                    auxiliarySumMs;
        }
    }
    private boolean captureHighResolutionRequested;
    public static final String REPORT_SCHEMA = "alpr.mobile_benchmark_report.v1";
    private static final int MAX_TRACES = 5_000;
    private long sessionStartedMillis = System.currentTimeMillis();
    private boolean crashMeasurementAvailable;
    private int recoveredCrashCount;
    private String recoveredCrashSessionId = "";
    private long recoveredCrashSessionStartedAtMillis;
    private long sessionStartedNanos = System.nanoTime();
    private long sessionStartedElapsedNanos = SystemClock.elapsedRealtimeNanos();

    private long sessionFinishedMillis = -1L;
    private boolean measurementSessionActive;
    private final Deque<InferenceTrace> traces = new ArrayDeque<>();
    private long droppedFrames;
    private long traceTotalSeen;
    private long traceRecordsEvicted;
    private long framesReceived;
    private long framesProcessed;
    private long framesSkippedGate;
    private long framesSkippedCameraTransform;
    private long framesSkippedHardSceneReset;
    private long framesSkippedContinuityHold;
    private long framesSkippedContinuityReacquire;
    private long finalResultsDroppedAfterReturn;
    private long finalResultsDroppedBeforeUi;
    private long finalResultsDroppedBeforeCrop;
    private long finalResultDispatchAccepted;
    private long secondaryScenePreflightDetections;
    private long secondaryScenePreflightHolds;
    private long secondaryScenePreflightReacquires;
    private long secondaryScenePreflightHardResets;
    private long secondaryScenePreflightSkippedInference;
    private long previewCoordinatorDecisions;
    private long legacyPreviewFallbacks;
    private String previewDecisionAuthority = "unavailable";
    private long estimatedUpstreamGaps;
    private long lastSourceTimestampNanos = -1L;
    private double expectedSourceIntervalNanos = Double.NaN;
    private final Map<Long, FrameFlowBucket> frameFlowBuckets = new LinkedHashMap<>();
    private final List<JSONObject> thermalSamples = new ArrayList<>();
    private final List<JSONObject> eventRecords = new ArrayList<>();
    private ScanAcquisitionSnapshot scanAcquisitionSnapshot;
    private long eventSequence;
    private String experimentSessionId = "";
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
    private String sceneHandlingMode = "dynamic_continuity";
    private String sceneContinuityProfile = "initial_v2";
    private String cameraTimestampSource = "UNKNOWN";
    private String frozenRecognitionProfile = "balanced";
    private boolean frozenVehicleCascadeEnabled;
    private String frozenRoiBudgetPolicy = "r0_full_frame";
    private boolean frozenExperimentModeEnabled;
    private String frozenExperimentRoiBudgetPolicy = "r2_two_roi";
    private String frozenCaptureProfile = "auto";
    private int frozenRequestedSourceWidth;
    private int frozenRequestedSourceHeight;
    private boolean frozenCaptureHighResolutionRequested;
    private boolean frozenMotionSensorAvailable;
    private String frozenSceneHandlingMode = "dynamic_continuity";
    private String frozenSceneContinuityProfile = "initial_v2";
    private String frozenCameraTimestampSource = "UNKNOWN";
    private long firstPreliminaryResultNanos = -1L;
    private long firstConfirmedResultNanos = -1L;
    private String cropSessionId = "";
    private boolean cropCollectionActive;
    private int cropCapacity;
    private final List<JSONObject> capturedCropRecords = new ArrayList<>();

    public synchronized void startMeasurementSession() {
        traces.clear();
        frameFlowBuckets.clear();
        thermalSamples.clear();
        eventRecords.clear();
        scanAcquisitionSnapshot = null;
        crashMeasurementAvailable = false;
        recoveredCrashCount = 0;
        recoveredCrashSessionId = "";
        recoveredCrashSessionStartedAtMillis = 0L;

        droppedFrames = 0L;
        traceTotalSeen = 0L;
        traceRecordsEvicted = 0L;
        framesReceived = 0L;
        framesProcessed = 0L;
        framesSkippedGate = 0L;
        framesSkippedCameraTransform = 0L;
        framesSkippedHardSceneReset = 0L;
        framesSkippedContinuityHold = 0L;
        framesSkippedContinuityReacquire = 0L;
        finalResultsDroppedAfterReturn = 0L;
        finalResultsDroppedBeforeUi = 0L;
        finalResultsDroppedBeforeCrop = 0L;
        finalResultDispatchAccepted = 0L;
        secondaryScenePreflightDetections = 0L;
        secondaryScenePreflightHolds = 0L;
        secondaryScenePreflightReacquires = 0L;
        secondaryScenePreflightHardResets = 0L;
        secondaryScenePreflightSkippedInference = 0L;
        previewCoordinatorDecisions = 0L;
        legacyPreviewFallbacks = 0L;
        previewDecisionAuthority = "unavailable";
        estimatedUpstreamGaps = 0L;
        lastSourceTimestampNanos = -1L;
        expectedSourceIntervalNanos = Double.NaN;
        eventSequence = 0L;
        experimentSessionId = "";
        frozenRecognitionProfile = recognitionProfile;
        frozenVehicleCascadeEnabled = vehicleCascadeEnabled;
        frozenRoiBudgetPolicy = roiBudgetPolicy;
        frozenExperimentModeEnabled = experimentModeEnabled;
        frozenExperimentRoiBudgetPolicy = experimentRoiBudgetPolicy;
        frozenCaptureProfile = captureProfile;
        frozenRequestedSourceWidth = requestedSourceWidth;
        frozenRequestedSourceHeight = requestedSourceHeight;
        frozenCaptureHighResolutionRequested = captureHighResolutionRequested;
        frozenMotionSensorAvailable = motionSensorAvailable;
        frozenSceneHandlingMode = sceneHandlingMode;
        frozenSceneContinuityProfile = sceneContinuityProfile;
        frozenCameraTimestampSource = cameraTimestampSource;

        firstPreliminaryResultNanos = -1L;
        firstConfirmedResultNanos = -1L;

        actualSourceWidth = 0;
        actualSourceHeight = 0;

        sessionStartedMillis =
                System.currentTimeMillis();

        sessionStartedNanos =
                System.nanoTime();

        sessionStartedElapsedNanos =
                SystemClock.elapsedRealtimeNanos();

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

    public synchronized void setCrashMeasurement(
            boolean available,
            int recoveredCrashes
    ) {
        setCrashMeasurement(available, recoveredCrashes, "", 0L);
    }

    public synchronized void setCrashMeasurement(
            boolean available,
            int recoveredCrashes,
            String lastRecoveredSessionId,
            long lastRecoveredSessionStartedAtMillis
    ) {
        crashMeasurementAvailable = available;
        recoveredCrashCount = Math.max(0, recoveredCrashes);
        recoveredCrashSessionId = lastRecoveredSessionId == null
                ? "" : lastRecoveredSessionId.trim();
        recoveredCrashSessionStartedAtMillis = Math.max(
                0L, lastRecoveredSessionStartedAtMillis
        );
    }

    public synchronized void add(InferenceTrace trace) {
        if (!measurementSessionActive) {
            return;
        }

        traceTotalSeen++;
        framesProcessed++;
        currentFrameFlowBucket().framesProcessed++;

        while (traces.size() >= MAX_TRACES) {
            traces.removeFirst();
            traceRecordsEvicted++;
        }

        traces.addLast(trace);
    }
    public synchronized void frameSkippedByGate() {
        if (!measurementSessionActive) return;
        droppedFrames++;
        framesSkippedGate++;
        currentFrameFlowBucket().framesSkippedGate++;
    }

    public synchronized void frameSkippedByCameraTransform() {
        if (!measurementSessionActive) return;
        droppedFrames++;
        framesSkippedCameraTransform++;
        currentFrameFlowBucket().framesSkippedCameraTransform++;
    }

    public synchronized void frameSkippedByHardSceneReset() {
        if (!measurementSessionActive) return;
        droppedFrames++;
        framesSkippedHardSceneReset++;
        currentFrameFlowBucket().framesSkippedHardSceneReset++;
    }

    public synchronized void frameSkippedByContinuityHold() {
        if (!measurementSessionActive) return;
        droppedFrames++;
        framesSkippedContinuityHold++;
        currentFrameFlowBucket().framesSkippedContinuityHold++;
    }

    public synchronized void frameSkippedByContinuityReacquire() {
        if (!measurementSessionActive) return;
        droppedFrames++;
        framesSkippedContinuityReacquire++;
        currentFrameFlowBucket().framesSkippedContinuityReacquire++;
    }

    public synchronized void finalResultDropped(String phase) {
        if (!measurementSessionActive) return;
        String safePhase = phase == null ? "" : phase;
        if ("before_ui_present".equals(safePhase)
                || "present_result_guard".equals(safePhase)) {
            finalResultsDroppedBeforeUi++;
        } else if ("before_crop_collect".equals(safePhase)) {
            finalResultsDroppedBeforeCrop++;
        } else {
            finalResultsDroppedAfterReturn++;
        }
    }

    public synchronized void finalResultDispatchAccepted() {
        if (measurementSessionActive) finalResultDispatchAccepted++;
    }

    public synchronized void secondaryScenePreflight(
            String action,
            boolean skippedInference
    ) {
        if (!measurementSessionActive) return;
        secondaryScenePreflightDetections++;
        String safeAction = action == null ? "NONE" : action;
        if ("SOFT_HOLD".equals(safeAction)) {
            secondaryScenePreflightHolds++;
        } else if ("SOFT_REACQUIRE".equals(safeAction)) {
            secondaryScenePreflightReacquires++;
        } else if ("HARD_RESET".equals(safeAction)) {
            secondaryScenePreflightHardResets++;
        }
        if (skippedInference) secondaryScenePreflightSkippedInference++;
    }

    public synchronized void previewDecisionAuthority(String authority) {
        if (!measurementSessionActive) return;
        String safeAuthority = authority == null
                ? "unavailable" : authority.trim();
        if (safeAuthority.isEmpty()) safeAuthority = "unavailable";
        previewDecisionAuthority = safeAuthority;
        if ("coordinator".equals(safeAuthority)) {
            previewCoordinatorDecisions++;
        } else if ("legacy_fallback".equals(safeAuthority)) {
            legacyPreviewFallbacks++;
        }
    }

    /** Zachowany alias dla starszych wywołań; nowe miejsca powinny podawać przyczynę. */
    public synchronized void frameDropped() {
        frameSkippedByGate();
    }

    public synchronized int size() { return traces.size(); }

    public synchronized LiveSnapshot liveSnapshot() {

        InferenceTrace trace =
                traces.peekLast();


        if (trace == null) {

            return new LiveSnapshot(
                    actualSourceWidth,
                    actualSourceHeight,
                    droppedFrames,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN


            );
        }


        return new LiveSnapshot(
                actualSourceWidth,
                actualSourceHeight,
                droppedFrames,

                stageMilliseconds(
                        trace,
                        "vehicle_inference"
                ),

                stageMilliseconds(
                        trace,
                        "plate_inference"
                ),

                stageMilliseconds(
                        trace,
                        "character_inference"
                ),

                stageMilliseconds(
                        trace,
                        "total"
                ),

                stageMilliseconds(
                        trace,
                        "inference_sum"
                ),

                stageMilliseconds(
                        trace,
                        "auxiliary_sum"
                )
        );
    }

    private static double stageMilliseconds(
            InferenceTrace trace,
            String stage
    ) {
        Long nanos =
                trace.durationsNanos()
                        .get(stage);


        /*
         * Brak etapu oznacza brak wykonania,
         * nie czas równy 0 ms.
         */
        if (nanos == null) {
            return Double.NaN;
        }


        return nanos / 1_000_000.0;
    }
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

    public synchronized void setSceneContinuityConfiguration(
            String mode,
            String profile
    ) {
        sceneHandlingMode = mode == null || mode.trim().isEmpty()
                ? "dynamic_continuity" : mode.trim();
        sceneContinuityProfile = profile == null || profile.trim().isEmpty()
                ? "initial_v2" : profile.trim();
    }

    public synchronized void setCameraTimestampSource(String source) {
        cameraTimestampSource = source == null || source.trim().isEmpty()
                ? "UNAVAILABLE" : source.trim();
    }

    public synchronized void setCaptureConfiguration(
            String profile,
            int width,
            int height
    ) {
        setCaptureConfiguration(
                profile,
                width,
                height,
                false
        );
    }


    public synchronized void setCaptureConfiguration(
            String profile,
            int width,
            int height,
            boolean highResolutionRequested
    ) {
        captureProfile =
                profile == null
                        ? "auto"
                        : profile;

        requestedSourceWidth =
                Math.max(
                        0,
                        width
                );

        requestedSourceHeight =
                Math.max(
                        0,
                        height
                );

        captureHighResolutionRequested =
                highResolutionRequested;
    }
    public synchronized void observeSourceFrame(int width, int height) {
        observeSourceFrame(width, height, -1L);
    }

    public synchronized void observeSourceFrame(int width, int height, long sourceTimestampNanos) {
        actualSourceWidth = Math.max(0, width);
        actualSourceHeight = Math.max(0, height);
        if (!measurementSessionActive) return;
        framesReceived++;
        FrameFlowBucket bucket = currentFrameFlowBucket();
        bucket.framesReceived++;
        if (sourceTimestampNanos > 0L && lastSourceTimestampNanos > 0L) {
            long interval = sourceTimestampNanos - lastSourceTimestampNanos;
            if (interval > 0L && interval < 1_000_000_000L) {
                if (!Double.isNaN(expectedSourceIntervalNanos)
                        && interval > expectedSourceIntervalNanos * 1.5) {
                    long gaps = Math.max(0L, Math.round(interval / expectedSourceIntervalNanos) - 1L);
                    estimatedUpstreamGaps += gaps;
                    bucket.estimatedUpstreamGaps += gaps;
                }
                expectedSourceIntervalNanos = Double.isNaN(expectedSourceIntervalNanos)
                        ? interval
                        : expectedSourceIntervalNanos * 0.9 + interval * 0.1;
            }
        }
        if (sourceTimestampNanos > 0L) lastSourceTimestampNanos = sourceTimestampNanos;
    }

    public synchronized void setExperimentSessionId(String sessionId) {
        experimentSessionId = sessionId == null ? "" : sessionId;
    }

    public synchronized void updateScanAcquisition(
            ScanAcquisitionSnapshot snapshot
    ) {
        scanAcquisitionSnapshot = snapshot;
    }

    public synchronized void recordThermalSample(ThermalMonitor.Snapshot snapshot) {
        if (!measurementSessionActive || snapshot == null) return;
        try {
            JSONObject sample = new JSONObject();
            sample.put("experiment_session_id", experimentSessionId);
            sample.put("elapsed_ms", elapsedMillis(snapshot.capturedElapsedMillis));
            putFiniteOrNull(sample, "battery_temperature_c", snapshot.batteryTemperatureC);
            sample.put("thermal_status", snapshot.thermalStatus >= 0
                    ? snapshot.thermalStatus : JSONObject.NULL);
            putFiniteOrNull(sample, "thermal_headroom", snapshot.thermalHeadroom);
            sample.put("headroom_available", snapshot.headroomAvailable());
            sample.put("battery_percent", snapshot.batteryPercent >= 0
                    ? snapshot.batteryPercent : JSONObject.NULL);
            sample.put("charging", snapshot.charging);
            sample.put("available_memory_bytes", snapshot.availableMemoryBytes >= 0L
                    ? snapshot.availableMemoryBytes : JSONObject.NULL);
            thermalSamples.add(sample);
        } catch (JSONException ignored) {
            // Wszystkie pola pochodzą z kontrolowanych typów prostych.
        }
    }

    public synchronized void recordEvent(
            String eventType,
            long frameId,
            long trackId,
            JSONObject details
    ) {
        if (!measurementSessionActive) return;
        try {
            JSONObject event = new JSONObject();
            event.put("experiment_session_id", experimentSessionId);
            event.put("event_seq", ++eventSequence);
            event.put("elapsed_ms", elapsedMillis(SystemClock.elapsedRealtime()));
            if (frameId > 0L) event.put("frame_id", frameId);
            if (trackId > 0L) event.put("track_id", trackId);
            event.put("event_type", eventType == null ? "unknown" : eventType);
            if (details != null) {
                java.util.Iterator<String> keys = details.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    event.put(key, details.opt(key));
                }
            }
            eventRecords.add(event);
        } catch (JSONException ignored) {
            // Rekord zdarzenia pozostaje opcjonalną telemetrią.
        }
    }

    private FrameFlowBucket currentFrameFlowBucket() {
        long elapsed = elapsedMillis(SystemClock.elapsedRealtime());
        long bucketStart = (elapsed / 1_000L) * 1_000L;
        FrameFlowBucket bucket = frameFlowBuckets.get(bucketStart);
        if (bucket == null) {
            bucket = new FrameFlowBucket(bucketStart);
            frameFlowBuckets.put(bucketStart, bucket);
        }
        return bucket;
    }

    private long elapsedMillis(long elapsedRealtimeMillis) {
        return Math.max(0L, elapsedRealtimeMillis - sessionStartedElapsedNanos / 1_000_000L);
    }

    private static void putFiniteOrNull(JSONObject target, String key, double value)
            throws JSONException {
        target.put(key, Double.isNaN(value) || Double.isInfinite(value)
                ? JSONObject.NULL : value);
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
            record.put("consensus_text", item.consensusText);
            record.put("confirmed", item.confirmed);
            record.put("track_confirmed", item.trackConfirmed);
            record.put("fresh_mz_successful", item.freshMzSuccessful);
            record.put("crop_supports_consensus", item.cropSupportsConsensus);
            record.put("consensus_observations", item.consensusObservations);
            record.put("mz_attempt_index", item.mzAttemptIndex);
            record.put("layout", item.layout);
            record.put("row_counts", new JSONArray(item.rowCounts));
            record.put("fresh_prediction", item.freshPrediction);
            record.put("plate_confidence", item.plateConfidence);
            record.put("recognition_confidence", item.recognitionConfidence);
            record.put("sharpness", item.sharpness);
            record.put("camera_zoom_ratio", item.cameraZoomRatio);
            record.put("capture_source", item.captureSource);
            record.put("plate_geometry", item.plateGeometry.toJson());
            record.put("image_difficulty", item.imageDifficulty.toJson());
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
        return createJsonReport(
                device,
                registry,
                autoTuneManager,
                null
        );
    }
    public synchronized String createJsonReport(
            DeviceProfile device,
            ModelRegistry registry,
            AutoTuneManager autoTuneManager,
            ExperimentSession.Snapshot experimentSession
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
        ResearchExecutionConfig researchConfig = experimentSession == null
                ? null : experimentSession.frozenExecutionConfig;

        JSONObject plateExecution = researchConfig == null
                ? executionJson(plate, autoTuneManager)
                : researchConfig.plate.toJson();
        JSONObject characterExecution = researchConfig == null
                ? executionJson(character, autoTuneManager)
                : researchConfig.character.toJson();
        JSONObject vehicleExecution = researchConfig == null
                ? executionJson(vehicle, autoTuneManager)
                : researchConfig.vehicle.toJson();
        String packageId = researchConfig != null && !researchConfig.basePackageId.isEmpty()
                ? researchConfig.basePackageId
                : packageId(basePackage == null ? activePackage : basePackage, plate, character);
        String variantId = combinedVariantId(vehicleExecution, plateExecution, characterExecution);

        JSONObject report = new JSONObject();
        report.put("schema", REPORT_SCHEMA);
        report.put("report_id", safeId("r-" + finishedMillis + "-" + packageId));
        report.put("package_id", packageId);
        if (researchConfig != null && !researchConfig.basePackageId.isEmpty()) {
            report.put("package_version", researchConfig.basePackageVersion);
            report.put("package_created_at", researchConfig.basePackageCreatedAt);
            report.put("package_source_sha256", researchConfig.basePackageSourceSha256);
            report.put("base_package_fingerprint", researchConfig.basePackageFingerprint);
        } else if (basePackage != null) {
            report.put("package_version", basePackage.manifest().version());
            report.put("package_created_at", basePackage.manifest().createdAt());
            report.put("package_source_sha256", basePackage.sourceSha256());
            report.put("base_package_fingerprint", basePackage.fingerprint());
        }
        report.put("variant_id", variantId);
        report.put("measured_at", Instant.ofEpochMilli(finishedMillis).toString());
        report.put("app_version", device.appVersion);
        JSONObject appBuild = new JSONObject();
        appBuild.put("git_commit", BuildConfig.GIT_COMMIT);
        appBuild.put("git_dirty", BuildConfig.GIT_DIRTY);
        appBuild.put("git_dirty_available", BuildConfig.GIT_DIRTY_AVAILABLE);
        appBuild.put(
                "source_state",
                BuildConfig.GIT_DIRTY_AVAILABLE
                        ? (BuildConfig.GIT_DIRTY ? "dirty" : "clean")
                        : "unknown"
        );
        appBuild.put("build_type", BuildConfig.BUILD_TYPE);
        appBuild.put("built_at_utc", BuildConfig.BUILT_AT_UTC);
        appBuild.put("version_name", BuildConfig.VERSION_NAME);
        appBuild.put("version_code", BuildConfig.VERSION_CODE);
        report.put("app_build", appBuild);
        report.put("device", device.toJson());
        report.put("runtime", commonValue(plateExecution, characterExecution, "runtime"));
        report.put("delegate", commonValue(plateExecution, characterExecution, "delegate"));
        report.put("warmup_runs", 0);
        report.put("measured_runs", traces.size());
        report.put("trace_total_seen", traceTotalSeen);
        report.put("trace_records_retained", traces.size());
        report.put("trace_records_evicted", traceRecordsEvicted);
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
        report.put(
                "recognition_profile",
                researchConfig == null
                        ? frozenRecognitionProfile
                        : researchConfig.recognitionProfile.wireName()
        );

        /*
         * Zachowujemy efektywną politykę również na najwyższym poziomie
         * dla zgodności z wcześniejszymi raportami.
         */
        report.put(
                "roi_budget_policy",
                researchConfig == null
                        ? frozenRoiBudgetPolicy
                        : researchConfig.roiBudgetPolicy.wireName()
        );
        report.put("scene_handling_mode", frozenSceneHandlingMode);
        report.put("scene_continuity_profile", frozenSceneContinuityProfile);
        report.put("camera_timestamp_source", frozenCameraTimestampSource);

        JSONObject normalConfiguration = new JSONObject();
        normalConfiguration.put(
                "vehicle_cascade_enabled",
                frozenVehicleCascadeEnabled
        );
        report.put(
                "normal_configuration",
                normalConfiguration
        );

        JSONObject experiment = new JSONObject();

        boolean hasExperimentSession =
                experimentSession != null
                        && experimentSession.hasSession();

        /*
         * Jeżeli istnieje konkretna sesja eksperymentalna,
         * jej konfiguracja jest ważniejsza niż aktualny stan UI.
         *
         * Dzięki temu zmiana R0 -> R1 już PO wykonaniu eksperymentu,
         * ale PRZED eksportem, nie zmieni opisu zakończonego przebiegu.
         */
        String reportedExperimentType =
                hasExperimentSession
                        ? experimentSession.experimentType
                        : "roi_budget";

        String reportedExperimentRoiPolicy = researchConfig == null
                ? frozenExperimentRoiBudgetPolicy
                : researchConfig.roiBudgetPolicy.wireName();

        String reportedEffectiveRoiPolicy = researchConfig == null
                ? frozenRoiBudgetPolicy
                : researchConfig.roiBudgetPolicy.wireName();


        /*
         * Zakończona sesja eksperymentalna oznacza, że raport
         * opisuje eksperyment nawet wtedy, gdy użytkownik później
         * wyłączył tryb EXP przed eksportem.
         */
        experiment.put(
                "enabled",
                frozenExperimentModeEnabled || hasExperimentSession
        );

        experiment.put(
                "type",
                reportedExperimentType
        );

        experiment.put(
                "roi_budget_policy",
                reportedExperimentRoiPolicy
        );

        experiment.put(
                "effective_roi_budget_policy",
                reportedEffectiveRoiPolicy
        );


        /*
         * Sekcja session istnieje tylko wtedy,
         * gdy rzeczywiście uruchomiono eksperyment.
         */
        if (hasExperimentSession) {
            experiment.put("series_id", experimentSession.seriesId);
            experiment.put("scenario_id", experimentSession.scenarioId);
            experiment.put("variant", experimentSession.variant);
            experiment.put("replicate_index", experimentSession.replicateIndex);
            if (researchConfig != null) {
                experiment.put("effective_execution_config", researchConfig.toJson());
            }
            if (!experimentSession.notes.isEmpty()) {
                experiment.put("notes", experimentSession.notes);
            }
            JSONObject session = new JSONObject();

            session.put(
                    "id",
                    experimentSession.sessionId
            );

            session.put(
                    "state",
                    experimentSession.state
            );

            session.put(
                    "started_at_ms",
                    experimentSession.startedAtMillis
            );

            if (experimentSession.finishedAtMillis >= 0L) {
                session.put(
                        "finished_at_ms",
                        experimentSession.finishedAtMillis
                );
            }

            session.put(
                    "duration_ms",
                    experimentSession.durationMillis
            );

            if (experimentSession.completionReason != null
                    && !experimentSession.completionReason.isEmpty()) {

                session.put(
                        "completion_reason",
                        experimentSession.completionReason
                );
            }

            session.put(
                    "experiment_type",
                    experimentSession.experimentType
            );

            session.put(
                    "variant",
                    experimentSession.variant
            );
            session.put("series_id", experimentSession.seriesId);
            session.put("scenario_id", experimentSession.scenarioId);
            session.put("replicate_index", experimentSession.replicateIndex);
            session.put("completion_status", experimentSession.completionStatus);
            if (!experimentSession.notes.isEmpty()) {
                session.put("notes", experimentSession.notes);
            }
            JSONObject autoZoom = new JSONObject();
            autoZoom.put("enabled", experimentSession.autoZoomEnabled);
            autoZoom.put("max_zoom_ratio", experimentSession.maxZoomRatio);
            session.put("auto_zoom", autoZoom);
            JSONObject thermalStart = new JSONObject();
            thermalStart.put("enabled", experimentSession.thermalStartConditionEnabled);
            thermalStart.put(
                    "max_battery_temperature_c",
                    experimentSession.maxStartBatteryTemperatureC
            );
            thermalStart.put("max_thermal_status", experimentSession.maxStartThermalStatus);
            thermalStart.put(
                    "stabilization_ms",
                    experimentSession.thermalStabilizationMillis
            );
            session.put("thermal_start_condition", thermalStart);
            JSONObject timer = new JSONObject();

            timer.put(
                    "enabled",
                    experimentSession.timerEnabled
            );

            if (experimentSession.timerEnabled) {
                timer.put(
                        "configured_duration_ms",
                        experimentSession.timerDurationMillis
                );
            }

            session.put(
                    "timer",
                    timer
            );

            experiment.put(
                    "session",
                    session
            );
        }

        report.put(
                "experiment",
                experiment
        );
        JSONObject capture = new JSONObject();


        /*
         * Sposób wyboru rozdzielczości.
         *
         * "auto"     -> aplikacja dobiera format do urządzenia,
         * "explicit" -> użytkownik wskazał konkretny format WxH.
         */
        boolean automaticResolutionSelection =
                frozenCaptureProfile == null
                        || frozenCaptureProfile.trim().isEmpty()
                        || "auto".equalsIgnoreCase(
                        frozenCaptureProfile.trim()
                );


        String selectionMode =
                automaticResolutionSelection
                        ? "auto"
                        : "explicit";


        String selectedResolution =
                automaticResolutionSelection
                        ? "auto"
                        : frozenCaptureProfile;


        /*
         * Nowe, jednoznaczne pola.
         */
        capture.put(
                "selection_mode",
                selectionMode
        );

        capture.put(
                "selected_resolution",
                selectedResolution
        );


        /*
         * Zachowujemy stare pole "profile" dla zgodności
         * z wcześniej zapisanymi raportami i parserami.
         *
         * Od tej wersji jego kanonicznym odpowiednikiem
         * jest "selected_resolution".
         */
        capture.put(
                "profile",
                frozenCaptureProfile
        );


        capture.put(
                "requested_width",
                frozenRequestedSourceWidth
        );

        capture.put(
                "requested_height",
                frozenRequestedSourceHeight
        );

        capture.put(
                "actual_width",
                actualSourceWidth
        );

        capture.put(
                "actual_height",
                actualSourceHeight
        );


        /*
         * To NIE oznacza po prostu "dużej rozdzielczości".
         *
         * Informuje wyłącznie, że wybrany format pochodził
         * ze specjalnej puli high-resolution Camera2 i CameraX
         * został uruchomiony w trybie preferującym tę pulę.
         *
         * Dlatego np. zwykłe 4000x3000 może mieć tutaj false.
         */
        capture.put(
                "extended_high_resolution_mode_requested",
                frozenCaptureHighResolutionRequested
        );


        /*
         * Stary klucz pozostaje jako alias kompatybilności.
         * W nowych analizach używamy:
         *
         * extended_high_resolution_mode_requested
         */
        capture.put(
                "high_resolution_mode_requested",
                frozenCaptureHighResolutionRequested
        );


        boolean actualAvailable =
                actualSourceWidth > 0
                        && actualSourceHeight > 0;


        boolean resolutionMatched =
                actualAvailable
                        && (
                        (
                                frozenRequestedSourceWidth
                                        == actualSourceWidth
                                        && frozenRequestedSourceHeight
                                        == actualSourceHeight
                        )
                                ||
                                (
                                        /*
                                         * Obrót urządzenia nie oznacza
                                         * innej rozdzielczości źródłowej.
                                         */
                                        frozenRequestedSourceWidth
                                                == actualSourceHeight
                                                && frozenRequestedSourceHeight
                                                == actualSourceWidth
                                )
                );


        capture.put(
                "requested_resolution_matched",
                resolutionMatched
        );


        /*
         * Pomocniczy zapis faktycznie otrzymanego formatu.
         */
        if (actualAvailable) {
            capture.put(
                    "actual_resolution",
                    actualSourceWidth
                            + "x"
                            + actualSourceHeight
            );
        }


        capture.put(
                "pixel_format",
                "YUV_420_888"
        );

        capture.put(
                "gyroscope_available",
                frozenMotionSensorAvailable
        );


        report.put(
                "capture",
                capture
        );

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

        JSONObject modelRefs = researchConfig == null
                ? currentModelRefs(registry, autoTuneManager, vehicle, plate, character)
                : researchConfig.modelRefsJson();
        report.put("model_refs", modelRefs);
        if (researchConfig != null && !researchConfig.basePackageId.isEmpty()) {
            report.put("composition", researchConfig.compositionJson());
        }

        JSONObject modelIds = new JSONObject();
        if (researchConfig != null) {
            if (researchConfig.vehicle.enabled) {
                modelIds.put("vehicle", researchConfig.vehicle.modelId);
            }
            modelIds.put("plate", researchConfig.plate.modelId);
            modelIds.put("character", researchConfig.character.modelId);
        } else {
            if (vehicle != null) modelIds.put("vehicle", vehicle.manifest().modelId());
            if (plate != null) modelIds.put("plate", plate.manifest().modelId());
            if (character != null) modelIds.put("character", character.manifest().modelId());
        }
        report.put("model_ids", modelIds);
        report.put("models", execution);
        report.put("autotune_profiles", autoTuneManager.exportProfiles());
        report.put(
                "runtime_composition",
                researchConfig == null
                        ? runtimeCompositionJson(
                                registry,
                                autoTuneManager,
                                basePackage,
                                vehicle,
                                plate,
                                character
                        )
                        : frozenRuntimeCompositionJson(researchConfig)
        );

        Map<String, List<Double>> stageValues = new LinkedHashMap<>();
        Map<String, List<Double>> gaugeValues = new LinkedHashMap<>();
        Map<String, Long> gaugeLastValues = new LinkedHashMap<>();
        Map<String, Integer> statuses = new LinkedHashMap<>();
        Map<String, Long> counterTotals = new LinkedHashMap<>();
        JSONArray traceArray = new JSONArray();
        long peakPssKb = -1L;
        int recognizedFrames = 0;
        for (InferenceTrace trace : traces) {
            JSONObject traceJson = trace.toJson();
            traceJson.put("elapsed_ms", traceElapsedMillis(trace));
            traceArray.put(traceJson);
            statuses.put(trace.status(), statuses.getOrDefault(trace.status(), 0) + 1);
            if ("recognized".equals(trace.status())) recognizedFrames++;
            peakPssKb = Math.max(peakPssKb, Math.max(trace.pssStartKb(), trace.pssEndKb()));
            for (Map.Entry<String, Long> entry : trace.durationsNanos().entrySet()) {
                stageValues.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(entry.getValue() / 1_000_000.0);
            }
            for (Map.Entry<String, Long> entry : trace.counters().entrySet()) {
                if (isGauge(entry.getKey())) {
                    gaugeValues.computeIfAbsent(
                            entry.getKey(), ignored -> new ArrayList<>()
                    ).add(entry.getValue().doubleValue());
                    gaugeLastValues.put(entry.getKey(), entry.getValue());
                }
            }
            aggregateCounterTotals(counterTotals, trace.counters());
        }

        JSONObject summary = new JSONObject();
        summary.put("processed_frames", framesProcessed);
        summary.put("statuses", new JSONObject(statuses));
        summary.put("stages", stageSummaryJson(stageValues));
        summary.put("counters", new JSONObject(counterTotals));
        summary.put("gauges", gaugeSummaryJson(gaugeValues, gaugeLastValues));
        report.put("summary", summary);
        report.put("latency", latencyJson(stageValues));

        JSONObject frameFlow = new JSONObject();
        frameFlow.put("frames_received", framesReceived);
        frameFlow.put("frames_processed", framesProcessed);
        frameFlow.put("frames_skipped_frame_gate", framesSkippedGate);
        frameFlow.put("frames_skipped_camera_transform", framesSkippedCameraTransform);
        frameFlow.put("frames_skipped_hard_scene_reset", framesSkippedHardSceneReset);
        frameFlow.put("frames_skipped_continuity_hold", framesSkippedContinuityHold);
        frameFlow.put("frames_skipped_continuity_reacquire", framesSkippedContinuityReacquire);
        frameFlow.put("estimated_upstream_gaps", estimatedUpstreamGaps);
        frameFlow.put("upstream_gaps_are_estimated", true);
        frameFlow.put("bucket_ms", 1_000);
        report.put("frame_flow", frameFlow);

        if (scanAcquisitionSnapshot != null) {
            com.example.alpr_v1.acquisition.ScanAcquisitionStats scanStats =
                    scanAcquisitionSnapshot.stats;
            JSONObject scan = new JSONObject();
            scan.put("scan_run_id", scanAcquisitionSnapshot.scanRunId);
            scan.put("scan_run_state", scanAcquisitionSnapshot.runState.name());
            scan.put("scan_profile", "phase3b_initial");
            scan.put(
                    "scan_run_active_duration_ms",
                    scanAcquisitionSnapshot.runActiveDurationNanos / 1_000_000.0
            );
            scan.put(
                    "scan_run_wall_duration_ms",
                    scanAcquisitionSnapshot.runWallDurationNanos / 1_000_000.0
            );
            scan.put("acquisition_queue_size", scanAcquisitionSnapshot.queue.size());
            scan.put("vehicles_seen", scanStats.vehiclesSeen);
            scan.put("vehicles_queued", scanStats.vehiclesQueued);
            scan.put("vehicles_selected", scanStats.vehiclesSelected);
            scan.put("vehicles_deferred", scanStats.vehiclesDeferred);
            scan.put("vehicles_lost", scanStats.vehiclesLost);
            scan.put(
                    "entities_ready_to_finalize",
                    scanStats.entitiesReadyToFinalize
            );
            scan.put("acquisitions_finalized", scanStats.acquisitionsFinalized);
            scan.put("unique_plates_saved", scanStats.uniquePlatesSaved);
            scan.put(
                    "duplicate_acquisitions_suppressed",
                    scanStats.duplicateAcquisitionsSuppressed
            );
            scan.put("duplicate_capture_rate", scanStats.duplicateCaptureRate);
            scan.put("mean_acquisition_ms", scanStats.meanAcquisitionMillis);
            scan.put("p95_acquisition_ms", scanStats.p95AcquisitionMillis);
            scan.put(
                    "unique_plates_per_wall_minute",
                    scanStats.uniquePlatesPerWallMinute
            );
            scan.put("mean_queue_wait_ms", scanStats.meanQueueWaitMillis);
            scan.put("p95_queue_wait_ms", scanStats.p95QueueWaitMillis);
            scan.put(
                    "mean_active_session_ms",
                    scanStats.meanActiveSessionMillis
            );
            scan.put(
                    "p95_active_session_ms",
                    scanStats.p95ActiveSessionMillis
            );
            scan.put("mt_attempts_per_entity", scanStats.mtAttemptsPerEntity);
            scan.put(
                    "fresh_mz_attempts_per_entity",
                    scanStats.freshMzAttemptsPerEntity
            );
            JSONArray acquisitionRecords = new JSONArray();
            for (AcquisitionRecord record : scanAcquisitionSnapshot.acquisitionRecords) {
                JSONObject entry = new JSONObject();
                entry.put("record_id", record.recordId);
                entry.put("scan_run_id", record.scanRunId);
                entry.put("session_id", record.sessionId);
                entry.put("entity_id", record.entityId);
                entry.put("plate_track_id", record.plateTrackId);
                entry.put("text", record.text);
                entry.put("normalized_text", record.normalizedText);
                entry.put("confidence", record.confidence);
                entry.put("consensus_observations", record.consensusObservations);
                entry.put(
                        "first_observation_elapsed_nanos",
                        record.firstObservationRuntimeNanos
                );
                entry.put("finalized_elapsed_nanos", record.finalizedRuntimeNanos);
                entry.put(
                        "acquisition_duration_ms",
                        record.acquisitionDurationNanos / 1_000_000.0
                );
                entry.put("best_crop_id", record.bestCropId);
                entry.put("best_crop_reference_kind", "pipeline_observation");
                entry.put("unique_saved", record.uniqueSaved);
                entry.put("duplicate_suppressed", record.duplicateSuppressed());
                if (record.duplicateSuppressed()) {
                    entry.put("duplicate_of_record_id", record.duplicateOfRecordId);
                }
                acquisitionRecords.put(entry);
            }
            scan.put("acquisition_records", acquisitionRecords);
            report.put("scan_acquisition", scan);
        }

        JSONObject finalResultDispatch = new JSONObject();
        finalResultDispatch.put(
                "final_results_dropped_after_return",
                finalResultsDroppedAfterReturn
        );
        finalResultDispatch.put(
                "final_results_dropped_before_ui",
                finalResultsDroppedBeforeUi
        );
        finalResultDispatch.put(
                "final_results_dropped_before_crop",
                finalResultsDroppedBeforeCrop
        );
        finalResultDispatch.put(
                "final_result_dispatch_accepted",
                finalResultDispatchAccepted
        );
        finalResultDispatch.put(
                "final_result_stale_after_pipeline_return",
                finalResultsDroppedAfterReturn
        );
        finalResultDispatch.put(
                "final_result_stale_before_ui",
                finalResultsDroppedBeforeUi
        );
        finalResultDispatch.put(
                "final_result_stale_before_crop",
                finalResultsDroppedBeforeCrop
        );
        report.put("final_result_dispatch", finalResultDispatch);

        JSONObject secondaryPreflight = new JSONObject();
        secondaryPreflight.put(
                "secondary_scene_preflight_detected",
                secondaryScenePreflightDetections
        );
        secondaryPreflight.put(
                "secondary_scene_preflight_holds",
                secondaryScenePreflightHolds
        );
        secondaryPreflight.put(
                "secondary_scene_preflight_reacquires",
                secondaryScenePreflightReacquires
        );
        secondaryPreflight.put(
                "secondary_scene_preflight_hard_resets",
                secondaryScenePreflightHardResets
        );
        secondaryPreflight.put(
                "secondary_scene_preflight_skipped_inference",
                secondaryScenePreflightSkippedInference
        );
        report.put("secondary_scene_preflight", secondaryPreflight);

        JSONObject previewAuthority = new JSONObject();
        previewAuthority.put(
                "preview_decision_authority", previewDecisionAuthority
        );
        previewAuthority.put(
                "preview_coordinator_decisions", previewCoordinatorDecisions
        );
        previewAuthority.put(
                "legacy_preview_fallbacks", legacyPreviewFallbacks
        );
        report.put("preview_decision_authority", previewAuthority);

        report.put("thermal", thermalSummaryJson());

        JSONObject retention = new JSONObject();
        retention.put("trace_capacity", MAX_TRACES);
        retention.put("trace_total_seen", traceTotalSeen);
        retention.put("trace_records_retained", traces.size());
        retention.put("trace_records_evicted", traceRecordsEvicted);
        retention.put("complete", traceRecordsEvicted == 0L);
        if (traceRecordsEvicted > 0L) {
            retention.put("reason", "trace_ring_buffer_evicted_oldest_records");
        }
        InferenceTrace firstRetained = traces.peekFirst();
        InferenceTrace lastRetained = traces.peekLast();
        if (firstRetained != null) {
            retention.put("retained_from_elapsed_ms", traceElapsedMillis(firstRetained));
        }
        if (lastRetained != null) {
            retention.put("retained_to_elapsed_ms", traceElapsedMillis(lastRetained));
        }
        report.put("data_retention", retention);

        JSONObject completeness = new JSONObject();
        completeness.put("status", traceRecordsEvicted == 0L ? "complete" : "incomplete");
        completeness.put("time_series_complete", traceRecordsEvicted == 0L);
        completeness.put(
                "session_completion_status",
                hasExperimentSession
                        ? experimentSession.completionStatus
                        : (measurementSessionActive ? "running" : "stopped_manual")
        );
        if (traceRecordsEvicted > 0L) {
            completeness.put("reason", "trace_records_evicted");
        }
        report.put("data_completeness", completeness);

        JSONObject memory = new JSONObject();
        if (peakPssKb >= 0L) memory.put("ram_peak_mb", peakPssKb / 1024.0);
        long packageBytes = packageSizeBytes(activePackage, vehicle, plate, character);
        if (packageBytes > 0L) memory.put("package_size_mb", packageBytes / (1024.0 * 1024.0));
        report.put("memory", memory);

        JSONObject quality = qualityJson(capturedCropRecords);
        quality.put("observed_recognition_yield", traces.isEmpty() ? 0.0 : recognizedFrames / (double) traces.size());
        report.put("quality", quality);

        JSONObject errors = new JSONObject();
        errors.put("crash_measurement_available", crashMeasurementAvailable);
        errors.put(
                "crash_count",
                crashMeasurementAvailable ? recoveredCrashCount : JSONObject.NULL
        );
        errors.put(
                "crash_semantics",
                "previous_uncontrolled_measurement_sessions_recovered_on_next_process_start"
        );
        if (!recoveredCrashSessionId.isEmpty()) {
            errors.put("last_recovered_session_id", recoveredCrashSessionId);
        }
        if (recoveredCrashSessionStartedAtMillis > 0L) {
            errors.put(
                    "last_recovered_session_started_at_ms",
                    recoveredCrashSessionStartedAtMillis
            );
        }
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

    private static JSONObject currentModelRefs(
            ModelRegistry registry,
            AutoTuneManager autoTuneManager,
            InstalledModel vehicle,
            InstalledModel plate,
            InstalledModel character
    ) throws JSONException {
        JSONObject refs = new JSONObject();
        putCurrentModelRef(refs, "vehicle", ModelRole.VEHICLE, registry, autoTuneManager, vehicle);
        putCurrentModelRef(refs, "plate", ModelRole.PLATE, registry, autoTuneManager, plate);
        putCurrentModelRef(refs, "character", ModelRole.CHARACTER, registry, autoTuneManager, character);
        return refs;
    }

    private static void putCurrentModelRef(
            JSONObject refs,
            String key,
            ModelRole role,
            ModelRegistry registry,
            AutoTuneManager autoTuneManager,
            InstalledModel model
    ) throws JSONException {
        if (model == null) return;
        refs.put(
                key,
                ModelRefResolver.resolve(
                        registry,
                        role,
                        model,
                        autoTuneManager.chosenVariant(model)
                ).toJson()
        );
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

    private static JSONObject frozenRuntimeCompositionJson(ResearchExecutionConfig config)
            throws JSONException {
        JSONObject composition = new JSONObject();
        composition.put("schema", "alpr.runtime_composition.v1");
        composition.put("modified", config.compositionModified);
        if (!config.basePackageId.isEmpty()) {
            composition.put("base_package_id", config.basePackageId);
            composition.put("base_package_version", config.basePackageVersion);
            composition.put("base_package_created_at", config.basePackageCreatedAt);
            composition.put("base_package_sha256", config.basePackageSourceSha256);
        }
        JSONObject models = new JSONObject();
        putFrozenCompositionModel(models, "vehicle", config.vehicle);
        putFrozenCompositionModel(models, "plate", config.plate);
        putFrozenCompositionModel(models, "character", config.character);
        composition.put("models", models);
        composition.put(
                "runtime_set_id",
                safeId("runtime-"
                        + frozenCompositionPart(config.vehicle) + "-"
                        + frozenCompositionPart(config.plate) + "-"
                        + frozenCompositionPart(config.character))
        );
        return composition;
    }

    private static void putFrozenCompositionModel(
            JSONObject destination,
            String key,
            com.example.alpr_v1.experiment.ResearchStageExecutionConfig stage
    ) throws JSONException {
        if (!stage.enabled) return;
        JSONObject value = new JSONObject();
        value.put("model_id", stage.modelId);
        value.put("fingerprint", stage.modelFingerprint);
        value.put("variant_id", stage.variantId);
        value.put("runtime", stage.runtime.wireName());
        value.put("precision", stage.precision);
        destination.put(key, value);
    }

    private static String frozenCompositionPart(
            com.example.alpr_v1.experiment.ResearchStageExecutionConfig stage
    ) {
        return stage.enabled ? stage.modelFingerprint + "-" + stage.variantId : "none";
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

    private static JSONObject gaugeSummaryJson(
            Map<String, List<Double>> gaugeValues,
            Map<String, Long> gaugeLastValues
    ) throws JSONException {
        JSONObject gauges = stageSummaryJson(gaugeValues);
        for (Map.Entry<String, Long> entry : gaugeLastValues.entrySet()) {
            JSONObject gauge = gauges.optJSONObject(entry.getKey());
            if (gauge != null) gauge.put("last", entry.getValue());
        }
        return gauges;
    }

    static void aggregateCounterTotals(
            Map<String, Long> totals,
            Map<String, Long> traceCounters
    ) {
        if (totals == null || traceCounters == null) return;
        for (Map.Entry<String, Long> entry : traceCounters.entrySet()) {
            if (!isSummableCounter(entry.getKey())) continue;
            long value = entry.getValue() == null ? 0L : Math.max(0L, entry.getValue());
            totals.put(entry.getKey(), totals.getOrDefault(entry.getKey(), 0L) + value);
        }
    }

    static boolean isSummableCounter(String key) {
        if (key == null || key.isEmpty() || isGauge(key)) return false;
        return !key.endsWith("_id")
                && !key.endsWith("_timestamp_nanos")
                && !"processing_started_nanos".equals(key)
                && !"result_available_nanos".equals(key);
    }

    private static boolean isGauge(String key) {
        return "vehicle_tracks_active".equals(key)
                || "vehicle_entities_active".equals(key)
                || "vehicle_tracks_predicted".equals(key);
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

    private long traceElapsedMillis(InferenceTrace trace) {
        return Math.max(
                0L,
                (trace.elapsedRealtimeNanos() - sessionStartedElapsedNanos) / 1_000_000L
        );
    }

    private JSONObject thermalSummaryJson() throws JSONException {
        JSONObject summary = new JSONObject();
        summary.put("sample_count", thermalSamples.size());
        summary.put("sampling_independent_of_processed_fps", true);
        if (thermalSamples.isEmpty()) {
            summary.put("available", false);
            return summary;
        }
        summary.put("available", true);
        double minTemperature = Double.POSITIVE_INFINITY;
        double maxTemperature = Double.NEGATIVE_INFINITY;
        int transitions = 0;
        int previousStatus = Integer.MIN_VALUE;
        for (JSONObject sample : thermalSamples) {
            double temperature = sample.optDouble("battery_temperature_c", Double.NaN);
            if (!Double.isNaN(temperature)) {
                minTemperature = Math.min(minTemperature, temperature);
                maxTemperature = Math.max(maxTemperature, temperature);
            }
            int status = sample.optInt("thermal_status", Integer.MIN_VALUE);
            if (previousStatus != Integer.MIN_VALUE
                    && status != Integer.MIN_VALUE
                    && status != previousStatus) {
                transitions++;
            }
            if (status != Integer.MIN_VALUE) previousStatus = status;
        }
        JSONObject first = thermalSamples.get(0);
        JSONObject last = thermalSamples.get(thermalSamples.size() - 1);
        if (!Double.isInfinite(minTemperature)) {
            summary.put("battery_temperature_min_c", minTemperature);
            summary.put("battery_temperature_max_c", maxTemperature);
        }
        summary.put("start", new JSONObject(first.toString()));
        summary.put("end", new JSONObject(last.toString()));
        summary.put("thermal_status_transitions", transitions);
        return summary;
    }

    public synchronized String createThermalCsv() {
        StringBuilder csv = new StringBuilder(
                "experiment_session_id,elapsed_ms,battery_temperature_c,thermal_status,thermal_headroom,headroom_available,battery_percent,charging,available_memory_bytes\n"
        );
        for (JSONObject sample : thermalSamples) {
            csv.append(csvCell(sample.optString("experiment_session_id", ""))).append(',')
                    .append(jsonCell(sample, "elapsed_ms")).append(',')
                    .append(jsonCell(sample, "battery_temperature_c")).append(',')
                    .append(jsonCell(sample, "thermal_status")).append(',')
                    .append(jsonCell(sample, "thermal_headroom")).append(',')
                    .append(jsonCell(sample, "headroom_available")).append(',')
                    .append(jsonCell(sample, "battery_percent")).append(',')
                    .append(jsonCell(sample, "charging")).append(',')
                    .append(jsonCell(sample, "available_memory_bytes")).append('\n');
        }
        return csv.toString();
    }

    public synchronized String createFrameFlowCsv() {
        StringBuilder csv = new StringBuilder(
                "experiment_session_id,elapsed_ms,frames_received,frames_processed,frames_skipped_frame_gate,frames_skipped_camera_transform,frames_skipped_hard_scene_reset,frames_skipped_continuity_hold,frames_skipped_continuity_reacquire,estimated_upstream_gaps\n"
        );
        for (FrameFlowBucket bucket : frameFlowBuckets.values()) {
            csv.append(csvCell(experimentSessionId)).append(',')
                    .append(bucket.elapsedMs).append(',')
                    .append(bucket.framesReceived).append(',')
                    .append(bucket.framesProcessed).append(',')
                    .append(bucket.framesSkippedGate).append(',')
                    .append(bucket.framesSkippedCameraTransform).append(',')
                    .append(bucket.framesSkippedHardSceneReset).append(',')
                    .append(bucket.framesSkippedContinuityHold).append(',')
                    .append(bucket.framesSkippedContinuityReacquire).append(',')
                    .append(bucket.estimatedUpstreamGaps).append('\n');
        }
        return csv.toString();
    }

    public synchronized String createEventsJsonl() {
        StringBuilder jsonl = new StringBuilder();
        for (JSONObject event : eventRecords) {
            jsonl.append(event).append('\n');
        }
        return jsonl.toString();
    }

    private static String jsonCell(JSONObject source, String key) {
        if (!source.has(key) || source.isNull(key)) return "";
        Object value = source.opt(key);
        return value == null || value == JSONObject.NULL ? "" : csvCell(String.valueOf(value));
    }

    public synchronized String createCsvReport() {
        String[] stages = new String[]{
                "total", "engine_setup", "camera_conversion", "camera_to_bitmap", "camera_rotation",
                "vehicle_preprocess", "vehicle_inference", "vehicle_postprocess",
                "plate_preprocess", "plate_inference", "plate_postprocess",
                "rectification", "character_preprocess", "character_inference", "character_postprocess",
                "engine_total", "pipeline_finalize", "measured_stage_sum", "inference_sum",
                "auxiliary_sum", "engine_measured_sum", "pipeline_overhead", "engine_overhead"
        };
        StringBuilder csv = new StringBuilder();
        csv.append("frame_id,timestamp_ms,elapsed_ms,status,text");
        for (String stage : stages) csv.append(',').append(stage).append("_ms");
        csv.append(",vehicle_confidence,vehicle_roi_area_ratio,plate_confidence,plate_fit,plate_sharpness,characters_min,characters_mean")
                .append(",pipeline_overhead_ratio,scene_change_score,scene_change_fraction,scene_brightness_delta")
                .append(",camera_zoom_ratio,auto_zoom_target_roi_area_ratio,auto_zoom_lock_score,auto_zoom_lock_second_score,auto_zoom_lock_confidence")
                .append(",mz_runs,mz_skipped,invalid_plate_geometry")
                .append(",vehicle_runs,vehicle_skipped,vehicle_unavailable")
                .append(",plate_roi_runs,plate_full_frame_runs,full_frame_fallbacks")
                .append(",scene_change_candidate,scene_reset,camera_transform_in_progress")
                .append(",auto_zoom_target_roi,auto_zoom_lock_candidates,auto_zoom_lock_misses")
                .append(",plate_detections_raw,plate_detections_after_dedup,plate_detections_suppressed")
                .append(",vehicle_detections_raw,vehicle_detections_used,vehicle_detections_diagnostic,vehicle_detections_rejected_class,vehicle_regions_selected")
                .append(",source_width,source_height,camera_rotation_degrees")
                .append(",rapid_motion_frames")
                .append(",mt_execution_policy,mt_fallback_policy,mt_scheduler_reason,mz_state_event,target_state,target_transition_reason")
                .append(",tracker_quality,tracker_support_ratio,overlay_update_fps,target_roi_area_ratio")
                .append(",tracker_updates,tracker_failures,tracker_inliers")
                .append(",mt_skipped_by_tracker,mt_forced_by_quality,mt_periodic_refresh")
                .append(",mt_scheduler_queue_size,mt_runs_this_frame,mt_deferred_fallbacks,mt_staggered_roi_runs")
                .append(",mt_legacy_burst_runs,mt_legacy_same_cycle_fallbacks")
                .append(",target_lock_age_frames,target_roi_mt_runs")
                .append(",target_recoveries_level_1,target_recoveries_level_2,target_recoveries_level_3,target_recoveries_level_4")
                .append(",locked_track_id,lock_switches,lock_losses,lock_reassociations,frames_to_lock,time_to_lock_ms")
                .append(",pss_start_kb,pss_end_kb,pss_delta_kb")
                .append(",native_heap_start_bytes,native_heap_end_bytes,native_heap_delta_bytes\n");
        for (InferenceTrace trace : traces) {
            csv.append(trace.frameId()).append(',')
                    .append(trace.timestampMillis()).append(',')
                    .append(traceElapsedMillis(trace)).append(',')
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
            appendConfidence(csv, trace, "pipeline_overhead_ratio");
            appendConfidence(csv, trace, "scene_change_score");
            appendConfidence(csv, trace, "scene_change_fraction");
            appendConfidence(csv, trace, "scene_brightness_delta");
            appendConfidence(csv, trace, "camera_zoom_ratio");
            appendConfidence(csv, trace, "auto_zoom_target_roi_area_ratio");
            appendConfidence(csv, trace, "auto_zoom_lock_score");
            appendConfidence(csv, trace, "auto_zoom_lock_second_score");
            appendConfidence(csv, trace, "auto_zoom_lock_confidence");
            appendCount(csv, trace, "mz_runs");
            appendCount(csv, trace, "mz_skipped");
            appendCount(csv, trace, "invalid_plate_geometry");
            appendCount(csv, trace, "vehicle_runs");
            appendCount(csv, trace, "vehicle_skipped");
            appendCount(csv, trace, "vehicle_unavailable");
            appendCount(csv, trace, "plate_roi_runs");
            appendCount(csv, trace, "plate_full_frame_runs");
            appendCount(csv, trace, "full_frame_fallbacks");
            appendCount(csv, trace, "scene_change_candidate");
            appendCount(csv, trace, "scene_reset");
            appendCount(csv, trace, "camera_transform_in_progress");
            appendCount(csv, trace, "auto_zoom_target_roi");
            appendCount(csv, trace, "auto_zoom_lock_candidates");
            appendCount(csv, trace, "auto_zoom_lock_misses");
            appendCount(csv, trace, "plate_detections_raw");
            appendCount(csv, trace, "plate_detections_after_dedup");
            appendCount(csv, trace, "plate_detections_suppressed");
            appendCount(csv, trace, "vehicle_detections_raw");
            appendCount(csv, trace, "vehicle_detections_used");
            appendCount(csv, trace, "vehicle_detections_diagnostic");
            appendCount(csv, trace, "vehicle_detections_rejected_class");
            appendCount(csv, trace, "vehicle_regions_selected");
            appendCount(csv, trace, "source_width");
            appendCount(csv, trace, "source_height");
            appendCount(csv, trace, "camera_rotation_degrees");
            appendCount(csv, trace, "rapid_motion_frames");
            appendAttribute(csv, trace, "mt_execution_policy");
            appendAttribute(csv, trace, "mt_fallback_policy");
            appendAttribute(csv, trace, "mt_scheduler_reason");
            appendAttribute(csv, trace, "mz_state_event");
            appendAttribute(csv, trace, "target_state");
            appendAttribute(csv, trace, "target_transition_reason");
            appendConfidence(csv, trace, "tracker_quality");
            appendConfidence(csv, trace, "tracker_support_ratio");
            appendConfidence(csv, trace, "overlay_update_fps");
            appendConfidence(csv, trace, "target_roi_area_ratio");
            appendCount(csv, trace, "tracker_updates");
            appendCount(csv, trace, "tracker_failures");
            appendCount(csv, trace, "tracker_inliers");
            appendCount(csv, trace, "mt_skipped_by_tracker");
            appendCount(csv, trace, "mt_forced_by_quality");
            appendCount(csv, trace, "mt_periodic_refresh");
            appendCount(csv, trace, "mt_scheduler_queue_size");
            appendCount(csv, trace, "mt_runs_this_frame");
            appendCount(csv, trace, "mt_deferred_fallbacks");
            appendCount(csv, trace, "mt_staggered_roi_runs");
            appendCount(csv, trace, "mt_legacy_burst_runs");
            appendCount(csv, trace, "mt_legacy_same_cycle_fallbacks");
            appendCount(csv, trace, "target_lock_age_frames");
            appendCount(csv, trace, "target_roi_mt_runs");
            appendCount(csv, trace, "target_recoveries_level_1");
            appendCount(csv, trace, "target_recoveries_level_2");
            appendCount(csv, trace, "target_recoveries_level_3");
            appendCount(csv, trace, "target_recoveries_level_4");
            appendAttribute(csv, trace, "locked_track_id");
            appendCount(csv, trace, "lock_switches");
            appendCount(csv, trace, "lock_losses");
            appendCount(csv, trace, "lock_reassociations");
            appendCount(csv, trace, "frames_to_lock");
            appendCount(csv, trace, "time_to_lock_ms");
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

    private static void appendAttribute(StringBuilder csv, InferenceTrace trace, String key) {
        csv.append(',');
        String value = trace.attributes().get(key);
        if (value != null) csv.append(csvCell(value));
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
