package com.example.alpr_v1.ui;

/** Prevents short calm gaps from repeatedly resetting the local vehicle tracker. */
public final class VehiclePreviewMotionPolicy {
    public static final long LOCAL_RESUME_QUIET_NANOS = 600_000_000L;
    private boolean global;
    private long lastMotionNanos;

    public synchronized boolean update(boolean cameraMotion, long nowNanos) {
        if (cameraMotion) {
            global = true;
            lastMotionNanos = nowNanos;
        } else if (global && nowNanos >= lastMotionNanos
                && nowNanos - lastMotionNanos >= LOCAL_RESUME_QUIET_NANOS) {
            global = false;
        }
        return global;
    }

    public synchronized boolean globalOwnsGeometry() { return global; }
    public synchronized void reset() { global = false; lastMotionNanos = 0L; }
}
