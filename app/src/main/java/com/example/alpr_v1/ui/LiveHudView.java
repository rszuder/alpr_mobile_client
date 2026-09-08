package com.example.alpr_v1.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.util.Locale;

/** Presentation only: FPS comes from frame-flow counters, never from reciprocal inference time. */
public final class LiveHudView extends LinearLayout {
    private long lastUpdate;
    public LiveHudView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_hud_card);
        int padding = Math.round(14 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding / 2, padding, padding);
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_live_hud, content, true);
        scroll.addView(content);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void render(MetricsCollector.LiveSnapshot snapshot, String resolution, boolean awaitingFresh, double cameraFps) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastUpdate < 1_000L) return;
        lastUpdate = now;
        text(R.id.hud_camera_fps, rate(cameraFps));
        text(R.id.hud_pipeline_time, duration(awaitingFresh ? Double.NaN : snapshot.pipelineMs));
        text(R.id.hud_mp_time, duration(awaitingFresh ? Double.NaN : snapshot.vehicleInferenceMs));
        text(R.id.hud_mt_time, duration(awaitingFresh ? Double.NaN : snapshot.plateInferenceMs));
        text(R.id.hud_mz_time, duration(awaitingFresh ? Double.NaN : snapshot.characterInferenceMs));
        text(R.id.hud_resolution, resolution);
        text(R.id.hud_frame_flow, getContext().getString(R.string.hud_frame_flow, rate(snapshot.processedFps), snapshot.droppedFrames));
        text(R.id.hud_overhead, getContext().getString(R.string.hud_overhead,
                duration(awaitingFresh ? Double.NaN : snapshot.inferenceSumMs),
                duration(awaitingFresh ? Double.NaN : snapshot.auxiliarySumMs)));
    }
    public void clearMetrics() {
        lastUpdate = 0L;
        for (int id : new int[]{R.id.hud_camera_fps,R.id.hud_pipeline_time,R.id.hud_mp_time,
                R.id.hud_mt_time,R.id.hud_mz_time,R.id.hud_resolution}) text(id,"—");
        text(R.id.hud_frame_flow,"");text(R.id.hud_overhead,"");
        text(R.id.live_hud,"");
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
