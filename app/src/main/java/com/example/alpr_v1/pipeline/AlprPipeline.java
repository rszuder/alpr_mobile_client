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
import com.example.alpr_v1.continuity.SceneContinuityProfile;
import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneEvidence;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.TargetContinuityEvidence;
import com.example.alpr_v1.continuity.VehicleContinuityEvidence;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
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
                int sourceHeight
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
    private final SceneTransitionCoordinator sceneTransitionCoordinator;
    private final ContinuityGenerationGate continuityGenerationGate =
            new ContinuityGenerationGate();
    private final VehicleTrackingCoordinator vehicleTrackingCoordinator =
            new VehicleTrackingCoordinator();
    private final AtomicLong frameIds = new AtomicLong();
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
        metrics.observeSourceFrame(
                image.getWidth(),
                image.getHeight(),
                sourceTimestampNanos
        );
        if (cameraTransformInProgress) {
            metrics.frameSkippedByCameraTransform();
            return null;
        }
        long frameId = frameIds.incrementAndGet();
        SceneChangeDetector.Result sourceScene = sourceSceneDetector.updateSamples(
                sampleLuminance(image),
                image.getWidth(),
                image.getHeight()
        );
        if (sourceScene.sceneChanged) {
            applySceneTransition(observeSourceScene(
                    frameId,
                    sourceTimestampNanos,
                    sourceScene
            ));
            metrics.frameSkippedBySceneChange();
            if (sceneChangeCallback != null) {
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
                sourceTimestampNanos
        );
        InferenceTrace trace = new InferenceTrace(frameId, processingStamp);
        trace.putAttribute("source_timestamp_nanos", String.valueOf(sourceTimestampNanos));
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

                    engine.resetTracking();

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
                    engine.resetSceneDetectorReference();
                }

            } finally {

                trace.stop(
                        "engine_setup"
                );
            }
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
            PipelineResult result;
            final long processingHardResetRevision = hardResetRevision.get();
            final long processingVisualEpochRevision = visualEpochRevision.get();


            trace.start(
                    "engine_total"
            );

            try {

                result =
                        engine.run(
                                frame,
                                trace,
                                sourceTimestampNanos,
                                plateDetectionCallback,
                                () -> hardResetRevision.get()
                                        != processingHardResetRevision
                                        || visualEpochRevision.get()
                                        != processingVisualEpochRevision
                        );

            } finally {

                trace.stop(
                        "engine_total"
                );
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
            return new PipelineResult(
                    "pipeline_error",
                    "Błąd pipeline'u: " + e.getMessage(),
                    "",
                    0,
                    Collections.emptyList()
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
        if (frame == null || frame.isRecycled()) return null;
        metrics.observeSourceFrame(frame.getWidth(), frame.getHeight(), sourceTimestampNanos);
        if (cameraTransformInProgress) {
            metrics.frameSkippedByCameraTransform();
            return null;
        }
        long frameId = frameIds.incrementAndGet();
        SceneChangeDetector.Result sourceScene = sourceSceneDetector.update(frame);
        if (sourceScene.sceneChanged) {
            applySceneTransition(observeSourceScene(
                    frameId,
                    sourceTimestampNanos,
                    sourceScene
            ));
            metrics.frameSkippedBySceneChange();
            if (sceneChangeCallback != null) {
                sceneChangeCallback.onSceneChanged(sourceScene.score, sourceScene.changedFraction);
            }
            return null;
        }
        if (!frameGate.shouldProcess(frameId)) {
            metrics.frameSkippedByGate();
            return null;
        }

        ContinuityStamp processingStamp = sceneTransitionCoordinator.stamp(
                sourceTimestampNanos
        );
        InferenceTrace trace = new InferenceTrace(frameId, processingStamp);
        trace.putAttribute("source_timestamp_nanos", String.valueOf(sourceTimestampNanos));
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
                    engine.resetTracking();
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
                    engine.resetSceneDetectorReference();
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
                        sourceTimestampNanos,
                        plateDetectionCallback,
                        () -> hardResetRevision.get() != processingHardResetRevision
                                || visualEpochRevision.get()
                                != processingVisualEpochRevision
                );
            } finally {
                trace.stop("engine_total");
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
            return new PipelineResult(
                    "pipeline_error",
                    "Błąd pipeline'u: " + error.getMessage(),
                    "",
                    0,
                    Collections.emptyList()
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
            long sourceTimestampNanos,
            SceneChangeDetector.Result sourceScene
    ) {
        return sceneTransitionCoordinator.observe(
                new SceneEvidence(
                        frameId,
                        sourceTimestampNanos,
                        sourceScene.sceneChanged,
                        clamp01(sourceScene.score),
                        clamp01(sourceScene.changedFraction),
                        clamp01(sourceScene.brightnessDelta),
                        0f,
                        0f,
                        TargetContinuityEvidence.noTarget(),
                        VehicleContinuityEvidence.empty(),
                        new MotionExplanationEvidence(
                                false,
                                rapidCameraMotion,
                                rapidCameraMotion,
                                0f,
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
                        false
                ),
                System.nanoTime()
        );
    }

    private synchronized void applySceneTransition(SceneTransitionDecision decision) {
        if (decision == null) return;
        SceneContinuitySnapshot snapshot = sceneTransitionCoordinator.snapshot();
        sceneGeneration.set(snapshot.sceneGeneration);
        visualEpoch.set(snapshot.visualEpoch);
        if (decision.incrementSceneGeneration) {
            hardResetRevision.set(decision.revision);
        }
        if (decision.incrementVisualEpoch) {
            visualEpochRevision.set(decision.revision);
        }

        if (decision.action == SceneTransitionAction.HARD_RESET) {
            trackingResetRequested = true;
            targetSnapshot = TargetSnapshot.searching().withContinuityStamp(
                    sceneTransitionCoordinator.stamp(System.nanoTime())
            );
            frameGate.requestImmediateFrame();
        } else if (decision.action == SceneTransitionAction.SOFT_REACQUIRE) {
            frameGate.requestImmediateFrame();
        } else if (decision.action == SceneTransitionAction.RELEASE_ACTIVE_TARGET) {
            targetSnapshot = TargetSnapshot.searching().withContinuityStamp(
                    sceneTransitionCoordinator.stamp(System.nanoTime())
            );
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
            return null;
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

    public void requestTrackingReset() {
        applySceneTransition(sceneTransitionCoordinator.requestStructuralReset(
                "external_tracking_reset",
                System.nanoTime()
        ));
    }


    public synchronized void setRecognitionProfile(RecognitionProfile profile) {
        recognitionProfile = profile == null ? RecognitionProfile.BALANCED : profile;
        if (engine != null) engine.setRecognitionProfile(recognitionProfile);
    }

    public synchronized void resetTracking() {
        if (engine != null) engine.resetTracking();
        targetSnapshot = TargetSnapshot.searching();
        sourceSceneDetector.reset();
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
        if (engine != null) engine.setRapidCameraMotion(rapid);
    }

    public void setCameraTransformInProgress(boolean inProgress) {
        cameraTransformInProgress = inProgress;
    }

    public void setCurrentCameraZoomRatio(float zoomRatio) {
        currentCameraZoomRatio = Math.max(1f, zoomRatio);
    }

    public void setTargetSnapshot(TargetSnapshot snapshot) {
        TargetSnapshot safeSnapshot = snapshot == null
                ? TargetSnapshot.searching() : snapshot;
        targetSnapshot = safeSnapshot.withContinuityStamp(
                sceneTransitionCoordinator.stamp(safeSnapshot.updatedAtNanos)
        );
        long now = System.nanoTime();
        long previous = lastPreviewTrackingNanos.getAndSet(now);
        if (previous > 0L && now > previous) {
            double instantFps = Math.min(120.0, 1_000_000_000.0 / (now - previous));
            overlayUpdateFps = overlayUpdateFps <= 0.0
                    ? instantFps
                    : 0.82 * overlayUpdateFps + 0.18 * instantFps;
        }
        previewTrackingUpdates.incrementAndGet();
    }

    public void requestImmediateTargetRecovery() {
        applySceneTransition(sceneTransitionCoordinator.requestSoftReacquire(
                "immediate_target_recovery",
                System.nanoTime()
        ));
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
            sceneTransitionCoordinator.advanceCameraTransformGeneration(System.nanoTime());
        }
        sourceSceneDetector.reset();
        if (engine != null) {
            engine.setCameraTransformInProgress(false);
            engine.invalidateVehicleBackgroundAfterCameraTransform();
            engine.resetSceneDetectorReference();
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
            sceneTransitionCoordinator.advanceCameraTransformGeneration(System.nanoTime());
        }
        sourceSceneDetector.reset();
    }

    public synchronized void close() {
        if (engine != null) engine.close();
        engine = null;
        reloadRequested = false;
        sourceSceneDetector.reset();
    }

    /** Immutable entity-aware snapshot for the Phase 3 acquisition controller. */
    public VehicleTrackingFrame latestVehicleTrackingFrame() {
        VehicleTrackingFrame frame = vehicleTrackingCoordinator.latestFrame();
        return frame.withContinuityStamp(
                sceneTransitionCoordinator.stamp(frame.sourceTimestampNanos)
        );
    }

    public SceneContinuitySnapshot sceneContinuitySnapshot() {
        return sceneTransitionCoordinator.snapshot();
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
