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
            if (reloadRequested) {
                if (engine != null) engine.close();
                engine = null;
                reloadRequested = false;
            }
            if (engine == null) {

                engine = new MobileAlprEngine(
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


            /*
             * UI mogło zauważyć zmianę obrazu podczas
             * poprzedniej, nadal trwającej inferencji.
             *
             * Jej wynik zostanie odrzucony przez
             * uiSceneGeneration, a przed analizą następnej
             * klatki usuwamy również wewnętrzny tracking,
             * konsensus temporalny i cache sceny.
             */
            if (trackingResetRequested) {

                engine.resetTracking();

                trackingResetRequested =
                        false;
            }
            trace.start("camera_conversion");
            frame = com.example.alpr_v1.vision.CameraImageConverter.toBitmap(image);
            trace.stop("camera_conversion");
            PipelineResult result = engine.run(frame, trace);
            metrics.recordRecognitionState(
                    !result.recognitions.isEmpty(),
                    result.hasConfirmedRecognition()
            );
            trace.stop("total");
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
