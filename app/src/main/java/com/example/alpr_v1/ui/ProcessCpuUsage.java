package com.example.alpr_v1.ui;

/** Percentage of all available CPU cores consumed by this process between polls. */
public final class ProcessCpuUsage {
    private long previousCpu = -1L, previousUptime = -1L;

    public double sample(long cpuMillis, long uptimeMillis, int cores) {
        if (cpuMillis < 0L || uptimeMillis < 0L || cores <= 0) {
            reset();
            return Double.NaN;
        }
        long cpuDelta = cpuMillis - previousCpu;
        long timeDelta = uptimeMillis - previousUptime;
        boolean valid = previousCpu >= 0L && previousUptime >= 0L && cpuDelta >= 0L && timeDelta > 0L;
        previousCpu = cpuMillis;
        previousUptime = uptimeMillis;
        return valid ? Math.max(0.0, Math.min(100.0, 100.0 * cpuDelta / timeDelta / cores)) : Double.NaN;
    }

    public void reset() { previousCpu = previousUptime = -1L; }
}
