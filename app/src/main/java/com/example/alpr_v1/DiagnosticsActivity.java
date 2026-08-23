package com.example.alpr_v1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.camera.AnalysisResolutionProfile;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.ui.ModelStatusFormatter;
import com.google.android.material.appbar.MaterialToolbar;



/** Czytelny, dwukolumnowy ekran diagnostyczny niezależny od podglądu kamery. */
public final class DiagnosticsActivity extends AppCompatActivity {
    public static final String EXTRA_ACTION = "diagnostics_action";
    public static final String ACTION_EXPORT_REPORT = "export_report";
    public static final String EXTRA_CROP_COUNT = "crop_count";
    public static final String EXTRA_CROP_LIMIT = "crop_limit";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_COLLECTION_ACTIVE = "collection_active";

    private GridLayout grid;
    private TextView health;
    private TextView healthDetail;
    private TextView models;
    private TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_diagnostics);
        applySystemInsets();

        MaterialToolbar toolbar = findViewById(R.id.diagnostics_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        grid = findViewById(R.id.diagnostics_grid);
        health = findViewById(R.id.diagnostics_health);
        healthDetail = findViewById(R.id.diagnostics_health_detail);
        models = findViewById(R.id.diagnostics_models_value);
        log = findViewById(R.id.diagnostics_log);

        findViewById(R.id.diagnostics_refresh).setOnClickListener(view -> refresh());
        findViewById(R.id.diagnostics_export).setOnClickListener(view -> {
            Intent result = new Intent().putExtra(EXTRA_ACTION, ACTION_EXPORT_REPORT);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
        refresh();
    }

    private void refresh() {
        DeviceProfile device = DeviceProfile.capture(this);
        ModelRegistry registry = new ModelRegistry(this);
        AutoTuneManager autoTune = new AutoTuneManager(this);
        SharedPreferences preferences = getSharedPreferences(SettingsActivity.PREFERENCES, Context.MODE_PRIVATE);

        boolean ready = registry.hasRequiredPipeline();
        health.setText(ready
                ? R.string.diagnostics_health_ready
                : R.string.diagnostics_health_partial);
        health.setTextColor(ContextCompat.getColor(
                this, ready ? R.color.alpr_success : R.color.alpr_warning
        ));
        healthDetail.setText(ready
                ? R.string.diagnostics_health_ready_detail
                : R.string.diagnostics_health_partial_detail);

        grid.removeAllViews();
        addMetric(
                R.drawable.ic_device_24,
                R.drawable.bg_icon_blue,
                R.color.alpr_primary,
                getString(R.string.diagnostics_device),
                device.manufacturer + " " + device.model,
                getString(
                        R.string.diagnostics_device_detail,
                        device.androidVersion,
                        device.sdk,
                        device.cpuCores
                )
        );
        addMetric(
                R.drawable.ic_memory_24,
                R.drawable.bg_icon_violet,
                R.color.alpr_secondary,
                getString(R.string.diagnostics_memory),
                getString(R.string.diagnostics_memory_value, bytesToMegabytes(device.availableMemoryBytes)),
                getString(
                        R.string.diagnostics_memory_detail,
                        bytesToMegabytes(device.totalMemoryBytes),
                        yesNo(device.lowRamDevice)
                )
        );

        RecognitionProfile recognition = RecognitionProfile.fromWireName(preferences.getString(
                "recognition_profile", RecognitionProfile.BALANCED.wireName()
        ));
        AnalysisResolutionProfile resolution = AnalysisResolutionProfile.fromWireName(preferences.getString(
                "analysis_resolution_profile", AnalysisResolutionProfile.AUTO.wireName()
        ));
        boolean vehicle = preferences.getBoolean("vehicle_cascade_enabled", false);
        addMetric(
                R.drawable.ic_tune_24,
                R.drawable.bg_icon_green,
                R.color.alpr_success,
                getString(R.string.diagnostics_pipeline),
                recognitionLabel(recognition),
                getString(
                        R.string.diagnostics_pipeline_detail,
                        resolutionLabel(resolution),
                        getString(vehicle
                                ? R.string.diagnostics_vehicle_on
                                : R.string.diagnostics_vehicle_off)
                )
        );

        Intent source = getIntent();
        int count = Math.max(0, source.getIntExtra(EXTRA_CROP_COUNT, 0));
        int limit = Math.max(1, source.getIntExtra(EXTRA_CROP_LIMIT, 1));
        String sessionId = source.getStringExtra(EXTRA_SESSION_ID);
        boolean active = source.getBooleanExtra(EXTRA_COLLECTION_ACTIVE, false);
        String sessionDetail;
        if (sessionId == null || sessionId.isEmpty()) {
            sessionDetail = getString(R.string.diagnostics_no_session);
        } else {
            String shortId = sessionId.length() <= 12
                    ? sessionId
                    : "…" + sessionId.substring(sessionId.length() - 11);
            sessionDetail = getString(
                    active
                            ? R.string.diagnostics_session_detail_active
                            : R.string.diagnostics_session_detail_paused,
                    shortId
            );
        }
        addMetric(
                R.drawable.ic_session_24,
                R.drawable.bg_icon_pink,
                R.color.alpr_magenta,
                getString(R.string.diagnostics_session),
                getString(R.string.diagnostics_session_value, count, limit),
                sessionDetail
        );

        models.setText(ModelStatusFormatter.format(registry, autoTune));
        log.setText(
                formatRecentLog(
                        AppLog.recentEvents(this, 20)
                )
        );
    }

    private CharSequence formatRecentLog(String rawLog) {
        if (rawLog == null || rawLog.trim().isEmpty()
                || rawLog.equals("Brak zapisanych zdarzeń")) {
            return getString(R.string.persistent_log_empty);
        }

        String[] sourceLines = rawLog.split("\\R");
        List<String> lines = new ArrayList<>();

        for (String line : sourceLines) {
            if (line != null && !line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }

        SpannableStringBuilder result =
                new SpannableStringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result.append('\n');
            }

            appendStyledLogLine(
                    result,
                    i + 1,
                    lines.get(i)
            );
        }

        return result;
    }

    private void appendStyledLogLine(
            SpannableStringBuilder target,
            int number,
            String rawLine
    ) {
        /*
         * Format AppLog:
         *
         * 2026-08-23T19:21:12.123+02:00 INFO/MainActivity Treść
         */

        int levelSeparator = rawLine.indexOf(' ');
        int messageSeparator = levelSeparator < 0
                ? -1
                : rawLine.indexOf(' ', levelSeparator + 1);

        String timestamp;
        String levelAndTag;
        String message;

        if (levelSeparator < 0 || messageSeparator < 0) {
            timestamp = "";
            levelAndTag = "";
            message = rawLine;
        } else {
            timestamp = rawLine.substring(
                    0,
                    levelSeparator
            );

            levelAndTag = rawLine.substring(
                    levelSeparator + 1,
                    messageSeparator
            );

            message = rawLine.substring(
                    messageSeparator + 1
            );
        }

        String shortTime = extractTime(timestamp);

        /*
         * Numer zdarzenia.
         */
        int numberStart = target.length();

        target.append(
                String.format(
                        Locale.ROOT,
                        "[%02d] ",
                        number
                )
        );

        target.setSpan(
                new ForegroundColorSpan(
                        ContextCompat.getColor(
                                this,
                                R.color.alpr_text_muted
                        )
                ),
                numberStart,
                target.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        /*
         * Godzina.
         */
        if (!shortTime.isEmpty()) {
            int timeStart = target.length();

            target.append(shortTime);
            target.append("  ");

            target.setSpan(
                    new ForegroundColorSpan(
                            ContextCompat.getColor(
                                    this,
                                    R.color.alpr_text_muted
                            )
                    ),
                    timeStart,
                    target.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        /*
         * Treść zdarzenia.
         */
        int messageStart = target.length();

        target.append(message);

        int messageColor =
                resolveLogMessageColor(
                        levelAndTag,
                        message
                );

        target.setSpan(
                new ForegroundColorSpan(
                        ContextCompat.getColor(
                                this,
                                messageColor
                        )
                ),
                messageStart,
                target.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        /*
         * Najważniejsze zdarzenia eksperymentalne
         * dodatkowo wyróżniamy pogrubieniem.
         */
        if (message.startsWith("Rozpoczęto eksperyment")
                || message.startsWith("Zakończono eksperyment")) {

            target.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    messageStart,
                    target.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
    private void addMetric(
            int iconResource,
            int iconBackground,
            int tintColor,
            String label,
            String value,
            String detail
    ) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_diagnostic_metric, grid, false);
        ImageView icon = card.findViewById(R.id.diagnostic_metric_icon);
        icon.setImageResource(iconResource);
        icon.setBackgroundResource(iconBackground);
        icon.setColorFilter(ContextCompat.getColor(this, tintColor));
        ((TextView) card.findViewById(R.id.diagnostic_metric_label)).setText(label);
        ((TextView) card.findViewById(R.id.diagnostic_metric_value)).setText(value);
        ((TextView) card.findViewById(R.id.diagnostic_metric_detail)).setText(detail);

        int position = grid.getChildCount();
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(position / 2),
                GridLayout.spec(position % 2, 1f)
        );
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        card.setLayoutParams(params);
        grid.addView(card);
    }

    private String recognitionLabel(RecognitionProfile profile) {
        if (profile == RecognitionProfile.FAST) return getString(R.string.recognition_profile_fast);
        if (profile == RecognitionProfile.ACCURATE) return getString(R.string.recognition_profile_accurate);
        return getString(R.string.recognition_profile_balanced);
    }

    private String resolutionLabel(AnalysisResolutionProfile profile) {
        if (profile == AnalysisResolutionProfile.FAST) return getString(R.string.settings_fast_short);
        if (profile == AnalysisResolutionProfile.DISTANT) return getString(R.string.settings_distant_short);
        return getString(R.string.settings_auto_short);
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.diagnostics_boolean_yes : R.string.diagnostics_boolean_no);
    }

    private static double bytesToMegabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.diagnostics_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
    private int resolveLogMessageColor(
            String levelAndTag,
            String message
    ) {
        if (levelAndTag.startsWith("ERROR/")) {
            return R.color.alpr_error;
        }

        if (levelAndTag.startsWith("WARN/")) {
            return R.color.alpr_warning;
        }

        if (message.startsWith("Rozpoczęto eksperyment")
                || message.startsWith("Zakończono eksperyment")) {
            return R.color.alpr_success;
        }

        if (message.contains("analiz")
                || message.contains("kamer")
                || message.contains("Camera")) {
            return R.color.alpr_primary;
        }

        if (message.contains("crop")
                || message.contains("Crop")) {
            return R.color.alpr_magenta;
        }

        return R.color.alpr_text_primary;
    }

    private static String extractTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }

        int separator = timestamp.indexOf('T');

        if (separator < 0
                || timestamp.length() < separator + 9) {
            return timestamp;
        }

        /*
         * yyyy-MM-ddTHH:mm:ss...
         *            ^^^^^^^^
         */
        return timestamp.substring(
                separator + 1,
                separator + 9
        );
    }
}
