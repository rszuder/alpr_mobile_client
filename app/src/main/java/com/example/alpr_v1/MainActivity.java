package com.example.alpr_v1;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AutoTuneResult;
import com.example.alpr_v1.camera.CameraController;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.metrics.ReportArchive;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelPackageImporter;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.RecognitionStabilizer;
import com.example.alpr_v1.ui.DetectionOverlayView;
import com.google.android.material.button.MaterialButton;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong lastUiUpdateNanos = new AtomicLong();
    private final RecognitionStabilizer recognitionStabilizer = new RecognitionStabilizer(2);

    private PreviewView previewView;
    private DetectionOverlayView overlayView;
    private TextView liveStatus;
    private TextView modelStatus;
    private TextView recognitionResult;
    private ProgressBar progress;

    private ModelRegistry modelRegistry;
    private ModelPackageImporter packageImporter;
    private AutoTuneManager autoTuneManager;
    private MetricsCollector metricsCollector;
    private DeviceProfile deviceProfile;
    private AlprPipeline pipeline;
    private CameraController cameraController;
    private boolean cameraStarted;
    private volatile byte[] pendingReport;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) startCamera();
                else liveStatus.setText(R.string.camera_permission_required);
            }
    );

    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importModel(uri);
            }
    );

    private final ActivityResultLauncher<String> reportDestination = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                if (uri != null && pendingReport != null) writeReport(uri, pendingReport);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        bindViews();
        applySystemInsets();

        modelRegistry = new ModelRegistry(this);
        packageImporter = new ModelPackageImporter(this, modelRegistry.modelsRoot());
        autoTuneManager = new AutoTuneManager(this);
        metricsCollector = new MetricsCollector();
        deviceProfile = DeviceProfile.capture(this);
        pipeline = new AlprPipeline(this, modelRegistry, metricsCollector, autoTuneManager);
        cameraController = new CameraController(this, this, previewView);

        MaterialButton importButton = findViewById(R.id.import_model_button);
        MaterialButton exportButton = findViewById(R.id.export_report_button);
        importButton.setOnClickListener(view -> modelPicker.launch(new String[]{
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed"
        }));
        exportButton.setOnClickListener(view -> prepareReport());

        refreshModelStatus();
        ensureCameraPermission();
        scheduleMissingAutotuning();
    }

    private void bindViews() {
        previewView = findViewById(R.id.camera_preview);
        overlayView = findViewById(R.id.detection_overlay);
        liveStatus = findViewById(R.id.live_status);
        modelStatus = findViewById(R.id.model_status);
        recognitionResult = findViewById(R.id.recognition_result);
        progress = findViewById(R.id.progress);
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (cameraStarted) return;
        cameraStarted = true;
        liveStatus.setText(R.string.camera_starting);
        cameraController.start(
                image -> {
                    PipelineResult result = pipeline.process(image);
                    if (result == null) return;
                    long now = System.nanoTime();
                    long previous = lastUiUpdateNanos.get();
                    if (now - previous >= 200_000_000L && lastUiUpdateNanos.compareAndSet(previous, now)) {
                        runOnUiThread(() -> presentResult(result));
                    }
                },
                error -> runOnUiThread(() -> {
                    cameraStarted = false;
                    liveStatus.setText(getString(R.string.camera_error, error.getMessage()));
                }),
                chooseAnalysisSize()
        );
    }

    private Size chooseAnalysisSize() {
        boolean constrained = deviceProfile.lowRamDevice
                || deviceProfile.totalMemoryBytes < 4L * 1024L * 1024L * 1024L;
        return constrained ? new Size(640, 480) : new Size(1280, 720);
    }

    private void presentResult(PipelineResult result) {
        liveStatus.setText(result.message);
        overlayView.setItems(result.overlayItems, result.sourceWidth, result.sourceHeight);
        RecognitionStabilizer.StableResult stable = recognitionStabilizer.accept(
                result.recognizedText, result.confidence
        );
        if (stable != null) {
            recognitionResult.setText(stable.text);
        }
    }

    private void importModel(Uri uri) {
        setBusy(true, "Sprawdzanie pakietu modelu…");
        backgroundExecutor.execute(() -> {
            try {
                InstalledModel installedModel = packageImporter.importPackage(uri);
                String tuneMessage;
                if (ModelRegistry.isExecutable(installedModel)) {
                    modelRegistry.activate(installedModel);
                    try {
                        tuneMessage = tuneIfPossible(installedModel);
                    } catch (Exception tuningError) {
                        tuneMessage = "; " + tuningError.getMessage();
                    }
                } else {
                    modelRegistry.reload();
                    tuneMessage = "; zapisano wariant NCNN, adapter JNI nie jest jeszcze dostępny";
                }
                final String completedTuneMessage = tuneMessage;
                recognitionStabilizer.reset();
                pipeline.invalidateModels();
                runOnUiThread(() -> {
                    refreshModelStatus();
                    setBusy(false, "Zaimportowano " + installedModel.manifest().name() + completedTuneMessage);
                    Toast.makeText(this, "Model został zweryfikowany i zapisany", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Import odrzucony: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String tuneIfPossible(InstalledModel model) {
        if (autoTuneManager.hasProfile(model)) return "; profil urządzenia jest aktualny";
        boolean hasAvailableRuntime = false;
        for (ModelVariant variant : model.manifest().variants()) {
            if (com.example.alpr_v1.inference.RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                hasAvailableRuntime = true;
                break;
            }
        }
        if (!hasAvailableRuntime) return "; brak dostępnego backendu dla autotuningu";
        AutoTuneResult result = autoTuneManager.tune(model);
        if (result.chosenProfile.gpu) return "; autotuning: LiteRT/GPU";
        return String.format(
                Locale.ROOT,
                "; autotuning: %s/CPU, %d wątki",
                result.chosenProfile.runtime.wireName(),
                result.chosenProfile.cpuThreads
        );
    }

    private void refreshModelStatus() {
        modelRegistry.reload();
        modelStatus.setText(modelRegistry.summary());
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
                }
            }
            if (changed) pipeline.invalidateModels();
            if (!lastMessage.isEmpty()) {
                final String message = lastMessage;
                runOnUiThread(() -> liveStatus.setText(message));
            }
        });
    }

    private void prepareReport() {
        setBusy(true, "Tworzenie raportu sesji…");
        backgroundExecutor.execute(() -> {
            try {
                String json = metricsCollector.createJsonReport(
                        DeviceProfile.capture(this),
                        modelRegistry,
                        autoTuneManager.exportProfiles()
                );
                String csv = metricsCollector.createCsvReport();
                pendingReport = ReportArchive.create(json, csv);
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
                runOnUiThread(() -> {
                    setBusy(false, "Raport gotowy do zapisania");
                    reportDestination.launch("alpr_report_" + timestamp + ".zip");
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false, "Nie udało się utworzyć raportu: " + e.getMessage()));
            }
        });
    }

    private void writeReport(Uri destination, byte[] report) {
        setBusy(true, "Zapisywanie raportu…");
        backgroundExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new IllegalStateException("Nie można otworzyć pliku docelowego");
                output.write(report);
                output.flush();
                runOnUiThread(() -> setBusy(false, "Raport został zapisany"));
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false, "Błąd zapisu raportu: " + e.getMessage()));
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        liveStatus.setText(message);
    }

    @Override
    protected void onDestroy() {
        if (cameraController != null) cameraController.close(pipeline == null ? null : pipeline::close);
        else if (pipeline != null) pipeline.close();
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}
