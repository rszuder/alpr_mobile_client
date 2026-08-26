package com.example.alpr_v1.camera;

/** Wygładza prędkość kątową żyroskopu i rozpoznaje krótki okres szybkiego ruchu. */
public final class MotionIntensityFilter {
    private static final float RAPID_THRESHOLD_RADIANS_PER_SECOND = 1.0f;
    private static final float MOVING_THRESHOLD_RADIANS_PER_SECOND = 0.30f;
    private static final long MAX_SAMPLE_AGE_NANOS = 300_000_000L;
    private float smoothedMagnitude;
    private long lastSampleNanos = Long.MIN_VALUE;

    public synchronized void update(float x, float y, float z, long timestampNanos) {
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (lastSampleNanos == Long.MIN_VALUE) smoothedMagnitude = magnitude;
        else smoothedMagnitude = 0.55f * smoothedMagnitude + 0.45f * magnitude;
        lastSampleNanos = timestampNanos;
    }

    public synchronized boolean isRapid(long nowNanos) {
        return isRecent(nowNanos)
                && smoothedMagnitude >= RAPID_THRESHOLD_RADIANS_PER_SECOND;
    }

    public synchronized boolean isMoving(long nowNanos) {
        return isRecent(nowNanos)
                && smoothedMagnitude >= MOVING_THRESHOLD_RADIANS_PER_SECOND;
    }

    private boolean isRecent(long nowNanos) {
        return lastSampleNanos != Long.MIN_VALUE
                && nowNanos >= lastSampleNanos
                && nowNanos - lastSampleNanos <= MAX_SAMPLE_AGE_NANOS;
    }

    public synchronized float magnitude() { return smoothedMagnitude; }

    public synchronized void reset() {
        smoothedMagnitude = 0f;
        lastSampleNanos = Long.MIN_VALUE;
    }
}
