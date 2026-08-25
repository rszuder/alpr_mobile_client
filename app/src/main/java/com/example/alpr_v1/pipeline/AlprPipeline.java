package com.example.alpr_v1.pipeline;

import androidx.camera.core.ImageProxy;

import android.graphics.Bitmap;
import android.content.Context;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AdaptiveFrameGate;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

public final class AlprPipeline {
    private static final String LOG_TAG = "AlprPipeline";

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
    private final AtomicLong frameIds = new AtomicLong();
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
    }

    public synchronized PipelineResult process(ImageProxy image) {
        metrics.observeSourceFrame(image.getWidth(), image.getHeight());
        long frameId = frameIds.incrementAndGet();
        if (!frameGate.shouldProcess(frameId)) {
            metrics.frameDropped();
            return null;
        }
        InferenceTrace trace = new InferenceTrace(frameId);
        trace.putCount("source_width", image.getWidth());
        trace.putCount("source_height", image.getHeight());
        trace.start("total");
        if (!registry.hasRequiredPipeline()) {
            trace.stop("total");
            trace.finish("models_missing", "");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return PipelineResult.waitingForModels();
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
                                    effectiveRoiBudgetPolicy()
                            );

                    engine.setRecognitionProfile(
                            recognitionProfile
                    );

                    engine.setRapidCameraMotion(
                            rapidCameraMotion
                    );
                }


                if (trackingResetRequested) {

                    engine.resetTracking();

                    trackingResetRequested =
                            false;
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

                frame =
                        com.example.alpr_v1.vision.CameraImageConverter
                                .toBitmap(
                                        image
                                );

            } finally {

                trace.stop(
                        "camera_conversion"
                );
            }
            PipelineResult result;


            trace.start(
                    "engine_total"
            );

            try {

                result =
                        engine.run(
                                frame,
                                trace
                        );

            } finally {

                trace.stop(
                        "engine_total"
                );
            }
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
                                + "MZ_RUNS=%d",

                        trace.frameId(),

                        ms(
                                trace,
                                "total"
                        ),

                        ms(
                                trace,
                                "inference_sum"
                        ),

                        ms(
                                trace,
                                "auxiliary_sum"
                        ),

                        ms(
                                trace,
                                "pipeline_overhead"
                        ),

                        ms(
                                trace,
                                "camera_conversion"
                        ),

                        ms(
                                trace,
                                "engine_setup"
                        ),

                        ms(
                                trace,
                                "pipeline_finalize"
                        ),

                        ms(
                                trace,
                                "vehicle_preprocess"
                        ),

                        ms(
                                trace,
                                "vehicle_inference"
                        ),

                        ms(
                                trace,
                                "vehicle_postprocess"
                        ),

                        ms(
                                trace,
                                "plate_preprocess"
                        ),

                        ms(
                                trace,
                                "plate_inference"
                        ),

                        ms(
                                trace,
                                "plate_postprocess"
                        ),

                        ms(
                                trace,
                                "rectification"
                        ),

                        ms(
                                trace,
                                "character_preprocess"
                        ),

                        ms(
                                trace,
                                "character_inference"
                        ),

                        ms(
                                trace,
                                "character_postprocess"
                        ),

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
                                )
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

    public void invalidateModels() {
        reloadRequested = true;
    }

    public void requestTrackingReset() {

        trackingResetRequested =
                true;
    }


    public synchronized void setRecognitionProfile(RecognitionProfile profile) {
        recognitionProfile = profile == null ? RecognitionProfile.BALANCED : profile;
        if (engine != null) engine.setRecognitionProfile(recognitionProfile);
    }

    public synchronized void resetTracking() {
        if (engine != null) engine.resetTracking();
    }



    private RoiBudgetPolicy effectiveRoiBudgetPolicy() {
        if (experimentModeEnabled) {
            return experimentRoiBudgetPolicy;
        }

        return vehicleCascadeEnabled
                ? RoiBudgetPolicy.TWO_ROI
                : RoiBudgetPolicy.FULL_FRAME;
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

        experimentModeEnabled = enabled;
        experimentRoiBudgetPolicy =
                roiPolicy == null
                        ? RoiBudgetPolicy.TWO_ROI
                        : roiPolicy;

        RoiBudgetPolicy currentEffective =
                effectiveRoiBudgetPolicy();

        metrics.setExperimentConfiguration(
                experimentModeEnabled,
                experimentRoiBudgetPolicy.wireName()
        );

        metrics.setRoiBudgetPolicy(
                currentEffective.wireName()
        );

        if (previousEffective == currentEffective) {
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

    public synchronized void close() {
        if (engine != null) engine.close();
        engine = null;
        reloadRequested = false;
    }
}
