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
    private RecognitionProfile recognitionProfile = RecognitionProfile.BALANCED;
    private boolean vehicleCascadeEnabled;
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
                        registry, autoTuneManager, vehicleCascadeEnabled
                );
                engine.setRecognitionProfile(recognitionProfile);
                engine.setRapidCameraMotion(rapidCameraMotion);
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

    public synchronized void setRecognitionProfile(RecognitionProfile profile) {
        recognitionProfile = profile == null ? RecognitionProfile.BALANCED : profile;
        if (engine != null) engine.setRecognitionProfile(recognitionProfile);
    }

    public synchronized void resetTracking() {
        if (engine != null) engine.resetTracking();
    }

    public synchronized void setVehicleCascadeEnabled(boolean enabled) {
        if (vehicleCascadeEnabled == enabled) {
            metrics.setVehicleCascadeEnabled(enabled);
            return;
        }
        vehicleCascadeEnabled = enabled;
        metrics.setVehicleCascadeEnabled(enabled);
        if (engine != null) engine.resetTracking();
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
