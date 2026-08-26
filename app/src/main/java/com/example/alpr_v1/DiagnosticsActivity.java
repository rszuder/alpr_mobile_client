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
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.graphics.Typeface;

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
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelManifest;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.pipeline.RecognitionProfile;
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
    private TableLayout modelsTable;
    private LinearLayout log;

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
        modelsTable = findViewById(R.id.diagnostics_models_table);
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

        renderModelsTable(registry, autoTune);
        renderRecentLog(
                AppLog.recentEvents(this, 20)
        );
    }

    private void renderModelsTable(
            ModelRegistry registry,
            AutoTuneManager autoTune
    ) {
        models.setText(
                modelCompositionSummary(registry)
        );

        /*
         * Pierwszym dzieckiem jest statyczny nagłówek kolumn.
         * Odświeżenie diagnostyki wymienia wyłącznie wiersze danych.
         */
        while (modelsTable.getChildCount() > 1) {
            modelsTable.removeViewAt(1);
        }

        addModelRow(
                registry,
                autoTune,
                ModelRole.VEHICLE,
                R.string.diagnostics_model_stage_vehicle,
                R.string.diagnostics_model_optional,
                R.drawable.bg_icon_blue,
                R.color.alpr_primary,
                R.color.alpr_card_blue
        );

        addModelRow(
                registry,
                autoTune,
                ModelRole.PLATE,
                R.string.diagnostics_model_stage_plate,
                R.string.diagnostics_model_required,
                R.drawable.bg_icon_violet,
                R.color.alpr_secondary,
                R.color.alpr_card_violet
        );

        addModelRow(
                registry,
                autoTune,
                ModelRole.CHARACTER,
                R.string.diagnostics_model_stage_character,
                R.string.diagnostics_model_required,
                R.drawable.bg_icon_pink,
                R.color.alpr_magenta,
                R.color.alpr_card_magenta
        );
    }

    private void addModelRow(
            ModelRegistry registry,
            AutoTuneManager autoTune,
            ModelRole role,
            int stageLabel,
            int requirementLabel,
            int stageBackground,
            int stageColor,
            int rowColor
    ) {
        TableRow row = (TableRow) LayoutInflater.from(this).inflate(
                R.layout.item_model_runtime_row,
                modelsTable,
                false
        );

        TextView stage = row.findViewById(R.id.model_runtime_stage);
        TextView requirement = row.findViewById(R.id.model_runtime_requirement);
        TextView modelName = row.findViewById(R.id.model_runtime_name);
        TextView modelDetail = row.findViewById(R.id.model_runtime_model_detail);
        TextView execution = row.findViewById(R.id.model_runtime_execution);
        TextView executionDetail = row.findViewById(R.id.model_runtime_execution_detail);

        stage.setText(stageLabel);
        stage.setBackgroundResource(stageBackground);
        stage.setTextColor(
                ContextCompat.getColor(
                        this,
                        stageColor
                )
        );
        requirement.setText(requirementLabel);

        InstalledModel activeModel = registry.getActive(role);
        InstalledModel displayedModel = activeModel;
        boolean unavailableBaseModel = false;

        if (role == ModelRole.VEHICLE
                && displayedModel == null
                && registry.getBasePackage() != null
                && registry.getBasePackage().vehicleModel() != null) {
            displayedModel = registry.getBasePackage().vehicleModel();
            unavailableBaseModel = true;
        }

        if (displayedModel == null) {
            row.setBackgroundColor(
                    ContextCompat.getColor(
                            this,
                            R.color.alpr_card_warning
                    )
            );
            modelName.setText(R.string.diagnostics_model_missing);
            modelDetail.setText(
                    role == ModelRole.VEHICLE
                            ? R.string.diagnostics_model_missing_optional
                            : R.string.diagnostics_model_missing_required
            );
            execution.setText(R.string.diagnostics_model_value_unavailable);
            execution.setTextColor(
                    ContextCompat.getColor(
                            this,
                            R.color.alpr_warning
                    )
            );
            executionDetail.setText(
                    role == ModelRole.VEHICLE
                            ? R.string.diagnostics_model_stage_skipped
                            : R.string.diagnostics_model_pipeline_blocked
            );
            modelsTable.addView(row);
            return;
        }

        row.setBackgroundColor(
                ContextCompat.getColor(
                        this,
                        unavailableBaseModel
                                ? R.color.alpr_card_warning
                                : rowColor
                )
        );

        ModelManifest manifest = displayedModel.manifest();
        modelName.setText(manifest.modelId());
        modelDetail.setText(
                modelDetail(manifest)
        );

        if (unavailableBaseModel) {
            execution.setText(R.string.diagnostics_model_unavailable);
            execution.setTextColor(
                    ContextCompat.getColor(
                            this,
                            R.color.alpr_warning
                    )
            );
            executionDetail.setText(R.string.diagnostics_model_backend_missing);
            modelsTable.addView(row);
            return;
        }

        ModelVariant selected = autoTune.chosenVariant(displayedModel);
        ExecutionProfile profile = autoTune.chosenProfile(displayedModel);
        ModelInputSpec input = selected.input(manifest.input());
        ModelOutputSpec output = selected.output(manifest.output());

        execution.setText(
                getString(
                        R.string.diagnostics_model_runtime_precision,
                        selected.runtime().wireName().toUpperCase(Locale.ROOT),
                        selected.precision().toUpperCase(Locale.ROOT)
                )
        );
        execution.setTextColor(
                ContextCompat.getColor(
                        this,
                        stageColor
                )
        );

        String profileLabel = getString(
                autoTune.isVariantPinned(displayedModel)
                        ? R.string.diagnostics_model_profile_manual
                        : autoTune.hasProfile(displayedModel)
                        ? R.string.diagnostics_model_profile_autotune
                        : R.string.diagnostics_model_profile_default
        );

        String hardwareLabel = profile.gpu
                ? getString(R.string.diagnostics_model_hardware_gpu)
                : getString(
                        R.string.diagnostics_model_hardware_cpu,
                        profile.cpuThreads
                );

        executionDetail.setText(
                getString(
                        R.string.diagnostics_model_execution_detail,
                        selected.id(),
                        profileLabel,
                        hardwareLabel,
                        input.width(),
                        input.height(),
                        output.confidenceThreshold(),
                        output.iouThreshold()
                )
        );

        modelsTable.addView(row);
    }

    private CharSequence modelCompositionSummary(
            ModelRegistry registry
    ) {
        InstalledAlprPackage activePackage = registry.getActivePackage();
        InstalledAlprPackage basePackage = registry.getBasePackage();

        if (basePackage != null
                && registry.isCompositionModified()) {
            return getString(
                    R.string.diagnostics_model_package_modified,
                    basePackage.manifest().name(),
                    basePackage.manifest().version()
            );
        }

        if (activePackage != null) {
            String createdAt = shortDate(
                    activePackage.manifest().createdAt()
            );
            String dateSuffix = createdAt.isEmpty()
                    ? ""
                    : getString(
                            R.string.diagnostics_model_package_date,
                            createdAt
                    );
            return getString(
                    R.string.diagnostics_model_package_ready,
                    activePackage.manifest().name(),
                    activePackage.manifest().version(),
                    dateSuffix
            );
        }

        return getString(
                registry.hasRequiredPipeline()
                        ? R.string.diagnostics_model_package_individual
                        : R.string.diagnostics_model_package_partial
        );
    }

    private CharSequence modelDetail(
            ModelManifest manifest
    ) {
        String family = manifest.yoloFamily();
        long parameterCount = manifest.parameterCount();
        String specification;

        if (!family.isEmpty()
                && parameterCount > 0L) {
            specification = getString(
                    R.string.diagnostics_model_family_params,
                    family,
                    parameterCount / 1_000_000.0
            );
        } else if (!family.isEmpty()) {
            specification = getString(
                    R.string.diagnostics_model_family_only,
                    family
            );
        } else if (parameterCount > 0L) {
            specification = getString(
                    R.string.diagnostics_model_params_only,
                    parameterCount / 1_000_000.0
            );
        } else {
            specification = getString(
                    R.string.diagnostics_model_no_metadata
            );
        }

        return specification;
    }

    private static String shortDate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() >= 10
                ? trimmed.substring(0, 10)
                : trimmed;
    }

    private void renderRecentLog(String rawLog) {
        log.removeAllViews();

        if (rawLog == null || rawLog.trim().isEmpty()
                || rawLog.equals("Brak zapisanych zdarzeń")) {
            addEmptyLogRow();
            return;
        }

        String[] sourceLines = rawLog.split("\\R");
        List<String> lines = new ArrayList<>();

        for (String line : sourceLines) {
            if (line != null && !line.trim().isEmpty()) {
                lines.add(line.trim());
            }
        }

        int experimentNumber = 0;
        int activeExperimentColor = 0;
        boolean experimentOpen = false;

        if (historyStartsInsideExperiment(lines)) {
            activeExperimentColor = R.color.alpr_warning;
            addExperimentHeader(
                    getString(
                            R.string.diagnostics_log_previous_experiment
                    ),
                    activeExperimentColor,
                    R.color.alpr_card_warning
            );
            experimentOpen = true;
        }

        for (int i = 0; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            String message = extractLogMessage(rawLine);
            boolean experimentStart = message.startsWith(
                    "Rozpoczęto eksperyment"
            );
            boolean experimentEnd = message.startsWith(
                    "Zakończono eksperyment"
            );

            if (experimentStart) {
                experimentNumber++;
                activeExperimentColor = experimentAccentColor(
                        experimentNumber
                );
                addExperimentHeader(
                        getString(
                                R.string.diagnostics_log_experiment_header,
                                experimentNumber,
                                experimentVariantLabel(message)
                        ),
                        activeExperimentColor,
                        experimentBackgroundColor(experimentNumber)
                );
                experimentOpen = true;

            } else if (experimentEnd
                    && !experimentOpen) {
                activeExperimentColor = R.color.alpr_warning;
                addExperimentHeader(
                        getString(
                                R.string.diagnostics_log_previous_experiment
                        ),
                        activeExperimentColor,
                        R.color.alpr_card_warning
                );
                experimentOpen = true;
            }

            addLogEventRow(
                    i + 1,
                    rawLine,
                    experimentOpen
                            ? activeExperimentColor
                            : 0
            );

            if (experimentEnd) {
                addLogSpacer();
                experimentOpen = false;
                activeExperimentColor = 0;
            }
        }
    }

    private static boolean historyStartsInsideExperiment(
            List<String> lines
    ) {
        for (String line : lines) {
            String message = extractLogMessage(line);
            if (message.startsWith("Rozpoczęto eksperyment")) {
                return false;
            }
            if (message.startsWith("Zakończono eksperyment")) {
                return true;
            }
        }
        return false;
    }

    private void addExperimentHeader(
            String label,
            int accentColor,
            int backgroundColor
    ) {
        TextView header = (TextView) LayoutInflater.from(this).inflate(
                R.layout.item_diagnostic_log_header,
                log,
                false
        );
        header.setText(label);
        header.setTextColor(
                ContextCompat.getColor(
                        this,
                        accentColor
                )
        );
        header.setBackgroundColor(
                ContextCompat.getColor(
                        this,
                        backgroundColor
                )
        );
        log.addView(header);
    }

    private void addLogEventRow(
            int number,
            String rawLine,
            int experimentColor
    ) {
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
        String displayMessage = formatLogMessage(message);

        View row = LayoutInflater.from(this).inflate(
                R.layout.item_diagnostic_log_event,
                log,
                false
        );
        TextView marker = row.findViewById(
                R.id.diagnostic_log_marker
        );
        TextView prefix = row.findViewById(
                R.id.diagnostic_log_prefix
        );
        TextView messageView = row.findViewById(
                R.id.diagnostic_log_message
        );

        marker.setVisibility(
                experimentColor == 0
                        ? View.INVISIBLE
                        : View.VISIBLE
        );
        if (experimentColor != 0) {
            marker.setTextColor(
                    ContextCompat.getColor(
                            this,
                            experimentColor
                    )
            );
        }

        prefix.setText(
                getString(
                        R.string.diagnostics_log_prefix,
                        number,
                        shortTime
                )
        );
        messageView.setText(displayMessage);

        int messageColor = experimentColor != 0
                && (message.startsWith("Rozpoczęto eksperyment")
                || message.startsWith("Zakończono eksperyment"))
                ? experimentColor
                : resolveLogMessageColor(
                        levelAndTag,
                        message
                );
        messageView.setTextColor(
                ContextCompat.getColor(
                        this,
                        messageColor
                )
        );

        if (message.startsWith("Rozpoczęto eksperyment")
                || message.startsWith("Zakończono eksperyment")) {
            messageView.setTypeface(
                    messageView.getTypeface(),
                    Typeface.BOLD
            );
        }

        log.addView(row);
    }

    private void addLogSpacer() {
        View spacer = new View(this);
        spacer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.round(
                                8f
                                        * getResources()
                                        .getDisplayMetrics()
                                        .density
                        )
                )
        );
        log.addView(spacer);
    }

    private void addEmptyLogRow() {
        View row = LayoutInflater.from(this).inflate(
                R.layout.item_diagnostic_log_event,
                log,
                false
        );
        row.findViewById(
                R.id.diagnostic_log_marker
        ).setVisibility(View.GONE);
        row.findViewById(
                R.id.diagnostic_log_prefix
        ).setVisibility(View.GONE);
        ((TextView) row.findViewById(
                R.id.diagnostic_log_message
        )).setText(R.string.persistent_log_empty);
        log.addView(row);
    }

    private String formatLogMessage(String message) {
        if (message.startsWith("Rozpoczęto eksperyment")) {
            return getString(
                    R.string.diagnostics_log_experiment_start,
                    shortSessionId(
                            firstValueAfter(
                                    message,
                                    "Rozpoczęto eksperyment"
                            )
                    ),
                    experimentVariantLabel(message)
            );
        }

        if (message.startsWith("Zakończono eksperyment")) {
            String sessionId = shortSessionId(
                    firstValueAfter(
                            message,
                            "Zakończono eksperyment"
                    )
            );
            String reason = experimentReasonLabel(
                    tokenValue(message, "reason=")
            );
            long durationMillis = parseLong(
                    tokenValue(message, "durationMs=")
            );

            if (durationMillis >= 0L) {
                return getString(
                        R.string.diagnostics_log_experiment_end,
                        reason,
                        durationMillis / 1000.0,
                        sessionId
                );
            }

            return getString(
                    R.string.diagnostics_log_experiment_end_without_time,
                    reason,
                    sessionId
            );
        }

        if (message.startsWith("Upłynął czas eksperymentu")) {
            return getString(
                    R.string.diagnostics_log_timer_limit_reached
            );
        }

        return message;
    }

    private String experimentVariantLabel(String message) {
        String variant = tokenValue(message, "variant=");

        switch (variant) {
            case "r0_full_frame":
                return getString(
                        R.string.diagnostics_log_variant_r0
                );

            case "r1_one_roi":
                return getString(
                        R.string.diagnostics_log_variant_r1
                );

            case "r2_two_roi":
                return getString(
                        R.string.diagnostics_log_variant_r2
                );

            default:
                return variant.isEmpty()
                        ? getString(
                                R.string.diagnostics_log_variant_unknown
                        )
                        : variant.toUpperCase(Locale.ROOT);
        }
    }

    private String experimentReasonLabel(String reason) {
        switch (reason) {
            case "manual":
                return getString(
                        R.string.diagnostics_log_reason_manual
                );

            case "timer":
                return getString(
                        R.string.diagnostics_log_reason_timer
                );

            case "error":
                return getString(
                        R.string.diagnostics_log_reason_error
                );

            default:
                return reason.isEmpty()
                        ? getString(
                                R.string.diagnostics_log_reason_unknown
                        )
                        : reason;
        }
    }

    private static String extractLogMessage(String rawLine) {
        int levelSeparator = rawLine.indexOf(' ');
        int messageSeparator = levelSeparator < 0
                ? -1
                : rawLine.indexOf(' ', levelSeparator + 1);
        return messageSeparator < 0
                ? rawLine
                : rawLine.substring(messageSeparator + 1);
    }

    private static String firstValueAfter(
            String source,
            String prefix
    ) {
        if (!source.startsWith(prefix)) {
            return "";
        }
        String remainder = source.substring(prefix.length()).trim();
        int separator = remainder.indexOf(' ');
        return separator < 0
                ? remainder
                : remainder.substring(0, separator);
    }

    private static String tokenValue(
            String source,
            String prefix
    ) {
        int start = source.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = source.indexOf(' ', start);
        return (end < 0
                ? source.substring(start)
                : source.substring(start, end)).trim();
    }

    private static String shortSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return "—";
        }
        return sessionId.length() <= 10
                ? sessionId
                : "…" + sessionId.substring(sessionId.length() - 9);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static int experimentAccentColor(int experimentNumber) {
        switch ((experimentNumber - 1) % 4) {
            case 1:
                return R.color.alpr_secondary;
            case 2:
                return R.color.alpr_magenta;
            case 3:
                return R.color.alpr_success;
            case 0:
            default:
                return R.color.alpr_primary;
        }
    }

    private static int experimentBackgroundColor(int experimentNumber) {
        switch ((experimentNumber - 1) % 4) {
            case 1:
                return R.color.alpr_card_violet;
            case 2:
                return R.color.alpr_card_magenta;
            case 3:
                return R.color.alpr_card_green;
            case 0:
            default:
                return R.color.alpr_card_blue;
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
