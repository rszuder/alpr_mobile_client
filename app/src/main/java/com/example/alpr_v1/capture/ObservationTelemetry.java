package com.example.alpr_v1.capture;

import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** HUD measurements sampled when the observation reaches UI, not reconstructed when details open. */
public final class ObservationTelemetry {
    public final double cameraFps, batteryTemperatureC, cpuPercent;
    public final long sampledAtNanos;
    public final List<ModelRuntimeSummary> models;

    public ObservationTelemetry(double fps, double temperature, double cpu, long sampledAtNanos,
            List<ModelRuntimeSummary> models) {
        cameraFps = fps; batteryTemperatureC = temperature; cpuPercent = cpu;
        this.sampledAtNanos = sampledAtNanos;
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
    }
}
