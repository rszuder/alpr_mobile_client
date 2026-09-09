package com.example.alpr_v1.ui;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.util.Locale;

/** Transparent overlay; FPS is measured from camera frames, CPU from process time. */
public final class LiveHudView extends LinearLayout {
    private long lastUpdate;
    private double lastCpuPercent = Double.NaN;
    public double cpuPercent() { return lastCpuPercent; }
    private final ProcessCpuUsage cpuUsage = new ProcessCpuUsage();

    public LiveHudView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setClickable(false);
        LayoutInflater.from(context).inflate(R.layout.view_live_hud, this, true);
    }

    public void render(MetricsCollector.LiveSnapshot snapshot, String resolution, boolean awaitingFresh, double cameraFps) {
        long now = SystemClock.elapsedRealtime();
        if (lastUpdate != 0L && now - lastUpdate < 1_000L) return;
        lastUpdate = now;
        text(R.id.hud_camera_fps, rate(cameraFps));
        findViewById(R.id.hud_camera_fps).setContentDescription(
                getContext().getString(R.string.hud_camera_fps) + ": " + rate(cameraFps));
        stage(R.id.hud_mp_time, R.string.hud_vehicle_stage, awaitingFresh ? Double.NaN : snapshot.vehicleInferenceMs);
        stage(R.id.hud_mt_time, R.string.hud_plate_stage, awaitingFresh ? Double.NaN : snapshot.plateInferenceMs);
        stage(R.id.hud_mz_time, R.string.hud_character_stage, awaitingFresh ? Double.NaN : snapshot.characterInferenceMs);
    }

    /** Reuses the existing thermal poll; no extra sensor or /proc polling. */
    public void renderDeviceMetrics(double batteryTemperatureC) {
        String temperature = Double.isFinite(batteryTemperatureC)
                ? String.format(Locale.forLanguageTag("pl-PL"), "%.1f°", batteryTemperatureC) : "—";
        double cpu = cpuUsage.sample(Process.getElapsedCpuTime(), SystemClock.uptimeMillis(),
                Runtime.getRuntime().availableProcessors());
        lastCpuPercent = cpu;
        String resources = Double.isFinite(cpu)
                ? String.format(Locale.forLanguageTag("pl-PL"), "%.0f%%", cpu) : "—";
        text(R.id.hud_temperature, temperature);
        text(R.id.hud_resources, resources);
        findViewById(R.id.hud_temperature).setContentDescription(
                getContext().getString(R.string.hud_temperature_description, temperature));
        findViewById(R.id.hud_resources).setContentDescription(
                getContext().getString(R.string.hud_resources_description, resources));
    }

    public void resetDeviceSampling() {
        cpuUsage.reset();
        lastCpuPercent = Double.NaN;
        text(R.id.hud_temperature, "—");
        text(R.id.hud_resources, "—");
    }

    public void clearMetrics() {
        lastUpdate = 0L;
        for (int id : new int[]{R.id.hud_camera_fps, R.id.hud_mp_time, R.id.hud_mt_time, R.id.hud_mz_time}) {
            text(id, "—");
            findViewById(id).setContentDescription(null);
        }
        text(R.id.live_hud, "");
    }

    private void stage(int id, int label, double millis) {
        String value = duration(millis);
        text(id, value);
        findViewById(id).setContentDescription(
                getContext().getString(R.string.hud_stage_description, getContext().getString(label), value));
    }
    private void text(int id, String value) {
        TextView view = findViewById(id);
        if (!value.contentEquals(view.getText())) view.setText(value);
    }
    private static String rate(double value) {
        return !Double.isFinite(value) || value < 0 ? "—" : String.format(Locale.forLanguageTag("pl-PL"), "%.1f", value);
    }
    private static String duration(double millis) {
        if (!Double.isFinite(millis) || millis < 0) return "—";
        return millis < 1_000 ? String.format(Locale.forLanguageTag("pl-PL"), "%.0f ms", millis)
                : String.format(Locale.forLanguageTag("pl-PL"), "%.2f s", millis / 1_000);
    }
}
