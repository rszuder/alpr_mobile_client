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
import com.example.alpr_v1.vision.NonMaxSuppression;
import com.example.alpr_v1.vision.Point2;
import com.example.alpr_v1.vision.PreparedInput;
import com.example.alpr_v1.vision.YoloOutputSpec;
import com.example.alpr_v1.vision.YoloEndToEndDecoder;
import com.example.alpr_v1.vision.YoloRawDecoder;
import com.example.alpr_v1.vision.SceneChangeDetector;
import com.example.alpr_v1.vision.ReadingOrderResolver;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class MobileAlprEngine implements AutoCloseable {
    static final class ProcessingCancelledException extends RuntimeException {
        ProcessingCancelledException() {
            super("scene_superseded", null, false, false);
        }
    }
    private static final int VEHICLE_REFRESH_FRAMES = 3;
    private static final float VEHICLE_REGION_MARGIN = 0.18f;
    private final InstalledModel vehicleModel;
    private final InstalledModel plateModel;
    private final InstalledModel characterModel;

    private final RoiBudgetPolicy roiBudgetPolicy;
    private final MtExecutionPolicy mtExecutionPolicy;
    private final MtFallbackPolicy mtFallbackPolicy;
    private final InferenceBackend vehicleBackend;
    private final InferenceBackend plateBackend;
    private final InferenceBackend characterBackend;
    private final ModelInputSpec vehicleInputSpec;
    private final ModelInputSpec plateInputSpec;
    private final ModelInputSpec characterInputSpec;
    private final ModelOutputSpec vehicleOutputSpec;
    private final ModelOutputSpec plateOutputSpec;
    private final ModelOutputSpec characterOutputSpec;
    private final Set<Integer> vehicleClassIds;
    private final PlateTrackCoordinator trackCoordinator = new PlateTrackCoordinator();

    private final SceneChangeDetector sceneChangeDetector = new SceneChangeDetector();
    private final List<VehicleRoiSelector.Region> cachedVehicleRegions = new ArrayList<>();
    private final List<Detection> cachedVehicleDetections = new ArrayList<>();
    private final Map<Long, float[]> plateAppearanceByTrack = new java.util.HashMap<>();
    private final AutoZoomTargetLock autoZoomTargetLock = new AutoZoomTargetLock();
    private final MtInferenceScheduler mtInferenceScheduler = new MtInferenceScheduler();
    private long lastVehicleDetectionFrame = Long.MIN_VALUE;
    private long appliedAutoZoomTargetLockRevision = Long.MIN_VALUE;
    private int reportedTargetLockSwitches;
    private int reportedTargetLockLosses;
    private int reportedTargetLockReassociations;
    private long reportedTargetLockRevision;
    private volatile boolean rapidCameraMotion;
    private volatile boolean cameraTransformInProgress;
    private volatile TargetSnapshot targetSnapshot = TargetSnapshot.searching();

    private static final class VehicleDetectionResult {
        final List<Detection> vehicles;
        final List<VehicleRoiSelector.Region> selectedRegions;

        VehicleDetectionResult(
                List<Detection> vehicles,
                List<VehicleRoiSelector.Region> selectedRegions
        ) {
            this.vehicles = vehicles;
            this.selectedRegions = selectedRegions;
        }
    }

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
            RoiBudgetPolicy roiBudgetPolicy,
            MtExecutionPolicy mtExecutionPolicy,
            MtFallbackPolicy mtFallbackPolicy
    ) {
        this.roiBudgetPolicy = roiBudgetPolicy == null
                ? RoiBudgetPolicy.FULL_FRAME
                : roiBudgetPolicy;
        this.mtExecutionPolicy = mtExecutionPolicy == null
                ? MtExecutionPolicy.LIVE_STAGGERED : mtExecutionPolicy;
        this.mtFallbackPolicy = mtFallbackPolicy == null
                ? MtFallbackPolicy.DEFERRED : mtFallbackPolicy;

        vehicleModel = this.roiBudgetPolicy.usesVehicleCascade()
                ? registry.getActive(ModelRole.VEHICLE)
                : null;
        plateModel = required(registry, ModelRole.PLATE);
        characterModel = required(registry, ModelRole.CHARACTER);

        vehicleClassIds = vehicleModel == null
                ? Collections.emptySet()
                : resolveVehicleClassIds(vehicleModel.manifest().labels());

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

    private void resetSceneDependentState() {
        trackCoordinator.reset();
        cachedVehicleRegions.clear();
        cachedVehicleDetections.clear();
        plateAppearanceByTrack.clear();
        autoZoomTargetLock.clear();
        mtInferenceScheduler.reset();
        targetSnapshot = TargetSnapshot.searching();
        appliedAutoZoomTargetLockRevision = Long.MIN_VALUE;
        reportedTargetLockSwitches = 0;
        reportedTargetLockLosses = 0;
        reportedTargetLockReassociations = 0;
        reportedTargetLockRevision = 0L;
        lastVehicleDetectionFrame = Long.MIN_VALUE;
    }

    void resetTracking() {
        resetSceneDependentState();
        sceneChangeDetector.reset();
    }

    void setRapidCameraMotion(boolean rapid) {
        rapidCameraMotion = rapid;
    }

    void setCameraTransformInProgress(boolean inProgress) {
        cameraTransformInProgress = inProgress;
    }

    void setTargetSnapshot(TargetSnapshot snapshot) {
        targetSnapshot = snapshot == null ? TargetSnapshot.searching() : snapshot;
    }

    void setAutoZoomTargetLock(
            boolean active,
            long revision,
            long targetTrackId,
            float left,
            float top,
            float right,
            float bottom
    ) {
        AutoZoomTargetLock.Box prediction =
                new AutoZoomTargetLock.Box(left, top, right, bottom);
        if (revision != appliedAutoZoomTargetLockRevision) {
            appliedAutoZoomTargetLockRevision = revision;
            if (active) {
                autoZoomTargetLock.begin(
                        prediction,
                        plateAppearanceByTrack.get(targetTrackId)
                );
                mtInferenceScheduler.forceRefresh("target_lock_acquired");
            } else {
                autoZoomTargetLock.clear();
            }
        } else if (active) {
            autoZoomTargetLock.updatePrediction(prediction);
        } else {
            autoZoomTargetLock.clear();
        }
    }

    void resetSceneDetectorReference() {
        sceneChangeDetector.reset();
    }

    void applyCameraZoomTransform(float relativeRatio) {
        trackCoordinator.applyCameraZoomTransform(relativeRatio);
        mtInferenceScheduler.forceRefresh("zoom_transform");
        if (relativeRatio > 1.001f) {
            trackCoordinator.requestFreshRecognitionAfterZoom();
        }
    }

    PipelineResult run(
            Bitmap frame,
            InferenceTrace trace,
            AlprPipeline.PlateDetectionCallback plateDetectionCallback
    ) {
        return run(frame, trace, plateDetectionCallback, () -> false);
    }

    PipelineResult run(
            Bitmap frame,
            InferenceTrace trace,
            AlprPipeline.PlateDetectionCallback plateDetectionCallback,
            BooleanSupplier cancellationRequested
    ) {
        SceneChangeDetector.Result scene =
                sceneChangeDetector.update(frame);

        boolean effectiveSceneChanged =
                scene.sceneChanged
                        && !cameraTransformInProgress;

        trace.putConfidence(
                "scene_change_score",
                scene.score
        );

        trace.putConfidence(
                "scene_change_fraction",
                scene.changedFraction
        );

        trace.putConfidence(
                "scene_brightness_delta",
                scene.brightnessDelta
        );

        trace.putCount(
                "scene_change_candidate",
                scene.rawCandidate ? 1 : 0
        );

        android.util.Log.d(
                "ALPR_SCENE",
                String.format(
                        Locale.ROOT,
                        "frame=%d changed=%s candidate=%s armed=%s "
                                + "score=%.3f fraction=%.3f brightness=%.3f size=%dx%d",
                        trace.frameId(),
                        effectiveSceneChanged,
                        scene.rawCandidate,
                        scene.armed,
                        scene.score,
                        scene.changedFraction,
                        scene.brightnessDelta,
                        frame.getWidth(),
                        frame.getHeight()
                )
        );

        trace.putCount(
                "camera_transform_in_progress",
                cameraTransformInProgress ? 1 : 0
        );
        cancelIfRequested(cancellationRequested);

        if (effectiveSceneChanged) {

            resetSceneDependentState();

            trace.putCount("scene_reset", 1);
            trace.putAttribute("mz_state_event", "SCENE_RESET");

            android.util.Log.d(
                    "ALPR_SCENE",
                    "RESET frame=" + trace.frameId()
                            + " -> wyczyszczono tracki MT/MZ i cache MP"
            );
        } else {
            trace.putCount("scene_reset", 0);
        }

        List<OverlayItem> overlays = new ArrayList<>();
        List<VehicleRoiSelector.Region> vehicleRegions = new ArrayList<>();
        boolean liveExecution =
                mtExecutionPolicy == MtExecutionPolicy.LIVE_STAGGERED;
        trace.putAttribute("mt_execution_policy", mtExecutionPolicy.wireName());
        trace.putAttribute("mt_fallback_policy", mtFallbackPolicy.wireName());
        boolean targetRoiActive = autoZoomTargetLock.active();
        TargetSnapshot liveTarget = targetSnapshot == null
                ? TargetSnapshot.searching() : targetSnapshot;
        boolean targetLostSignal = liveTarget.state == TargetSnapshot.State.LOST;
        if (targetLostSignal) {
            trackCoordinator.onMtEvent(
                    PlateTrackCoordinator.MtStateEvent.TARGET_LOST,
                    SystemClock.elapsedRealtimeNanos()
            );
        }
        boolean trackedGeometryAvailable = !effectiveSceneChanged
                && liveTarget.trackId > 0L
                && liveTarget.overlayItem != null;
        boolean anyTargetGeometry = liveExecution
                && (trackedGeometryAvailable || targetRoiActive);
        boolean vehicleRecoveryRequested =
                mtInferenceScheduler.requiresVehicleRecovery();

        MtInferenceScheduler.Decision mtDecision = null;
        if (anyTargetGeometry && !vehicleRecoveryRequested) {
            mtDecision = mtInferenceScheduler.plan(new MtInferenceScheduler.Input(
                    trace.frameId(),
                    true,
                    trackedGeometryAvailable
                            ? liveTarget.state : TargetSnapshot.State.DEGRADED,
                    trackedGeometryAvailable ? liveTarget.trackingQuality : 0f,
                    trackedGeometryAvailable ? liveTarget.consecutiveFailures : 1,
                    effectiveSceneChanged,
                    rapidCameraMotion,
                    cameraTransformInProgress,
                    0
            ));
            recordSchedulerDecision(trace, mtDecision, liveTarget);
            if (!mtDecision.runsMt()) {
                trackCoordinator.onMtEvent(
                        PlateTrackCoordinator.MtStateEvent.NO_MT_RUN,
                        SystemClock.elapsedRealtimeNanos()
                );
                trace.putAttribute("mz_state_event", "NO_MT_RUN");
                trace.putCount("mt_skipped_by_tracker", 1);
                trace.putCount("mt_runs_this_frame", 0);
                addVehicleDiagnostics(
                        overlays,
                        cachedVehicleDetections,
                        cachedVehicleRegions,
                        frame,
                        roiBudgetPolicy
                );
                if (liveTarget.overlayItem != null) {
                    overlays.add(carriedTargetOverlay(liveTarget.overlayItem));
                }
                trace.finish("tracking", "");
                return new PipelineResult(
                        "tracking",
                        "Śledzenie celu — MT pominięte",
                        "",
                        0.0,
                        overlays,
                        frame.getWidth(),
                        frame.getHeight(),
                        effectiveSceneChanged
                );
            }
        }

        boolean useVehicleRegions = (!anyTargetGeometry || vehicleRecoveryRequested)
                && roiBudgetPolicy.usesVehicleCascade()
                && vehicleBackend != null;
        if (useVehicleRegions) {
            boolean refreshVehicles = cachedVehicleRegions.isEmpty()
                    || rapidCameraMotion
                    || trace.frameId() - lastVehicleDetectionFrame >= VEHICLE_REFRESH_FRAMES;
            if (refreshVehicles) {
                VehicleDetectionResult vehicleResult =
                        detectVehicleRegions(frame, trace);
                cachedVehicleRegions.clear();
                cachedVehicleRegions.addAll(vehicleResult.selectedRegions);
                cachedVehicleDetections.clear();
                cachedVehicleDetections.addAll(vehicleResult.vehicles);
                lastVehicleDetectionFrame = trace.frameId();
                trace.putCount("vehicle_runs", 1);
            } else {
                trace.putCount("vehicle_skipped", 1);
            }
            if (rapidCameraMotion) trace.putCount("rapid_motion_frames", 1);
            vehicleRegions.addAll(cachedVehicleRegions);
            addVehicleDiagnostics(
                    overlays,
                    cachedVehicleDetections,
                    cachedVehicleRegions,
                    frame,
                    roiBudgetPolicy
            );
        } else if ((!anyTargetGeometry || vehicleRecoveryRequested)
                && roiBudgetPolicy.usesVehicleCascade()) {
            trace.putCount("vehicle_unavailable", 1);
        } else if (anyTargetGeometry) {
            trace.putCount("vehicle_skipped", 1);
            addVehicleDiagnostics(
                    overlays,
                    cachedVehicleDetections,
                    cachedVehicleRegions,
                    frame,
                    roiBudgetPolicy
            );
        }
        cancelIfRequested(cancellationRequested);

        long[] plateDurations = new long[3];
        List<Detection> plates = new ArrayList<>();
        if (!liveExecution) {
            int roiRuns = 0;
            int fullFrameRuns = 0;
            trace.putAttribute("mt_scheduler_reason", "experiment_legacy");
            trace.putAttribute("target_state", "EXPERIMENT_LEGACY");

            if (roiBudgetPolicy == RoiBudgetPolicy.FULL_FRAME) {
                plates.addAll(detectPlates(
                        frame,
                        fullFrameRegion(frame),
                        plateDurations
                ));
                fullFrameRuns++;
            } else {
                for (VehicleRoiSelector.Region region : vehicleRegions) {
                    plates.addAll(detectPlates(frame, region, plateDurations));
                    roiRuns++;
                    cancelIfRequested(cancellationRequested);
                }
                if (plates.isEmpty()
                        && mtFallbackPolicy == MtFallbackPolicy.SAME_CYCLE) {
                    plates.addAll(detectPlates(
                            frame,
                            fullFrameRegion(frame),
                            plateDurations
                    ));
                    fullFrameRuns++;
                    trace.putCount("full_frame_fallbacks", 1);
                }
            }
            trace.putCount("mt_runs_this_frame", roiRuns + fullFrameRuns);
            trace.putCount("plate_roi_runs", roiRuns);
            trace.putCount("plate_full_frame_runs", fullFrameRuns);
            trace.putCount("mt_legacy_burst_runs", roiRuns);
            trace.putCount("mt_legacy_same_cycle_fallbacks", fullFrameRuns > 0
                    && roiBudgetPolicy != RoiBudgetPolicy.FULL_FRAME ? 1 : 0);
        } else {
            if (mtDecision == null) {
                mtDecision = mtInferenceScheduler.plan(new MtInferenceScheduler.Input(
                        trace.frameId(),
                        anyTargetGeometry,
                        trackedGeometryAvailable
                                ? liveTarget.state : TargetSnapshot.State.SEARCHING,
                        trackedGeometryAvailable ? liveTarget.trackingQuality : 0f,
                        trackedGeometryAvailable ? liveTarget.consecutiveFailures : 0,
                        effectiveSceneChanged,
                        rapidCameraMotion,
                        cameraTransformInProgress,
                        vehicleRegions.size()
                ));
                recordSchedulerDecision(trace, mtDecision, liveTarget);
            }

            VehicleRoiSelector.Region scheduledRegion;
            switch (mtDecision.kind) {
                case TARGET_ROI:
                    scheduledRegion = targetRegion(
                            frame,
                            liveTarget,
                            targetRoiActive,
                            mtDecision.targetMargin
                    );
                    overlays.add(roiOverlay(
                            scheduledRegion,
                            frame,
                            targetRoiActive ? "ROI AUTO ZOOM" : "ROI TARGET"
                    ));
                    trace.putCount("target_roi_mt_runs", 1);
                    trace.putConfidence(
                            "target_roi_area_ratio",
                            scheduledRegion.area()
                                    / (double) ((long) frame.getWidth() * frame.getHeight())
                    );
                    if (targetRoiActive) trace.putCount("auto_zoom_target_roi", 1);
                    break;
                case VEHICLE_ROI:
                    int regionIndex = Math.min(
                            Math.max(0, mtDecision.vehicleRegionIndex),
                            Math.max(0, vehicleRegions.size() - 1)
                    );
                    scheduledRegion = vehicleRegions.isEmpty()
                            ? fullFrameRegion(frame)
                            : vehicleRegions.get(regionIndex);
                    trace.putCount("mt_staggered_roi_runs", vehicleRegions.size() > 1 ? 1 : 0);
                    break;
                case FULL_FRAME:
                default:
                    scheduledRegion = fullFrameRegion(frame);
                    if (mtDecision.reason.contains("deferred")) {
                        trace.putCount("mt_deferred_fallbacks", 1);
                        trace.putCount("full_frame_fallbacks", 1);
                    }
                    break;
            }

            plates.addAll(detectPlates(frame, scheduledRegion, plateDurations));
            cancelIfRequested(cancellationRequested);
            boolean roiPass = mtDecision.kind != MtInferenceScheduler.Kind.FULL_FRAME;
            trace.putCount("mt_runs_this_frame", 1);
            trace.putCount("plate_roi_runs", roiPass ? 1 : 0);
            trace.putCount("plate_full_frame_runs", roiPass ? 0 : 1);
            mtInferenceScheduler.onMtResult(
                    mtDecision,
                    trace.frameId(),
                    !plates.isEmpty()
            );
            if (plates.isEmpty()
                    && mtDecision.kind == MtInferenceScheduler.Kind.FULL_FRAME) {
                cachedVehicleRegions.clear();
                cachedVehicleDetections.clear();
                lastVehicleDetectionFrame = Long.MIN_VALUE;
            }
        }
        cancelIfRequested(cancellationRequested);
        trace.putDurationNanos("plate_preprocess", plateDurations[0]);
        trace.putDurationNanos("plate_inference", plateDurations[1]);
        trace.putDurationNanos("plate_postprocess", plateDurations[2]);

        // Deduplikacja po połączeniu wyników kilku ROI oraz fallbacku pełnoklatkowego.
        /*
         * Diagnostyka surowych detekcji MT.
         *
         * Nie zmienia działania pipeline'u.
         */
        trace.putCount(
                "plate_detections_raw",
                plates.size()
        );

        logPlateDetectionPairs(
                "RAW",
                plates,
                trace.frameId()
        );


        /*
         * Deduplikacja po połączeniu wyników kilku ROI
         * oraz fallbacku pełnoklatkowego.
         */
        int rawPlateDetectionCount =
                plates.size();

        plates =
                new ArrayList<>(
                        DetectionDeduplicator.suppress(
                                plates,
                                plateOutputSpec.iouThreshold(),
                                0.82f,
                                false
                        )
                );


        trace.putCount(
                "plate_detections_after_dedup",
                plates.size()
        );

        trace.putCount(
                "plate_detections_suppressed",
                Math.max(
                        0,
                        rawPlateDetectionCount
                                - plates.size()
                )
        );

        if (targetRoiActive && !plates.isEmpty()) {
            List<AutoZoomTargetLock.Candidate> targetCandidates = new ArrayList<>();
            for (int index = 0; index < plates.size(); index++) {
                Detection detection = plates.get(index);
                List<Point2> corners = new ArrayList<>(
                        detection.keypoints.subList(
                                0,
                                Math.min(4, detection.keypoints.size())
                        )
                );
                targetCandidates.add(new AutoZoomTargetLock.Candidate(
                        index,
                        new AutoZoomTargetLock.Box(
                                detection.left / frame.getWidth(),
                                detection.top / frame.getHeight(),
                                detection.right / frame.getWidth(),
                                detection.bottom / frame.getHeight()
                        ),
                        detection.confidence,
                        corners.size() == 4 && cornersInsideFrame(
                                corners,
                                frame.getWidth(),
                                frame.getHeight()
                        ),
                        PlateAppearanceDescriptor.from(frame, detection)
                ));
            }
            AutoZoomTargetLock.Selection selection =
                    autoZoomTargetLock.select(targetCandidates);
            trace.putCount("auto_zoom_lock_candidates", selection.candidateCount);
            trace.putCount("auto_zoom_lock_misses", autoZoomTargetLock.misses());
            trace.putConfidence("auto_zoom_lock_score", selection.score);
            trace.putConfidence("auto_zoom_lock_second_score", selection.secondScore);
            trace.putConfidence("auto_zoom_lock_confidence", autoZoomTargetLock.confidence());
            if (selection.candidate == null) {
                plates = new ArrayList<>();
            } else {
                plates = new ArrayList<>(Collections.singletonList(
                        plates.get(selection.candidate.sourceIndex)
                ));
            }
        } else if (targetRoiActive) {
            AutoZoomTargetLock.Selection selection =
                    autoZoomTargetLock.select(Collections.emptyList());
            trace.putCount("auto_zoom_lock_candidates", 0);
            trace.putCount("auto_zoom_lock_misses", autoZoomTargetLock.misses());
            trace.putConfidence("auto_zoom_lock_score", selection.score);
            trace.putConfidence("auto_zoom_lock_confidence", autoZoomTargetLock.confidence());
        }


        /*
         * To są już dokładnie detekcje, które mogą
         * przejść dalej do trackera i overlayu.
         */
        logPlateDetectionPairs(
                "KEPT",
                plates,
                trace.frameId()
        );
        cancelIfRequested(cancellationRequested);
        trace.putAttribute(
                "mz_state_event",
                effectiveSceneChanged
                        ? "SCENE_RESET"
                        : targetLostSignal
                                ? "TARGET_LOST"
                                : plates.isEmpty()
                                        ? "MT_RUN_WITHOUT_DETECTIONS"
                                        : "MT_RUN_WITH_DETECTIONS"
        );
        if (plates.isEmpty()) {
            trackCoordinator.update(
                    Collections.emptyList(), trace.frameId(), SystemClock.elapsedRealtimeNanos()
            );
            trace.finish("no_plate", "");
            return new PipelineResult(
                    "no_plate", "Nie wykryto tablicy", "", 0,
                    overlays, frame.getWidth(), frame.getHeight(), effectiveSceneChanged
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

        for (PlateTrackCoordinator.Decision decision : decisions) {
            cancelIfRequested(cancellationRequested);
            if (decision.sourceIndex < 0
                    || decision.sourceIndex >= candidates.size()) continue;
            float[] appearance = PlateAppearanceDescriptor.from(
                    frame,
                    candidates.get(decision.sourceIndex).detection
            );
            if (appearance != null) {
                plateAppearanceByTrack.put(
                        decision.trackId,
                        PlateAppearanceDescriptor.blend(
                                plateAppearanceByTrack.get(decision.trackId),
                                appearance,
                                0.08f
                        )
                );
            }
        }

        /*
         * MT jest już zakończone, natomiast MZ jeszcze się nie rozpoczęło.
         * Publikujemy teraz samą geometrię, aby UI nie czekało na OCR i mogło
         * utrzymywać świeżą ramkę przez cały dalszy przebieg auto-zoomu.
         */
        if (plateDetectionCallback != null && !decisions.isEmpty()) {
            List<OverlayItem> mtOverlays = new ArrayList<>(overlays);
            for (PlateTrackCoordinator.Decision decision : decisions) {
                if (decision.sourceIndex < 0
                        || decision.sourceIndex >= candidates.size()) continue;
                PlateCandidate candidate = candidates.get(decision.sourceIndex);
                mtOverlays.add(overlayBox(
                        frame,
                        candidate.detection.left,
                        candidate.detection.top,
                        candidate.detection.right,
                        candidate.detection.bottom,
                        candidate.corners,
                        "tablica · MT",
                        candidate.detection.confidence,
                        decision.trackId
                ));
            }
            plateDetectionCallback.onPlateDetections(
                    Collections.unmodifiableList(mtOverlays),
                    frame.getWidth(),
                    frame.getHeight()
            );
        }

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
            String predictionBefore = trackResult == null ? "" : trackResult.text;
            Bitmap observationBitmap = null;
            List<PlateCharacter> observedCharacters = Collections.emptyList();
            String freshPrediction = "";
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
                    cancelIfRequested(cancellationRequested);
                    cropCharacterInferenceNanos = SystemClock.elapsedRealtimeNanos() - started;
                    characterInferenceNanos += cropCharacterInferenceNanos;
                    characterRuns++;

                    started = SystemClock.elapsedRealtimeNanos();
                    List<Detection> characters =
                            characterCandidates(
                                    characterRun,
                                    decision.expectedCharacterCount,
                                    decision.expectedRowCounts
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
                    StringBuilder freshText = new StringBuilder();
                    for (PlateCharacter character : observedCharacters) {
                        freshText.append(character.label);
                    }
                    freshPrediction = freshText.toString();
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
                    trace.frameId(),
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
                    plateAppearanceByTrack.get(decision.trackId),
                    cropTiming,
                    PlateGeometry.from(
                            frame.getWidth(),
                            frame.getHeight(),
                            candidate.detection,
                            candidate.corners
                    ),
                    decision.recognize,
                    decision.recognize && !freshPrediction.isEmpty(),
                    freshPrediction,
                    !freshPrediction.isEmpty() && freshPrediction.equals(visibleText),
                    decision.mzAttemptIndex,
                    trackResult == null
                            ? decision.expectedLayout
                            : trackResult.layout,
                    trackResult == null
                            ? decision.expectedRowCounts
                            : trackResult.rowCounts,
                    predictionBefore,
                    visibleText
            ));
            String overlayText;


            /*
             * Rozdzielamy trzy sytuacje:
             *
             * 1. MT widzi tablicę, ale nie mamy jeszcze OCR.
             * 2. MZ został wykonany w tej klatce.
             * 3. Tekst pochodzi z pamięci temporalnej tracka.
             */
            if (visibleText.isEmpty() && decision.recognize) {

                overlayText =
                        "brak odczytu · MZ";

            } else if (visibleText.isEmpty()) {

                overlayText =
                        "tablica · MT";

            } else if (decision.recognize) {

                overlayText =
                        visibleText
                                + " · MZ";

            } else {

                overlayText =
                        visibleText
                                + " · pamięć MZ";
            }

            float overlayConfidence = visibleText.isEmpty()
                    ? candidate.detection.confidence
                    : (float) trackResult.confidence;


            overlays.add(
                    overlayBox(
                            frame,
                            candidate.detection.left,
                            candidate.detection.top,
                            candidate.detection.right,
                            candidate.detection.bottom,
                            candidate.corners,
                            overlayText,
                            overlayConfidence,
                            decision.trackId
                    )
            );
        }

        /*
         * Czasy MZ zapisujemy tylko dla klatek,
         * w których MZ rzeczywiście został uruchomiony.
         *
         * Wcześniej dla klatek bez MZ zapisywaliśmy:
         *
         * character_preprocess = 0
         * character_inference  = 0
         * character_postprocess = 0
         *
         * przez co zera trafiały do percentyli i sztucznie
         * obniżały p50/p90/p95.
         *
         * Brak etapu w trace oznacza teraz:
         * "ten etap nie wystąpił w tej klatce",
         * a nie "wykonał się w czasie 0 ms".
         */
        if (characterRuns > 0) {

            trace.putDurationNanos(
                    "rectification",
                    rectificationNanos
            );

            trace.putDurationNanos(
                    "character_preprocess",
                    characterPreprocessNanos
            );

            trace.putDurationNanos(
                    "character_inference",
                    characterInferenceNanos
            );

            trace.putDurationNanos(
                    "character_postprocess",
                    characterPostprocessNanos
            );
        }


        /*
         * Liczniki zapisujemy zawsze.
         *
         * Dzięki temu nadal wiemy:
         * - ile razy MZ rzeczywiście uruchomiono,
         * - ile kandydatów pominięto,
         * - ile tablic miało niepoprawną geometrię.
         */
        trace.putCount(
                "mz_runs",
                characterRuns
        );

        trace.putCount(
                "mz_skipped",
                Math.max(
                        0,
                        plates.size() - characterRuns
                )
        );

        trace.putCount(
                "invalid_plate_geometry",
                invalidGeometryCount
        );
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
                    plateObservations, effectiveSceneChanged
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
        String resultStatus =
                hasConfirmed
                        ? "recognized"
                        : "preliminary";


        trace.finish(
                resultStatus,
                traceText.toString()
        );


        String resultMessage;


        if (characterRuns == 0) {

            /*
             * Mamy wynik, ale w bieżącej klatce MZ
             * nie zostało uruchomione.
             *
             * Wynik pochodzi z historii temporalnej tracka.
             */
            resultMessage =
                    String.format(
                            Locale.ROOT,
                            hasConfirmed
                                    ? "Potwierdzone odczyty: %d/%d · MZ: bez nowej próby · wynik z pamięci"
                                    : "Wynik wstępny: %d/%d · MZ: bez nowej próby · wynik z pamięci",
                            recognitions.size(),
                            plates.size()
                    );

        } else {

            resultMessage =
                    String.format(
                            Locale.ROOT,
                            hasConfirmed
                                    ? "Potwierdzone odczyty: %d/%d · MZ wykonano: %d"
                                    : "Wynik wstępny: %d/%d · MZ wykonano: %d",
                            recognitions.size(),
                            plates.size(),
                            characterRuns
                    );
        }


        return new PipelineResult(
                resultStatus,
                resultMessage,
                recognitions,
                overlays,
                frame.getWidth(),
                frame.getHeight(),
                plateObservations,
                effectiveSceneChanged
        );
    }

    private void recordSchedulerDecision(
            InferenceTrace trace,
            MtInferenceScheduler.Decision decision,
            TargetSnapshot target
    ) {
        trace.putAttribute("mt_scheduler_reason", decision.reason);
        trace.putAttribute(
                "target_state",
                target == null ? "SEARCHING" : target.state.name()
        );
        trace.putCount("mt_scheduler_queue_size", decision.runsMt() ? 1 : 0);
        trace.putCount("target_recovery_level", decision.recoveryLevel);
        if (target != null) {
            trace.putAttribute("target_transition_reason", target.transitionReason);
            trace.putConfidence("tracker_quality", target.trackingQuality);
            trace.putConfidence("tracker_support_ratio", target.supportRatio);
            trace.putCount("tracker_inliers", target.trackerInliers);
            trace.putCount("target_lock_age_frames", target.ageFrames);
            trace.putCount("tracker_failures", target.consecutiveFailures);
            trace.putCount("locked_track_id", target.lockedTrackId);

            int switchDelta = Math.max(
                    0,
                    target.lockSwitches - reportedTargetLockSwitches
            );
            int lossDelta = Math.max(
                    0,
                    target.lockLosses - reportedTargetLockLosses
            );
            int reassociationDelta = Math.max(
                    0,
                    target.lockReassociations
                            - reportedTargetLockReassociations
            );
            trace.putCount("lock_switches", switchDelta);
            trace.putCount("lock_losses", lossDelta);
            trace.putCount("lock_reassociations", reassociationDelta);
            reportedTargetLockSwitches = target.lockSwitches;
            reportedTargetLockLosses = target.lockLosses;
            reportedTargetLockReassociations = target.lockReassociations;

            if (target.lockRevision > 0L
                    && target.lockRevision != reportedTargetLockRevision) {
                trace.putCount("frames_to_lock", target.framesToLock);
                trace.putCount("time_to_lock_ms", target.timeToLockMillis);
                reportedTargetLockRevision = target.lockRevision;
            } else {
                trace.putCount("frames_to_lock", 0);
                trace.putCount("time_to_lock_ms", 0);
            }
        }
        if ("periodic_refresh".equals(decision.reason)) {
            trace.putCount("mt_periodic_refresh", 1);
        } else if (decision.reason.contains("degraded")
                || decision.reason.contains("invalid")) {
            trace.putCount("mt_forced_by_quality", 1);
        }
        if (decision.recoveryLevel > 0) {
            trace.putCount(
                    "target_recoveries_level_" + decision.recoveryLevel,
                    1
            );
        }
    }

    private VehicleRoiSelector.Region targetRegion(
            Bitmap frame,
            TargetSnapshot target,
            boolean autoZoomActive,
            float margin
    ) {
        if (autoZoomActive) {
            AutoZoomTargetLock.Box box = autoZoomTargetLock.searchBox();
            return VehicleRoiSelector.normalizedRegion(
                    frame.getWidth(), frame.getHeight(),
                    box.left, box.top, box.right, box.bottom
            );
        }
        RectF bounds = target == null ? new RectF(0f, 0f, 1f, 1f)
                : target.normalizedBounds;
        float marginX = bounds.width() * Math.max(0f, margin);
        float marginY = bounds.height() * Math.max(0f, margin);
        return VehicleRoiSelector.normalizedRegion(
                frame.getWidth(),
                frame.getHeight(),
                bounds.left - marginX,
                bounds.top - marginY,
                bounds.right + marginX,
                bounds.bottom + marginY
        );
    }

    private static void addVehicleDiagnostics(
            List<OverlayItem> overlays,
            List<Detection> vehicles,
            List<VehicleRoiSelector.Region> selectedRegions,
            Bitmap frame,
            RoiBudgetPolicy policy
    ) {
        for (Detection vehicle : vehicles) {
            overlays.add(new OverlayItem(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(
                            vehicle.left / frame.getWidth(),
                            vehicle.top / frame.getHeight(),
                            vehicle.right / frame.getWidth(),
                            vehicle.bottom / frame.getHeight()
                    ),
                    Collections.emptyList(),
                    String.format(
                            Locale.ROOT,
                            "MP pojazd %.0f%%",
                            vehicle.confidence * 100f
                    ),
                    0L,
                    false
            ));
        }

        int selectedCount = selectedRegions.size();
        String profile = policy == RoiBudgetPolicy.TWO_ROI
                ? "R2" : policy == RoiBudgetPolicy.ONE_ROI ? "R1" : "R0";
        for (int index = 0; index < selectedCount; index++) {
            overlays.add(roiOverlay(
                    selectedRegions.get(index),
                    frame,
                    String.format(
                            Locale.ROOT,
                            "%s ROI %d/%d",
                            profile,
                            index + 1,
                            selectedCount
                    )
            ));
        }
    }

    private static OverlayItem roiOverlay(
            VehicleRoiSelector.Region region,
            Bitmap frame,
            String label
    ) {
        return new OverlayItem(
                OverlayItem.Kind.VEHICLE_ROI,
                new RectF(
                        region.left / (float) frame.getWidth(),
                        region.top / (float) frame.getHeight(),
                        region.right / (float) frame.getWidth(),
                        region.bottom / (float) frame.getHeight()
                ),
                Collections.emptyList(),
                label,
                0L,
                false
        );
    }

    private static OverlayItem carriedTargetOverlay(OverlayItem source) {
        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                source.normalizedBounds,
                source.normalizedKeypoints,
                source.label,
                source.trackId,
                true
        );
    }

    private static void cancelIfRequested(BooleanSupplier cancellationRequested) {
        if (cancellationRequested != null && cancellationRequested.getAsBoolean()) {
            throw new ProcessingCancelledException();
        }
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

    private VehicleDetectionResult detectVehicleRegions(
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
        Map.Entry<Integer, ByteBuffer> outputEntry =
                run.outputs().entrySet().iterator().next();

        TensorInfo outputInfo =
                run.tensorInfo().get(outputEntry.getKey());

        android.util.Log.d(
                "ALPR_MP",
                "OUTPUT shape="
                        + java.util.Arrays.toString(outputInfo.shape)
                        + ", decoder="
                        + vehicleOutputSpec.decoder()
                        + ", channelsFirst="
                        + vehicleOutputSpec.channelsFirst()
                        + ", classCount="
                        + vehicleOutputSpec.classCount()
                        + ", hasObjectness="
                        + vehicleOutputSpec.hasObjectness()
                        + ", normalized="
                        + vehicleOutputSpec.normalizedCoordinates()
        );
        trace.stop("vehicle_inference");

        trace.start("vehicle_postprocess");
        List<Detection> decodedVehicles = decodeFirstOutput(
                run,
                vehicleInputSpec,
                vehicleOutputSpec
        );

        android.util.Log.d(
                "ALPR_MP",
                "SPEC normalized="
                        + vehicleOutputSpec.normalizedCoordinates()
                        + ", input="
                        + vehicleInputSpec.width()
                        + "x"
                        + vehicleInputSpec.height()
                        + ", scale="
                        + input.scale
                        + ", padX="
                        + input.padX
                        + ", padY="
                        + input.padY
        );

        List<Detection> vehicles = new ArrayList<>();

        for (int i = 0; i < decodedVehicles.size(); i++) {
            Detection raw = decodedVehicles.get(i);

            if (!vehicleClassIds.contains(raw.classId)) {
                continue;
            }

            android.util.Log.d(
                    "ALPR_MP",
                    "RAW[" + i + "] "
                            + "box=("
                            + raw.left + ", "
                            + raw.top + ", "
                            + raw.right + ", "
                            + raw.bottom + ")"
                            + " size="
                            + raw.width() + "x" + raw.height()
                            + " conf=" + raw.confidence
            );

            Detection mapped = DetectionCoordinateMapper.toSource(
                    raw,
                    input,
                    0,
                    0
            );

            android.util.Log.d(
                    "ALPR_MP",
                    "MAP[" + i + "] "
                            + "box=("
                            + mapped.left + ", "
                            + mapped.top + ", "
                            + mapped.right + ", "
                            + mapped.bottom + ")"
                            + " size="
                            + mapped.width() + "x" + mapped.height()
            );

            vehicles.add(mapped);
        }

        trace.putCount(
                "vehicle_detections_raw",
                decodedVehicles.size()
        );

        trace.putCount(
                "vehicle_detections_used",
                vehicles.size()
        );

        trace.putCount(
                "vehicle_detections_rejected_class",
                Math.max(0, decodedVehicles.size() - vehicles.size())
        );

        android.util.Log.d(
                "ALPR_MP",
                "CLASS FILTER raw=" + decodedVehicles.size()
                        + ", used=" + vehicles.size()
                        + ", rejected="
                        + Math.max(0, decodedVehicles.size() - vehicles.size())
                        + ", allowedClassIds=" + vehicleClassIds
        );

        List<VehicleRoiSelector.Region> allDiagnosticRegions =
                VehicleRoiSelector.select(
                        vehicles,
                        frame.getWidth(),
                        frame.getHeight(),
                        Integer.MAX_VALUE,
                        rapidCameraMotion ? 0.28f : VEHICLE_REGION_MARGIN,
                        vehicleOutputSpec.iouThreshold()
                );
        List<Detection> diagnosticVehicles = new ArrayList<>(
                allDiagnosticRegions.size()
        );
        for (VehicleRoiSelector.Region region : allDiagnosticRegions) {
            if (region.vehicle != null) diagnosticVehicles.add(region.vehicle);
        }
        trace.putCount(
                "vehicle_detections_diagnostic",
                diagnosticVehicles.size()
        );

        List<VehicleRoiSelector.Region> regions =
                VehicleRoiSelector.select(
                        diagnosticVehicles,
                        frame.getWidth(),
                        frame.getHeight(),
                        roiBudgetPolicy.maximumRegions(),
                        rapidCameraMotion ? 0.28f : VEHICLE_REGION_MARGIN,
                        vehicleOutputSpec.iouThreshold()
                );
        trace.putCount(
                "vehicle_regions_selected",
                regions.size()
        );
        android.util.Log.d(
                "ALPR_MP",
                "MP detections=" + vehicles.size()
                        + ", regions=" + regions.size()
                        + ", policy=" + roiBudgetPolicy.wireName()
                        + ", maxRegions=" + roiBudgetPolicy.maximumRegions()
                        + ", confThreshold=" + vehicleOutputSpec.confidenceThreshold()
                        + ", iouThreshold=" + vehicleOutputSpec.iouThreshold()
                        + ", frame=" + frame.getWidth() + "x" + frame.getHeight()
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
        return new VehicleDetectionResult(
                diagnosticVehicles,
                regions
        );
    }

    private static Set<Integer> resolveVehicleClassIds(List<String> labels) {
        Set<Integer> result = new HashSet<>();

        for (int i = 0; i < labels.size(); i++) {
            if (isVehicleLabel(labels.get(i))) {
                result.add(i);
            }
        }

        /*
         * Jeżeli model ma nietypowe etykiety i nie rozpoznaliśmy żadnej klasy,
         * nie blokujemy całego MP. Zachowujemy kompatybilność z modelem
         * wyspecjalizowanym wyłącznie w pojazdach.
         */
        if (result.isEmpty()) {
            for (int i = 0; i < labels.size(); i++) {
                result.add(i);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    private static boolean isVehicleLabel(String label) {
        if (label == null) return false;

        String value = label
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');

        return value.equals("car")
                || value.equals("motorcycle")
                || value.equals("motorbike")
                || value.equals("bus")
                || value.equals("truck")
                || value.equals("van")
                || value.equals("pickup")
                || value.equals("pickup truck")
                || value.equals("lorry")
                || value.equals("vehicle");
    }

    private static void logPlateDetectionPairs(
            String stage,
            List<Detection> detections,
            long frameId
    ) {
        if (detections == null) {
            return;
        }


        android.util.Log.d(
                "ALPR_MT_PAIR",
                stage
                        + " frame="
                        + frameId
                        + " count="
                        + detections.size()
        );


        /*
         * Najpierw wypisujemy każdą pojedynczą detekcję.
         * Dzięki temu widzimy również wynik po deduplikacji,
         * nawet jeśli pozostała tylko jedna ramka.
         */
        for (int index = 0;
             index < detections.size();
             index++) {

            Detection detection =
                    detections.get(index);


            android.util.Log.d(
                    "ALPR_MT_PAIR",
                    String.format(
                            Locale.ROOT,
                            "%s frame=%d det=%d "
                                    + "conf=%.3f area=%.0f "
                                    + "box=[%.1f,%.1f,%.1f,%.1f]",
                            stage,
                            frameId,
                            index,
                            detection.confidence,
                            detection.width()
                                    * detection.height(),
                            detection.left,
                            detection.top,
                            detection.right,
                            detection.bottom
                    )
            );
        }


        /*
         * Następnie relacje pomiędzy parami.
         */
        for (int firstIndex = 0;
             firstIndex < detections.size();
             firstIndex++) {

            Detection first =
                    detections.get(firstIndex);


            for (int secondIndex =
                 firstIndex + 1;
                 secondIndex < detections.size();
                 secondIndex++) {

                Detection second =
                        detections.get(secondIndex);


                float iou =
                        NonMaxSuppression.iou(
                                first,
                                second
                        );


                float containment =
                        DetectionDeduplicator
                                .overlapOverSmaller(
                                        first,
                                        second
                                );


                android.util.Log.d(
                        "ALPR_MT_PAIR",
                        String.format(
                                Locale.ROOT,
                                "%s frame=%d pair=%d-%d "
                                        + "iou=%.3f containment=%.3f "
                                        + "confA=%.3f confB=%.3f "
                                        + "areaA=%.0f areaB=%.0f",
                                stage,
                                frameId,
                                firstIndex,
                                secondIndex,
                                iou,
                                containment,
                                first.confidence,
                                second.confidence,
                                first.width()
                                        * first.height(),
                                second.width()
                                        * second.height()
                        )
                );
            }
        }
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
            int expectedCharacterCount,
            List<Integer> expectedRowCounts
    ) {
        float normalThreshold =
                characterOutputSpec.confidenceThreshold();

        float candidateFloor =
                Math.min(
                        normalThreshold,
                        0.10f
                );


        List<Detection> candidates =
                decodeFirstOutput(
                        run,
                        characterInputSpec,
                        characterOutputSpec,
                        candidateFloor
                );


        List<Detection> normal =
                new ArrayList<>();


        for (Detection candidate :
                candidates) {

            if (candidate.confidence
                    >= normalThreshold) {

                normal.add(
                        candidate
                );
            }
        }


        List<Detection> selected =
                CharacterSequencePostProcessor.process(
                        normal,
                        expectedCharacterCount
                );


        /*
         * Jeżeli nie mamy jeszcze stabilnej wiedzy czasowej
         * o oczekiwanym wyniku, pozostajemy przy zwykłym
         * progu confidence.
         */
        if (expectedCharacterCount <= 0) {
            return selected;
        }


        int selectedDistance =
                candidateStructureDistance(
                        selected,
                        expectedCharacterCount,
                        expectedRowCounts
                );


        /*
         * Jeżeli wynik z normalnego progu dokładnie odpowiada
         * oczekiwanej strukturze, nie ma powodu uruchamiać
         * bardziej liberalnego recall.
         */
        if (selectedDistance == 0) {
            return selected;
        }


        /*
         * Druga próba używa również kandydatów z niższym
         * confidence.
         *
         * Nie wybieramy jej już wyłącznie na podstawie
         * całkowitej liczby znaków.
         *
         * Uwzględniamy także strukturę wierszy zapamiętaną
         * przez konsensus czasowy.
         */
        List<Detection> recall =
                CharacterSequencePostProcessor.process(
                        candidates,
                        expectedCharacterCount
                );


        int recallDistance =
                candidateStructureDistance(
                        recall,
                        expectedCharacterCount,
                        expectedRowCounts
                );


        if (recallDistance < selectedDistance) {
            return recall;
        }


        return selected;
    }

    private static int candidateStructureDistance(
            List<Detection> detections,
            int expectedCharacterCount,
            List<Integer> expectedRowCounts
    ) {
        int totalCountDistance =
                Math.abs(
                        detections.size()
                                - Math.max(
                                0,
                                expectedCharacterCount
                        )
                );


        /*
         * Jeśli tracker zna tylko długość sekwencji,
         * zachowujemy stare zachowanie.
         */
        if (expectedRowCounts == null
                || expectedRowCounts.isEmpty()) {

            return totalCountDistance;
        }


        List<List<Detection>> actualRows =
                ReadingOrderResolver.rows(
                        detections
                );


        /*
         * Różna liczba wierszy jest znacznie poważniejszą
         * niezgodnością niż brak pojedynczego znaku.
         *
         * Dlatego nadajemy jej większą wagę.
         */
        int distance =
                Math.abs(
                        actualRows.size()
                                - expectedRowCounts.size()
                ) * 100;


        int commonRows =
                Math.min(
                        actualRows.size(),
                        expectedRowCounts.size()
                );


        for (int rowIndex = 0;
             rowIndex < commonRows;
             rowIndex++) {

            distance +=
                    Math.abs(
                            actualRows
                                    .get(rowIndex)
                                    .size()
                                    - expectedRowCounts
                                    .get(rowIndex)
                    );
        }


        /*
         * Znaki należące do brakujących albo dodatkowych
         * wierszy także zwiększają odległość.
         */
        for (int rowIndex = commonRows;
             rowIndex < actualRows.size();
             rowIndex++) {

            distance +=
                    actualRows
                            .get(rowIndex)
                            .size();
        }


        for (int rowIndex = commonRows;
             rowIndex < expectedRowCounts.size();
             rowIndex++) {

            distance +=
                    Math.max(
                            0,
                            expectedRowCounts
                                    .get(rowIndex)
                    );
        }


        /*
         * Zachowujemy dodatkowo kontrolę całkowitej
         * liczby znaków.
         */
        distance +=
                totalCountDistance;


        return distance;
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
            float confidence,
            long trackId
    ) {
        float width = frame.getWidth();
        float height = frame.getHeight();
        List<PointF> points = new ArrayList<>();
        for (Point2 point : corners) points.add(new PointF(point.x / width, point.y / height));
        return new OverlayItem(
                new RectF(left / width, top / height, right / width, bottom / height),
                points,
                String.format(Locale.ROOT, "%s %.0f%%", name, confidence * 100),
                trackId,
                false
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
