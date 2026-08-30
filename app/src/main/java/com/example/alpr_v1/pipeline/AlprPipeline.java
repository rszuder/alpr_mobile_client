package com.example.alpr_v1.pipeline;

import androidx.camera.core.ImageProxy;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.content.Context;
import android.os.SystemClock;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AdaptiveFrameGate;
import com.example.alpr_v1.continuity.ContinuityGenerationGate;
import com.example.alpr_v1.continuity.ContinuityResultDisposition;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.MotionExplanationEvidence;
import com.example.alpr_v1.continuity.ReacquireTelemetry;
import com.example.alpr_v1.continuity.RecoveryFrameGate;
import com.example.alpr_v1.continuity.SceneContinuityProfile;
import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.continuity.SceneEvidence;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.continuity.TargetContinuityEvidence;
import com.example.alpr_v1.continuity.VehicleContinuityEvidence;
import com.example.alpr_v1.continuity.VisualChangeClassification;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingEvent;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.vision.SceneChangeDetector;

import java.nio.ByteBuffer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.json.JSONException;

public final class AlprPipeline {
    private static final String LOG_TAG = "AlprPipeline";

    /**
     * Lekki wynik etapu MT publikowany zanim silnik rozpocznie MZ.
     * Callback działa na wątku analizatora i nie może blokować inferencji.
     */
    public interface PlateDetectionCallback {
        void onPlateDetections(
                List<OverlayItem> overlayItems,
                int sourceWidth,
                int sourceHeight,
                ContinuityStamp sourceStamp
        );
    }

    public interface SceneChangeCallback {
        void onSceneChanged(float score, float changedFraction);
    }

    /*
     * Etapy, których czasy nie nakładają się na siebie
     * i mogą zostać zsumowane jako jawnie zmierzony
     * koszt przetwarzania klatki.
     *
     * engine_total nie znajduje się tutaj celowo,
     * ponieważ obejmuje wszystkie etapy wykonywane
     * wewnątrz MobileAlprEngine.
     */
    private static final String[] ACCOUNTED_TIMING_STAGES = {
            "engine_setup",
            "camera_conversion",

            "vehicle_preprocess",
            "vehicle_inference",
            "vehicle_postprocess",

            "plate_preprocess",
            "plate_inference",
            "plate_postprocess",

            "rectification",

            "character_preprocess",
            "character_inference",
            "character_postprocess",

            "pipeline_finalize"
    };


    /*
     * Podzbiór etapów wykonywanych wewnątrz
     * MobileAlprEngine.
     *
     * Pozwala osobno policzyć niewyjaśniony koszt
     * samego silnika.
     */
    private static final String[] ENGINE_ACCOUNTED_STAGES = {
            "vehicle_preprocess",
            "vehicle_inference",
            "vehicle_postprocess",

            "plate_preprocess",
            "plate_inference",
            "plate_postprocess",

            "rectification",

            "character_preprocess",
            "character_inference",
            "character_postprocess"
    };
    private final Context context;
    private final ModelRegistry registry;
    private final MetricsCollector metrics;
    private final AutoTuneManager autoTuneManager;
    private final AdaptiveFrameGate frameGate;
    private final SceneChangeDetector sourceSceneDetector = new SceneChangeDetector();
    private final SceneChangeDetector rotatedSceneDetector = new SceneChangeDetector();
    private final SceneTransitionCoordinator sceneTransitionCoordinator;
    private final ContinuityGenerationGate continuityGenerationGate =
            new ContinuityGenerationGate();
    private final VehicleTrackingCoordinator vehicleTrackingCoordinator =
            new VehicleTrackingCoordinator();
    private final AtomicLong frameIds = new AtomicLong();
    private final AtomicLong previewEvidenceIds = new AtomicLong();
    private final AtomicLong sceneGeneration = new AtomicLong();
    private final AtomicLong visualEpoch = new AtomicLong();
    private final AtomicLong hardResetRevision = new AtomicLong();
    private final AtomicLong visualEpochRevision = new AtomicLong();
    private final AtomicLong previewTrackingUpdates = new AtomicLong();
    private final AtomicLong lastPreviewTrackingNanos = new AtomicLong();
    private final AtomicLong lastTracedPreviewTrackingUpdates = new AtomicLong();
    private MobileAlprEngine engine;
    private volatile boolean reloadRequested;
    /*
     * Żądanie resetu może przyjść z wątku UI
     * podczas trwającej ciężkiej inferencji.
     *
     * Nie blokujemy UI na synchronized process().
     * Reset zostanie wykonany przed następnym
     * wywołaniem engine.run().
     */
    private volatile boolean trackingResetRequested;

    /*
     * Żądanie resetu może przyjść z wątku UI
     * podczas trwającej ciężkiej inferencji.
     *
     * Nie blokujemy UI na synchronized process().
     * Reset zostanie wykonany przed następnym
     * wywołaniem engine.run().
     */

    private RecognitionProfile recognitionProfile = RecognitionProfile.BALANCED;

    /*
     * Normalna konfiguracja użytkownika.
     * Tryb eksperymentalny nigdy nie zmienia tej wartości.
     */
    private boolean vehicleCascadeEnabled;

    /*
     * Oddzielna warstwa eksperymentalna.
     */
    private boolean experimentModeEnabled;
    private RoiBudgetPolicy experimentRoiBudgetPolicy =
            RoiBudgetPolicy.TWO_ROI;

    private volatile boolean rapidCameraMotion;
    private volatile boolean cameraMoving;
    private volatile boolean motionSensorAvailable;
    private volatile float angularMotionMagnitude;
    private volatile VehicleContinuityEvidence lastReacquireVehicleEvidence =
            VehicleContinuityEvidence.empty();
    private volatile SceneEvidence lastSceneEvidence;
    private volatile SceneTransitionDecision lastSceneDecision;
    private long lastContinuityTelemetryRevision;
    private SceneContinuityState lastAppliedContinuityState =
            SceneContinuityState.STABLE;
    private long softHoldStartedNanos = -1L;
    private long softReacquireStartedNanos = -1L;
    private final AtomicLong pendingReacquireSucceededEvents = new AtomicLong();
    private final AtomicLong pendingReacquireFailedEvents = new AtomicLong();
    private final AtomicLong pendingStaleResultEvents = new AtomicLong();
    private final AtomicLong pendingSoftHoldDurationNanos = new AtomicLong();
    private final AtomicLong pendingReacquireDurationNanos = new AtomicLong();
    private final AtomicLong withoutValidatedTargetSinceNanos = new AtomicLong(-1L);
    private volatile boolean cameraTransformInProgress;
    private volatile float currentCameraZoomRatio = 1f;
    private volatile TargetSnapshot targetSnapshot = TargetSnapshot.searching();
    private volatile double overlayUpdateFps;
    private final Object cameraTransformLock = new Object();
    private float pendingCameraZoomRatio = 1f;
    private boolean cameraTransformFinishPending;
    private volatile AutoZoomTargetConfig autoZoomTargetConfig =
            AutoZoomTargetConfig.disabled(0L);

    private static final class AutoZoomTargetConfig {
        final boolean active;
        final long revision;
        final long trackId;
        final float left;
        final float top;
        final float right;
        final float bottom;

        AutoZoomTargetConfig(
                boolean active,
                long revision,
                long trackId,
                float left,
                float top,
                float right,
                float bottom
        ) {
            this.active = active;
            this.revision = revision;
            this.trackId = trackId;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        static AutoZoomTargetConfig disabled(long revision) {
            return new AutoZoomTargetConfig(
                    false, revision, 0L, 0f, 0f, 1f, 1f
            );
        }
    }

    public AlprPipeline(
            Context context,
            ModelRegistry registry,
            MetricsCollector metrics,
            AutoTuneManager autoTuneManager
    ) {
        this.context = context.getApplicationContext();
        this.registry = registry;
        this.metrics = metrics;
        this.autoTuneManager = autoTuneManager;
        this.frameGate = new AdaptiveFrameGate(context);
        this.sceneTransitionCoordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,
                SceneContinuityProfile.INITIAL
        );
        this.metrics.setSceneContinuityConfiguration(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName(),
                "initial_v2"
        );
    }

    public synchronized PipelineResult process(ImageProxy image) {
        return process(image, null);
    }

    public synchronized PipelineResult process(
            ImageProxy image,
            PlateDetectionCallback plateDetectionCallback
    ) {
        return process(image, plateDetectionCallback, null);
    }

    public synchronized PipelineResult process(
            ImageProxy image,
            PlateDetectionCallback plateDetectionCallback,
            SceneChangeCallback sceneChangeCallback
    ) {
        long sourceTimestampNanos = image.getImageInfo().getTimestamp();
        SourceFrameStamp sourceFrameStamp = new SourceFrameStamp(
                0L,
                Math.max(0L, sourceTimestampNanos),
                SourceTimestampDomain.CAMERAX_SENSOR,
                0L, 0L, 0L
        );
        metrics.observeSourceFrame(
                image.getWidth(),
                image.getHeight(),
                sourceTimestampNanos
        );
        if (cameraTransformInProgress) {
            metrics.frameSkippedByCameraTransform();
            return null;
        }
        if (isPreRecoveryFrame(sourceFrameStamp)) {
            metrics.frameSkippedByContinuityReacquire();
            frameGate.requestImmediateFrame();
            return null;
        }
        long frameId = frameIds.incrementAndGet();
        SceneChangeDetector.Result sourceScene = sourceSceneDetector.updateSamples(
                sampleLuminance(image),
                image.getWidth(),
                image.getHeight()
        );
        SceneTransitionDecision sceneDecision = observeSourceScene(
                frameId,
                sourceFrameStamp,
                sourceScene
        );
        applySceneTransition(sceneDecision);
        if (shouldSkipHeavyInference(sceneDecision)) {
            recordContinuityFrameSkip(sceneDecision);
            if (sceneChangeCallback != null
                    && sceneDecision.action == SceneTransitionAction.HARD_RESET) {
                sceneChangeCallback.onSceneChanged(
                        sourceScene.score,
                        sourceScene.changedFraction
                );
            }
            return null;
        }
        if (!frameGate.shouldProcess(frameId)) {
            metrics.frameSkippedByGate();
            return null;
        }
        ContinuityStamp processingStamp = sceneTransitionCoordinator.stamp(
                sourceFrameStamp
        );
        InferenceTrace trace = new InferenceTrace(frameId, processingStamp);
        appendContinuityTelemetry(trace, sceneDecision, lastSceneEvidence);
        trace.putAttribute("source_timestamp_nanos", String.valueOf(sourceTimestampNanos));
        trace.putAttribute("source_sequence", String.valueOf(processingStamp.sourceSequence));
        trace.putAttribute(
                "source_timestamp_domain",
                processingStamp.sourceTimestampDomain.name()
        );
        trace.putAttribute(
                "processing_started_nanos",
                String.valueOf(SystemClock.elapsedRealtimeNanos())
        );
        trace.putCount("source_width", image.getWidth());
        trace.putCount("source_height", image.getHeight());
        trace.putConfidence("camera_zoom_ratio", currentCameraZoomRatio);
        long trackingUpdateCount = previewTrackingUpdates.get();
        long previousTrackingUpdateCount =
                lastTracedPreviewTrackingUpdates.getAndSet(trackingUpdateCount);
        trace.putCount(
                "tracker_updates",
                Math.max(0L, trackingUpdateCount - previousTrackingUpdateCount)
        );
        trace.putConfidence("overlay_update_fps", overlayUpdateFps);
        trace.start("total");
        if (!registry.hasRequiredPipeline()) {
            trace.stop("total");
            trace.finish("models_missing", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return PipelineResult.waitingForModels(processingStamp);
        }

        Bitmap frame = null;

        try {

            trace.start(
                    "camera_conversion"
            );

            try {

                com.example.alpr_v1.vision.CameraImageConverter.Result
                        conversion =
                        com.example.alpr_v1.vision.CameraImageConverter
                                .convert(
                                        image
                                );

                frame =
                        conversion.bitmap;

                trace.putDurationNanos(
                        "camera_to_bitmap",
                        conversion.toBitmapNanos
                );

                trace.putDurationNanos(
                        "camera_rotation",
                        conversion.rotationNanos
                );

                trace.putCount(
                        "camera_rotation_degrees",
                        conversion.rotationDegrees
                );

            } finally {

                trace.stop(
                        "camera_conversion"
                );
            }

            InternalSceneEvidence secondaryEvidence =
                    inspectSecondarySceneBeforeInference(frame);
            SceneTransitionDecision secondaryDecision =
                    handleInternalSceneEvidence(
                            secondaryEvidence,
                            processingStamp
                    );
            appendSecondaryScenePreflightTelemetry(
                    trace,
                    secondaryEvidence,
                    secondaryDecision
            );
            SecondaryScenePreflightGate secondaryPreflightGate =
                    SecondaryScenePreflightGate.from(
                            secondaryDecision,
                            sceneTransitionCoordinator.snapshot()
                                    .heavyInferenceSuspended
                    );
            if (secondaryPreflightGate.skipsInference()) {
                recordContinuityFrameSkip(secondaryDecision);
                if (sceneChangeCallback != null
                        && secondaryDecision.action
                        == SceneTransitionAction.HARD_RESET) {
                    sceneChangeCallback.onSceneChanged(
                            secondaryEvidence.score,
                            secondaryEvidence.changedFraction
                    );
                }
                trace.putCount("secondary_scene_preflight_skipped_inference", 1);
                trace.stop("total");
                appendTimingAudit(trace);
                trace.finish("secondary_scene_preflight_skip", "");
                trace.captureMemoryAfterMeasurement();
                metrics.add(trace);
                return null;
            }

            trace.start(
                    "engine_setup"
            );

            try {

                if (reloadRequested) {

                    if (engine != null) {
                        engine.close();
                    }

                    engine = null;

                    reloadRequested =
                            false;
                }


                if (engine == null) {

                    engine =
                            new MobileAlprEngine(
                                    registry,
                                    autoTuneManager,
                                    effectiveRoiBudgetPolicy(),
                                    effectiveMtExecutionPolicy(),
                                    effectiveMtFallbackPolicy(),
                                    effectiveVehicleTrackingPolicy(),
                                    vehicleTrackingCoordinator
                            );

                    engine.setRecognitionProfile(
                            recognitionProfile
                    );

                    engine.setRapidCameraMotion(
                            rapidCameraMotion
                    );

                    engine.setCameraTransformInProgress(
                            cameraTransformInProgress
                    );
                    engine.setSoftReacquireResultListener(
                            this::handleSoftReacquireReport
                    );
                }

                AutoZoomTargetConfig targetConfig = autoZoomTargetConfig;
                engine.setAutoZoomTargetLock(
                        targetConfig.active,
                        targetConfig.revision,
                        targetConfig.trackId,
                        targetConfig.left,
                        targetConfig.top,
                        targetConfig.right,
                        targetConfig.bottom
                );


                if (trackingResetRequested) {

                    engine.hardResetScene("coordinated_hard_reset");

                    trackingResetRequested =
                            false;
                }

                engine.setTargetSnapshot(targetSnapshot);

                float pendingZoomRatio;
                boolean finishPending;
                synchronized (cameraTransformLock) {
                    pendingZoomRatio = pendingCameraZoomRatio;
                    finishPending = cameraTransformFinishPending;
                    pendingCameraZoomRatio = 1f;
                    cameraTransformFinishPending = false;
                }
                if (finishPending) {
                    if (Math.abs(pendingZoomRatio - 1f) > 0.0001f) {
                        engine.applyCameraZoomTransform(pendingZoomRatio);
                    }
                    engine.setCameraTransformInProgress(false);
                }

            } finally {

                trace.stop(
                        "engine_setup"
                );
            }
            PipelineResult result;
            final Bitmap inferenceFrame = frame;
            final long processingHardResetRevision = hardResetRevision.get();
            final long processingVisualEpochRevision = visualEpochRevision.get();


            trace.start(
                    "engine_total"
            );

            try {

                result = secondaryPreflightGate.run(
                        () -> engine.run(
                                inferenceFrame,
                                trace,
                                processingStamp,
                                plateDetectionCallback,
                                () -> hardResetRevision.get()
                                        != processingHardResetRevision
                                        || visualEpochRevision.get()
                                        != processingVisualEpochRevision
                        )
                );

            } finally {

                trace.stop(
                        "engine_total"
                );
            }
            SoftReacquireReport softReport = engine.consumeSoftReacquireReport();
            if (isCurrentContinuityStamp(processingStamp)) {
                handleSoftReacquireReport(softReport);
            }
            trace.putAttribute(
                    "result_available_nanos",
                    String.valueOf(SystemClock.elapsedRealtimeNanos())
            );
            result = stampAndValidateResult(result, processingStamp);
            if (result == null) {
                finishStaleResultTrace(trace);
                return null;
            }
            recordVehicleTrackingEvents();
            trace.start(
                    "pipeline_finalize"
            );

            try {

                metrics.recordRecognitionState(
                        !result.recognitions.isEmpty(),
                        result.hasConfirmedRecognition()
                );

            } finally {

                trace.stop(
                        "pipeline_finalize"
                );
            }


            trace.stop(
                    "total"
            );


            /*
             * Dopiero po zatrzymaniu total możemy wyliczyć
             * bilans konkretnej klatki.
             */
            appendTimingAudit(
                    trace
            );
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return result;
        } catch (MobileAlprEngine.ProcessingCancelledException cancelled) {
            trace.stop("total");
            appendTimingAudit(trace);
            trace.finish("scene_superseded", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            frameGate.requestImmediateFrame();
            return null;
        } catch (Exception e) {
            AppLog.errorRateLimited(
                    context,
                    "pipeline_error",
                    LOG_TAG,
                    "Błąd pipeline'u w klatce " + frameId + ": " + e.getMessage(),
                    e,
                    5_000L
            );
            trace.stop("total");
            appendTimingAudit(
                    trace
            );
            trace.finish("pipeline_error", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return PipelineResult.pipelineError(
                    "Błąd pipeline'u: " + e.getMessage(),
                    processingStamp
            );
        } finally {
            if (frame != null) frame.recycle();
        }
    }

    /**
     * Przetwarza własnościowy Bitmap po zamknięciu ImageProxy. Umożliwia to
     * odblokowanie analizatora CameraX i ciągły dopływ małych klatek YUV do
     * live trackera podczas długiej inferencji MP/MT/MZ.
     */
    public synchronized PipelineResult processBitmap(
            Bitmap frame,
            long sourceTimestampNanos,
            long cameraToBitmapNanos,
            long cameraRotationNanos,
            int cameraRotationDegrees,
            PlateDetectionCallback plateDetectionCallback,
            SceneChangeCallback sceneChangeCallback
    ) {
        return processBitmap(
                frame,
                new SourceFrameStamp(
                        0L,
                        Math.max(0L, sourceTimestampNanos),
                        SourceTimestampDomain.UNKNOWN,
                        0L, 0L, 0L
                ),
                cameraToBitmapNanos,
                cameraRotationNanos,
                cameraRotationDegrees,
                plateDetectionCallback,
                sceneChangeCallback
        );
    }

    public synchronized PipelineResult processBitmap(
            Bitmap frame,
            SourceFrameStamp sourceFrameStamp,
            long cameraToBitmapNanos,
            long cameraRotationNanos,
            int cameraRotationDegrees,
            PlateDetectionCallback plateDetectionCallback,
            SceneChangeCallback sceneChangeCallback
    ) {
        if (frame == null || frame.isRecycled()) return null;
        SourceFrameStamp safeSourceFrame = sourceFrameStamp == null
                ? SourceFrameStamp.unknown(0L, 0L, 0L)
                : sourceFrameStamp;
        long sourceTimestampNanos = safeSourceFrame.sourceTimestampNanos;
        metrics.observeSourceFrame(frame.getWidth(), frame.getHeight(), sourceTimestampNanos);
        if (cameraTransformInProgress) {
            metrics.frameSkippedByCameraTransform();
            return null;
        }
        if (isPreRecoveryFrame(safeSourceFrame)) {
            metrics.frameSkippedByContinuityReacquire();
            frameGate.requestImmediateFrame();
            return null;
        }
        long frameId = frameIds.incrementAndGet();
        SceneChangeDetector.Result sourceScene = sourceSceneDetector.update(frame);
        SceneTransitionDecision sceneDecision = observeSourceScene(
                frameId,
                safeSourceFrame,
                sourceScene
        );
        applySceneTransition(sceneDecision);
        if (shouldSkipHeavyInference(sceneDecision)) {
            recordContinuityFrameSkip(sceneDecision);
            if (sceneChangeCallback != null
                    && sceneDecision.action == SceneTransitionAction.HARD_RESET) {
                sceneChangeCallback.onSceneChanged(sourceScene.score, sourceScene.changedFraction);
            }
            return null;
        }
        if (!frameGate.shouldProcess(frameId)) {
            metrics.frameSkippedByGate();
            return null;
        }

        ContinuityStamp processingStamp = sceneTransitionCoordinator.stamp(
                safeSourceFrame
        );
        InferenceTrace trace = new InferenceTrace(frameId, processingStamp);
        appendContinuityTelemetry(trace, sceneDecision, lastSceneEvidence);
        trace.putAttribute("source_timestamp_nanos", String.valueOf(sourceTimestampNanos));
        trace.putAttribute("source_sequence", String.valueOf(processingStamp.sourceSequence));
        trace.putAttribute(
                "source_timestamp_domain",
                processingStamp.sourceTimestampDomain.name()
        );
        trace.putAttribute(
                "processing_started_nanos",
                String.valueOf(SystemClock.elapsedRealtimeNanos())
        );
        trace.putCount("source_width", frame.getWidth());
        trace.putCount("source_height", frame.getHeight());
        trace.putConfidence("camera_zoom_ratio", currentCameraZoomRatio);
        long trackingUpdateCount = previewTrackingUpdates.get();
        long previousTrackingUpdateCount =
                lastTracedPreviewTrackingUpdates.getAndSet(trackingUpdateCount);
        trace.putCount("tracker_updates",
                Math.max(0L, trackingUpdateCount - previousTrackingUpdateCount));
        trace.putConfidence("overlay_update_fps", overlayUpdateFps);
        trace.putDurationNanos("camera_to_bitmap", cameraToBitmapNanos);
        trace.putDurationNanos("camera_rotation", cameraRotationNanos);
        trace.putDurationNanos(
                "camera_conversion",
                Math.max(0L, cameraToBitmapNanos) + Math.max(0L, cameraRotationNanos)
        );
        trace.putCount("camera_rotation_degrees", cameraRotationDegrees);
        trace.start("total");
        if (!registry.hasRequiredPipeline()) {
            trace.stop("total");
            trace.finish("models_missing", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return PipelineResult.waitingForModels(processingStamp);
        }

        try {
            trace.start("engine_setup");
            try {
                if (reloadRequested) {
                    if (engine != null) engine.close();
                    engine = null;
                    reloadRequested = false;
                }
                if (engine == null) {
                    engine = new MobileAlprEngine(
                            registry,
                            autoTuneManager,
                            effectiveRoiBudgetPolicy(),
                            effectiveMtExecutionPolicy(),
                            effectiveMtFallbackPolicy(),
                            effectiveVehicleTrackingPolicy(),
                            vehicleTrackingCoordinator
                    );
                    engine.setRecognitionProfile(recognitionProfile);
                    engine.setRapidCameraMotion(rapidCameraMotion);
                    engine.setCameraTransformInProgress(cameraTransformInProgress);
                    engine.setSoftReacquireResultListener(
                            this::handleSoftReacquireReport
                    );
                }
                AutoZoomTargetConfig targetConfig = autoZoomTargetConfig;
                engine.setAutoZoomTargetLock(
                        targetConfig.active,
                        targetConfig.revision,
                        targetConfig.trackId,
                        targetConfig.left,
                        targetConfig.top,
                        targetConfig.right,
                        targetConfig.bottom
                );
                if (trackingResetRequested) {
                    engine.hardResetScene("coordinated_hard_reset");
                    trackingResetRequested = false;
                }
                engine.setTargetSnapshot(targetSnapshot);
                float pendingZoomRatio;
                boolean finishPending;
                synchronized (cameraTransformLock) {
                    pendingZoomRatio = pendingCameraZoomRatio;
                    finishPending = cameraTransformFinishPending;
                    pendingCameraZoomRatio = 1f;
                    cameraTransformFinishPending = false;
                }
                if (finishPending) {
                    if (Math.abs(pendingZoomRatio - 1f) > 0.0001f) {
                        engine.applyCameraZoomTransform(pendingZoomRatio);
                    }
                    engine.setCameraTransformInProgress(false);
                }
            } finally {
                trace.stop("engine_setup");
            }

            final long processingHardResetRevision = hardResetRevision.get();
            final long processingVisualEpochRevision = visualEpochRevision.get();
            trace.start("engine_total");
            PipelineResult result;
            try {
                result = engine.run(
                        frame,
                        trace,
                        processingStamp,
                        plateDetectionCallback,
                        () -> hardResetRevision.get() != processingHardResetRevision
                                || visualEpochRevision.get()
                                != processingVisualEpochRevision
                );
            } finally {
                trace.stop("engine_total");
            }
            SoftReacquireReport softReport = engine.consumeSoftReacquireReport();
            if (isCurrentContinuityStamp(processingStamp)) {
                handleSoftReacquireReport(softReport);
            }
            trace.putAttribute(
                    "result_available_nanos",
                    String.valueOf(SystemClock.elapsedRealtimeNanos())
            );
            result = stampAndValidateResult(result, processingStamp);
            if (result == null) {
                finishStaleResultTrace(trace);
                return null;
            }
            recordVehicleTrackingEvents();
            trace.start("pipeline_finalize");
            try {
                metrics.recordRecognitionState(
                        !result.recognitions.isEmpty(),
                        result.hasConfirmedRecognition()
                );
            } finally {
                trace.stop("pipeline_finalize");
            }
            trace.stop("total");
            appendTimingAudit(trace);
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return result;
        } catch (MobileAlprEngine.ProcessingCancelledException cancelled) {
            trace.stop("total");
            appendTimingAudit(trace);
            trace.finish("scene_superseded", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            frameGate.requestImmediateFrame();
            return null;
        } catch (Exception error) {
            AppLog.errorRateLimited(
                    context,
                    "pipeline_error",
                    LOG_TAG,
                    "Błąd pipeline'u w klatce " + frameId + ": " + error.getMessage(),
                    error,
                    5_000L
            );
            trace.stop("total");
            appendTimingAudit(trace);
            trace.finish("pipeline_error", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return PipelineResult.pipelineError(
                    "Błąd pipeline'u: " + error.getMessage(),
                    processingStamp
            );
        }
    }

    private static void appendTimingAudit(
            InferenceTrace trace
    ) {

        if (trace == null) {
            return;
        }


        long total =
                trace.durationNanos(
                        "total"
                );


        /*
         * SUM:
         * suma wszystkich jawnie zmierzonych,
         * nienakładających się etapów.
         */
        long accounted =
                sumStages(
                        trace,
                        ACCOUNTED_TIMING_STAGES
                );


        long overhead =
                Math.max(
                        0L,
                        total - accounted
                );


        trace.putDurationNanos(
                "measured_stage_sum",
                accounted
        );


        trace.putDurationNanos(
                "pipeline_overhead",
                overhead
        );


        /*
         * Osobno badamy wnętrze MobileAlprEngine.
         */
        long engineTotal =
                trace.durationNanos(
                        "engine_total"
                );


        long engineAccounted =
                sumStages(
                        trace,
                        ENGINE_ACCOUNTED_STAGES
                );
        long inferenceSum =
                trace.durationNanos(
                        "vehicle_inference"
                )
                        + trace.durationNanos(
                        "plate_inference"
                )
                        + trace.durationNanos(
                        "character_inference"
                );


        long auxiliarySum =
                Math.max(
                        0L,
                        accounted - inferenceSum
                );


        trace.putDurationNanos(
                "inference_sum",
                inferenceSum
        );


        trace.putDurationNanos(
                "auxiliary_sum",
                auxiliarySum
        );


        trace.putDurationNanos(
                "engine_measured_sum",
                engineAccounted
        );


        trace.putDurationNanos(
                "engine_overhead",
                Math.max(
                        0L,
                        engineTotal
                                - engineAccounted
                )
        );



        /*
         * Udział niewyjaśnionego czasu w całym pipeline.
         * Zapisujemy jako confidence, bo InferenceTrace
         * przechowuje tam wartości double.
         */
        if (total > 0L) {

            trace.putConfidence(
                    "pipeline_overhead_ratio",
                    overhead
                            / (double) total
            );
        }
        android.util.Log.d(
                "ALPR_TIMING_AUDIT",
                String.format(
                        java.util.Locale.ROOT,

                        "frame=%d "
                                + "PIPE=%.3f "
                                + "INF=%.3f "
                                + "AUX=%.3f "
                                + "OVH=%.3f | "

                                + "CAM=%.3f "
                                + "CAM_BITMAP=%.3f "
                                + "CAM_ROT=%.3f "
                                + "ROT=%d "
                                + "SETUP=%.3f "
                                + "FINAL=%.3f | "

                                + "MP_PRE=%.3f "
                                + "MP_INF=%.3f "
                                + "MP_POST=%.3f | "

                                + "MT_PRE=%.3f "
                                + "MT_INF=%.3f "
                                + "MT_POST=%.3f | "

                                + "RECT=%.3f "
                                + "MZ_PRE=%.3f "
                                + "MZ_INF=%.3f "
                                + "MZ_POST=%.3f | "

                                + "MT_ROI=%d "
                                + "MT_FULL=%d "
                                + "MZ_RUNS=%d "
                                + "SRC=%dx%d "
                                + "LOCK_CAND=%d "
                                + "LOCK_MISS=%d "
                                + "LOCK_SCORE=%.3f",

                        trace.frameId(),

                        ms(trace, "total"),
                        ms(trace, "inference_sum"),
                        ms(trace, "auxiliary_sum"),
                        ms(trace, "pipeline_overhead"),

                        ms(trace, "camera_conversion"),
                        ms(trace, "camera_to_bitmap"),
                        ms(trace, "camera_rotation"),

                        trace.counters()
                                .getOrDefault(
                                        "camera_rotation_degrees",
                                        0L
                                ),

                        ms(trace, "engine_setup"),
                        ms(trace, "pipeline_finalize"),

                        ms(trace, "vehicle_preprocess"),
                        ms(trace, "vehicle_inference"),
                        ms(trace, "vehicle_postprocess"),

                        ms(trace, "plate_preprocess"),
                        ms(trace, "plate_inference"),
                        ms(trace, "plate_postprocess"),

                        ms(trace, "rectification"),
                        ms(trace, "character_preprocess"),
                        ms(trace, "character_inference"),
                        ms(trace, "character_postprocess"),

                        trace.counters()
                                .getOrDefault(
                                        "plate_roi_runs",
                                        0L
                                ),

                        trace.counters()
                                .getOrDefault(
                                        "plate_full_frame_runs",
                                        0L
                                ),

                        trace.counters()
                                .getOrDefault(
                                        "mz_runs",
                                        0L
                                ),

                        trace.counters()
                                .getOrDefault(
                                        "source_width",
                                        0L
                                ),

                        trace.counters()
                                .getOrDefault(
                                        "source_height",
                                        0L
                                ),

                        trace.counters().getOrDefault(
                                "auto_zoom_lock_candidates", 0L
                        ),
                        trace.counters().getOrDefault(
                                "auto_zoom_lock_misses", 0L
                        ),
                        trace.confidences().getOrDefault(
                                "auto_zoom_lock_score", 0.0
                        )
                )
        );
        android.util.Log.d(
                "ALPR_BASELINE",
                String.format(
                        java.util.Locale.ROOT,
                        "frame=%d policy=%s fallback=%s pipe_ms=%.3f "
                                + "mt_runs=%d mt_skip=%d tracker_updates=%d "
                                + "overlay_fps=%.3f tracker_quality=%.3f "
                                + "tracker_inliers=%d frames_to_lock=%d "
                                + "time_to_lock_ms=%d lock_losses=%d "
                                + "target=%s mz_event=%s",
                        trace.frameId(),
                        trace.attributes().getOrDefault("mt_execution_policy", ""),
                        trace.attributes().getOrDefault("mt_fallback_policy", ""),
                        ms(trace, "total"),
                        trace.counters().getOrDefault("mt_runs_this_frame", 0L),
                        trace.counters().getOrDefault("mt_skipped_by_tracker", 0L),
                        trace.counters().getOrDefault("tracker_updates", 0L),
                        trace.confidences().getOrDefault("overlay_update_fps", 0.0),
                        trace.confidences().getOrDefault("tracker_quality", 0.0),
                        trace.counters().getOrDefault("tracker_inliers", 0L),
                        trace.counters().getOrDefault("frames_to_lock", 0L),
                        trace.counters().getOrDefault("time_to_lock_ms", 0L),
                        trace.counters().getOrDefault("lock_losses", 0L),
                        trace.attributes().getOrDefault("target_state", ""),
                        trace.attributes().getOrDefault("mz_state_event", "")
                )
        );
    }


    private static long sumStages(
            InferenceTrace trace,
            String[] stages
    ) {

        long sum =
                0L;


        for (String stage :
                stages) {

            long duration =
                    trace.durationNanos(
                            stage
                    );


            if (duration > 0L) {

                sum +=
                        duration;
            }
        }


        return sum;
    }

    private static double ms(
            InferenceTrace trace,
            String stage
    ) {

        return trace.durationNanos(
                stage
        ) / 1_000_000.0;
    }

    private SceneTransitionDecision observeSourceScene(
            long frameId,
            SourceFrameStamp sourceFrameStamp,
            SceneChangeDetector.Result sourceScene
    ) {
        SourceFrameStamp safeSourceFrame = sourceFrameStamp == null
                ? SourceFrameStamp.unknown(0L, 0L, 0L)
                : sourceFrameStamp;
        TargetContinuityEvidence targetEvidence = currentTargetEvidence(
                safeSourceFrame.sourceTimestampNanos
        );
        VehicleContinuityEvidence vehicleEvidence = currentVehicleEvidence();
        SceneEvidence evidence = new SceneEvidence(
                        frameId,
                        safeSourceFrame.sourceSequence,
                        safeSourceFrame.sourceTimestampNanos,
                        safeSourceFrame.domain,
                        sourceScene.sceneChanged,
                        clamp01(sourceScene.score),
                        clamp01(sourceScene.changedFraction),
                        clamp01(sourceScene.brightnessDelta),
                        0f,
                        0f,
                        targetEvidence,
                        vehicleEvidence,
                        new MotionExplanationEvidence(
                                motionSensorAvailable,
                                cameraMoving,
                                rapidCameraMotion,
                                angularMotionMagnitude,
                                cameraTransformInProgress,
                                false,
                                0f,
                                0f,
                                0f,
                                0f
                        ),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                );
        SceneTransitionDecision decision = sceneTransitionCoordinator.observe(
                evidence,
                SystemClock.elapsedRealtimeNanos()
        );
        lastSceneEvidence = evidence;
        lastSceneDecision = decision;
        return decision;
    }

    private SceneTransitionDecision handleInternalSceneEvidence(
            InternalSceneEvidence internal,
            ContinuityStamp sourceStamp
    ) {
        if (internal == null || !internal.detected || sourceStamp == null) return null;
        return onPreviewSceneEvidence(
                sourceStamp.sourceFrameStamp(),
                true,
                internal.score,
                internal.changedFraction,
                internal.brightnessDelta,
                0f,
                0f,
                false,
                false
        );
    }

    private InternalSceneEvidence inspectSecondarySceneBeforeInference(
            Bitmap frame
    ) {
        if (frame == null || frame.isRecycled()) {
            return InternalSceneEvidence.none();
        }
        SceneChangeDetector.Result result = rotatedSceneDetector.update(frame);
        if (!result.sceneChanged || cameraTransformInProgress) {
            return InternalSceneEvidence.none();
        }
        return InternalSceneEvidence.detected(
                result.score,
                result.changedFraction,
                result.brightnessDelta
        );
    }

    private void appendSecondaryScenePreflightTelemetry(
            InferenceTrace trace,
            InternalSceneEvidence evidence,
            SceneTransitionDecision decision
    ) {
        boolean detected = evidence != null && evidence.detected;
        String action = decision == null
                ? "NONE" : decision.action.name();
        trace.putCount("secondary_scene_preflight_detected", detected ? 1 : 0);
        trace.putAttribute("secondary_scene_preflight_action", action);
        if (!detected) return;
        boolean skippedInference = decision != null
                && shouldSkipHeavyInference(decision);
        metrics.secondaryScenePreflight(action, skippedInference);
        try {
            JSONObject details = new JSONObject();
            details.put("secondary_scene_preflight_detected", true);
            details.put("secondary_scene_preflight_action", action);
            details.put(
                    "secondary_scene_preflight_skipped_inference",
                    skippedInference
            );
            details.put("score", evidence.score);
            details.put("changed_fraction", evidence.changedFraction);
            details.put("brightness_delta", evidence.brightnessDelta);
            metrics.recordEvent(
                    "secondary_scene_preflight", 0L, 0L, details
            );
        } catch (JSONException ignored) {
            // Telemetry cannot influence the preflight decision.
        }
    }

    private TargetContinuityEvidence currentTargetEvidence(long nowNanos) {
        TargetSnapshot target = targetSnapshot;
        if (target == null || !target.hasTrack()) {
            if (target != null && target.state == TargetSnapshot.State.LOST) {
                return new TargetContinuityEvidence(
                        0L, 0L, 0L, com.example.alpr_v1.continuity.TargetContinuityLevel.LOST,
                        0f, 0, 0f, target.consecutiveFailures,
                        0f, 0f, 0f, 0f, 0f, 0f,
                        false, false, false,
                        target.sourceTimestampNanos > 0L
                                && nowNanos >= target.sourceTimestampNanos
                                ? nowNanos - target.sourceTimestampNanos
                                : Long.MAX_VALUE
                );
            }
            return TargetContinuityEvidence.noTarget();
        }

        VehicleEntity entity = vehicleTrackingCoordinator.repository()
                .findByPlateTrackId(target.trackId);
        if (entity == null && target.lockedTrackId > 0L) {
            entity = vehicleTrackingCoordinator.repository()
                    .findByPlateTrackId(target.lockedTrackId);
        }
        long entityId = entity == null ? 0L : entity.entityId();
        long vehicleTrackId = entity == null ? 0L : entity.vehicleTrackId();
        long plateTrackId = entity != null && entity.plateTrackId() != null
                ? entity.plateTrackId() : target.trackId;
        com.example.alpr_v1.continuity.TargetContinuityLevel level;
        if (target.state == TargetSnapshot.State.DEGRADED) {
            level = com.example.alpr_v1.continuity.TargetContinuityLevel.PREDICTED_ONLY;
        } else if (entity != null && entity.plateTrackId() != null) {
            level = com.example.alpr_v1.continuity.TargetContinuityLevel.VEHICLE_AND_PLATE;
        } else if (entity != null) {
            level = com.example.alpr_v1.continuity.TargetContinuityLevel.VEHICLE_ONLY;
        } else {
            level = com.example.alpr_v1.continuity.TargetContinuityLevel.PLATE_ONLY;
        }

        long latestMeasurementNanos = target.sourceTimestampNanos;
        boolean freshVehicle = false;
        float registrationConsistency = 0f;
        if (entity != null) {
            freshVehicle = nowNanos > 0L
                    && entity.lastMpNanos() > 0L
                    && nowNanos >= entity.lastMpNanos()
                    && nowNanos - entity.lastMpNanos()
                    <= SceneContinuityProfile.INITIAL.reacquireTimeoutNanos;
        }
        long focusedEvidenceAgeNanos = latestMeasurementNanos > 0L
                && nowNanos >= latestMeasurementNanos
                ? nowNanos - latestMeasurementNanos : Long.MAX_VALUE;
        boolean freshPlate = latestMeasurementNanos > 0L
                && target.framesSinceMtAnchor <= 1
                && focusedEvidenceAgeNanos <= 350_000_000L;
        if (entity != null && freshPlate) {
            registrationConsistency = entity.registration().confidence;
        }
        boolean geometryValidated = target.overlayItem != null
                && target.driftScore <= 0.50f
                && target.state != TargetSnapshot.State.DEGRADED;
        float geometryConsistency = 1f - clamp01(target.driftScore);

        return new TargetContinuityEvidence(
                entityId,
                vehicleTrackId,
                Math.max(0L, plateTrackId),
                level,
                target.trackingQuality,
                target.trackerInliers,
                target.supportRatio,
                target.consecutiveFailures,
                geometryConsistency,
                geometryConsistency,
                geometryConsistency,
                0f,
                target.localAppearanceValidated
                        ? target.localAppearanceSimilarity : 0f,
                registrationConsistency,
                freshVehicle,
                freshPlate,
                geometryValidated,
                focusedEvidenceAgeNanos,
                target.localAppearanceValidated
        );
    }

    private VehicleContinuityEvidence currentVehicleEvidence() {
        if (sceneTransitionCoordinator.snapshot().state
                == com.example.alpr_v1.continuity.SceneContinuityState.REACQUIRING
                && lastReacquireVehicleEvidence.entitiesBefore > 0) {
            return lastReacquireVehicleEvidence;
        }
        VehicleTrackingFrame frame = vehicleTrackingCoordinator.latestFrame();
        int entities = frame.candidates.size();
        if (entities == 0) return VehicleContinuityEvidence.empty();

        int measured = 0;
        int predicted = 0;
        long newestAgeNanos = Long.MAX_VALUE;
        for (VehicleCandidate candidate : frame.candidates) {
            if (candidate.predicted) predicted++;
            else measured++;
            newestAgeNanos = Math.min(newestAgeNanos, candidate.predictionAgeNanos);
        }
        float reassociationRatio = measured / (float) entities;
        return new VehicleContinuityEvidence(
                entities,
                entities,
                measured,
                predicted,
                0,
                reassociationRatio,
                0f,
                0f,
                newestAgeNanos == Long.MAX_VALUE ? 0L : newestAgeNanos,
                false,
                false
        );
    }

    private void handleSoftReacquireReport(SoftReacquireReport report) {
        if (report == null || !report.attempted) return;
        android.util.Log.d(
                "ALPR_REACQUIRE_RESULT",
                "result=" + report.result
                        + " reason=" + report.reason
                        + " before=" + report.vehicles.entitiesBefore
                        + " after=" + report.vehicles.entitiesAfter
                        + " measured=" + report.vehicles.freshMeasuredEntities
                        + " predicted=" + report.vehicles.entitiesStillPredicted
                        + " reassociated=" + report.vehicles.entitiesReassociated
                        + " new=" + report.vehicles.newlyCreatedEntities
        );
        lastReacquireVehicleEvidence = report.vehicles;
        long nowNanos = SystemClock.elapsedRealtimeNanos();
        sceneTransitionCoordinator.onSoftReacquireResult(report.result, nowNanos);
        if (shouldRebaseSceneReference(report.result)) {
            sourceSceneDetector.reset();
            rotatedSceneDetector.reset();
        }
        if (report.result == SoftReacquireResult.TARGET_RECOVERED
                || report.result == SoftReacquireResult.VEHICLE_POOL_RECOVERED) {
            JSONObject details = lastSceneDecision == null
                    ? new JSONObject()
                    : continuityEventDetails(
                    lastSceneDecision,
                    sceneTransitionCoordinator.snapshot(),
                    lastSceneEvidence
            );
            putDuration(details, "scene_reacquire", softReacquireStartedNanos, nowNanos);
            metrics.recordEvent("scene_soft_reacquire_succeeded", 0L, 0L, details);
            pendingReacquireSucceededEvents.incrementAndGet();
            pendingReacquireDurationNanos.set(Math.max(
                    0L,
                    softReacquireStartedNanos < 0L
                            ? 0L : nowNanos - softReacquireStartedNanos
            ));
            softReacquireStartedNanos = -1L;
        }
    }

    static boolean shouldRebaseSceneReference(SoftReacquireResult result) {
        return result == SoftReacquireResult.TARGET_RECOVERED
                || result == SoftReacquireResult.VEHICLE_POOL_RECOVERED;
    }

    private boolean shouldSkipHeavyInference(SceneTransitionDecision decision) {
        if (decision == null) return false;
        return decision.action == SceneTransitionAction.HARD_RESET
                || decision.action == SceneTransitionAction.SOFT_REACQUIRE
                || sceneTransitionCoordinator.snapshot().heavyInferenceSuspended;
    }

    private boolean isPreRecoveryFrame(SourceFrameStamp sourceFrameStamp) {
        SourceFrameStamp safe = sourceFrameStamp == null
                ? SourceFrameStamp.unknown(0L, 0L, 0L)
                : sourceFrameStamp;
        return RecoveryFrameGate.shouldSkip(
                sceneTransitionCoordinator.reacquireTelemetry(),
                safe.sourceSequence,
                safe.sourceTimestampNanos,
                safe.domain
        );
    }

    private SourceFrameStamp latestContinuitySourceFrame() {
        SceneContinuitySnapshot snapshot = sceneTransitionCoordinator.snapshot();
        SceneEvidence evidence = lastSceneEvidence;
        if (evidence != null) {
            return new SourceFrameStamp(
                    evidence.sourceSequence,
                    evidence.sourceTimestampNanos,
                    evidence.sourceTimestampDomain,
                    snapshot.sceneGeneration,
                    snapshot.visualEpoch,
                    snapshot.cameraTransformGeneration
            );
        }
        TargetSnapshot target = targetSnapshot;
        if (target != null) {
            return target.continuityStamp().sourceFrameStamp()
                    .withGenerations(
                            snapshot.sceneGeneration,
                            snapshot.visualEpoch,
                            snapshot.cameraTransformGeneration
                    );
        }
        return SourceFrameStamp.unknown(
                snapshot.sceneGeneration,
                snapshot.visualEpoch,
                snapshot.cameraTransformGeneration
        );
    }

    private void recordContinuityFrameSkip(SceneTransitionDecision decision) {
        if (decision == null) return;
        if (decision.action == SceneTransitionAction.HARD_RESET
                || decision.nextState == SceneContinuityState.HARD_RESETTING) {
            metrics.frameSkippedByHardSceneReset();
        } else if (decision.action == SceneTransitionAction.SOFT_HOLD
                || decision.nextState == SceneContinuityState.MOTION_HOLD) {
            metrics.frameSkippedByContinuityHold();
        } else if (decision.action == SceneTransitionAction.SOFT_REACQUIRE
                || decision.nextState == SceneContinuityState.REACQUIRING) {
            metrics.frameSkippedByContinuityReacquire();
        }
    }

    private synchronized void applySceneTransition(SceneTransitionDecision decision) {
        if (decision == null) return;
        SceneContinuitySnapshot snapshot = sceneTransitionCoordinator.snapshot();
        ReacquireTelemetry recovery = sceneTransitionCoordinator.reacquireTelemetry();
        SourceFrameStamp transitionSourceFrame = recovery.active
                ? new SourceFrameStamp(
                        recovery.triggerSourceSequence,
                        recovery.triggerSourceTimestampNanos,
                        recovery.triggerSourceTimestampDomain,
                        snapshot.sceneGeneration,
                        snapshot.visualEpoch,
                        snapshot.cameraTransformGeneration
                )
                : latestContinuitySourceFrame();
        sceneGeneration.set(snapshot.sceneGeneration);
        visualEpoch.set(snapshot.visualEpoch);
        if (decision.incrementSceneGeneration) {
            hardResetRevision.set(decision.revision);
        }
        if (decision.incrementVisualEpoch) {
            visualEpochRevision.set(decision.revision);
        }

        if (decision.action == SceneTransitionAction.HARD_RESET) {
            lastReacquireVehicleEvidence = VehicleContinuityEvidence.empty();
            trackingResetRequested = true;
            rotatedSceneDetector.reset();
            targetSnapshot = TargetSnapshot.searching().withContinuityStamp(
                    sceneTransitionCoordinator.stamp(transitionSourceFrame)
            );
            frameGate.requestImmediateFrame();
        } else if (decision.action == SceneTransitionAction.SOFT_HOLD) {
            if (engine != null) {
                engine.beginSoftHold(snapshot.visualEpoch, decision.reason);
            }
        } else if (decision.action == SceneTransitionAction.SOFT_REACQUIRE) {
            targetSnapshot = targetSnapshot.withState(TargetSnapshot.State.DEGRADED)
                    .withContinuityStamp(
                            sceneTransitionCoordinator.stamp(transitionSourceFrame)
                    );
            if (engine != null) {
                engine.beginSoftReacquire(
                        snapshot.visualEpoch,
                        decision.reason,
                        recovery.startedRuntimeNanos,
                        recovery.triggerSourceSequence,
                        recovery.triggerSourceTimestampNanos
                );
            }
            frameGate.requestImmediateFrame();
        } else if (decision.action == SceneTransitionAction.RELEASE_ACTIVE_TARGET) {
            lastReacquireVehicleEvidence = VehicleContinuityEvidence.empty();
            if (engine != null) engine.releaseFocusedTarget(decision.reason);
            targetSnapshot = TargetSnapshot.searching().withContinuityStamp(
                    sceneTransitionCoordinator.stamp(transitionSourceFrame)
            );
        } else if (decision.action == SceneTransitionAction.NONE
                && snapshot.state
                == com.example.alpr_v1.continuity.SceneContinuityState.STABLE
                && engine != null) {
            engine.endSoftHold(snapshot.visualEpoch, decision.reason);
        }
        recordContinuityEvents(decision, snapshot, lastSceneEvidence);
    }

    private void appendContinuityTelemetry(
            InferenceTrace trace,
            SceneTransitionDecision decision,
            SceneEvidence evidence
    ) {
        if (trace == null || decision == null) return;
        SceneContinuitySnapshot snapshot = sceneTransitionCoordinator.snapshot();
        trace.putAttribute("scene_handling_mode", decision.mode.wireName());
        trace.putAttribute("scene_continuity_profile", "initial_v2");
        trace.putAttribute(
                "visual_change_classification",
                decision.assessment.classification.name()
        );
        trace.putAttribute("scene_transition_action", decision.action.name());
        trace.putAttribute("scene_transition_reason", decision.reason);
        trace.putAttribute("scene_continuity_state", decision.nextState.name());
        trace.putConfidence(
                "target_continuity_score",
                decision.assessment.targetContinuityScore
        );
        trace.putConfidence(
                "vehicle_continuity_score",
                decision.assessment.vehicleContinuityScore
        );
        trace.putConfidence(
                "motion_explanation_score",
                decision.assessment.motionExplanationScore
        );
        trace.putConfidence("cut_evidence_score", decision.assessment.cutEvidenceScore);
        trace.putCount("focused_target_preserved",
                decision.assessment.focusedTargetPreserved ? 1 : 0);
        trace.putCount("scene_generation", snapshot.sceneGeneration);
        trace.putCount("visual_epoch", snapshot.visualEpoch);
        trace.putCount("finalization_suspended",
                snapshot.finalizationSuspended ? 1 : 0);
        appendReacquireTelemetry(trace);

        VisualChangeClassification classification = decision.assessment.classification;
        trace.putCount("raw_visual_change_events",
                evidence != null && evidence.rawVisualChange ? 1 : 0);
        trace.putCount("motion_explained_change_events",
                classification == VisualChangeClassification.MOTION_EXPLAINED_CHANGE ? 1 : 0);
        trace.putCount("unexplained_change_events",
                classification == VisualChangeClassification.UNEXPLAINED_CHANGE ? 1 : 0);
        trace.putCount("continuity_break_events",
                classification == VisualChangeClassification.CONTINUITY_BREAK ? 1 : 0);
        trace.putCount("scene_soft_holds",
                decision.action == SceneTransitionAction.SOFT_HOLD ? 1 : 0);
        trace.putCount("scene_soft_reacquire_started",
                decision.action == SceneTransitionAction.SOFT_REACQUIRE ? 1 : 0);
        trace.putCount("scene_active_target_releases",
                decision.action == SceneTransitionAction.RELEASE_ACTIVE_TARGET ? 1 : 0);
        trace.putCount("scene_hard_resets",
                decision.action == SceneTransitionAction.HARD_RESET ? 1 : 0);
        trace.putCount("scene_soft_reacquire_succeeded",
                pendingReacquireSucceededEvents.getAndSet(0L));
        trace.putCount("scene_soft_reacquire_failed",
                pendingReacquireFailedEvents.getAndSet(0L));
        trace.putCount("scene_stale_results_dropped",
                pendingStaleResultEvents.getAndSet(0L));
        long holdDuration = pendingSoftHoldDurationNanos.getAndSet(0L);
        long reacquireDuration = pendingReacquireDurationNanos.getAndSet(0L);
        if (holdDuration > 0L) trace.putDurationNanos("scene_soft_hold", holdDuration);
        if (reacquireDuration > 0L) {
            trace.putDurationNanos("scene_reacquire", reacquireDuration);
        }

        if (evidence == null) return;
        trace.putConfidence("raw_visual_change_score", evidence.rawVisualChangeScore);
        trace.putAttribute("target_continuity_level", evidence.target.level.name());
        trace.putCount("target_geometry_validated",
                evidence.target.geometryValidated ? 1 : 0);
        trace.putConfidence("target_vehicle_appearance_similarity",
                evidence.target.vehicleAppearanceSimilarity);
        trace.putConfidence("target_plate_appearance_similarity",
                evidence.target.plateAppearanceSimilarity);
        trace.putConfidence("target_registration_consistency",
                evidence.target.registrationConsistency);
        trace.putConfidence("target_kalman_innovation_score",
                evidence.target.kalmanInnovationScore);
        trace.putCount("vehicle_entities_before", evidence.vehicles.entitiesBefore);
        trace.putCount("vehicle_entities_after", evidence.vehicles.entitiesAfter);
        trace.putCount("vehicle_entities_reassociated",
                evidence.vehicles.entitiesReassociated);
        trace.putCount("fresh_mp_measured_entities",
                evidence.vehicles.freshMeasuredEntities);
        trace.putCount("fresh_mp_predicted_entities",
                evidence.vehicles.entitiesStillPredicted);
        trace.putCount("fresh_mp_reassociated_entities",
                evidence.vehicles.entitiesReassociated);
        trace.putCount("vehicle_appearance_agreement_available",
                evidence.vehicles.appearanceAgreementAvailable ? 1 : 0);
        trace.putCount("vehicle_trajectory_agreement_available",
                evidence.vehicles.trajectoryAgreementAvailable ? 1 : 0);
        trace.putConfidence("vehicle_reassociation_ratio",
                evidence.vehicles.reassociationRatio);
        trace.putConfidence("vehicle_trajectory_agreement",
                evidence.vehicles.trajectoryAgreement);
        trace.putCount("camera_motion_available", evidence.motion.gyroAvailable ? 1 : 0);
        trace.putCount("camera_moving", evidence.motion.cameraMoving ? 1 : 0);
        trace.putCount("rapid_camera_motion", evidence.motion.rapidCameraMotion ? 1 : 0);
        trace.putConfidence("camera_motion_magnitude",
                evidence.motion.angularMotionMagnitude);
        trace.putCount("global_motion_estimated",
                evidence.motion.dominantMotionEstimated ? 1 : 0);
        trace.putConfidence("global_motion_coherence",
                evidence.motion.globalMotionCoherence);
        trace.putConfidence("compensated_frame_residual",
                evidence.motion.compensatedFrameResidual);
        long nowNanos = System.nanoTime();
        if (!evidence.target.geometryValidated) {
            withoutValidatedTargetSinceNanos.compareAndSet(-1L, nowNanos);
        } else {
            long missingSince = withoutValidatedTargetSinceNanos.getAndSet(-1L);
            if (missingSince >= 0L) {
                trace.putDurationNanos(
                        "time_without_validated_target_overlay",
                        Math.max(0L, nowNanos - missingSince)
                );
            }
        }
    }

    private void appendReacquireTelemetry(InferenceTrace trace) {
        ReacquireTelemetry recovery = sceneTransitionCoordinator.reacquireTelemetry();
        trace.putCount("reacquire_context_available", recovery.available ? 1 : 0);
        trace.putAttribute(
                "reacquire_result",
                recovery.result.isEmpty() ? "UNAVAILABLE" : recovery.result
        );
        trace.putAttribute(
                "reacquire_trigger_classification",
                recovery.triggerClassification == null
                        ? "UNAVAILABLE" : recovery.triggerClassification.name()
        );
        trace.putCount("reacquire_active_target_present",
                recovery.activeTargetPresent ? 1 : 0);
        trace.putCount("reacquire_vehicle_pool_recovered",
                recovery.vehiclePoolRecovered ? 1 : 0);
        trace.putCount("reacquire_deadline_reached",
                recovery.deadlineReached ? 1 : 0);
        if (recovery.available) {
            trace.putConfidence("reacquire_trigger_cut_score", recovery.triggerCutScore);
            trace.putConfidence("reacquire_max_cut_score", recovery.maximumCutScore);
            trace.putAttribute(
                    "reacquire_started_runtime_nanos",
                    String.valueOf(recovery.startedRuntimeNanos)
            );
            trace.putAttribute(
                    "reacquire_trigger_source_sequence",
                    String.valueOf(recovery.triggerSourceSequence)
            );
            trace.putAttribute(
                    "reacquire_trigger_source_timestamp_nanos",
                    String.valueOf(recovery.triggerSourceTimestampNanos)
            );
            trace.putAttribute(
                    "reacquire_trigger_source_timestamp_domain",
                    recovery.triggerSourceTimestampDomain.name()
            );
            trace.putAttribute(
                    "runtime_timestamp_domain", "elapsed_realtime_nanos"
            );
        }
    }

    private void recordContinuityEvents(
            SceneTransitionDecision decision,
            SceneContinuitySnapshot snapshot,
            SceneEvidence evidence
    ) {
        if (decision.revision <= lastContinuityTelemetryRevision) return;
        lastContinuityTelemetryRevision = decision.revision;
        long nowNanos = SystemClock.elapsedRealtimeNanos();
        JSONObject details = continuityEventDetails(decision, snapshot, evidence);
        VisualChangeClassification classification = decision.assessment.classification;
        if (evidence != null && evidence.rawVisualChange) {
            metrics.recordEvent("raw_visual_change_detected", 0L, 0L, details);
        }
        if (classification == VisualChangeClassification.MOTION_EXPLAINED_CHANGE) {
            metrics.recordEvent("motion_explained_change", 0L, 0L, details);
        } else if (classification == VisualChangeClassification.UNEXPLAINED_CHANGE) {
            metrics.recordEvent("unexplained_change_detected", 0L, 0L, details);
        } else if (classification == VisualChangeClassification.CONTINUITY_BREAK) {
            metrics.recordEvent("continuity_break_confirmed", 0L, 0L, details);
        }

        if (decision.action == SceneTransitionAction.SOFT_HOLD) {
            softHoldStartedNanos = nowNanos;
            metrics.recordEvent("scene_soft_hold_started", 0L, 0L, details);
        }
        if (lastAppliedContinuityState == SceneContinuityState.MOTION_HOLD
                && snapshot.state != SceneContinuityState.MOTION_HOLD
                && softHoldStartedNanos >= 0L) {
            putDuration(details, "scene_soft_hold", softHoldStartedNanos, nowNanos);
            pendingSoftHoldDurationNanos.set(Math.max(
                    0L,
                    nowNanos - softHoldStartedNanos
            ));
            metrics.recordEvent("scene_soft_hold_finished", 0L, 0L, details);
            softHoldStartedNanos = -1L;
        }
        if (decision.action == SceneTransitionAction.SOFT_REACQUIRE) {
            softReacquireStartedNanos = nowNanos;
            metrics.recordEvent("scene_soft_reacquire_started", 0L, 0L, details);
        } else if (decision.action == SceneTransitionAction.RELEASE_ACTIVE_TARGET) {
            metrics.recordEvent("scene_active_target_released", 0L, 0L, details);
        } else if (decision.action == SceneTransitionAction.HARD_RESET) {
            if (lastAppliedContinuityState == SceneContinuityState.REACQUIRING) {
                putDuration(details, "scene_reacquire", softReacquireStartedNanos, nowNanos);
                metrics.recordEvent("scene_soft_reacquire_failed", 0L, 0L, details);
                pendingReacquireFailedEvents.incrementAndGet();
                pendingReacquireDurationNanos.set(Math.max(
                        0L,
                        softReacquireStartedNanos < 0L
                                ? 0L : nowNanos - softReacquireStartedNanos
                ));
            }
            metrics.recordEvent("scene_hard_reset", 0L, 0L, details);
        }
        lastAppliedContinuityState = snapshot.state;
    }

    private JSONObject continuityEventDetails(
            SceneTransitionDecision decision,
            SceneContinuitySnapshot snapshot,
            SceneEvidence evidence
    ) {
        JSONObject details = new JSONObject();
        try {
            details.put("scene_handling_mode", decision.mode.wireName());
            details.put("scene_continuity_profile", "initial_v2");
            details.put("visual_change_classification",
                    decision.assessment.classification.name());
            details.put("scene_transition_action", decision.action.name());
            details.put("scene_transition_reason", decision.reason);
            details.put("scene_continuity_state", snapshot.state.name());
            details.put("target_continuity_score",
                    decision.assessment.targetContinuityScore);
            details.put("vehicle_continuity_score",
                    decision.assessment.vehicleContinuityScore);
            details.put("motion_explanation_score",
                    decision.assessment.motionExplanationScore);
            details.put("cut_evidence_score", decision.assessment.cutEvidenceScore);
            details.put("scene_generation", snapshot.sceneGeneration);
            details.put("visual_epoch", snapshot.visualEpoch);
            details.put("finalization_suspended", snapshot.finalizationSuspended);
            ReacquireTelemetry recovery = sceneTransitionCoordinator.reacquireTelemetry();
            details.put("reacquire_context_available", recovery.available);
            details.put("reacquire_result",
                    recovery.result.isEmpty() ? "UNAVAILABLE" : recovery.result);
            details.put("reacquire_trigger_classification",
                    recovery.triggerClassification == null
                            ? "UNAVAILABLE" : recovery.triggerClassification.name());
            details.put("reacquire_active_target_present",
                    recovery.activeTargetPresent);
            details.put("reacquire_vehicle_pool_recovered",
                    recovery.vehiclePoolRecovered);
            details.put("reacquire_deadline_reached", recovery.deadlineReached);
            if (recovery.available) {
                details.put("reacquire_trigger_cut_score", recovery.triggerCutScore);
                details.put("reacquire_max_cut_score", recovery.maximumCutScore);
                details.put(
                        "reacquire_started_runtime_nanos",
                        recovery.startedRuntimeNanos
                );
                details.put(
                        "reacquire_trigger_source_sequence",
                        recovery.triggerSourceSequence
                );
                details.put(
                        "reacquire_trigger_source_timestamp_nanos",
                        recovery.triggerSourceTimestampNanos
                );
                details.put(
                        "reacquire_trigger_source_timestamp_domain",
                        recovery.triggerSourceTimestampDomain.name()
                );
                details.put(
                        "runtime_timestamp_domain", "elapsed_realtime_nanos"
                );
            }
            if (evidence != null) {
                details.put("source_sequence", evidence.sourceSequence);
                details.put(
                        "source_timestamp_domain",
                        evidence.sourceTimestampDomain.name()
                );
                details.put("raw_visual_change_score", evidence.rawVisualChangeScore);
                details.put("target_continuity_level", evidence.target.level.name());
                details.put("target_geometry_validated", evidence.target.geometryValidated);
                details.put("vehicle_entities_before", evidence.vehicles.entitiesBefore);
                details.put("vehicle_entities_after", evidence.vehicles.entitiesAfter);
                details.put("vehicle_entities_reassociated",
                        evidence.vehicles.entitiesReassociated);
                details.put("fresh_mp_measured_entities",
                        evidence.vehicles.freshMeasuredEntities);
                details.put("fresh_mp_predicted_entities",
                        evidence.vehicles.entitiesStillPredicted);
                details.put("fresh_mp_reassociated_entities",
                        evidence.vehicles.entitiesReassociated);
                details.put("vehicle_appearance_agreement_available",
                        evidence.vehicles.appearanceAgreementAvailable);
                details.put("vehicle_trajectory_agreement_available",
                        evidence.vehicles.trajectoryAgreementAvailable);
                details.put("vehicle_reassociation_ratio",
                        evidence.vehicles.reassociationRatio);
                details.put("camera_motion_available", evidence.motion.gyroAvailable);
                details.put("camera_moving", evidence.motion.cameraMoving);
                details.put("rapid_camera_motion", evidence.motion.rapidCameraMotion);
                details.put("camera_motion_magnitude",
                        evidence.motion.angularMotionMagnitude);
            }
        } catch (JSONException ignored) {
            // Telemetry must never influence runtime scene policy.
        }
        return details;
    }

    private static void putDuration(
            JSONObject details,
            String name,
            long startedNanos,
            long nowNanos
    ) {
        if (startedNanos < 0L) return;
        try {
            details.put(name + "_ms", Math.max(0L, nowNanos - startedNanos)
                    / 1_000_000.0);
        } catch (JSONException ignored) {
            // Best-effort telemetry only.
        }
    }

    private PipelineResult stampAndValidateResult(
            PipelineResult result,
            ContinuityStamp processingStamp
    ) {
        if (result == null) return null;
        PipelineResult stamped = result.withContinuityStamp(processingStamp);
        ContinuityResultDisposition disposition = continuityGenerationGate.evaluate(
                sceneTransitionCoordinator.stamp(processingStamp.sourceTimestampNanos),
                processingStamp
        );
        if (disposition == ContinuityResultDisposition.REJECT_ALL) {
            stamped.close();
            frameGate.requestImmediateFrame();
            JSONObject details = new JSONObject();
            try {
                details.put("result_scene_generation", processingStamp.sceneGeneration);
                details.put("current_scene_generation",
                        sceneTransitionCoordinator.snapshot().sceneGeneration);
            } catch (JSONException ignored) {
                // Best-effort telemetry only.
            }
            metrics.recordEvent("scene_stale_result_dropped", 0L, 0L, details);
            pendingStaleResultEvents.incrementAndGet();
            return null;
        }
        if (disposition != ContinuityResultDisposition.ACCEPT_ALL
                || sceneTransitionCoordinator.snapshot().finalizationSuspended) {
            return stamped.withoutGeometryAndFinalization();
        }
        return stamped;
    }

    private void finishStaleResultTrace(InferenceTrace trace) {
        trace.stop("total");
        appendTimingAudit(trace);
        trace.finish("stale_scene_result", "");
        trace.captureMemoryAfterMeasurement();
        metrics.add(trace);
    }

    public void invalidateModels() {
        reloadRequested = true;
    }

    public synchronized void requestTrackingReset() {
        SceneTransitionDecision decision = sceneTransitionCoordinator.requestStructuralReset(
                "external_tracking_reset",
                SystemClock.elapsedRealtimeNanos()
        );
        lastSceneDecision = decision;
        lastSceneEvidence = null;
        applySceneTransition(decision);
    }


    public synchronized void setRecognitionProfile(RecognitionProfile profile) {
        recognitionProfile = profile == null ? RecognitionProfile.BALANCED : profile;
        if (engine != null) engine.setRecognitionProfile(recognitionProfile);
    }

    public synchronized void resetTracking() {
        applySceneTransition(sceneTransitionCoordinator.requestStructuralReset(
                "explicit_pipeline_reset",
                SystemClock.elapsedRealtimeNanos()
        ));
        if (engine != null && trackingResetRequested) {
            engine.hardResetScene("explicit_pipeline_reset");
            trackingResetRequested = false;
        }
        sourceSceneDetector.reset();
        rotatedSceneDetector.reset();
    }



    private RoiBudgetPolicy effectiveRoiBudgetPolicy() {
        if (experimentModeEnabled) {
            return experimentRoiBudgetPolicy;
        }

        return vehicleCascadeEnabled
                ? RoiBudgetPolicy.TWO_ROI
                : RoiBudgetPolicy.FULL_FRAME;
    }

    private MtExecutionPolicy effectiveMtExecutionPolicy() {
        return MtExecutionPolicy.forExperiment(experimentModeEnabled);
    }

    private MtFallbackPolicy effectiveMtFallbackPolicy() {
        return MtFallbackPolicy.forExperiment(experimentModeEnabled);
    }

    private VehicleTrackingPolicy effectiveVehicleTrackingPolicy() {
        return VehicleTrackingPolicy.forExperiment(experimentModeEnabled);
    }
    public synchronized void setVehicleCascadeEnabled(boolean enabled) {
        RoiBudgetPolicy previousEffective =
                effectiveRoiBudgetPolicy();

        vehicleCascadeEnabled = enabled;

        metrics.setVehicleCascadeEnabled(enabled);

        RoiBudgetPolicy currentEffective =
                effectiveRoiBudgetPolicy();

        metrics.setRoiBudgetPolicy(
                currentEffective.wireName()
        );

        /*
         * Jeżeli działa EXP, zmiana normalnej konfiguracji nie musi
         * wpływać na aktualnie wykonywany pipeline.
         */
        if (previousEffective == currentEffective) {
            return;
        }

        if (engine != null) {
            engine.resetTracking();
        }

        reloadRequested = true;
    }

    public synchronized void setExperimentConfiguration(
            boolean enabled,
            RoiBudgetPolicy roiPolicy
    ) {
        RoiBudgetPolicy previousEffective =
                effectiveRoiBudgetPolicy();
        MtExecutionPolicy previousExecution = effectiveMtExecutionPolicy();
        MtFallbackPolicy previousFallback = effectiveMtFallbackPolicy();

        experimentModeEnabled = enabled;
        experimentRoiBudgetPolicy =
                roiPolicy == null
                        ? RoiBudgetPolicy.TWO_ROI
                        : roiPolicy;

        RoiBudgetPolicy currentEffective =
                effectiveRoiBudgetPolicy();
        MtExecutionPolicy currentExecution = effectiveMtExecutionPolicy();
        MtFallbackPolicy currentFallback = effectiveMtFallbackPolicy();

        metrics.setExperimentConfiguration(
                experimentModeEnabled,
                experimentRoiBudgetPolicy.wireName()
        );

        metrics.setRoiBudgetPolicy(
                currentEffective.wireName()
        );

        if (previousEffective == currentEffective
                && previousExecution == currentExecution
                && previousFallback == currentFallback) {
            return;
        }

        if (engine != null) {
            engine.resetTracking();
        }

        reloadRequested = true;
    }

    public synchronized void setRapidCameraMotion(boolean rapid) {
        rapidCameraMotion = rapid;
        if (rapid) cameraMoving = true;
        if (engine != null) engine.setRapidCameraMotion(rapid);
    }

    public synchronized void setCameraMotionEvidence(
            boolean sensorAvailable,
            boolean moving,
            boolean rapid,
            float angularMagnitude
    ) {
        motionSensorAvailable = sensorAvailable;
        cameraMoving = moving;
        rapidCameraMotion = rapid;
        angularMotionMagnitude = Float.isFinite(angularMagnitude)
                ? Math.max(0f, angularMagnitude) : 0f;
        if (engine != null) engine.setRapidCameraMotion(rapid);
    }

    public synchronized void setSceneHandlingMode(SceneHandlingMode mode) {
        SceneHandlingMode safeMode = mode == null
                ? SceneHandlingMode.STRICT_SCENE_BOUNDARY : mode;
        sceneTransitionCoordinator.setMode(
                safeMode,
                SystemClock.elapsedRealtimeNanos()
        );
        metrics.setSceneContinuityConfiguration(safeMode.wireName(), "initial_v2");
    }

    public void setCameraTransformInProgress(boolean inProgress) {
        cameraTransformInProgress = inProgress;
    }

    public void setCurrentCameraZoomRatio(float zoomRatio) {
        currentCameraZoomRatio = Math.max(1f, zoomRatio);
    }

    public synchronized void setTargetSnapshot(TargetSnapshot snapshot) {
        TargetSnapshot safeSnapshot = snapshot == null
                ? TargetSnapshot.searching() : snapshot;
        targetSnapshot = safeSnapshot.withContinuityStamp(
                sceneTransitionCoordinator.stamp(
                        safeSnapshot.continuityStamp().sourceFrameStamp()
                )
        );
        long now = SystemClock.elapsedRealtimeNanos();
        long previous = lastPreviewTrackingNanos.getAndSet(now);
        if (previous > 0L && now > previous) {
            double instantFps = Math.min(120.0, 1_000_000_000.0 / (now - previous));
            overlayUpdateFps = overlayUpdateFps <= 0.0
                    ? instantFps
                    : 0.82 * overlayUpdateFps + 0.18 * instantFps;
        }
        previewTrackingUpdates.incrementAndGet();
    }

    public synchronized boolean setTargetSnapshotIfCurrent(
            TargetSnapshot snapshot,
            ContinuityStamp sourceStamp
    ) {
        if (!isCurrentContinuityStamp(sourceStamp)) return false;
        setTargetSnapshot(snapshot == null
                ? null : snapshot.withContinuityStamp(sourceStamp));
        return true;
    }

    public synchronized void requestImmediateTargetRecovery() {
        SourceFrameStamp triggerSourceFrame = latestContinuitySourceFrame();
        SceneTransitionDecision decision = sceneTransitionCoordinator.requestSoftReacquire(
                "immediate_target_recovery",
                SystemClock.elapsedRealtimeNanos(),
                triggerSourceFrame
        );
        lastSceneDecision = decision;
        lastSceneEvidence = null;
        applySceneTransition(decision);
    }

    public void setAutoZoomTargetLock(
            long targetTrackId,
            float left,
            float top,
            float right,
            float bottom
    ) {
        float safeLeft = clamp01(Math.min(left, right));
        float safeTop = clamp01(Math.min(top, bottom));
        float safeRight = clamp01(Math.max(left, right));
        float safeBottom = clamp01(Math.max(top, bottom));
        if (safeRight - safeLeft < 0.02f
                || safeBottom - safeTop < 0.02f) {
            clearAutoZoomTargetRoi();
            return;
        }
        AutoZoomTargetConfig previous = autoZoomTargetConfig;
        long revision = previous.active
                ? previous.revision
                : previous.revision + 1L;
        autoZoomTargetConfig = new AutoZoomTargetConfig(
                true,
                revision,
                targetTrackId,
                safeLeft,
                safeTop,
                safeRight,
                safeBottom
        );
    }

    public void clearAutoZoomTargetRoi() {
        AutoZoomTargetConfig previous = autoZoomTargetConfig;
        if (!previous.active) return;
        autoZoomTargetConfig = AutoZoomTargetConfig.disabled(
                previous.revision + 1L
        );
    }

    public synchronized void finishCameraTransform() {
        boolean transformWasActive = cameraTransformInProgress;
        cameraTransformInProgress = false;
        if (transformWasActive) {
            sceneTransitionCoordinator.advanceCameraTransformGeneration(
                    SystemClock.elapsedRealtimeNanos()
            );
        }
        sourceSceneDetector.reset();
        rotatedSceneDetector.reset();
        if (engine != null) {
            engine.setCameraTransformInProgress(false);
            engine.invalidateVehicleBackgroundAfterCameraTransform();
        }
    }

    public void finishCameraTransform(float fromZoomRatio, float toZoomRatio) {
        float from = Math.max(0.1f, fromZoomRatio);
        float to = Math.max(0.1f, toZoomRatio);
        synchronized (cameraTransformLock) {
            pendingCameraZoomRatio *= to / from;
            cameraTransformFinishPending = true;
        }
        boolean transformWasActive = cameraTransformInProgress;
        cameraTransformInProgress = false;
        if (transformWasActive) {
            sceneTransitionCoordinator.advanceCameraTransformGeneration(
                    SystemClock.elapsedRealtimeNanos()
            );
        }
        sourceSceneDetector.reset();
        rotatedSceneDetector.reset();
    }

    public synchronized void close() {
        if (engine != null) engine.close();
        engine = null;
        reloadRequested = false;
        sourceSceneDetector.reset();
        rotatedSceneDetector.reset();
    }

    /** Immutable entity-aware snapshot for the Phase 3 acquisition controller. */
    public VehicleTrackingFrame latestVehicleTrackingFrame() {
        VehicleTrackingFrame frame = vehicleTrackingCoordinator.latestFrame();
        return frame.withContinuityStamp(
                sceneTransitionCoordinator.stamp(
                        frame.continuityStamp().sourceFrameStamp()
                )
        );
    }

    public SceneContinuitySnapshot sceneContinuitySnapshot() {
        return sceneTransitionCoordinator.snapshot();
    }

    public ReacquireTelemetry reacquireTelemetry() {
        return sceneTransitionCoordinator.reacquireTelemetry();
    }

    public synchronized SceneTransitionDecision onPreviewSceneEvidence(
            long sourceTimestampNanos,
            boolean rawVisualChange,
            float rawVisualChangeScore,
            float changedFraction,
            float brightnessDelta,
            float anchorDriftScore,
            float anchorChangedFraction
    ) {
        return onPreviewSceneEvidence(
                new SourceFrameStamp(
                        0L,
                        Math.max(0L, sourceTimestampNanos),
                        SourceTimestampDomain.UNKNOWN,
                        0L, 0L, 0L
                ),
                rawVisualChange,
                rawVisualChangeScore,
                changedFraction,
                brightnessDelta,
                anchorDriftScore,
                anchorChangedFraction,
                false,
                false
        );
    }

    public synchronized SceneTransitionDecision onPreviewSceneEvidence(
            long sourceTimestampNanos,
            boolean rawVisualChange,
            float rawVisualChangeScore,
            float changedFraction,
            float brightnessDelta,
            float anchorDriftScore,
            float anchorChangedFraction,
            boolean focusedTrackingLost,
            boolean focusedTrackingDegraded
    ) {
        return onPreviewSceneEvidence(
                new SourceFrameStamp(
                        0L,
                        Math.max(0L, sourceTimestampNanos),
                        SourceTimestampDomain.UNKNOWN,
                        0L, 0L, 0L
                ),
                rawVisualChange,
                rawVisualChangeScore,
                changedFraction,
                brightnessDelta,
                anchorDriftScore,
                anchorChangedFraction,
                focusedTrackingLost,
                focusedTrackingDegraded
        );
    }

    public synchronized SceneTransitionDecision onPreviewSceneEvidence(
            SourceFrameStamp sourceFrameStamp,
            boolean rawVisualChange,
            float rawVisualChangeScore,
            float changedFraction,
            float brightnessDelta,
            float anchorDriftScore,
            float anchorChangedFraction,
            boolean focusedTrackingLost,
            boolean focusedTrackingDegraded
    ) {
        SourceFrameStamp safeSourceFrame = sourceFrameStamp == null
                ? SourceFrameStamp.unknown(0L, 0L, 0L)
                : sourceFrameStamp;
        TargetContinuityEvidence targetEvidence = currentTargetEvidence(
                safeSourceFrame.sourceTimestampNanos
        );
        SceneEvidence evidence = new SceneEvidence(
                        previewEvidenceIds.incrementAndGet(),
                        safeSourceFrame.sourceSequence,
                        safeSourceFrame.sourceTimestampNanos,
                        safeSourceFrame.domain,
                        rawVisualChange,
                        clamp01(rawVisualChangeScore),
                        clamp01(changedFraction),
                        clamp01(brightnessDelta),
                        clamp01(anchorDriftScore),
                        clamp01(anchorChangedFraction),
                        targetEvidence,
                        currentVehicleEvidence(),
                        new MotionExplanationEvidence(
                                motionSensorAvailable,
                                cameraMoving,
                                rapidCameraMotion,
                                angularMotionMagnitude,
                                cameraTransformInProgress,
                                false,
                                0f,
                                0f,
                                0f,
                                0f
                        ),
                        focusedTrackingLost,
                        focusedTrackingDegraded,
                        false,
                        false,
                        false,
                        false
                );
        SceneTransitionDecision decision = sceneTransitionCoordinator.observe(
                evidence,
                SystemClock.elapsedRealtimeNanos()
        );
        if (rawVisualChange || decision.action != SceneTransitionAction.NONE) {
            android.util.Log.d(
                    "ALPR_SCENE_EVIDENCE",
                    String.format(
                            java.util.Locale.ROOT,
                            "raw=%s focused_lost=%s focused_degraded=%s "
                                    + "score=%.3f fraction=%.3f local_valid=%s "
                                    + "local_similarity=%.3f target=%.3f vehicle=%.3f "
                                    + "moving=%s rapid=%s angular=%.3f "
                                    + "class=%s action=%s reason=%s",
                            rawVisualChange,
                            focusedTrackingLost,
                            focusedTrackingDegraded,
                            rawVisualChangeScore,
                            changedFraction,
                            targetEvidence.localAppearanceValidated,
                            targetEvidence.plateAppearanceSimilarity,
                            decision.assessment.targetContinuityScore,
                            decision.assessment.vehicleContinuityScore,
                            cameraMoving,
                            rapidCameraMotion,
                            angularMotionMagnitude,
                            decision.assessment.classification,
                            decision.action,
                            decision.reason
                    )
            );
        }
        lastSceneEvidence = evidence;
        lastSceneDecision = decision;
        applySceneTransition(decision);
        return decision;
    }

    public boolean isCurrentContinuityStamp(ContinuityStamp stamp) {
        return stamp != null
                && continuityGenerationGate.evaluate(
                sceneTransitionCoordinator.stamp(stamp.sourceFrameStamp()),
                stamp
                ) == ContinuityResultDisposition.ACCEPT_ALL;
    }

    public ContinuityStamp currentContinuityStamp(long sourceTimestampNanos) {
        return sceneTransitionCoordinator.stamp(Math.max(0L, sourceTimestampNanos));
    }

    public ContinuityStamp currentContinuityStamp(
            SourceFrameStamp sourceFrameStamp
    ) {
        return sceneTransitionCoordinator.stamp(sourceFrameStamp);
    }

    private void recordVehicleTrackingEvents() {
        for (VehicleTrackingEvent event : vehicleTrackingCoordinator.drainEvents()) {
            try {
                JSONObject details = new JSONObject();
                if (event.entityId > 0L) details.put("entity_id", event.entityId);
                if (event.vehicleTrackId > 0L) {
                    details.put("vehicle_track_id", event.vehicleTrackId);
                }
                if (event.plateTrackId > 0L) {
                    details.put("plate_track_id", event.plateTrackId);
                }
                details.put("scene_generation", event.sceneGeneration);
                details.put("event_elapsed_ms", event.elapsedNanos / 1_000_000.0);
                details.put("reason", event.reason);
                metrics.recordEvent(
                        event.eventType,
                        event.frameId,
                        event.vehicleTrackId,
                        details
                );
            } catch (JSONException ignored) {
                // Pola zdarzenia pochodzą wyłącznie z typów prostych.
            }
        }
    }

    private static float[] sampleLuminance(ImageProxy image) {
        final int gridX = 20;
        final int gridY = 20;
        float[] samples = new float[gridX * gridY];
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return samples;
        ImageProxy.PlaneProxy yPlane = planes[0];
        ByteBuffer buffer = yPlane.getBuffer().duplicate();
        Rect crop = image.getCropRect();
        int cropWidth = Math.max(1, crop.width());
        int cropHeight = Math.max(1, crop.height());
        int rowStride = yPlane.getRowStride();
        int pixelStride = yPlane.getPixelStride();
        int base = buffer.position();
        int limit = buffer.limit();
        int output = 0;
        for (int gy = 0; gy < gridY; gy++) {
            int y = crop.top + Math.min(
                    cropHeight - 1,
                    Math.round((gy + 0.5f) * cropHeight / gridY)
            );
            for (int gx = 0; gx < gridX; gx++) {
                int x = crop.left + Math.min(
                        cropWidth - 1,
                        Math.round((gx + 0.5f) * cropWidth / gridX)
                );
                int index = base + y * rowStride + x * pixelStride;
                samples[output++] = index >= base && index < limit
                        ? buffer.get(index) & 0xff
                        : 0f;
            }
        }
        return samples;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
