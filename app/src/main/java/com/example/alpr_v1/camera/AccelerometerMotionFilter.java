package com.example.alpr_v1.camera;

import com.example.alpr_v1.continuity.SceneContinuityProfile;

/**
 * Detects short physical camera motion from linear acceleration when a device
 * has no gyroscope. It exposes only categorical motion; it is not an angular
 * velocity estimator.
 */
public final class AccelerometerMotionFilter {
    private final float gravityAlpha;
    private final float magnitudeAlpha;
    private final float movingThreshold;
    private final float rapidThreshold;
    private final long maximumEventAgeNanos;

    private boolean initialized;
    private float gravityX;
    private float gravityY;
    private float gravityZ;
    private float smoothedLinearAcceleration;
    private long lastMovingNanos = Long.MIN_VALUE;
    private long lastRapidNanos = Long.MIN_VALUE;

    public AccelerometerMotionFilter() {
        this(SceneContinuityProfile.INITIAL);
    }

    AccelerometerMotionFilter(SceneContinuityProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile is required");
        gravityAlpha = profile.accelerometerGravityAlpha;
        magnitudeAlpha = profile.accelerometerMagnitudeAlpha;
        movingThreshold = profile.accelerometerMovingThreshold;
        rapidThreshold = profile.accelerometerRapidThreshold;
        maximumEventAgeNanos = profile.accelerometerEventRetentionNanos;
    }

    public synchronized void update(float x, float y, float z, long timestampNanos) {
        if (!initialized) {
            initialized = true;
            gravityX = x;
            gravityY = y;
            gravityZ = z;
            smoothedLinearAcceleration = 0f;
            return;
        }
        gravityX = gravityAlpha * gravityX + (1f - gravityAlpha) * x;
        gravityY = gravityAlpha * gravityY + (1f - gravityAlpha) * y;
        gravityZ = gravityAlpha * gravityZ + (1f - gravityAlpha) * z;
        float dx = x - gravityX;
        float dy = y - gravityY;
        float dz = z - gravityZ;
        float linear = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        smoothedLinearAcceleration = magnitudeAlpha * smoothedLinearAcceleration
                + (1f - magnitudeAlpha) * linear;
        if (smoothedLinearAcceleration >= movingThreshold) {
            lastMovingNanos = timestampNanos;
        }
        if (smoothedLinearAcceleration >= rapidThreshold) {
            lastRapidNanos = timestampNanos;
        }
    }

    public synchronized boolean isMoving(long nowNanos) {
        return recent(lastMovingNanos, nowNanos);
    }

    public synchronized boolean isRapid(long nowNanos) {
        return recent(lastRapidNanos, nowNanos);
    }

    public synchronized void reset() {
        initialized = false;
        gravityX = gravityY = gravityZ = 0f;
        smoothedLinearAcceleration = 0f;
        lastMovingNanos = Long.MIN_VALUE;
        lastRapidNanos = Long.MIN_VALUE;
    }

    private boolean recent(long eventNanos, long nowNanos) {
        return eventNanos != Long.MIN_VALUE
                && nowNanos >= eventNanos
                && nowNanos - eventNanos <= maximumEventAgeNanos;
    }
}
