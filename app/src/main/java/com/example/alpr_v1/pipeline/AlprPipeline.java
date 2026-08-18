package com.example.alpr_v1.pipeline;

import androidx.camera.core.ImageProxy;

import android.graphics.Bitmap;
import android.content.Context;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AdaptiveFrameGate;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

public final class AlprPipeline {
    private final ModelRegistry registry;
    private final MetricsCollector metrics;
    private final AutoTuneManager autoTuneManager;
    private final AdaptiveFrameGate frameGate;
    private final AtomicLong frameIds = new AtomicLong();
    private MobileAlprEngine engine;
    private volatile boolean reloadRequested;

    public AlprPipeline(
            Context context,
            ModelRegistry registry,
            MetricsCollector metrics,
            AutoTuneManager autoTuneManager
    ) {
        this.registry = registry;
        this.metrics = metrics;
        this.autoTuneManager = autoTuneManager;
        this.frameGate = new AdaptiveFrameGate(context);
    }

    public synchronized PipelineResult process(ImageProxy image) {
        long frameId = frameIds.incrementAndGet();
        if (!frameGate.shouldProcess(frameId)) {
            metrics.frameDropped();
            return null;
        }
        InferenceTrace trace = new InferenceTrace(frameId);
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
            if (engine == null) engine = new MobileAlprEngine(registry, autoTuneManager);
            trace.start("camera_conversion");
            frame = com.example.alpr_v1.vision.RgbaImageConverter.toBitmap(image);
            trace.stop("camera_conversion");
            PipelineResult result = engine.run(frame, trace);
            trace.stop("total");
            trace.captureMemoryAfterMeasurement();
            metrics.add(trace);
            return result;
        } catch (Exception e) {
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

    public synchronized void close() {
        if (engine != null) engine.close();
        engine = null;
        reloadRequested = false;
    }
}
