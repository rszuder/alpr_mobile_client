package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.inference.InferenceBackend;
import com.example.alpr_v1.inference.InferenceRunResult;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.inference.TensorDataReader;
import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.vision.BitmapTensorPreprocessor;
import com.example.alpr_v1.vision.CharacterSequencePostProcessor;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.DetectionCoordinateMapper;
import com.example.alpr_v1.vision.DetectionDeduplicator;
import com.example.alpr_v1.vision.ImageSharpnessScorer;
import com.example.alpr_v1.vision.PlateRectifier;
import com.example.alpr_v1.vision.PlateQualityScorer;
import com.example.alpr_v1.vision.Point2;
import com.example.alpr_v1.vision.PreparedInput;
import com.example.alpr_v1.vision.YoloOutputSpec;
import com.example.alpr_v1.vision.YoloEndToEndDecoder;
import com.example.alpr_v1.vision.YoloRawDecoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MobileAlprEngine implements AutoCloseable {
    private static final int VEHICLE_REFRESH_FRAMES = 3;
    private static final int FULL_FRAME_FALLBACK_FRAMES = 15;
    private static final int MAX_VEHICLE_REGIONS = 2;
    private static final float VEHICLE_REGION_MARGIN = 0.18f;
    private final InstalledModel vehicleModel;
    private final InstalledModel plateModel;
    private final InstalledModel characterModel;
    private final InferenceBackend vehicleBackend;
    private final InferenceBackend plateBackend;
    private final InferenceBackend characterBackend;
    private final ModelInputSpec vehicleInputSpec;
    private final ModelInputSpec plateInputSpec;
    private final ModelInputSpec characterInputSpec;
    private final ModelOutputSpec vehicleOutputSpec;
    private final ModelOutputSpec plateOutputSpec;
    private final ModelOutputSpec characterOutputSpec;
    private final PlateTrackCoordinator trackCoordinator = new PlateTrackCoordinator();
    private final List<VehicleRoiSelector.Region> cachedVehicleRegions = new ArrayList<>();
    private long lastVehicleDetectionFrame = Long.MIN_VALUE;
    private boolean vehicleCascadeEnabled;
    private volatile boolean rapidCameraMotion;

    private static final class PlateCandidate {
        final Detection detection;
        final List<Point2> corners;
        final PlateQualityScorer.Score quality;
        final float sharpness;
        final float schedulingQuality;

        PlateCandidate(
                Detection detection,
                List<Point2> corners,
                PlateQualityScorer.Score quality,
                float sharpness
        ) {
            this.detection = detection;
            this.corners = corners;
            this.quality = quality;
            this.sharpness = sharpness;
            // Ostrość może przyspieszyć wybór lepszej kolejnej klatki, ale nie może
            // zablokować pierwszej próby MZ, która przeszła bramkę geometrii MT.
            this.schedulingQuality = Math.min(1f, quality.total + 0.20f * sharpness);
        }
    }

    MobileAlprEngine(
            ModelRegistry registry,
            AutoTuneManager autoTuneManager,
            boolean vehicleCascadeEnabled
    ) {
        this.vehicleCascadeEnabled = vehicleCascadeEnabled;
        vehicleModel = vehicleCascadeEnabled ? registry.getActive(ModelRole.VEHICLE) : null;
        plateModel = required(registry, ModelRole.PLATE);
        characterModel = required(registry, ModelRole.CHARACTER);

        ModelVariant plateVariant = autoTuneManager.chosenVariant(plateModel);
        ModelVariant characterVariant = autoTuneManager.chosenVariant(characterModel);
        plateInputSpec = plateVariant.input(plateModel.manifest().input());
        characterInputSpec = characterVariant.input(characterModel.manifest().input());
        plateOutputSpec = plateVariant.output(plateModel.manifest().output());
        characterOutputSpec = characterVariant.output(characterModel.manifest().output());
        validateDecoder(plateOutputSpec);
        validateDecoder(characterOutputSpec);

        ModelVariant vehicleVariant = null;
        ModelInputSpec resolvedVehicleInput = null;
        ModelOutputSpec resolvedVehicleOutput = null;
        if (vehicleModel != null) {
            vehicleVariant = autoTuneManager.chosenVariant(vehicleModel);
            resolvedVehicleInput = vehicleVariant.input(vehicleModel.manifest().input());
            resolvedVehicleOutput = vehicleVariant.output(vehicleModel.manifest().output());
            validateDecoder(resolvedVehicleOutput);
        }
        vehicleInputSpec = resolvedVehicleInput;
        vehicleOutputSpec = resolvedVehicleOutput;

        InferenceBackend openedVehicle = null;
        InferenceBackend openedPlate = null;
        InferenceBackend openedCharacter = null;
        try {
            if (vehicleModel != null) {
                openedVehicle = RuntimeBackendFactory.create(
                        vehicleModel, vehicleVariant, autoTuneManager.chosenProfile(vehicleModel)
                );
            }
            openedPlate = RuntimeBackendFactory.create(
                    plateModel, plateVariant, autoTuneManager.chosenProfile(plateModel)
            );
            openedCharacter = RuntimeBackendFactory.create(
                    characterModel, characterVariant, autoTuneManager.chosenProfile(characterModel)
            );
        } catch (RuntimeException error) {
            closeQuietly(openedCharacter);
            closeQuietly(openedPlate);
            closeQuietly(openedVehicle);
            throw error;
        }
        vehicleBackend = openedVehicle;
        plateBackend = openedPlate;
        characterBackend = openedCharacter;
    }

    void setRecognitionProfile(RecognitionProfile profile) {
        trackCoordinator.setProfile(profile);
    }

    void resetTracking() {
        trackCoordinator.reset();
        cachedVehicleRegions.clear();
        lastVehicleDetectionFrame = Long.MIN_VALUE;
    }

    void setRapidCameraMotion(boolean rapid) {
        rapidCameraMotion = rapid;
    }

    PipelineResult run(Bitmap frame, InferenceTrace trace) {
        List<OverlayItem> overlays = new ArrayList<>();
        List<VehicleRoiSelector.Region> plateRegions = new ArrayList<>();
        boolean useVehicleRegions = vehicleCascadeEnabled && vehicleBackend != null;
        if (useVehicleRegions) {
            boolean refreshVehicles = cachedVehicleRegions.isEmpty()
                    || rapidCameraMotion
                    || trace.frameId() - lastVehicleDetectionFrame >= VEHICLE_REFRESH_FRAMES;
            if (refreshVehicles) {
                cachedVehicleRegions.clear();
                cachedVehicleRegions.addAll(detectVehicleRegions(frame, trace));
                lastVehicleDetectionFrame = trace.frameId();
                trace.putCount("vehicle_runs", 1);
            } else {
                trace.putCount("vehicle_skipped", 1);
            }
            if (rapidCameraMotion) trace.putCount("rapid_motion_frames", 1);
            plateRegions.addAll(cachedVehicleRegions);
        } else if (vehicleCascadeEnabled) {
            trace.putCount("vehicle_unavailable", 1);
        }

        boolean roiPass = !plateRegions.isEmpty();
        if (!roiPass) plateRegions.add(fullFrameRegion(frame));
        long[] plateDurations = new long[3];
        List<Detection> plates = new ArrayList<>();
        for (VehicleRoiSelector.Region region : plateRegions) {
            plates.addAll(detectPlates(frame, region, plateDurations));
        }
        trace.putCount("plate_roi_runs", roiPass ? plateRegions.size() : 0);
        trace.putCount("plate_full_frame_runs", roiPass ? 0 : 1);

        boolean periodicFallback = roiPass
                && trace.frameId() % FULL_FRAME_FALLBACK_FRAMES == 0;
        if (roiPass && (plates.isEmpty() || periodicFallback)) {
            plates.addAll(detectPlates(frame, fullFrameRegion(frame), plateDurations));
            trace.putCount("plate_full_frame_runs", 1);
            trace.putCount("full_frame_fallbacks", 1);
            if (plates.isEmpty()) {
                cachedVehicleRegions.clear();
                lastVehicleDetectionFrame = Long.MIN_VALUE;
            }
        }
        trace.putDurationNanos("plate_preprocess", plateDurations[0]);
        trace.putDurationNanos("plate_inference", plateDurations[1]);
        trace.putDurationNanos("plate_postprocess", plateDurations[2]);

        // Deduplikacja po połączeniu wyników kilku ROI oraz fallbacku pełnoklatkowego.
        plates = new ArrayList<>(DetectionDeduplicator.suppress(
                plates, plateOutputSpec.iouThreshold(), 0.82f, false
        ));
        if (plates.isEmpty()) {
            trackCoordinator.update(
                    Collections.emptyList(), trace.frameId(), SystemClock.elapsedRealtimeNanos()
            );
            trace.finish("no_plate", "");
            return new PipelineResult(
                    "no_plate", "Nie wykryto tablicy", "", 0,
                    overlays, frame.getWidth(), frame.getHeight()
            );
        }
        trace.putConfidence("plate", plates.get(0).confidence);

        List<PlateCandidate> candidates = new ArrayList<>();
        List<PlateTrackCoordinator.Observation> trackObservations = new ArrayList<>();
        float maximumFitScore = 0f;
        float maximumSharpness = 0f;
        for (int plateIndex = 0; plateIndex < plates.size(); plateIndex++) {
            Detection sourceDetection = plates.get(plateIndex);
            List<Point2> sourceCorners = new ArrayList<>(
                    sourceDetection.keypoints.subList(
                            0, Math.min(4, sourceDetection.keypoints.size())
                    )
            );
            PlateQualityScorer.Score quality = PlateQualityScorer.compute(
                    sourceDetection, sourceCorners, frame.getWidth(), frame.getHeight()
            );
            boolean cornersInsideFrame = cornersInsideFrame(
                    sourceCorners, frame.getWidth(), frame.getHeight()
            );
            float sharpness = ImageSharpnessScorer.score(frame, sourceDetection);
            maximumFitScore = Math.max(maximumFitScore, quality.total);
            maximumSharpness = Math.max(maximumSharpness, sharpness);
            PlateCandidate candidate = new PlateCandidate(
                    sourceDetection, sourceCorners, quality, sharpness
            );
            candidates.add(candidate);
            trackObservations.add(new PlateTrackCoordinator.Observation(
                    plateIndex,
                    new com.example.alpr_v1.tracking.MotionBoxTracker.Box(
                            sourceDetection.left / frame.getWidth(),
                            sourceDetection.top / frame.getHeight(),
                            sourceDetection.right / frame.getWidth(),
                            sourceDetection.bottom / frame.getHeight()
                    ),
                    candidate.schedulingQuality,
                    quality.validQuad && cornersInsideFrame
            ));
        }
        trace.putConfidence("plate_fit", maximumFitScore);
        trace.putConfidence("plate_sharpness", maximumSharpness);
        List<PlateTrackCoordinator.Decision> decisions = trackCoordinator.update(
                trackObservations,
                trace.frameId(),
                SystemClock.elapsedRealtimeNanos()
        );

        List<PlateRecognition> recognitions = new ArrayList<>();
        List<PlateObservation> plateObservations = new ArrayList<>();
        long rectificationNanos = 0L;
        long characterPreprocessNanos = 0L;
        long characterInferenceNanos = 0L;
        long characterPostprocessNanos = 0L;
        double charactersMinimum = 1.0;
        double charactersSum = 0.0;
        int characterCount = 0;
        int characterRuns = 0;
        int invalidGeometryCount = 0;

        for (PlateTrackCoordinator.Decision decision : decisions) {
            if (decision.sourceIndex < 0 || decision.sourceIndex >= candidates.size()) continue;
            PlateCandidate candidate = candidates.get(decision.sourceIndex);
            TemporalCharacterAggregator.Result trackResult = decision.currentResult;
            Bitmap observationBitmap = null;
            List<PlateCharacter> observedCharacters = Collections.emptyList();
            CropInferenceTiming cropTiming = null;
            long cropRectificationNanos = 0L;
            long cropCharacterPreprocessNanos = 0L;
            long cropCharacterInferenceNanos = 0L;
            long cropCharacterPostprocessNanos = 0L;
            boolean validGeometry = candidate.quality.validQuad && cornersInsideFrame(
                    candidate.corners, frame.getWidth(), frame.getHeight()
            );
            if (!validGeometry) invalidGeometryCount++;

            if (decision.recognize) {
                long started = SystemClock.elapsedRealtimeNanos();
                Bitmap rectified = PlateRectifier.rectify(frame, candidate.corners);
                cropRectificationNanos = SystemClock.elapsedRealtimeNanos() - started;
                rectificationNanos += cropRectificationNanos;
                try {
                    started = SystemClock.elapsedRealtimeNanos();
                    PreparedInput characterInput = BitmapTensorPreprocessor.prepare(
                            rectified, characterInputSpec, characterBackend.inputInfo()
                    );
                    cropCharacterPreprocessNanos = SystemClock.elapsedRealtimeNanos() - started;
                    characterPreprocessNanos += cropCharacterPreprocessNanos;

                    started = SystemClock.elapsedRealtimeNanos();
                    InferenceRunResult characterRun = characterBackend.run(characterInput.buffer);
                    cropCharacterInferenceNanos = SystemClock.elapsedRealtimeNanos() - started;
                    characterInferenceNanos += cropCharacterInferenceNanos;
                    characterRuns++;

                    started = SystemClock.elapsedRealtimeNanos();
                    List<Detection> characters = characterCandidates(
                            characterRun,
                            decision.expectedCharacterCount
                    );
                    cropCharacterPostprocessNanos = SystemClock.elapsedRealtimeNanos() - started;
                    characterPostprocessNanos += cropCharacterPostprocessNanos;
                    for (Detection character : characters) {
                        charactersMinimum = Math.min(charactersMinimum, character.confidence);
                        charactersSum += character.confidence;
                        characterCount++;
                    }
                    trackResult = trackCoordinator.recordRecognition(
                            decision.trackId,
                            candidate.schedulingQuality,
                            trace.frameId(),
                            characters,
                            characterModel.manifest().labels()
                    );
                    List<Detection> sourceCharacters = new ArrayList<>();
                    for (Detection character : characters) {
                        sourceCharacters.add(DetectionCoordinateMapper.toSource(
                                character, characterInput, 0, 0
                        ));
                    }
                    observedCharacters = plateCharacters(
                            sourceCharacters,
                            characterModel.manifest().labels(),
                            rectified.getWidth(),
                            rectified.getHeight()
                    );
                    observationBitmap = rectified.copy(Bitmap.Config.ARGB_8888, false);
                    cropTiming = new CropInferenceTiming(
                            trace.frameId(),
                            trace.durationNanos("camera_conversion"),
                            trace.durationNanos("vehicle_preprocess")
                                    + trace.durationNanos("vehicle_inference")
                                    + trace.durationNanos("vehicle_postprocess"),
                            trace.durationNanos("plate_preprocess")
                                    + trace.durationNanos("plate_inference")
                                    + trace.durationNanos("plate_postprocess"),
                            cropRectificationNanos,
                            cropCharacterPreprocessNanos,
                            cropCharacterInferenceNanos,
                            cropCharacterPostprocessNanos,
                            trace.elapsedSinceStageStart("total")
                    );
                } finally {
                    rectified.recycle();
                }
            }

            String visibleText = "";
            if (trackResult != null && !trackResult.text.isEmpty()) {
                visibleText = trackResult.text;
                recognitions.add(new PlateRecognition(
                        trackResult.text,
                        trackResult.confidence,
                        trackResult.stable,
                        trackResult.observations
                ));
            }
            plateObservations.add(new PlateObservation(
                    decision.trackId,
                    observationBitmap,
                    visibleText,
                    candidate.detection.confidence,
                    trackResult == null ? 0.0 : trackResult.confidence,
                    trackResult != null && trackResult.stable,
                    trackResult == null ? 0 : trackResult.observations,
                    observedCharacters,
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtimeNanos(),
                    candidate.sharpness,
                    cropTiming
            ));
            overlays.add(overlayBox(
                    frame,
                    candidate.detection.left,
                    candidate.detection.top,
                    candidate.detection.right,
                    candidate.detection.bottom,
                    candidate.corners,
                    visibleText.isEmpty() ? "tablica" : visibleText,
                    candidate.detection.confidence
            ));
        }

        trace.putDurationNanos("rectification", rectificationNanos);
        trace.putDurationNanos("character_preprocess", characterPreprocessNanos);
        trace.putDurationNanos("character_inference", characterInferenceNanos);
        trace.putDurationNanos("character_postprocess", characterPostprocessNanos);
        trace.putCount("mz_runs", characterRuns);
        trace.putCount("mz_skipped", Math.max(0, plates.size() - characterRuns));
        trace.putCount("invalid_plate_geometry", invalidGeometryCount);
        if (recognitions.isEmpty()) {
            trace.finish("stabilizing", "");
            return new PipelineResult(
                    "stabilizing",
                    invalidGeometryCount == plates.size()
                            ? "Wykryto tablicę, ale geometria narożników jest niestabilna"
                            : String.format(
                                    Locale.ROOT,
                                    "Śledzę %d tablic; próby MZ w tej klatce: %d",
                                    plates.size(), characterRuns
                            ),
                    recognitions, overlays, frame.getWidth(), frame.getHeight(),
                    plateObservations
            );
        }

        if (characterCount > 0) {
            trace.putConfidence("characters_min", charactersMinimum);
            trace.putConfidence("characters_mean", charactersSum / characterCount);
        }
        StringBuilder traceText = new StringBuilder();
        for (PlateRecognition recognition : recognitions) {
            if (traceText.length() > 0) traceText.append(" | ");
            traceText.append(recognition.text);
        }
        boolean hasConfirmed = false;
        for (PlateRecognition recognition : recognitions) {
            if (recognition.confirmed) {
                hasConfirmed = true;
                break;
            }
        }
        String resultStatus = hasConfirmed ? "recognized" : "preliminary";
        trace.finish(resultStatus, traceText.toString());
        return new PipelineResult(
                resultStatus,
                String.format(
                        Locale.ROOT,
                        hasConfirmed
                                ? "Potwierdzone odczyty: %d/%d; MZ w tej klatce: %d"
                                : "Wynik wstępny: %d/%d; MZ w tej klatce: %d",
                        recognitions.size(), plates.size(), characterRuns
                ),
                recognitions, overlays, frame.getWidth(), frame.getHeight(),
                plateObservations
        );
    }

    private static List<PlateCharacter> plateCharacters(
            List<Detection> detections,
            List<String> labels,
            int width,
            int height
    ) {
        List<PlateCharacter> result = new ArrayList<>();
        for (Detection detection : detections) {
            if (detection.classId < 0 || detection.classId >= labels.size()) continue;
            result.add(new PlateCharacter(
                    labels.get(detection.classId),
                    detection.confidence,
                    detection.left / Math.max(1f, width),
                    detection.top / Math.max(1f, height),
                    detection.right / Math.max(1f, width),
                    detection.bottom / Math.max(1f, height)
            ));
        }
        return result;
    }

    private List<VehicleRoiSelector.Region> detectVehicleRegions(
            Bitmap frame,
            InferenceTrace trace
    ) {
        trace.start("vehicle_preprocess");
        PreparedInput input = BitmapTensorPreprocessor.prepare(
                frame, vehicleInputSpec, vehicleBackend.inputInfo()
        );
        trace.stop("vehicle_preprocess");

        trace.start("vehicle_inference");
        InferenceRunResult run = vehicleBackend.run(input.buffer);
        trace.stop("vehicle_inference");

        trace.start("vehicle_postprocess");
        List<Detection> vehicles = new ArrayList<>();
        for (Detection detection : decodeFirstOutput(run, vehicleInputSpec, vehicleOutputSpec)) {
            vehicles.add(DetectionCoordinateMapper.toSource(detection, input, 0, 0));
        }
        List<VehicleRoiSelector.Region> regions = VehicleRoiSelector.select(
                vehicles,
                frame.getWidth(),
                frame.getHeight(),
                MAX_VEHICLE_REGIONS,
                rapidCameraMotion ? 0.28f : VEHICLE_REGION_MARGIN,
                vehicleOutputSpec.iouThreshold()
        );
        double maximumConfidence = 0.0;
        long totalArea = 0L;
        for (Detection vehicle : vehicles) {
            maximumConfidence = Math.max(maximumConfidence, vehicle.confidence);
        }
        for (VehicleRoiSelector.Region region : regions) totalArea += region.area();
        trace.stop("vehicle_postprocess");
        if (maximumConfidence > 0.0) trace.putConfidence("vehicle", maximumConfidence);
        if (!regions.isEmpty()) {
            trace.putConfidence(
                    "vehicle_roi_area_ratio",
                    Math.min(1.0, totalArea / (double) ((long) frame.getWidth() * frame.getHeight()))
            );
        }
        return regions;
    }

    private List<Detection> detectPlates(
            Bitmap frame,
            VehicleRoiSelector.Region region,
            long[] durations
    ) {
        boolean fullFrame = region.left == 0 && region.top == 0
                && region.right == frame.getWidth() && region.bottom == frame.getHeight();
        Bitmap inputBitmap = fullFrame
                ? frame
                : Bitmap.createBitmap(frame, region.left, region.top, region.width(), region.height());
        try {
            long started = SystemClock.elapsedRealtimeNanos();
            PreparedInput input = BitmapTensorPreprocessor.prepare(
                    inputBitmap, plateInputSpec, plateBackend.inputInfo()
            );
            durations[0] += SystemClock.elapsedRealtimeNanos() - started;

            started = SystemClock.elapsedRealtimeNanos();
            InferenceRunResult run = plateBackend.run(input.buffer);
            durations[1] += SystemClock.elapsedRealtimeNanos() - started;

            started = SystemClock.elapsedRealtimeNanos();
            List<Detection> result = new ArrayList<>();
            for (Detection detection : decodeFirstOutput(run, plateInputSpec, plateOutputSpec)) {
                if (detection.keypoints.size() >= 4) {
                    result.add(DetectionCoordinateMapper.toSource(
                            detection, input, region.left, region.top
                    ));
                }
            }
            durations[2] += SystemClock.elapsedRealtimeNanos() - started;
            return result;
        } finally {
            if (!fullFrame) inputBitmap.recycle();
        }
    }

    private static VehicleRoiSelector.Region fullFrameRegion(Bitmap frame) {
        return VehicleRoiSelector.fullFrame(frame.getWidth(), frame.getHeight());
    }

    private static boolean cornersInsideFrame(
            List<Point2> corners,
            int width,
            int height
    ) {
        if (corners == null || corners.size() != 4) return false;
        for (Point2 point : corners) {
            if (point.x < 0f || point.x > width || point.y < 0f || point.y > height) {
                return false;
            }
        }
        return true;
    }

    private List<Detection> characterCandidates(
            InferenceRunResult run,
            int expectedCharacterCount
    ) {
        float normalThreshold = characterOutputSpec.confidenceThreshold();
        float candidateFloor = Math.min(normalThreshold, 0.10f);
        List<Detection> candidates = decodeFirstOutput(
                run, characterInputSpec, characterOutputSpec, candidateFloor
        );
        List<Detection> normal = new ArrayList<>();
        for (Detection candidate : candidates) {
            if (candidate.confidence >= normalThreshold) normal.add(candidate);
        }
        List<Detection> selected = CharacterSequencePostProcessor.process(
                normal, expectedCharacterCount
        );
        if (expectedCharacterCount > 0 && selected.size() < expectedCharacterCount) {
            List<Detection> recall = CharacterSequencePostProcessor.process(
                    candidates, expectedCharacterCount
            );
            if (Math.abs(recall.size() - expectedCharacterCount)
                    < Math.abs(selected.size() - expectedCharacterCount)) {
                selected = recall;
            }
        }
        return selected;
    }

    private static List<Detection> decodeFirstOutput(
            InferenceRunResult run,
            ModelInputSpec inputSpec,
            ModelOutputSpec outputSpec
    ) {
        return decodeFirstOutput(run, inputSpec, outputSpec, outputSpec.confidenceThreshold());
    }

    private static List<Detection> decodeFirstOutput(
            InferenceRunResult run,
            ModelInputSpec inputSpec,
            ModelOutputSpec outputSpec,
            float confidenceThreshold
    ) {
        Map.Entry<Integer, ByteBuffer> entry = run.outputs().entrySet().iterator().next();
        TensorInfo info = run.tensorInfo().get(entry.getKey());
        int[] shape = info.shape;
        if (shape.length < 2) throw new IllegalArgumentException("Wyjście YOLO musi mieć co najmniej 2 wymiary");
        for (int i = 0; i < shape.length - 2; i++) {
            if (shape[i] != 1) throw new IllegalArgumentException("Obsługiwany jest wyłącznie batch size 1");
        }
        int first = shape[shape.length - 2];
        int second = shape[shape.length - 1];
        float[] values = TensorDataReader.toFloatArray(entry.getValue(), info);
        YoloOutputSpec yoloSpec = new YoloOutputSpec(
                outputSpec.classCount(), outputSpec.keypointCount(), outputSpec.keypointDimensions(),
                outputSpec.hasObjectness(),
                outputSpec.channelsFirst(), outputSpec.normalizedCoordinates(),
                inputSpec.width(), inputSpec.height(),
                confidenceThreshold, outputSpec.iouThreshold(),
                outputSpec.scoreIndex(), outputSpec.classIndex()
        );
        if (isEndToEndDecoder(outputSpec.decoder())) {
            return YoloEndToEndDecoder.decode(values, first, second, yoloSpec);
        }
        return YoloRawDecoder.decode(values, first, second, yoloSpec);
    }

    private static OverlayItem overlayBox(
            Bitmap frame,
            float left,
            float top,
            float right,
            float bottom,
            List<Point2> corners,
            String name,
            float confidence
    ) {
        float width = frame.getWidth();
        float height = frame.getHeight();
        List<PointF> points = new ArrayList<>();
        for (Point2 point : corners) points.add(new PointF(point.x / width, point.y / height));
        return new OverlayItem(
                new RectF(left / width, top / height, right / width, bottom / height),
                points,
                String.format(Locale.ROOT, "%s %.0f%%", name, confidence * 100)
        );
    }

    private static InstalledModel required(ModelRegistry registry, ModelRole role) {
        InstalledModel model = registry.getActive(role);
        if (model == null) throw new IllegalStateException("Brak aktywnego modelu: " + role.wireName());
        return model;
    }

    private static void validateDecoder(ModelOutputSpec outputSpec) {
        if (outputSpec.nmsInGraph()) {
            throw new IllegalArgumentException("Dekoder v1 wymaga eksportu bez NMS w grafie");
        }
        String decoder = outputSpec.decoder();
        if (!decoder.equals("ultralytics_yolo_raw_v1")
                && !decoder.equals("ultralytics_pose_raw_v1")
                && !decoder.equals("ultralytics_detect_raw_v1")
                && !isEndToEndDecoder(decoder)) {
            throw new IllegalArgumentException("Nieobsługiwany dekoder: " + decoder);
        }
    }

    private static boolean isEndToEndDecoder(String decoder) {
        return decoder.equals("ultralytics_pose_end2end_v1")
                || decoder.equals("ultralytics_detect_end2end_v1")
                || decoder.equals("ultralytics_yolo_end2end_v1");
    }

    private static void closeQuietly(InferenceBackend backend) {
        if (backend != null) backend.close();
    }

    @Override
    public void close() {
        characterBackend.close();
        plateBackend.close();
        closeQuietly(vehicleBackend);
    }
}
