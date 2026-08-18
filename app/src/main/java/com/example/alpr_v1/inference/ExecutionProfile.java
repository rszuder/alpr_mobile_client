package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.ModelRuntime;

public final class ExecutionProfile {
    public final ModelRuntime runtime;
    public final int cpuThreads;
    public final boolean gpu;

    public ExecutionProfile(ModelRuntime runtime, int cpuThreads, boolean gpu) {
        this.runtime = runtime;
        this.cpuThreads = Math.max(1, cpuThreads);
        this.gpu = gpu;
    }

    public static ExecutionProfile tfliteCpu(int threads) {
        return new ExecutionProfile(ModelRuntime.TFLITE, threads, false);
    }

    public static ExecutionProfile tfliteGpu() {
        return new ExecutionProfile(ModelRuntime.TFLITE, 1, true);
    }
}
