package com.example.alpr_v1;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Size;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;


import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
//---------------------------------------------------------------

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AutoTuneResult;
import com.example.alpr_v1.camera.CameraController;
import com.example.alpr_v1.camera.LumaFrame;
import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.camera.AutoZoomRecognitionMemory;
//do usuniecia po migracji
import com.example.alpr_v1.camera.AnalysisResolutionProfile;
//--------------------------------------------------------------
import com.example.alpr_v1.camera.CameraResolutionCatalog;
import com.example.alpr_v1.camera.CameraResolutionSelection;
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
import com.example.alpr_v1.metrics.ImageDifficultyMetrics;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.TargetSnapshot;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneAnchorRefreshPolicy;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.pipeline.TargetStateMachine;
import com.example.alpr_v1.ui.CameraMotionOverlayTracker;
import com.example.alpr_v1.ui.DetectionOverlayView;
import com.example.alpr_v1.ui.LivePresentationController;
import com.example.alpr_v1.ui.PlateCaptureAdapter;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.example.alpr_v1.experiment.ExperimentSession;
import com.example.alpr_v1.experiment.ExperimentIdentity;
import com.example.alpr_v1.experiment.TimerConfig;
import com.example.alpr_v1.experiment.ThermalConfig;
import com.example.alpr_v1.experiment.ThermalMonitor;
import com.example.alpr_v1.vision.SceneChangeDetector;
import com.example.alpr_v1.vision.CameraImageConverter;
import com.example.alpr_v1.tracking.PreviewPlateTracker;
import com.example.alpr_v1.tracking.PreviewTrackingFrame;
import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.vision.SceneAnchorGuard;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "MainActivity";
    private static final String KEY_CAMERA_PERMISSION_REQUESTED =
            "camera_permission_requested";

    private static final String KEY_LAST_EXPERIMENT_TIMER_SECONDS =
            "last_experiment_timer_seconds";
    private static final String KEY_LAST_EXPERIMENT_THERMAL_TENTHS =
            "last_experiment_thermal_tenths";
    private static final String KEY_AUTO_ZOOM_ENABLED =
            "auto_zoom_enabled";

    private static final long THERMAL_POLL_MS =
            1000L;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService previewTrackingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pipelineInferenceExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean previewFrameInFlight = new AtomicBoolean();
    private final AtomicBoolean pipelineFrameInFlight = new AtomicBoolean();
    private final AtomicLong lastUiUpdateNanos = new AtomicLong();
    /*
     * Lekki monitor tego, co użytkownik faktycznie widzi
     * w PreviewView.
     *
     * Jest niezależny od ciężkiego pipeline'u MP/MT/MZ.
     */
    private static final long PREVIEW_SCENE_POLL_MS =
            50L;
    private static final long PRE_ZOOM_OVERLAY_HOLD_MS =
            120L;


    private final AtomicLong uiSceneGeneration =
            new AtomicLong();
    /*
     * Zoom zmienia geometrię klatki, ale nie jej logiczną scenę. Osobna
     * generacja odrzuca wyniki rozpoczęte dla poprzedniego poziomu zoomu bez
     * zerowania tracków i stabilizacji tej samej sceny.
     */
    private final AtomicLong uiCameraTransformGeneration =
            new AtomicLong();


    private final Handler previewSceneHandler =
            new Handler(
                    Looper.getMainLooper()
            );


    private final SceneChangeDetector previewSceneDetector =
            new SceneChangeDetector();

    private final SceneAnchorGuard previewSceneAnchorGuard =
            new SceneAnchorGuard();
    private final SceneAnchorGuard autoZoomPreZoomSceneAnchorGuard =
            new SceneAnchorGuard();
    private final SceneAnchorGuard autoZoomZoomedSceneAnchorGuard =
            new SceneAnchorGuard();


    /*
     * Po otrzymaniu świeżego wyniku MT następna klatka
     * PreviewView stanie się referencją dla trackera.
     */
    private volatile boolean previewSceneAnchorPending;
    private long previewSceneAnchorLockRevision = -1L;
    private com.example.alpr_v1.continuity.SceneContinuityState
            lastRenderedContinuityState =
            com.example.alpr_v1.continuity.SceneContinuityState.STABLE;

    private List<OverlayItem> latestDiagnosticOverlayItems =
            java.util.Collections.emptyList();

    /*
     * Pozycje tablic pochodzące z ostatniego
     * rzeczywistego wyniku MT.
     *
     * Służą jako geometria referencyjna do przesuwania
     * VEHICLE i VEHICLE_ROI razem ze śledzoną tablicą.
     */
    private List<OverlayItem> latestPipelinePlateItems =
            java.util.Collections.emptyList();
    private List<OverlayItem> autoZoomBaseMemoryOverlayItems =
            java.util.Collections.emptyList();
    private int latestOverlaySourceWidth;
    private int latestOverlaySourceHeight;


    private volatile boolean previewSceneMonitorRunning;
    private volatile long lastDirectLumaFrameNanos;
    private volatile boolean directLumaTrackingUnavailable;


    private final Runnable previewSceneMonitorRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (!previewSceneMonitorRunning
                            || !cameraStarted
                            || previewView == null) {

                        return;
                    }

                    if (!needsPreviewSceneSampling()) {
                        previewSceneHandler.postDelayed(this, previewScenePollDelay());
                        return;
                    }

                    if (previewFrameInFlight.compareAndSet(false, true)) {
                        Bitmap previewBitmap = null;
                        try {
                            previewBitmap = previewView.getBitmap();
                            if (previewBitmap != null && !previewBitmap.isRecycled()) {
                                final Bitmap ownedBitmap = previewBitmap;
                                final long sceneGeneration = uiSceneGeneration.get();
                                final long transformGeneration =
                                        uiCameraTransformGeneration.get();
                                final ContinuityStamp continuityStamp =
                                        currentContinuityStamp(System.nanoTime());
                                previewBitmap = null;
                                previewTrackingExecutor.execute(() ->
                                        processPreviewTrackingFrame(
                                                ownedBitmap,
                                                sceneGeneration,
                                                transformGeneration,
                                                continuityStamp
                                        )
                                );
                            } else {
                                previewFrameInFlight.set(false);
                            }
                        } catch (RuntimeException ignored) {
                            previewFrameInFlight.set(false);
                        } finally {
                            recycleBitmap(previewBitmap);
                        }
                    }


                    if (previewSceneMonitorRunning
                            && cameraStarted) {

                        previewSceneHandler.postDelayed(
                                this,
                                previewScenePollDelay()
                        );
                    }
                }
            };

    private void processPreviewTrackingFrame(
            Bitmap previewBitmap,
            long sceneGeneration,
            long transformGeneration,
            ContinuityStamp continuityStamp
    ) {
        boolean bitmapHandedToUi = false;
        try {
            if (!cameraStarted
                    || sceneGeneration != uiSceneGeneration.get()
                    || transformGeneration != uiCameraTransformGeneration.get()
                    || (pipeline != null
                    && !pipeline.isCurrentContinuityStamp(continuityStamp))) {
                return;
            }

            if (isAutoZoomHoldingMemory()) {
                boolean physicalMotion = cameraTransformInProgress
                        && autoZoomController.state()
                        == AutoZoomController.State.ZOOM_SETTLING
                        && cameraMotionMonitor != null
                        && cameraMotionMonitor.isMoving();
                previewSceneDetector.reset();
                previewSceneAnchorGuard.reset();
                previewPlateTracker.reset();
                if (physicalMotion) runOnUiThread(
                        this::invalidateAutoZoomForPhysicalCameraMotion
                );
                return;
            }

            if (autoZoomReturnValidationPending) {
                bitmapHandedToUi = true;
                runOnUiThread(() -> {
                    try {
                        if (cameraStarted
                                && sceneGeneration == uiSceneGeneration.get()
                                && transformGeneration
                                == uiCameraTransformGeneration.get()) {
                            validateReturnedAutoZoomScene(previewBitmap);
                        }
                    } finally {
                        recycleBitmap(previewBitmap);
                    }
                });
                return;
            }

            if (autoZoomZoomedAnchorPending
                    && autoZoomController.state()
                    == AutoZoomController.State.ZOOMED_RETRY) {
                autoZoomZoomedSceneAnchorGuard.anchor(previewBitmap);
                autoZoomZoomedAnchorPending = false;
                autoZoomZoomedAnchorValid = true;
            }

            if (previewSceneAnchorPending) {
                previewSceneAnchorGuard.anchor(previewBitmap);
                previewSceneAnchorPending = false;
            }

            SceneAnchorGuard.Result anchorResult =
                    previewSceneAnchorGuard.evaluate(previewBitmap);
            SceneChangeDetector.Result scene =
                    previewSceneDetector.update(previewBitmap);
            SceneAnchorGuard.Result zoomedAnchorResult =
                    autoZoomZoomedSceneAnchorGuard.evaluate(previewBitmap);
            PreviewTrackingFrame trackingFrame = directLumaTrackingFresh()
                    ? null
                    : previewPlateTracker.updateTracking(previewBitmap);
            List<OverlayItem> trackedItems = trackingFrame == null
                    ? null : trackingFrame.overlayItems;

            if (trackingFrame != null && pipeline != null) {
                pipeline.setTargetSnapshotIfCurrent(
                        targetStateMachine.onTrackingFrame(trackingFrame),
                        continuityStamp
                );
            }

            boolean zoomedRetry = autoZoomController.state()
                    == AutoZoomController.State.ZOOMED_RETRY;
            boolean targetStillTracked = zoomedRetry
                    && trackedItems != null
                    && !trackedItems.isEmpty()
                    && findAutoZoomTargetOverlay(trackedItems) != null;
            boolean changed = scene.sceneChanged
                    || anchorResult.changed
                    || (zoomedRetry && zoomedAnchorResult.changed);
            boolean shortMotionGrace = zoomedRetry
                    && cameraMotionMonitor != null
                    && cameraMotionMonitor.isMoving()
                    && autoZoomDynamicFrameGraceCount < 2;
            boolean trackingLost = trackingFrame != null && trackedItems.isEmpty();
            boolean trackingDegraded = trackingFrame != null
                    && targetStateMachine.snapshot().state
                    == TargetSnapshot.State.DEGRADED;
            float evidenceScore = Math.max(
                    scene.score,
                    Math.max(anchorResult.score, zoomedAnchorResult.score)
            );
            float evidenceFraction = Math.max(
                    scene.changedFraction,
                    Math.max(
                            anchorResult.changedFraction,
                            zoomedAnchorResult.changedFraction
                    )
            );
            refreshPipelineCameraMotionEvidence();
            SceneTransitionDecision continuityDecision = pipeline == null
                    ? null
                    : pipeline.onPreviewSceneEvidence(
                    continuityStamp.sourceTimestampNanos,
                    changed,
                    evidenceScore,
                    evidenceFraction,
                    scene.brightnessDelta,
                    Math.max(anchorResult.score, zoomedAnchorResult.score),
                    Math.max(
                            anchorResult.changedFraction,
                            zoomedAnchorResult.changedFraction
                    ),
                    trackingLost,
                    trackingDegraded
            );

            bitmapHandedToUi = true;
            runOnUiThread(() -> {
                try {
                    if (!cameraStarted
                            || sceneGeneration != uiSceneGeneration.get()
                            || transformGeneration
                            != uiCameraTransformGeneration.get()) {
                        return;
                    }
                    if (continuityDecision != null
                            && (continuityDecision.action
                            != SceneTransitionAction.NONE
                            || continuityDecision.nextState
                            != com.example.alpr_v1.continuity.SceneContinuityState.STABLE
                            || lastRenderedContinuityState
                            != com.example.alpr_v1.continuity.SceneContinuityState.STABLE)) {
                        renderPreviewContinuityDecision(
                                continuityDecision,
                                evidenceScore,
                                evidenceFraction,
                                trackedItems
                        );
                    } else if (changed && (targetStillTracked || shortMotionGrace
                            || continuityDecision != null
                            && continuityDecision.assessment.focusedTargetPreserved)) {
                        autoZoomDynamicFrameGraceCount = targetStillTracked
                                ? 0 : autoZoomDynamicFrameGraceCount + 1;
                        previewSceneDetector.reset();
                        previewSceneDetector.update(previewBitmap);
                        previewSceneAnchorGuard.anchor(previewBitmap);
                        autoZoomZoomedSceneAnchorGuard.anchor(previewBitmap);
                        if (targetStillTracked) {
                            presentTrackedPreviewOverlay(trackedItems);
                        }
                    } else if (changed) {
                        autoZoomDynamicFrameGraceCount = 0;
                        invalidateUiForPreviewSceneChange(
                                Math.max(scene.score, Math.max(
                                        anchorResult.score,
                                        zoomedAnchorResult.score
                                )),
                                Math.max(scene.changedFraction, Math.max(
                                        anchorResult.changedFraction,
                                        zoomedAnchorResult.changedFraction
                                )),
                                continuityDecision == null
                        );
                    } else {
                        autoZoomDynamicFrameGraceCount = 0;
                        if (trackedItems != null) {
                            if (trackedItems.isEmpty()) {
                                if (continuityDecision == null) {
                                    invalidateUiForLostPreviewTracking();
                                }
                            } else {
                                presentTrackedPreviewOverlay(trackedItems);
                            }
                        }
                    }
                } finally {
                    recycleBitmap(previewBitmap);
                }
            });
        } catch (RuntimeException ignored) {
            // PreviewView może zmienić powierzchnię podczas restartu kamery.
        } finally {
            previewFrameInFlight.set(false);
            if (!bitmapHandedToUi) recycleBitmap(previewBitmap);
        }
    }

    private static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private ContinuityStamp currentContinuityStamp(long sourceTimestampNanos) {
        if (pipeline == null) return ContinuityStamp.initial(sourceTimestampNanos);
        SceneContinuitySnapshot snapshot = pipeline.sceneContinuitySnapshot();
        return new ContinuityStamp(
                snapshot.sceneGeneration,
                snapshot.visualEpoch,
                snapshot.cameraTransformGeneration,
                sourceTimestampNanos
        );
    }

    private long previewScenePollDelay() {
        return directLumaTrackingFresh() ? 260L : PREVIEW_SCENE_POLL_MS;
    }

    private boolean directLumaTrackingFresh() {
        long lastFrame = lastDirectLumaFrameNanos;
        return !directLumaTrackingUnavailable
                && lastFrame > 0L
                && System.nanoTime() - lastFrame < 600_000_000L;
    }

    private void processDirectLumaFrame(LumaFrame frame) {
        if (frame == null || !cameraStarted || cameraTransformInProgress
                || isAutoZoomHoldingMemory() || autoZoomReturnValidationPending) return;
        final long sceneGeneration = uiSceneGeneration.get();
        final long transformGeneration = uiCameraTransformGeneration.get();
        final ContinuityStamp continuityStamp = currentContinuityStamp(
                frame.timestampNanos
        );
        lastDirectLumaFrameNanos = System.nanoTime();
        long trackingStartedNanos =
                android.os.SystemClock.elapsedRealtimeNanos();
        PreviewTrackingFrame trackingFrame = previewPlateTracker.updateLuma(
                frame.gray,
                frame.width,
                frame.height
        );
        long trackingElapsedNanos = Math.max(
                0L,
                android.os.SystemClock.elapsedRealtimeNanos()
                        - trackingStartedNanos
        );
        if (trackingFrame == null) return;
        if (pipeline != null
                && !pipeline.isCurrentContinuityStamp(continuityStamp)) return;
        float maximumQuality = 0f;
        int maximumInliers = 0;
        for (com.example.alpr_v1.tracking.TrackedPlate plate
                : trackingFrame.trackedPlates) {
            maximumQuality = Math.max(maximumQuality, plate.trackingQuality);
            maximumInliers = Math.max(maximumInliers, plate.trackerInliers);
        }
        android.util.Log.d(
                "ALPR_TRACK_TIMING",
                String.format(
                        Locale.ROOT,
                        "timestamp_ns=%d update_ms=%.3f tracks=%d quality=%.3f inliers=%d",
                        frame.timestampNanos,
                        trackingElapsedNanos / 1_000_000.0,
                        trackingFrame.trackedPlates.size(),
                        maximumQuality,
                        maximumInliers
                )
        );
        if (pipeline != null) {
            pipeline.setTargetSnapshotIfCurrent(
                    targetStateMachine.onTrackingFrame(trackingFrame),
                    continuityStamp
            );
        }
        List<OverlayItem> trackedItems = trackingFrame.overlayItems;
        boolean trackingLost = trackedItems.isEmpty();
        boolean trackingDegraded = targetStateMachine.snapshot().state
                == TargetSnapshot.State.DEGRADED;
        refreshPipelineCameraMotionEvidence();
        SceneTransitionDecision lumaContinuityDecision = pipeline == null
                ? null
                : pipeline.onPreviewSceneEvidence(
                frame.timestampNanos,
                false,
                0f,
                0f,
                0f,
                0f,
                0f,
                trackingLost,
                trackingDegraded
        );
        runOnUiThread(() -> {
            if (!cameraStarted
                    || sceneGeneration != uiSceneGeneration.get()
                    || transformGeneration != uiCameraTransformGeneration.get()
                    || cameraTransformInProgress) return;
            if (lumaContinuityDecision != null
                    && (lumaContinuityDecision.action
                    != SceneTransitionAction.NONE
                    || lumaContinuityDecision.nextState
                    != com.example.alpr_v1.continuity.SceneContinuityState.STABLE
                    || lastRenderedContinuityState
                    != com.example.alpr_v1.continuity.SceneContinuityState.STABLE)) {
                renderPreviewContinuityDecision(
                        lumaContinuityDecision,
                        0f,
                        0f,
                        trackedItems
                );
            } else if (trackedItems.isEmpty()) {
                invalidateUiForLostPreviewTracking();
            } else {
                presentTrackedPreviewOverlay(trackedItems);
            }
        });
    }
    private final CameraMotionOverlayTracker overlayTracker = new CameraMotionOverlayTracker();
    /*
     * Tracker działający na lekkich klatkach PreviewView
     * pomiędzy kolejnymi wywołaniami MT.
     */
    private final PreviewPlateTracker previewPlateTracker =
            new PreviewPlateTracker();
    private final TargetStateMachine targetStateMachine =
            new TargetStateMachine();
    private final ExperimentSession experimentSession =
            new ExperimentSession();
    private final Set<Long> telemetryActiveTrackIds = new HashSet<>();
    private final Set<Long> telemetryConfirmedTrackIds = new HashSet<>();
    private final Map<Long, String> telemetryConsensusByTrack = new HashMap<>();

    private final Handler experimentTimerHandler =
            new Handler(Looper.getMainLooper());

    private final Handler thermalHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private final Handler autoZoomHandler =
            new Handler(Looper.getMainLooper());

    private final AutoZoomController autoZoomController =
            new AutoZoomController();

    private volatile boolean cameraTransformInProgress;
    private boolean autoZoomMemoryVisible;
    private float currentCameraZoomRatio = 1f;
    private float cameraTransformStartZoomRatio = 1f;
    private float autoZoomTargetSceneX = 0.5f;
    private float autoZoomTargetSceneY = 0.5f;
    private String autoZoomBestText = "";
    private double autoZoomBestConfidence;
    private boolean abortAutoZoomAfterTransform;
    private boolean resetAutoZoomSessionAfterReturn;
    private boolean autoZoomPreZoomAnchorValid;
    private volatile boolean autoZoomZoomedAnchorPending;
    private volatile boolean autoZoomZoomedAnchorValid;
    private volatile boolean autoZoomReturnValidationPending;
    private volatile int autoZoomDynamicFrameGraceCount;
    private Runnable pendingAutoZoomStartRunnable;

    private boolean thermalUiMonitorRunning;

    private long experimentTimerDeadlineElapsedMillis = -1L;

    private final Runnable experimentTimerUiRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!experimentSession.isRunning()
                            || experimentTimerDeadlineElapsedMillis < 0L) {
                        return;
                    }

                    updateExperimentTimerButton();

                    experimentTimerHandler.postDelayed(
                            this,
                            250L
                    );
                }
            };

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
    private TextView liveStatusSecondary;
    private TextView liveHud;
    private LinearLayout liveHudRow;
    private View liveStatusStrip;
    private View liveStatusDot;
    private TextView liveEvent;
    private TextView recognitionHint;
    private View confirmedResultTray;
    private TextView confirmedResultText;
    private TextView confirmedResultConfidence;
    private TextView confirmedResultMeta;
    private LivePresentationController livePresentation;
    private MaterialButton collectionToggle;
    private MaterialButton galleryOpenButton;
    private MaterialButton analysisStartButton;
    private MaterialButton exportReportButton;


    /*
     * Galeria nie jest już częścią activity_main.
     * Jej widoki istnieją tylko wtedy, gdy Bottom Sheet jest otwarty.
     */
    private BottomSheetDialog galleryBottomSheet;

    private RecyclerView galleryResultsList;

    private TextView galleryResultsEmpty;

    private TextView gallerySheetCount;

    private TextView galleryCollectionStats;

    private MaterialCheckBox gallerySelectAllCropsToggle;

    private MaterialButton gallerySaveSelectedCropsButton;
    private MaterialButton experimentTimerButton;
    private MaterialButton experimentThermalButton;
    private MaterialButton autoZoomButton;
    private View autoZoomGlow;
    private View autoZoomControl;
    private ImageView autoZoomTarget;
    private ObjectAnimator autoZoomGlowAnimator;
    private ObjectAnimator autoZoomTargetAnimator;
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
    /*
     * true:
     * HUD czeka na pierwszy wynik ciężkiego pipeline'u
     * należący do aktualnej sceny.
     *
     * W tym czasie nie pokazujemy czasów poprzedniej
     * inferencji jako danych bieżącego kadru.
     */
    private boolean liveHudAwaitingFreshResult =
            true;

    private boolean explicitExitRequested;
    private ResearchArchive.Kind pendingExportKind;
    private boolean exportInProgress;
    private RecognitionProfile recognitionProfile = RecognitionProfile.BALANCED;
    private CameraResolutionCatalog cameraResolutionCatalog;

    private CameraResolutionSelection cameraResolutionSelection =
            CameraResolutionSelection.auto();
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
    private ThermalConfig experimentThermalConfig =
            ThermalConfig.disabled();

    private ThermalMonitor thermalMonitor;

    private ThermalMonitor.Snapshot latestThermalSnapshot;

    private boolean waitingForThermalStart;

    private long thermalReadySinceElapsedMillis =
            -1L;
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


    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            requestAnalysisStart();
                        } else {
                            livePresentation.showTransient(
                                    getString(R.string.camera_permission_required)
                            );

                            recordWarning(
                                    "Odmówiono dostępu do kamery, możesz dodać dostęp Ustawienia -> Aplikacje."
                            );
                        }
                    }
            );

    private final ActivityResultLauncher<Intent> reportDestination = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Uri uri = result.getResultCode() == RESULT_OK && result.getData() != null
                        ? result.getData().getData()
                        : null;
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
        enableImmersiveMode();

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
        autoZoomController.setEnabled(
                uiPreferences.getBoolean(
                        KEY_AUTO_ZOOM_ENABLED,
                        false
                )
        );
        configureRecognitionProfile();

        cameraResolutionCatalog =
                new CameraResolutionCatalog(
                        this
                );

        configureCameraResolutionSelection();
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

        applyDebugBaselineProfile();


        pipeline.setVehicleCascadeEnabled(
                vehicleCascadeEnabled
        );

        pipeline.setExperimentConfiguration(
                experimentModeEnabled,
                experimentRoiBudgetPolicy
        );
        pipeline.setSceneHandlingMode(
                effectiveSceneHandlingMode()
        );


        configureAppMenu();
        configureCaptureCollection();
        configureAnalysisControls();
        modelRegistry.reload();

        scheduleMissingAutotuning();
    }

    private void applyDebugBaselineProfile() {
        if (!BuildConfig.DEBUG || getIntent() == null) return;
        String profile = getIntent().getStringExtra("debug_baseline_profile");
        if (profile == null || profile.trim().isEmpty()) return;
        switch (profile.trim().toLowerCase(Locale.ROOT)) {
            case "r0":
                experimentModeEnabled = true;
                experimentRoiBudgetPolicy = RoiBudgetPolicy.FULL_FRAME;
                break;
            case "r1":
                experimentModeEnabled = true;
                experimentRoiBudgetPolicy = RoiBudgetPolicy.ONE_ROI;
                break;
            case "r2":
                experimentModeEnabled = true;
                experimentRoiBudgetPolicy = RoiBudgetPolicy.TWO_ROI;
                break;
            case "live":
                experimentModeEnabled = false;
                vehicleCascadeEnabled = true;
                break;
            default:
                return;
        }
        android.util.Log.i(
                "ALPR_BASELINE",
                "debug_profile=" + profile
                        + " experiment=" + experimentModeEnabled
                        + " roi=" + experimentRoiBudgetPolicy.wireName()
        );
    }

    private void bindViews() {

        previewView =
                findViewById(
                        R.id.camera_preview
                );

        overlayView =
                findViewById(
                        R.id.detection_overlay
                );

        analysisStartButton =
                findViewById(
                        R.id.analysis_start_button
                );

        liveStatus =
                findViewById(
                        R.id.live_status
                );

        liveStatusSecondary = findViewById(R.id.live_status_secondary);
        liveStatusStrip = findViewById(R.id.live_status_strip);
        liveStatusDot = findViewById(R.id.live_status_dot);
        liveEvent = findViewById(R.id.live_event);

        liveHud =
                findViewById(
                        R.id.live_hud
                );

        liveHudRow =
                findViewById(
                        R.id.live_hud_row
                );

        autoZoomButton =
                findViewById(
                        R.id.auto_zoom_button
                );

        autoZoomGlow =
                findViewById(
                        R.id.auto_zoom_glow
                );

        autoZoomControl = findViewById(R.id.auto_zoom_control);
        autoZoomTarget = findViewById(R.id.auto_zoom_target);

        recognitionHint =
                findViewById(
                        R.id.recognition_hint
                );

        confirmedResultTray = findViewById(R.id.confirmed_result_tray);
        confirmedResultText = findViewById(R.id.confirmed_result_text);
        confirmedResultConfidence = findViewById(R.id.confirmed_result_confidence);
        confirmedResultMeta = findViewById(R.id.confirmed_result_meta);

        livePresentation = new LivePresentationController(
                liveStatusStrip,
                liveStatusDot,
                liveStatus,
                liveStatusSecondary,
                findViewById(R.id.live_diagnostics_toggle),
                liveEvent,
                liveHudRow,
                liveHud,
                recognitionHint,
                confirmedResultTray,
                confirmedResultText,
                confirmedResultConfidence,
                confirmedResultMeta
        );
        livePresentation.setDiagnosticsVisibilityListener(
                overlayView::setDiagnosticMode
        );

        collectionToggle =
                findViewById(
                        R.id.collection_toggle
                );

        galleryOpenButton =
                findViewById(
                        R.id.gallery_open_button
                );

        exportReportButton =
                findViewById(
                        R.id.export_report_button
                );

        progress =
                findViewById(
                        R.id.progress
                );

        topAppBar =
                findViewById(
                        R.id.top_app_bar
                );

        experimentTimerButton =
                findViewById(
                        R.id.experiment_timer_button
                );
        experimentThermalButton =
                findViewById(
                        R.id.experiment_thermal_button
                );
        thermalMonitor = new ThermalMonitor(this);

        latestThermalSnapshot = thermalMonitor.read();


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
        livePresentation.showTransient(getString(
                R.string.recognition_profile_changed,
                recognitionProfileLabel()
        ));
    }





    private void updateCaptureMetrics() {

        Size requested =
                chooseAnalysisSize();


        boolean highResolution =
                cameraResolutionCatalog != null
                        && cameraResolutionCatalog
                        .isHighResolution(
                                requested
                        );


        metricsCollector.setCaptureConfiguration(
                cameraResolutionSelection.wireName(),
                requested.getWidth(),
                requested.getHeight(),
                highResolution
        );
    }

    private void configureCameraResolutionSelection() {

        cameraResolutionSelection =
                readCameraResolutionSelection();

        updateCaptureMetrics();
    }


    private CameraResolutionSelection readCameraResolutionSelection() {

        if (uiPreferences.contains(
                SettingsActivity.KEY_ANALYSIS_RESOLUTION_SELECTION
        )) {

            return CameraResolutionSelection.fromWireName(
                    uiPreferences.getString(
                            SettingsActivity.KEY_ANALYSIS_RESOLUTION_SELECTION,
                            CameraResolutionSelection.AUTO
                    )
            );
        }


        /*
         * Migracja starego profilu.
         */
        AnalysisResolutionProfile legacy =
                AnalysisResolutionProfile.fromWireName(
                        uiPreferences.getString(
                                "analysis_resolution_profile",
                                AnalysisResolutionProfile.AUTO.wireName()
                        )
                );


        if (legacy
                == AnalysisResolutionProfile.AUTO) {

            return CameraResolutionSelection.auto();
        }


        Size target =
                legacy
                        == AnalysisResolutionProfile.FAST

                        ? new Size(
                        640,
                        480
                )

                        : new Size(
                        1920,
                        1080
                );


        Size resolved =
                cameraResolutionCatalog
                        .closestRegularTo(
                                target
                        );


        CameraResolutionSelection migrated =
                CameraResolutionSelection.exact(
                        resolved != null
                                ? resolved
                                : target
                );


        uiPreferences.edit()
                .putString(
                        SettingsActivity.KEY_ANALYSIS_RESOLUTION_SELECTION,
                        migrated.wireName()
                )
                .apply();


        return migrated;
    }


    private void applyCameraResolutionSelection(
            CameraResolutionSelection selection
    ) {

        if (selection == null) {
            selection =
                    CameraResolutionSelection.auto();
        }

        if (selection.equals(
                cameraResolutionSelection
        )) {
            return;
        }


        cameraResolutionSelection =
                selection;


        uiPreferences.edit()
                .putString(
                        SettingsActivity.KEY_ANALYSIS_RESOLUTION_SELECTION,
                        cameraResolutionSelection.wireName()
                )
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

            /*
             * Restart techniczny.
             *
             * ExperimentSession i timer pozostają bez zmian.
             */
            cameraController.stop();

            cameraStarted = false;

            startCamera(
                    false
            );
        }


        livePresentation.showTransient(
                getString(
                        R.string.resolution_profile_changed,
                        cameraResolutionLabel()
                )
        );
    }


    private String cameraResolutionLabel() {

        if (cameraResolutionSelection.automatic()) {

            Size automatic =
                    chooseAnalysisSize();

            return "Auto · "
                    + cameraResolutionCatalog.label(
                    automatic
            );
        }


        Size selected =
                cameraResolutionSelection.size();

        if (selected == null) {
            selected =
                    chooseAnalysisSize();
        }

        Size available =
                cameraResolutionCatalog.find(
                        selected.getWidth(),
                        selected.getHeight()
                );

        return cameraResolutionCatalog.label(
                available != null
                        ? available
                        : selected
        );
    }

    private RoiBudgetPolicy effectiveRoiBudgetPolicy() {
        if (experimentModeEnabled) {
            return experimentRoiBudgetPolicy;
        }

        return vehicleCascadeEnabled
                ? RoiBudgetPolicy.TWO_ROI
                : RoiBudgetPolicy.FULL_FRAME;
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
            livePresentation.showTransient(getString(R.string.vehicle_cascade_missing_model));
        } else {
            livePresentation.showTransient(getString(enabled
                    ? R.string.vehicle_cascade_enabled
                    : R.string.vehicle_cascade_disabled));
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
        livePresentation.clearResult();
        livePresentation.showState(
                LivePresentationController.State.SEARCHING,
                hudRoiLabel() + " · SZUKAM"
        );
        clearCapturedCrops();
        livePresentation.showTransient(getString(R.string.recognition_cleared));
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

    private void enableImmersiveMode() {

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.hide(
                WindowInsetsCompat.Type.systemBars()
        );

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    private void configureAnalysisControls() {

        experimentTimerButton.setOnClickListener(
                view ->
                        showExperimentTimerDialog()
        );
        experimentThermalButton.setOnClickListener(
                view ->
                        showExperimentThermalDialog()
        );
        autoZoomButton.setOnClickListener(
                view -> toggleAutoZoom()
        );
        autoZoomButton.setOnLongClickListener(view -> {
            showAutoZoomModeDialog();
            return true;
        });
        updateAutoZoomButton();


        /*
         * Jeden przycisk obsługuje cały lifecycle analizy.
         */
        analysisStartButton.setOnClickListener(
                view -> {

                    if (cameraStarted) {

                        stopAnalysis();

                    } else if (waitingForThermalStart) {

                        cancelThermalStartWaiting();

                    } else {

                        ensureCameraPermission();
                    }
                }
        );


        if (!cameraStarted) {

            previewView.setVisibility(
                    View.INVISIBLE
            );
        }


        renderAnalysisControls();


        livePresentation.stop();

        exportReportButton.setOnClickListener(
                view ->
                        requestExportDestination(
                                ResearchArchive.Kind.RESEARCH_SESSION
                        )
        );
    }

    private void renderAnalysisControls() {

        analysisStartButton.setText(
                cameraStarted

                        ? R.string.analysis_stop

                        : waitingForThermalStart

                          ? R.string.analysis_cancel_waiting

                          : R.string.analysis_start
        );


        analysisStartButton.setIconResource(
                cameraStarted
                        || waitingForThermalStart

                        ? R.drawable.ic_stop_24

                        : R.drawable.ic_camera_24
        );


        /*
         * Cropy mogą być zbierane tylko podczas
         * aktywnej analizy.
         */
        if (collectionToggle != null) {

            collectionToggle.setEnabled(
                    cameraStarted
            );
        }


        /*
         * Timer jest widoczny wyłącznie w trybie EXP.
         */
        if (experimentTimerButton != null) {

            experimentTimerButton.setVisibility(
                    experimentModeEnabled
                            ? View.VISIBLE
                            : View.GONE
            );


            /*
             * Konfigurację czasu można zmienić
             * tylko przed rozpoczęciem przebiegu.
             */
            experimentTimerButton.setEnabled(
                    experimentModeEnabled
                            && !cameraStarted
            );


            updateExperimentTimerButton();
        }

        if (exportReportButton != null) {

            boolean experimentFinished =
                    experimentModeEnabled
                            && experimentSession.state()
                            == ExperimentSession.State.FINISHED;

            exportReportButton.setVisibility(
                    experimentFinished
                            ? View.VISIBLE
                            : View.GONE
            );

            exportReportButton.setEnabled(
                    experimentFinished
                            && !exportInProgress
            );
        }

        if (recognitionHint != null) {
            recognitionHint.setVisibility(
                    View.GONE
            );
        }

        if (liveHud != null) {

            if (cameraStarted) {

                renderLiveHud();

            } else {

                liveHud.setVisibility(
                        View.GONE
                );
            }
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
        resetAutoZoomForStoppedCamera();
        cameraStarted = false;
        liveHudAwaitingFreshResult =
                true;
        stopPreviewSceneMonitor();

        /*
         * Unieważniamy również ewentualną inferencję
         * znajdującą się jeszcze w toku.
         */
        uiSceneGeneration.incrementAndGet();
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

        targetStateMachine.reset();

        overlayTracker.reset();

        previewPlateTracker.reset();
        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();

        latestPipelinePlateItems =
                java.util.Collections.emptyList();

        previewSceneAnchorGuard.reset();

        previewSceneAnchorPending =
                false;

        lastCaptureByTrack.clear();

        overlayView.setItems(
                java.util.Collections.emptyList()
        );

        livePresentation.stop();



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

        experimentTimerDeadlineElapsedMillis =
                android.os.SystemClock.elapsedRealtime()
                        + timerConfig.durationMillis();

        experimentTimerHandler.postDelayed(
                experimentTimerRunnable,
                timerConfig.durationMillis()
        );

        experimentTimerHandler.post(
                experimentTimerUiRunnable
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

        experimentTimerHandler.removeCallbacks(
                experimentTimerUiRunnable
        );

        experimentTimerDeadlineElapsedMillis = -1L;
    }
    private void beginAnalysisMeasurement() {
        synchronized (this) {
            telemetryActiveTrackIds.clear();
            telemetryConfirmedTrackIds.clear();
            telemetryConsensusByTrack.clear();
        }
        metricsCollector.startMeasurementSession();

        if (experimentModeEnabled) {

            /*
             * Zamrażamy konfigurację timera dla konkretnego przebiegu.
             * Zmiana ustawień później nie zmienia aktywnej sesji.
             */
            TimerConfig timerForSession =
                    experimentTimerConfig;

            int replicateIndex = Math.max(
                    1,
                    uiPreferences.getInt(
                            SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX,
                            1
                    )
            );
            String defaultSeriesId = "ROI-" + new SimpleDateFormat(
                    "yyyyMMdd",
                    Locale.ROOT
            ).format(new Date());
            ExperimentIdentity identity = new ExperimentIdentity(
                    uiPreferences.getString(
                            SettingsActivity.KEY_EXPERIMENT_SERIES_ID,
                            defaultSeriesId
                    ),
                    uiPreferences.getString(
                            SettingsActivity.KEY_EXPERIMENT_SCENARIO_ID,
                            "live_camera"
                    ),
                    replicateIndex,
                    uiPreferences.getString(
                            SettingsActivity.KEY_EXPERIMENT_NOTES,
                            ""
                    ),
                    autoZoomController.enabled(),
                    cameraController == null ? 1.0 : cameraController.maximumZoomRatio()
            );

            boolean started =
                    experimentSession.start(
                            "roi_budget",
                            experimentRoiBudgetPolicy.wireName(),
                            timerForSession,
                            experimentThermalConfig,
                            identity
                    );

            if (started) {
                metricsCollector.setExperimentSessionId(
                        experimentSession.sessionId()
                );
                if (latestThermalSnapshot != null) {
                    metricsCollector.recordThermalSample(latestThermalSnapshot);
                }

                recordInfo(
                        "Rozpoczęto eksperyment "
                                + experimentSession.sessionId()
                                + " type="
                                + experimentSession.experimentType()
                                + " variant="
                                + experimentSession.variant()
                                + " series="
                                + identity.seriesId
                                + " scenario="
                                + identity.scenarioId
                                + " replicate="
                                + identity.replicateIndex
                );

                uiPreferences.edit()
                        .putInt(
                                SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX,
                                identity.replicateIndex + 1
                        )
                        .apply();

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
            metricsCollector.setExperimentSessionId("");
            if (latestThermalSnapshot != null) {
                metricsCollector.recordThermalSample(latestThermalSnapshot);
            }
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

        /*
         * Kończymy aktywne odliczanie, ale zachowujemy
         * konfigurację timera dla następnego przebiegu.
         *
         * Dzięki temu seria R0/R1/R2 o jednakowym czasie
         * nie wymaga ponownego ustawiania timera
         * po każdym eksperymencie.
         *
         * ExperimentSession nadal przechowuje własną
         * zamrożoną konfigurację zakończonego przebiegu.
         */
                updateExperimentTimerButton();


    }
    private void ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            requestAnalysisStart();
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
    private void startPreviewSceneMonitor() {

        previewSceneHandler.removeCallbacks(
                previewSceneMonitorRunnable
        );

        previewSceneDetector.reset();

        previewSceneMonitorRunning =
                true;

        previewSceneHandler.post(
                previewSceneMonitorRunnable
        );
    }

    private boolean needsPreviewSceneSampling() {
        return previewSceneAnchorPending
                || lastRenderedContinuityState
                != com.example.alpr_v1.continuity.SceneContinuityState.STABLE
                || !latestPipelinePlateItems.isEmpty()
                || !latestDiagnosticOverlayItems.isEmpty()
                || autoZoomMemoryVisible
                || autoZoomReturnValidationPending
                || autoZoomZoomedAnchorPending
                || autoZoomZoomedAnchorValid
                || isAutoZoomHoldingMemory();
    }


    private void stopPreviewSceneMonitor() {

        previewSceneMonitorRunning =
                false;

        previewSceneHandler.removeCallbacks(
                previewSceneMonitorRunnable
        );

        previewSceneDetector.reset();
    }


    private void invalidateUiForPreviewSceneChange(
            SceneChangeDetector.Result scene
    ) {
        invalidateUiForPreviewSceneChange(scene.score, scene.changedFraction);
    }

    private void renderPreviewContinuityDecision(
            SceneTransitionDecision decision,
            float sceneScore,
            float changedFraction,
            List<OverlayItem> trackedItems
    ) {
        if (decision == null) return;
        if (decision.action == SceneTransitionAction.NONE
                && decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.STABLE) {
            lastRenderedContinuityState =
                    com.example.alpr_v1.continuity.SceneContinuityState.STABLE;
            TargetSnapshot target = targetStateMachine.snapshot();
            livePresentation.showState(
                    target != null && target.hasTrack()
                            ? LivePresentationController.State.TRACKING
                            : LivePresentationController.State.SEARCHING,
                    hudRoiLabel()
            );
            return;
        }
        lastRenderedContinuityState = decision.nextState;
        SceneTransitionAction action = decision.action;
        if (action == SceneTransitionAction.HARD_RESET) {
            invalidateUiForPreviewSceneChange(sceneScore, changedFraction, false);
            return;
        }
        if (action == SceneTransitionAction.RELEASE_ACTIVE_TARGET) {
            targetStateMachine.reset();
            previewPlateTracker.reset();
            List<OverlayItem> vehicles = new ArrayList<>();
            for (OverlayItem item : currentOverlayItems()) {
                if (item.kind != OverlayItem.Kind.PLATE) vehicles.add(item);
            }
            applyVisibleOverlay(
                    vehicles,
                    latestOverlaySourceWidth,
                    latestOverlaySourceHeight
            );
            livePresentation.showState(
                    LivePresentationController.State.SEARCHING,
                    "Cel utracony — kontynuowanie skanowania"
            );
            return;
        }

        boolean holding = action == SceneTransitionAction.SOFT_HOLD
                || decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.MOTION_HOLD;
        if (holding) {
            if (trackedItems != null && !trackedItems.isEmpty()) {
                presentTrackedPreviewOverlay(trackedItems);
            }
            markVehicleOverlaysPredicted();
            livePresentation.showState(
                    LivePresentationController.State.RECOVERING,
                    "Ruch kamery — utrzymywanie celu"
            );
            return;
        }

        boolean reacquiring = action == SceneTransitionAction.SOFT_REACQUIRE
                || decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.REACQUIRING;
        if (reacquiring) {
            if (decision.resetFocusedTracker
                    || action == SceneTransitionAction.SOFT_REACQUIRE) {
                previewPlateTracker.reset();
                overlayTracker.reset();
                latestPipelinePlateItems = java.util.Collections.emptyList();
                lastCaptureByTrack.clear();
                overlayView.setFocusedTrackId(0L);

                List<OverlayItem> vehicles = new ArrayList<>();
                for (OverlayItem item : currentOverlayItems()) {
                    if (item.kind != OverlayItem.Kind.PLATE) {
                        vehicles.add(item.kind == OverlayItem.Kind.VEHICLE
                                || item.kind == OverlayItem.Kind.VEHICLE_ROI
                                ? new OverlayItem(
                                        item.kind,
                                        item.normalizedBounds,
                                        item.normalizedKeypoints,
                                        item.label,
                                        item.trackId,
                                        true
                                )
                                : item);
                    }
                }
                applyVisibleOverlay(
                        vehicles,
                        latestOverlaySourceWidth,
                        latestOverlaySourceHeight
                );
                livePresentation.clearResult();
            }
            livePresentation.showState(
                    LivePresentationController.State.RECOVERING,
                    "Ponowne dopasowanie pojazdu"
            );
        }
    }

    private void markVehicleOverlaysPredicted() {
        List<OverlayItem> predicted = new ArrayList<>();
        for (OverlayItem item : currentOverlayItems()) {
            if (item.kind == OverlayItem.Kind.VEHICLE
                    || item.kind == OverlayItem.Kind.VEHICLE_ROI) {
                predicted.add(new OverlayItem(
                        item.kind,
                        item.normalizedBounds,
                        item.normalizedKeypoints,
                        item.label,
                        item.trackId,
                        true
                ));
            } else {
                predicted.add(item);
            }
        }
        applyVisibleOverlay(
                predicted,
                latestOverlaySourceWidth,
                latestOverlaySourceHeight
        );
    }

    private void invalidateUiForPreviewSceneChange(
            float sceneScore,
            float changedFraction
    ) {
        invalidateUiForPreviewSceneChange(sceneScore, changedFraction, true);
    }

    private void invalidateUiForPreviewSceneChange(
            float sceneScore,
            float changedFraction,
            boolean requestPipelineReset
    ) {

        if (isAutoZoomHoldingMemory()) {
            previewSceneDetector.reset();
            previewSceneAnchorGuard.reset();
            previewPlateTracker.reset();
            return;
        }


        /*
         * Wszystkie wyniki pipeline'u rozpoczęte przed tym
         * momentem należą już do starej sceny.
         */
        long generation =
                uiSceneGeneration.incrementAndGet();

        /* Pipeline decision has already been applied by SceneTransitionCoordinator. */
        targetStateMachine.reset();

        clearAutoZoomRecognitionMemory();
        if (currentCameraZoomRatio > 1.01f) {
            recordInfo("Auto zoom przerwany: wykryto zmianę sceny");
            resetAutoZoomSessionAfterReturn = true;
            requestAutoZoomReturn(null);
        } else {
            /* Dopiero rzeczywista zmiana sceny otwiera tablice na kolejną próbę zoomu. */
            autoZoomController.resetSession();
        }


        /*
         * Czyścimy wyłącznie stan prezentacji.
         *
         * NIE wywołujemy tutaj pipeline.resetTracking(),
         * ponieważ ciężki pipeline może właśnie pracować
         * na swoim wątku.
         */
        overlayTracker.reset();

        previewPlateTracker.reset();

        previewSceneAnchorGuard.reset();

        previewSceneAnchorPending =
                false;

        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();

        latestPipelinePlateItems =
                java.util.Collections.emptyList();



        lastCaptureByTrack.clear();



        overlayView.setItems(
                java.util.Collections.emptyList()
        );


        /*
         * Kolejny prawidłowy wynik ma zostać pokazany
         * natychmiast, bez dodatkowego throttlingu UI.
         */
        lastUiUpdateNanos.set(
                0L
        );


        /*
         * Od tej chwili ostatni InferenceTrace należy
         * do poprzedniej sceny.
         *
         * Nie usuwamy go z MetricsCollector, bo jest
         * poprawnym wynikiem badawczym. Unieważniamy
         * tylko jego prezentację w HUD.
         */
        liveHudAwaitingFreshResult =
                true;


        /*
         * Odświeżamy HUD od razu, nie dopiero po
         * zakończeniu następnego MP/MT/MZ.
         */
        renderLiveHud();


        livePresentation.clearResult();
        livePresentation.showState(
                LivePresentationController.State.RECOVERING,
                hudRoiLabel() + " · NOWA SCENA"
        );
        livePresentation.showTransient(getString(R.string.scene_change_analyzing));


        android.util.Log.d(
                "ALPR_SCENE_UI",
                String.format(
                        Locale.ROOT,
                        "INVALIDATE generation=%d score=%.3f fraction=%.3f",
                        generation,
                        sceneScore,
                        changedFraction
                )
        );
    }

    private void invalidateUiForLostPreviewTracking() {

        if (isAutoZoomHoldingMemory()) {
            previewSceneDetector.reset();
            previewSceneAnchorGuard.reset();
            previewPlateTracker.reset();
            return;
        }

        /*
         * Tracker Preview stracił wszystkie tablice.
         *
         * Traktujemy to jako zmianę kontekstu obrazu,
         * nawet jeżeli globalny SceneChangeDetector
         * nie przekroczył jeszcze swojego progu.
         */
        long generation =
                uiSceneGeneration.incrementAndGet();


        /*
         * Wynik ciężkiej inferencji rozpoczętej przed
         * utratą trackera nie może później ponownie
         * narysować starej tablicy.
         */
        TargetSnapshot lostTarget = targetStateMachine.onTrackingLost();
        if (pipeline != null) {
            pipeline.setTargetSnapshot(lostTarget);
            pipeline.requestImmediateTargetRecovery();
        }

        clearAutoZoomRecognitionMemory();
        if (currentCameraZoomRatio > 1.01f) {
            recordInfo("Auto zoom przerwany: utracono śledzoną tablicę");
            requestAutoZoomReturn(null);
        }


        overlayTracker.reset();

        previewPlateTracker.reset();
        previewSceneAnchorGuard.reset();

        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();



        previewSceneAnchorPending =
                false;

        lastCaptureByTrack.clear();



        overlayView.setItems(
                java.util.Collections.emptyList()
        );


        /*
         * Następny świeży PipelineResult ma zostać
         * pokazany bez throttlingu UI.
         */
        lastUiUpdateNanos.set(
                0L
        );


        /*
         * Stare czasy również nie opisują już
         * aktualnie widocznego obrazu.
         */
        liveHudAwaitingFreshResult =
                true;

        renderLiveHud();


        livePresentation.onTargetLost();


        android.util.Log.d(
                "ALPR_SCENE_UI",
                "TRACK_LOST generation="
                        + generation
                        + " -> overlay invalidated"
        );
    }

    private void startCamera(boolean beginNewMeasurement) {
        if (cameraStarted) return;
        lastRenderedContinuityState =
                com.example.alpr_v1.continuity.SceneContinuityState.STABLE;
        if (beginNewMeasurement) {
            autoZoomHandler.removeCallbacksAndMessages(null);
            autoZoomController.resetSession();
            clearAutoZoomRecognitionMemory();
            cameraTransformInProgress = false;
            currentCameraZoomRatio = 1f;
            pipeline.finishCameraTransform();
        }
        if (beginNewMeasurement) {
            beginAnalysisMeasurement();
        }
        previewView.setVisibility(View.VISIBLE);

        /*
         * Każde ręczne uruchomienie analizy rozpoczyna się
         * bez stanu trackingowego poprzedniego przebiegu.
         */
        pipeline.resetTracking();
        targetStateMachine.reset();

        overlayTracker.reset();

        previewPlateTracker.reset();

        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();

        latestPipelinePlateItems =
                java.util.Collections.emptyList();

        previewSceneAnchorGuard.reset();

        previewSceneAnchorPending =
                false;

        lastCaptureByTrack.clear();

        overlayView.setItems(
                java.util.Collections.emptyList()
        );

        /*
         * Nowe uruchomienie kamery oznacza nową generację.
         * Wynik ewentualnej starej inferencji nie może
         * pojawić się po restarcie.
         */
        uiSceneGeneration.incrementAndGet();


        /*
         * Również restart techniczny kamery nie może
         * chwilowo pokazywać czasów poprzedniego obrazu.
         */
        liveHudAwaitingFreshResult =
                true;


        cameraStarted = true;

        startPreviewSceneMonitor();

        renderAnalysisControls();

        if (cameraMotionMonitor != null) {
            cameraMotionMonitor.start();
        }

        livePresentation.clearResult();
        livePresentation.showState(
                LivePresentationController.State.SEARCHING,
                hudRoiLabel() + " · SZUKAM"
        );

        recordInfo(
                beginNewMeasurement
                        ? "Uruchamianie kamery i pipeline'u"
                        : "Restart kamery w aktywnej analizie"
        );
        Size requestedCameraSize =
                chooseAnalysisSize();

        boolean allowHighResolution =
                cameraResolutionCatalog != null
                        && cameraResolutionCatalog
                        .isHighResolution(
                                requestedCameraSize
                        );
        lastDirectLumaFrameNanos = 0L;
        directLumaTrackingUnavailable = false;
        cameraController.start(
                image -> {

                    /*
                     * Zapamiętujemy scenę, do której należała klatka
                     * w chwili rozpoczęcia ciężkiej inferencji.
                     */
                    final long sceneGenerationAtStart =
                            uiSceneGeneration.get();
                    final long transformGenerationAtStart =
                            uiCameraTransformGeneration.get();

                    if (!pipelineFrameInFlight.compareAndSet(false, true)) {
                        return;
                    }
                    final long sourceTimestampNanos =
                            image.getImageInfo().getTimestamp();
                    final CameraImageConverter.Result conversion;
                    try {
                        conversion = CameraImageConverter.convert(image);
                    } catch (RuntimeException conversionError) {
                        pipelineFrameInFlight.set(false);
                        throw conversionError;
                    }
                    final Bitmap pipelineBitmap = conversion.bitmap;

                    try {
                        pipelineInferenceExecutor.execute(() -> {
                            try {

                    long observationNanos =
                            System.nanoTime();


                    pipeline.setCameraMotionEvidence(
                            cameraMotionMonitor.isGyroscopeAvailable(),
                            cameraMotionMonitor.isMoving(),
                            cameraMotionMonitor.isRapidMotion(),
                            cameraMotionMonitor.magnitude()
                    );
                    pipeline.setCurrentCameraZoomRatio(currentCameraZoomRatio);


                    PipelineResult result =
                            pipeline.processBitmap(
                                    pipelineBitmap,
                                    sourceTimestampNanos,
                                    conversion.toBitmapNanos,
                                    conversion.rotationNanos,
                                    conversion.rotationDegrees,
                                    (overlayItems, sourceWidth, sourceHeight, sourceStamp) -> {
                                        if (pipeline == null
                                                || !pipeline.isCurrentContinuityStamp(
                                                        sourceStamp
                                                )) {
                                            android.util.Log.d(
                                                    "ALPR_MT_CALLBACK_GATE",
                                                    "callback_rejected phase=analyzer"
                                                            + " scene=" + sourceStamp.sceneGeneration
                                                            + " visual=" + sourceStamp.visualEpoch
                                                            + " transform="
                                                            + sourceStamp.cameraTransformGeneration
                                            );
                                            return;
                                        }
                                        if (autoZoomController.state()
                                                != AutoZoomController.State.ZOOMED_RETRY) {
                                            return;
                                        }
                                        runOnUiThread(() -> {
                                            if (!cameraStarted
                                                    || pipeline == null
                                                    || !pipeline.isCurrentContinuityStamp(
                                                            sourceStamp
                                                    )
                                                    || sceneGenerationAtStart
                                                    != uiSceneGeneration.get()
                                                    || transformGenerationAtStart
                                                    != uiCameraTransformGeneration.get()
                                                    || cameraTransformInProgress
                                                    || autoZoomController.state()
                                                    != AutoZoomController.State.ZOOMED_RETRY) {
                                                if (pipeline == null
                                                        || !pipeline.isCurrentContinuityStamp(
                                                                sourceStamp
                                                        )) {
                                                    android.util.Log.d(
                                                            "ALPR_MT_CALLBACK_GATE",
                                                            "callback_rejected phase=ui"
                                                                    + " scene="
                                                                    + sourceStamp.sceneGeneration
                                                                    + " visual="
                                                                    + sourceStamp.visualEpoch
                                                                    + " transform="
                                                                    + sourceStamp.cameraTransformGeneration
                                                    );
                                                }
                                                return;
                                            }
                                            android.util.Log.d(
                                                    "ALPR_MT_CALLBACK_GATE",
                                                    "callback_accepted scene="
                                                            + sourceStamp.sceneGeneration
                                                            + " visual="
                                                            + sourceStamp.visualEpoch
                                                            + " transform="
                                                            + sourceStamp.cameraTransformGeneration
                                            );
                                            presentZoomedMtStage(
                                                    overlayItems,
                                                    sourceWidth,
                                                    sourceHeight,
                                                    sourceStamp
                                            );
                                        });
                                    },
                                    (score, changedFraction) -> runOnUiThread(() -> {
                                        if (!cameraStarted
                                                || sceneGenerationAtStart
                                                != uiSceneGeneration.get()
                                                || transformGenerationAtStart
                                                != uiCameraTransformGeneration.get()) {
                                            return;
                                        }
                                        try {
                                            JSONObject details = new JSONObject();
                                            details.put("score", score);
                                            details.put("changed_fraction", changedFraction);
                                            details.put(
                                                    "detection_latency_ms",
                                                    Math.max(0L, System.nanoTime() - observationNanos)
                                                            / 1_000_000.0
                                            );
                                            metricsCollector.recordEvent(
                                                    "scene_change_fast",
                                                    0L,
                                                    0L,
                                                    details
                                            );
                                        } catch (Exception ignored) {
                                            // Telemetria nie może opóźniać unieważnienia sceny.
                                        }
                                        invalidateUiForPreviewSceneChange(
                                                score,
                                                changedFraction
                                        );
                                    })
                            );


                    if (result == null) {
                        return;
                    }


                    /*
                     * Podczas MP/MT/MZ użytkownik mógł już skierować
                     * kamerę na zupełnie inną scenę.
                     *
                     * Takiego wyniku nie wolno ani pokazać,
                     * ani zapisać jako crop.
                     */
                    if (!cameraStarted
                            || sceneGenerationAtStart
                            != uiSceneGeneration.get()
                            || transformGenerationAtStart
                            != uiCameraTransformGeneration.get()) {

                        result.close();

                        return;
                    }

                    recordRecognitionTelemetry(result);


                    long now =
                            System.nanoTime();

                    long previous =
                            lastUiUpdateNanos.get();


                    if (now - previous >= 200_000_000L
                            && lastUiUpdateNanos.compareAndSet(
                            previous,
                            now
                    )) {

                        runOnUiThread(
                                () -> {

                                    /*
                                     * Generacja mogła zmienić się również
                                     * pomiędzy post() a wykonaniem callbacku UI.
                                     */
                                    if (!cameraStarted
                                            || sceneGenerationAtStart
                                            != uiSceneGeneration.get()
                                            || transformGenerationAtStart
                                            != uiCameraTransformGeneration.get()) {

                                        result.close();

                                        return;
                                    }


                                    try {

                                        presentResult(
                                                result,
                                                observationNanos
                                        );

                                    } finally {

                                        result.close();
                                    }
                                }
                        );

                    } else if (collectionActive
                            && containsNewCrop(
                            result.plateObservations
                    )) {

                        runOnUiThread(
                                () -> {

                                    /*
                                     * Stara scena nie może również trafić
                                     * do kolekcji cropów.
                                     */
                                    if (!cameraStarted
                                            || sceneGenerationAtStart
                                            != uiSceneGeneration.get()
                                            || transformGenerationAtStart
                                            != uiCameraTransformGeneration.get()) {

                                        result.close();

                                        return;
                                    }


                                    try {

                                        collectCrops(
                                                result.plateObservations
                                        );

                                    } finally {

                                        result.close();
                                    }
                                }
                        );

                    } else {

                        result.close();
                    }
                            } finally {
                                if (!pipelineBitmap.isRecycled()) {
                                    pipelineBitmap.recycle();
                                }
                                pipelineFrameInFlight.set(false);
                            }
                        });
                    } catch (RuntimeException dispatchError) {
                        if (!pipelineBitmap.isRecycled()) pipelineBitmap.recycle();
                        pipelineFrameInFlight.set(false);
                        throw dispatchError;
                    }
                },
                new CameraController.LumaFrameHandler() {
                    @Override
                    public void onLumaFrame(LumaFrame frame) {
                        directLumaTrackingUnavailable = false;
                        processDirectLumaFrame(frame);
                    }

                    @Override
                    public void onUnavailable() {
                        directLumaTrackingUnavailable = true;
                        lastDirectLumaFrameNanos = 0L;
                        android.util.Log.w(
                                "ALPR_PREVIEW_TRACK",
                                "Oddzielny strumień YUV niedostępny; używam PreviewView fallback"
                        );
                    }
                },
                error -> runOnUiThread(() -> {
                    cameraStarted = false;
                    stopPreviewSceneMonitor();

                    uiSceneGeneration.incrementAndGet();
                    finishAnalysisMeasurement(
                            ExperimentSession.CompletionReason.ERROR
                    );
                    if (cameraMotionMonitor != null) {
                        cameraMotionMonitor.stop();
                    }

                    renderAnalysisControls();
                    livePresentation.showState(
                            LivePresentationController.State.ERROR,
                            hudRoiLabel()
                    );
                    livePresentation.showTransient(
                            getString(R.string.camera_error, error.getMessage())
                    );
                    recordError("Błąd kamery: " + error.getMessage(), error);
                }),
                requestedCameraSize,
                allowHighResolution
        );
    }

    private Size chooseAnalysisSize() {

        /*
         * Ręczny wybór:
         * dokładnie rozdzielczość wskazana przez użytkownika.
         */
        if (!cameraResolutionSelection.automatic()) {

            Size requested =
                    cameraResolutionSelection.size();

            if (requested != null) {

                Size available =
                        cameraResolutionCatalog.find(
                                requested.getWidth(),
                                requested.getHeight()
                        );

                if (available != null) {
                    return available;
                }

                /*
                 * Np. ustawienie przeniesione ze starszego
                 * telefonu na nowe urządzenie.
                 */
                Size fallback =
                        cameraResolutionCatalog
                                .closestRegularTo(
                                        requested
                                );

                if (fallback != null) {
                    return fallback;
                }

                return requested;
            }
        }


        /*
         * AUTO:
         * słabsze urządzenie -> okolice 640x480,
         * mocniejsze -> okolice 1280x720.
         *
         * Wybieramy jednak FORMAT RZECZYWIŚCIE
         * zgłaszany przez aparat.
         */
        boolean constrained =
                deviceProfile.lowRamDevice
                        || deviceProfile.totalMemoryBytes
                        < 4L
                        * 1024L
                        * 1024L
                        * 1024L;


        Size target =
                constrained

                        ? new Size(
                        640,
                        480
                )

                        : new Size(
                        1280,
                        720
                );


        Size resolved =
                cameraResolutionCatalog
                        .closestRegularTo(
                                target
                        );


        return resolved != null
                ? resolved
                : target;
    }

    private void renderLiveHud() {

        if (liveHud == null) {
            return;
        }


        if (!cameraStarted) {
            if (livePresentation != null) livePresentation.stop();
            return;
        }


        MetricsCollector.LiveSnapshot snapshot =
                metricsCollector.liveSnapshot();


        int width =
                snapshot.sourceWidth;

        int height =
                snapshot.sourceHeight;


        /*
         * Przed otrzymaniem pierwszej klatki CameraX
         * pokazujemy rozdzielczość oczekiwaną.
         */
        if (width <= 0
                || height <= 0) {

            Size requested =
                    chooseAnalysisSize();

            width =
                    requested.getWidth();

            height =
                    requested.getHeight();
        }


        double megapixels =
                (
                        (double) width
                                * (double) height
                ) / 1_000_000.0;


        String resolution;


        if (cameraResolutionSelection.automatic()) {

            resolution =
                    String.format(
                            Locale.forLanguageTag("pl-PL"),
                            "AUTO→%d×%d · %.1fM",
                            width,
                            height,
                            megapixels
                    );

        } else {

            resolution =
                    String.format(
                            Locale.forLanguageTag("pl-PL"),
                            "%d×%d · %.1fM",
                            width,
                            height,
                            megapixels
                    );
        }


        /*
         * Jeżeli obraz Preview należy już do nowej sceny,
         * ale ciężki pipeline nie zakończył jeszcze jej
         * pierwszej inferencji, ostatnie czasy w MetricsCollector
         * dotyczą poprzedniego kadru.
         *
         * Dlatego pokazujemy kreski.
         */
        double vehicleInference =
                liveHudAwaitingFreshResult
                        ? Double.NaN
                        : snapshot.vehicleInferenceMs;

        double plateInference =
                liveHudAwaitingFreshResult
                        ? Double.NaN
                        : snapshot.plateInferenceMs;

        double characterInference =
                liveHudAwaitingFreshResult
                        ? Double.NaN
                        : snapshot.characterInferenceMs;

        double pipelineInference =
                liveHudAwaitingFreshResult
                        ? Double.NaN
                        : snapshot.pipelineMs;
        String firstDiagnosticsLine =
                "MP "
                        + hudDuration(
                        vehicleInference
                )
                        + " · MT "
                        + hudDuration(
                        plateInference
                )
                        + " · MZ "
                        + hudDuration(
                        characterInference
                );


        /*
         * DROP jest licznikiem całej aktywnej sesji,
         * więc może pozostać widoczny również podczas
         * oczekiwania na nowy trace.
         */
        String secondDiagnosticsLine =
                "PIPE "
                        + hudDuration(
                        pipelineInference
                )
                        + " · DROP "
                        + snapshot.droppedFrames;

        StringBuilder hudText = new StringBuilder()
                .append(firstDiagnosticsLine)
                .append('\n')
                .append(secondDiagnosticsLine)
                .append('\n')
                .append(resolution);
        if (autoZoomController.enabled()) {
            hudText.append('\n').append(autoZoomHudLabel());
        }
        TargetSnapshot target = targetStateMachine.snapshot();
        if (target.trackId > 0L) {
            hudText.append('\n').append(String.format(
                    Locale.forLanguageTag("pl-PL"),
                    "KLT q %.2f · %d/8 inlier",
                    target.trackingQuality,
                    target.trackerInliers
            ));
        }
        LivePresentationController.State presentationState;
        switch (target.state) {
            case LOCKED:
                presentationState = LivePresentationController.State.TRACKING;
                break;
            case TRACKING:
            case ACQUIRED:
                presentationState = LivePresentationController.State.RECOGNIZING;
                break;
            case DEGRADED:
            case LOST:
                presentationState = LivePresentationController.State.RECOVERING;
                break;
            case SEARCHING:
            default:
                presentationState = LivePresentationController.State.SEARCHING;
                break;
        }
        livePresentation.showState(presentationState, "");
        livePresentation.updateDiagnostics(hudText);
        updateAutoZoomButton();
    }
    private String hudPipelineLabel() {
        if (experimentModeEnabled) {
            return "EXP " + hudRoiLabel();
        }
        return vehicleCascadeEnabled
                ? "MP→MT→MZ"
                : "MT→MZ";
    }

    private String autoZoomHudLabel() {
        switch (autoZoomController.state()) {
            case ZOOM_SETTLING:
                return getString(R.string.hud_auto_zoom_focusing);
            case ZOOMED_RETRY:
                return getString(
                        R.string.hud_auto_zoom_retry,
                        currentCameraZoomRatio
                );
            case RETURNING:
                return getString(R.string.hud_auto_zoom_returning);
            case READY:
            default:
                return getString(
                        R.string.hud_auto_zoom_ready,
                        currentCameraZoomRatio
                );
        }
    }
    private String hudRoiLabel() {

        switch (effectiveRoiBudgetPolicy()) {

            case ONE_ROI:
                return "R1";

            case TWO_ROI:
                return "R2";

            case FULL_FRAME:
            default:
                return "R0";
        }
    }

    private static String hudDuration(
            double milliseconds
    ) {

        if (Double.isNaN(milliseconds)
                || Double.isInfinite(milliseconds)) {

            return "—";
        }


        return String.format(
                Locale.forLanguageTag("pl-PL"),
                "%.1fs",
                milliseconds / 1000.0
        );
    }
    private void presentResult(PipelineResult result, long observationNanos) {
        if (result.sceneReset && !isAutoZoomHoldingMemory()) {

            clearAutoZoomRecognitionMemory();

            if (currentCameraZoomRatio > 1.01f) {
                resetAutoZoomSessionAfterReturn = true;
                requestAutoZoomReturn(null);
            } else {
                autoZoomController.resetSession();
            }

            /*
             * Pipeline rozpoczął nową scenę.
             *
             * Usuwamy więc również stan trackera warstwy UI.
             */
            overlayTracker.reset();
            previewPlateTracker.reset();
            targetStateMachine.reset();
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

        if (autoZoomController.state()
                == AutoZoomController.State.ZOOMED_RETRY) {
            repositionAutoZoomTarget(result);
            if (!hasFreshAutoZoomTargetRecognition(result)) {
                livePresentation.showState(
                        LivePresentationController.State.RECOGNIZING,
                        hudRoiLabel() + " · AZ "
                                + String.format(Locale.ROOT, "%.1f×", currentCameraZoomRatio)
                );
                renderLiveHud();
                handleAutoZoomResult(result);
                return;
            }
            rememberFreshAutoZoomRecognition(result);
        }

        /*
         * Po powrocie do 1x nadal trzymamy wynik zoomu. Pierwszy sensowny
         * odczyt MZ tej samej ramki może go jeszcze uaktualnić, ale pusty lub
         * słabszy odczyt nie może wyczyścić tekstu widocznego na ekranie.
         */
        if (autoZoomMemoryVisible
                && autoZoomController.state()
                != AutoZoomController.State.ZOOMED_RETRY) {
            rememberFreshAutoZoomRecognition(result);
        }

        if (autoZoomMemoryVisible
                && (!hasAnyFreshRecognition(result)
                || !hasRecognitionCompatibleWithZoomMemory(result))) {
            transformMemoryOverlay(currentCameraZoomRatio);
            livePresentation.showState(
                    LivePresentationController.State.RECOGNIZING,
                    hudRoiLabel() + " · AZ"
            );
            renderLiveHud();
            handleAutoZoomResult(result);
            return;
        }
        autoZoomMemoryVisible = false;


        /*
         * presentResult() może zostać wywołane tylko dla wyniku,
         * który przeszedł kontrolę uiSceneGeneration.
         *
         * Oznacza to, że trace należy już do aktualnej sceny
         * i jego czasy mogą wrócić do HUD.
         */
        liveHudAwaitingFreshResult =
                false;


        renderLiveHud();
        if ("pipeline_error".equals(result.status)) refreshPersistentLogThrottled();
        long presentationNanos = System.nanoTime();
        List<OverlayItem> latencyAlignedItems = alignMtGeometryToLiveTarget(
                result.overlayItems,
                targetStateMachine.snapshot()
        );
        boolean geometryAlignedToPresentation =
                latencyAlignedItems != result.overlayItems;
        List<OverlayItem> visibleOverlayItems =
                overlayTracker.update(
                        latencyAlignedItems,
                        geometryAlignedToPresentation
                                ? presentationNanos : observationNanos,
                        presentationNanos
                );
        List<OverlayItem> diagnosticItems =
                new ArrayList<>();

        List<OverlayItem> pipelinePlateItems =
                new ArrayList<>();

        boolean hasCarriedPlatePrediction = false;


        for (OverlayItem item :
                visibleOverlayItems) {

            if (item.kind == OverlayItem.Kind.PLATE) {

                if (item.carriedPrediction) {
                    hasCarriedPlatePrediction = true;
                    continue;
                }

                /*
                 * Zapamiętujemy dokładną pozycję wynikającą
                 * z ostatniego pełnego przebiegu MT.
                 */
                pipelinePlateItems.add(
                        item
                );

            } else {

                /*
                 * VEHICLE oraz VEHICLE_ROI.
                 */
                diagnosticItems.add(
                        item
                );
            }
        }


        latestDiagnosticOverlayItems =
                java.util.Collections.unmodifiableList(
                        diagnosticItems
                );


        if (!hasCarriedPlatePrediction || !pipelinePlateItems.isEmpty()) {
            latestPipelinePlateItems =
                    java.util.Collections.unmodifiableList(
                            pipelinePlateItems
                    );
        }

        latestOverlaySourceWidth = result.sourceWidth;
        latestOverlaySourceHeight = result.sourceHeight;



        overlayView.setFocusedTrackId(targetStateMachine.snapshot().trackId);
        overlayView.setItems(
                visibleOverlayItems,
                result.sourceWidth,
                result.sourceHeight
        );


        /*
         * Każdy nowy rzeczywisty wynik MT ponownie
         * ustawia dokładną pozycję trackera Preview.
         *
         * Jeżeli w tej konkretnej inferencji MT niczego
         * nie zwrócił, tracker zachowa poprzednią kotwicę
         * przez krótki czas.
         */
        boolean previewTrackerAnchored =
                previewPlateTracker.anchor(
                        visibleOverlayItems,
                        result.sourceWidth,
                        result.sourceHeight
                );


        if (previewTrackerAnchored) {

            Map<Long, float[]> appearanceByTrack = new HashMap<>();
            for (PlateObservation observation : result.plateObservations) {
                if (observation.appearanceDescriptor != null) {
                    appearanceByTrack.put(
                            observation.trackId,
                            observation.appearanceDescriptor
                    );
                }
            }

            TargetSnapshot anchoredTarget =
                    targetStateMachine.onMtAnchor(
                            visibleOverlayItems,
                            appearanceByTrack
                    );
            if (pipeline != null) {
                pipeline.setTargetSnapshotIfCurrent(
                        anchoredTarget,
                        result.continuityStamp()
                );
            }
            if (SceneAnchorRefreshPolicy.shouldRefresh(
                    anchoredTarget.hasTrack(),
                    previewSceneAnchorGuard.hasAnchor(),
                    anchoredTarget.lockRevision,
                    previewSceneAnchorLockRevision
            )) {
                previewSceneAnchorPending = true;
                previewSceneAnchorLockRevision = anchoredTarget.lockRevision;
            }

        }
        if (collectionActive) collectCrops(result.plateObservations);
        if ("models_missing".equals(result.status) || "pipeline_error".equals(result.status)) {
            livePresentation.showState(
                    LivePresentationController.State.ERROR,
                    hudRoiLabel()
            );
            livePresentation.showTransient(result.message);
        } else {
            PlateObservation bestObservation = null;
            for (PlateObservation observation : result.plateObservations) {
                if (observation.text.isEmpty()) continue;
                if (bestObservation == null
                        || observation.confirmed && !bestObservation.confirmed
                        || observation.confirmed == bestObservation.confirmed
                        && observation.recognitionConfidence
                        > bestObservation.recognitionConfidence) {
                    bestObservation = observation;
                }
            }
            if (bestObservation != null) {
                livePresentation.showResult(
                        bestObservation.trackId,
                        bestObservation.text,
                        bestObservation.recognitionConfidence,
                        bestObservation.confirmed,
                        bestObservation.observations
                );
            } else if ("stabilizing".equals(result.status)
                    || !result.overlayItems.isEmpty()) {
                livePresentation.showState(
                        LivePresentationController.State.RECOGNIZING,
                        hudRoiLabel() + " · CEL"
                );
            } else {
                livePresentation.showState(
                        LivePresentationController.State.SEARCHING,
                        hudRoiLabel() + " · SZUKAM"
                );
            }
        }

        handleAutoZoomResult(result);
    }

    private boolean hasFreshAutoZoomTargetRecognition(PipelineResult result) {
        AutoZoomController.Sample target =
                autoZoomController.targetSample(autoZoomSamples(result));
        return target != null && target.recognitionExecuted;
    }

    private boolean hasAnyFreshRecognition(PipelineResult result) {
        for (PlateObservation observation : result.plateObservations) {
            if (observation.timing != null) return true;
        }
        return false;
    }

    private void rememberFreshAutoZoomRecognition(PipelineResult result) {
        AutoZoomController.Sample sample = autoZoomRecognitionSample(result);
        if (sample == null
                || !sample.recognitionExecuted) return;
        AutoZoomRecognitionMemory.Result selected =
                AutoZoomRecognitionMemory.choose(
                        autoZoomBestText,
                        autoZoomBestConfidence,
                        sample.text,
                        sample.recognitionConfidence,
                        sample.confirmed
                );
        autoZoomBestText = selected.text;
        autoZoomBestConfidence = selected.confidence;
    }

    private boolean hasRecognitionCompatibleWithZoomMemory(PipelineResult result) {
        AutoZoomController.Sample sample = autoZoomRecognitionSample(result);
        if (sample == null || !sample.recognitionExecuted) return false;
        if (autoZoomBestText.isEmpty()) return !sample.text.isEmpty();
        return autoZoomBestText.equals(sample.text)
                && (sample.confirmed
                || sample.recognitionConfidence + 0.08
                >= autoZoomBestConfidence);
    }

    private AutoZoomController.Sample autoZoomRecognitionSample(
            PipelineResult result
    ) {
        List<AutoZoomController.Sample> samples = autoZoomSamples(result);
        AutoZoomController.State state = autoZoomController.state();
        if (state == AutoZoomController.State.ZOOM_SETTLING
                || state == AutoZoomController.State.ZOOMED_RETRY
                || state == AutoZoomController.State.RETURNING) {
            return autoZoomController.targetSample(samples);
        }

        OverlayItem target = findAutoZoomTargetOverlay(result.overlayItems);
        if (target == null) return null;
        for (AutoZoomController.Sample sample : samples) {
            if (sample.trackId == target.trackId) return sample;
        }
        return null;
    }

    private void repositionAutoZoomTarget(PipelineResult result) {
        AutoZoomController.Sample sample =
                autoZoomController.targetSample(autoZoomSamples(result));
        if (sample != null) {
            updateAutoZoomTargetGeometry(sample.centerX, sample.centerY);
        }
    }

    private void toggleAutoZoom() {
        boolean enable = !autoZoomController.enabled();
        uiPreferences.edit()
                .putBoolean(KEY_AUTO_ZOOM_ENABLED, enable)
                .apply();

        if (enable) {
            autoZoomController.setEnabled(true);
            autoZoomController.resetSession();
            recordInfo(getString(R.string.auto_zoom_enabled_log));
            metricsCollector.recordEvent("auto_zoom_enabled", 0L, 0L, null);
        } else {
            autoZoomController.setEnabled(false);
            recordInfo(getString(R.string.auto_zoom_disabled_log));
            metricsCollector.recordEvent("auto_zoom_disabled", 0L, 0L, null);
            if (currentCameraZoomRatio > 1.01f) {
                requestAutoZoomReturn(null);
            }
        }
        updateAutoZoomButton();
        renderLiveHud();
    }

    private void showAutoZoomModeDialog() {
        String[] modes = getResources().getStringArray(R.array.auto_zoom_modes);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_zoom_mode_title)
                .setSingleChoiceItems(modes, 0, (dialog, which) -> {
                    if (which == 0) {
                        dialog.dismiss();
                        Toast.makeText(
                                this,
                                R.string.auto_zoom_mode_hold_full_selected,
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                this,
                                R.string.auto_zoom_mode_requires_offline_pipeline,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateAutoZoomButton() {
        if (autoZoomButton == null) return;
        boolean enabled = autoZoomController.enabled();
        String symbol;
        switch (autoZoomController.state()) {
            case ZOOM_SETTLING:
                symbol = getString(R.string.auto_zoom_symbol_focus);
                break;
            case ZOOMED_RETRY:
                symbol = getString(R.string.auto_zoom_symbol_recognize);
                break;
            case RETURNING:
                symbol = getString(R.string.auto_zoom_symbol_return);
                break;
            case READY:
            case DISABLED:
            default:
                symbol = getString(R.string.auto_zoom_symbol_ready);
                break;
        }
        autoZoomButton.setText(symbol);
        int color = ContextCompat.getColor(
                this,
                enabled ? R.color.alpr_success : R.color.alpr_text_muted
        );
        autoZoomButton.setIconTint(ColorStateList.valueOf(color));
        autoZoomButton.setStrokeColor(ColorStateList.valueOf(color));
        autoZoomButton.setContentDescription(
                getString(enabled
                        ? R.string.auto_zoom_disable
                        : R.string.auto_zoom_enable)
        );
        autoZoomButton.setEnabled(cameraStarted && !cameraTransformInProgress);
        if (autoZoomControl != null) {
            autoZoomControl.setVisibility(cameraStarted ? View.VISIBLE : View.GONE);
        }
        updateAutoZoomGlow(enabled);
    }

    private void updateAutoZoomGlow(boolean enabled) {
        if (autoZoomGlow == null) return;
        if (!enabled) {
            if (autoZoomGlowAnimator != null) autoZoomGlowAnimator.cancel();
            autoZoomGlow.setAlpha(0f);
            autoZoomGlow.setVisibility(View.GONE);
            return;
        }
        if (autoZoomGlowAnimator != null) autoZoomGlowAnimator.cancel();
        autoZoomGlow.setVisibility(View.VISIBLE);
        autoZoomGlow.setAlpha(0.42f);
    }

    private void handleAutoZoomResult(PipelineResult result) {
        if (!cameraStarted || !autoZoomController.enabled()) return;
        if (autoZoomController.state() == AutoZoomController.State.READY) {
            TargetSnapshot target = targetStateMachine.snapshot();
            if (target.state != TargetSnapshot.State.TRACKING
                    && target.state != TargetSnapshot.State.LOCKED) {
                return;
            }
        }
        AutoZoomController.Decision decision = autoZoomController.evaluate(
                autoZoomSamples(result),
                System.nanoTime()
        );
        if (decision.action == AutoZoomController.Action.REQUEST_ZOOM) {
            requestAutoZoom(decision);
        } else if (decision.action == AutoZoomController.Action.RETURN_NORMAL) {
            requestAutoZoomReturn(decision);
        }
    }

    private List<AutoZoomController.Sample> autoZoomSamples(PipelineResult result) {
        List<AutoZoomController.Sample> samples = new ArrayList<>();
        TargetSnapshot target = targetStateMachine.snapshot();
        for (PlateObservation observation : result.plateObservations) {
            OverlayItem plate = null;
            for (OverlayItem item : result.overlayItems) {
                if (item.kind == OverlayItem.Kind.PLATE
                        && item.trackId == observation.trackId) {
                    plate = item;
                    break;
                }
            }
            if (plate == null) continue;
            RectF bounds = plate.normalizedBounds;
            samples.add(new AutoZoomController.Sample(
                    observation.trackId,
                    bounds.centerX(),
                    bounds.centerY(),
                    bounds.width(),
                    observation.recognitionConfidence,
                    observation.confirmed,
                    observation.observations,
                    plate.normalizedKeypoints.size() == 4,
                    observation.freshMzAttempted,
                    observation.freshMzSuccessful,
                    observation.text,
                    target.state == TargetSnapshot.State.LOCKED
                            && target.trackId == observation.trackId
            ));
        }
        return samples;
    }

    private void requestAutoZoom(AutoZoomController.Decision decision) {
        if (cameraTransformInProgress || cameraController == null) return;
        if (cameraController.maximumZoomRatio() <= 1.01f) {
            autoZoomController.onRequestFailed();
            livePresentation.showTransient(getString(R.string.auto_zoom_unavailable));
            recordWarning(getString(R.string.auto_zoom_unavailable));
            return;
        }

        TargetSnapshot trackedTarget = targetStateMachine.snapshot();
        boolean useTrackedGeometry = trackedTarget.trackId == decision.trackId
                && trackedTarget.hasTrack();
        autoZoomTargetSceneX = useTrackedGeometry
                ? trackedTarget.normalizedBounds.centerX()
                : decision.centerX;
        autoZoomTargetSceneY = useTrackedGeometry
                ? trackedTarget.normalizedBounds.centerY()
                : decision.centerY;

        final float requestedZoomRatio = autoZoomRatioKeepingTargetVisible(decision);
        autoZoomBestText = autoZoomController.targetText();
        autoZoomBestConfidence = decision.beforeConfidence;
        capturePreZoomSceneAnchor();
        freezeCurrentOverlayAsMemory();
        showAutoZoomTarget(autoZoomTargetSceneX, autoZoomTargetSceneY);
        livePresentation.showTransient(getString(
                R.string.auto_zoom_request_status,
                requestedZoomRatio
        ));
        recordInfo(String.format(
                Locale.ROOT,
                "Auto zoom request track=%d reason=%s before=%.3f ratio=%.2f",
                decision.trackId,
                decision.reason,
                decision.beforeConfidence,
                requestedZoomRatio
        ));
        try {
            JSONObject event = new JSONObject();
            event.put("reason", decision.reason);
            event.put("confidence", decision.beforeConfidence);
            event.put("zoom_ratio", requestedZoomRatio);
            event.put("target_center_x", autoZoomTargetSceneX);
            event.put("target_center_y", autoZoomTargetSceneY);
            metricsCollector.recordEvent(
                    "auto_zoom_started",
                    0L,
                    decision.trackId,
                    event
            );
        } catch (Exception ignored) {
            // Zdarzenie diagnostyczne nie może blokować zoomu.
        }

        final long scheduledSceneGeneration = uiSceneGeneration.get();
        pendingAutoZoomStartRunnable = () -> {
            pendingAutoZoomStartRunnable = null;
            if (!cameraStarted
                    || scheduledSceneGeneration != uiSceneGeneration.get()
                    || autoZoomController.state()
                    != AutoZoomController.State.ZOOM_SETTLING
                    || cameraTransformInProgress) {
                return;
            }
            startAutoZoomTransform(decision, requestedZoomRatio);
        };

        /*
         * setItems() i start zoomu w jednym callbacku UI nie dają Androidowi
         * szansy narysować ramki. Najpierw czekamy na klatkę renderowania, a
         * następnie utrzymujemy overlay jeszcze przez krótki, widoczny moment.
         */
        overlayView.postOnAnimation(() -> {
            Runnable pending = pendingAutoZoomStartRunnable;
            if (pending != null) {
                autoZoomHandler.postDelayed(pending, PRE_ZOOM_OVERLAY_HOLD_MS);
            }
        });
    }

    private float autoZoomRatioKeepingTargetVisible(
            AutoZoomController.Decision decision
    ) {
        OverlayItem target = findAutoZoomTargetOverlay(latestPipelinePlateItems);
        TargetSnapshot trackedTarget = targetStateMachine.snapshot();
        RectF bounds;
        if (trackedTarget.trackId == decision.trackId && trackedTarget.hasTrack()) {
            bounds = trackedTarget.normalizedBounds;
        } else if (target != null) {
            bounds = target.normalizedBounds;
        } else {
            return AutoZoomController.REQUESTED_ZOOM_RATIO;
        }
        RectF visible = overlayView.normalizedVisibleBounds();
        return CameraController.centeredZoomKeepingBoundsVisible(
                AutoZoomController.REQUESTED_ZOOM_RATIO,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                visible.left,
                visible.top,
                visible.right,
                visible.bottom,
                0.05f
        );
    }

    private void startAutoZoomTransform(
            AutoZoomController.Decision decision,
            float requestedZoomRatio
    ) {
        if (!cameraStarted
                || cameraController == null
                || cameraTransformInProgress
                || autoZoomController.state()
                != AutoZoomController.State.ZOOM_SETTLING) {
            return;
        }

        beginControlledCameraTransform();

        android.graphics.PointF focusPoint =
                overlayView.normalizedToViewPoint(
                        autoZoomTargetSceneX,
                        autoZoomTargetSceneY
                );
        float focusX = overlayView.getWidth() <= 0
                ? autoZoomTargetSceneX
                : Math.max(0f, Math.min(1f,
                focusPoint.x / overlayView.getWidth()));
        float focusY = overlayView.getHeight() <= 0
                ? autoZoomTargetSceneY
                : Math.max(0f, Math.min(1f,
                focusPoint.y / overlayView.getHeight()));

        cameraController.zoomAndFocus(
                requestedZoomRatio,
                focusX,
                focusY,
                new CameraController.ControlCallback() {
                    @Override
                    public void onProgress(float appliedZoomRatio) {
                        if (!cameraStarted) return;
                        currentCameraZoomRatio = appliedZoomRatio;
                        transformMemoryOverlay(appliedZoomRatio);
                        showAutoZoomTarget(
                                CameraController.zoomedCoordinate(
                                        autoZoomTargetSceneX,
                                        appliedZoomRatio
                                ),
                                CameraController.zoomedCoordinate(
                                        autoZoomTargetSceneY,
                                        appliedZoomRatio
                                )
                        );
                    }

                    @Override
                    public void onSuccess(float appliedZoomRatio) {
                        if (!cameraStarted) return;
                        currentCameraZoomRatio = appliedZoomRatio;
                        float zoomedCenterX = CameraController.zoomedCoordinate(
                                autoZoomTargetSceneX,
                                appliedZoomRatio
                        );
                        float zoomedCenterY = CameraController.zoomedCoordinate(
                                autoZoomTargetSceneY,
                                appliedZoomRatio
                        );
                        autoZoomController.onZoomApplied(zoomedCenterX, zoomedCenterY);
                        transformMemoryOverlay(appliedZoomRatio);
                        showAutoZoomTarget(
                                zoomedCenterX,
                                zoomedCenterY
                        );
                        updateAutoZoomAnalysisRoi(new RectF(
                                Math.max(0f, zoomedCenterX - 0.06f),
                                Math.max(0f, zoomedCenterY - 0.025f),
                                Math.min(1f, zoomedCenterX + 0.06f),
                                Math.min(1f, zoomedCenterY + 0.025f)
                        ));
                        updateAutoZoomTargetFromOverlayItems(
                                latestPipelinePlateItems
                        );
                        autoZoomHandler.postDelayed(() -> {
                            if (!cameraStarted) return;
                            if (abortAutoZoomAfterTransform) {
                                abortAutoZoomAfterTransform = false;
                                finishControlledCameraTransform(false);
                                requestAutoZoomReturn(null);
                                return;
                            }
                            autoZoomController.onZoomSettled(System.nanoTime());
                            try {
                                JSONObject event = new JSONObject();
                                event.put("zoom_ratio", currentCameraZoomRatio);
                                metricsCollector.recordEvent(
                                        "lock_acquired",
                                        0L,
                                        decision.trackId,
                                        event
                                );
                                metricsCollector.recordEvent(
                                        "mz_retry_after_zoom",
                                        0L,
                                        decision.trackId,
                                        event
                                );
                            } catch (Exception ignored) {
                                // Telemetria nie wpływa na sterowanie kamerą.
                            }
                            finishControlledCameraTransform(true);
                            livePresentation.showTransient(getString(
                                    R.string.auto_zoom_retry_status,
                                    currentCameraZoomRatio
                            ));
                        }, AutoZoomController.SETTLING_MILLIS);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!cameraStarted) return;
                        abortAutoZoomAfterTransform = false;
                        autoZoomController.onRequestFailed();
                        metricsCollector.recordEvent(
                                "lock_lost",
                                0L,
                                decision.trackId,
                                null
                        );
                        currentCameraZoomRatio = 1f;
                        finishControlledCameraTransform(false);
                        recordError("Nie udało się ustawić auto zoom", error);
                    }
                }
        );
    }

    private void requestAutoZoomReturn(AutoZoomController.Decision decision) {
        if (cameraTransformInProgress || cameraController == null) return;
        cancelPendingAutoZoomStart();
        pipeline.clearAutoZoomTargetRoi();
        if (decision != null
                && (!"timeout".equals(decision.reason)
                || hasPresentedFreshMzForAutoZoomTarget())) {
            freezeZoomResultForReturn();
        } else if (decision != null) {
            autoZoomMemoryVisible = !autoZoomBaseMemoryOverlayItems.isEmpty();
            transformMemoryOverlay(currentCameraZoomRatio);
        }
        autoZoomController.requestReturn();
        try {
            JSONObject event = new JSONObject();
            event.put("zoom_ratio", currentCameraZoomRatio);
            if (decision != null) event.put("reason", decision.reason);
            metricsCollector.recordEvent(
                    "auto_zoom_return_started",
                    0L,
                    decision == null ? autoZoomController.targetTrackId() : decision.trackId,
                    event
            );
        } catch (Exception ignored) {
            // Telemetria nie wpływa na sterowanie kamerą.
        }
        beginControlledCameraTransform();
        livePresentation.showTransient(getString(R.string.auto_zoom_return_status));

        if (decision != null) {
            recordInfo(String.format(
                    Locale.ROOT,
                    "Auto zoom result track=%d reason=%s confidence=%.3f->%.3f",
                    decision.trackId,
                    decision.reason,
                    decision.beforeConfidence,
                    decision.afterConfidence
            ));
        }

        cameraController.setZoomRatio(
                1f,
                new CameraController.ControlCallback() {
                    @Override
                    public void onProgress(float appliedZoomRatio) {
                        if (!cameraStarted) return;
                        currentCameraZoomRatio = appliedZoomRatio;
                        transformMemoryOverlay(appliedZoomRatio);
                        showAutoZoomTarget(
                                CameraController.zoomedCoordinate(
                                        autoZoomTargetSceneX,
                                        appliedZoomRatio
                                ),
                                CameraController.zoomedCoordinate(
                                        autoZoomTargetSceneY,
                                        appliedZoomRatio
                                )
                        );
                    }

                    @Override
                    public void onSuccess(float appliedZoomRatio) {
                        if (!cameraStarted) return;
                        currentCameraZoomRatio = appliedZoomRatio;
                        autoZoomHandler.postDelayed(() -> {
                            autoZoomController.onReturnSettled();
                            if (resetAutoZoomSessionAfterReturn) {
                                resetAutoZoomSessionAfterReturn = false;
                                autoZoomController.resetSession();
                            }
                            finishControlledCameraTransform(false);
                            try {
                                JSONObject event = new JSONObject();
                                event.put("zoom_ratio", currentCameraZoomRatio);
                                metricsCollector.recordEvent(
                                        "zoom_finished",
                                        0L,
                                        autoZoomController.targetTrackId(),
                                        event
                                );
                            } catch (Exception ignored) {
                                // Telemetria nie wpływa na sterowanie kamerą.
                            }
                        }, AutoZoomController.SETTLING_MILLIS);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!cameraStarted) return;
                        autoZoomController.onRequestFailed();
                        finishControlledCameraTransform(false);
                        recordError("Nie udało się przywrócić zoom 1.0", error);
                    }
                }
        );
    }

    private boolean hasPresentedFreshMzForAutoZoomTarget() {
        long targetTrackId = autoZoomController.targetTrackId();
        for (OverlayItem item : latestPipelinePlateItems) {
            if (item.trackId == targetTrackId
                    && !item.carriedPrediction
                    && item.label.contains("· MZ")) {
                return true;
            }
        }
        return false;
    }

    private void beginControlledCameraTransform() {
        cameraTransformStartZoomRatio = currentCameraZoomRatio;
        cameraTransformInProgress = true;
        pipeline.setCameraTransformInProgress(true);
        uiCameraTransformGeneration.incrementAndGet();
        previewSceneDetector.reset();
        previewSceneAnchorGuard.reset();
        previewPlateTracker.reset();
        overlayTracker.reset();
        liveHudAwaitingFreshResult = true;
        updateAutoZoomButton();
    }

    private void finishControlledCameraTransform(boolean keepTarget) {
        if (!keepTarget) hideAutoZoomTarget();
        if (!keepTarget) pipeline.clearAutoZoomTargetRoi();
        cameraTransformInProgress = false;
        pipeline.finishCameraTransform(
                cameraTransformStartZoomRatio,
                currentCameraZoomRatio
        );
        uiCameraTransformGeneration.incrementAndGet();
        previewSceneDetector.reset();
        previewSceneAnchorGuard.reset();
        previewPlateTracker.reset();
        overlayTracker.reset();
        previewSceneAnchorPending = false;
        liveHudAwaitingFreshResult = true;
        if (keepTarget) {
            autoZoomZoomedAnchorPending = true;
            autoZoomZoomedAnchorValid = false;
            autoZoomReturnValidationPending = false;
        } else {
            autoZoomZoomedAnchorPending = false;
            autoZoomZoomedAnchorValid = false;
            autoZoomZoomedSceneAnchorGuard.reset();
            autoZoomReturnValidationPending = autoZoomPreZoomAnchorValid;
        }
        if (!keepTarget && autoZoomReturnValidationPending) {
            validateReturnedAutoZoomSceneNow();
        }
        updateAutoZoomButton();
        renderLiveHud();
    }

    private boolean isAutoZoomHoldingMemory() {
        /*
         * Maskujemy detektor sceny podczas faktycznej zmiany zoomu oraz przez
         * krótki etap, w którym ramki są już pokazane, ale animacja jeszcze nie
         * ruszyła. Zmiana tekstu statusu może wtedy przebudować PreviewView i nie
         * może zostać pomylona ze zmianą fizycznej sceny.
         *
         * Po ustabilizowaniu obrazu ZOOMED_RETRY jest normalnym kadrem i musi
         * natychmiast reagować na ruch telefonu, zniknięcie tablicy lub nową scenę.
         */
        return cameraTransformInProgress
                || pendingAutoZoomStartRunnable != null;
    }

    private void invalidateAutoZoomForPhysicalCameraMotion() {
        if (abortAutoZoomAfterTransform) return;
        abortAutoZoomAfterTransform = true;
        autoZoomController.requestReturn();
        clearAutoZoomRecognitionMemory();
        if (pipeline != null) {
            refreshPipelineCameraMotionEvidence();
            SceneTransitionDecision decision = pipeline.onPreviewSceneEvidence(
                    System.nanoTime(),
                    false,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    false,
                    true
            );
            renderPreviewContinuityDecision(
                    decision,
                    0f,
                    0f,
                    currentOverlayItems()
            );
        }
        livePresentation.showState(
                LivePresentationController.State.RECOVERING,
                hudRoiLabel() + " · RUCH KAMERY"
        );
        livePresentation.showTransient(getString(R.string.scene_change_analyzing));
        recordInfo("Auto zoom przerwany: telefon zmienił kadr podczas zbliżenia");
    }

    private void refreshPipelineCameraMotionEvidence() {
        AlprPipeline activePipeline = pipeline;
        CameraMotionMonitor activeMonitor = cameraMotionMonitor;
        if (activePipeline == null || activeMonitor == null) return;
        activePipeline.setCameraMotionEvidence(
                activeMonitor.isGyroscopeAvailable(),
                activeMonitor.isMoving(),
                activeMonitor.isRapidMotion(),
                activeMonitor.magnitude()
        );
    }

    private void freezeCurrentOverlayAsMemory() {
        if (latestOverlaySourceWidth <= 0 || latestOverlaySourceHeight <= 0) return;
        List<OverlayItem> frozen = memoryOverlayItems(
                overlayItemsWithAutoZoomRecognitionMemory(currentOverlayItems()),
                false
        );
        autoZoomBaseMemoryOverlayItems = java.util.Collections.unmodifiableList(frozen);
        autoZoomMemoryVisible = containsPlate(frozen);
        applyVisibleOverlay(frozen, latestOverlaySourceWidth, latestOverlaySourceHeight);
        livePresentation.showState(
                LivePresentationController.State.RECOGNIZING,
                hudRoiLabel() + " · AZ"
        );
    }

    private void transformMemoryOverlay(float zoomRatio) {
        if (!autoZoomMemoryVisible
                || latestOverlaySourceWidth <= 0
                || latestOverlaySourceHeight <= 0) return;
        List<OverlayItem> transformed = transformOverlayItems(
                autoZoomBaseMemoryOverlayItems,
                Math.max(0.1f, zoomRatio),
                true
        );
        applyVisibleOverlay(
                transformed,
                latestOverlaySourceWidth,
                latestOverlaySourceHeight
        );
    }

    private void freezeZoomResultForReturn() {
        List<OverlayItem> visible = currentOverlayItems();
        if (!containsPlate(visible)) return;
        float inverseRatio = 1f / Math.max(1f, currentCameraZoomRatio);
        List<OverlayItem> sceneItems = transformOverlayItems(
                memoryOverlayItems(visible, true),
                inverseRatio,
                true
        );
        autoZoomBaseMemoryOverlayItems =
                java.util.Collections.unmodifiableList(sceneItems);
        autoZoomMemoryVisible = true;
        transformMemoryOverlay(currentCameraZoomRatio);
    }

    private void presentZoomedMtStage(
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight,
            ContinuityStamp sourceStamp
    ) {
        if (overlayItems == null
                || sourceWidth <= 0
                || sourceHeight <= 0
                || pipeline == null
                || !pipeline.isCurrentContinuityStamp(sourceStamp)
                || !containsPlate(overlayItems)) return;

        latestOverlaySourceWidth = sourceWidth;
        latestOverlaySourceHeight = sourceHeight;
        List<OverlayItem> visibleItems =
                overlayItemsWithAutoZoomRecognitionMemory(overlayItems);
        applyVisibleOverlay(visibleItems, sourceWidth, sourceHeight);
        updateAutoZoomTargetFromOverlayItems(visibleItems);

        float inverseRatio = 1f / Math.max(1f, currentCameraZoomRatio);
        autoZoomBaseMemoryOverlayItems = java.util.Collections.unmodifiableList(
                transformOverlayItems(
                        memoryOverlayItems(visibleItems, true),
                        inverseRatio,
                        true
                )
        );
        autoZoomMemoryVisible = true;

        if (previewPlateTracker.anchor(visibleItems, sourceWidth, sourceHeight)) {
            if (pipeline != null) {
                pipeline.setTargetSnapshotIfCurrent(
                        targetStateMachine.onMtAnchor(visibleItems),
                        sourceStamp
                );
            }
            previewSceneAnchorPending = true;
        }
        if (!autoZoomZoomedAnchorValid) {
            autoZoomZoomedAnchorPending = true;
        }
        livePresentation.showState(
                LivePresentationController.State.RECOGNIZING,
                hudRoiLabel() + " · AZ"
        );
    }

    private List<OverlayItem> overlayItemsWithAutoZoomRecognitionMemory(
            List<OverlayItem> items
    ) {
        if (items == null || items.isEmpty() || autoZoomBestText.isEmpty()) {
            return items == null
                    ? java.util.Collections.emptyList()
                    : items;
        }

        OverlayItem target = findAutoZoomTargetOverlay(items);
        if (target == null) return items;

        List<OverlayItem> result = new ArrayList<>(items.size());
        String rememberedLabel = String.format(
                Locale.ROOT,
                "%s · pamięć MZ %.0f%%",
                autoZoomBestText,
                autoZoomBestConfidence * 100.0
        );
        for (OverlayItem item : items) {
            if (item == target) {
                result.add(new OverlayItem(
                        item.kind,
                        item.normalizedBounds,
                        item.normalizedKeypoints,
                        rememberedLabel,
                        item.trackId,
                        item.carriedPrediction
                ));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private List<OverlayItem> currentOverlayItems() {
        List<OverlayItem> visible = new ArrayList<>(latestDiagnosticOverlayItems);
        visible.addAll(latestPipelinePlateItems);
        return visible;
    }

    private void applyVisibleOverlay(
            List<OverlayItem> items,
            int sourceWidth,
            int sourceHeight
    ) {
        List<OverlayItem> diagnostics = new ArrayList<>();
        List<OverlayItem> plates = new ArrayList<>();
        for (OverlayItem item : items) {
            if (item.kind == OverlayItem.Kind.PLATE) {
                plates.add(item);
            } else {
                diagnostics.add(item);
            }
        }
        latestDiagnosticOverlayItems =
                java.util.Collections.unmodifiableList(diagnostics);
        latestPipelinePlateItems =
                java.util.Collections.unmodifiableList(plates);
        overlayView.setItems(items, sourceWidth, sourceHeight);
    }

    private static List<OverlayItem> memoryOverlayItems(
            List<OverlayItem> items,
            boolean zoomResult
    ) {
        List<OverlayItem> memory = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            String label = item.label;
            if (item.kind == OverlayItem.Kind.PLATE) {
                if (zoomResult) {
                    label = zoomMemoryLabel(label);
                } else if (!label.contains("pamięć")) {
                    label = label + " · pamięć";
                }
            }
            memory.add(new OverlayItem(
                    item.kind,
                    item.normalizedBounds,
                    item.normalizedKeypoints,
                    label,
                    item.trackId,
                    item.kind == OverlayItem.Kind.PLATE || item.carriedPrediction
            ));
        }
        return memory;
    }

    private static List<OverlayItem> transformOverlayItems(
            List<OverlayItem> items,
            float scaleRatio,
            boolean preserveAsMemory
    ) {
        List<OverlayItem> transformed = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            RectF source = item.normalizedBounds;
            RectF bounds = new RectF(
                    CameraController.scaledCoordinate(source.left, scaleRatio),
                    CameraController.scaledCoordinate(source.top, scaleRatio),
                    CameraController.scaledCoordinate(source.right, scaleRatio),
                    CameraController.scaledCoordinate(source.bottom, scaleRatio)
            );
            List<android.graphics.PointF> points = new ArrayList<>();
            for (android.graphics.PointF point : item.normalizedKeypoints) {
                points.add(new android.graphics.PointF(
                        CameraController.scaledCoordinate(point.x, scaleRatio),
                        CameraController.scaledCoordinate(point.y, scaleRatio)
                ));
            }
            transformed.add(new OverlayItem(
                    item.kind,
                    bounds,
                    points,
                    item.label,
                    item.trackId,
                    preserveAsMemory
                            ? item.kind == OverlayItem.Kind.PLATE
                            || item.carriedPrediction
                            : item.carriedPrediction
            ));
        }
        return transformed;
    }

    private static boolean containsPlate(List<OverlayItem> items) {
        for (OverlayItem item : items) {
            if (item.kind == OverlayItem.Kind.PLATE) return true;
        }
        return false;
    }

    private static List<OverlayItem> activeTrackingOverlayItems(
            List<OverlayItem> items
    ) {
        List<OverlayItem> active = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            active.add(new OverlayItem(
                    item.kind,
                    item.normalizedBounds,
                    item.normalizedKeypoints,
                    item.label,
                    item.trackId,
                    item.kind == OverlayItem.Kind.PLATE
                            ? false
                            : item.carriedPrediction
            ));
        }
        return active;
    }

    private static String zoomMemoryLabel(String label) {
        String value = label == null ? "" : label.trim();
        int separator = value.lastIndexOf(' ');
        if (separator > 0 && value.substring(separator + 1).matches("\\d{1,3}%")) {
            return value.substring(0, separator) + " · zoom "
                    + value.substring(separator + 1);
        }
        return value + " · zoom";
    }

    private void clearAutoZoomRecognitionMemory() {
        cancelPendingAutoZoomStart();
        if (pipeline != null) pipeline.clearAutoZoomTargetRoi();
        autoZoomMemoryVisible = false;
        autoZoomBaseMemoryOverlayItems = java.util.Collections.emptyList();
        autoZoomBestText = "";
        autoZoomBestConfidence = 0.0;
        autoZoomPreZoomSceneAnchorGuard.reset();
        autoZoomZoomedSceneAnchorGuard.reset();
        autoZoomPreZoomAnchorValid = false;
        autoZoomZoomedAnchorPending = false;
        autoZoomZoomedAnchorValid = false;
        autoZoomReturnValidationPending = false;
        autoZoomDynamicFrameGraceCount = 0;
    }

    private void cancelPendingAutoZoomStart() {
        if (pendingAutoZoomStartRunnable != null) {
            autoZoomHandler.removeCallbacks(pendingAutoZoomStartRunnable);
            pendingAutoZoomStartRunnable = null;
        }
    }

    private void capturePreZoomSceneAnchor() {
        autoZoomPreZoomSceneAnchorGuard.reset();
        autoZoomPreZoomAnchorValid = false;
        Bitmap bitmap = null;
        try {
            bitmap = previewView == null ? null : previewView.getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                autoZoomPreZoomSceneAnchorGuard.anchor(bitmap);
                autoZoomPreZoomAnchorValid = true;
            }
        } catch (RuntimeException ignored) {
            // PreviewView może jeszcze nie mieć gotowej klatki.
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void validateReturnedAutoZoomScene(Bitmap previewBitmap) {
        autoZoomReturnValidationPending = false;
        if (!autoZoomPreZoomAnchorValid) return;

        SceneAnchorGuard.Result returned =
                autoZoomPreZoomSceneAnchorGuard.evaluate(previewBitmap);
        if (returned.changed) {
            recordInfo("Auto zoom: po powrocie wykryto inną scenę");
            if (pipeline == null) return;
            SceneTransitionDecision decision = pipeline.onPreviewSceneEvidence(
                    System.nanoTime(),
                    true,
                    returned.score,
                    returned.changedFraction,
                    0f,
                    returned.score,
                    returned.changedFraction,
                    false,
                    false
            );
            renderPreviewContinuityDecision(
                    decision,
                    returned.score,
                    returned.changedFraction,
                    currentOverlayItems()
            );
            if (decision.action != SceneTransitionAction.NONE
                    || decision.nextState
                    != com.example.alpr_v1.continuity.SceneContinuityState.STABLE) {
                return;
            }
        }

        /* Ta sama scena: zachowujemy wynik i jedynie ponownie kotwiczymy UI. */
        previewSceneDetector.reset();
        previewSceneDetector.update(previewBitmap);
        previewSceneAnchorGuard.anchor(previewBitmap);
        previewSceneAnchorPending = false;

        if (autoZoomMemoryVisible) {
            ContinuityStamp sourceStamp = currentContinuityStamp(System.nanoTime());
            List<OverlayItem> returnedOverlay = transformOverlayItems(
                    autoZoomBaseMemoryOverlayItems,
                    1f,
                    true
            );
            applyVisibleOverlay(
                    returnedOverlay,
                    latestOverlaySourceWidth,
                    latestOverlaySourceHeight
            );
            if (previewPlateTracker.anchor(
                    activeTrackingOverlayItems(returnedOverlay),
                    latestOverlaySourceWidth,
                    latestOverlaySourceHeight
            )) {
                if (pipeline != null) {
                    pipeline.setTargetSnapshotIfCurrent(
                            targetStateMachine.onMtAnchor(
                                    activeTrackingOverlayItems(returnedOverlay)
                            ),
                            sourceStamp
                    );
                }
                previewPlateTracker.update(previewBitmap);
            }
        }

        liveHudAwaitingFreshResult = false;
        livePresentation.showTransient(getString(R.string.auto_zoom_same_scene_status));
        renderLiveHud();
        recordInfo("Auto zoom: powrót do tej samej sceny, zachowano ramki i wynik");
    }

    private void validateReturnedAutoZoomSceneNow() {
        Bitmap bitmap = null;
        try {
            bitmap = previewView == null ? null : previewView.getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                validateReturnedAutoZoomScene(bitmap);
            }
        } catch (RuntimeException ignored) {
            // Lekki monitor Preview ponowi walidację na najbliższej klatce.
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void updateAutoZoomTargetFromOverlayItems(
            List<OverlayItem> items
    ) {
        OverlayItem target = findAutoZoomTargetOverlay(items);
        if (target == null) return;
        updateAutoZoomTargetGeometry(
                target.normalizedBounds.centerX(),
                target.normalizedBounds.centerY()
        );
        updateAutoZoomAnalysisRoi(target.normalizedBounds);
    }

    private OverlayItem findAutoZoomTargetOverlay(List<OverlayItem> items) {
        if (items == null || items.isEmpty()) return null;
        long targetTrackId = autoZoomController.targetTrackId();
        for (OverlayItem item : items) {
            if (item.kind == OverlayItem.Kind.PLATE
                    && item.trackId == targetTrackId) {
                return item;
            }
        }

        float expectedX = CameraController.zoomedCoordinate(
                autoZoomTargetSceneX,
                currentCameraZoomRatio
        );
        float expectedY = CameraController.zoomedCoordinate(
                autoZoomTargetSceneY,
                currentCameraZoomRatio
        );
        OverlayItem nearest = null;
        float bestDistance = Float.MAX_VALUE;
        for (OverlayItem item : items) {
            if (item.kind != OverlayItem.Kind.PLATE) continue;
            float dx = item.normalizedBounds.centerX() - expectedX;
            float dy = item.normalizedBounds.centerY() - expectedY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = item;
            }
        }
        return bestDistance <= 0.20f * 0.20f ? nearest : null;
    }

    private void presentTrackedPreviewOverlay(List<OverlayItem> trackedItems) {
        List<OverlayItem> trackedOverlay = mergePreviewOverlay(trackedItems);
        overlayView.setFocusedTrackId(targetStateMachine.snapshot().trackId);
        overlayView.setItems(
                trackedOverlay,
                previewPlateTracker.sourceWidth(),
                previewPlateTracker.sourceHeight()
        );
        if (autoZoomController.state()
                == AutoZoomController.State.ZOOMED_RETRY) {
            updateAutoZoomTargetFromOverlayItems(trackedOverlay);
        }
    }

    private void updateAutoZoomAnalysisRoi(RectF plateBounds) {
        if (pipeline == null
                || currentCameraZoomRatio <= 1.01f
                || plateBounds == null) return;

        pipeline.setAutoZoomTargetLock(
                autoZoomController.targetTrackId(),
                plateBounds.left,
                plateBounds.top,
                plateBounds.right,
                plateBounds.bottom
        );
    }

    private void updateAutoZoomTargetGeometry(
            float zoomedCenterX,
            float zoomedCenterY
    ) {
        float inverseRatio = 1f / Math.max(1f, currentCameraZoomRatio);
        autoZoomTargetSceneX = CameraController.scaledCoordinate(
                zoomedCenterX,
                inverseRatio
        );
        autoZoomTargetSceneY = CameraController.scaledCoordinate(
                zoomedCenterY,
                inverseRatio
        );
        autoZoomController.onZoomApplied(zoomedCenterX, zoomedCenterY);
        showAutoZoomTarget(zoomedCenterX, zoomedCenterY);
    }

    private void showAutoZoomTarget(float normalizedX, float normalizedY) {
        if (autoZoomTarget == null) return;
        android.graphics.PointF point =
                overlayView.normalizedToViewPoint(normalizedX, normalizedY);
        float minimumX = overlayView.getX();
        float minimumY = overlayView.getY();
        float maximumX = minimumX + overlayView.getWidth()
                - autoZoomTarget.getWidth();
        float maximumY = minimumY + overlayView.getHeight()
                - autoZoomTarget.getHeight();
        float x = minimumX + point.x - autoZoomTarget.getWidth() / 2f;
        float y = minimumY + point.y - autoZoomTarget.getHeight() / 2f;
        autoZoomTarget.setX(Math.max(minimumX, Math.min(maximumX, x)));
        autoZoomTarget.setY(Math.max(minimumY, Math.min(maximumY, y)));
        if (autoZoomTargetAnimator != null) autoZoomTargetAnimator.cancel();
        autoZoomTarget.setAlpha(1f);
        autoZoomTarget.setVisibility(View.VISIBLE);
    }

    private void hideAutoZoomTarget() {
        if (autoZoomTargetAnimator != null) autoZoomTargetAnimator.cancel();
        if (autoZoomTarget != null) {
            autoZoomTarget.setAlpha(1f);
            autoZoomTarget.setVisibility(View.GONE);
        }
    }

    private void resetAutoZoomForStoppedCamera() {
        autoZoomHandler.removeCallbacksAndMessages(null);
        if (autoZoomGlowAnimator != null) autoZoomGlowAnimator.cancel();
        cameraTransformInProgress = false;
        clearAutoZoomRecognitionMemory();
        currentCameraZoomRatio = 1f;
        cameraTransformStartZoomRatio = 1f;
        autoZoomTargetSceneX = 0.5f;
        autoZoomTargetSceneY = 0.5f;
        abortAutoZoomAfterTransform = false;
        resetAutoZoomSessionAfterReturn = false;
        autoZoomController.resetSession();
        if (pipeline != null) pipeline.finishCameraTransform();
        if (liveHudRow != null) liveHudRow.setVisibility(View.GONE);
        if (autoZoomControl != null) autoZoomControl.setVisibility(View.GONE);
        hideAutoZoomTarget();
        updateAutoZoomButton();
    }

    private void configureCaptureCollection() {

        cropLimitSetting =
                CropCapacityPolicy.normalizeSetting(
                        uiPreferences.getString(
                                "crop_limit",
                                CropCapacityPolicy.AUTO
                        )
                );


        resolvedCropLimit =
                CropCapacityPolicy.resolve(
                        cropLimitSetting,
                        Runtime.getRuntime().maxMemory(),
                        deviceProfile.lowRamDevice
                );


        String directory =
                uiPreferences.getString(
                        "capture_directory_uri",
                        ""
                );


        if (!directory.isEmpty()) {

            try {

                captureDirectoryUri =
                        Uri.parse(
                                directory
                        );

            } catch (RuntimeException ignored) {

                captureDirectoryUri =
                        null;
            }
        }


        /*
         * Adapter istnieje niezależnie od tego,
         * czy Bottom Sheet jest aktualnie otwarty.
         */
        captureAdapter =
                new PlateCaptureAdapter(
                        new PlateCaptureAdapter.SelectionListener() {

                            @Override
                            public void onSelectionChanged(
                                    CapturedPlateItem item,
                                    boolean selected
                            ) {

                                onCropSelectionChanged(
                                        item,
                                        selected
                                );
                            }


                            @Override
                            public void onVerificationChanged(
                                    CapturedPlateItem item,
                                    CapturedPlateItem.VerificationStatus status
                            ) {

                                applyHumanVerification(
                                        item,
                                        status,
                                        ""
                                );
                            }


                            @Override
                            public void onCorrectionRequested(
                                    CapturedPlateItem item
                            ) {

                                showCorrectionDialog(
                                        item
                                );
                            }
                        }
                );


        collectionToggle.setOnClickListener(
                view ->
                        toggleCollection()
        );


        galleryOpenButton.setOnClickListener(
                view ->
                        showGalleryBottomSheet()
        );


        /*
         * Nowa instancja MainActivity nie może odziedziczyć
         * aktywnego kolektora, skoro kamera jest zatrzymana.
         *
         * Historia cropów i sessionId pozostają.
         */
        if (!cameraStarted
                && collectionActive) {

            collectionActive =
                    false;


            metricsCollector.setCropCollectionActive(
                    false
            );


            captureGalleryState.retainSession(
                    false,
                    collectionSessionId,
                    collectionSessionStartedElapsedNanos,
                    collectionSequence
            );
        }


        metricsCollector.setCropCapacity(
                resolvedCropLimit
        );


        renderCapturedCrops();
    }

    private void showGalleryBottomSheet() {

        /*
         * Nie tworzymy drugiej instancji,
         * jeżeli galeria już jest otwarta.
         */
        if (galleryBottomSheet != null
                && galleryBottomSheet.isShowing()) {

            return;
        }


        View content =
                getLayoutInflater().inflate(
                        R.layout.bottom_sheet_gallery,
                        (ViewGroup) findViewById(android.R.id.content),
                        false
                );


        BottomSheetDialog dialog =
                new BottomSheetDialog(
                        this
                );


        dialog.setContentView(
                content
        );


        galleryBottomSheet =
                dialog;


        galleryResultsList =
                content.findViewById(
                        R.id.gallery_sheet_list
                );


        galleryResultsEmpty =
                content.findViewById(
                        R.id.gallery_sheet_empty
                );


        gallerySheetCount =
                content.findViewById(
                        R.id.gallery_sheet_count
                );


        galleryCollectionStats =
                content.findViewById(
                        R.id.gallery_sheet_collection_stats
                );


        gallerySelectAllCropsToggle =
                content.findViewById(
                        R.id.gallery_sheet_select_all
                );


        gallerySaveSelectedCropsButton =
                content.findViewById(
                        R.id.gallery_sheet_save_selected
                );


        galleryResultsList.setLayoutManager(
                new LinearLayoutManager(
                        this,
                        RecyclerView.VERTICAL,
                        false
                )
        );


        galleryResultsList.setAdapter(
                captureAdapter
        );


        gallerySelectAllCropsToggle.setOnCheckedChangeListener(
                (button, checked) ->
                        selectAllCrops(
                                checked
                        )
        );


        gallerySaveSelectedCropsButton.setOnClickListener(
                view ->
                        saveSelectedCrops()
        );


        /*
         * Po zamknięciu Bottom Sheeta usuwamy wyłącznie
         * referencje do jego widoków.
         *
         * Adapter, cropy i sesja pozostają.
         */
        dialog.setOnDismissListener(
                ignored -> {

                    galleryBottomSheet =
                            null;

                    galleryResultsList =
                            null;

                    galleryResultsEmpty =
                            null;

                    gallerySheetCount =
                            null;

                    galleryCollectionStats =
                            null;

                    gallerySelectAllCropsToggle =
                            null;

                    gallerySaveSelectedCropsButton =
                            null;
                }
        );


        dialog.show();


        /*
         * Galeria otwiera się jako duży panel.
         * Użytkownik może go przeciągnąć w dół,
         * zamiast używać osobnych przycisków
         * "ukryj" i "maksymalizuj".
         */
        View bottomSheet =
                dialog.findViewById(
                        com.google.android.material.R.id.design_bottom_sheet
                );


        if (bottomSheet != null) {

            ViewGroup.LayoutParams parameters =
                    bottomSheet.getLayoutParams();


            parameters.height =
                    ViewGroup.LayoutParams.MATCH_PARENT;


            bottomSheet.setLayoutParams(
                    parameters
            );


            BottomSheetBehavior<View> behavior =
                    BottomSheetBehavior.from(
                            bottomSheet
                    );


            behavior.setState(
                    BottomSheetBehavior.STATE_EXPANDED
            );


            behavior.setHideable(
                    true
            );


            behavior.setDraggable(
                    true
            );
        }


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

    private synchronized void recordRecognitionTelemetry(PipelineResult result) {
        try {
            if (result.sceneReset) {
                metricsCollector.recordEvent("scene_reset", 0L, 0L, null);
                telemetryActiveTrackIds.clear();
                telemetryConfirmedTrackIds.clear();
                telemetryConsensusByTrack.clear();
            }

            Set<Long> currentTrackIds = new HashSet<>();
            for (PlateObservation observation : result.plateObservations) {
                currentTrackIds.add(observation.trackId);
            }
            for (Long previousTrackId : new HashSet<>(telemetryActiveTrackIds)) {
                if (currentTrackIds.contains(previousTrackId)) continue;
                metricsCollector.recordEvent("track_lost", 0L, previousTrackId, null);
                telemetryConfirmedTrackIds.remove(previousTrackId);
                telemetryConsensusByTrack.remove(previousTrackId);
            }

            for (PlateObservation observation : result.plateObservations) {
                if (!telemetryActiveTrackIds.contains(observation.trackId)) {
                    JSONObject created = new JSONObject();
                    created.put("plate_geometry", observation.geometry.toJson());
                    metricsCollector.recordEvent(
                            "track_created",
                            observation.frameId,
                            observation.trackId,
                            created
                    );
                }

                JSONArray rows = new JSONArray();
                for (Integer count : observation.rowCounts) rows.put(count);

                if (observation.freshMzAttempted) {
                    JSONObject attempt = new JSONObject();
                    attempt.put("mz_attempt_index", observation.mzAttemptIndex);
                    attempt.put("zoom_ratio", currentCameraZoomRatio);
                    attempt.put("capture_source", autoZoomController.captureSource());
                    metricsCollector.recordEvent(
                            "mz_attempt",
                            observation.frameId,
                            observation.trackId,
                            attempt
                    );

                    JSONObject mzResult = new JSONObject();
                    mzResult.put("mz_attempt_index", observation.mzAttemptIndex);
                    mzResult.put("prediction", observation.freshPrediction);
                    mzResult.put("confidence", observation.recognitionConfidence);
                    mzResult.put("fresh_mz_successful", observation.freshMzSuccessful);
                    mzResult.put("crop_supports_consensus", observation.cropSupportsConsensus);
                    mzResult.put("track_confirmed", observation.confirmed);
                    mzResult.put("consensus_observations", observation.observations);
                    mzResult.put("layout", observation.layout);
                    mzResult.put("row_counts", rows);
                    mzResult.put("zoom_ratio", currentCameraZoomRatio);
                    metricsCollector.recordEvent(
                            "mz_result",
                            observation.frameId,
                            observation.trackId,
                            mzResult
                    );
                }

                String previousConsensus = telemetryConsensusByTrack.getOrDefault(
                        observation.trackId,
                        observation.predictionBefore
                );
                if (!observation.predictionAfter.isEmpty()
                        && !observation.predictionAfter.equals(previousConsensus)) {
                    JSONObject update = new JSONObject();
                    update.put("prediction_before", previousConsensus);
                    update.put("prediction_after", observation.predictionAfter);
                    update.put("confidence", observation.recognitionConfidence);
                    update.put("consensus_observations", observation.observations);
                    update.put("layout", observation.layout);
                    update.put("row_counts", rows);
                    update.put("track_confirmed", observation.confirmed);
                    metricsCollector.recordEvent(
                            "consensus_updated",
                            observation.frameId,
                            observation.trackId,
                            update
                    );
                }
                if (observation.confirmed
                        && !telemetryConfirmedTrackIds.contains(observation.trackId)) {
                    JSONObject confirmed = new JSONObject();
                    confirmed.put("prediction", observation.predictionAfter);
                    confirmed.put("confidence", observation.recognitionConfidence);
                    confirmed.put("consensus_observations", observation.observations);
                    confirmed.put("mz_attempt_index", observation.mzAttemptIndex);
                    metricsCollector.recordEvent(
                            "consensus_confirmed",
                            observation.frameId,
                            observation.trackId,
                            confirmed
                    );
                    telemetryConfirmedTrackIds.add(observation.trackId);
                }
                if (!observation.predictionAfter.isEmpty()) {
                    telemetryConsensusByTrack.put(
                            observation.trackId,
                            observation.predictionAfter
                    );
                }
            }
            telemetryActiveTrackIds.clear();
            telemetryActiveTrackIds.addAll(currentTrackIds);
        } catch (Exception error) {
            AppLog.warning(this, LOG_TAG, "Nie udało się zapisać zdarzenia telemetrii");
        }
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
                    observation.recognitionConfidence,
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
                    observation.timing,
                    currentCameraZoomRatio,
                    autoZoomController.captureSource(),
                    observation.geometry,
                    ImageDifficultyMetrics.measure(bitmap),
                    observation.confirmed,
                    observation.freshMzSuccessful,
                    observation.cropSupportsConsensus,
                    observation.observations,
                    observation.mzAttemptIndex,
                    observation.layout,
                    observation.rowCounts,
                    observation.freshPrediction
            );
            try {
                captured.miniReportJson = CropMiniReport.create(
                        captured,
                        collectionSessionStartedElapsedNanos,
                        deviceProfile,
                        modelRegistry,
                        autoTuneManager,
                        recognitionProfile.wireName(),
                        cameraResolutionSelection.wireName(),
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
                    observation.recognitionConfidence,
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

        if (captureAdapter == null) {
            return;
        }


        updateCaptureAdapterItems();


        boolean empty =
                capturedCrops.isEmpty();


        /*
         * Przycisk galerii na głównym ekranie
         * zawsze pokazuje bieżącą liczbę cropów.
         */
        if (galleryOpenButton != null) {

            galleryOpenButton.setText(
                    getString(
                            R.string.gallery_open_count,
                            capturedCrops.size()
                    )
            );
        }


        /*
         * Bottom Sheet może być zamknięty,
         * dlatego wszystkie jego widoki są opcjonalne.
         */
        if (galleryResultsEmpty != null) {

            galleryResultsEmpty.setVisibility(
                    empty
                            ? View.VISIBLE
                            : View.GONE
            );
        }


        if (galleryResultsList != null) {

            galleryResultsList.setVisibility(
                    empty
                            ? View.GONE
                            : View.VISIBLE
            );
        }


        if (gallerySheetCount != null) {

            gallerySheetCount.setText(
                    String.valueOf(
                            capturedCrops.size()
                    )
            );
        }


        /*
         * Główny ekran pokazuje tylko jedną prostą akcję
         * uruchomienia / wstrzymania zbierania.
         */
        collectionToggle.setText(
                collectionActive
                        ? R.string.collection_stop
                        : R.string.collection_start
        );


        collectionToggle.setIconResource(
                collectionActive
                        ? R.drawable.ic_stop_24
                        : R.drawable.ic_session_24
        );


        updateGalleryCollectionStatus();

        updateSelectionControls();
    }

    private void updateGalleryCollectionStatus() {

        if (galleryCollectionStats == null) {
            return;
        }


        if (collectionActive) {

            galleryCollectionStats.setText(
                    getString(
                            R.string.gallery_collection_running,
                            capturedCrops.size(),
                            resolvedCropLimit
                    )
            );

            return;
        }


        if (collectionSessionId.isEmpty()) {

            galleryCollectionStats.setText(
                    getString(
                            R.string.gallery_collection_ready_limit,
                            resolvedCropLimit
                    )
            );

            return;
        }


        galleryCollectionStats.setText(
                getString(
                        R.string.gallery_collection_paused,
                        capturedCrops.size(),
                        resolvedCropLimit
                )
        );
    }




    private void updateCaptureAdapterItems() {

        /*
         * Galeria może być zamknięta.
         *
         * Adapter nadal aktualizujemy, żeby po ponownym
         * otwarciu od razu zawierał aktualne dane.
         */
        if (galleryResultsList == null) {

            captureAdapter.setItems(
                    capturedCrops
            );

            return;
        }


        RecyclerView.LayoutManager manager =
                galleryResultsList.getLayoutManager();


        LinearLayoutManager linear =
                manager instanceof LinearLayoutManager

                        ? (LinearLayoutManager) manager

                        : null;


        int previousCount =
                captureAdapter.getItemCount();


        int firstVisible =
                linear == null

                        ? RecyclerView.NO_POSITION

                        : linear.findFirstVisibleItemPosition();


        View anchor =
                firstVisible == RecyclerView.NO_POSITION
                        ? null
                        : linear.findViewByPosition(
                        firstVisible
                );


        int anchorOffset =
                0;


        if (anchor != null) {

            anchorOffset =
                    anchor.getTop()
                            - galleryResultsList.getPaddingTop();
        }


        captureAdapter.setItems(
                capturedCrops
        );


        int addedAtFront =
                captureAdapter.getItemCount()
                        - previousCount;


        /*
         * Jeżeli użytkownik ogląda starsze cropy,
         * nowa detekcja nie przesuwa mu gwałtownie listy.
         */
        if (linear != null
                && firstVisible > 0
                && addedAtFront > 0) {

            linear.scrollToPositionWithOffset(
                    firstVisible + addedAtFront,
                    anchorOffset
            );
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
        String miniReportJson =
                item.miniReportJson;

        if (miniReportJson == null
                || miniReportJson.isEmpty()) {
            return;
        }
        final String refreshed;
        try {
            refreshed =
                    CropMiniReport.refreshHumanVerification(
                            miniReportJson,
                            item
                    );
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

        int selectable =
                0;

        int selected =
                0;


        for (CapturedPlateItem item :
                capturedCrops) {

            if (!isSelectableForSave(
                    item
            )) {
                continue;
            }


            selectable++;


            if (item.selectedForSave) {
                selected++;
            }
        }


        /*
         * Kontrolki istnieją wyłącznie przy
         * otwartym Bottom Sheecie.
         */
        if (gallerySelectAllCropsToggle != null) {

            gallerySelectAllCropsToggle
                    .setOnCheckedChangeListener(
                            null
                    );


            gallerySelectAllCropsToggle.setChecked(
                    selectable > 0
                            && selected == selectable
            );


            gallerySelectAllCropsToggle.setEnabled(
                    selectable > 0
            );


            gallerySelectAllCropsToggle
                    .setOnCheckedChangeListener(
                            (button, checked) ->
                                    selectAllCrops(
                                            checked
                                    )
                    );
        }


        if (gallerySaveSelectedCropsButton != null) {

            gallerySaveSelectedCropsButton.setText(
                    getString(
                            R.string.crop_save_selected_count,
                            selected
                    )
            );


            gallerySaveSelectedCropsButton.setEnabled(
                    selected > 0
                            && pendingBatchWrites == 0
            );
        }
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
        livePresentation.showTransient(
                getString(R.string.crop_limit_changed, resolvedCropLimit)
        );
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
                            cameraResolutionSelection.wireName(),
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
                    livePresentation.showTransient(message);
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
        Intent destination = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(kind == ResearchArchive.Kind.RESEARCH_SESSION
                        ? "application/octet-stream"
                        : "application/zip")
                .putExtra(Intent.EXTRA_TITLE, name);
        reportDestination.launch(destination);
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
                String thermalCsv = metricsCollector.createThermalCsv();
                String frameFlowCsv = metricsCollector.createFrameFlowCsv();
                String eventsJsonl = metricsCollector.createEventsJsonl();
                String log = AppLog.contents(this);
                try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) {
                        throw new IllegalStateException("Nie można otworzyć pliku docelowego");
                    }
                    if (kind == ResearchArchive.Kind.RESEARCH_SESSION) {
                        ResearchArchive.writeResearchSession(
                                output,
                                json,
                                csv,
                                thermalCsv,
                                frameFlowCsv,
                                eventsJsonl,
                                log,
                                cropSnapshot,
                                modelRegistry
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
        livePresentation.showTransient(message);
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
        startThermalUiMonitor();

        /*
         * Sensor ruchu pracuje tylko wtedy,
         * gdy działa analiza.
         */
        if (cameraStarted && cameraMotionMonitor != null) {
            cameraMotionMonitor.start();
        }
        if (cameraStarted) {
            startPreviewSceneMonitor();
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
         * rzeczywista rozdzielczość źródła CameraX.
         */
        CameraResolutionSelection requestedResolution =
                CameraResolutionSelection.fromWireName(
                        uiPreferences.getString(
                                SettingsActivity.KEY_ANALYSIS_RESOLUTION_SELECTION,
                                CameraResolutionSelection.AUTO
                        )
                );


        if (!requestedResolution.equals(
                cameraResolutionSelection
        )) {

            applyCameraResolutionSelection(
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


        if (requestedExperimentMode != experimentModeEnabled
                || requestedExperimentRoi != experimentRoiBudgetPolicy) {

            applyExperimentConfiguration(
                    requestedExperimentMode,
                    requestedExperimentRoi
            );
        }

        /*
         * NORMALNA KONFIGURACJA:
         * polityka ciągłości sceny. Ustawienie jest zapisywane w osobnym
         * Activity, więc musi zostać przekazane do już działającego pipeline'u
         * po powrocie użytkownika. setSceneHandlingMode() jest bezpieczne dla
         * pozostałych zmian ustawień, ponieważ koordynator zeruje stan tylko
         * wtedy, gdy tryb rzeczywiście się zmienił.
         */
        SceneHandlingMode requestedSceneHandlingMode =
                effectiveSceneHandlingMode();
        pipeline.setSceneHandlingMode(requestedSceneHandlingMode);
        android.util.Log.d(
                "ALPR_SCENE_MODE",
                "applied=" + requestedSceneHandlingMode.wireName()
                        + " settings_revision=" + revision
        );




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
        /*
         * Monitor termiczny nie może odświeżać UI ani uruchomić
         * kamery, kiedy Activity nie znajduje się na pierwszym planie.
         * stopThermalUiMonitor() zeruje również rozpoczęty okres
         * stabilizacji, więc po powrocie warunek musi być spełniony
         * ponownie przez pełny wymagany czas.
         */
        stopThermalUiMonitor();
        retainCaptureGalleryState();
        stopPreviewSceneMonitor();
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
        /*
         * Wywołanie defensywne. Standardowo monitor został już
         * zatrzymany w onPause(), ale usunięcie callbacków również
         * tutaj chroni przed utrzymaniem zniszczonej Activity przez
         * Handler w nietypowej sekwencji cyklu życia.
         */
        stopThermalUiMonitor();
        autoZoomHandler.removeCallbacksAndMessages(null);
        if (autoZoomGlowAnimator != null) autoZoomGlowAnimator.cancel();
        cameraTransformInProgress = false;
        clearAutoZoomRecognitionMemory();
        if (pipeline != null) {
            pipeline.finishCameraTransform();
        }

        AppLog.info(
                this,
                LOG_TAG,
                "Zamykanie aplikacji"
        );
        stopPreviewSceneMonitor();

        uiSceneGeneration.incrementAndGet();

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
        previewTrackingExecutor.shutdownNow();
        pipelineInferenceExecutor.shutdownNow();
        cancelExperimentTimer();

        if (galleryBottomSheet != null) {

            galleryBottomSheet.setOnDismissListener(
                    null
            );

            galleryBottomSheet.dismiss();

            galleryBottomSheet =
                    null;
        }

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
    private SceneHandlingMode effectiveSceneHandlingMode() {
        if (experimentModeEnabled) return SceneHandlingMode.STRICT_SCENE_BOUNDARY;
        return SceneHandlingMode.fromWireName(uiPreferences.getString(
                SettingsActivity.KEY_SCENE_HANDLING_MODE,
                SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()
        ));
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
        pipeline.setSceneHandlingMode(
                effectiveSceneHandlingMode()
        );

        overlayTracker.reset();
        pipeline.resetTracking();
        lastCaptureByTrack.clear();

        if (experimentModeEnabled) {
            if (experimentRoiBudgetPolicy.usesVehicleCascade()
                    && modelRegistry.getActive(
                    com.example.alpr_v1.model.ModelRole.VEHICLE
            ) == null) {

                livePresentation.showTransient(
                        getString(R.string.vehicle_cascade_missing_model)
                );
                return;
            }

            livePresentation.showTransient(
                    getString(
                            R.string.experiment_mode_enabled_status,
                            roiBudgetPolicyLabel(
                                    experimentRoiBudgetPolicy
                            )
                    )
            );

        } else {
            livePresentation.showTransient(
                    getString(R.string.experiment_mode_disabled_status)
            );
        }
        if (!experimentModeEnabled
                && !experimentSession.isRunning()) {

            experimentTimerConfig =
                    TimerConfig.disabled();

            cancelExperimentTimer();
        }

        renderAnalysisControls();
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
    private void showExperimentTimerDialog() {
        if (cameraStarted || !experimentModeEnabled) {
            return;
        }

        final int[] durations = {
                0,
                15,
                60,
                180,
                300
        };

        CharSequence[] labels = {
                getString(R.string.experiment_timer_none),
                "15 s",
                "1 min",
                "3 min",
                "5 min"
        };

        int rememberedSeconds =
                uiPreferences.getInt(
                        KEY_LAST_EXPERIMENT_TIMER_SECONDS,
                        TimerConfig.DEFAULT_DURATION_SECONDS
                );

        int currentSeconds =
                experimentTimerConfig.enabled()
                        ? experimentTimerConfig.durationSeconds()
                        : 0;

        int initialIndex = 0;

        if (currentSeconds > 0) {
            initialIndex =
                    timerIndexForSeconds(
                            durations,
                            currentSeconds
                    );
        } else if (rememberedSeconds > 0) {
            /*
             * Timer nadal pozostaje WYŁĄCZONY.
             * Zapamiętany czas wykorzystujemy tylko jako
             * wygodną podpowiedź po otwarciu dialogu.
             */
            initialIndex =
                    timerIndexForSeconds(
                            durations,
                            rememberedSeconds
                    );
        }

        final int[] selectedIndex = {
                initialIndex
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        R.string.experiment_timer_dialog_title
                )
                .setSingleChoiceItems(
                        labels,
                        initialIndex,
                        (dialog, which) ->
                                selectedIndex[0] = which
                )
                .setNegativeButton(
                        R.string.menu_close,
                        null
                )
                .setPositiveButton(
                        R.string.settings_apply,
                        (dialog, which) -> {
                            int seconds =
                                    durations[
                                            selectedIndex[0]
                                            ];

                            if (seconds <= 0) {
                                experimentTimerConfig =
                                        TimerConfig.disabled();

                                recordInfo(
                                        getString(
                                                R.string.experiment_timer_disabled_log
                                        )
                                );

                            } else {
                                experimentTimerConfig =
                                        TimerConfig.of(
                                                true,
                                                seconds
                                        );

                                /*
                                 * Zapamiętujemy tylko ostatni WYBRANY CZAS.
                                 * Nie zapisujemy stanu "timer włączony".
                                 */
                                uiPreferences.edit()
                                        .putInt(
                                                KEY_LAST_EXPERIMENT_TIMER_SECONDS,
                                                seconds
                                        )
                                        .apply();

                                recordInfo(
                                        getString(
                                                R.string.experiment_timer_armed_log,
                                                seconds
                                        )
                                );
                            }

                            updateExperimentTimerButton();
                        }
                )
                .show();
    }

    private static int timerIndexForSeconds(
            int[] durations,
            int seconds
    ) {
        for (int index = 0;
             index < durations.length;
             index++) {

            if (durations[index] == seconds) {
                return index;
            }
        }

        return 0;
    }

    private void updateExperimentTimerButton() {
        if (experimentTimerButton == null) {
            return;
        }

        /*
         * Aktywny przebieg z timerem:
         * pokazujemy pozostały czas.
         */
        if (experimentSession.isRunning()
                && experimentTimerDeadlineElapsedMillis > 0L) {

            long remainingMillis =
                    Math.max(
                            0L,
                            experimentTimerDeadlineElapsedMillis
                                    - android.os.SystemClock.elapsedRealtime()
                    );

            long remainingSeconds =
                    (remainingMillis + 999L) / 1000L;

            experimentTimerButton.setText(
                    formatTimerDuration(
                            remainingSeconds
                    )
            );

            return;
        }

        /*
         * Timer przygotowany dla następnego START.
         */
        if (experimentTimerConfig.enabled()) {
            experimentTimerButton.setText(
                    formatTimerDuration(
                            experimentTimerConfig.durationSeconds()
                    )
            );
            return;
        }

        experimentTimerButton.setText(
                R.string.experiment_timer_button
        );
    }

    private static String formatTimerDuration(
            long seconds
    ) {
        if (seconds < 60L) {
            return seconds + " s";
        }

        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;

        if (remainingSeconds == 0L) {
            return minutes + " min";
        }

        return String.format(
                Locale.ROOT,
                "%d:%02d",
                minutes,
                remainingSeconds
        );
    }
    /**
     * A heavy MT/MZ result describes the camera frame captured before inference.
     * If the lightweight preview tracker has already moved the same target, keep
     * its current position and only apply the fresh label/shape from MT.
     */
    private static List<OverlayItem> alignMtGeometryToLiveTarget(
            List<OverlayItem> items,
            TargetSnapshot liveTarget
    ) {
        if (items == null
                || items.isEmpty()
                || liveTarget == null
                || !liveTarget.hasTrack()
                || liveTarget.trackingQuality
                < TargetStateMachine.QUALITY_TRACKING
                || liveTarget.normalizedBounds.width() <= 0f
                || liveTarget.normalizedBounds.height() <= 0f) {
            return items;
        }

        OverlayItem sourcePlate = null;
        for (OverlayItem item : items) {
            if (item.kind == OverlayItem.Kind.PLATE
                    && !item.carriedPrediction
                    && item.trackId == liveTarget.trackId) {
                sourcePlate = item;
                break;
            }
        }
        if (sourcePlate == null
                || sourcePlate.normalizedBounds.width() <= 0f
                || sourcePlate.normalizedBounds.height() <= 0f) {
            return items;
        }

        RectF liveBounds = new RectF(liveTarget.normalizedBounds);
        float dx = liveBounds.centerX() - sourcePlate.normalizedBounds.centerX();
        float dy = liveBounds.centerY() - sourcePlate.normalizedBounds.centerY();
        List<OverlayItem> aligned = new ArrayList<>(items.size());

        for (OverlayItem item : items) {
            if (item == sourcePlate) {
                aligned.add(new OverlayItem(
                        OverlayItem.Kind.PLATE,
                        liveBounds,
                        remapPointsToBounds(
                                sourcePlate.normalizedKeypoints,
                                sourcePlate.normalizedBounds,
                                liveBounds,
                                liveTarget.overlayItem == null
                                        ? java.util.Collections.emptyList()
                                        : liveTarget.overlayItem.normalizedKeypoints
                        ),
                        sourcePlate.label,
                        liveTarget.trackId,
                        false
                ));
            } else if (item.kind != OverlayItem.Kind.PLATE
                    && item.normalizedBounds.contains(
                    sourcePlate.normalizedBounds.centerX(),
                    sourcePlate.normalizedBounds.centerY()
            )) {
                RectF movedBounds = translatedBounds(item.normalizedBounds, dx, dy);
                aligned.add(new OverlayItem(
                        item.kind,
                        movedBounds,
                        translatedPoints(item.normalizedKeypoints, dx, dy),
                        item.label,
                        item.trackId,
                        item.carriedPrediction
                ));
            } else {
                aligned.add(item);
            }
        }

        return aligned;
    }

    private static List<android.graphics.PointF> remapPointsToBounds(
            List<android.graphics.PointF> sourcePoints,
            RectF sourceBounds,
            RectF targetBounds,
            List<android.graphics.PointF> fallbackPoints
    ) {
        if (sourcePoints == null
                || sourcePoints.isEmpty()
                || sourceBounds.width() <= 0f
                || sourceBounds.height() <= 0f) {
            return fallbackPoints == null
                    ? java.util.Collections.emptyList()
                    : fallbackPoints;
        }
        List<android.graphics.PointF> result = new ArrayList<>(sourcePoints.size());
        for (android.graphics.PointF point : sourcePoints) {
            float relativeX = (point.x - sourceBounds.left) / sourceBounds.width();
            float relativeY = (point.y - sourceBounds.top) / sourceBounds.height();
            result.add(new android.graphics.PointF(
                    targetBounds.left + relativeX * targetBounds.width(),
                    targetBounds.top + relativeY * targetBounds.height()
            ));
        }
        return result;
    }

    private static List<android.graphics.PointF> translatedPoints(
            List<android.graphics.PointF> points,
            float dx,
            float dy
    ) {
        if (points == null || points.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<android.graphics.PointF> translated = new ArrayList<>(points.size());
        for (android.graphics.PointF point : points) {
            translated.add(new android.graphics.PointF(
                    Math.max(0f, Math.min(1f, point.x + dx)),
                    Math.max(0f, Math.min(1f, point.y + dy))
            ));
        }
        return translated;
    }

    private List<OverlayItem> mergePreviewOverlay(
            List<OverlayItem> trackedPlates
    ) {

        List<OverlayItem> result =
                new ArrayList<>();


        /*
         * VEHICLE oraz ROI nie mają własnego szybkiego
         * trackera.
         *
         * Przesuwamy je więc o translację odpowiadającej
         * im tablicy.
         */
        for (OverlayItem diagnostic :
                latestDiagnosticOverlayItems) {

            result.add(
                    moveDiagnosticWithTrackedPlate(
                            diagnostic,
                            trackedPlates
                    )
            );
        }


        /*
         * Tablice rysujemy na końcu, dzięki czemu
         * pozostają nad pomarańczowymi ramkami.
         */
        result.addAll(
                trackedPlates
        );


        return result;
    }
    private OverlayItem moveDiagnosticWithTrackedPlate(
            OverlayItem diagnostic,
            List<OverlayItem> trackedPlates
    ) {

        OverlayItem basePlate =
                findAssociatedPipelinePlate(
                        diagnostic
                );


        /*
         * Nie znaleźliśmy tablicy należącej do tego
         * VEHICLE/ROI.
         *
         * Nie zgadujemy ruchu — pozostawiamy ostatnią
         * prawidłową pozycję MP.
         */
        if (basePlate == null) {

            return diagnostic;
        }


        OverlayItem trackedPlate =
                findTrackedPlate(
                        trackedPlates,
                        basePlate.trackId
                );


        if (trackedPlate == null) {

            return diagnostic;
        }


        /*
         * Ruch tablicy pomiędzy ostatnim MT
         * a aktualną klatką Preview.
         */
        float dx =
                trackedPlate.normalizedBounds.centerX()
                        - basePlate.normalizedBounds.centerX();

        float dy =
                trackedPlate.normalizedBounds.centerY()
                        - basePlate.normalizedBounds.centerY();


        RectF movedBounds =
                translatedBounds(
                        diagnostic.normalizedBounds,
                        dx,
                        dy
                );


        return new OverlayItem(
                diagnostic.kind,
                movedBounds,
                diagnostic.normalizedKeypoints,
                diagnostic.label,
                diagnostic.trackId,
                false
        );
    }
    private OverlayItem findAssociatedPipelinePlate(
            OverlayItem diagnostic
    ) {

        OverlayItem best =
                null;

        float bestDistance =
                Float.MAX_VALUE;


        for (OverlayItem plate :
                latestPipelinePlateItems) {

            float plateX =
                    plate.normalizedBounds.centerX();

            float plateY =
                    plate.normalizedBounds.centerY();


            /*
             * Tablica musi fizycznie znajdować się
             * wewnątrz ramki pojazdu / ROI.
             */
            if (!diagnostic.normalizedBounds.contains(
                    plateX,
                    plateY
            )) {

                continue;
            }


            float dx =
                    plateX
                            - diagnostic.normalizedBounds.centerX();

            float dy =
                    plateY
                            - diagnostic.normalizedBounds.centerY();


            float distance =
                    dx * dx
                            + dy * dy;


            if (distance < bestDistance) {

                bestDistance =
                        distance;

                best =
                        plate;
            }
        }


        return best;
    }
    private static OverlayItem findTrackedPlate(
            List<OverlayItem> trackedPlates,
            long trackId
    ) {

        if (trackedPlates == null) {

            return null;
        }


        for (OverlayItem plate :
                trackedPlates) {

            if (plate.kind
                    == OverlayItem.Kind.PLATE
                    && plate.trackId
                    == trackId) {

                return plate;
            }
        }


        return null;
    }
    private static RectF translatedBounds(
            RectF source,
            float dx,
            float dy
    ) {

        RectF result =
                new RectF(
                        source
                );


        result.offset(
                dx,
                dy
        );


        /*
         * Zachowujemy rozmiar ramki, ale nie pozwalamy
         * jej wyjechać poza znormalizowany obraz.
         */
        if (result.left < 0f) {

            result.offset(
                    -result.left,
                    0f
            );
        }


        if (result.right > 1f) {

            result.offset(
                    1f - result.right,
                    0f
            );
        }


        if (result.top < 0f) {

            result.offset(
                    0f,
                    -result.top
            );
        }


        if (result.bottom > 1f) {

            result.offset(
                    0f,
                    1f - result.bottom
            );
        }


        return result;
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            enableImmersiveMode();
        }
    }
    private final Runnable thermalUiRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (!thermalUiMonitorRunning
                            || thermalMonitor == null) {
                        return;
                    }


                    latestThermalSnapshot =
                            thermalMonitor.read();

                    if (cameraStarted && metricsCollector != null) {
                        metricsCollector.recordThermalSample(
                                latestThermalSnapshot
                        );
                    }


                    updateExperimentThermalButton();


                    if (waitingForThermalStart) {

                        evaluateThermalStartCondition(
                                latestThermalSnapshot
                        );
                    }


                    /*
                     * Podczas aktywnej analizy odświeżamy
                     * również temperaturę w HUD.
                     */
                    if (cameraStarted) {
                        renderLiveHud();
                    }


                    thermalHandler.postDelayed(
                            this,
                            THERMAL_POLL_MS
                    );
                }
            };
    private void startThermalUiMonitor() {

        thermalHandler.removeCallbacks(
                thermalUiRunnable
        );

        thermalUiMonitorRunning =
                true;

        thermalHandler.post(
                thermalUiRunnable
        );
    }

    private void updateExperimentThermalButton() {

        if (experimentThermalButton == null) {
            return;
        }

        experimentThermalButton.setVisibility(
                experimentModeEnabled
                        ? View.VISIBLE
                        : View.GONE
        );

        if (!experimentModeEnabled) {
            return;
        }

        ThermalMonitor.Snapshot snapshot =
                latestThermalSnapshot;

        if (snapshot == null
                || !snapshot.available()) {

            experimentThermalButton.setText(
                    R.string.experiment_thermal_button
            );

            return;
        }

        String headroomText =
                snapshot.headroomAvailable()

                        ? String.format(
                        Locale.ROOT,
                        "%.2f",
                        snapshot.thermalHeadroom
                )

                        : "—";

        String text;



        if (experimentThermalConfig.enabled()) {

            text =
                    String.format(
                            Locale.ROOT,
                            "BAT %.1f°C · TH%d · HEAD %s\nCEL ≤%.1f°C · TH0",
                            snapshot.batteryTemperatureC,
                            snapshot.thermalStatus,
                            headroomText,
                            experimentThermalConfig
                                    .maxBatteryTemperatureC()
                    );

        } else {

            text =
                    String.format(
                            Locale.ROOT,
                            "BAT %.1f°C · TH%d · HEAD %s",
                            snapshot.batteryTemperatureC,
                            snapshot.thermalStatus,
                            headroomText
                    );
        }
        experimentThermalButton.setText(
                text
        );

        /*
         * Konfigurację warunku termicznego można zmienić
         * tylko wtedy, gdy analiza jeszcze nie działa
         * i nie oczekujemy właśnie na schłodzenie.
         */
        experimentThermalButton.setEnabled(
                !cameraStarted
                        && !waitingForThermalStart
        );
    }

    private void evaluateThermalStartCondition(
            ThermalMonitor.Snapshot snapshot
    ) {

        if (!waitingForThermalStart) {
            return;
        }

        /*
         * Jeżeli warunek termiczny jest wyłączony,
         * nie ma na co czekać.
         */
        if (!experimentThermalConfig.enabled()) {

            waitingForThermalStart =
                    false;

            thermalReadySinceElapsedMillis =
                    -1L;

            startCamera(
                    true
            );

            return;
        }


        /*
         * Brak prawidłowego odczytu nie może rozpocząć
         * eksperymentu.
         */
        if (snapshot == null
                || !snapshot.available()) {

            thermalReadySinceElapsedMillis =
                    -1L;

            livePresentation.showTransient(
                    getString(R.string.experiment_thermal_waiting_reading)
            );

            return;
        }


        boolean acceptable =
                experimentThermalConfig.accepts(
                        snapshot
                );


        if (!acceptable) {

            thermalReadySinceElapsedMillis =
                    -1L;

            livePresentation.showTransient(
                    String.format(
                            Locale.ROOT,
                            "Chłodzenie: %.1f°C · TH%d · cel ≤ %.1f°C · TH≤%d",
                            snapshot.batteryTemperatureC,
                            snapshot.thermalStatus,
                            experimentThermalConfig
                                    .maxBatteryTemperatureC(),
                            experimentThermalConfig
                                    .maxThermalStatus()
                    )
            );

            return;
        }


        long now =
                android.os.SystemClock.elapsedRealtime();


        /*
         * Pierwszy prawidłowy pomiar rozpoczyna
         * okres stabilizacji.
         */
        if (thermalReadySinceElapsedMillis < 0L) {

            thermalReadySinceElapsedMillis =
                    now;
        }


        long stableMillis =
                now
                        - thermalReadySinceElapsedMillis;


        long requiredMillis =
                experimentThermalConfig
                        .stabilizationMillis();


        if (stableMillis < requiredMillis) {

            long remainingMillis =
                    requiredMillis
                            - stableMillis;

            long remainingSeconds =
                    Math.max(
                            1L,
                            (remainingMillis + 999L)
                                    / 1000L
                    );


            livePresentation.showTransient(
                    String.format(
                            Locale.ROOT,
                            "Temperatura OK: %.1f°C · TH%d · stabilizacja %d s",
                            snapshot.batteryTemperatureC,
                            snapshot.thermalStatus,
                            remainingSeconds
                    )
            );

            return;
        }


        /*
         * Warunek był spełniony nieprzerwanie przez
         * wymagany czas. Dopiero teraz zaczynamy
         * właściwy eksperyment.
         */
        waitingForThermalStart =
                false;

        thermalReadySinceElapsedMillis =
                -1L;


        recordInfo(
                String.format(
                        Locale.ROOT,
                        "Warunek termiczny spełniony: %.1f°C TH%d",
                        snapshot.batteryTemperatureC,
                        snapshot.thermalStatus
                )
        );


        startCamera(
                true
        );
    }


    private void stopThermalUiMonitor() {

        thermalUiMonitorRunning =
                false;

        thermalHandler.removeCallbacks(
                thermalUiRunnable
        );

        /*
         * Po powrocie do aplikacji wymagamy ponownie
         * pełnego okresu stabilizacji.
         */
        thermalReadySinceElapsedMillis =
                -1L;
    }

    private void showExperimentThermalDialog() {

        if (cameraStarted
                || waitingForThermalStart
                || !experimentModeEnabled) {
            return;
        }


        final int[] temperaturesTenths = {
                0,
                300,
                310,
                320,
                330,
                340
        };


        CharSequence[] labels = {
                "Bez warunku",
                "≤ 30.0°C · TH0",
                "≤ 31.0°C · TH0",
                "≤ 32.0°C · TH0",
                "≤ 33.0°C · TH0",
                "≤ 34.0°C · TH0"
        };


        int rememberedTenths =
                uiPreferences.getInt(
                        KEY_LAST_EXPERIMENT_THERMAL_TENTHS,
                        320
                );


        int currentTenths =
                experimentThermalConfig.enabled()

                        ? (int) Math.round(
                        experimentThermalConfig
                                .maxBatteryTemperatureC()
                        * 10.0
                )

                        : rememberedTenths;


        int initialIndex =
                0;


        if (experimentThermalConfig.enabled()) {

            for (int i = 1;
                 i < temperaturesTenths.length;
                 i++) {

                if (temperaturesTenths[i]
                        == currentTenths) {

                    initialIndex =
                            i;

                    break;
                }
            }
        }


        final int[] selectedIndex = {
                initialIndex
        };


        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "Warunek temperatury"
                )
                .setSingleChoiceItems(
                        labels,
                        initialIndex,
                        (dialog, which) ->
                                selectedIndex[0] =
                                        which
                )
                .setNegativeButton(
                        "Anuluj",
                        null
                )
                .setPositiveButton(
                        android.R.string.ok,
                        (dialog, which) -> {

                            int temperatureTenths =
                                    temperaturesTenths[
                                            selectedIndex[0]
                                            ];


                            if (temperatureTenths <= 0) {

                                experimentThermalConfig =
                                        ThermalConfig.disabled();

                                recordInfo(
                                        "Wyłączono warunek termiczny eksperymentu"
                                );

                            } else {

                                double temperature =
                                        temperatureTenths
                                                / 10.0;


                                experimentThermalConfig =
                                        ThermalConfig.of(
                                                true,
                                                temperature,
                                                0,
                                                5
                                        );


                                uiPreferences.edit()
                                        .putInt(
                                                KEY_LAST_EXPERIMENT_THERMAL_TENTHS,
                                                temperatureTenths
                                        )
                                        .apply();


                                recordInfo(
                                        String.format(
                                                Locale.ROOT,
                                                "Warunek termiczny: BAT <= %.1f°C, TH <= 0, stabilizacja 5 s",
                                                temperature
                                        )
                                );
                            }


                            updateExperimentThermalButton();
                        }
                )
                .show();
    }

    private void requestAnalysisStart() {

        /*
         * Poza trybem eksperymentalnym warunek
         * termiczny nie ma znaczenia.
         */
        if (!experimentModeEnabled
                || !experimentThermalConfig.enabled()) {

            startCamera(
                    true
            );

            return;
        }


        /*
         * Kamera jeszcze nie startuje.
         * Nie uruchamiamy również MetricsCollector,
         * ExperimentSession ani timera.
         */
        waitingForThermalStart =
                true;

        thermalReadySinceElapsedMillis =
                -1L;


        livePresentation.showTransient(
                getString(R.string.experiment_thermal_waiting_cooling)
        );


        renderAnalysisControls();


        /*
         * Nie czekamy na kolejny tick monitora,
         * tylko od razu oceniamy bieżący stan.
         */
        latestThermalSnapshot =
                thermalMonitor.read();


        updateExperimentThermalButton();


        evaluateThermalStartCondition(
                latestThermalSnapshot
        );
    }

    private void cancelThermalStartWaiting() {

        waitingForThermalStart =
                false;

        thermalReadySinceElapsedMillis =
                -1L;


        livePresentation.stop();


        renderAnalysisControls();


        recordInfo(
                "Anulowano oczekiwanie na warunek termiczny"
        );
    }
}
