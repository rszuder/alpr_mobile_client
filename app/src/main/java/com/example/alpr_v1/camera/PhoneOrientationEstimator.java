package com.example.alpr_v1.camera;

/** Gravity-based deviation from an upright screen; independent of camera image rotation. */
public final class PhoneOrientationEstimator {
    public static final float WARNING_DEGREES = 30f;
    public static final float RECOVERY_DEGREES = 25f;
    private static final long MAX_AGE_NANOS = 1_000_000_000L;
    private long measuredAtNanos;
    private float gravityX, gravityY, gravityZ;
    private boolean warning;

    public static final class Snapshot {
        public final boolean available, warning;
        public final float deviationDegrees, sidewaysDegrees, forwardDegrees;

        private Snapshot(boolean available, boolean warning, float deviation,
                float sideways, float forward) {
            this.available = available;
            this.warning = warning;
            deviationDegrees = deviation;
            sidewaysDegrees = sideways;
            forwardDegrees = forward;
        }

        public static Snapshot unavailable() {
            return new Snapshot(false, false, Float.NaN, Float.NaN, Float.NaN);
        }
    }

    public synchronized void update(float x, float y, float z, long timestampNanos) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || timestampNanos <= measuredAtNanos) return;
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        // Reject freefall / strong acceleration instead of presenting a false level.
        if (magnitude < 7f || magnitude > 12.5f) return;
        if (measuredAtNanos == 0L || timestampNanos - measuredAtNanos > MAX_AGE_NANOS) {
            gravityX = x;
            gravityY = y;
            gravityZ = z;
        } else {
            float seconds = (timestampNanos - measuredAtNanos) / 1_000_000_000f;
            float alpha = seconds / (0.15f + seconds);
            gravityX += alpha * (x - gravityX);
            gravityY += alpha * (y - gravityY);
            gravityZ += alpha * (z - gravityZ);
        }
        measuredAtNanos = timestampNanos;
    }

    /** displayRotation uses Surface.ROTATION_0 .. ROTATION_270 (0 .. 3). */
    public synchronized Snapshot snapshot(long nowNanos, int displayRotation) {
        if (measuredAtNanos == 0L || nowNanos < measuredAtNanos
                || nowNanos - measuredAtNanos > MAX_AGE_NANOS) {
            warning = false;
            return Snapshot.unavailable();
        }
        float x = gravityX, y = gravityY;
        switch (displayRotation) {
            case 1: x = gravityY; y = -gravityX; break;
            case 2: x = -gravityX; y = -gravityY; break;
            case 3: x = -gravityY; y = gravityX; break;
            default: break;
        }
        float planar = (float) Math.hypot(x, y);
        if (Math.hypot(planar, gravityZ) < 4f) return Snapshot.unavailable();
        float deviation = (float) Math.toDegrees(Math.atan2(Math.hypot(x, gravityZ), y));
        float sideways = planar < 0.5f ? Float.NaN : (float) Math.toDegrees(Math.atan2(-x, y));
        float forward = (float) Math.toDegrees(Math.atan2(gravityZ, planar));
        warning = warning ? deviation > RECOVERY_DEGREES : deviation >= WARNING_DEGREES;
        return new Snapshot(true, warning, deviation, sideways, forward);
    }

    public synchronized void reset() {
        measuredAtNanos = 0L;
        gravityX = gravityY = gravityZ = 0f;
        warning = false;
    }
}
