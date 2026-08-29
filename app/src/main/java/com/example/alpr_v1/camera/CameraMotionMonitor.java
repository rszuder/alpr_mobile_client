package com.example.alpr_v1.camera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/** Lekki monitor ruchu kamery oparty na żyroskopie telefonu. */
public final class CameraMotionMonitor implements SensorEventListener {
    private final SensorManager sensorManager;
    private final Sensor gyroscope;
    private final Sensor accelerometer;
    private final Sensor activeSensor;
    private final MotionIntensityFilter gyroFilter = new MotionIntensityFilter();
    private final AccelerometerMotionFilter accelerometerFilter =
            new AccelerometerMotionFilter();
    private boolean running;

    public CameraMotionMonitor(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        activeSensor = gyroscope != null ? gyroscope : accelerometer;
    }

    public void start() {
        if (running || sensorManager == null || activeSensor == null) return;
        running = sensorManager.registerListener(
                this, activeSensor, SensorManager.SENSOR_DELAY_GAME
        );
    }

    public void stop() {
        if (sensorManager != null && running) sensorManager.unregisterListener(this);
        running = false;
        gyroFilter.reset();
        accelerometerFilter.reset();
    }

    public boolean isRapidMotion() {
        long nowNanos = SystemClock.elapsedRealtimeNanos();
        return gyroscope != null
                ? gyroFilter.isRapid(nowNanos)
                : accelerometerFilter.isRapid(nowNanos);
    }

    public boolean isMoving() {
        long nowNanos = SystemClock.elapsedRealtimeNanos();
        return gyroscope != null
                ? gyroFilter.isMoving(nowNanos)
                : accelerometerFilter.isMoving(nowNanos);
    }

    public float magnitude() { return gyroscope == null ? 0f : gyroFilter.magnitude(); }

    public boolean isAvailable() { return activeSensor != null; }

    public boolean isGyroscopeAvailable() { return gyroscope != null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values.length < 3) return;
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroFilter.update(event.values[0], event.values[1], event.values[2], event.timestamp);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER
                && gyroscope == null) {
            accelerometerFilter.update(
                    event.values[0], event.values[1], event.values[2], event.timestamp
            );
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Próg jest jakościowy; chwilowa zmiana dokładności nie wymaga rekonfiguracji.
    }
}
