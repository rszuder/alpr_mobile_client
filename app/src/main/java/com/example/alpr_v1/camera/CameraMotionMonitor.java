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
    private final MotionIntensityFilter filter = new MotionIntensityFilter();
    private boolean running;

    public CameraMotionMonitor(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void start() {
        if (running || sensorManager == null || gyroscope == null) return;
        running = sensorManager.registerListener(
                this, gyroscope, SensorManager.SENSOR_DELAY_GAME
        );
    }

    public void stop() {
        if (sensorManager != null && running) sensorManager.unregisterListener(this);
        running = false;
        filter.reset();
    }

    public boolean isRapidMotion() {
        return filter.isRapid(SystemClock.elapsedRealtimeNanos());
    }

    public boolean isMoving() {
        return filter.isMoving(SystemClock.elapsedRealtimeNanos());
    }

    public float magnitude() { return filter.magnitude(); }

    public boolean isAvailable() { return gyroscope != null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_GYROSCOPE || event.values.length < 3) return;
        filter.update(event.values[0], event.values[1], event.values[2], event.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Próg jest jakościowy; chwilowa zmiana dokładności nie wymaga rekonfiguracji.
    }
}
