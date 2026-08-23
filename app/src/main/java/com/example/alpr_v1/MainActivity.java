package com.example.alpr_v1;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Size;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AutoTuneResult;
import com.example.alpr_v1.camera.CameraController;
import com.example.alpr_v1.camera.AnalysisResolutionProfile;
import com.example.alpr_v1.camera.CameraMotionMonitor;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.capture.CropCapacityPolicy;
import com.example.alpr_v1.capture.CropSamplingPolicy;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.metrics.CropMiniReport;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.metrics.ReportArchive;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.ui.CameraMotionOverlayTracker;
import com.example.alpr_v1.ui.DetectionOverlayView;
import com.example.alpr_v1.ui.PlateCaptureAdapter;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.alpr_v1.experiment.ExperimentSession;
import com.example.alpr_v1.experiment.TimerConfig;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "MainActivity";
    private static final String KEY_CAMERA_PERMISSION_REQUESTED =
            "camera_permission_requested";
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong lastUiUpdateNanos = new AtomicLong();
    private final CameraMotionOverlayTracker overlayTracker = new CameraMotionOverlayTracker();
    private final ExperimentSession experimentSession =
            new ExperimentSession();

    private final Handler experimentTimerHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable experimentTimerRunnable =
            () -> {
                /*
                 * Nie sprawdzamy aktualnych ustawień EXP.
                 * Liczy się sesja, która została już rozpoczęta.
                 */
                if (!experimentSession.isRunning()) {
                    return;
                }

                recordInfo(
                        "Upłynął czas eksperymentu "
                                + experimentSession.sessionId()
                );

                stopAnalysis(
                        ExperimentSession.CompletionReason.TIMER
                );
            };
    private List<CapturedPlateItem> capturedCrops;
    private Map<Long, CropSamplingPolicy.Previous> lastCaptureByTrack;

    private PreviewView previewView;
    private DetectionOverlayView overlayView;
    private TextView liveStatus;
    private TextView recognitionHint;
    private TextView resultCount;
    private TextView collectionStats;
    private RecyclerView resultsList;
    private View resultsEmpty;
    private View galleryContent;
    private View controlPanel;
    private View mainRoot;
    private MaterialButton collectionToggle;
    private MaterialButton galleryVisibilityToggle;
    private MaterialButton gallerySizeToggle;
    private MaterialButton analysisStartButton;
    private MaterialButton analysisStopButton;
    private MaterialCheckBox selectAllCropsToggle;
    private MaterialButton saveSelectedCropsButton;
    private PlateCaptureAdapter captureAdapter;
    private ProgressBar progress;
    private MaterialToolbar topAppBar;

    private ModelRegistry modelRegistry;
    private AutoTuneManager autoTuneManager;
    private MetricsCollector metricsCollector;
    private DeviceProfile deviceProfile;
    private AlprPipeline pipeline;
    private CameraController cameraController;
    private CameraMotionMonitor cameraMotionMonitor;
    private volatile boolean cameraStarted;

    private boolean explicitExitRequested;
    private ResearchArchive.Kind pendingExportKind;
    private boolean exportInProgress;
    private RecognitionProfile recognitionProfile = RecognitionProfile.BALANCED;
    private AnalysisResolutionProfile analysisResolutionProfile = AnalysisResolutionProfile.AUTO;
    /*
     * Normalna konfiguracja aplikacji.
     */
    private boolean vehicleCascadeEnabled;

    /*
     * Konfiguracja eksperymentalna.
     */
    private boolean experimentModeEnabled;
    private RoiBudgetPolicy experimentRoiBudgetPolicy =
            RoiBudgetPolicy.TWO_ROI;
    private TimerConfig experimentTimerConfig =
            TimerConfig.disabled();
    private volatile boolean collectionActive;
    private String collectionSessionId = "";
    private long collectionSessionStartedElapsedNanos;
    private int collectionSequence;
    private String cropLimitSetting = CropCapacityPolicy.AUTO;
    private int resolvedCropLimit = 10;
    private Uri captureDirectoryUri;
    private boolean pendingBatchDirectorySelection;
    private int pendingBatchWrites;
    private int successfulBatchWrites;
    private int failedBatchWrites;
    private SharedPreferences uiPreferences;
    private int knownSettingsRevision;
    private CaptureGalleryViewModel captureGalleryState;

    private View galleryListContainer;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startCamera(true);
                        } else {
                            liveStatus.setText(
                                    R.string.camera_permission_required
                            );

                            recordWarning(
                                    "Odmówiono dostępu do kamery, możesz dodać dostęp Ustawienia -> Aplikacje."
                            );
                        }
                    }
            );

    private final ActivityResultLauncher<String> reportDestination = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                ResearchArchive.Kind kind = pendingExportKind;
                pendingExportKind = null;
                if (uri != null && kind != null) writeResearchExport(uri, kind);
            }
    );

    private final ActivityResultLauncher<Uri> captureDirectoryPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) {
                    pendingBatchDirectorySelection = false;
                    renderCapturedCrops();
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    captureDirectoryUri = uri;
                    uiPreferences.edit().putString("capture_directory_uri", uri.toString()).apply();
                    boolean continueBatch = pendingBatchDirectorySelection;
                    pendingBatchDirectorySelection = false;
                    if (continueBatch) saveSelectedCrops();
                } catch (SecurityException error) {
                    pendingBatchDirectorySelection = false;
                    renderCapturedCrops();
                    Toast.makeText(this, R.string.capture_directory_error, Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<Intent> diagnosticsScreen = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null
                        && DiagnosticsActivity.ACTION_EXPORT_REPORT.equals(
                        data.getStringExtra(DiagnosticsActivity.EXTRA_ACTION))) {
                    showExportOptions();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        bindViews();
        applySystemInsets();

        captureGalleryState = new ViewModelProvider(this).get(CaptureGalleryViewModel.class);
        capturedCrops = captureGalleryState.capturedCrops();
        lastCaptureByTrack = captureGalleryState.lastCaptureByTrack();
        collectionActive = captureGalleryState.collectionActive();
        collectionSessionId = captureGalleryState.collectionSessionId();
        collectionSessionStartedElapsedNanos =
                captureGalleryState.collectionSessionStartedElapsedNanos();
        collectionSequence = captureGalleryState.collectionSequence();

        modelRegistry = new ModelRegistry(this);
        autoTuneManager = new AutoTuneManager(this);
        metricsCollector = captureGalleryState.metricsCollector();
        deviceProfile = DeviceProfile.capture(this);
        pipeline = new AlprPipeline(this, modelRegistry, metricsCollector, autoTuneManager);
        cameraController = new CameraController(this, this, previewView);
        cameraMotionMonitor = new CameraMotionMonitor(this);
        metricsCollector.setMotionSensorAvailable(cameraMotionMonitor.isAvailable());
        recordInfo("Uruchomiono aplikację");

        uiPreferences = getSharedPreferences("alpr_ui", MODE_PRIVATE);
        knownSettingsRevision = uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0);
        configureRecognitionProfile();
        configureAnalysisResolutionProfile();
        /*
         * Normalna konfiguracja użytkownika.
         */
        vehicleCascadeEnabled =
                uiPreferences.getBoolean(
                        "vehicle_cascade_enabled",
                        false
                );

        /*
         * Konfiguracja eksperymentalna jest całkowicie oddzielna.
         */
        experimentModeEnabled =
                uiPreferences.getBoolean(
                        SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,
                        false
                );

        String legacyRoiPolicy =
                uiPreferences.getString(
                        "roi_budget_policy",
                        RoiBudgetPolicy.TWO_ROI.wireName()
                );

        experimentRoiBudgetPolicy =
                RoiBudgetPolicy.fromWireName(
                        uiPreferences.getString(
                                SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,
                                legacyRoiPolicy
                        )
                );
        experimentTimerConfig =
                readExperimentTimerConfig();

        pipeline.setVehicleCascadeEnabled(
                vehicleCascadeEnabled
        );

        pipeline.setExperimentConfiguration(
                experimentModeEnabled,
                experimentRoiBudgetPolicy
        );


        configureAppMenu();
        configureCaptureCollection();
        configureAnalysisControls();
        modelRegistry.reload();

        scheduleMissingAutotuning();
    }

    private void bindViews() {
        previewView = findViewById(R.id.camera_preview);
        overlayView = findViewById(R.id.detection_overlay);
        analysisStartButton = findViewById(R.id.analysis_start_button);
        analysisStopButton = findViewById(R.id.analysis_stop_button);
        liveStatus = findViewById(R.id.live_status);
        recognitionHint = findViewById(R.id.recognition_hint);
        resultCount = findViewById(R.id.result_count);
        collectionStats = findViewById(R.id.collection_stats);
        collectionToggle = findViewById(R.id.collection_toggle);
        galleryVisibilityToggle = findViewById(R.id.gallery_visibility_toggle);
        gallerySizeToggle = findViewById(R.id.gallery_size_toggle);
        selectAllCropsToggle = findViewById(R.id.crop_select_all);
        saveSelectedCropsButton = findViewById(R.id.crop_save_selected);
        resultsList = findViewById(R.id.results_list);
        resultsEmpty = findViewById(R.id.results_empty);
        galleryContent = findViewById(R.id.gallery_content);
        controlPanel = findViewById(R.id.control_panel);
        mainRoot = findViewById(R.id.main);
        progress = findViewById(R.id.progress);
        topAppBar = findViewById(R.id.top_app_bar);
        galleryListContainer = findViewById(R.id.gallery_list_container);
    }

    private void configureRecognitionProfile() {
        recognitionProfile = RecognitionProfile.fromWireName(
                uiPreferences.getString(
                        "recognition_profile",
                        RecognitionProfile.BALANCED.wireName()
                )
        );
        pipeline.setRecognitionProfile(recognitionProfile);
        metricsCollector.setRecognitionProfile(recognitionProfile.wireName());
    }

    private void configureAppMenu() {
        topAppBar.setOnMenuItemClickListener(this::handleMenuItem);
    }

    private boolean handleMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.menu_diagnostics) {
            openDiagnostics();
        } else if (id == R.id.menu_clear_result) {
            clearCurrentResult();
        } else if (id == R.id.menu_help) {
            showHelp();
        } else if (id == R.id.menu_about) {
            showAbout();
        } else if (id == R.id.menu_exit_app) {
            showExitConfirmation();
        } else {
            return false;
        }
        return true;
    }

    private void openDiagnostics() {
        Intent intent = new Intent(this, DiagnosticsActivity.class)
                .putExtra(DiagnosticsActivity.EXTRA_CROP_COUNT, capturedCrops.size())
                .putExtra(DiagnosticsActivity.EXTRA_CROP_LIMIT, resolvedCropLimit)
                .putExtra(DiagnosticsActivity.EXTRA_SESSION_ID, collectionSessionId)
                .putExtra(DiagnosticsActivity.EXTRA_COLLECTION_ACTIVE, collectionActive);
        diagnosticsScreen.launch(intent);
    }

    private void applyRecognitionProfile(RecognitionProfile profile) {
        recognitionProfile = profile;
        uiPreferences.edit()
                .putString("recognition_profile", recognitionProfile.wireName())
                .apply();
        pipeline.setRecognitionProfile(recognitionProfile);
        pipeline.resetTracking();
        metricsCollector.setRecognitionProfile(recognitionProfile.wireName());
        overlayTracker.reset();
        lastCaptureByTrack.clear();
        liveStatus.setText(getString(
                R.string.recognition_profile_changed,
                recognitionProfileLabel()
        ));
    }

    private void configureAnalysisResolutionProfile() {
        analysisResolutionProfile = AnalysisResolutionProfile.fromWireName(
                uiPreferences.getString(
                        "analysis_resolution_profile",
                        AnalysisResolutionProfile.AUTO.wireName()
                )
        );
        updateCaptureMetrics();
    }

    private void applyAnalysisResolutionProfile(AnalysisResolutionProfile profile) {
        if (analysisResolutionProfile == profile) return;
        analysisResolutionProfile = profile;
        uiPreferences.edit()
                .putString("analysis_resolution_profile", profile.wireName())
                .apply();
        updateCaptureMetrics();
        pipeline.resetTracking();
        overlayTracker.reset();
        lastCaptureByTrack.clear();
        if (cameraStarted
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            cameraController.stop();
            cameraStarted = false;
            startCamera(false);
        }
        liveStatus.setText(getString(
                R.string.resolution_profile_changed,
                resolutionProfileLabel()
        ));
    }

    private void updateCaptureMetrics() {
        Size requested = chooseAnalysisSize();
        metricsCollector.setCaptureConfiguration(
                analysisResolutionProfile.wireName(),
                requested.getWidth(),
                requested.getHeight()
        );
    }

    private String resolutionProfileLabel() {
        switch (analysisResolutionProfile) {
            case FAST:
                return getString(R.string.resolution_profile_fast);
            case DISTANT:
                return getString(R.string.resolution_profile_distant);
            case AUTO:
            default:
                return getString(R.string.resolution_profile_auto);
        }
    }

    private RoiBudgetPolicy effectiveRoiBudgetPolicy() {
        if (experimentModeEnabled) {
            return experimentRoiBudgetPolicy;
        }

        return vehicleCascadeEnabled
                ? RoiBudgetPolicy.TWO_ROI
                : RoiBudgetPolicy.FULL_FRAME;
    }
    private TimerConfig readExperimentTimerConfig() {

        boolean enabled =
                uiPreferences.getBoolean(
                        SettingsActivity.KEY_EXPERIMENT_TIMER_ENABLED,
                        false
                );

        int seconds =
                uiPreferences.getInt(
                        SettingsActivity.KEY_EXPERIMENT_TIMER_SECONDS,
                        TimerConfig.DEFAULT_DURATION_SECONDS
                );

        return TimerConfig.of(
                enabled,
                seconds
        );
    }

    private void setVehicleCascadeEnabled(boolean enabled) {
        vehicleCascadeEnabled = enabled;
        uiPreferences.edit().putBoolean("vehicle_cascade_enabled", enabled).apply();
        pipeline.setVehicleCascadeEnabled(enabled);
        overlayTracker.reset();
        lastCaptureByTrack.clear();
        boolean hasVehicleModel = modelRegistry.getActive(
                com.example.alpr_v1.model.ModelRole.VEHICLE
        ) != null;
        if (enabled && !hasVehicleModel) {
            liveStatus.setText(R.string.vehicle_cascade_missing_model);
        } else {
            liveStatus.setText(enabled
                    ? R.string.vehicle_cascade_enabled
                    : R.string.vehicle_cascade_disabled);
        }
    }

    private void clearCurrentResult() {
        if (exportInProgress) {
            Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        overlayTracker.reset();
        pipeline.resetTracking();
        overlayView.setItems(java.util.Collections.emptyList());
        recognitionHint.setText(R.string.recognition_searching);
        clearCapturedCrops();
        liveStatus.setText(R.string.recognition_cleared);
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.menu_close, null)
                .show();
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.menu_close, null)
                .show();
    }

    private void showExitConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exit_app_title)
                .setMessage(R.string.exit_app_message)
                .setNegativeButton(
                        R.string.exit_app_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.exit_app_confirm,
                        (dialog, which) -> closeApplication()
                )
                .show();
    }
    private void closeApplication() {
        /*
         * Twarde zakończenie procesu wykonujemy tylko wtedy,
         * gdy użytkownik jawnie wybrał "Zamknij aplikację".
         */
        explicitExitRequested = true;

        /*
         * Najpierw normalnie kończymy aktywne operacje.
         */
        if (cameraStarted) {
            stopAnalysis(
                    ExperimentSession.CompletionReason.MANUAL
            );
        } else {
            if (collectionActive) {
                pauseCropCollectionForStoppedAnalysis();
            }

            if (metricsCollector != null
                    && metricsCollector.isMeasurementSessionActive()) {
                finishAnalysisMeasurement(
                        ExperimentSession.CompletionReason.MANUAL
                );
            }
        }

        recordInfo(
                "Zamykanie aplikacji przez użytkownika"
        );

        /*
         * Kończymy task. Faktyczne zakończenie procesu
         * nastąpi w onDestroy(), po zwolnieniu zasobów.
         */
        finishAndRemoveTask();
    }

    private String recognitionProfileLabel() {
        switch (recognitionProfile) {
            case FAST:
                return getString(R.string.recognition_profile_fast);
            case ACCURATE:
                return getString(R.string.recognition_profile_accurate);
            case BALANCED:
            default:
                return getString(R.string.recognition_profile_balanced);
        }
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void configureAnalysisControls() {
        analysisStartButton.setOnClickListener(
                view -> ensureCameraPermission()
        );

        analysisStopButton.setOnClickListener(
                view -> stopAnalysis()
        );

        if (!cameraStarted) {
            previewView.setVisibility(View.INVISIBLE);
        }

        renderAnalysisControls();

        liveStatus.setText(R.string.analysis_idle);
        recognitionHint.setText(R.string.analysis_idle_hint);
    }


    private void renderAnalysisControls() {
        analysisStartButton.setVisibility(
                cameraStarted ? View.GONE : View.VISIBLE
        );

        analysisStopButton.setVisibility(
                cameraStarted ? View.VISIBLE : View.GONE
        );

        /*
         * Cropy mogą być zbierane wyłącznie wtedy,
         * gdy działa kamera i pipeline.
         */
        if (collectionToggle != null) {
            collectionToggle.setEnabled(cameraStarted);
        }
    }
    private void pauseCropCollectionForStoppedAnalysis() {
        if (!collectionActive) {
            if (collectionToggle != null) {
                collectionToggle.setEnabled(false);
            }
            return;
        }

        collectionActive = false;

        metricsCollector.setCropCollectionActive(false);

        captureGalleryState.retainSession(
                false,
                collectionSessionId,
                collectionSessionStartedElapsedNanos,
                collectionSequence
        );

        renderCapturedCrops();

        recordInfo(
                "Wstrzymano zbieranie cropów z powodu zatrzymania analizy"
        );
    }
    private void stopAnalysis() {
        stopAnalysis(
                ExperimentSession.CompletionReason.MANUAL
        );
    }
    private void stopAnalysis(
            ExperimentSession.CompletionReason reason
    ) {
        cameraStarted = false;
        /*
         * Po zatrzymaniu źródła danych kolektor cropów
         * również nie może pozostać aktywny.
         */
        pauseCropCollectionForStoppedAnalysis();
        /*
         * Od tej chwili żaden kolejny trace nie należy
         * już do zakończonego przebiegu.
         */
        finishAnalysisMeasurement(reason);

        if (cameraController != null) {
            cameraController.stop();
        }
        /*
         * PreviewView zachowuje ostatnią wyrenderowaną klatkę
         * nawet po odpięciu CameraX. Ukrywamy podgląd, ale
         * pozostawiamy jego miejsce w layoucie.
         */
        previewView.setVisibility(View.INVISIBLE);

        if (cameraMotionMonitor != null) {
            cameraMotionMonitor.stop();
        }

        /*
         * Nowe uruchomienie analizy ma zacząć bez starego
         * trackingu i overlayu.
         *
         * Nie kasujemy historii cropów.
         */
        if (pipeline != null) {
            pipeline.resetTracking();
        }

        overlayTracker.reset();
        lastCaptureByTrack.clear();

        overlayView.setItems(
                java.util.Collections.emptyList()
        );

        liveStatus.setText(
                R.string.analysis_idle
        );

        recognitionHint.setText(
                R.string.analysis_idle_hint
        );

        renderAnalysisControls();

        recordInfo(
                "Kamera i pipeline zatrzymane"
        );
    }

    private void scheduleExperimentTimer(
            TimerConfig timerConfig
    ) {
        cancelExperimentTimer();

        if (timerConfig == null
                || !timerConfig.enabled()
                || !experimentSession.isRunning()) {
            return;
        }

        experimentTimerHandler.postDelayed(
                experimentTimerRunnable,
                timerConfig.durationMillis()
        );

        recordInfo(
                "Aktywowano timer eksperymentu: "
                        + timerConfig.durationSeconds()
                        + " s"
        );
    }


    private void cancelExperimentTimer() {
        experimentTimerHandler.removeCallbacks(
                experimentTimerRunnable
        );
    }
    private void beginAnalysisMeasurement() {
        metricsCollector.startMeasurementSession();

        if (experimentModeEnabled) {

            /*
             * Zamrażamy konfigurację timera dla konkretnego przebiegu.
             * Zmiana ustawień później nie zmienia aktywnej sesji.
             */
            TimerConfig timerForSession =
                    experimentTimerConfig;

            boolean started =
                    experimentSession.start(
                            "roi_budget",
                            experimentRoiBudgetPolicy.wireName(),
                            timerForSession
                    );

            if (started) {

                recordInfo(
                        "Rozpoczęto eksperyment "
                                + experimentSession.sessionId()
                                + " type="
                                + experimentSession.experimentType()
                                + " variant="
                                + experimentSession.variant()
                );

                scheduleExperimentTimer(
                        timerForSession
                );
            }

        } else {

            cancelExperimentTimer();

            /*
             * Zwykła analiza nie może odziedziczyć informacji
             * o wcześniejszym eksperymencie.
             */
            experimentSession.reset();
        }
    }


    private void finishAnalysisMeasurement(
            ExperimentSession.CompletionReason reason
    ) {
        /*
         * Każda droga zakończenia sesji:
         * MANUAL, TIMER albo ERROR
         * unieważnia oczekujący callback timera.
         */
        cancelExperimentTimer();

        if (experimentSession.isRunning()) {
            experimentSession.finish(reason);

            recordInfo(
                    "Zakończono eksperyment "
                            + experimentSession.sessionId()
                            + " reason="
                            + experimentSession.completionReasonWireName()
                            + " durationMs="
                            + experimentSession.durationMillis()
            );
        }

        metricsCollector.finishMeasurementSession();
    }
    private void ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            startCamera(true);
            return;
        }

        boolean requestedBefore =
                uiPreferences.getBoolean(
                        KEY_CAMERA_PERMISSION_REQUESTED,
                        false
                );

        boolean canShowRationale =
                shouldShowRequestPermissionRationale(
                        Manifest.permission.CAMERA
                );

        /*
         * Jeżeli wcześniej pytaliśmy o zgodę, a Android nie chce już
         * pokazać wyjaśnienia/systemowego dialogu, traktujemy to jako
         * konieczność wejścia do ustawień aplikacji.
         */
        if (requestedBefore && !canShowRationale) {
            showCameraPermissionSettingsDialog();
            return;
        }

        uiPreferences.edit()
                .putBoolean(
                        KEY_CAMERA_PERMISSION_REQUESTED,
                        true
                )
                .apply();

        permissionLauncher.launch(
                Manifest.permission.CAMERA
        );
    }

    private void showCameraPermissionSettingsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Dostęp do kamery")
                .setMessage(
                        "Dostęp do kamery został wyłączony. "
                                + "Aby uruchomić analizę, nadaj aplikacji "
                                + "uprawnienie do kamery w ustawieniach Androida."
                )
                .setNegativeButton(
                        "Anuluj",
                        null
                )
                .setPositiveButton(
                        "Otwórz ustawienia",
                        (dialog, which) -> openApplicationSettings()
                )
                .show();
    }
    private void openApplicationSettings() {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        );

        intent.setData(
                Uri.parse(
                        "package:" + getPackageName()
                )
        );

        startActivity(intent);
    }

    private void startCamera(boolean beginNewMeasurement) {
        if (cameraStarted) return;
        if (beginNewMeasurement) {
            beginAnalysisMeasurement();
        }
        previewView.setVisibility(View.VISIBLE);

        /*
         * Każde ręczne uruchomienie analizy rozpoczyna się
         * bez stanu trackingowego poprzedniego przebiegu.
         */
        pipeline.resetTracking();
        overlayTracker.reset();
        lastCaptureByTrack.clear();

        overlayView.setItems(
                java.util.Collections.emptyList()
        );

        cameraStarted = true;

        renderAnalysisControls();

        if (cameraMotionMonitor != null) {
            cameraMotionMonitor.start();
        }

        liveStatus.setText(
                R.string.camera_starting
        );

        recognitionHint.setText(
                R.string.recognition_searching
        );

        recordInfo(
                beginNewMeasurement
                        ? "Uruchamianie kamery i pipeline'u"
                        : "Restart kamery w aktywnej analizie"
        );
        cameraController.start(
                image -> {
                    long observationNanos = System.nanoTime();
                    pipeline.setRapidCameraMotion(cameraMotionMonitor.isRapidMotion());
                    PipelineResult result = pipeline.process(image);
                    if (result == null) return;
                    if (!cameraStarted) {
                        result.close();
                        return;
                    }
                    long now = System.nanoTime();
                    long previous = lastUiUpdateNanos.get();
                    if (now - previous >= 200_000_000L && lastUiUpdateNanos.compareAndSet(previous, now)) {
                        runOnUiThread(() -> {
                            try {
                                presentResult(result, observationNanos);
                            } finally {
                                result.close();
                            }
                        });
                    } else if (collectionActive && containsNewCrop(result.plateObservations)) {
                        runOnUiThread(() -> {
                            try {
                                collectCrops(result.plateObservations);
                            } finally {
                                result.close();
                            }
                        });
                    } else {
                        result.close();
                    }
                },
                error -> runOnUiThread(() -> {
                    cameraStarted = false;
                    finishAnalysisMeasurement(
                            ExperimentSession.CompletionReason.ERROR
                    );
                    if (cameraMotionMonitor != null) {
                        cameraMotionMonitor.stop();
                    }

                    renderAnalysisControls();
                    liveStatus.setText(getString(R.string.camera_error, error.getMessage()));
                    recordError("Błąd kamery: " + error.getMessage(), error);
                }),
                chooseAnalysisSize()
        );
    }

    private Size chooseAnalysisSize() {
        if (analysisResolutionProfile == AnalysisResolutionProfile.FAST) {
            return new Size(640, 480);
        }
        if (analysisResolutionProfile == AnalysisResolutionProfile.DISTANT) {
            return new Size(1920, 1080);
        }
        boolean constrained = deviceProfile.lowRamDevice
                || deviceProfile.totalMemoryBytes < 4L * 1024L * 1024L * 1024L;
        return constrained ? new Size(640, 480) : new Size(1280, 720);
    }

    private void presentResult(PipelineResult result, long observationNanos) {
        if (result.sceneReset) {

            /*
             * Pipeline rozpoczął nową scenę.
             *
             * Usuwamy więc również stan trackera warstwy UI.
             */
            overlayTracker.reset();

            /*
             * TrackId w pipeline może po resecie zacząć się ponownie od 1.
             * Stary stan próbkowania cropów nie może zostać przypisany
             * do tablicy z nowego zdjęcia.
             */
            lastCaptureByTrack.clear();

            /*
             * Natychmiast usuwamy ewentualny stary overlay.
             */
            overlayView.setItems(
                    java.util.Collections.emptyList()
            );
        }
        liveStatus.setText(result.message);
        if ("pipeline_error".equals(result.status)) refreshPersistentLogThrottled();
        overlayView.setItems(
                overlayTracker.update(result.overlayItems, observationNanos, System.nanoTime()),
                result.sourceWidth,
                result.sourceHeight
        );
        if (collectionActive) collectCrops(result.plateObservations);
        if ("models_missing".equals(result.status) || "pipeline_error".equals(result.status)) {
            recognitionHint.setText(R.string.recognition_unavailable);
        } else if (!result.plateObservations.isEmpty()) {
            int confirmed = 0;
            for (PlateObservation observation : result.plateObservations) {
                if (observation.confirmed) confirmed++;
            }
            recognitionHint.setText(getString(
                    R.string.live_results_summary,
                    result.plateObservations.size(),
                    confirmed
            ));
        } else if ("stabilizing".equals(result.status) || !result.overlayItems.isEmpty()) {
            recognitionHint.setText(R.string.recognition_stabilizing);
        } else {
            recognitionHint.setText(R.string.recognition_searching);
        }
    }

    private void configureCaptureCollection() {
        cropLimitSetting = CropCapacityPolicy.normalizeSetting(
                uiPreferences.getString("crop_limit", CropCapacityPolicy.AUTO)
        );
        resolvedCropLimit = CropCapacityPolicy.resolve(
                cropLimitSetting,
                Runtime.getRuntime().maxMemory(),

                deviceProfile.lowRamDevice
        );
        String directory = uiPreferences.getString("capture_directory_uri", "");
        if (!directory.isEmpty()) {
            try {
                captureDirectoryUri = Uri.parse(directory);
            } catch (RuntimeException ignored) {
                captureDirectoryUri = null;
            }
        }
        captureAdapter = new PlateCaptureAdapter(new PlateCaptureAdapter.SelectionListener() {
            @Override
            public void onSelectionChanged(CapturedPlateItem item, boolean selected) {
                onCropSelectionChanged(item, selected);
            }

            @Override
            public void onVerificationChanged(
                    CapturedPlateItem item,
                    CapturedPlateItem.VerificationStatus status
            ) {
                applyHumanVerification(item, status, "");
            }

            @Override
            public void onCorrectionRequested(CapturedPlateItem item) {
                showCorrectionDialog(item);
            }
        });
        resultsList.setLayoutManager(new LinearLayoutManager(
                this, RecyclerView.VERTICAL, false
        ));
        resultsList.setAdapter(captureAdapter);
        collectionToggle.setOnClickListener(view -> toggleCollection());
        galleryVisibilityToggle.setOnClickListener(view -> {
            captureGalleryState.setGalleryExpanded(!captureGalleryState.galleryExpanded());
            renderGalleryVisibility();
        });
        gallerySizeToggle.setOnClickListener(view -> {
            captureGalleryState.setGalleryMaximized(!captureGalleryState.galleryMaximized());
            renderGalleryVisibility();
        });
        selectAllCropsToggle.setOnCheckedChangeListener(
                (button, checked) -> selectAllCrops(checked)
        );
        saveSelectedCropsButton.setOnClickListener(view -> saveSelectedCrops());
        /*
         * Nowa instancja MainActivity nie uruchamia kamery automatycznie.
         * Nie możemy więc odziedziczyć aktywnego stanu kolektora.
         */
        if (!cameraStarted && collectionActive) {
            collectionActive = false;

            metricsCollector.setCropCollectionActive(false);

            captureGalleryState.retainSession(
                    false,
                    collectionSessionId,
                    collectionSessionStartedElapsedNanos,
                    collectionSequence
            );
        }
        metricsCollector.setCropCapacity(resolvedCropLimit);
        renderCapturedCrops();
    }

    private void toggleCollection() {
        if (!cameraStarted) {
            collectionActive = false;
            metricsCollector.setCropCollectionActive(false);
            renderCapturedCrops();
            return;
        }
        collectionActive = !collectionActive;
        if (collectionActive && collectionSessionId.isEmpty()) {
            collectionSessionId = "s-" + new SimpleDateFormat(
                    "yyyyMMdd-HHmmss", Locale.ROOT
            ).format(new Date()) + "-" + UUID.randomUUID().toString().substring(0, 6);
            collectionSessionStartedElapsedNanos = android.os.SystemClock.elapsedRealtimeNanos();
            collectionSequence = 0;
            lastCaptureByTrack.clear();
            metricsCollector.startCropSession(collectionSessionId, resolvedCropLimit);
            recordInfo("Rozpoczęto sesję cropów " + collectionSessionId);
        } else if (collectionActive) {
            metricsCollector.setCropCollectionActive(true);
            recordInfo("Wznowiono sesję cropów " + collectionSessionId);
        } else {
            metricsCollector.setCropCollectionActive(false);
            recordInfo("Wstrzymano sesję cropów " + collectionSessionId);
        }
        renderCapturedCrops();
    }

    private void collectCrops(List<PlateObservation> observations) {
        if (!collectionActive) return;
        boolean changed = false;
        for (PlateObservation observation : observations) {
            if (observation.previewBitmap == null || observation.previewBitmap.isRecycled()
                    || observation.timing == null) continue;
            CropSamplingPolicy.Previous previous = lastCaptureByTrack.get(observation.trackId);
            if (!CropSamplingPolicy.shouldCapture(
                    previous,
                    observation.text,
                    observation.confirmed,
                    observation.sharpness,
                    observation.capturedElapsedNanos
            )) continue;
            Bitmap bitmap = observation.previewBitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (bitmap == null) continue;
            collectionSequence++;
            String captureId = String.format(
                    Locale.ROOT, "%s-c%04d", collectionSessionId, collectionSequence
            );
            CapturedPlateItem captured = new CapturedPlateItem(
                    captureId,
                    collectionSessionId,
                    observation.trackId,
                    bitmap,
                    observation.text,
                    observation.plateConfidence,
                    observation.recognitionConfidence,
                    observation.confirmed,
                    observation.characters,
                    observation.capturedAtMillis,
                    observation.capturedElapsedNanos,
                    observation.sharpness,
                    observation.timing
            );
            try {
                captured.miniReportJson = CropMiniReport.create(
                        captured,
                        collectionSessionStartedElapsedNanos,
                        deviceProfile,
                        modelRegistry,
                        autoTuneManager,
                        recognitionProfile.wireName(),
                        analysisResolutionProfile.wireName(),
                        vehicleCascadeEnabled,
                        experimentModeEnabled,
                        experimentRoiBudgetPolicy,
                        effectiveRoiBudgetPolicy()
                );
            } catch (Exception error) {
                AppLog.warning(this, LOG_TAG, "Nie udało się zamrozić miniraportu cropu");
            }
            capturedCrops.add(captured);
            metricsCollector.recordCapturedCrop(captured);
            lastCaptureByTrack.put(observation.trackId, new CropSamplingPolicy.Previous(
                    observation.text,
                    observation.confirmed,
                    observation.sharpness,
                    observation.capturedElapsedNanos
            ));
            changed = true;
        }
        if (!changed) return;
        enforceCropLimit();
        renderCapturedCrops();
    }

    private static boolean containsNewCrop(List<PlateObservation> observations) {
        for (PlateObservation observation : observations) {
            if (observation.previewBitmap != null && observation.timing != null) return true;
        }
        return false;
    }

    private void enforceCropLimit() {
        while (capturedCrops.size() > resolvedCropLimit) {
            CapturedPlateItem removable = null;
            for (CapturedPlateItem item : capturedCrops) {
                if (!item.isProtectedFromEviction()) {
                    removable = item;
                    break;
                }
            }
            if (removable == null) {
                collectionActive = false;
                Toast.makeText(this, R.string.collection_limit_blocked, Toast.LENGTH_LONG).show();
                break;
            }
            capturedCrops.remove(removable);
            removable.recycle();
        }
    }

    private void renderCapturedCrops() {
        if (captureAdapter == null) return;
        updateCaptureAdapterItems();
        boolean empty = capturedCrops.isEmpty();
        resultsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        resultsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        renderGalleryVisibility();
        resultCount.setText(String.valueOf(capturedCrops.size()));
        collectionToggle.setText(collectionActive
                ? R.string.collection_stop
                : R.string.collection_start);
        collectionToggle.setIconResource(collectionActive
                ? R.drawable.ic_stop_24
                : R.drawable.ic_camera_24);
        if (collectionActive) {
            String shortId = collectionSessionId.length() <= 8
                    ? collectionSessionId
                    : collectionSessionId.substring(collectionSessionId.length() - 8);
            collectionStats.setText(getString(
                    R.string.collection_running,
                    shortId,
                    capturedCrops.size(),
                    resolvedCropLimit
            ));
        } else if (collectionSessionId.isEmpty()) {
            collectionStats.setText(R.string.collection_ready);
        } else {
            collectionStats.setText(getString(
                    R.string.collection_paused,
                    capturedCrops.size(),
                    resolvedCropLimit
            ));
        }
        updateSelectionControls();
    }

    private void renderGalleryVisibility() {
        if (galleryContent == null || galleryVisibilityToggle == null
                || gallerySizeToggle == null
                || captureGalleryState == null) return;
        boolean expanded = captureGalleryState.galleryExpanded();
        boolean maximized = captureGalleryState.galleryMaximized();
        galleryContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        galleryVisibilityToggle.setText(null);
        galleryVisibilityToggle.setContentDescription(getString(expanded
                ? R.string.gallery_hide_description
                : R.string.gallery_show_description));
        galleryVisibilityToggle.setIconResource(expanded
                ? R.drawable.ic_expand_more_24
                : R.drawable.ic_expand_less_24);
        galleryVisibilityToggle.setTooltipText(getString(expanded
                ? R.string.gallery_hide_description
                : R.string.gallery_show_description));
        gallerySizeToggle.setVisibility(expanded ? View.VISIBLE : View.GONE);
        gallerySizeToggle.setIconResource(maximized
                ? R.drawable.ic_gallery_restore_24
                : R.drawable.ic_gallery_maximize_24);
        gallerySizeToggle.setContentDescription(getString(maximized
                ? R.string.gallery_restore_description
                : R.string.gallery_maximize_description));
        gallerySizeToggle.setTooltipText(getString(maximized
                ? R.string.gallery_restore_description
                : R.string.gallery_maximize_description));
        updateGalleryLayout(maximized);
        updateControlPanelHeight(expanded, maximized);
    }

    private void updateCaptureAdapterItems() {
        RecyclerView.LayoutManager manager = resultsList.getLayoutManager();
        LinearLayoutManager linear = manager instanceof LinearLayoutManager
                ? (LinearLayoutManager) manager
                : null;
        int previousCount = captureAdapter.getItemCount();
        int firstVisible = linear == null ? RecyclerView.NO_POSITION
                : linear.findFirstVisibleItemPosition();
        View anchor = firstVisible == RecyclerView.NO_POSITION || linear == null
                ? null
                : linear.findViewByPosition(firstVisible);
        int anchorOffset = 0;
        if (anchor != null) {
            anchorOffset = linear.getOrientation() == RecyclerView.HORIZONTAL
                    ? anchor.getLeft() - resultsList.getPaddingLeft()
                    : anchor.getTop() - resultsList.getPaddingTop();
        }
        captureAdapter.setItems(capturedCrops);
        int addedAtFront = captureAdapter.getItemCount() - previousCount;
        if (linear != null && firstVisible > 0 && addedAtFront > 0) {
            linear.scrollToPositionWithOffset(firstVisible + addedAtFront, anchorOffset);
        }
    }

    private void updateGalleryLayout(boolean maximized) {
        RecyclerView.LayoutManager current = resultsList.getLayoutManager();

        if (!(current instanceof LinearLayoutManager)
                || ((LinearLayoutManager) current).getOrientation()
                != RecyclerView.VERTICAL) {

            resultsList.setLayoutManager(
                    new LinearLayoutManager(
                            this,
                            RecyclerView.VERTICAL,
                            false
                    )
            );
        }
    }

    private void updateControlPanelHeight(boolean galleryExpanded, boolean galleryMaximized) {
        if (controlPanel == null) return;
        int targetHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int availableHeight = 0;
        if (galleryMaximized || (galleryExpanded && landscape)) {
            availableHeight = mainRoot == null || liveStatus == null
                    ? 0
                    : mainRoot.getHeight() - liveStatus.getBottom();
            if (availableHeight <= 0) {
                availableHeight = Math.round(
                        getResources().getDisplayMetrics().heightPixels * 0.65f
                );
                controlPanel.post(() -> updateControlPanelHeight(
                        captureGalleryState != null && captureGalleryState.galleryExpanded(),
                        captureGalleryState != null && captureGalleryState.galleryMaximized()
                ));
            }
            targetHeight = Math.max(1, availableHeight);
        }
        ViewGroup.LayoutParams containerParameters =
                galleryListContainer.getLayoutParams();

        int listHeight = Math.round(
                344f * getResources().getDisplayMetrics().density
        );

        if (galleryMaximized && availableHeight > 0) {
            int reserved = Math.round(
                    150f * getResources().getDisplayMetrics().density
            );

            listHeight = Math.max(
                    1,
                    availableHeight - reserved
            );
        }

        if (containerParameters.height != listHeight) {
            containerParameters.height = listHeight;
            galleryListContainer.setLayoutParams(containerParameters);
        }
        ViewGroup.LayoutParams parameters = controlPanel.getLayoutParams();
        if (parameters.height != targetHeight) {
            parameters.height = targetHeight;
            controlPanel.setLayoutParams(parameters);
        }
    }

    private void onCropSelectionChanged(CapturedPlateItem item, boolean selected) {
        item.selectedForSave = selected && isSelectableForSave(item);
        updateSelectionControls();
    }

    private void applyHumanVerification(
            CapturedPlateItem item,
            CapturedPlateItem.VerificationStatus status,
            String correctedText
    ) {
        item.verificationStatus = status;
        item.verificationRevision++;
        if (status == CapturedPlateItem.VerificationStatus.NOT_REVIEWED) {
            item.groundTruthText = "";
            item.verifiedAtMillis = 0L;
        } else {
            item.verifiedAtMillis = System.currentTimeMillis();
            item.groundTruthText = status == CapturedPlateItem.VerificationStatus.ACCEPTED
                    ? item.text
                    : status == CapturedPlateItem.VerificationStatus.CORRECTED
                    ? correctedText.trim().toUpperCase(Locale.ROOT)
                    : "";
        }
        metricsCollector.markHumanVerification(item);
        captureAdapter.setItems(capturedCrops);
        refreshPersistedVerification(item);
    }

    private void refreshPersistedVerification(CapturedPlateItem item) {
        if (item.miniReportJson.isEmpty()) return;
        final String refreshed;
        try {
            refreshed = CropMiniReport.refreshHumanVerification(item.miniReportJson, item);
            item.miniReportJson = refreshed;
        } catch (Exception error) {
            AppLog.warning(this, LOG_TAG, "Nie udało się zaktualizować manualnej walidacji");
            return;
        }
        if (item.savedReportUri == null) return;
        backgroundExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(
                    item.savedReportUri, "wt"
            )) {
                if (output == null) throw new IllegalStateException("Brak strumienia raportu cropu");
                output.write(refreshed.getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                AppLog.error(
                        this,
                        LOG_TAG,
                        "Nie udało się zapisać manualnej walidacji cropu: " + error.getMessage(),
                        error
                );
            }
        });
    }

    private void showCorrectionDialog(CapturedPlateItem item) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.verification_edit_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(item.groundTruthText.isEmpty() ? item.text : item.groundTruthText);
        input.setSelectAllOnFocus(true);
        int padding = Math.round(20f * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.verification_edit_title)
                .setView(container)
                .setNegativeButton(R.string.menu_close, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(view -> {
            String corrected = input.getText().toString().trim();
            if (corrected.isEmpty()) {
                input.setError(getString(R.string.verification_empty_error));
                return;
            }
            applyHumanVerification(
                    item,
                    CapturedPlateItem.VerificationStatus.CORRECTED,
                    corrected
            );
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void selectAllCrops(boolean selected) {
        for (CapturedPlateItem item : capturedCrops) {
            if (isSelectableForSave(item)) item.selectedForSave = selected;
        }
        captureAdapter.setItems(capturedCrops);
        updateSelectionControls();
    }

    private void updateSelectionControls() {
        int selectable = 0;
        int selected = 0;
        for (CapturedPlateItem item : capturedCrops) {
            if (!isSelectableForSave(item)) continue;
            selectable++;
            if (item.selectedForSave) selected++;
        }
        selectAllCropsToggle.setOnCheckedChangeListener(null);
        selectAllCropsToggle.setChecked(selectable > 0 && selected == selectable);
        selectAllCropsToggle.setEnabled(selectable > 0);
        selectAllCropsToggle.setOnCheckedChangeListener(
                (button, checked) -> selectAllCrops(checked)
        );
        saveSelectedCropsButton.setText(getString(R.string.crop_save_selected_count, selected));
        saveSelectedCropsButton.setEnabled(selected > 0 && pendingBatchWrites == 0);
    }

    private static boolean isSelectableForSave(CapturedPlateItem item) {
        return item.saveState == CapturedPlateItem.SaveState.NOT_SAVED
                || item.saveState == CapturedPlateItem.SaveState.ERROR;
    }

    private void applyCropLimitSetting(String setting) {
        cropLimitSetting = CropCapacityPolicy.normalizeSetting(setting);
        uiPreferences.edit().putString("crop_limit", cropLimitSetting).apply();
        resolvedCropLimit = CropCapacityPolicy.resolve(
                cropLimitSetting,
                Runtime.getRuntime().maxMemory(),
                deviceProfile.lowRamDevice
        );
        metricsCollector.setCropCapacity(resolvedCropLimit);
        enforceCropLimit();
        renderCapturedCrops();
        liveStatus.setText(getString(R.string.crop_limit_changed, resolvedCropLimit));
    }

    private void clearCapturedCrops() {
        collectionActive = false;
        for (CapturedPlateItem item : capturedCrops) item.recycle();
        capturedCrops.clear();
        lastCaptureByTrack.clear();
        collectionSessionId = "";
        collectionSequence = 0;
        metricsCollector.clearCropSession();
        renderCapturedCrops();
    }

    private void saveSelectedCrops() {
        List<CapturedPlateItem> selected = new ArrayList<>();
        for (CapturedPlateItem item : capturedCrops) {
            if (item.selectedForSave && isSelectableForSave(item)) selected.add(item);
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.crop_save_none_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (captureDirectoryUri == null) {
            pendingBatchDirectorySelection = true;
            captureDirectoryPicker.launch(null);
            return;
        }
        pendingBatchWrites = selected.size();
        successfulBatchWrites = 0;
        failedBatchWrites = 0;
        for (CapturedPlateItem item : selected) saveCapturedCrop(item);
        updateSelectionControls();
    }

    private void saveCapturedCrop(CapturedPlateItem item) {
        if (item.saveState == CapturedPlateItem.SaveState.SAVING
                || item.saveState == CapturedPlateItem.SaveState.SAVED) return;
        final String report;
        try {
            String baseReport = item.miniReportJson.isEmpty()
                    ? CropMiniReport.create(
                            item,
                            collectionSessionStartedElapsedNanos,
                            deviceProfile,
                            modelRegistry,
                            autoTuneManager,
                            recognitionProfile.wireName(),
                            analysisResolutionProfile.wireName(),
                            vehicleCascadeEnabled,
                            experimentModeEnabled,
                            experimentRoiBudgetPolicy,
                            effectiveRoiBudgetPolicy()
                    )
                    : item.miniReportJson;
            report = CropMiniReport.refreshHumanVerification(baseReport, item);
            item.miniReportJson = report;
        } catch (Exception error) {
            item.saveState = CapturedPlateItem.SaveState.ERROR;
            renderCapturedCrops();
            completeBatchWrite(false);
            return;
        }
        Bitmap bitmap = item.bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (bitmap == null) {
            item.saveState = CapturedPlateItem.SaveState.ERROR;
            renderCapturedCrops();
            completeBatchWrite(false);
            return;
        }
        item.saveState = CapturedPlateItem.SaveState.SAVING;
        renderCapturedCrops();
        Uri directory = captureDirectoryUri;
        backgroundExecutor.execute(() -> {
            Uri imageUri = null;
            Uri reportUri = null;
            try {
                String baseName = captureFileBaseName(item);
                imageUri = createDocument(directory, "image/jpeg", baseName + ".jpg");
                try (OutputStream output = getContentResolver().openOutputStream(imageUri, "wt")) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                        throw new IllegalStateException("Nie udało się zapisać obrazu JPEG");
                    }
                }
                reportUri = createDocument(directory, "application/json", baseName + ".json");
                try (OutputStream output = getContentResolver().openOutputStream(reportUri, "wt")) {
                    if (output == null) throw new IllegalStateException("Nie można otworzyć raportu");
                    output.write(report.getBytes(StandardCharsets.UTF_8));
                }
                item.savedImageUri = imageUri;
                item.savedReportUri = reportUri;
                item.saveState = CapturedPlateItem.SaveState.SAVED;
                item.selectedForSave = false;
                metricsCollector.markCropPersisted(
                        item.captureId, imageUri.toString(), reportUri.toString()
                );
                runOnUiThread(() -> {
                    renderCapturedCrops();
                    completeBatchWrite(true);
                });
            } catch (Exception error) {
                item.saveState = CapturedPlateItem.SaveState.ERROR;
                deleteCreatedDocument(reportUri);
                deleteCreatedDocument(imageUri);
                AppLog.error(this, LOG_TAG, "Błąd trwałego zapisu cropu: " + error.getMessage(), error);
                runOnUiThread(() -> {
                    renderCapturedCrops();
                    completeBatchWrite(false);
                });
            } finally {
                bitmap.recycle();
            }
        });
    }

    private void completeBatchWrite(boolean success) {
        if (success) successfulBatchWrites++;
        else failedBatchWrites++;
        pendingBatchWrites = Math.max(0, pendingBatchWrites - 1);
        updateSelectionControls();
        if (pendingBatchWrites > 0) return;
        if (failedBatchWrites == 0) {
            Toast.makeText(
                    this,
                    getString(R.string.capture_batch_save_success, successfulBatchWrites),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    getString(
                            R.string.capture_batch_save_partial,
                            successfulBatchWrites,
                            failedBatchWrites
                    ),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void deleteCreatedDocument(Uri uri) {
        if (uri == null) return;
        try {
            DocumentsContract.deleteDocument(getContentResolver(), uri);
        } catch (Exception ignored) {
            // Plik był tworzony wyłącznie przez bieżącą próbę; provider mógł już go usunąć.
        }
    }

    private Uri createDocument(Uri treeUri, String mimeType, String name) throws Exception {
        ContentResolver resolver = getContentResolver();
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        );
        Uri created = DocumentsContract.createDocument(resolver, parent, mimeType, name);
        if (created == null) throw new IllegalStateException("Dostawca plików odrzucił zapis");
        return created;
    }

    private String captureFileBaseName(CapturedPlateItem item) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT)
                .format(new Date(item.capturedAtMillis));
        String text = item.text.replaceAll("[^A-Za-z0-9_-]", "");
        if (text.isEmpty()) text = "nieodczytana";
        return timestamp + "_" + text + "_" + item.captureId.substring(
                Math.max(0, item.captureId.length() - 5)
        );
    }

    private void scheduleMissingAutotuning() {
        backgroundExecutor.execute(() -> {
            boolean changed = false;
            String lastMessage = "";
            for (com.example.alpr_v1.model.ModelRole role : com.example.alpr_v1.model.ModelRole.values()) {
                InstalledModel model = modelRegistry.getActive(role);
                if (model == null || autoTuneManager.hasProfile(model)) continue;
                try {
                    AutoTuneResult result = autoTuneManager.tune(model);
                    changed = true;
                    lastMessage = result.chosenProfile.gpu
                            ? "Autotuning zakończony: LiteRT/GPU"
                            : "Autotuning zakończony: " + result.chosenProfile.runtime.wireName()
                                    + "/CPU, " + result.chosenProfile.cpuThreads + " wątki";
                } catch (Exception error) {
                    lastMessage = error.getMessage();
                    AppLog.error(
                            this,
                            LOG_TAG,
                            "Błąd autotuningu modelu " + model.manifest().modelId() + ": "
                                    + error.getMessage(),
                            error
                    );
                }
            }
            if (changed) pipeline.invalidateModels();
            if (!lastMessage.isEmpty()) {
                final String message = lastMessage;
                runOnUiThread(() -> {
                    liveStatus.setText(message);
                    refreshPersistentLog();
                });
            }
        });
    }

    private void showExportOptions() {
        if (exportInProgress) {
            Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] options = new CharSequence[]{
                getString(R.string.export_research_session),
                getString(R.string.export_thesis_bundle),
                getString(R.string.export_legacy_report)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_choose_title)
                .setItems(options, (dialog, which) -> {
                    ResearchArchive.Kind kind = which == 0
                            ? ResearchArchive.Kind.RESEARCH_SESSION
                            : which == 1
                            ? ResearchArchive.Kind.THESIS_BUNDLE
                            : ResearchArchive.Kind.LEGACY_REPORT;
                    requestExportDestination(kind);
                })
                .setNegativeButton(R.string.menu_close, null)
                .show();
    }

    private void requestExportDestination(ResearchArchive.Kind kind) {
        pendingExportKind = kind;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String name = kind == ResearchArchive.Kind.RESEARCH_SESSION
                ? "alpr_session_" + timestamp + ".alprsession"
                : kind == ResearchArchive.Kind.THESIS_BUNDLE
                ? "alpr_thesis_" + timestamp + ".zip"
                : "alpr_benchmark_report_" + timestamp + ".zip";
        reportDestination.launch(name);
    }

    private void writeResearchExport(Uri destination, ResearchArchive.Kind kind) {
        exportInProgress = true;
        if (collectionActive) {
            collectionActive = false;
            metricsCollector.setCropCollectionActive(false);
        }
        List<CapturedPlateItem> cropSnapshot = new ArrayList<>(capturedCrops);
        for (CapturedPlateItem item : cropSnapshot) item.exportProtected = true;
        renderCapturedCrops();
        setBusy(true, getString(R.string.export_creating));
        /*
         * Zamrażamy dokładnie tę ExperimentSession,
         * która ma trafić do eksportu.
         *
         * Nie przekazujemy do wątku eksportującego żywego obiektu
         * ExperimentSession, ponieważ użytkownik mógłby w międzyczasie
         * rozpocząć kolejny przebieg.
         */
        final ExperimentSession.Snapshot experimentSnapshot =
                experimentSession.snapshot();
        backgroundExecutor.execute(() -> {
            try {
                modelRegistry.reload();
                String json = metricsCollector.createJsonReport(
                        DeviceProfile.capture(this),
                        modelRegistry,
                        autoTuneManager,
                        experimentSnapshot
                );
                String csv = metricsCollector.createCsvReport();
                String log = AppLog.contents(this);
                try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) {
                        throw new IllegalStateException("Nie można otworzyć pliku docelowego");
                    }
                    if (kind == ResearchArchive.Kind.RESEARCH_SESSION) {
                        ResearchArchive.writeResearchSession(
                                output, json, csv, log, cropSnapshot, modelRegistry
                        );
                    } else if (kind == ResearchArchive.Kind.THESIS_BUNDLE) {
                        ResearchArchive.writeThesisBundle(output, json, csv, cropSnapshot);
                    } else {
                        output.write(ReportArchive.create(json, csv, log));
                    }
                }
                AppLog.info(this, LOG_TAG, "Zapisano eksport " + kind.name());
                runOnUiThread(() -> finishResearchExport(
                        cropSnapshot,
                        getString(R.string.export_saved),
                        null
                ));
            } catch (Exception error) {
                AppLog.error(this, LOG_TAG, "Błąd eksportu badawczego: " + error.getMessage(), error);
                runOnUiThread(() -> finishResearchExport(
                        cropSnapshot,
                        getString(R.string.export_failed, error.getMessage()),
                        error
                ));
            }
        });
    }

    private void finishResearchExport(
            List<CapturedPlateItem> cropSnapshot,
            String message,
            Exception error
    ) {
        for (CapturedPlateItem item : cropSnapshot) item.exportProtected = false;
        exportInProgress = false;
        enforceCropLimit();
        renderCapturedCrops();
        setBusy(false, message);
        Toast.makeText(
                this,
                message,
                error == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
        ).show();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        liveStatus.setText(message);
    }

    private void recordInfo(String message) {
        AppLog.info(this, LOG_TAG, message);
        refreshPersistentLog();
    }

    private void recordWarning(String message) {
        AppLog.warning(this, LOG_TAG, message);
        refreshPersistentLog();
    }

    private void recordError(String message, Throwable error) {
        AppLog.error(this, LOG_TAG, message, error);
        refreshPersistentLog();
    }

    private void refreshPersistentLog() {
        // Dziennik jest prezentowany w osobnej DiagnosticsActivity.
    }

    private void refreshPersistentLogThrottled() {
        // Brak kosztownego odświeżania widoku diagnostycznego nad kamerą.
    }

    @Override
    protected void onResume() {
        super.onResume();

        applySettingsRevision();

        /*
         * Sensor ruchu pracuje tylko wtedy,
         * gdy działa analiza.
         */
        if (cameraStarted && cameraMotionMonitor != null) {
            cameraMotionMonitor.start();
        }
    }

    private void applySettingsRevision() {
        if (uiPreferences == null || pipeline == null) return;

        int revision = uiPreferences.getInt(
                SettingsActivity.KEY_REVISION,
                0
        );

        if (revision == knownSettingsRevision) return;

        knownSettingsRevision = revision;


        /*
         * NORMALNA KONFIGURACJA:
         * profil rozpoznawania.
         */
        RecognitionProfile requestedRecognition =
                RecognitionProfile.fromWireName(
                        uiPreferences.getString(
                                "recognition_profile",
                                RecognitionProfile.BALANCED.wireName()
                        )
                );

        if (requestedRecognition != recognitionProfile) {
            applyRecognitionProfile(
                    requestedRecognition
            );
        }


        /*
         * NORMALNA KONFIGURACJA:
         * rozdzielczość analizy.
         */
        AnalysisResolutionProfile requestedResolution =
                AnalysisResolutionProfile.fromWireName(
                        uiPreferences.getString(
                                "analysis_resolution_profile",
                                AnalysisResolutionProfile.AUTO.wireName()
                        )
                );

        if (requestedResolution != analysisResolutionProfile) {
            applyAnalysisResolutionProfile(
                    requestedResolution
            );
        }


        /*
         * NORMALNA KONFIGURACJA:
         * kaskada MP.
         *
         * Eksperyment NIE modyfikuje tej wartości.
         */
        boolean requestedCascade =
                uiPreferences.getBoolean(
                        "vehicle_cascade_enabled",
                        false
                );

        if (requestedCascade != vehicleCascadeEnabled) {
            setVehicleCascadeEnabled(
                    requestedCascade
            );
        }


        /*
         * ODDZIELNA WARSTWA EKSPERYMENTALNA.
         */
        boolean requestedExperimentMode =
                uiPreferences.getBoolean(
                        SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,
                        false
                );

        RoiBudgetPolicy requestedExperimentRoi =
                RoiBudgetPolicy.fromWireName(
                        uiPreferences.getString(
                                SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,
                                RoiBudgetPolicy.TWO_ROI.wireName()
                        )
                );
        TimerConfig requestedExperimentTimer =
                readExperimentTimerConfig();

        if (requestedExperimentMode != experimentModeEnabled
                || requestedExperimentRoi != experimentRoiBudgetPolicy) {

            applyExperimentConfiguration(
                    requestedExperimentMode,
                    requestedExperimentRoi
            );
        }

        /*
         * Nowa konfiguracja timera obowiązuje dopiero
         * przy kolejnym START.
         *
         * Nie restartujemy i nie przedłużamy timera
         * aktywnej ExperimentSession.
         */
        experimentTimerConfig =
                requestedExperimentTimer;


        /*
         * NORMALNA KONFIGURACJA:
         * limit cropów.
         */
        String requestedLimit =
                CropCapacityPolicy.normalizeSetting(
                        uiPreferences.getString(
                                "crop_limit",
                                CropCapacityPolicy.AUTO
                        )
                );

        if (!requestedLimit.equals(cropLimitSetting)) {
            applyCropLimitSetting(
                    requestedLimit
            );
        }


        /*
         * NORMALNA KONFIGURACJA:
         * katalog zapisu.
         */
        String directory =
                uiPreferences.getString(
                        "capture_directory_uri",
                        ""
                );

        try {
            captureDirectoryUri =
                    directory.isEmpty()
                            ? null
                            : Uri.parse(directory);

        } catch (RuntimeException ignored) {
            captureDirectoryUri = null;
        }


        /*
         * Ustawienia mogły zmienić modele lub warianty,
         * dlatego odświeżamy rejestr i pipeline.
         */
        modelRegistry.reload();
        pipeline.invalidateModels();

        lastCaptureByTrack.clear();

        scheduleMissingAutotuning();
    }

    @Override
    protected void onPause() {
        retainCaptureGalleryState();
        if (cameraMotionMonitor != null) cameraMotionMonitor.stop();
        super.onPause();
    }

    private void retainCaptureGalleryState() {
        if (captureGalleryState == null) return;
        captureGalleryState.retainSession(
                collectionActive,
                collectionSessionId,
                collectionSessionStartedElapsedNanos,
                collectionSequence
        );
    }

    @Override
    protected void onDestroy() {
        AppLog.info(
                this,
                LOG_TAG,
                "Zamykanie aplikacji"
        );

        /*
         * CameraController.close() zatrzymuje CameraX,
         * czeka na zwolnienie pipeline'u na wątku analizatora
         * i dopiero potem zamyka executor kamery.
         */
        if (cameraController != null) {
            cameraController.close(
                    pipeline == null
                            ? null
                            : pipeline::close
            );
        } else if (pipeline != null) {
            pipeline.close();
        }

        backgroundExecutor.shutdownNow();
        cancelExperimentTimer();

        super.onDestroy();

        /*
         * Zwykły onDestroy, np. przy odtworzeniu Activity,
         * NIE może kończyć procesu.
         *
         * Proces kończymy tylko po świadomym wybraniu
         * pozycji "Zamknij aplikację".
         */
        if (explicitExitRequested) {
            android.os.Process.killProcess(
                    android.os.Process.myPid()
            );
        }
    }
    private void applyExperimentConfiguration(
            boolean enabled,
            RoiBudgetPolicy roiPolicy
    ) {
        experimentModeEnabled = enabled;
        experimentRoiBudgetPolicy =
                roiPolicy == null
                        ? RoiBudgetPolicy.TWO_ROI
                        : roiPolicy;

        /*
         * Zapisujemy wyłącznie stan eksperymentu.
         * vehicle_cascade_enabled pozostaje nietknięte.
         */
        uiPreferences.edit()
                .putBoolean(
                        SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,
                        experimentModeEnabled
                )
                .putString(
                        SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,
                        experimentRoiBudgetPolicy.wireName()
                )
                .apply();

        pipeline.setExperimentConfiguration(
                experimentModeEnabled,
                experimentRoiBudgetPolicy
        );

        overlayTracker.reset();
        pipeline.resetTracking();
        lastCaptureByTrack.clear();

        if (experimentModeEnabled) {
            if (experimentRoiBudgetPolicy.usesVehicleCascade()
                    && modelRegistry.getActive(
                    com.example.alpr_v1.model.ModelRole.VEHICLE
            ) == null) {

                liveStatus.setText(
                        R.string.vehicle_cascade_missing_model
                );
                return;
            }

            liveStatus.setText(
                    getString(
                            R.string.experiment_mode_enabled_status,
                            roiBudgetPolicyLabel(
                                    experimentRoiBudgetPolicy
                            )
                    )
            );

        } else {
            liveStatus.setText(
                    R.string.experiment_mode_disabled_status
            );
        }
    }
    private String roiBudgetPolicyLabel(
            RoiBudgetPolicy policy
    ) {
        switch (policy) {
            case ONE_ROI:
                return getString(
                        R.string.roi_budget_r1
                );

            case TWO_ROI:
                return getString(
                        R.string.roi_budget_r2
                );

            case FULL_FRAME:
            default:
                return getString(
                        R.string.roi_budget_r0
                );
        }
    }
}
