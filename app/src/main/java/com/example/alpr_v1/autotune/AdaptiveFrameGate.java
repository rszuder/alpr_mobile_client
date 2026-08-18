package com.example.alpr_v1.autotune;

import android.app.ActivityManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

/** Lekki regulator obciążenia reagujący na pamięć i throttling termiczny. */
public final class AdaptiveFrameGate {
    private static final long REFRESH_INTERVAL_MS = 1_000L;
    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final int baseStride;
    private long lastRefreshMs;
    private int currentStride;

    public AdaptiveFrameGate(Context context) {
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memory);
        baseStride = activityManager.isLowRamDevice() || memory.totalMem < 4L * 1024L * 1024L * 1024L ? 2 : 1;
        currentStride = baseStride;
    }

    public synchronized boolean shouldProcess(long frameId) {
        refreshIfNeeded();
        return frameId % currentStride == 0;
    }

    public synchronized int currentStride() {
        refreshIfNeeded();
        return currentStride;
    }

    private void refreshIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshMs < REFRESH_INTERVAL_MS) return;
        lastRefreshMs = now;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memory);
        int thermal = powerManager.getCurrentThermalStatus();
        int stride = baseStride;
        if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL) stride = Math.max(stride, 8);
        else if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) stride = Math.max(stride, 4);
        else if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) stride = Math.max(stride, 2);
        if (memory.lowMemory) stride = Math.max(stride, 4);
        currentStride = stride;
    }
}
