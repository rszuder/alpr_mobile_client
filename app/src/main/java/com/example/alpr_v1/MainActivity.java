package com.example.alpr_v1;

import com.example.alpr_v1.ui.readiness.*;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageButton;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
//---------------------------------------------------------------

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.autotune.AutoTuneResult;
import com.example.alpr_v1.camera.CameraController;
import com.example.alpr_v1.camera.CameraSetupReadiness;
import com.example.alpr_v1.camera.LumaFrame;
import com.example.alpr_v1.continuity.SceneObservationDomain;
import com.example.alpr_v1.continuity.ObservationMask;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.camera.AutoZoomRecognitionMemory;
//do usuniecia po migracji
import com.example.alpr_v1.camera.AnalysisResolutionProfile;
import com.example.alpr_v1.camera.AnalysisResolutionPolicy;
//--------------------------------------------------------------
import com.example.alpr_v1.camera.CameraResolutionCatalog;
import com.example.alpr_v1.camera.CameraResolutionSelection;
import com.example.alpr_v1.camera.CameraMotionMonitor;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.capture.CaptureDirectoryStore;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.capture.CropCapacityPolicy;
import com.example.alpr_v1.capture.CropSamplingPolicy;
import com.example.alpr_v1.capture.HumanVerificationEditor;
import com.example.alpr_v1.capture.RecognitionHistoryItem;
import com.example.alpr_v1.capture.RecognitionHistoryStore;
import com.example.alpr_v1.capture.VerificationIssue;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.metrics.DeviceProfile;
import com.example.alpr_v1.metrics.CropMiniReport;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.metrics.ReportArchive;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.metrics.ImageDifficultyMetrics;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;
import com.example.alpr_v1.acquisition.PlateAnchor;
import com.example.alpr_v1.acquisition.AcquisitionDirectiveAction;
import com.example.alpr_v1.acquisition.ScanAcquisitionSnapshot;
import com.example.alpr_v1.domain.RegistrationTextPolicy;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PipelineResult;
import com.example.alpr_v1.pipeline.PipelineResultDispatchGate;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.TargetSnapshot;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneAnchorRefreshPolicy;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.ReacquireTelemetry;
import com.example.alpr_v1.continuity.MotionExplanationEvidence;
import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneTransitionAction;
import com.example.alpr_v1.continuity.SceneTransitionDecision;
import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.pipeline.TargetStateMachine;
import com.example.alpr_v1.ui.CameraMotionOverlayTracker;
import com.example.alpr_v1.ui.DetectionOverlayView;
import com.example.alpr_v1.ui.EntityAwareOverlayAlignment;
import com.example.alpr_v1.ui.EntityOverlayMotionProjector;
import com.example.alpr_v1.ui.LivePresentationController;
import com.example.alpr_v1.ui.PlateCropView;
import com.example.alpr_v1.ui.PlateOverlayFreshness;
import com.example.alpr_v1.ui.PreviewContinuityUiPolicy;
import com.example.alpr_v1.ui.PreviewPresentationBarrier;
import com.example.alpr_v1.ui.SceneModeHudPolicy;
import com.example.alpr_v1.ui.RecognitionHistoryAdapter;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.example.alpr_v1.experiment.ExperimentSession;
import com.example.alpr_v1.experiment.ExperimentIdentity;
import com.example.alpr_v1.experiment.CrashSessionMarker;
import com.example.alpr_v1.experiment.ResearchExecutionChoice;
import com.example.alpr_v1.experiment.ResearchExecutionConfig;
import com.example.alpr_v1.experiment.ResearchStageExecutionConfig;
import com.example.alpr_v1.experiment.TimerConfig;
import com.example.alpr_v1.experiment.ThermalConfig;
import com.example.alpr_v1.experiment.ThermalMonitor;
import com.example.alpr_v1.vision.SceneChangeDetector;
import com.example.alpr_v1.vision.CameraImageConverter;
import com.example.alpr_v1.tracking.PreviewPlateTracker;
import com.example.alpr_v1.tracking.PreviewTrackingFrame;
import com.example.alpr_v1.tracking.PreviewMotionGenerationGate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import com.example.alpr_v1.tracking.FrameMotionHistory;
import com.example.alpr_v1.tracking.FrameMotionQuality;
import com.example.alpr_v1.tracking.FrameMotionTransform;
import com.example.alpr_v1.tracking.GlobalLumaMotionTracker;
import com.example.alpr_v1.tracking.LumaSceneChangeDetector;
import com.example.alpr_v1.tracking.VisualMotionEvidenceDecay;
import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.vision.SceneAnchorGuard;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class MainActivity extends AppCompatActivity {
    private enum VerificationFilter {
        ALL,
        UNREVIEWED,
        CHANGED,
        DESKTOP
    }

    private static final String LOG_TAG = "MainActivity";
    private static final String KEY_CAMERA_PERMISSION_REQUESTED =
            "camera_permission_requested";

    private static final String KEY_LAST_EXPERIMENT_TIMER_SECONDS =
            "last_experiment_timer_seconds";
    private static final String KEY_ANALYSIS_TIMER_ENABLED = "analysis_timer_enabled";
    private static final String KEY_LAST_EXPERIMENT_THERMAL_TENTHS =
            "last_experiment_thermal_tenths";
    private static final String KEY_AUTO_ZOOM_ENABLED =
            "auto_zoom_enabled";

    private static final long THERMAL_POLL_MS =
            1000L;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService previewTrackingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService directLumaTrackingExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        () -> {
                            android.os.Process.setThreadPriority(
                                    android.os.Process.THREAD_PRIORITY_DISPLAY
                            );
                            runnable.run();
                        },
                        "alpr-direct-luma"
                );
                return thread;
            });
    private final ExecutorService previewCoordinationExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService pipelineInferenceExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean previewFrameInFlight = new AtomicBoolean();
    private final AtomicBoolean pipelineFrameInFlight = new AtomicBoolean();
    private final AtomicBoolean directLumaWorkerRunning = new AtomicBoolean();
    private static final class PendingObservation {
        final LumaFrame frame; final SceneObservationDomain domain;
        PendingObservation(LumaFrame frame,SceneObservationDomain domain){this.frame=frame;this.domain=domain;}
    }
    private final AtomicReference<PendingObservation> pendingDirectLumaFrame =
            new AtomicReference<>();
    private final AtomicBoolean previewCoordinationWorkerRunning = new AtomicBoolean();
    private final AtomicReference<PreviewCoordinationRequest>
            pendingPreviewCoordination = new AtomicReference<>();
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
    private static final class PreviewCoordinationRequest {
        final SourceFrameStamp sourceFrame;
        final ContinuityStamp continuityStamp;
        final TargetSnapshot targetSnapshot;
        final List<OverlayItem> trackedItems;
        final long sceneGeneration;
        final long transformGeneration;
        final long presentationGeneration;
        final boolean trackingLost;
        final boolean trackingDegraded;
        final boolean sensorAvailable;
        final boolean sensorMoving;
        final boolean sensorRapid;
        final float angularMagnitude;
        final boolean visualMotionEstimated;
        final boolean visualRapid;
        final boolean motionSettling;
        final float visualMotionCoherence;
        final float visualMotionResidual;
        final int visualMotionInliers;
        final float visualMotionSpatialCoverage;
        final boolean rawVisualChange;
        final float rawChangedFraction,rawMeanDelta;

        PreviewCoordinationRequest(
                SourceFrameStamp sourceFrame,
                ContinuityStamp continuityStamp,
                TargetSnapshot targetSnapshot,
                List<OverlayItem> trackedItems,
                long sceneGeneration,
                long transformGeneration,
                long presentationGeneration,
                boolean trackingLost,
                boolean trackingDegraded,
                boolean sensorAvailable,
                boolean sensorMoving,
                boolean sensorRapid,
                float angularMagnitude,
                boolean visualMotionEstimated,
                boolean visualRapid,
                boolean motionSettling,
                float visualMotionCoherence,
                float visualMotionResidual,
                int visualMotionInliers,
                float visualMotionSpatialCoverage,
                boolean rawVisualChange,float rawChangedFraction,float rawMeanDelta
        ) {
            this.sourceFrame = sourceFrame;
            this.continuityStamp = continuityStamp;
            this.targetSnapshot = targetSnapshot;
            this.trackedItems = java.util.Collections.unmodifiableList(
                    new ArrayList<>(trackedItems == null
                            ? java.util.Collections.emptyList() : trackedItems)
            );
            this.sceneGeneration = sceneGeneration;
            this.transformGeneration = transformGeneration;
            this.presentationGeneration = presentationGeneration;
            this.trackingLost = trackingLost;
            this.trackingDegraded = trackingDegraded;
            this.sensorAvailable = sensorAvailable;
            this.sensorMoving = sensorMoving;
            this.sensorRapid = sensorRapid;
            this.angularMagnitude = angularMagnitude;
            this.visualMotionEstimated = visualMotionEstimated;
            this.visualRapid = visualRapid;
            this.motionSettling = motionSettling;
            this.visualMotionCoherence = clamp01(visualMotionCoherence);
            this.visualMotionResidual = clamp01(visualMotionResidual);
            this.visualMotionInliers = Math.max(0, visualMotionInliers);
            this.visualMotionSpatialCoverage = clamp01(
                    visualMotionSpatialCoverage
            );
            this.rawVisualChange=rawVisualChange;
            this.rawChangedFraction=clamp01(rawChangedFraction);
            this.rawMeanDelta=clamp01(rawMeanDelta/255f);
        }

        boolean currentMotion() {
            return sensorMoving || visualMotionEstimated;
        }

        boolean rapidMotion() {
            return sensorRapid || visualRapid;
        }

        private static float clamp01(float value) {
            if (!Float.isFinite(value)) return 0f;
            return Math.max(0f, Math.min(1f, value));
        }
    }


    private final AtomicLong uiSceneGeneration =
            new AtomicLong();
    /*
     * Zoom zmienia geometrię klatki, ale nie jej logiczną scenę. Osobna
     * generacja odrzuca wyniki rozpoczęte dla poprzedniego poziomu zoomu bez
     * zerowania tracków i stabilizacji tej samej sceny.
     */
    private final AtomicLong uiCameraTransformGeneration =
            new AtomicLong();
    private final AtomicLong overlayPresentationRevision =
            new AtomicLong();
    private final PreviewPresentationBarrier previewPresentationBarrier =
            new PreviewPresentationBarrier();
    private final EntityOverlayMotionProjector entityOverlayMotionProjector =
            new EntityOverlayMotionProjector();
    private final GlobalLumaMotionTracker globalLumaMotionTracker =
            new GlobalLumaMotionTracker();
    private final LumaSceneChangeDetector lumaSceneChangeDetector =
            new LumaSceneChangeDetector();
    private final FrameMotionHistory frameMotionHistory =
            new FrameMotionHistory();
    private final PreviewMotionGenerationGate previewMotionGenerationGate =
            new PreviewMotionGenerationGate();
    private final VisualMotionEvidenceDecay visualMotionEvidenceDecay =
            new VisualMotionEvidenceDecay();
    private final Object previewMotionStateLock = new Object();
    private final PlateOverlayFreshness plateOverlayFreshness =
            new PlateOverlayFreshness();
    private final com.example.alpr_v1.ui.StationarySceneSupport stationarySceneSupport =
            new com.example.alpr_v1.ui.StationarySceneSupport();


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
    private final AtomicLong previewSceneRecoveryRebaseRevision =
            new AtomicLong();
    private volatile long previewSceneRecoveryRebaseAppliedRevision;
    private long appliedScanTargetReleaseRevision;
    private long previewSceneAnchorLockRevision = -1L;
    private volatile com.example.alpr_v1.continuity.SceneContinuityState
            lastRenderedContinuityState =
            com.example.alpr_v1.continuity.SceneContinuityState.STABLE;

    private List<OverlayItem> latestDiagnosticOverlayItems =
            java.util.Collections.emptyList();
    private long latestDiagnosticOverlayAnchorTimestampNanos;

    /*
     * Pozycje tablic pochodzące z ostatniego
     * rzeczywistego wyniku MT.
     *
     * Służą jako geometria referencyjna do przesuwania
     * VEHICLE i VEHICLE_ROI razem ze śledzoną tablicą.
     */
    private List<OverlayItem> latestPipelinePlateItems =
            java.util.Collections.emptyList();
    private Set<Long> latestFreshMzPlateTrackIds =
            java.util.Collections.emptySet();
    private List<OverlayItem> latestPreviewMotionItems =
            java.util.Collections.emptyList();
    private long latestPipelinePlateEntityId;
    private long scanOverlayPresentationEntityId;
    private List<OverlayItem> autoZoomBaseMemoryOverlayItems =
            java.util.Collections.emptyList();
    private final com.example.alpr_v1.ui.ZoomPlateReturnGeometry autoZoomReturnGeometry =
            new com.example.alpr_v1.ui.ZoomPlateReturnGeometry();
    private int latestOverlaySourceWidth;
    private int latestOverlaySourceHeight;


    private volatile boolean previewSceneMonitorRunning;
    private com.example.alpr_v1.ui.PhoneOrientationHud phoneOrientationHud;
    private long lastPhoneOrientationUiNanos;
    private volatile long lastDirectLumaFrameNanos;
    private volatile long lastDirectLumaSourceTimestampNanos;
    private volatile boolean directLumaTrackingUnavailable;
    private volatile LumaFrame latestCameraLuma;
    // Main-thread scope of the already generation-validated neural result.
    private LumaFrame presentingSourceLuma;
    private ContinuityStamp presentingSourceStamp;
    private final com.example.alpr_v1.tracking.PreviewLumaHistory ordinaryLumaHistory=
            new com.example.alpr_v1.tracking.PreviewLumaHistory();
    private volatile long previewVehicleGeometryFreshAtNanos;
    private com.example.alpr_v1.camera.OrdinaryDynamicResourcePolicy ordinaryResourcePolicy;
    private com.example.alpr_v1.camera.BitmapMemoryBudget ordinaryBitmapBudget;
    private final java.util.Map<Bitmap,com.example.alpr_v1.camera.BitmapMemoryBudget.Lease> previewBitmapLeases =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private volatile boolean ordinaryMemoryPaused;
    private long lastOrdinaryResourceLogNanos;
    private final Object ordinaryPreviewCoordinationLock = new Object();
    private com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot dynamicDisplaySnapshot;
    private final Runnable ordinaryMemoryMonitor = new Runnable() {
        @Override public void run() {
            if(!cameraStarted || !ordinaryDynamicResourcesEnabled()) return;
            if(ordinaryMemoryCritical()) { pauseOrdinaryForMemory(); return; }
            long now=android.os.SystemClock.elapsedRealtimeNanos();
            if(now-lastOrdinaryResourceLogNanos>=5_000_000_000L) {
                lastOrdinaryResourceLogNanos=now;
                Size actual=cameraController==null?null:cameraController.actualAnalysisSize();
                android.util.Log.i("ALPR_DYNAMIC_RESOURCES","analysis="+actual+" cap_pixels="+dynamicResourcePolicy().analysisPixels
                        +" bitmap_used="+ordinaryBitmapBudget.usedBytes()+" bitmap_peak="+ordinaryBitmapBudget.peakBytes()
                        +" heap_used="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
                        +" native_heap="+android.os.Debug.getNativeHeapAllocatedSize());
            }
            previewSceneHandler.postDelayed(this,1000L);
        }
    };
    /*
     * Lokalny KLT ma sens dla ruchu pojedynczego obiektu przy nieruchomej
     * kamerze. Podczas obrotu telefonu maĹ‚e MP boxy zawierajÄ… za maĹ‚o tekstury
     * pojazdu i KLT potrafi przykleiÄ‡ siÄ™ do tĹ‚a. Wtedy autorytatywna jest
     * transformacja caĹ‚ego kadru. Po ruchu czekamy na nowÄ… kotwicÄ™ MP, aby nie
     * porĂłwnywaÄ‡ klatki sprzed i po obrocie.
     */
    private volatile boolean previewVehicleTrackerAwaitingFreshMpAnchor;
    private volatile boolean previewVehicleTrackerAwaitingFirstLocalFrame;
    private final com.example.alpr_v1.ui.VehiclePreviewMotionPolicy vehiclePreviewMotionPolicy =
            new com.example.alpr_v1.ui.VehiclePreviewMotionPolicy();


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
                        com.example.alpr_v1.camera.BitmapMemoryBudget.Lease previewLease = null;
                        try {
                            if(ordinaryDynamicResourcesEnabled()) {
                                dynamicResourcePolicy();
                                previewLease=ordinaryBitmapBudget.acquire(4L*previewView.getWidth()*previewView.getHeight());
                            }
                            if(!ordinaryDynamicResourcesEnabled() || previewLease!=null)previewBitmap = previewView.getBitmap();
                            if (previewBitmap != null && !previewBitmap.isRecycled()) {
                                if(previewLease!=null) { previewBitmapLeases.put(previewBitmap,previewLease); previewLease=null; }
                                final SceneObservationDomain observationDomain=observationDomain();
                                final CameraPreviewGeometry observationGeometry=tvSetup==null?null:tvSetup.observationGeometry();
                                final Bitmap ownedBitmap = previewBitmap;
                                final long sceneGeneration = uiSceneGeneration.get();
                                final long transformGeneration =
                                        uiCameraTransformGeneration.get();
                                final long presentationGeneration =
                                        previewPresentationBarrier.capture();
                                final ContinuityStamp continuityStamp =
                                        currentPreviewSourceFrameStamp()
                                                .continuityStamp();
                                previewTrackingExecutor.execute(() ->
                                        processPreviewTrackingFrame(
                                                ownedBitmap,
                                                sceneGeneration,
                                                transformGeneration,
                                                presentationGeneration,
                                                continuityStamp, observationDomain, observationGeometry
                                        )
                                );
                                previewBitmap = null;
                            } else {
                                previewFrameInFlight.set(false);
                            }
                        } catch (RuntimeException ignored) {
                            previewFrameInFlight.set(false);
                        } finally {
                            recycleBudgetedPreviewBitmap(previewBitmap);
                            if(previewLease!=null)previewLease.close();
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
            long presentationGeneration,
            ContinuityStamp continuityStamp,
            SceneObservationDomain domain, CameraPreviewGeometry geometry
    ) {
        boolean bitmapHandedToUi = false;
        try {
            // Ordinary DYN has one pixel-tracker owner: the camera-luma worker.
            // A queued Preview bitmap must not consume a stamped neural anchor,
            // reset its dimensions or publish a competing absence observation.
            if (ordinaryDynamicResourcesEnabled()) return;
            if(domain!=observationDomain()) return;
            if (pipeline != null && pipeline.isStaticIdle()) return;
            if (!cameraStarted
                    || sceneGeneration != uiSceneGeneration.get()
                    || transformGeneration != uiCameraTransformGeneration.get()
                    || !previewPresentationBarrier.matchesGeneration(
                            presentationGeneration
                    )
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

            // STATIC scene evidence comes from camera luma, never KLT or a delayed Preview bitmap.
            if (effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY) return;
            final SceneObservationDomain.Frame observation=domain==null?null:domain.preview(previewBitmap,geometry);

            if (autoZoomZoomedAnchorPending
                    && autoZoomController.state()
                    == AutoZoomController.State.ZOOMED_RETRY) {
                anchorPreview(autoZoomZoomedSceneAnchorGuard,previewBitmap,observation);
                autoZoomZoomedAnchorPending = false;
                autoZoomZoomedAnchorValid = true;
            }

            if (previewSceneAnchorPending) {
                anchorPreview(previewSceneAnchorGuard,previewBitmap,observation);
                previewSceneAnchorPending = false;
            }

            SceneContinuitySnapshot runtimeContinuity = pipeline == null
                    ? null : pipeline.sceneContinuitySnapshot();
            long requestedRecoveryRebaseRevision =
                    previewSceneRecoveryRebaseRevision.get();
            if (runtimeContinuity != null
                    && PreviewContinuityUiPolicy
                    .shouldApplyRecoveredSceneRebase(
                            requestedRecoveryRebaseRevision,
                            previewSceneRecoveryRebaseAppliedRevision,
                            runtimeContinuity.state
                    )) {
                resetGlobalPreviewMotionState();
                previewSceneDetector.reset();
                updatePreviewScene(previewBitmap,observation);
                anchorPreview(previewSceneAnchorGuard,previewBitmap,observation);
                anchorPreview(autoZoomZoomedSceneAnchorGuard,previewBitmap,observation);
                ReacquireTelemetry recovery = pipeline.reacquireTelemetry();
                if (PreviewContinuityUiPolicy
                        .shouldClearFocusedTargetAfterRecovery(
                                recovery.vehiclePoolRecovered,
                                recovery.result
                        )) {
                    previewPlateTracker.reset();
                    TargetSnapshot searchingTarget = targetStateMachine.reset();
                    pipeline.setTargetSnapshotIfCurrent(
                            searchingTarget,
                            continuityStamp
                    );
                }
                long committedPresentationGeneration =
                        previewPresentationBarrier.commitRebase(
                                presentationGeneration
                        );
                if (committedPresentationGeneration < 0L) {
                    android.util.Log.d(
                            "ALPR_PREVIEW_REFERENCE",
                            "defer=stale_rebase_bitmap presentation_generation="
                                    + presentationGeneration
                                    + " recovery_revision="
                                    + requestedRecoveryRebaseRevision
                    );
                    return;
                }
                previewSceneRecoveryRebaseAppliedRevision =
                        requestedRecoveryRebaseRevision;
                android.util.Log.d(
                        "ALPR_PRESENTATION_BARRIER",
                        "commit_rebase generation="
                                + committedPresentationGeneration
                                + " recovery_revision="
                                + requestedRecoveryRebaseRevision
                );
                android.util.Log.d(
                        "ALPR_PREVIEW_REFERENCE",
                        "rebase=coordinator_recovery revision="
                                + runtimeContinuity.decisionRevision
                                + " result=" + recovery.result
                );
                // Ta bitmapa jest wyłącznie nową referencją. Jej ponowne
                // ocenienie w tym samym przebiegu mogłoby natychmiast
                // uruchomić kolejny reacquire, zanim pojawi się świeża klatka.
                return;
            }

            SceneAnchorGuard.Result anchorResult =
                    evaluatePreview(previewSceneAnchorGuard,previewBitmap,observation);
            SceneChangeDetector.Result scene =
                    updatePreviewScene(previewBitmap,observation);
            SceneAnchorGuard.Result zoomedAnchorResult =
                    evaluatePreview(autoZoomZoomedSceneAnchorGuard,previewBitmap,observation);
            PreviewTrackingFrame trackingFrame = directLumaTrackingFresh()
                    ? null
                    : (observation==null?previewPlateTracker.updateTracking(previewBitmap)
                            :previewPlateTracker.updateLuma(observation.gray,observation.width,observation.height,observation.mask));
            List<OverlayItem> trackedItems = trackingFrame == null
                    ? null : trackingFrame.overlayItems;
            if (trackingFrame != null && pipeline != null) {
                boolean localVehiclesAvailable = !cameraTransformInProgress
                        && !previewVehicleTrackerAwaitingFreshMpAnchor
                        && !previewVehicleTrackerAwaitingFirstLocalFrame;
                PreviewTrackingFrame fallbackVehicles = localVehiclesAvailable
                        ? (observation == null ? previewVehicleTracker.updateTracking(previewBitmap)
                        : previewVehicleTracker.updateLuma(observation.gray, observation.width, observation.height, observation.mask)) : null;
                pipeline.observeVehicleIdentity(continuityStamp, fallbackVehicles, localVehiclesAvailable, trackingFrame, true);
            }

            TargetSnapshot targetBeforeTracking = targetStateMachine.snapshot();
            TargetSnapshot targetAfterTracking = targetBeforeTracking;
            if (trackingFrame != null && pipeline != null) {
                targetAfterTracking = targetStateMachine.onTrackingFrame(
                        trackingFrame
                );
                pipeline.setTargetSnapshotIfCurrent(
                        targetAfterTracking,
                        continuityStamp
                );
            }

            boolean zoomedRetry = autoZoomController.state()
                    == AutoZoomController.State.ZOOMED_RETRY;
            boolean changed = scene.sceneChanged
                    || anchorResult.changed
                    || (zoomedRetry && zoomedAnchorResult.changed);
            boolean establishedFocusedTarget = PreviewContinuityUiPolicy
                    .isEstablishedFocusedTarget(
                            targetBeforeTracking.state,
                            targetBeforeTracking.lockedTrackId
                    );
            boolean trackingLost = trackingFrame != null
                    && establishedFocusedTarget
                    && trackedItems.isEmpty();
            boolean trackingDegraded = trackingFrame != null
                    && establishedFocusedTarget
                    && targetAfterTracking.state == TargetSnapshot.State.DEGRADED;
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
            MotionExplanationEvidence previewMotionEvidence =
                    currentPreviewMotionEvidence();
            if (PreviewContinuityUiPolicy.shouldActivatePresentationBarrier(
                    changed,
                    evidenceFraction,
                    previewMotionEvidence.cameraMoving,
                    previewMotionEvidence.motionSettling
            )) {
                activateAbruptScenePresentationBarrier(evidenceFraction);
            }
            refreshPipelineCameraMotionEvidence(previewMotionEvidence);
            final SceneTransitionDecision continuityDecision;
            AlprPipeline activePipeline = pipeline;
            if (activePipeline != null) {
                /*
                 * MP i monitor bitmap pracują na różnych executorach. Bitmapa
                 * pobrana podczas REACQUIRING może czekać na synchronized MP,
                 * a następnie wejść tutaj już po udanym recovery. Sprawdzenie
                 * pod tym samym monitorem co pipeline zapobiega wysłaniu do
                 * koordynatora dowodu policzonego względem starej referencji.
                 */
                synchronized (ordinaryDynamicResourcesEnabled()?ordinaryPreviewCoordinationLock:activePipeline) {
                    SceneContinuitySnapshot submissionContinuity =
                            activePipeline.sceneContinuitySnapshot();
                    long requestedRebaseRevision =
                            previewSceneRecoveryRebaseRevision.get();
                    if (PreviewContinuityUiPolicy
                            .shouldApplyRecoveredSceneRebase(
                                    requestedRebaseRevision,
                                    previewSceneRecoveryRebaseAppliedRevision,
                                    submissionContinuity.state
                            )) {
                        android.util.Log.d(
                                "ALPR_PREVIEW_REFERENCE",
                                "defer=stale_preview_evidence recovery_revision="
                                        + requestedRebaseRevision
                                        + " coordinator_revision="
                                        + submissionContinuity.decisionRevision
                        );
                        return;
                    }
                    continuityDecision = activePipeline.onWorkerPreviewSceneEvidence(
                            continuityStamp.sourceFrameStamp(),
                            changed,
                            evidenceScore,
                            evidenceFraction,
                            scene.brightnessDelta,
                            Math.max(
                                    anchorResult.score,
                                    zoomedAnchorResult.score
                            ),
                            Math.max(
                                    anchorResult.changedFraction,
                                    zoomedAnchorResult.changedFraction
                            ),
                            trackingLost,
                            trackingDegraded,
                            previewMotionEvidence
                    );
                }
            } else {
                continuityDecision = null;
            }
            if (PreviewContinuityUiPolicy.requestsRecoveryRebase(
                    continuityDecision
            )) {
                resetGlobalPreviewMotionState();
                previewSceneRecoveryRebaseRevision.accumulateAndGet(
                        continuityDecision.revision,
                        Math::max
                );
            }
            if (PreviewContinuityUiPolicy
                    .shouldCommitStablePresentationBarrier(
                            previewPresentationBarrier.active(),
                            changed,
                            continuityDecision
                    )) {
                long committedGeneration =
                        previewPresentationBarrier.commitRebase(
                                presentationGeneration
                        );
                if (committedGeneration >= 0L) {
                    android.util.Log.d(
                            "ALPR_PRESENTATION_BARRIER",
                            "commit_stable generation=" + committedGeneration
                                    + " reason=" + continuityDecision.reason
                    );
                    return;
                }
            }

            bitmapHandedToUi = true;
            runOnUiThread(() -> {
                try {
                    if (ordinaryDynamicResourcesEnabled()) return;
                    refreshVehicleIdentityPresentation();
                    if (renderStaticIdle()) return;
                    if (!cameraStarted
                            || sceneGeneration != uiSceneGeneration.get()
                            || transformGeneration
                            != uiCameraTransformGeneration.get()) {
                        return;
                    }
                    boolean presentationBlocked =
                            previewPresentationBarrier.active();
                    List<OverlayItem> presentationTrackedItems = presentationBlocked
                            ? java.util.Collections.emptyList() : trackedItems;
                    PreviewContinuityUiPolicy.DynamicOverlayDisposition disposition =
                            presentationBlocked
                            ? PreviewContinuityUiPolicy.DynamicOverlayDisposition.CLEAR
                            : PreviewContinuityUiPolicy.dynamicOverlayDisposition(
                            effectiveSceneHandlingMode(),
                            continuityDecision,
                            changed,
                            evidenceFraction
                    );
                    if (disposition
                            != PreviewContinuityUiPolicy.DynamicOverlayDisposition.KEEP
                            || isDynamicCameraMotion()) {
                        applyDynamicOverlayDisposition(
                                disposition,
                                presentationTrackedItems
                        );
                    }
                    PreviewContinuityUiPolicy.Outcome uiOutcome =
                            PreviewContinuityUiPolicy.decide(
                                    continuityDecision,
                                    changed,
                                    presentationTrackedItems != null
                                            && !presentationTrackedItems.isEmpty()
                            );
                    recordPreviewDecisionAuthority(uiOutcome, "bitmap");
                    if (uiOutcome.renderCoordinatorDecision) {
                        renderPreviewContinuityDecision(
                                continuityDecision,
                                evidenceScore,
                                evidenceFraction,
                                presentationTrackedItems
                        );
                        autoZoomDynamicFrameGraceCount = 0;
                        if (uiOutcome.rebaseAcceptedPreviewScene) {
                            previewSceneDetector.reset();
                            updatePreviewScene(previewBitmap,observation);
                            anchorPreview(previewSceneAnchorGuard,previewBitmap,observation);
                            anchorPreview(autoZoomZoomedSceneAnchorGuard,previewBitmap,observation);
                        }
                        if (uiOutcome.presentTrackedOverlay) {
                            presentTrackedPreviewOverlay(
                                    presentationTrackedItems
                            );
                        }
                        return;
                    }
                    autoZoomDynamicFrameGraceCount = 0;
                    if (uiOutcome.legacySceneInvalidation) {
                        invalidateUiForPreviewSceneChange(
                                Math.max(scene.score, Math.max(
                                        anchorResult.score,
                                        zoomedAnchorResult.score
                                )),
                                Math.max(scene.changedFraction, Math.max(
                                        anchorResult.changedFraction,
                                        zoomedAnchorResult.changedFraction
                                ))
                        );
                    } else if (uiOutcome.legacyTrackingLossInvalidation) {
                        invalidateUiForLostPreviewTracking();
                    } else if (uiOutcome.presentTrackedOverlay) {
                        presentTrackedPreviewOverlay(presentationTrackedItems);
                    }
                } finally {
                    recycleBudgetedPreviewBitmap(previewBitmap);
                    previewFrameInFlight.set(false);
                }
            });
        } catch (RuntimeException ignored) {
            if(domain!=null) handleObservationFailure(domain,continuityStamp,ignored);
            // PreviewView może zmienić powierzchnię podczas restartu kamery.
        } finally {
            if (!bitmapHandedToUi) {
                recycleBudgetedPreviewBitmap(previewBitmap);
                previewFrameInFlight.set(false);
            }
        }
    }

    private SceneObservationDomain observationDomain(){return pipeline==null?null:pipeline.sceneObservationDomain();}
    private void anchorPreview(SceneAnchorGuard guard,Bitmap bitmap,SceneObservationDomain.Frame frame){
        if(frame==null)guard.anchor(bitmap);else guard.anchorLuma(frame.gray,frame.width,frame.height,frame.mask);
    }
    private SceneAnchorGuard.Result evaluatePreview(SceneAnchorGuard guard,Bitmap bitmap,SceneObservationDomain.Frame frame){
        return frame==null?guard.evaluate(bitmap):guard.evaluateLuma(frame.gray,frame.width,frame.height,frame.mask);
    }
    private SceneChangeDetector.Result updatePreviewScene(Bitmap bitmap,SceneObservationDomain.Frame frame){
        return frame==null?previewSceneDetector.update(bitmap):previewSceneDetector.updateLuma(frame.gray,frame.width,frame.height,frame.mask);
    }
    /** A stale worker may neither observe a new domain nor stop a newer session. */
    private void handleObservationFailure(SceneObservationDomain domain,ContinuityStamp stamp,RuntimeException error){
        if(domain==null||domain!=observationDomain()||!isCurrentPreviewStamp(stamp))return;
        com.example.alpr_v1.experiment.ResearchSessionStore store=researchSessions.store();
        if(store!=null){
            try{if(store.tvBboxEvidence()!=null)store.tvBboxEvidence().rejected(null,stamp,error.getMessage())
                    .complete("REJECTED_GEOMETRY",error.getMessage()==null?"TV_OBSERVATION_INVALID":error.getMessage());}
            catch(RuntimeException evidenceError){android.util.Log.e("ALPR_TV_OBSERVATION","Rejection evidence unavailable",evidenceError);}
            finally{store.closeAdmission("TV_OBSERVATION_INVALID",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos());}
        }
        runOnUiThread(()->{
            if(domain!=observationDomain())return;
            recordError("Obserwacja TV zatrzymana: nieaktualna lub niedostępna geometria polygonu. "+error.getMessage(),error);
            stopAnalysis(ExperimentSession.CompletionReason.ERROR);
            livePresentation.showUserStatus(LivePresentationController.State.ERROR,
                    "Obserwacja TV zatrzymana", "Nieaktualna lub niedostępna maska TV. Sprawdź kadr i potwierdź kalibrację. "+error.getMessage());
        });
    }

    private static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private SourceFrameStamp currentPreviewSourceFrameStamp() {
        SceneContinuitySnapshot snapshot = pipeline == null
                ? null : pipeline.sceneContinuitySnapshot();
        long sceneGeneration = snapshot == null ? 0L : snapshot.sceneGeneration;
        long visualEpoch = snapshot == null ? 0L : snapshot.visualEpoch;
        long transformGeneration = snapshot == null
                ? 0L : snapshot.cameraTransformGeneration;
        if (cameraController == null) {
            return SourceFrameStamp.unknown(
                    sceneGeneration,
                    visualEpoch,
                    transformGeneration
            );
        }
        return cameraController.sourceTimeline().current(
                sceneGeneration,
                visualEpoch,
                transformGeneration
        );
    }

    private SourceFrameStamp cameraSourceFrameStamp(
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain
    ) {
        SceneContinuitySnapshot snapshot = pipeline == null
                ? null : pipeline.sceneContinuitySnapshot();
        return new SourceFrameStamp(
                sourceSequence,
                Math.max(0L, sourceTimestampNanos),
                sourceTimestampDomain,
                snapshot == null ? 0L : snapshot.sceneGeneration,
                snapshot == null ? 0L : snapshot.visualEpoch,
                snapshot == null ? 0L : snapshot.cameraTransformGeneration
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

    private boolean isCurrentPreviewStamp(ContinuityStamp stamp) {
        AlprPipeline activePipeline = pipeline;
        return activePipeline != null
                && stamp != null
                && activePipeline.isCurrentContinuityStamp(stamp);
    }

    private static final class PreviewFrameMotionSample {
        final FrameMotionTransform motion;
        final LumaSceneChangeDetector.Result sceneChange;
        final PreviewPlateTracker.CameraMotionSample cameraMotion;
        final long[] motionStageNanos;

        PreviewFrameMotionSample(
                FrameMotionTransform motion,
                LumaSceneChangeDetector.Result sceneChange,
                PreviewPlateTracker.CameraMotionSample cameraMotion,long[] motionStageNanos
        ) {
            this.motion = motion;
            this.sceneChange = sceneChange;
            this.cameraMotion=cameraMotion;
            this.motionStageNanos=motionStageNanos;
        }
    }
    // Guarded by previewMotionStateLock; this is the actual global image reference.
    private SourceFrameStamp globalLumaMotionSource;

    private PreviewFrameMotionSample updatePreviewFrameMotion(
            LumaFrame frame,
            ContinuityStamp stamp,
            boolean cameraMotionConfirmedBySensor
    ) { return updatePreviewFrameMotion(frame,stamp,cameraMotionConfirmedBySensor,null); }
    private PreviewFrameMotionSample updatePreviewFrameMotion(LumaFrame frame,ContinuityStamp stamp,
            boolean cameraMotionConfirmedBySensor,SceneObservationDomain.Frame observation) {
        byte[] gray=observation==null?frame.gray:observation.gray;
        int width=observation==null?frame.width:observation.width,height=observation==null?frame.height:observation.height;
        ObservationMask mask=observation==null?null:observation.mask;
        List<com.example.alpr_v1.domain.NormalizedBounds> foregroundMasks =
                recentVehicleForegroundMasks();
        byte[] motionGray=gray;int motionWidth=width,motionHeight=height;
        if(mask==null&&ordinaryDynamicResourcesEnabled()&&width>96) {
            // The coarse global recovery search scales with both search area
            // and image area. It must not delay the independent local trackers.
            motionWidth=96;motionHeight=Math.max(1,Math.round(height*96f/width));
            motionGray=new byte[motionWidth*motionHeight];
            for(int y=0;y<motionHeight;y++)for(int x=0;x<motionWidth;x++) {
                int left=x*width/motionWidth,right=Math.max(left+1,(x+1)*width/motionWidth);
                int top=y*height/motionHeight,bottom=Math.max(top+1,(y+1)*height/motionHeight),sum=0,count=0;
                for(int sy=top;sy<bottom;sy++)for(int sx=left;sx<right;sx++){sum+=gray[sy*width+sx]&255;count++;}
                motionGray[y*motionWidth+x]=(byte)(sum/count);
            }
        }
        synchronized (previewMotionStateLock) {
            if (previewMotionGenerationGate.enter(stamp)) {
                globalLumaMotionTracker.reset();
                globalLumaMotionSource=null;
                lumaSceneChangeDetector.reset();
                frameMotionHistory.reset();
                visualMotionEvidenceDecay.reset();
            }
            LumaSceneChangeDetector.Result sceneChange =
                    lumaSceneChangeDetector.update(
                            gray, width, height,
                            foregroundMasks, mask
                    );
            SourceFrameStamp previousMotionSource=globalLumaMotionSource;
            FrameMotionTransform motion = globalLumaMotionTracker.update(
                    motionGray, motionWidth, motionHeight,
                    foregroundMasks,
                    cameraMotionConfirmedBySensor, mask
            );
            frameMotionHistory.record(frame.timestampNanos, motion);
            globalLumaMotionSource=stamp.sourceFrameStamp();
            return new PreviewFrameMotionSample(motion,sceneChange,
                    new PreviewPlateTracker.CameraMotionSample(previousMotionSource,globalLumaMotionSource,motion),globalLumaMotionTracker.stageNanos());
        }
    }

    private List<com.example.alpr_v1.domain.NormalizedBounds>
    recentVehicleForegroundMasks() {
        AlprPipeline activePipeline = pipeline;
        if (activePipeline == null) return java.util.Collections.emptyList();
        VehicleTrackingFrame frame = activePipeline.latestVehicleTrackingFrame();
        if (frame == null || frame.candidates.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        long maximumAgeNanos = vehicleOverlayMaximumAgeNanos(
                        activePipeline.lastMpObservationGapNanos()
                );
        List<com.example.alpr_v1.domain.NormalizedBounds> masks =
                new ArrayList<>();
        for (com.example.alpr_v1.tracking.VehicleCandidate candidate
                : frame.candidates) {
            if (candidate != null
                    && candidate.bounds != null
                    && candidate.bounds.valid()
                    && candidate.predictionAgeNanos <= maximumAgeNanos) {
                masks.add(candidate.bounds);
            }
        }
        return java.util.Collections.unmodifiableList(masks);
    }

    private void resetGlobalPreviewMotionState() {
        resetGlobalPreviewMotionState(true);
    }
    private void resetGlobalPreviewMotionState(boolean clearSourcePixels) {
        if(clearSourcePixels)ordinaryLumaHistory.clear();
        vehiclePreviewMotionPolicy.reset();
        stationarySceneSupport.reset();
        synchronized (previewMotionStateLock) {
            globalLumaMotionTracker.reset();
            globalLumaMotionSource=null;
            lumaSceneChangeDetector.reset();
            frameMotionHistory.reset();
            previewMotionGenerationGate.reset();
            visualMotionEvidenceDecay.reset();
            previewVehicleGeometryFreshAtNanos = 0L;
            previewVehicleTrackerAwaitingFirstLocalFrame = false;
        }
    }

    private void activateAbruptScenePresentationBarrier(
            float changedFraction
    ) {
        if (previewPresentationBarrier.active()) return;
        long barrierGeneration = previewPresentationBarrier.activate();
        pendingPreviewCoordination.set(null);
        resetGlobalPreviewMotionState();
        previewPlateTracker.reset();
        previewVehicleTracker.reset();
        android.util.Log.d(
                "ALPR_PRESENTATION_BARRIER",
                String.format(
                        Locale.ROOT,
                        "activate generation=%d changed_fraction=%.3f",
                        barrierGeneration,
                        changedFraction
                )
        );
        runOnUiThread(() -> {
            if (!previewPresentationBarrier.isActiveGeneration(
                    barrierGeneration
            )) return;
            overlayTracker.reset();
            plateOverlayFreshness.reset();
            latestDiagnosticOverlayItems = java.util.Collections.emptyList();
            latestDiagnosticOverlayAnchorTimestampNanos = 0L;
            latestPipelinePlateItems = java.util.Collections.emptyList();
            latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
            latestPreviewMotionItems = java.util.Collections.emptyList();
            latestPipelinePlateEntityId = 0L;
            overlayView.setFocusedTrackId(0L);
            overlayView.setActiveVehicleEntityId(0L);
            overlayView.clearPlateItems();
            overlayView.setStationaryScene(stationarySceneSupported());
            overlayView.setItems(java.util.Collections.emptyList());
            overlayView.setStationaryScene(stationarySceneSupported());
            overlayView.setPreviewItems(java.util.Collections.emptyList());
            overlayView.hardResetForNewScene(currentPreviewSourceFrameStamp().continuityStamp());
            livePresentation.clearResult();
            livePresentation.showState(
                    LivePresentationController.State.RECOVERING,
                    "Zmiana widoku — weryfikacja sceny"
            );
        });
    }

    private void processDirectLumaFrame(LumaFrame frame) {
        processDirectLumaFrame(frame,observationDomain());
    }
    private void processDirectLumaFrame(LumaFrame frame,SceneObservationDomain domain) {
        if(domain!=observationDomain()) return;
        final long lumaWorkStarted=android.os.SystemClock.elapsedRealtimeNanos();
        /*
         * Direct-luma comes from the camera stream, not from PreviewView. UI
         * overlay holds and post-return validation therefore cannot suppress
         * scene-cut evidence. Only a real optical transform or an already
         * active presentation barrier is allowed to pause this channel.
         */
        boolean staticScene = effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY;
        boolean ordinaryDynamic = ordinaryDynamicResourcesEnabled();
        if (frame == null || !cameraStarted || cameraTransformInProgress
                || !staticScene && !ordinaryDynamic && previewPresentationBarrier.active()) return;
        final long presentationGeneration = previewPresentationBarrier.capture();
        if (!staticScene && !ordinaryDynamic && !previewPresentationBarrier.permits(presentationGeneration)) return;
        if (!staticScene && !ordinaryDynamic && PreviewContinuityUiPolicy.shouldSuspendDirectLumaEvidence(
                previewSceneRecoveryRebaseRevision.get(),
                previewSceneRecoveryRebaseAppliedRevision
        )) return;
        final long sceneGeneration = uiSceneGeneration.get();
        final long transformGeneration = uiCameraTransformGeneration.get();
        final SourceFrameStamp lumaSourceFrame = cameraSourceFrameStamp(
                frame.sourceSequence,
                frame.timestampNanos,
                frame.timestampDomain
        );
        final ContinuityStamp continuityStamp = lumaSourceFrame.continuityStamp();
        if (!isCurrentPreviewStamp(continuityStamp)) return;
        final SceneObservationDomain.Frame observation;
        try {
            if(domain!=null) domain.requireGeometry(tvSetup==null?null:tvSetup.observationGeometry());
            observation=domain==null?null:domain.luma(frame);
        } catch(RuntimeException invalid){handleObservationFailure(domain,continuityStamp,invalid);return;}
        byte[] gray=observation==null?frame.gray:observation.gray;
        int width=observation==null?frame.width:observation.width,height=observation==null?frame.height:observation.height;
        ObservationMask mask=observation==null?null:observation.mask;
        if(ordinaryDynamicResourcesEnabled())try {
            ordinaryLumaHistory.offer(lumaSourceFrame,gray,width,height);
        } catch(OutOfMemoryError exhausted) {
            ordinaryLumaHistory.clear();ordinaryMemoryPaused=true;
            runOnUiThread(this::pauseOrdinaryForMemory);return;
        }
        if (ordinaryDynamic && rebaseOrdinaryLumaReference(frame, observation,
                continuityStamp, presentationGeneration)) return;
        lastDirectLumaFrameNanos = System.nanoTime();
        lastDirectLumaSourceTimestampNanos = frame.timestampNanos;
        if (staticScene && pipeline != null) {
            SceneTransitionDecision staticDecision = pipeline.observeStaticLuma(continuityStamp,
                    gray, width, height, android.os.SystemClock.elapsedRealtimeNanos(),
                    cameraTransformInProgress || domain==null && currentCameraZoomRatio > 1.01f, mask);
            if (staticDecision != null) {
                runOnUiThread(() -> renderPreviewContinuityDecision(staticDecision, 1f, 1f,
                        java.util.Collections.emptyList()));
                return;
            }
            return;
        }
        CameraMotionMonitor earlyMotionMonitor = cameraMotionMonitor;
        boolean earlySensorMotion = earlyMotionMonitor != null
                && earlyMotionMonitor.isMoving();
        long motionStarted=android.os.SystemClock.elapsedRealtimeNanos();
        PreviewFrameMotionSample motionSample = updatePreviewFrameMotion(
                frame,
                continuityStamp,
                earlySensorMotion, observation
        );
        long motionElapsed=android.os.SystemClock.elapsedRealtimeNanos()-motionStarted;
        FrameMotionTransform frameMotion = motionSample.motion;
        if (!isCurrentPreviewStamp(continuityStamp)
                || !previewPresentationBarrier.permits(presentationGeneration)) {
            resetGlobalPreviewMotionState();
            previewPlateTracker.reset();
            previewVehicleTracker.reset();
            return;
        }
        AlprPipeline motionPipeline = pipeline;
        if (motionPipeline != null) {
            motionPipeline.recordPreviewFrameMotion(
                    continuityStamp,
                    frame.timestampNanos,
                    frameMotion
            );
        }
        VisualMotionEvidenceDecay.Snapshot earlyVisualMotion =
                visualMotionEvidenceDecay.snapshot(
                        android.os.SystemClock.elapsedRealtimeNanos()
                );
        boolean earlyAutoZoomSensorMotion = !experimentModeEnabled && pipeline.researchExecutionConfig() == null
                ? earlyMotionMonitor != null && earlyMotionMonitor.isRapidMotion() : earlySensorMotion;
        if (effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY
                && autoZoomController.state() == AutoZoomController.State.ZOOMED_RETRY
                && com.example.alpr_v1.acquisition.DynamicZoomPolicy.shouldAbort(earlyVisualMotion.rapid
                        || earlyAutoZoomSensorMotion,
                targetStateMachine.snapshot().state == TargetSnapshot.State.LOST, pipeline.sceneContinuitySnapshot().state)) {
            runOnUiThread(() -> {
                if (!cameraStarted || autoZoomController.state() != AutoZoomController.State.ZOOMED_RETRY) return;
                clearAutoZoomRecognitionMemory();
                requestAutoZoomReturn(null);
                pipelineInferenceExecutor.execute(() -> pipeline.requestImmediateTargetRecovery());
            });
            return;
        }
        long evidenceNow = android.os.SystemClock.elapsedRealtimeNanos();
        stationarySceneSupport.setObservationInterval(
                motionPipeline == null ? 0L : motionPipeline.lastMpObservationGapNanos());
        boolean measuredSceneMotion = earlySensorMotion || isVisualCameraMotion(frameMotion)
                || cameraTransformInProgress || motionSample.sceneChange.globalChanged
                || motionSample.sceneChange.globalChangedFraction >= 0.20f;
        if (measuredSceneMotion) {
            stationarySceneSupport.observe(evidenceNow, false);
        } else if (motionSample.sceneChange.globalSamples >= 128
                && motionSample.sceneChange.globalChangedFraction < 0.05f
                && motionSample.sceneChange.globalMeanDelta < 8f) {
            stationarySceneSupport.observe(evidenceNow, true);
        } else {
            stationarySceneSupport.observeUncertain(evidenceNow);
        }
        if (motionPipeline != null) {
            motionPipeline.recordStationarySceneEvidence(continuityStamp,
                    stationarySceneSupported()
                            && motionSample.sceneChange.globalSamples >= 128
                            && motionSample.sceneChange.globalChangedFraction < 0.01f
                            && motionSample.sceneChange.globalMeanDelta < 3f,
                    evidenceNow);
        }
        if (motionSample.sceneChange.globalChangedFraction >= 0.20f
                || motionSample.sceneChange.changedFraction >= 0.08f) {
            android.util.Log.d(
                    "ALPR_LUMA_AUDIT",
                    String.format(
                            Locale.ROOT,
                            "masked_fraction=%.3f masked_delta=%.1f "
                                    + "global_fraction=%.3f global_delta=%.1f "
                                    + "sensor_motion=%s visual_motion=%s recent_visual_motion=%s settling=%s",
                            motionSample.sceneChange.changedFraction,
                            motionSample.sceneChange.meanDelta,
                            motionSample.sceneChange.globalChangedFraction,
                            motionSample.sceneChange.globalMeanDelta,
                            earlySensorMotion,
                            isVisualCameraMotion(frameMotion),
                            earlyVisualMotion.motionEstimated,
                            earlyVisualMotion.settling
                    )
            );
        }
        if (!ordinaryDynamic && effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY && PreviewContinuityUiPolicy
                .shouldForceHardSceneBoundaryFromDirectLuma(
                motionSample.sceneChange.changed,
                motionSample.sceneChange.changedFraction,
                motionSample.sceneChange.meanDelta,
                motionSample.sceneChange.globalChanged,
                motionSample.sceneChange.globalChangedFraction,
                motionSample.sceneChange.globalMeanDelta,
                earlySensorMotion || isVisualCameraMotion(frameMotion),
                earlyVisualMotion.protectsContinuity()
        )) {
            boolean maskedBoundary = motionSample.sceneChange.changed;
            float boundaryFraction = maskedBoundary
                    ? motionSample.sceneChange.changedFraction
                    : motionSample.sceneChange.globalChangedFraction;
            float boundaryMeanDelta = maskedBoundary
                    ? motionSample.sceneChange.meanDelta
                    : motionSample.sceneChange.globalMeanDelta;
            android.util.Log.d(
                    "ALPR_LUMA_SCENE_CHANGE",
                    String.format(
                            Locale.ROOT,
                            "masked_fraction=%.3f masked_delta=%.1f samples=%d "
                                    + "global_fraction=%.3f global_delta=%.1f "
                                    + "global_samples=%d authority=%s",
                            motionSample.sceneChange.changedFraction,
                            motionSample.sceneChange.meanDelta,
                            motionSample.sceneChange.samples,
                            motionSample.sceneChange.globalChangedFraction,
                            motionSample.sceneChange.globalMeanDelta,
                            motionSample.sceneChange.globalSamples,
                            maskedBoundary ? "masked" : "unmasked"
                    )
            );
            activateAbruptScenePresentationBarrier(
                    boundaryFraction
            );
            AlprPipeline activePipeline = pipeline;
            SceneTransitionDecision resetDecision = activePipeline == null
                    ? null : activePipeline.requestAbruptSceneReset(
                    lumaSourceFrame,
                    boundaryFraction,
                    boundaryMeanDelta
            );
            if (resetDecision != null) {
                float resetScore = Math.max(
                        boundaryFraction,
                        Math.min(
                                1f,
                                boundaryMeanDelta / 255f
                        )
                );
                runOnUiThread(() -> renderPreviewContinuityDecision(
                        resetDecision,
                        resetScore,
                        boundaryFraction,
                        java.util.Collections.emptyList()
                ));
            }
            return;
        }
        boolean acceptedCameraMotion = isVisualCameraMotion(frameMotion)
                || earlySensorMotion
                && isSensorConfirmedFrameMotion(frameMotion);
        if (acceptedCameraMotion) {
            previewVehicleGeometryFreshAtNanos =
                    android.os.SystemClock.elapsedRealtimeNanos();
            float frameDx = frameMotion.mapX(0.5f, 0.5f) - 0.5f;
            float frameDy = frameMotion.mapY(0.5f, 0.5f) - 0.5f;
            boolean visualRapid = frameDx * frameDx + frameDy * frameDy
                    >= 0.035f * 0.035f;
            if (frameMotion.quality.reliableCameraMotion()) {
                visualMotionEvidenceDecay.record(
                        android.os.SystemClock.elapsedRealtimeNanos(),
                        frameMotion.quality,
                        visualRapid
                );
            }
            android.util.Log.d(
                    "ALPR_GLOBAL_MOTION",
                    String.format(
                            Locale.ROOT,
                            "timestamp_ns=%d dx=%.5f dy=%.5f inliers=%d "
                                    + "ratio=%.3f coverage=%.3f quadrants=%d "
                                    + "coherence=%.3f residual=%.3f sensor_confirmed=%s",
                            frame.timestampNanos,
                            frameMotion.mapX(0.5f, 0.5f) - 0.5f,
                            frameMotion.mapY(0.5f, 0.5f) - 0.5f,
                            frameMotion.inliers,
                            frameMotion.quality.inlierRatio,
                            frameMotion.quality.spatialCoverage,
                            frameMotion.quality.occupiedQuadrants,
                            frameMotion.quality.coherenceScore,
                            frameMotion.quality.meanResidual,
                            !frameMotion.quality.reliableCameraMotion()
                    )
            );
        }
        long trackingStartedNanos =
                android.os.SystemClock.elapsedRealtimeNanos();
        boolean cameraMotionOwnsVehicleGeometry = !ordinaryDynamicResourcesEnabled() && vehiclePreviewMotionPolicy.update(
                earlySensorMotion || earlyVisualMotion.motionEstimated || isVisualCameraMotion(frameMotion),
                android.os.SystemClock.elapsedRealtimeNanos());
        if (cameraMotionOwnsVehicleGeometry
                && !previewVehicleTrackerAwaitingFreshMpAnchor) {
            previewVehicleTracker.reset();
            previewVehicleTrackerAwaitingFreshMpAnchor = true;
            previewVehicleTrackerAwaitingFirstLocalFrame = false;
            android.util.Log.d(
                    "ALPR_VEHICLE_PREVIEW_TRACK",
                    "mode=GLOBAL_FRAME_MOTION awaiting_fresh_mp_anchor=true"
            );
        }
        if (!cameraMotionOwnsVehicleGeometry
                && previewVehicleTrackerAwaitingFreshMpAnchor) {
            long rebaseNowNanos = android.os.SystemClock.elapsedRealtimeNanos();
            long rebaseMaximumAgeNanos = vehicleOverlayMaximumAgeNanos(
                            pipeline == null ? 0L : pipeline.lastMpObservationGapNanos()
                    );
            long rebaseGeometryAgeNanos = previewVehicleGeometryFreshAtNanos <= 0L
                    ? Long.MAX_VALUE
                    : Math.max(0L, rebaseNowNanos - previewVehicleGeometryFreshAtNanos);
            if (rebaseGeometryAgeNanos <= rebaseMaximumAgeNanos
                    && countKind(latestPreviewMotionItems, OverlayItem.Kind.VEHICLE) > 0
                    && latestOverlaySourceWidth > 0
                    && latestOverlaySourceHeight > 0) {
                anchorPreviewVehicleTracker(
                        latestPreviewMotionItems,
                        latestOverlaySourceWidth,
                        latestOverlaySourceHeight,
                        "GLOBAL_PRESENTATION"
                );
            }
        }
        PreviewTrackingFrame vehicleTrackingFrame =
                previewVehicleTrackerAwaitingFreshMpAnchor
                        ? null
                        : previewVehicleTracker.updateLuma(
                                gray, width, height, mask,lumaSourceFrame,ordinaryDynamicResourcesEnabled()?ordinaryLumaHistory:null,
                                ordinaryDynamicResourcesEnabled()?motionSample.cameraMotion:null
                        );
        PreviewTrackingFrame trackingFrame = previewPlateTracker.updateLuma(
                gray, width, height, mask,lumaSourceFrame,ordinaryDynamicResourcesEnabled()?ordinaryLumaHistory:null,
                ordinaryDynamicResourcesEnabled()?motionSample.cameraMotion:null
        );
        long trackingElapsedNanos = Math.max(
                0L,
                android.os.SystemClock.elapsedRealtimeNanos()
                        - trackingStartedNanos
        );
        if (!isCurrentPreviewStamp(continuityStamp)
                || !previewPresentationBarrier.permits(presentationGeneration)) {
            resetGlobalPreviewMotionState();
            previewPlateTracker.reset();
            previewVehicleTracker.reset();
            return;
        }
        float maximumQuality = 0f;
        long identityStarted=android.os.SystemClock.elapsedRealtimeNanos();
        if (pipeline != null) pipeline.observeVehicleIdentity(continuityStamp,
                vehicleTrackingFrame, !cameraMotionOwnsVehicleGeometry
                        && !previewVehicleTrackerAwaitingFreshMpAnchor
                        && !previewVehicleTrackerAwaitingFirstLocalFrame, trackingFrame, trackingFrame != null);
        long identityElapsed=android.os.SystemClock.elapsedRealtimeNanos()-identityStarted;
        int maximumInliers = 0;
        if (trackingFrame != null) {
            for (com.example.alpr_v1.tracking.TrackedPlate plate
                    : trackingFrame.trackedPlates) {
                maximumQuality = Math.max(maximumQuality, plate.trackingQuality);
                maximumInliers = Math.max(maximumInliers, plate.trackerInliers);
            }
        }
        com.example.alpr_v1.tracking.TrackedPlate bodyDiagnostic=vehicleTrackingFrame==null||vehicleTrackingFrame.trackedPlates.isEmpty()
                ?null:vehicleTrackingFrame.trackedPlates.get(0);
        LumaFrame newestLuma=latestCameraLuma;
        android.util.Log.d(
                "ALPR_TRACK_TIMING",
                String.format(
                        Locale.ROOT,
                        "timestamp_ns=%d update_ms=%.3f tracks=%d quality=%.3f inliers=%d body_state=%s plate_state=%s body_replay=%d plate_replay=%d body_n=%d body_q=%.3f body_app=%.3f body_fail=%d luma_ms=%.3f input_lag_ms=%.3f motion_ms=%.3f identity_ms=%.3f global_flow_ms=%.3f global_affine_ms=%.3f global_fallback_ms=%.3f",
                        frame.timestampNanos,
                        trackingElapsedNanos / 1_000_000.0,
                        trackingFrame == null ? 0 : trackingFrame.trackedPlates.size(),
                        maximumQuality,
                        maximumInliers,
                        vehicleTrackingFrame==null?"UNAVAILABLE":vehicleTrackingFrame.state.name(),
                        trackingFrame==null?"UNAVAILABLE":trackingFrame.state.name(),
                        previewVehicleTracker.lastReplaySteps(),previewPlateTracker.lastReplaySteps(),
                        vehicleTrackingFrame==null?0:vehicleTrackingFrame.trackedPlates.size(),
                        bodyDiagnostic==null?0f:bodyDiagnostic.trackingQuality,bodyDiagnostic==null?0f:bodyDiagnostic.localAppearanceSimilarity,
                        bodyDiagnostic==null?0:bodyDiagnostic.consecutiveFailures,
                        (android.os.SystemClock.elapsedRealtimeNanos()-lumaWorkStarted)/1_000_000.0,
                        newestLuma==null?0.0:Math.max(0L,newestLuma.timestampNanos-frame.timestampNanos)/1_000_000.0,
                        motionElapsed/1_000_000.0,identityElapsed/1_000_000.0,
                        motionSample.motionStageNanos[0]/1_000_000.0,motionSample.motionStageNanos[1]/1_000_000.0,motionSample.motionStageNanos[2]/1_000_000.0
                )
        );
        TargetSnapshot targetBeforeTracking = targetStateMachine.snapshot();
        TargetSnapshot targetAfterTracking = trackingFrame == null || trackingFrame.state!=PreviewTrackingFrame.State.LIVE
                ? targetBeforeTracking
                : targetStateMachine.onTrackingFrame(trackingFrame);
        if (!isCurrentPreviewStamp(continuityStamp)
                || !previewPresentationBarrier.permits(presentationGeneration)) {
            targetStateMachine.reset();
            previewPlateTracker.reset();
            previewVehicleTracker.reset();
            resetGlobalPreviewMotionState();
            return;
        }
        List<OverlayItem> trackedItems = trackingFrame == null
                ? java.util.Collections.emptyList()
                : trackingFrame.overlayItems;
        List<OverlayItem> locallyTrackedVehicles = new ArrayList<>(trackedVehicleOverlayItems(vehicleTrackingFrame));
        if (pipeline != null) {
            java.util.Set<Long> localOwners = new java.util.HashSet<>();
            for (OverlayItem item : locallyTrackedVehicles) localOwners.add(item.trackId);
            for (OverlayItem item : pipeline.plateAnchoredVehicleItems(frame.timestampNanos))
                if (!localOwners.contains(item.trackId)) locallyTrackedVehicles.add(item);
        }
        if (!locallyTrackedVehicles.isEmpty()) {
            previewVehicleGeometryFreshAtNanos =
                    android.os.SystemClock.elapsedRealtimeNanos();
            previewVehicleTrackerAwaitingFirstLocalFrame = false;
        }
        boolean establishedFocusedTarget = PreviewContinuityUiPolicy
                .isEstablishedFocusedTarget(
                        targetBeforeTracking.state,
                        targetBeforeTracking.lockedTrackId
                );
        boolean trackingLost = trackingFrame != null
                && establishedFocusedTarget
                && trackedItems.isEmpty();
        boolean trackingDegraded = trackingFrame != null
                && establishedFocusedTarget
                && targetAfterTracking.state == TargetSnapshot.State.DEGRADED;

        CameraMotionMonitor motionMonitor = cameraMotionMonitor;
        boolean sensorAvailable = motionMonitor != null
                && motionMonitor.isGyroscopeAvailable();
        boolean moving = motionMonitor != null && motionMonitor.isMoving();
        boolean rapid = motionMonitor != null && motionMonitor.isRapidMotion();
        float angularMagnitude = motionMonitor == null ? 0f : motionMonitor.magnitude();
        VisualMotionEvidenceDecay.Snapshot visualMotion =
                visualMotionEvidenceDecay.snapshot(
                        android.os.SystemClock.elapsedRealtimeNanos()
                );
        boolean visualMoving = visualMotion.motionEstimated;
        boolean effectiveMoving = moving || visualMoving;
        boolean effectiveRapid = rapid || visualMotion.rapid;
        if (pipeline != null) {
            pipeline.setPreviewMotionEvidence(new MotionExplanationEvidence(
                    sensorAvailable, effectiveMoving, effectiveRapid, angularMagnitude,
                    cameraTransformInProgress, visualMotion.motionEstimated,
                    visualMotion.quality.coherenceScore, normalizedMotionResidual(visualMotion.quality),
                    0f, 0f, visualMotion.settling));
        }

        // Publikacja geometrii nie czeka na synchronized ciężkiego pipeline'u.
        runOnUiThread(() -> {
            if (renderStaticIdle()) return;
            if (!cameraStarted
                    || sceneGeneration != uiSceneGeneration.get()
                    || transformGeneration != uiCameraTransformGeneration.get()
                    || cameraTransformInProgress
                    || !previewPresentationBarrier.permits(presentationGeneration)
                    || !isCurrentPreviewStamp(continuityStamp)) return;
            refreshVehicleIdentityPresentation();
            if(pipeline!=null&&pipeline.targetFocus().revision!=presentedFocusRevision)renderSearchControls();
            if (!trackedItems.isEmpty()) {
                presentTrackedPreviewOverlay(
                        trackedItems,
                        locallyTrackedVehicles,
                        frameMotion
                );
            } else if (effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY
                    || frameMotion.significant()
                    || !locallyTrackedVehicles.isEmpty()) {
                overlayView.setStationaryScene(stationarySceneSupported());
                overlayView.setPreviewItems(
                        dynamicCameraMotionOverlayItems(
                                trackedItems,
                                locallyTrackedVehicles,
                                frameMotion
                        )
                );
            }
        });

        if (!isCurrentPreviewStamp(continuityStamp)
                || !previewPresentationBarrier.permits(presentationGeneration)) return;
        submitPreviewCoordination(new PreviewCoordinationRequest(
                lumaSourceFrame,
                continuityStamp,
                targetAfterTracking,
                trackedItems,
                sceneGeneration,
                transformGeneration,
                presentationGeneration,
                trackingLost,
                trackingDegraded,
                sensorAvailable,
                moving,
                rapid,
                angularMagnitude,
                visualMotion.motionEstimated,
                visualMotion.rapid,
                visualMotion.settling,
                visualMotion.quality.coherenceScore,
                normalizedMotionResidual(visualMotion.quality),
                visualMotion.quality.inliers,
                visualMotion.quality.spatialCoverage,
                ordinaryDynamic&&(motionSample.sceneChange.changed||motionSample.sceneChange.globalChanged),
                Math.max(motionSample.sceneChange.changedFraction,motionSample.sceneChange.globalChangedFraction),
                Math.max(motionSample.sceneChange.meanDelta,motionSample.sceneChange.globalMeanDelta)
        ));
    }

    /** Recovery in ordinary DYN uses a fresh camera sample, never a Preview bitmap. */
    private boolean rebaseOrdinaryLumaReference(LumaFrame frame, SceneObservationDomain.Frame observation,
            ContinuityStamp stamp, long presentationGeneration) {
        long requested = previewSceneRecoveryRebaseRevision.get();
        if (!previewPresentationBarrier.active()
                && requested <= previewSceneRecoveryRebaseAppliedRevision) return false;
        AlprPipeline active = pipeline;
        if (active == null || !isCurrentPreviewStamp(stamp)
                || !previewPresentationBarrier.matchesGeneration(presentationGeneration)) return true;
        if (active.sceneContinuitySnapshot().state
                != com.example.alpr_v1.continuity.SceneContinuityState.STABLE) {
            // HARD_RESETTING needs a fresh source observation to release inference.
            // Recovery deadlines also need to advance while local evidence is N/A.
            final SceneTransitionDecision decision;
            synchronized (ordinaryPreviewCoordinationLock) {
                decision = active.onWorkerPreviewSceneEvidence(stamp.sourceFrameStamp(),
                        false, 0f, 0f, 0f, 0f, 0f, false, false, currentPreviewMotionEvidence());
            }
            if (decision != null && decision.action != SceneTransitionAction.NONE) runOnUiThread(() -> {
                if (cameraStarted && isCurrentPreviewStamp(stamp))
                    renderPreviewContinuityDecision(decision, 0f, 0f, java.util.Collections.emptyList());
            });
            if (active.sceneContinuitySnapshot().state
                    != com.example.alpr_v1.continuity.SceneContinuityState.STABLE) return true;
        }
        resetGlobalPreviewMotionState(false);
        updatePreviewFrameMotion(frame, stamp, false, observation);
        if (!isCurrentPreviewStamp(stamp)) return true;
        if (previewPresentationBarrier.commitRebase(presentationGeneration) < 0L) return true;
        previewSceneRecoveryRebaseAppliedRevision = requested;
        previewSceneAnchorPending = false;
        android.util.Log.d("ALPR_PREVIEW_REFERENCE", "rebase=camera_luma recovery_revision="
                + requested + " source=" + frame.timestampNanos);
        return true; // This sample establishes the reference; the next one supplies evidence.
    }

    private void submitDirectLumaFrame(LumaFrame frame) {
        if (frame == null || directLumaTrackingExecutor.isShutdown()) return;
        latestCameraLuma=frame;
        liveCameraFrameRate.onFrame(android.os.SystemClock.elapsedRealtimeNanos());
        SceneObservationDomain domain=observationDomain();
        // Capture source pixels before the latest-frame drain coalesces work.
        // Research TV domains retain their existing transformed/masked route.
        if(cameraStarted && domain==null && ordinaryDynamicResourcesEnabled())try {
            ordinaryLumaHistory.offer(cameraSourceFrameStamp(frame.sourceSequence,frame.timestampNanos,frame.timestampDomain),
                    frame.gray,frame.width,frame.height);
        } catch(OutOfMemoryError exhausted) {
            ordinaryLumaHistory.clear();ordinaryMemoryPaused=true;
            runOnUiThread(this::pauseOrdinaryForMemory);return;
        }
        pendingDirectLumaFrame.set(new PendingObservation(frame,domain));
        if (directLumaWorkerRunning.compareAndSet(false, true)) {
            scheduleDirectLumaDrain();
        }
    }

    private void scheduleDirectLumaDrain() {
        try {
            directLumaTrackingExecutor.execute(this::drainDirectLumaFrames);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // Camera callbacks and a draining worker can race Activity destruction.
            if (!directLumaTrackingExecutor.isShutdown()) throw stopped;
            pendingDirectLumaFrame.set(null);
            directLumaWorkerRunning.set(false);
        }
    }

    private void drainDirectLumaFrames() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                PendingObservation pending = pendingDirectLumaFrame.getAndSet(null);
                if (pending == null) return;
                processDirectLumaFrame(pending.frame,pending.domain);
            }
        } finally {
            directLumaWorkerRunning.set(false);
            if (pendingDirectLumaFrame.get() != null
                    && directLumaWorkerRunning.compareAndSet(false, true)) {
                scheduleDirectLumaDrain();
            }
        }
    }

    private void submitPreviewCoordination(PreviewCoordinationRequest request) {
        if (request == null
                || pipeline == null
                || !previewPresentationBarrier.permits(
                        request.presentationGeneration
                )
                || !isCurrentPreviewStamp(request.continuityStamp)) return;
        pendingPreviewCoordination.getAndUpdate(previous -> {
            if (previous != null
                    && previous.currentMotion()
                    && !request.currentMotion()) {
                return previous;
            }
            return request;
        });
        if (previewCoordinationWorkerRunning.compareAndSet(false, true)) {
            previewCoordinationExecutor.execute(this::drainPreviewCoordination);
        }
    }

    private void drainPreviewCoordination() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                PreviewCoordinationRequest request =
                        pendingPreviewCoordination.getAndSet(null);
                if (request == null) return;
                coordinatePreviewFrame(request);
            }
        } finally {
            previewCoordinationWorkerRunning.set(false);
            if (pendingPreviewCoordination.get() != null
                    && previewCoordinationWorkerRunning.compareAndSet(false, true)) {
                previewCoordinationExecutor.execute(this::drainPreviewCoordination);
            }
        }
    }

    private void coordinatePreviewFrame(PreviewCoordinationRequest request) {
        AlprPipeline activePipeline = pipeline;
        if (activePipeline == null
                || !cameraStarted
                || request.sceneGeneration != uiSceneGeneration.get()
                || request.transformGeneration != uiCameraTransformGeneration.get()
                || !previewPresentationBarrier.permits(
                        request.presentationGeneration
                )
                || !isCurrentPreviewStamp(request.continuityStamp)) {
            return;
        }
        SceneTransitionDecision decision;
        synchronized (ordinaryDynamicResourcesEnabled()?ordinaryPreviewCoordinationLock:activePipeline) {
            SceneContinuitySnapshot submissionContinuity =
                    activePipeline.sceneContinuitySnapshot();
            long requestedRebaseRevision =
                    previewSceneRecoveryRebaseRevision.get();
            if (PreviewContinuityUiPolicy.shouldApplyRecoveredSceneRebase(
                    requestedRebaseRevision,
                    previewSceneRecoveryRebaseAppliedRevision,
                    submissionContinuity.state
            )) {
                android.util.Log.d(
                        "ALPR_PREVIEW_REFERENCE",
                        "defer=stale_direct_luma_evidence recovery_revision="
                                + requestedRebaseRevision
                                + " coordinator_revision="
                                + submissionContinuity.decisionRevision
                );
                return;
            }
            activePipeline.setCameraMotionEvidence(
                    request.sensorAvailable,
                    request.currentMotion(),
                    request.rapidMotion(),
                    request.angularMagnitude
            );
            if (!activePipeline.setTargetSnapshotIfCurrent(
                    request.targetSnapshot,
                    request.continuityStamp
            )) return;
            if (!isCurrentPreviewStamp(request.continuityStamp)
                    || !previewPresentationBarrier.permits(
                            request.presentationGeneration
                    )) return;
            decision = activePipeline.onWorkerPreviewSceneEvidence(
                    request.sourceFrame,
                    request.rawVisualChange,
                    request.rawChangedFraction,
                    request.rawChangedFraction,
                    request.rawMeanDelta,
                    0f,
                    0f,
                    request.trackingLost,
                    request.trackingDegraded,
                    new MotionExplanationEvidence(
                            request.sensorAvailable,
                            request.currentMotion(),
                            request.rapidMotion(),
                            request.angularMagnitude,
                            false,
                            request.visualMotionEstimated,
                            request.visualMotionCoherence,
                            request.visualMotionResidual,
                            0f,
                            0f,
                            request.motionSettling
                    )
            );
        }
        if (PreviewContinuityUiPolicy.requestsRecoveryRebase(decision)) {
            resetGlobalPreviewMotionState(false);
            previewSceneRecoveryRebaseRevision.accumulateAndGet(
                    decision.revision,
                    Math::max
            );
        }
        runOnUiThread(() -> {
            if (renderStaticIdle()) return;
            if (!cameraStarted
                    || request.sceneGeneration != uiSceneGeneration.get()
                    || request.transformGeneration
                    != uiCameraTransformGeneration.get()
                    || !previewPresentationBarrier.permits(
                            request.presentationGeneration
                    )
                    || !isCurrentPreviewStamp(request.continuityStamp)) return;
            PreviewContinuityUiPolicy.Outcome uiOutcome =
                    PreviewContinuityUiPolicy.decide(
                            decision,
                            false,
                            !request.trackedItems.isEmpty()
                    );
            recordPreviewDecisionAuthority(uiOutcome, "direct_luma_async");
            if (uiOutcome.renderCoordinatorDecision) {
                renderPreviewContinuityDecision(
                        decision,
                        0f,
                        0f,
                        request.trackedItems
                );
                return;
            }
            if (uiOutcome.legacyTrackingLossInvalidation) {
                invalidateUiForLostPreviewTracking();
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
    private final PreviewPlateTracker previewVehicleTracker =
            new PreviewPlateTracker(true);
    private final TargetStateMachine targetStateMachine =
            new TargetStateMachine();
    private ExperimentSession experimentSession = new ExperimentSession();
    private com.example.alpr_v1.experiment.ResearchSessionViewModel researchSessions;
    private java.io.File pendingPersistentResearchArchive;
    private boolean researchArchivePickerLoading;
    private CrashSessionMarker crashSessionMarker;
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
    private long autoZoomOperationGeneration;
    private volatile long autoZoomPreferenceRevision;
    private boolean autoZoomReturnInProgress;
    private boolean resetAutoZoomSessionAfterReturn;
    private boolean autoZoomPreZoomAnchorValid;
    private volatile boolean autoZoomZoomedAnchorPending;
    private volatile boolean autoZoomZoomedAnchorValid;
    private volatile boolean autoZoomReturnValidationPending;
    private volatile int autoZoomDynamicFrameGraceCount;
    private Runnable pendingAutoZoomStartRunnable;

    private boolean thermalUiMonitorRunning;

    // Presentation context only; editing a next timer never rearms the retained session deadline.
    private String timerConfiguredAfterFinishedSession = "";
    private com.example.alpr_v1.ui.LiveModelInfoView liveModelInfo;
    private long experimentTimerDeadlineElapsedMillis = -1L;
    private long lastExperimentUiTickMillis;

    private final Runnable experimentTimerUiRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!researchSessions.timerState().running) {
                        return;
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (BuildConfig.DEBUG && lastExperimentUiTickMillis > 0L && now - lastExperimentUiTickMillis > 1500L)
                        android.util.Log.d("ALPR_TIMER", "ui_gap_ms=" + (now - lastExperimentUiTickMillis));
                    lastExperimentUiTickMillis = now;
                    tintCameraAction(experimentTimerButton,R.color.alpr_accent);
            updateExperimentTimerButton();

                    experimentTimerHandler.postDelayed(
                            this,
                            com.example.alpr_v1.ui.ExperimentCountdown.nextRefreshMillis(experimentTimerDeadlineElapsedMillis, now)
                    );
                }
            };

    private List<CapturedPlateItem> capturedCrops;
    private Map<Long, CropSamplingPolicy.Previous> lastCaptureByTrack;
    private final Map<Long, String> normalCropIdentityOwners = new java.util.HashMap<>();
    private RecognitionHistoryStore recognitionHistory;

    private PreviewView previewView;
    private DetectionOverlayView overlayView;
    private TextView liveStatus;
    private TextView liveStatusSecondary;
    private TextView liveHud;
    private final com.example.alpr_v1.camera.LiveFrameRateMeter liveCameraFrameRate =
            new com.example.alpr_v1.camera.LiveFrameRateMeter();
    private LinearLayout liveHudRow;
    private View liveStatusStrip;
    private View liveStatusDot;
    private MaterialButton liveSceneModeToggle;
    private MaterialButton searchPlateButton;
    private MaterialButton releaseTargetButton;
    private TextView liveEvent;
    private TextView recognitionHint;
    private View confirmedResultTray;
    private TextView confirmedResultText;
    private TextView confirmedResultConfidence;
    private TextView confirmedResultMeta;
    private LivePresentationController livePresentation;
    private MaterialButton collectionToggle;
    private MaterialButton galleryOpenButton;
    private MaterialButton cameraPreviewButton;
    private com.example.alpr_v1.ui.TvSetupController tvSetup;
    private MaterialButton analysisStartButton;
    private MaterialButton exportReportButton;


    /*
     * Galeria nie jest już częścią activity_main.
     * Jej widoki istnieją tylko wtedy, gdy Bottom Sheet jest otwarty.
     */
    private BottomSheetDialog galleryBottomSheet;

    private RecyclerView galleryResultsList;

    private View galleryResultsEmpty;

    private TextView gallerySheetCount;

    private TextView gallerySheetTitle;

    private TextView gallerySheetModeLabel;

    private TextView galleryCollectionStats;

    private MaterialButton gallerySaveSelectedCropsButton;
    private View galleryRecentContainer;
    private View galleryResearchContainer;
    private MaterialButton galleryClearHistoryButton;
    private RecognitionHistoryAdapter recognitionHistoryAdapter;
    private boolean galleryResearchMode;
    private View verificationEmpty;
    private TextView verificationEmptyText;
    private View verificationScroll;
    private PlateCropView verificationCrop;
    private MaterialSwitch verificationShowBoxes;
    private TextView verificationStatus;
    private TextView verificationModelText;
    private TextView verificationConsensusText;
    private com.example.alpr_v1.ui.CropTimingView verificationTiming;
    private TextView verificationCharacters;
    private TextView verificationMetrics;
    private MaterialButton verificationAcceptButton;
    private MaterialButton verificationCorrectButton;
    private MaterialButton verificationRejectButton;
    private MaterialButton verificationSaveCurrentButton;
    private MaterialButton verificationPreviousButton;
    private MaterialButton verificationNextButton;
    private MaterialCheckBox verificationDesktopReview;
    private EditText verificationNote;
    private final Map<VerificationIssue, Chip> verificationIssueChips =
            new EnumMap<>(VerificationIssue.class);
    private VerificationFilter verificationFilter = VerificationFilter.ALL;
    private final List<CapturedPlateItem> filteredVerificationItems = new ArrayList<>();
    private String selectedVerificationCaptureId = "";
    private String verificationSessionId = "";
    private MaterialButton verificationSessionPicker;
    private CapturedPlateItem boundVerificationItem;
    private String persistedVerificationNote = "";
    private boolean bindingVerificationControls;
    private MaterialButton experimentTimerButton;
    private MaterialButton experimentThermalButton;
    private MaterialButton autoZoomButton;
    private View autoZoomGlow;
    private View autoZoomControl;
    private ImageView autoZoomTarget;
    private ObjectAnimator autoZoomGlowAnimator;
    private ObjectAnimator autoZoomTargetAnimator;
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
    private volatile boolean cameraPreviewActive;
    private boolean pendingPreviewPermissionRequest;
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
    private boolean experimentLockEnabled = true;
    private boolean experimentAutoZoomEnabled;
    private TimerConfig experimentTimerConfig =
            TimerConfig.disabled();
    private ThermalConfig experimentThermalConfig =
            ThermalConfig.disabled();

    private ThermalMonitor thermalMonitor;

    private ThermalMonitor.Snapshot latestThermalSnapshot;

    private boolean waitingForThermalStart;

    private long thermalReadySinceElapsedMillis =
            -1L;
    private static final String KEY_COLLECTION_ARMED = "collection_armed";
    private volatile boolean collectionActive;
    private boolean normalCollectionReady;
    private volatile long analysisControlGeneration;
    private long analysisStartRequestedElapsedNanos;
    private String collectionSessionId = "";
    private long collectionSessionStartedElapsedNanos;
    private int collectionSequence;
    private String cropLimitSetting = CropCapacityPolicy.AUTO;
    private int resolvedCropLimit = 10;
    private int pendingBatchWrites;
    private int successfulBatchWrites;
    private int failedBatchWrites;
    private SharedPreferences uiPreferences;
    private int knownSettingsRevision;
    private boolean geometryCalibrationEnabled;
    private boolean geometryCalibrationEventRecorded;
    private long lastOverlayTelemetryNanos;
    private long lastDynamicProjectionEventNanos;
    private CaptureGalleryViewModel captureGalleryState;
    private final com.example.alpr_v1.ui.ExperimentStartRequest experimentStartRequest = new com.example.alpr_v1.ui.ExperimentStartRequest();


    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        boolean requestedStart = experimentStartRequest.permissionResult(granted);
                        if (granted) {
                            boolean previewRequested = pendingPreviewPermissionRequest;
                            pendingPreviewPermissionRequest = false;
                            if (previewRequested) {
                                experimentPreviewPaused = false;
                                startCameraPreview();
                            } else if (!experimentModeEnabled && requestedStart) {
                                requestAnalysisStart();
                            } else if (requestedStart) {
                                experimentPreviewPaused = false;
                            }
                            requestExperimentReadinessRefresh();
                        } else {
                            pendingPreviewPermissionRequest = false;
                            livePresentation.showTransient(
                                    getString(R.string.camera_permission_required)
                            );

                            recordWarning(
                                    "Odmówiono dostępu do kamery, możesz dodać dostęp Ustawienia -> Aplikacje."
                            );
                        }
                        renderAnalysisControls();
                    }
            );

    private final ActivityResultLauncher<Intent> cropSessionDestination = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                String sessionId = captureGalleryState.pendingCropSessionExport;
                captureGalleryState.pendingCropSessionExport = null;
                Uri uri = result.getResultCode() == RESULT_OK && result.getData() != null
                        ? result.getData().getData() : null;
                if (uri != null && sessionId != null) writeCropSessionExport(sessionId, uri);
            });

    private final ActivityResultLauncher<Intent> reportDestination = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Uri uri = result.getResultCode() == RESULT_OK && result.getData() != null
                        ? result.getData().getData()
                        : null;
                ResearchArchive.Kind kind = pendingExportKind;
                pendingExportKind = null;
                if (uri != null && kind != null) {
                    int grants=result.getData().getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if((result.getData().getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)
                        try{getContentResolver().takePersistableUriPermission(uri,grants);}catch(SecurityException ignored){}
                    writeResearchExport(uri,kind);
                }else pendingPersistentResearchArchive=null;
            }
    );

    private ReadinessController configurationReadiness;
    private final ReadinessEvaluator readinessDeviceProjection=new ReadinessEvaluator();
    private ReadinessSnapshot readinessSnapshot;
    private ReadinessResult readinessConfiguration;
    private ReadinessReturnContext readinessReturn;
    private String readinessDestination="";
    private boolean readinessEditorStarted,readinessReturnDue,readinessReturnManual;
    private final SharedPreferences.OnSharedPreferenceChangeListener configurationReadinessChanges=(prefs,key)->{
        if(configurationReadiness!=null)runOnUiThread(()->configurationReadiness.refresh());
    };
    private final ActivityResultLauncher<Intent> settingsScreen=registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),result->{
                receiveReadinessSettingsResult(result.getData());
            });

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
        if(savedInstanceState!=null)timerConfiguredAfterFinishedSession=savedInstanceState.getString("timer_configured_after_finished_session","");
        if(savedInstanceState!=null){
            readinessReturn=ReadinessReturnContext.fromBundle(savedInstanceState.getBundle("readiness_main_return"));
            readinessDestination=savedInstanceState.getString("readiness_destination","");
            readinessReturnDue=savedInstanceState.getBoolean("readiness_return_due");
            readinessReturnManual=savedInstanceState.getBoolean("readiness_return_manual");
            if(savedInstanceState.getBoolean("readiness_editor_started")){readinessReturnDue=true;readinessReturnManual=true;}
        }
        researchSessions = new ViewModelProvider(this).get(com.example.alpr_v1.experiment.ResearchSessionViewModel.class);
        experimentSession = researchSessions.experiment;
        restoreResearchExportSelection(savedInstanceState);
        researchSessions.operationChanged.observe(this,ignored->{if(exportReportButton!=null)renderAnalysisControls();});
        researchSessions.messages.observe(this,message -> {
            if (message != null) Toast.makeText(this,message,Toast.LENGTH_LONG).show();
            if (galleryOpenButton != null) { refreshResearchCropCount(); updateExperimentTimerButton(); }
        });
        researchSessions.galleryChanged.observe(this, revision -> {
            if (galleryOpenButton != null) renderCapturedCrops();
        });
        researchSessions.timerExpired.observe(this, this::onAnalysisTimerExpired);
        EdgeToEdge.enable(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        bindViews();
        geometryCalibrationEnabled = BuildConfig.DEBUG
                && getIntent() != null
                && getIntent().getBooleanExtra(
                "debug_geometry_calibration",
                false
        );
        overlayView.setGeometryCalibrationEnabled(geometryCalibrationEnabled);
        overlayView.setPlateEntityResolver(track -> pipeline == null ? 0L : pipeline.plateOwnerEntityId(track));
        overlayView.setVehicleTapListener(entity -> {
            if (!cameraStarted || pipeline == null || effectiveSceneHandlingMode() != SceneHandlingMode.DYNAMIC_CONTINUITY) return;
            ResearchExecutionConfig frozen = pipeline.researchExecutionConfig();
            if (frozen != null && !frozen.lockEnabled) return;
            if(!experimentModeEnabled&&pipeline.targetFocus().entityId==entity)return;
            returnZoomBeforeUserTargetChange();
            pipeline.pickVehicle(entity);
            renderSearchControls();
        });
        applySystemInsets();

        captureGalleryState = new ViewModelProvider(this).get(CaptureGalleryViewModel.class);
        capturedCrops = captureGalleryState.capturedCrops();
        lastCaptureByTrack = captureGalleryState.lastCaptureByTrack();
        recognitionHistory = captureGalleryState.recognitionHistory();
        if (savedInstanceState != null && captureGalleryState.pendingCropSessionExport == null)
            captureGalleryState.pendingCropSessionExport = savedInstanceState.getString("crop_session_export_id");
        captureGalleryState.cropSessionMessages.observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                captureGalleryState.cropSessionMessages.setValue(null);
            }
        });
        collectionActive = captureGalleryState.collectionActive();
        collectionSessionId = captureGalleryState.collectionSessionId();
        collectionSessionStartedElapsedNanos =
                captureGalleryState.collectionSessionStartedElapsedNanos();
        collectionSequence = captureGalleryState.collectionSequence();

        modelRegistry = new ModelRegistry(this);
        autoTuneManager = new AutoTuneManager(this);
        metricsCollector = captureGalleryState.metricsCollector();
        captureGalleryState.cropPreparationReady.observe(this, ignored -> {
            List<CapturedPlateItem> readyCrops = captureGalleryState.takePreparedCrops();
            if (readyCrops.isEmpty()) return;
            capturedCrops.addAll(readyCrops);
            enforceCropLimit();
            renderCapturedCrops();
        });
        deviceProfile = DeviceProfile.capture(this);
        pipeline = new AlprPipeline(this, modelRegistry, metricsCollector, autoTuneManager);
        if(BuildConfig.DEBUG&&getIntent()!=null&&getIntent().hasExtra(com.example.alpr_v1.pipeline.MpDiagnosticCapture.INTENT_EXTRA)) {
            try {pipeline.requestMpDiagnosticCapture(getIntent().getStringExtra(com.example.alpr_v1.pipeline.MpDiagnosticCapture.INTENT_EXTRA));}
            catch(Exception invalid){android.util.Log.e("ALPR_MP_DIAGNOSTIC","Invalid debug capture request",invalid);}
        }
        cameraController = new CameraController(this, this, previewView);
        cameraController.setPreviewGeometryListener(overlayView::setPreviewSourceSize);
        metricsCollector.setCameraTimestampSource(
                cameraController.sourceTimeline().cameraTimestampSource().name()
        );
        cameraMotionMonitor = new CameraMotionMonitor(this);
        metricsCollector.setMotionSensorAvailable(cameraMotionMonitor.isAvailable());
        recordInfo("Uruchomiono aplikację");

        uiPreferences = getSharedPreferences("alpr_ui", MODE_PRIVATE);
        restoreTimerConfiguration();
        verificationSessionId=uiPreferences.getString("gallery_session_id", "");
        researchSessions.refreshGallerySessions();
        restoreTvCameraSetup();
        tvSetup = new com.example.alpr_v1.ui.TvSetupController(this, previewView, overlayView,
                () -> tvSetupAvailable() && !preparationActionsLocked(),
                () -> { if (!cameraPreviewActive) { experimentPreviewPaused = false; ensureCameraPermission(true); } },
                this::onTvSetupEvent);
        tvSetup.setInputSummaryProvider(this::tvInputSummary);
        tvSetup.setControlsRefresh(this::renderAnalysisControls);
        installReadinessEditorReturn();
        tvSetup.setGeometryRefresh(cameraController::refreshSetupGeometry);
        cameraController.setSetupGeometryListener(tvSetup::onGeometryState);
        tvSetup.setCameraControls(new com.example.alpr_v1.camera.CameraSetupControl(){
            public State state(){return cameraController.state();}
            public void setListener(java.util.function.Consumer<State> listener){cameraController.setListener(listener);}
            public org.json.JSONObject observation(){return cameraController.observation();}
            public void apply(com.example.alpr_v1.camera.CameraSelection selection,float zoom){
                if(!tvSetupAvailable()||preparationActionsLocked())return;
                State previous=state();
                if(previous.requested.equals(selection)&&Math.abs(previous.requestedZoom-zoom)<1e-4&&previous.reason.isEmpty())return;
                invalidatePreparedExperiment();
                cameraResolutionCatalog=new CameraResolutionCatalog(MainActivity.this,selection);
                Size size=chooseAnalysisSize();
                cameraController.apply(selection,zoom,size,cameraResolutionCatalog.isHighResolution(size));
                experimentPreviewPaused=false;experimentPreparationFailed=false;
                if(!cameraPreviewActive)ensureCameraPermission(true);
                requestExperimentReadinessRefresh();
            }
        });
        crashSessionMarker = new CrashSessionMarker(this);
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
                        this,cameraController.state().requested
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
        experimentLockEnabled = uiPreferences.getBoolean(
                SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,
                true
        );
        experimentAutoZoomEnabled = uiPreferences.getBoolean(
                SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,
                false
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
        configureSceneModeHudToggle();
        configureAnalysisControls();
        modelRegistry.reload();

        configurationReadiness=new ReadinessController(ticket->{
            readinessSnapshot=ReadinessSnapshot.capture(uiPreferences,modelRegistry,autoTuneManager,null,
                    com.example.alpr_v1.experiment.ResearchSessionStore.sessionsRoot(this),readinessDevice(),ticket);
            return readinessSnapshot;
        },result->{readinessConfiguration=result;renderConfigurationReadiness();});
        for(String name:new String[]{SettingsActivity.PREFERENCES,"autotune","model_registry"})
            getSharedPreferences(name,MODE_PRIVATE).registerOnSharedPreferenceChangeListener(configurationReadinessChanges);
        topAppBar.getMenu().add(0,R.id.readiness_main_return,0,R.string.readiness_return).setVisible(false);
        findViewById(R.id.tv_frame_status).setOnClickListener(v->{if(!settingsActionsLocked())openReadinessOptions(null);});
        configurationReadiness.refresh();

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
        phoneOrientationHud = new com.example.alpr_v1.ui.PhoneOrientationHud(
                findViewById(R.id.phone_orientation_panel));

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
        cameraPreviewButton = findViewById(R.id.camera_preview_button);

        liveStatus =
                findViewById(
                        R.id.live_status
                );

        liveStatusSecondary = findViewById(R.id.live_status_secondary);
        liveStatusStrip = findViewById(R.id.live_status_strip);
        liveStatusDot = findViewById(R.id.live_status_dot);
        liveSceneModeToggle = findViewById(R.id.live_scene_mode_toggle);
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
                visible -> {
                    overlayView.setDiagnosticMode(visible);
                    tintCameraAction(findViewById(R.id.live_diagnostics_toggle),
                            visible ? R.color.alpr_success : R.color.alpr_accent);
                }
        );
        previewView.getViewTreeObserver().addOnGlobalLayoutListener(overlayReadabilityLayoutListener);
        liveModelInfo=findViewById(R.id.live_model_info);
        findViewById(R.id.live_model_info_toggle).setOnClickListener(view -> {
            boolean visible=livePresentation.toggleModelInfo();
            tintCameraAction((MaterialButton)view,visible?R.color.alpr_success:R.color.alpr_accent);
            if(visible)renderHudModelInfo();
        });

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
        searchPlateButton = findViewById(R.id.search_plate_button);
        releaseTargetButton = findViewById(R.id.release_target_button);
        searchPlateButton.setOnClickListener(view -> showRegistrationSearch());
        releaseTargetButton.setOnClickListener(view -> {
            returnZoomBeforeUserTargetChange();
            pipeline.releaseSelectedTarget(false);
            renderSearchControls();
        });
    }

    private void returnZoomBeforeUserTargetChange() {
        if (!cameraTransformInProgress && currentCameraZoomRatio <= 1.01f
                && autoZoomController.state() == AutoZoomController.State.ZOOM_SETTLING) {
            cancelPendingAutoZoomStart();
            autoZoomController.onRequestFailed();
            pipeline.finishDynamicZoom();
            hideAutoZoomTarget();
            updateAutoZoomButton();
        } else if (cameraTransformInProgress) abortAutoZoomAfterTransform = true;
        else if (currentCameraZoomRatio > 1.01f && autoZoomController.state() != AutoZoomController.State.RETURNING)
            requestAutoZoomReturn(null);
    }

    private void showRegistrationSearch() {
        if (!cameraStarted || pipeline == null || effectiveSceneHandlingMode() != SceneHandlingMode.DYNAMIC_CONTINUITY) return;
        if (!pipeline.searchRegistration().isEmpty()) {
            new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this).setTitle(R.string.search_vehicle)
                    .setItems(new String[]{getString(R.string.search_plate_change), getString(R.string.search_plate_end)},
                            (dialog, choice) -> {
                                if (choice == 0) showRegistrationSearchInput();
                                else { returnZoomBeforeUserTargetChange(); pipeline.releaseSelectedTarget(true); renderSearchControls(); }
                            }).show();
        } else showRegistrationSearchInput();
    }

    private void showRegistrationSearchInput() {
        androidx.appcompat.widget.AppCompatEditText input = new androidx.appcompat.widget.AppCompatEditText(this);
        input.setHint(R.string.search_plate_number);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(16)});
        input.setText(pipeline.searchRegistration());
        int padding = Math.round(24f * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0); container.addView(input);
        androidx.appcompat.app.AlertDialog dialog = new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(R.string.search_vehicle).setView(container)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.search_plate_start, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = com.example.alpr_v1.acquisition.RegistrationSearchPolicy.normalize(input.getText().toString());
                    if (value.length() < 4) { input.setError(getString(R.string.search_plate_invalid)); return; }
                    returnZoomBeforeUserTargetChange();
                    pipeline.startRegistrationSearch(value); dialog.dismiss(); renderSearchControls();
                }));
        dialog.show();
    }

    private boolean renderSearchControls() {
        if (topAppBar == null || pipeline == null || uiPreferences == null) return false;
        synchronizeTargetFocusPresentation();
        boolean dynamic = effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY;
        String text = pipeline.searchRegistration();
        long locked = pipeline.lockedEntityId();
        if (searchPlateButton != null) {
            searchPlateButton.setVisibility(View.VISIBLE);
            searchPlateButton.setEnabled(dynamic && cameraStarted && !pipeline.manualTargetSelected());
            tintCameraAction(searchPlateButton, text.isEmpty() ? R.color.alpr_text_secondary : R.color.alpr_success);
        }
        if (releaseTargetButton != null) {
            releaseTargetButton.setVisibility(View.VISIBLE);
            boolean foreground = pipeline.foregroundEntityId() > 0L;
            releaseTargetButton.setEnabled(dynamic && cameraStarted && foreground);
            tintCameraAction(releaseTargetButton, foreground ? R.color.alpr_accent : R.color.alpr_text_muted);
        }
        if (!dynamic || !cameraStarted) return false;
        String status = null;
        com.example.alpr_v1.acquisition.TargetFocusSnapshot selectedFocus=pipeline.targetFocus();
        boolean recoveringFocus = selectedFocus.sessionState == com.example.alpr_v1.domain.TargetSessionState.RECOVERING;
        long displayNumber=selectedFocus.displayNumber;
        if(locked>0L&&selectedFocus.waitingForTarget)status=getString(R.string.dynamic_lock_target_unavailable);
        else if (locked > 0L && recoveringFocus) status = displayNumber>0
                ?getString(R.string.target_focus_recovering, displayNumber):getString(R.string.dynamic_lock_recovering);
        else if (pipeline.searchMatchState() == com.example.alpr_v1.domain.SearchMatchState.POSSIBLE_MATCH
                && pipeline.foregroundEntityId() > 0L && locked == 0L)
            status = getString(R.string.search_plate_verifying);
        else if (locked > 0L) status = !text.isEmpty()
                && pipeline.searchMatchState() == com.example.alpr_v1.domain.SearchMatchState.CONFIRMED_MATCH
                ? getString(R.string.search_plate_found, text) : displayNumber>0
                ?getString(R.string.dynamic_vehicle_locked,displayNumber):getString(R.string.dynamic_lock_selected);
        else if (!text.isEmpty()) status = getString(pipeline.searchMatchState()
                == com.example.alpr_v1.domain.SearchMatchState.REJECTED_MATCH
                ? R.string.search_plate_unconfirmed : R.string.search_plate_active, text);
        if (status == null) return false;
        livePresentation.showUserStatus(recoveringFocus||selectedFocus.waitingForTarget ? LivePresentationController.State.RECOVERING
                : LivePresentationController.State.SEARCHING, status,
                selectedFocus.waitingForTarget?getString(R.string.dynamic_lock_release_hint):
                !experimentModeEnabled&&requestedAutoZoomEnabled()?autoZoomHudLabel():"");
        return true;
    }

    private long presentedFocusRevision = -1L;

    private void synchronizeTargetFocusPresentation() {
        com.example.alpr_v1.acquisition.TargetFocusSnapshot focus = pipeline.targetFocus();
        if (focus.revision != presentedFocusRevision) {
            if (presentedFocusRevision >= 0L && cameraStarted) uiSceneGeneration.incrementAndGet();
            presentedFocusRevision = focus.revision;
            pendingPreviewCoordination.set(null);
            if(focus.entityId>0L&&!experimentModeEnabled&&effectiveSceneHandlingMode()==SceneHandlingMode.DYNAMIC_CONTINUITY) {
                previewVehicleTracker.retainOnly(java.util.Collections.singleton(focus.entityId));
                java.util.Set<Long> selectedPlates=new java.util.HashSet<>();
                for(java.util.Map.Entry<Long,Long> owner:pipeline.vehicleIdentityPlateOwners().entrySet())
                    if(owner.getValue()==focus.entityId)selectedPlates.add(owner.getKey());
                previewPlateTracker.retainOnly(selectedPlates);
            } else {
                previewVehicleTracker.retainOnly(null);previewPlateTracker.retainOnly(null);
                if(!ordinaryDynamicResourcesEnabled())previewVehicleTracker.reset();
            }
            overlayTracker.reset();
            latestDiagnosticOverlayItems = focusPresentationItems(latestDiagnosticOverlayItems, focus);
            latestPipelinePlateItems = focusPresentationItems(latestPipelinePlateItems, focus);
            latestPreviewMotionItems = focusPresentationItems(latestPreviewMotionItems, focus);
            autoZoomBaseVehicleItems = focusPresentationItems(autoZoomBaseVehicleItems, focus);
            autoZoomBaseMemoryOverlayItems = focusPresentationItems(autoZoomBaseMemoryOverlayItems, focus);
            autoZoomReturnGeometry.discardForDifferentOwner(focus.entityId);
            TargetSnapshot previousTarget = targetStateMachine.snapshot();
            if (focus.entityId > 0L && previousTarget.trackId > 0L
                    && pipeline.plateOwnerEntityId(previousTarget.trackId) != focus.entityId) {
                previewPlateTracker.reset();
                targetStateMachine.reset();
                latestPipelinePlateEntityId = 0L;
                latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
            }
            if (focus.awaitingFreshScanAnchor || focus.waitingForTarget) {
                returnZoomBeforeUserTargetChange();
                hideAutoZoomTarget();
                previewPlateTracker.reset();
                targetStateMachine.reset();
                latestPipelinePlateEntityId = 0L;
                latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
                clearAutoZoomRecognitionMemory();
            }
        }
        overlayView.setTargetFocus(focus.entityId, focus.awaitingFreshScanAnchor || focus.waitingForTarget);
    }

    private List<OverlayItem> focusPresentationItems(List<OverlayItem> items,
            com.example.alpr_v1.acquisition.TargetFocusSnapshot focus) {
        if (focus.awaitingFreshScanAnchor || focus.waitingForTarget) return java.util.Collections.emptyList();
        if (focus.entityId == 0L) return items;
        List<OverlayItem> kept = new ArrayList<>();
        for (OverlayItem item : items) {
            long owner = item.kind == OverlayItem.Kind.PLATE ? pipeline.plateOwnerEntityId(item.trackId) : item.trackId;
            if (owner == focus.entityId) kept.add(item);
        }
        return java.util.Collections.unmodifiableList(kept);
    }

    private boolean handleMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (com.example.alpr_v1.ui.TvSetupNavigation.select(id, tvSetup)) return true;
        if (id == R.id.menu_settings) {
            if (settingsActionsLocked()) {
                Toast.makeText(
                        this,
                        R.string.research_settings_locked,
                        Toast.LENGTH_LONG
                ).show();
                return true;
            }
            openReadinessOptions(null);
        } else if(id==R.id.readiness_main_return){
            if(settingsActionsLocked()){Toast.makeText(this,R.string.readiness_busy_return,Toast.LENGTH_LONG).show();return true;}
            readinessReturnManual=false;readinessReturnDue=true;continueReadinessNavigation();
        } else if (id == R.id.menu_diagnostics) {
            openDiagnostics();
        } else if(id==R.id.menu_session_operation){
            showSessionOperationDetails();
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
                .putExtra("analysis_mode", effectiveSceneHandlingMode().analysisMode())
                .putExtra("analysis_mode_frozen", experimentSession.isRunning())
                .putExtra(DiagnosticsActivity.EXTRA_COLLECTION_ACTIVE, collectionActive);
        diagnosticsScreen.launch(intent);
    }

    private void applyRecognitionProfile(RecognitionProfile profile) {
        recognitionProfile = profile;
        uiPreferences.edit()
                .putString("recognition_profile", recognitionProfile.wireName())
                .apply();
        pipeline.setRecognitionProfile(recognitionProfile);
        deferPipelineControl(AlprPipeline::resetTracking);
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

        deferPipelineControl(AlprPipeline::resetTracking);

        overlayTracker.reset();

        lastCaptureByTrack.clear();


        if ((cameraStarted || cameraPreviewActive)
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            /*
             * Restart techniczny.
             *
             * ExperimentSession i timer pozostają bez zmian.
             */
            boolean restartAnalysis = cameraStarted;
            cameraController.stop();
            cameraStarted = false;
            cameraPreviewActive = false;
            if (restartAnalysis) {
                startCamera(false);
            } else {
                startCameraPreview();
            }
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

    /**
     * Wyniki zebrane przed zmianą modeli, wariantu lub topologii pipeline'u nie
     * mogą zostać pokazane po powrocie z ustawień. Dotyczy to także osobnej
     * warstwy fade tablic, która inaczej potrafi zachować różową ramkę z trybu
     * pełnoklatkowego po włączeniu MP.
     */
    private void invalidatePresentationAfterSettingsChange(int settingsRevision) {
        pendingPreviewCoordination.set(null);
        pendingDirectLumaFrame.set(null);
        long generation = uiSceneGeneration.incrementAndGet();
        pendingPreviewCoordination.set(null);
        pendingDirectLumaFrame.set(null);
        previewPresentationBarrier.reset();
        previewSceneRecoveryRebaseRevision.set(0L);
        previewSceneRecoveryRebaseAppliedRevision = 0L;
        previewPresentationBarrier.reset();

        deferPipelineControl(AlprPipeline::resetTracking);
        targetStateMachine.reset();
        clearAutoZoomRecognitionMemory();

        overlayTracker.reset();
        previewPlateTracker.reset();
        previewVehicleTracker.reset();
        resetGlobalPreviewMotionState();
        previewSceneDetector.reset();
        previewSceneAnchorGuard.reset();
        previewSceneAnchorPending = cameraStarted;
        plateOverlayFreshness.reset();

        latestDiagnosticOverlayItems = java.util.Collections.emptyList();
        latestDiagnosticOverlayAnchorTimestampNanos = 0L;
        latestPipelinePlateItems = java.util.Collections.emptyList();
        latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
        latestPreviewMotionItems = java.util.Collections.emptyList();
        latestPipelinePlateEntityId = 0L;
        lastCaptureByTrack.clear();
        lastUiUpdateNanos.set(0L);
        liveHudAwaitingFreshResult = true;

        overlayView.setFocusedTrackId(0L);
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setPreviewItems(java.util.Collections.emptyList());
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(java.util.Collections.emptyList());
        overlayView.setAnalysisViewportEnabled(
                cameraStarted || cameraPreviewActive
        );
        livePresentation.clearResult();

        android.util.Log.i(
                "ALPR_PIPELINE_CONFIG",
                "presentation_invalidated generation=" + generation
                        + " settings_revision=" + settingsRevision
                        + " vehicle_cascade=" + vehicleCascadeEnabled
        );
    }

    private void clearCurrentResult() {
        if (exportInProgress) {
            Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        overlayTracker.reset();
        deferPipelineControl(AlprPipeline::resetTracking);
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(java.util.Collections.emptyList());
        livePresentation.clearResult();
        showSearchingStatusForCurrentPipeline();
        clearCapturedCrops();
        livePresentation.showTransient(getString(R.string.recognition_cleared));
    }

    private void showHelp() {
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.menu_close, null)
                .show();
    }

    private void showAbout() {
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.menu_close, null)
                .show();
    }

    private void showExitConfirmation() {
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
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
            if (cameraPreviewActive) {
                stopCameraPreview(false);
            }
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
        com.example.alpr_v1.ui.CameraWindowUi.install(getWindow(), findViewById(R.id.main),com.example.alpr_v1.ui.CameraWindowUi.ContentPolicy.MAIN_FULL_BLEED);
    }

    private boolean f6FocusMode(){return experimentModeEnabled&&uiPreferences!=null
            &&uiPreferences.getBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,false);}
    private boolean sessionWriteBusy(){return researchSessions!=null&&(researchSessions.finalizing()
            ||researchSessions.documentBusy()||researchSessions.operations.hasWriteWork());}
    private boolean preparationActionsLocked(){return cameraStarted||experimentSession.isRunning()
            ||analysisStartGate.preparing()||experimentStartRequest.pending()||sessionWriteBusy();}
    private boolean settingsActionsLocked(){return experimentSession.isRunning()||analysisStartGate.preparing()
            ||experimentStartRequest.pending()||sessionWriteBusy()||tvSetup!=null&&tvSetup.editing();}
    private void renderPreparationActions(){
        View actions=findViewById(R.id.main_calibration_actions);if(actions==null)return;
        int visibility=experimentModeEnabled?View.VISIBLE:View.GONE;
        if(actions.getVisibility()!=visibility){actions.setVisibility(visibility);
            com.example.alpr_v1.ui.LivePresentationController.configureLayout(findViewById(R.id.main));}
        boolean available=tvSetupAvailable(),enabled=available&&!preparationActionsLocked()&&(tvSetup==null||!tvSetup.editing());
        findViewById(R.id.tv_frame_edit).setEnabled(enabled);
        findViewById(R.id.f6_photo_roi_edit).setEnabled(enabled&&f6FocusMode());
        if(topAppBar!=null){MenuItem settings=topAppBar.getMenu().findItem(R.id.menu_settings);
            if(settings!=null)settings.setEnabled(!settingsActionsLocked());}
    }
    private void showSessionOperationDetails(){
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this).setTitle(R.string.main_session_work_dialog)
                .setMessage(researchSessions.operationHistoryDescription()).setPositiveButton(android.R.string.ok,null).show();
    }
    private void renderSessionOperation(){
        View panel=findViewById(R.id.session_operation_panel);if(panel==null||researchSessions==null)return;
        com.example.alpr_v1.experiment.ResearchOperationState.Snapshot receipt=researchSessions.operations.presentation();
        com.example.alpr_v1.ui.ResearchOperationPresentation state=com.example.alpr_v1.ui.ResearchOperationPresentation.from(receipt);
        boolean editor=tvSetup!=null&&tvSetup.editing();
        boolean show=state.visible&&state.active&&!editor;
        panel.setVisibility(show?View.VISIBLE:View.GONE);
        MaterialToolbar toolbar=findViewById(R.id.top_app_bar);
        if(toolbar!=null){MenuItem detailsItem=toolbar.getMenu().findItem(R.id.menu_session_operation);
            if(detailsItem!=null)detailsItem.setVisible(state.visible&&!editor);}
        TextView title=findViewById(R.id.session_operation_title),detail=findViewById(R.id.session_operation_detail);
        if(!state.title.contentEquals(title.getText()))title.setText(state.title);
        String description=state.detail;
        if(!state.active&&researchSessions.ready())description+=" "+getString(R.string.main_session_work_next);
        if(!description.contentEquals(detail.getText()))detail.setText(description);
        int color=receipt!=null&&("FAILED".equals(receipt.status)||"INTERRUPTED".equals(receipt.status))?R.color.alpr_error
                :receipt!=null&&"SAVED_UNVERIFIED".equals(receipt.status)?R.color.alpr_warning:R.color.alpr_accent;
        title.setTextColor(ContextCompat.getColor(this,color));
        boolean warning=receipt!=null&&("FAILED".equals(receipt.status)||"INTERRUPTED".equals(receipt.status)||"SAVED_UNVERIFIED".equals(receipt.status));
        if(livePresentation!=null)livePresentation.setOperationStatus(editor?null:state,!cameraStarted&&!experimentSession.isRunning(),warning);
        com.google.android.material.progressindicator.LinearProgressIndicator progress=findViewById(R.id.session_operation_progress);
        if(progress.isIndeterminate()==state.determinate){progress.setVisibility(View.INVISIBLE);progress.setIndeterminate(!state.determinate);}
        if(state.determinate)progress.setProgressCompat(state.percent,false);
        progress.setVisibility(show?View.VISIBLE:View.GONE);
    }
    private boolean focusBlocksF6Start(){return com.example.alpr_v1.experiment.ConfigurationRequirements.focusBlocksStart(
            f6FocusMode(),cameraController!=null&&cameraController.focusRequested(),cameraController!=null&&cameraController.focusHeld());}
    private void renderCameraFocusButton(){
        MaterialButton button=findViewById(R.id.f6_focus_toggle);if(button==null)return;
        button.setVisibility(f6FocusMode()?View.VISIBLE:View.GONE);
        if(cameraController==null){button.setEnabled(false);return;}
        boolean running=cameraStarted||experimentSession.isRunning(),requested=cameraController.focusRequested(),held=cameraController.focusHeld();
        button.setText(held?(running?"AF ✓\nna TV":"AF ✓\nZwolnij"):requested?
                ("PENDING".equals(cameraController.focusStatus())?"AF…\nAnuluj":"AF brak\nAnuluj"):"Ustaw AF\nna TV");
        button.setContentDescription(cameraController.focusDescription()+". "+(requested?"Zwolnij żądanie AF":"Ustaw i utrzymaj ostrość TV"));
        button.setTooltipText(cameraController.focusDescription());
        button.setEnabled(!running&&!sessionWriteBusy()&&(tvSetup==null||!tvSetup.editing())
                &&(!experimentStartRequest.pending()||requested));
    }
    private void toggleF6Focus(){
        if(!f6FocusMode()||cameraController==null||cameraStarted||experimentSession.isRunning()||sessionWriteBusy())return;
        if(cameraController.focusRequested()){
            if(experimentStartRequest.pending())cancelExperimentStart();
            cameraController.releaseFocus("operator");renderAnalysisControls();return;
        }
        if(experimentStartRequest.pending())return;
        try{
            CameraSetupReadiness confirmation=currentCameraConfirmation();
            if(confirmation==null||!confirmation.ready())throw new IllegalStateException("Poczekaj na zgodną klatkę kamery");
            com.example.alpr_v1.setup.TvBboxCrop tv=tvSetup.selectionForInput(confirmation.applied);
            double x=(tv.left+tv.right)/2.0,y=(tv.top+tv.bottom)/2.0;
            // Prefer the approved inner field; an unavailable F6 approval never invents an ROI.
            try{
                com.example.alpr_v1.experiment.Final180PhotoRoi photo=com.example.alpr_v1.experiment.Final180PhotoRoiApproval.resolve(
                        uiPreferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,""),
                        uiPreferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,""),tv,
                        new org.json.JSONObject(tvSetup.approvedTvRecord()),null).forSession(tv,new org.json.JSONObject(tvSetup.approvedTvRecord()));
                x=(photo.left+photo.right)/2.0;y=(photo.top+photo.bottom)/2.0;
            }catch(Exception unavailable){/* AF may use the confirmed TV centre before inner-field approval. */}
            cameraController.focusOnSource(tv.geometry,x,y);
            Toast.makeText(this,cameraController.focusDescription(),Toast.LENGTH_LONG).show();
        }catch(Exception error){Toast.makeText(this,"Nie ustawiono AF: "+error.getMessage(),Toast.LENGTH_LONG).show();}
        renderAnalysisControls();
    }
    private void onCameraFocusChanged(){
        if(isFinishing()||isDestroyed())return;
        if(!cameraStarted&&("FAILED".equals(cameraController.focusStatus())||"UNAVAILABLE".equals(cameraController.focusStatus())||"LOST".equals(cameraController.focusStatus())))
            Toast.makeText(this,cameraController.focusDescription(),Toast.LENGTH_LONG).show();
        com.example.alpr_v1.experiment.ResearchSessionStore store=researchSessions==null?null:researchSessions.store();
        ResearchExecutionConfig config=experimentSession.isRunning()?experimentSession.frozenExecutionConfig():null;
        if(store!=null&&config!=null&&config.final180PhotoRoi()!=null&&store.sessionId().equals(experimentSession.sessionId()))try{
            store.appendTelemetry("events.jsonl",new org.json.JSONObject().put("event_type","camera_focus_state_changed")
                    .put("experiment_session_id",store.sessionId()).put("camera_observation",cameraController.observation()).toString());
        }catch(Exception error){AppLog.error(this,"CAMERA_FOCUS","Nie zapisano diagnostyki AF",error);}
        renderAnalysisControls();
        if(experimentStartRequest.pending())requestExperimentReadinessRefresh();
    }
    private void configureAnalysisControls() {
        MaterialButton focus=findViewById(R.id.f6_focus_toggle);
        focus.setOnClickListener(v->toggleF6Focus());
        findViewById(R.id.f6_photo_roi_edit).setOnClickListener(v->{
            if(tvSetupAvailable()&&f6FocusMode()&&!preparationActionsLocked())tvSetup.open(com.example.alpr_v1.ui.TvSetupController.Mode.F6_PHOTO);
        });
        findViewById(R.id.session_operation_details).setOnClickListener(v->showSessionOperationDetails());
        if(cameraController!=null)cameraController.setFocusListener(this::onCameraFocusChanged);

        experimentTimerButton.setOnClickListener(
                view ->
                        toggleAnalysisTimer()
        );
        findViewById(R.id.experiment_timer_status).setOnClickListener(view -> showExperimentTimerDialog());
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

        cameraPreviewButton.setOnClickListener(view -> {
            if (cameraStarted || waitingForThermalStart || analysisStartGate.preparing()
                    || experimentStartRequest.pending() || sessionWriteBusy()) return;
            if (cameraPreviewActive) {
                experimentPreviewPaused = true;
                stopCameraPreview(true);
            } else {
                experimentPreviewPaused = false;
                ensureCameraPermission(true);
            }
        });


        /*
         * Jeden przycisk obsługuje cały lifecycle analizy.
         */
        analysisStartButton.setOnClickListener(view -> {
            if (experimentModeEnabled) {
                if (cameraStarted) stopAnalysis();
                else if (experimentStartRequest.pending()) cancelExperimentStart();
                else requestExperimentStart();
                return;
            }
            if (cameraStarted) stopAnalysis();
            else if (experimentStartRequest.pending() || analysisStartGate.preparing() || waitingForThermalStart)
                cancelExperimentStart();
            else {
                if(sessionWriteBusy()){renderAnalysisControls();return;}
                experimentStartRequest.request();
                analysisStartRequestedElapsedNanos=android.os.SystemClock.elapsedRealtimeNanos();
                renderAnalysisControls();
                ensureCameraPermission(false);
            }
        });

        if (!cameraStarted && !cameraPreviewActive) {

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

    private ResearchExecutionConfig researchSummaryFrozenOwner;
    private int researchSummarySettingsRevision=Integer.MIN_VALUE;
    private boolean researchSummaryWasFrozen;

    private void renderAnalysisControls() {
        renderCameraFocusButton();
        if (topAppBar != null) {
            boolean tvAvailable=tvSetupAvailable();
            com.example.alpr_v1.ui.TvSetupMenuState.render(topAppBar.getMenu(),tvAvailable,
                    !preparationActionsLocked(),
                    tvSetup!=null&&tvSetup.editing()?tvSetup.mode():null,tvSetup!=null&&tvSetup.restoring());
        }
        if (tvSetup != null) {tvSetup.openRequested();tvSetup.refreshInputSummary();}

        if (cameraPreviewButton != null) {
            cameraPreviewButton.setText(R.string.camera_action_preview);
            cameraPreviewButton.setContentDescription(getString(
                    cameraPreviewActive
                            ? R.string.camera_preview_stop
                            : R.string.camera_preview_start
            ));
            cameraPreviewButton.setIconResource(R.drawable.ic_camera_preview_32);
            cameraPreviewButton.setEnabled(
                    !cameraStarted && !waitingForThermalStart && !analysisStartGate.preparing()
                            && !experimentStartRequest.pending() && !sessionWriteBusy()
            );
            styleDashboardAction(cameraPreviewButton, cameraPreviewActive ? R.color.alpr_success : R.color.alpr_accent);
        }

        analysisStartButton.setText(
                cameraStarted

                        ? (experimentSession.isRunning()?R.string.main_ui_stop_session:R.string.main_ui_stop_analysis)

                        : waitingForThermalStart || analysisStartGate.preparing()

                          ? R.string.main_ui_stop_analysis

                          : R.string.camera_action_start
        );
        analysisStartButton.setContentDescription(getString(cameraStarted ? (experimentSession.isRunning()?R.string.main_ui_stop_session_description:R.string.main_ui_stop_analysis_description)
                : waitingForThermalStart || analysisStartGate.preparing() ? R.string.analysis_cancel_waiting : R.string.analysis_start));
        if (experimentModeEnabled && !cameraStarted) {
            boolean pending = experimentStartRequest.pending();
            StartBlocker blocker=experimentStartBlocker();
            PreparationCameraContext context=preparedExperimentStart!=null?preparedExperimentStart.cameraContext:preparingCameraContext;
            String inputReason=tvBboxRequested()?tvBboxStartReason():"";
            boolean cameraWait=tvCameraPreparationEnabled()&&cameraPreparationWait(context,currentCameraConfirmation(),inputReason);
            boolean preparationAction=blocker==StartBlocker.READY||blocker==StartBlocker.MODELS
                    ||blocker==StartBlocker.FOCUS
                    ||blocker==StartBlocker.PREVIOUS_SESSION||blocker==StartBlocker.ERROR;
            analysisStartButton.setEnabled(pending||!tvBboxRequested()||inputReason.isEmpty()&&preparationAction||cameraWait);
            analysisStartButton.setText(pending ? R.string.main_ui_stop_analysis : R.string.camera_action_start);
            analysisStartButton.setContentDescription(pending ? "Anuluj oczekujące uruchomienie eksperymentu"
                    : "START: przygotuj i uruchom eksperyment");

        } else analysisStartButton.setEnabled(true);
        if (!cameraStarted && experimentStartRequest.pending()) {
            analysisStartButton.setText(R.string.main_ui_stop_analysis);
            analysisStartButton.setContentDescription("Zatrzymaj oczekujące uruchomienie analizy");
        }


        analysisStartButton.setIconResource(
                cameraStarted
                        || waitingForThermalStart || experimentStartRequest.pending() || !experimentModeEnabled&&analysisStartGate.preparing()

                        ? R.drawable.ic_analysis_stop_32

                        : R.drawable.ic_analysis_play_32
        );
        styleDashboardAction(analysisStartButton, cameraStarted ? R.color.alpr_error : R.color.alpr_accent);


        /*
         * Cropy mogą być zbierane tylko podczas
         * aktywnej analizy.
         */
        renderCollectionControl();


        // The same persistent timer is available for ordinary analysis and F5A.
        if (experimentTimerButton != null) {

            experimentTimerButton.setVisibility(View.VISIBLE);


            /*
             * Konfigurację czasu można zmienić
             * tylko przed rozpoczęciem przebiegu.
             */
            experimentTimerButton.setEnabled(
                    timerSettingsAvailable()
            );


            updateExperimentTimerButton();
        }

        if (exportReportButton != null) {
            exportReportButton.setVisibility(View.VISIBLE);
            exportReportButton.setText(R.string.research_sessions_export);
            exportReportButton.setEnabled(true);
            com.example.alpr_v1.ui.CockpitControlStyle.apply(exportReportButton,exportInProgress||researchSessions.documentBusy()
                    ?com.example.alpr_v1.ui.CockpitControlStyle.State.BUSY:com.example.alpr_v1.ui.CockpitControlStyle.State.OFF);
        }
        if(tvSetup!=null&&uiPreferences!=null&&modelRegistry!=null&&autoTuneManager!=null){
            ResearchExecutionConfig frozen=experimentSession.isRunning()||researchSessions.finalizing()?experimentSession.snapshot().frozenExecutionConfig:null;
            int revision=uiPreferences.getInt(SettingsActivity.KEY_REVISION,0);
            if(frozen!=null? !researchSummaryWasFrozen||researchSummaryFrozenOwner!=frozen : researchSummaryWasFrozen||revision!=researchSummarySettingsRevision){
                researchSummaryFrozenOwner=frozen;researchSummaryWasFrozen=frozen!=null;researchSummarySettingsRevision=revision;
                try{
                    org.json.JSONObject configuration=frozen==null?com.example.alpr_v1.ui.ResearchConfigurationText.planned(uiPreferences,modelRegistry,autoTuneManager):frozen.toJson();
                    tvSetup.setResearchSummary(com.example.alpr_v1.ui.ResearchConfigurationText.isF6(configuration)?
                            com.example.alpr_v1.ui.ResearchConfigurationText.title(configuration)+" · "+com.example.alpr_v1.ui.ResearchConfigurationText.compactDetails(configuration):"");
                }catch(Exception unavailable){tvSetup.setResearchSummary("Konfiguracja niepotwierdzona: "+unavailable.getMessage());}
            }
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

                ((com.example.alpr_v1.ui.LiveHudView) liveHudRow).clearMetrics();
            }
        }
        if (tvSetup != null && tvSetup.editing()) {
            analysisStartButton.setEnabled(false);
            tvSetup.maintainPreparationUi();
        }
        if(!cameraStarted&&!experimentStartRequest.pending()&&sessionWriteBusy())analysisStartButton.setEnabled(false);
        renderPreparationActions();renderCameraFocusButton();renderSessionOperation();
        updateAutoZoomButton();renderSceneModeHudToggle();
        // Presentation follows the final enabled state, including the existing TV editor lock.
        if(experimentModeEnabled&&!cameraStarted)renderExperimentStartStatus(experimentStartBlocker());
        renderConfigurationReadiness();
    }
    private void receiveReadinessSettingsResult(Intent data){
        if(data==null)return;
        ReadinessReturnContext context=ReadinessReturnContext.fromBundle(data.getBundleExtra(ReadinessReturnContext.EXTRA));
        if(context==null)return;
        readinessReturn=context;readinessEditorStarted=false;
        String action=data.getStringExtra(ReadinessReturnContext.EXTRA_ACTION);
        readinessReturnDue="BLOCKED".equals(action);readinessReturnManual=readinessReturnDue;
        readinessDestination="CALIBRATOR".equals(action)?data.getStringExtra(ReadinessReturnContext.EXTRA_MODE)
                :"PREVIEW".equals(action)||"FOCUS".equals(action)?action:"";
        if(readinessDestination==null)readinessDestination="";
        getWindow().getDecorView().post(this::continueReadinessNavigation);
    }
    private void installReadinessEditorReturn(){
        tvSetup.setEditorExitListener(reason->{
            if(readinessReturn==null||!readinessEditorStarted)return;
            readinessEditorStarted=false;readinessReturnDue=true;
            readinessReturnManual=reason==com.example.alpr_v1.ui.TvSetupController.EditorExit.BACKGROUND;
            if(configurationReadiness!=null)configurationReadiness.refresh(true);
            renderConfigurationReadiness();
            getWindow().getDecorView().post(this::continueReadinessNavigation);
        });
    }
    private ReadinessSnapshot.Device readinessDevice(){
        boolean permission=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
        CameraSetupReadiness camera=currentCameraConfirmation();
        boolean stream=false;
        if(analysisScreenForeground&&cameraPreviewActive&&cameraController!=null){
            Size size=chooseAnalysisSize();
            stream=cameraController.isStreamReady(size,cameraResolutionCatalog!=null&&cameraResolutionCatalog.isHighResolution(size));
        }
        boolean verified=stream&&(!tvCameraPreparationEnabled()||camera!=null&&camera.ready()&&camera.frameFreshAt(android.os.SystemClock.elapsedRealtimeNanos()));
        PreparedAnalysisStart prepared=preparedExperimentStart;
        boolean preparedCurrent=prepared!=null&&uiPreferences!=null&&prepared.settingsRevision==uiPreferences.getInt(SettingsActivity.KEY_REVISION,0)
                &&prepared.prepared.timer==experimentTimerConfig&&prepared.prepared.thermal==experimentThermalConfig
                &&prepared.store.state()==com.example.alpr_v1.experiment.ResearchSessionStore.State.PREPARED;
        return new ReadinessSnapshot.Device(permission,verified,experimentModeEnabled?preparedCurrent:cameraStarted,
                !experimentThermalConfig.enabled()||prestartThermalReady,cameraController!=null&&cameraController.focusRequested(),
                cameraController!=null&&cameraController.focusHeld(),sessionWriteBusy(),experimentSession.isRunning(),"",
                camera==null?0:camera.bindingGeneration,analysisControlGeneration);
    }
    private void renderConfigurationReadiness(){
        if(topAppBar!=null){
            MenuItem back=topAppBar.getMenu().findItem(R.id.readiness_main_return);
            if(back!=null){
                boolean show=readinessReturn!=null&&!readinessEditorStarted&&readinessDestination.isEmpty();
                back.setVisible(show);back.setEnabled(show&&!settingsActionsLocked());
                if(show&&settingsActionsLocked()){readinessReturnManual=true;back.setTitle("Podsumowanie · trwa operacja");}
                else back.setTitle(R.string.readiness_return);
            }
        }
        if(configurationReadiness==null||tvSetup==null||!analysisScreenForeground||cameraStarted||experimentSession.isRunning()
                ||sessionWriteBusy()||analysisStartGate.preparing()||experimentStartRequest.pending()||tvSetup.editing()||experimentPreparationFailed)return;
        ReadinessResult result=readinessConfiguration==null||readinessSnapshot==null?null:
                readinessDeviceProjection.updateDevice(readinessSnapshot.withDevice(readinessDevice()),readinessConfiguration);
        String text=new ReadinessPresentation(result,experimentModeEnabled).mainText();
        tvSetup.setResearchSummary(text);
        TextView summary=findViewById(R.id.tv_frame_status);
        summary.setContentDescription(text+". Otwórz Opcje, aby zobaczyć wymagania.");
        summary.setClickable(!settingsActionsLocked());
    }
    private void openReadinessOptions(ReadinessReturnContext context){
        Intent settings=new Intent(this,SettingsActivity.class);
        settings.putExtra(SettingsActivity.EXTRA_THERMAL_ENABLED,experimentThermalConfig.enabled());
        settings.putExtra(SettingsActivity.EXTRA_RETURN_TO_SUMMARY,true);
        if(cameraController!=null)settings.putExtra(SettingsActivity.EXTRA_CAMERA_OBSERVATION,cameraController.observation().toString());
        MetricsCollector.LiveSnapshot mtFrame=metricsCollector.liveSnapshot();
        settings.putExtra(SettingsActivity.EXTRA_MT_FRAME_WIDTH,mtFrame.sourceWidth);
        settings.putExtra(SettingsActivity.EXTRA_MT_FRAME_HEIGHT,mtFrame.sourceHeight);
        if(context!=null)settings.putExtra(ReadinessReturnContext.EXTRA,context.toBundle());
        settingsScreen.launch(settings);
    }
    private void continueReadinessNavigation(){
        if(!analysisScreenForeground||isFinishing()||isDestroyed()||readinessReturn==null)return;
        if(!readinessDestination.isEmpty()){
            if(settingsActionsLocked()||preparationActionsLocked()){
                readinessDestination="";readinessReturnDue=true;readinessReturnManual=true;
                Toast.makeText(this,R.string.readiness_busy_return,Toast.LENGTH_LONG).show();renderConfigurationReadiness();return;
            }
            String destination=readinessDestination;readinessDestination="";
            if("PREVIEW".equals(destination)||"FOCUS".equals(destination)){
                readinessReturnDue=true;readinessReturnManual=true;ensureCameraPermission(true);
                if("FOCUS".equals(destination))findViewById(R.id.f6_focus_toggle).requestFocus();
                renderConfigurationReadiness();return;
            }
            try{
                com.example.alpr_v1.ui.TvSetupController.Mode mode=com.example.alpr_v1.ui.TvSetupController.Mode.valueOf(destination);
                if(tvSetupAvailable())tvSetup.open(mode);
                readinessEditorStarted=tvSetup.editing();
                if(readinessEditorStarted)return;
            }catch(IllegalArgumentException invalid){/* A removed destination returns to a fresh summary. */}
            readinessReturnDue=true;
        }
        if(!readinessReturnDue||readinessReturnManual)return;
        if(settingsActionsLocked()){
            readinessReturnManual=true;Toast.makeText(this,R.string.readiness_busy_return,Toast.LENGTH_LONG).show();
            renderConfigurationReadiness();return;
        }
        ReadinessReturnContext context=readinessReturn;
        readinessReturn=null;readinessReturnDue=false;readinessReturnManual=false;
        openReadinessOptions(context);
    }

    private void pauseCropCollectionForStoppedAnalysis() {
        normalCollectionReady = false;
        analysisControlGeneration++;
        if (!collectionActive) {
            renderCollectionControl();
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
        experimentStartRequest.cancel();
        analysisStartGate.cancel();
        experimentReadinessHandler.removeCallbacks(experimentReadinessRunnable);
        cancelExperimentTimer();
        preparingCameraContext=null;
        waitingForThermalStart=false;
        closeResearchAdmission(reason);
        cameraStarted = false;
        dynamicDisplaySnapshot=null;
        if(overlayView!=null)overlayView.setDynamicVehicleSnapshot(null,0L);
        if(livePresentation!=null)livePresentation.setDynamicPresentation(false);
        cameraPreviewActive = false;
        analysisControlGeneration++;
        uiSceneGeneration.incrementAndGet();
        renderAnalysisControls();
        pendingPreviewCoordination.set(null);
        // Odpowiedź wizualna na STOP nie może czekać na zamknięcie CameraX,
        // pipeline'u ani zapisu metryk.
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.setAnalysisViewportEnabled(false);
        resetAutoZoomForStoppedCamera();
        liveHudAwaitingFreshResult = true;
        stopPreviewSceneMonitor();
        // The UI generation was invalidated before rendering the stopped controls.
        pauseCropCollectionForStoppedAnalysis();
        long stoppedAt=android.os.SystemClock.elapsedRealtimeNanos();
        deferPipelineControl(p -> p.stopScanRun(stoppedAt));
        /*
         * Od tej chwili żaden kolejny trace nie należy
         * już do zakończonego przebiegu.
         */
        finishAnalysisMeasurement(reason);

        if (cameraController != null) {
            pendingDirectLumaFrame.set(null);
            cameraController.stop();
        }
        /*
         * PreviewView zachowuje ostatnią wyrenderowaną klatkę
         * nawet po odpięciu CameraX. Ukrywamy podgląd, ale
         * pozostawiamy jego miejsce w layoucie.
         */
        previewView.setVisibility(View.INVISIBLE);


        /*
         * Nowe uruchomienie analizy ma zacząć bez starego
         * trackingu i overlayu.
         *
         * Nie kasujemy historii cropów.
         */
        if (pipeline != null) {
            deferPipelineControl(AlprPipeline::resetTracking);
        }

        targetStateMachine.reset();

        overlayTracker.reset();

        previewPlateTracker.reset();
        previewVehicleTracker.reset();
        resetGlobalPreviewMotionState();
        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();

        latestPipelinePlateItems =
                java.util.Collections.emptyList();
        latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
        latestPreviewMotionItems = java.util.Collections.emptyList();
        latestPipelinePlateEntityId = 0L;
        plateOverlayFreshness.reset();

        previewSceneAnchorGuard.reset();

        previewSceneAnchorPending =
                false;

        lastCaptureByTrack.clear();

        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(
                java.util.Collections.emptyList()
        );
        overlayView.hardResetForNewScene(currentPreviewSourceFrameStamp().continuityStamp());

        livePresentation.stop();



        renderAnalysisControls();

        recordInfo(
                "Kamera i pipeline zatrzymane"
        );
    }

    private void scheduleExperimentTimer(TimerConfig timerConfig) {
        cancelExperimentTimer(); // Detach presentation only; the retained deadline is not rearmed.
        if (!researchSessions.timerState().running) return;
        researchSessions.armTimer(metricsCollector);
        long deadline = researchSessions.timerState().deadlineElapsedNanos;
        experimentTimerDeadlineElapsedMillis = deadline < 0L ? -1L : deadline / 1_000_000L;
        updateExperimentTimerButton();
        if (deadline >= 0L) experimentTimerHandler.post(experimentTimerUiRunnable);
    }

    private void onAnalysisTimerExpired(String id) {
        com.example.alpr_v1.experiment.ResearchSessionViewModel.TimerState timer=researchSessions.timerState();
        if (id == null || !id.equals(timer.id) || !timer.expired || timer.running) return;
        if (!experimentSession.sessionId().equals(id)) {
            if (cameraStarted) stopAnalysis(ExperimentSession.CompletionReason.TIMER);
        } else if (!researchSessions.finalizing() && researchSessions.store() != null
                && researchSessions.store().state() == com.example.alpr_v1.experiment.ResearchSessionStore.State.FINALIZING) {
            stopAnalysis(ExperimentSession.CompletionReason.TIMER);
        }
    }

    private void cancelExperimentTimer() {
        experimentTimerHandler.removeCallbacks(experimentTimerUiRunnable);
        experimentTimerDeadlineElapsedMillis = -1L;
        lastExperimentUiTickMillis = 0L;
    }

    private boolean beginAnalysisMeasurement(PreparedAnalysisStart ready) {
        if(ready.config!=null&&ready.config.final180PhotoRoi()!=null&&focusBlocksF6Start())return false;
        CameraSetupReadiness startCamera=null;
        if(ready.config!=null&&"roi_budget".equals(ready.config.experimentType)){
            CameraSetupReadiness current=currentCameraConfirmation();
            startCamera=current;
            CameraSetupReadiness.Reason failure=preparedCameraFailure(ready,current);
            if(failure!=CameraSetupReadiness.Reason.READY){rejectPreparedCamera(ready,failure,current);return false;}
        }
        if (researchSessions.finalizing()) {
            discardPreparedStart(ready);
            Toast.makeText(this,"Trwa zapisywanie paczki poprzedniego eksperymentu",Toast.LENGTH_LONG).show();
            return false;
        }
        synchronized (this) {
            telemetryActiveTrackIds.clear();
            telemetryConfirmedTrackIds.clear();
            telemetryConsensusByTrack.clear();
        }
        ResearchExecutionConfig frozenConfig = ready.config;
        targetStateMachine.setEnabled(
                frozenConfig == null || frozenConfig.lockEnabled
        );
        autoZoomController.setEnabled(
                frozenConfig == null
                        ? uiPreferences.getBoolean(KEY_AUTO_ZOOM_ENABLED, false)
                        : frozenConfig.autoZoomEnabled
        );
        if (ready.prepared != null) {
            TimerConfig timerForSession = ready.prepared.timer;
            ExperimentIdentity identity = ready.prepared.identity;

            boolean started;
            try {
                ExperimentSession.Prepared prepared = ready.prepared;
                com.example.alpr_v1.experiment.ResearchSessionStore store = ready.store;
                long startWall = ready.startWall;
                long startElapsed = ready.startElapsed;
                long startMonotonic = ready.startMonotonic;
                org.json.JSONObject calibrationValidation="roi_budget".equals(ready.config.experimentType)
                        ?tvSetup.revalidationEvidence(startCamera):null;
                if(ready.config.tvBboxCrop()!=null){
                    if(calibrationValidation==null)throw new IllegalStateException("CALIBRATION_REVALIDATION_EVIDENCE_MISSING");
                    calibrationValidation.put("calibration_digest_sha256",ready.config.tvBboxCrop().calibrationDigest);
                }
                if(ready.config.final180PhotoRoi()!=null)store.setCameraObservationBeforeStart(cameraController.observation());
                java.util.concurrent.CompletableFuture<Void> startCommitted = store.startAtQueued(startWall, startElapsed,
                        "roi_budget".equals(ready.config.experimentType)
                                ?ready.config.tvBboxCrop()==null?tvSetup.freeze(startWall):tvSetup.freezeInputSelection(ready.config.tvBboxCrop()):null,
                        calibrationValidation==null?null:calibrationValidation.put("event_type","tv_setup_calibration_revalidated"),
                        error -> runOnUiThread(() -> {
                    if (experimentSession.isRunning() && experimentSession.sessionId().equals(prepared.sessionId)) {
                        recordError("Nie zapisano początku sesji", error);
                        stopAnalysis(ExperimentSession.CompletionReason.ERROR);
                    }
                }));
                // Preserve durable START before any inference, without blocking UI on storage.
                pipelineInferenceExecutor.execute(() -> {
                    try { startCommitted.join(); }
                    catch (java.util.concurrent.CompletionException ignored) { /* failure callback stops this run */ }
                });
                started = experimentSession.startPrepared(prepared,startWall,startElapsed);
                if (!started) throw new IllegalStateException("Eksperyment już trwa");
                metricsCollector.startMeasurementSession(startWall,startElapsed,startMonotonic);
                metricsCollector.setResearchCollector(store);
                pipeline.setResearchCollector(store);
                collectionActive = true;
                collectionSessionId = prepared.sessionId;
                collectionSessionStartedElapsedNanos = startElapsed;
                collectionSequence = 0;
                selectVerificationSession(prepared.sessionId);
                selectedVerificationCaptureId = "";
                captureGalleryState.beginCollectionWindow();
                metricsCollector.startCropSession(collectionSessionId,resolvedCropLimit);
            } catch (Exception error) {
                experimentSession.finish(ExperimentSession.CompletionReason.ERROR);
                metricsCollector.finishMeasurementSession();
                metricsCollector.setResearchCollector(null);
                discardPreparedStart(ready);
                recordWarning("START badania zablokowany: "+error.getMessage());
                livePresentation.showState(LivePresentationController.State.ERROR,"Nie przygotowano zapisu sesji");
                Toast.makeText(this,"Nie można rozpocząć badania: "+com.example.alpr_v1.ui.CalibrationOperatorText.preparationError(this,error.getMessage()),Toast.LENGTH_LONG).show();
                return false;
            }

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
            metricsCollector.setResearchCollector(null);
            pipeline.setResearchCollector(null);
            metricsCollector.startMeasurementSession(ready.startWall, ready.startElapsed, ready.startMonotonic);

            /*
             * Zwykła analiza nie może odziedziczyć informacji
             * o wcześniejszym eksperymencie.
             */
            experimentSession.reset();
            metricsCollector.setExperimentSessionId("");
            researchSessions.startOrdinaryTimer(ready.timer, ready.startWall, ready.startElapsed, metricsCollector);
            scheduleExperimentTimer(ready.timer);
            if (latestThermalSnapshot != null) {
                metricsCollector.recordThermalSample(latestThermalSnapshot);
            }
        }
        CrashSessionMarker.Recovery recoveredCrash = crashSessionMarker.consumeRecovery();
        metricsCollector.setCrashMeasurement(true,recoveredCrash.count,recoveredCrash.lastSessionId,
                recoveredCrash.lastSessionStartedAtMillis);
        crashSessionMarker.markStarted(
                experimentSession.isRunning()
                        ? experimentSession.sessionId()
                        : "measurement-" + System.currentTimeMillis()
        );
        return true;
    }

    private ResearchExecutionConfig buildResearchExecutionConfig(PreparationCameraContext capturedCamera) {
        String experimentType = uiPreferences.getString(
                SettingsActivity.KEY_EXPERIMENT_TYPE,
                "roi_budget"
        );
        if (experimentType == null || experimentType.trim().isEmpty()) {
            experimentType = "roi_budget";
        }
        String experimentVariant = uiPreferences.getString(
                SettingsActivity.KEY_EXPERIMENT_VARIANT,
                ""
        );
        if (experimentVariant == null || experimentVariant.trim().isEmpty()) {
            experimentVariant = "roi_budget".equals(experimentType.trim())
                    ? experimentRoiBudgetPolicy.wireName()
                    : "default";
        }
        ResearchExecutionConfig configuration=new ResearchExecutionConfig(
                experimentType,
                experimentVariant,
                experimentRoiBudgetPolicy,
                recognitionProfile,
                cameraResolutionSelection.wireName(),
                experimentLockEnabled,
                experimentAutoZoomEnabled,
                experimentRoiBudgetPolicy.usesVehicleCascade(),
                true,
                true,
                true,
                resolveResearchStage(
                        ModelRole.VEHICLE,
                        experimentRoiBudgetPolicy.usesVehicleCascade(),
                        SettingsActivity.KEY_RESEARCH_MP_EXECUTION
                ),
                resolveResearchStage(
                        ModelRole.PLATE,
                        true,
                        SettingsActivity.KEY_RESEARCH_MT_EXECUTION
                ),
                resolveResearchStage(
                        ModelRole.CHARACTER,
                        true,
                        SettingsActivity.KEY_RESEARCH_MZ_EXECUTION
                ),
                modelRegistry.getBasePackage(),
                modelRegistry.isCompositionModified(),
                MetricsCollector.capturePackageSizeBytes(
                        modelRegistry.getActivePackage(),
                        modelRegistry.getActive(ModelRole.VEHICLE),
                        modelRegistry.getActive(ModelRole.PLATE),
                        modelRegistry.getActive(ModelRole.CHARACTER)
                ),
                SceneHandlingMode.fromWireName(uiPreferences.getString(
                        SettingsActivity.KEY_SCENE_HANDLING_MODE, SceneHandlingMode.DYNAMIC_CONTINUITY.wireName())),
                com.example.alpr_v1.acquisition.DynamicMtSettings.read(uiPreferences)
        );
        if("roi_budget".equals(experimentType)){
            if(capturedCamera==null)throw new CameraPreparationWait(CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_MISSING);
            if(tvBboxRequested()!=(capturedCamera.input!=null))throw new CameraPreparationWait(CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_STALE);
            configuration=configuration.withCameraSetup(capturedCamera.confirmation.applied);
            if(capturedCamera.input!=null)configuration=configuration.withTvBboxCrop(capturedCamera.input);
            if(capturedCamera.f6Enabled){
                try{configuration=configuration.withFinal180(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.resolve(
                        capturedCamera.photoRecord,capturedCamera.photoApprovalContext,capturedCamera.input,
                        new org.json.JSONObject(capturedCamera.tvApproval),com.example.alpr_v1.experiment.ResearchSessionStore.sessionsRoot(this))
                        .forSession(capturedCamera.input,new org.json.JSONObject(capturedCamera.tvApproval)));}
                catch(org.json.JSONException invalid){throw new IllegalStateException("F6_PHOTO_ROI_REQUIRED: wskaż obszar fotografii F6",invalid);}
            }
            android.util.Log.i("ALPR_CAMERA_SETUP","preparation_worker "+capturedCamera.confirmation.diagnostics());
            if(configuration.final180PhotoRoi()!=null)try{
                AppLog.info(this,"F6_SESSION_CONFIG",new org.json.JSONObject().put("phase","prepared_frozen")
                        .put("execution",configuration.toJson()).put("time_domain","ANDROID_ELAPSED_REALTIME")
                        .put("elapsed_nanos",android.os.SystemClock.elapsedRealtimeNanos()).toString());
            }catch(org.json.JSONException diagnosticFailure){android.util.Log.w("F6_SESSION_CONFIG",diagnosticFailure);}
        }
        return configuration;
    }

    private static final class PreparationCameraContext {
        final CameraSetupReadiness confirmation;
        final com.example.alpr_v1.setup.TvBboxCrop input;
        final boolean f6Enabled;
        final String photoRecord,photoApprovalContext,tvApproval;
        PreparationCameraContext(CameraSetupReadiness confirmation,com.example.alpr_v1.setup.TvBboxCrop input){
            this(confirmation,input,false,"","","");
        }
        PreparationCameraContext(CameraSetupReadiness confirmation,com.example.alpr_v1.setup.TvBboxCrop input,
                boolean enabled,String photo,String context,String approval){
            this.confirmation=confirmation;this.input=input;
            f6Enabled=enabled;photoRecord=photo;photoApprovalContext=context;tvApproval=approval;
        }
    }
    private static final class CameraPreparationWait extends IllegalStateException {
        final CameraSetupReadiness.Reason reason;
        CameraPreparationWait(CameraSetupReadiness.Reason reason){super(reason.description());this.reason=reason;}
    }
    private String lastCameraSetupDiagnostic="",cameraPreparationNotice="";
    private CameraSetupReadiness.Reason missingCameraReason(){return cameraController==null?CameraSetupReadiness.Reason.CAMERA_NOT_BOUND:CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_MISSING;}
    private CameraSetupReadiness currentCameraConfirmation(){
        CameraSetupReadiness current=cameraController==null?null:cameraController.captureSetupReadiness();
        logCameraSetup("readiness",current==null?missingCameraReason():current.reason,current,false);
        return current;
    }
    private void logCameraSetup(String phase,CameraSetupReadiness.Reason reason,CameraSetupReadiness current,boolean force){
        String key=phase+":"+reason+":"+(current==null?"missing":current.bindingGeneration+":"+current.revision+":"+current.requested+":"+current.requestedZoom);
        if(force||!key.equals(lastCameraSetupDiagnostic)){
            lastCameraSetupDiagnostic=key;
            android.util.Log.i("ALPR_CAMERA_SETUP",phase+" refusal="+reason+" "+(current==null?"published_setup=missing reader_thread="+Thread.currentThread().getName():current.diagnostics()));
        }
    }
    private PreparationCameraContext capturePreparationCameraContext(){
        if(!tvCameraPreparationEnabled())return null;
        CameraSetupReadiness current=currentCameraConfirmation();
        if(current==null)throw new CameraPreparationWait(missingCameraReason());
        if(!current.ready())throw new CameraPreparationWait(current.reason);
        if(tvSetup!=null)tvSetup.revalidateForCamera(current);
        com.example.alpr_v1.setup.TvBboxCrop input=null;
        if(tvBboxRequested()){
            if(tvSetup==null)throw new CameraPreparationWait(CameraSetupReadiness.Reason.CAMERA_SETUP_GEOMETRY_MISMATCH);
            try{input=tvSetup.selectionForInput(current.applied);}
            catch(RuntimeException invalid){throw new CameraPreparationWait(CameraSetupReadiness.Reason.CAMERA_SETUP_GEOMETRY_MISMATCH);}
            if(input.geometry.bindingGeneration!=current.bindingGeneration)throw new CameraPreparationWait(CameraSetupReadiness.Reason.CAMERA_SETUP_GENERATION_CHANGED);
        }
        logCameraSetup("preparation_capture",CameraSetupReadiness.Reason.READY,current,true);
        return new PreparationCameraContext(current,input,
                uiPreferences.getBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,false),
                uiPreferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,""),
                uiPreferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,""),
                tvSetup==null?"":tvSetup.approvedTvRecord());
    }
    private CameraSetupReadiness.Reason preparationCameraFailure(PreparationCameraContext captured,CameraSetupReadiness current,boolean requireFresh){
        if(captured==null||current==null)return CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_MISSING;
        CameraSetupReadiness.Reason identity=captured.confirmation.identityFailure(current);
        if(identity!=CameraSetupReadiness.Reason.READY)return identity;
        if(captured.input!=null&&(tvSetup==null||!tvSetup.matchesPreparedGeometry(captured.input)))return CameraSetupReadiness.Reason.CAMERA_SETUP_GEOMETRY_MISMATCH;
        if(requireFresh&&!current.ready())return current.reason;
        if(requireFresh&&!captured.confirmation.applied.same(current.applied))return CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_STALE;
        return CameraSetupReadiness.Reason.READY;
    }
    private void rejectPreparedCamera(PreparedAnalysisStart ready,CameraSetupReadiness.Reason reason,CameraSetupReadiness current){
        boolean retain=ready.cameraContext!=null&&transientFrameWait(ready.cameraContext,current);
        if(retain)preparedExperimentStart=ready;
        else{if(preparedExperimentStart==ready)preparedExperimentStart=null;discardPreparedStart(ready);}
        if(!retain)experimentStartRequest.cancel();
        experimentPreparationFailed=false;experimentPreparationError="";
        cameraPreparationNotice=reason.description();logCameraSetup("start_rejected",reason,current,true);
        requestExperimentReadinessRefresh();renderAnalysisControls();
    }
    private boolean transientFrameWait(PreparationCameraContext captured,CameraSetupReadiness current){
        return current!=null&&current.reason==CameraSetupReadiness.Reason.NO_FRESH_CAMERA_FRAME
                &&(captured==null||preparationCameraFailure(captured,current,false)==CameraSetupReadiness.Reason.READY);
    }
    /** Only authorizes waiting/preparation. A fresh UI capture is still mandatory before model admission. */
    private boolean cameraPreparationWait(PreparationCameraContext captured,CameraSetupReadiness current,String inputReason){
        if(current==null||!inputReason.equals(current.reason.description()))return false;
        if(captured!=null)return transientFrameWait(captured,current);
        return current.reason==CameraSetupReadiness.Reason.CAMERA_NOT_BOUND
                ||current.reason==CameraSetupReadiness.Reason.CAMERA_ROUTE_UNCONFIRMED
                ||current.reason==CameraSetupReadiness.Reason.ZOOM_NOT_ACKNOWLEDGED
                ||current.reason==CameraSetupReadiness.Reason.NO_FRESH_CAMERA_FRAME;
    }
    private CameraSetupReadiness.Reason preparedCameraFailure(PreparedAnalysisStart ready,CameraSetupReadiness current){
        CameraSetupReadiness.Reason failure=preparationCameraFailure(ready.cameraContext,current,true);
        if(failure!=CameraSetupReadiness.Reason.READY)return failure;
        if(ready.config==null||ready.config.cameraSetup==null)return CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_MISSING;
        if(!ready.config.cameraSetup.same(ready.cameraContext.confirmation.applied)
                ||(ready.cameraContext.input!=null)!=(ready.config.tvBboxCrop()!=null))return CameraSetupReadiness.Reason.CAMERA_SETUP_SNAPSHOT_STALE;
        return CameraSetupReadiness.Reason.READY;
    }

    private boolean tvCameraPreparationEnabled(){return uiPreferences!=null&&uiPreferences.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false)
            &&"roi_budget".equals(uiPreferences.getString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget"));}
    private boolean tvSetupAvailable(){return uiPreferences!=null&&com.example.alpr_v1.ui.TvSetupAvailability.available(
            uiPreferences.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false),uiPreferences.getString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget"));}
    private void restoreTvCameraSetup(){
        if(cameraController==null||uiPreferences==null||cameraStarted||experimentSession.isRunning())return;
        com.example.alpr_v1.camera.CameraSetupSnapshot saved=tvSetupAvailable()?
                com.example.alpr_v1.camera.CameraSetupSnapshot.restore(uiPreferences.getString(com.example.alpr_v1.camera.CameraSetupSnapshot.PREFERENCE,"")):null;
        cameraController.apply(saved==null?com.example.alpr_v1.camera.CameraSelection.defaultBack():saved.selection,saved==null?1:saved.zoomRatio);
    }
    private boolean cameraSetupMatches(ResearchExecutionConfig config){
        return !tvCameraPreparationEnabled()||config!=null&&config.cameraSetup!=null&&config.cameraSetup.same(cameraController.setupSnapshot());
    }

    private ResearchStageExecutionConfig resolveResearchStage(
            ModelRole role,
            boolean enabled,
            String preferenceKey
    ) {
        if (!enabled) return ResearchStageExecutionConfig.disabled(role);
        InstalledModel model = modelRegistry.getActive(role);
        if (model == null) {
            throw new IllegalStateException(
                    "Brak aktywnego modelu etapu " + role.wireName()
            );
        }
        ModelVariant variant = autoTuneManager.requireResearchVariant(model);
        ResearchExecutionChoice choice = ResearchExecutionChoice.fromWireName(
                uiPreferences.getString(
                        preferenceKey,
                        ResearchExecutionChoice.AUTO.wireName()
                )
        );
        ExecutionProfile profile = choice.resolveApproved(variant);
        return ResearchStageExecutionConfig.enabled(
                role,
                model,
                variant,
                profile,
                modelRegistry
        );
    }


    private void closeResearchAdmission(ExperimentSession.CompletionReason reason) {
        if (researchSessions == null) return;
        researchSessions.closeMeasurement(reason, metricsCollector);
        if (researchSessions.store() != null
                && researchSessions.store().state() == com.example.alpr_v1.experiment.ResearchSessionStore.State.FINALIZING) {
            collectionActive = false;
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
        closeResearchAdmission(reason);
        boolean researchWasRunning = researchSessions.store() != null
                && researchSessions.store().state() == com.example.alpr_v1.experiment.ResearchSessionStore.State.FINALIZING
                && !researchSessions.finalizing();

        if (experimentSession.isRunning() || researchWasRunning) {
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
        crashSessionMarker.markFinished();
        if (researchWasRunning && researchSessions.store() != null) {
            com.example.alpr_v1.experiment.ResearchSessionStore store = researchSessions.store();
            ExperimentSession.Snapshot snapshot = experimentSession.snapshot();
            if(snapshot.frozenExecutionConfig!=null&&snapshot.frozenExecutionConfig.final180PhotoRoi()!=null)try{
                AppLog.info(this,"F6_SESSION_CONFIG",new org.json.JSONObject().put("phase","stop_frozen")
                        .put("session_id",store.sessionId()).put("execution",snapshot.frozenExecutionConfig.toJson())
                        .put("time_domain","ANDROID_ELAPSED_REALTIME").put("elapsed_nanos",android.os.SystemClock.elapsedRealtimeNanos()).toString());
            }catch(org.json.JSONException diagnosticFailure){android.util.Log.w("F6_SESSION_CONFIG",diagnosticFailure);}
            MetricsCollector measured = metricsCollector;
            android.content.Context application = getApplicationContext();
            AlprPipeline finishingPipeline = pipeline;
            researchSessions.beginFinalizing();
            collectionSessionId="";
            retainCaptureGalleryState();
            com.example.alpr_v1.experiment.ResearchSessionViewModel retained = researchSessions;
            try{
            java.util.concurrent.CompletableFuture<Void> cropPrepared = captureGalleryState.cropPreparationBarrier();
            pipelineInferenceExecutor.execute(() -> {
                try{cropPrepared.join();
                finishingPipeline.setResearchCollector(null);
                finishingPipeline.setResearchExecutionConfig(null);
                measured.setResearchCollector(null);
                retained.finish(store,() -> new com.example.alpr_v1.experiment.ResearchSessionStore.Telemetry(
                        measured.createJsonReport(DeviceProfile.capture(application),new ModelRegistry(application),
                                new AutoTuneManager(application),snapshot),measured.createCsvReport(),measured.createThermalCsv(),
                        measured.createFrameFlowCsv(),measured.createEventsJsonl(),AppLog.contents(application)),snapshot.frozenExecutionConfig);
                }catch(Exception|Error failure){retained.failFinalization(store,failure);}
            });}catch(RuntimeException|Error failure){retained.failFinalization(store,failure);}
        } else deferPipelineControl(p -> p.setResearchExecutionConfig(null));
        targetStateMachine.setEnabled(true);
        autoZoomController.setEnabled(uiPreferences.getBoolean(
                KEY_AUTO_ZOOM_ENABLED,
                false
        ));

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
    private void ensureCameraPermission(boolean previewOnly) {
        pendingPreviewPermissionRequest = previewOnly;
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            pendingPreviewPermissionRequest = false;
            if (previewOnly) {
                startCameraPreview();
            } else {
                requestAnalysisStart();
            }
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
            experimentStartRequest.cancel();
            renderAnalysisControls();
            showCameraPermissionSettingsDialog();
            return;
        }

        uiPreferences.edit()
                .putBoolean(
                        KEY_CAMERA_PERMISSION_REQUESTED,
                        true
                )
                .apply();

        experimentStartRequest.permissionStarted();
        permissionLauncher.launch(
                Manifest.permission.CAMERA
        );
    }

    private void showCameraPermissionSettingsDialog() {
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle("Dostęp do kamery")
                .setMessage(
                        "Dostęp do kamery został wyłączony. "
                                + "Aby uruchomić podgląd lub analizę, nadaj aplikacji "
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
        previewSceneHandler.removeCallbacks(ordinaryMemoryMonitor);
        if(ordinaryDynamicResourcesEnabled()&&cameraStarted)previewSceneHandler.post(ordinaryMemoryMonitor);
        lastPhoneOrientationUiNanos = 0L;

        previewSceneHandler.removeCallbacks(
                previewSceneMonitorRunnable
        );

        previewSceneDetector.reset();
        previewSceneRecoveryRebaseRevision.set(0L);
        previewSceneRecoveryRebaseAppliedRevision = 0L;
        previewPresentationBarrier.reset();

        previewSceneMonitorRunning =
                true;

        previewSceneHandler.post(
                previewSceneMonitorRunnable
        );
    }

    private final Handler phoneOrientationHandler = new Handler(Looper.getMainLooper());
    private boolean phoneOrientationRunning;
    private final Runnable phoneOrientationTick = new Runnable() {
        @Override public void run() {
            if (!phoneOrientationRunning) return;
            updatePhoneOrientationHud();
            phoneOrientationHandler.postDelayed(this, 250L);
        }
    };

    private void startPhoneOrientationUi() {
        phoneOrientationRunning = true;
        lastPhoneOrientationUiNanos = 0L;
        if (cameraMotionMonitor != null) cameraMotionMonitor.start();
        phoneOrientationHandler.removeCallbacks(phoneOrientationTick);
        phoneOrientationHandler.post(phoneOrientationTick);
    }

    private void stopPhoneOrientationUi() {
        phoneOrientationRunning = false;
        phoneOrientationHandler.removeCallbacks(phoneOrientationTick);
    }

    private void updatePhoneOrientationHud() {
        if (phoneOrientationHud == null || cameraMotionMonitor == null) return;
        long now = android.os.SystemClock.elapsedRealtimeNanos();
        if (now - lastPhoneOrientationUiNanos < 250_000_000L) return;
        lastPhoneOrientationUiNanos = now;
        android.view.Display display = previewView.getDisplay();
        phoneOrientationHud.render(cameraMotionMonitor.orientation(
                display == null ? android.view.Surface.ROTATION_0 : display.getRotation()));
    }

    private boolean needsPreviewSceneSampling() {
        if (ordinaryDynamicResourcesEnabled()) return false;
        if (effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY)
            return autoZoomReturnValidationPending || isAutoZoomHoldingMemory();
        if (pipeline != null && pipeline.isStaticIdle()) return false;
        return previewPresentationBarrier.active()
                || previewSceneAnchorPending
                || previewSceneRecoveryRebaseRevision.get()
                > previewSceneRecoveryRebaseAppliedRevision
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
        previewSceneHandler.removeCallbacks(ordinaryMemoryMonitor);

        previewSceneMonitorRunning =
                false;

        previewSceneHandler.removeCallbacks(
                previewSceneMonitorRunnable
        );

        previewSceneDetector.reset();
        previewSceneRecoveryRebaseRevision.set(0L);
        previewSceneRecoveryRebaseAppliedRevision = 0L;
        previewPresentationBarrier.reset();
    }


    private void renderPreviewContinuityDecision(
            SceneTransitionDecision decision,
            float sceneScore,
            float changedFraction,
            List<OverlayItem> trackedItems
    ) {
        if (decision == null) return;
        if (decision.action != SceneTransitionAction.HARD_RESET && renderStaticIdle()) return;
        if (decision.action == SceneTransitionAction.NONE
                && decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.STABLE) {
            lastRenderedContinuityState =
                    com.example.alpr_v1.continuity.SceneContinuityState.STABLE;
            if (ordinaryDynamicResourcesEnabled()) {
                refreshVehicleIdentityPresentation();
                return;
            }
            if (renderSearchControls()) return;
            TargetSnapshot target = targetStateMachine.snapshot();
            if (target != null && target.hasTrack()) {
                livePresentation.showState(
                        LivePresentationController.State.TRACKING,
                        hudRoiLabel()
                );
            } else {
                showSearchingStatusForCurrentPipeline();
            }
            return;
        }
        lastRenderedContinuityState = decision.nextState;
        SceneTransitionAction action = decision.action;
        if (action == SceneTransitionAction.HARD_RESET) {
            invalidateUiForPreviewSceneChange(sceneScore, changedFraction);
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
            showSearchingStatusForCurrentPipeline();
            return;
        }

        boolean holding = action == SceneTransitionAction.SOFT_HOLD
                || decision.nextState
                == com.example.alpr_v1.continuity.SceneContinuityState.MOTION_HOLD;
        if (holding) {
            if (effectiveSceneHandlingMode()
                    == SceneHandlingMode.DYNAMIC_CONTINUITY) {
                if (trackedItems != null) {
                    overlayView.setStationaryScene(stationarySceneSupported());
                    overlayView.setPreviewItems(
                            dynamicCameraMotionOverlayItems(trackedItems)
                    );
                }
            } else {
                if (trackedItems != null && !trackedItems.isEmpty()) {
                    presentTrackedPreviewOverlay(trackedItems);
                }
                markVehicleOverlaysPredicted();
            }
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
                resetGlobalPreviewMotionState();
                previewPlateTracker.reset();
                overlayTracker.reset();
                latestPipelinePlateItems = java.util.Collections.emptyList();
                latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
                latestPipelinePlateEntityId = 0L;
                plateOverlayFreshness.reset();
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

    private void recordPreviewDecisionAuthority(
            PreviewContinuityUiPolicy.Outcome outcome,
            String source
    ) {
        if (outcome == null) return;
        String authority = outcome.authority
                == PreviewContinuityUiPolicy.Authority.COORDINATOR
                ? "coordinator" : "legacy_fallback";
        metricsCollector.previewDecisionAuthority(authority);
        if (outcome.authority
                == PreviewContinuityUiPolicy.Authority.LEGACY_FALLBACK) {
            android.util.Log.d(
                    "ALPR_PREVIEW_AUTHORITY",
                    "authority=legacy_fallback source="
                            + (source == null ? "unknown" : source)
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

        /*
         * Wszystkie wyniki pipeline'u rozpoczęte przed tym
         * momentem należą już do starej sceny.
         */
        long generation = uiSceneGeneration.incrementAndGet();

        /* Pipeline decision has already been applied by SceneTransitionCoordinator. */
        targetStateMachine.reset();

        clearAutoZoomRecognitionMemory();
        if (cameraTransformInProgress) {
            abortAutoZoomAfterTransform = true;
            resetAutoZoomSessionAfterReturn = true;
        } else if (currentCameraZoomRatio > 1.01f) {
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
        previewVehicleTracker.reset();
        resetGlobalPreviewMotionState();

        previewSceneDetector.reset();
        previewSceneAnchorGuard.reset();

        previewSceneAnchorPending = effectiveSceneHandlingMode() != SceneHandlingMode.STRICT_SCENE_BOUNDARY;

        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();

        latestPipelinePlateItems =
                java.util.Collections.emptyList();
        latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
        latestPreviewMotionItems = java.util.Collections.emptyList();
        latestPipelinePlateEntityId = 0L;
        plateOverlayFreshness.reset();



        lastCaptureByTrack.clear();



        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(
                java.util.Collections.emptyList()
        );
        overlayView.hardResetForNewScene(currentPreviewSourceFrameStamp().continuityStamp());
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.setAnalysisViewportEnabled(true);
        if (effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
            // The coordinator already invalidated old results. Never wait for a preview rebase
            // that STATIC_IDLE can stop scheduling before its first callback.
            previewPresentationBarrier.release();
            previewSceneRecoveryRebaseRevision.set(0L);
            previewSceneRecoveryRebaseAppliedRevision = 0L;
            renderedStaticIdleScene = -1L;
        }


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
         * To jest wyłącznie awaryjna ścieżka utraty lokalnego trackera.
         * Nie tworzy globalnej generacji sceny. Jeżeli pipeline istnieje,
         * jego koordynator zwiększy visualEpoch przez soft recovery.
         */
        long generation = uiSceneGeneration.get();


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



        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
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

    private void startCameraPreview() {
        if (cameraStarted || cameraPreviewActive) return;
        final long previewRequest = ++previewRequestGeneration;
        cameraPreviewActive = true;
        pendingPreviewPermissionRequest = false;
        previewView.setVisibility(View.VISIBLE);
        overlayView.setFocusedTrackId(0L);
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setPreviewItems(java.util.Collections.emptyList());
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(java.util.Collections.emptyList());
        overlayView.setAnalysisViewportEnabled(true);
        livePresentation.clearResult();
        livePresentation.showUserStatus(
                LivePresentationController.State.PREVIEW,
                getString(R.string.live_state_preview),
                getString(R.string.live_hint_preview)
        );
        renderAnalysisControls();

        Size requestedCameraSize = experimentModeEnabled ? chooseAnalysisSize() : choosePreviewSize();
        experimentPreviewSize = experimentModeEnabled ? requestedCameraSize : null;
        boolean allowHighResolution = !ordinaryDynamicResourcesEnabled() && cameraResolutionCatalog != null
                && cameraResolutionCatalog.isHighResolution(requestedCameraSize);
        cameraController.startPreview(
                error -> runOnUiThread(() -> {
                    if (!cameraPreviewActive || previewRequest != previewRequestGeneration) return;
                    cameraPreviewActive = false;
                    if (experimentModeEnabled) {
                        experimentPreparationFailed = true;
                        experimentPreparationError = "Błąd kamery: " + error.getMessage();
                        experimentPreviewPaused = true;
                        experimentStartRequest.cancel();
                    }
                    previewView.setVisibility(View.INVISIBLE);
                    overlayView.setAnalysisViewportEnabled(false);
                    renderAnalysisControls();
                    livePresentation.showState(
                            LivePresentationController.State.ERROR,
                            ""
                    );
                    livePresentation.showTransient(
                            getString(R.string.camera_error, error.getMessage())
                    );
                    recordError("Błąd podglądu kamery: " + error.getMessage(), error);
                }),
                requestedCameraSize,
                allowHighResolution,
                () -> {
                    if (cameraPreviewActive && previewRequest == previewRequestGeneration)
                        requestExperimentReadinessRefresh();
                }
        );
        recordInfo("Uruchomiono podgląd kadru bez analizy");
    }

    private void stopCameraPreview(boolean reportToUser) {
        if (!cameraPreviewActive) return;
        previewRequestGeneration++;
        cameraPreviewActive = false;
        pendingPreviewPermissionRequest = false;
        if (cameraController != null) cameraController.stop();
        previewView.setVisibility(View.INVISIBLE);
        overlayView.setAnalysisViewportEnabled(false);
        overlayView.setFocusedTrackId(0L);
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setPreviewItems(java.util.Collections.emptyList());
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(java.util.Collections.emptyList());
        livePresentation.stop();
        renderAnalysisControls();
        if (reportToUser) recordInfo("Wyłączono podgląd kadru");
    }

    private final com.example.alpr_v1.ui.AnalysisStartGate analysisStartGate = new com.example.alpr_v1.ui.AnalysisStartGate();
    private final Handler experimentReadinessHandler = new Handler(Looper.getMainLooper());
    private final Runnable experimentReadinessRunnable = new Runnable() {
        @Override public void run() {
            if (!analysisScreenForeground || cameraStarted || !experimentModeEnabled&&!experimentStartRequest.pending()) return;
            refreshExperimentStartReadiness();
            if (analysisScreenForeground && !cameraStarted && (experimentModeEnabled||experimentStartRequest.pending()))
                experimentReadinessHandler.postDelayed(this, 250L);
        }
    };
    private PreparedAnalysisStart preparedExperimentStart;
    private PreparationCameraContext preparingCameraContext;
    private boolean analysisScreenForeground, experimentPreparationFailed;
    private String experimentPreparationError = "";
    private long prestartThermalSince = -1L;
    private boolean prestartThermalReady;
    private Size experimentPreviewSize;
    private boolean experimentPreviewPaused;
    private long previewRequestGeneration;
    private final java.util.concurrent.atomic.AtomicLong firstFrameAfterStart = new java.util.concurrent.atomic.AtomicLong();

    private enum StartBlocker { READY, FOREGROUND, PERMISSION, PREVIOUS_SESSION, MODELS, CAMERA, THERMAL, ERROR, INPUT_MODE, FOCUS }
    private void onTvSetupEvent(String name,org.json.JSONObject details){
        if (experimentSession.isRunning()) metricsCollector.recordEvent(name,0L,0L,details);
        ResearchExecutionConfig frozen=experimentSession.isRunning()?experimentSession.frozenExecutionConfig():null;
        if("tv_setup_calibration_invalidated".equals(name)&&frozen!=null&&frozen.tvBboxCrop()!=null){
            com.example.alpr_v1.experiment.ResearchSessionStore store=researchSessions.store();
            if(store!=null)store.closeAdmission("TV_BBOX_SETUP_INVALIDATED",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos());
            runOnUiThread(()->{if(experimentSession.isRunning())stopAnalysis(ExperimentSession.CompletionReason.ERROR);});
        }
    }
    private boolean tvBboxRequested(){return tvCameraPreparationEnabled()
            &&"TV_BBOX_CROP".equals(uiPreferences.getString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME"));}
    private String tvBboxStartReason(){
        return tvBboxStartReason(preparedExperimentStart==null?null:preparedExperimentStart.config,
                preparedExperimentStart==null?preparingCameraContext:preparedExperimentStart.cameraContext);
    }
    private String tvBboxStartReason(ResearchExecutionConfig prepared){
        return tvBboxStartReason(prepared,preparedExperimentStart==null?preparingCameraContext:preparedExperimentStart.cameraContext);
    }
    private String tvBboxStartReason(ResearchExecutionConfig prepared,PreparationCameraContext captured){
        if(uiPreferences.getBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,false))return "TV_BBOX_REQUIRES_FIXED_ZOOM · wyłącz AZ przed kalibracją";
        if(tvSetup!=null&&tvSetup.editing())return "Zakończ i zatwierdź kalibrację TV";
        CameraSetupReadiness current=currentCameraConfirmation();
        String cameraReason=current==null?missingCameraReason().description():current.reason.description();
        if(tvSetup!=null)tvSetup.revalidateForCamera(current);
        String geometry=tvSetup==null?"Brak aktualnej kalibracji TV":tvSetup.bboxPreparationStatus(current!=null&&current.ready()?current.applied:null,cameraReason);
        if(!"READY".equals(geometry))return geometry;
        if(current==null||!current.ready())return cameraReason;
        if(prepared!=null){
            try{prepared.requireSessionInputReady();}catch(RuntimeException error){return "Nieprawidłowa zamrożona konfiguracja TV";}
            if(!tvSetup.matchesPreparedGeometry(prepared.tvBboxCrop()))return "Kalibracja lub geometria TV zmieniła się · przygotuj START ponownie";
            boolean f6=uiPreferences.getBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,false);
            if(f6!=(prepared.final180PhotoRoi()!=null))return "Zmieniono profil F6 · przygotuj START ponownie";
            if(f6)try{
                // The worker already verified/rebound the approved source coordinates. The stored approval
                // intentionally retains its original TV digest; it is not this session's projection.
                if(captured==null||!captured.f6Enabled||captured.input==null||!captured.input.digest.equals(prepared.tvBboxCrop().digest)
                        ||!captured.photoRecord.equals(uiPreferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,""))
                        ||!captured.photoApprovalContext.equals(uiPreferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,""))
                        ||!new org.json.JSONObject(captured.tvApproval).getString("digest_sha256").equals(new org.json.JSONObject(tvSetup.approvedTvRecord()).getString("digest_sha256")))
                    return "Zmieniono zatwierdzenie pola fotografii F6 · przygotuj START ponownie";
            }catch(Exception invalid){return "F6: wskaż aktualny obszar fotografii";}
        }
        return "";
    }

    private void requestExperimentStart() {
        if(sessionWriteBusy()&&!experimentStartRequest.pending()){renderAnalysisControls();return;}
        PreparationCameraContext context=preparedExperimentStart!=null?preparedExperimentStart.cameraContext:preparingCameraContext;
        String inputReason=tvBboxRequested()?tvBboxStartReason():"";
        if(!inputReason.isEmpty()&&!cameraPreparationWait(context,currentCameraConfirmation(),inputReason)){
            AppLog.info(this,"RESEARCH_START","event=start_rejected_before_preparation reason="+inputReason);
            experimentStartRequest.cancel();renderAnalysisControls();return;
        }
        if (cameraStarted || experimentSession.isRunning()) return;
        if (tvSetup != null && tvSetup.editing()) return;
        if (preparedExperimentStart != null && (preparedExperimentStart.settingsRevision
                != uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0)
                || preparedExperimentStart.prepared.timer != experimentTimerConfig
                || preparedExperimentStart.prepared.thermal != experimentThermalConfig
                || preparedExperimentStart.store.state() != com.example.alpr_v1.experiment.ResearchSessionStore.State.PREPARED))
            invalidatePreparedExperiment();
        boolean newRequest=!experimentStartRequest.pending();
        long requestGeneration=experimentStartRequest.request();
        if(newRequest)AppLog.info(this,"RESEARCH_START","event=start_requested request_generation="+requestGeneration);
        if(newRequest)analysisStartRequestedElapsedNanos=android.os.SystemClock.elapsedRealtimeNanos();
        experimentPreviewPaused = false;
        experimentPreparationFailed = false;
        experimentPreparationError = "";
        renderAnalysisControls();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ensureCameraPermission(false);
            return;
        }
        refreshExperimentStartReadiness();
        requestExperimentReadinessRefresh();
    }

    private void cancelExperimentStart() {
        AppLog.info(this,"RESEARCH_START","event=start_cancelled request_generation="+experimentStartRequest.pendingGeneration());
        experimentStartRequest.cancel();
        waitingForThermalStart=false;
        experimentReadinessHandler.removeCallbacks(experimentReadinessRunnable);
        invalidatePreparedExperiment();
        cancelAnalysisPreparation();
        experimentPreviewPaused = true;
        if (cameraPreviewActive) stopCameraPreview(false);
        renderAnalysisControls();
        livePresentation.showUserStatus(LivePresentationController.State.SEARCHING,
                "Uruchomienie anulowane", "START ponowi przygotowanie i uruchomienie");
    }

    private void requestExperimentReadinessRefresh() {
        experimentReadinessHandler.removeCallbacks(experimentReadinessRunnable);
        if (analysisScreenForeground && !cameraStarted && (experimentModeEnabled||experimentStartRequest.pending()))
            experimentReadinessHandler.post(experimentReadinessRunnable);
    }

    private void refreshExperimentStartReadiness() {
        if (!analysisScreenForeground || cameraStarted) return;
        if (!experimentModeEnabled) {
            if (!experimentStartRequest.pending() || experimentStartRequest.waitingForPermission()) return;
            if (researchSessions.finalizing()) {
                livePresentation.showUserStatus(LivePresentationController.State.PREVIEW,"Czekam na zapis poprzedniej sesji","Stop anuluje uruchomienie");
            } else if (!analysisStartGate.preparing()) startCamera(true);
            renderAnalysisControls();
            return;
        }
        ensureExperimentPrepared();
        updatePrestartThermalReadiness();
        if (experimentStartRequest.pending() && !experimentStartRequest.waitingForPermission() && isExperimentStartReady()) {
            PreparedAnalysisStart ready = preparedExperimentStart;
            preparedExperimentStart = null;
            experimentReadinessHandler.removeCallbacks(experimentReadinessRunnable);
            startPreparedCamera(true, ready);
            return;
        }
        renderAnalysisControls();
    }

    private void updatePrestartThermalReadiness() {
        if (!experimentThermalConfig.enabled()) { prestartThermalReady = true; return; }
        long now = android.os.SystemClock.elapsedRealtime();
        if (!experimentThermalConfig.accepts(latestThermalSnapshot)
                || now < latestThermalSnapshot.capturedElapsedMillis
                || now - latestThermalSnapshot.capturedElapsedMillis > Math.max(2000L, 2 * THERMAL_POLL_MS)) {
            prestartThermalSince = -1L;
            prestartThermalReady = false;
            return;
        }
        if (prestartThermalSince < 0L) prestartThermalSince = now;
        prestartThermalReady = now - prestartThermalSince >= experimentThermalConfig.stabilizationMillis();
    }

    private void renderExperimentStartStatus(StartBlocker blocker) {
        if (!analysisScreenForeground || cameraStarted) return;
        if(tvSetup!=null&&tvSetup.editing()&&!analysisStartButton.isEnabled()&&blocker!=StartBlocker.ERROR){
            livePresentation.showUserStatus(LivePresentationController.State.PREVIEW,"Zakończ konfigurację TV","START zablokowany podczas edycji TV");
            return;
        }
        String status, hint;
        switch (blocker) {
            case INPUT_MODE:
                hint=tvBboxStartReason();
                PreparationCameraContext context=preparedExperimentStart!=null?preparedExperimentStart.cameraContext:preparingCameraContext;
                boolean cameraWait=cameraPreparationWait(context,currentCameraConfirmation(),hint);
                com.example.alpr_v1.camera.CameraSetupReadiness.Reason inputCameraReason=com.example.alpr_v1.ui.CameraOperatorPresentation.reason(hint);
                if(cameraWait||inputCameraReason!=null){
                    com.example.alpr_v1.ui.CameraOperatorPresentation cameraText=com.example.alpr_v1.ui.CameraOperatorPresentation.from(this,
                            inputCameraReason,cameraPreviewActive,experimentPreviewPaused,experimentStartRequest.pending());
                    status=cameraText.primary;hint=cameraText.hint;
                }else{status=getString(R.string.camera_operator_tv_input);hint=com.example.alpr_v1.ui.CameraOperatorPresentation.readableDetail(this,hint);}
                break;
            case READY:
                status=!analysisStartButton.isEnabled()?"START zablokowany":experimentStartRequest.pending()?"Uruchamianie eksperymentu":tvBboxRequested()?"TV BBOX · próba nieoficjalna":"Gotowe do START";
                hint=!analysisStartButton.isEnabled()?"Gotowość zmieniła się · sprawdź bieżący stan": experimentTimerConfig.enabled()?"Timer i analiza ruszą po kliknięciu":"Analiza ruszy po kliknięciu";
                break;
            case PERMISSION: status = "Potrzebny dostęp do kamery"; hint = "START otworzy obsługę uprawnienia"; break;
            case PREVIOUS_SESSION: status = "Trwa przygotowanie lub zapis poprzedniej sesji"; hint = researchSessions.operationDescription(); break;
            case FOCUS: status=cameraController.focusDescription();hint="Anuluj żądanie AF lub ustaw ostrość ponownie przed START";break;
            case MODELS:
                status=analysisStartGate.preparing()?"Przygotowanie modeli i zapisu…":"START przygotuje modele i zapis";
                hint="";break;
            case CAMERA:
                CameraSetupReadiness observedCamera=currentCameraConfirmation();
                com.example.alpr_v1.ui.CameraOperatorPresentation cameraText=com.example.alpr_v1.ui.CameraOperatorPresentation.from(this,
                        observedCamera==null?com.example.alpr_v1.ui.CameraOperatorPresentation.reason(cameraPreparationNotice):observedCamera.reason,
                        cameraPreviewActive,experimentPreviewPaused,experimentStartRequest.pending());
                status=cameraText.primary;hint=cameraText.hint;break;
            case THERMAL: status = "Czekam na warunki termiczne"; hint = "Sprawdzam temperaturę i jej stabilizację"; break;
            case ERROR: status = "Nie przygotowano eksperymentu";
                hint = com.example.alpr_v1.ui.CalibrationOperatorText.preparationMessage(this,experimentPreparationError) + " · START ponowi próbę"; break;
            default: return;
        }
        if (experimentStartRequest.pending()) {
            hint = (blocker==StartBlocker.READY?"":hint.isEmpty()?"":hint+" · ")+"Stop anuluje uruchomienie";
        }
        livePresentation.showUserStatus(blocker == StartBlocker.ERROR ? LivePresentationController.State.ERROR
                : LivePresentationController.State.PREVIEW, status, hint);
    }

    private void ensureExperimentPrepared() {
        if (!analysisScreenForeground || !experimentModeEnabled || cameraStarted || experimentSession.isRunning()
                || experimentPreviewPaused) return;
        if (preparedExperimentStart != null && (preparedExperimentStart.settingsRevision
                != uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0)
                || preparedExperimentStart.prepared.timer != experimentTimerConfig
                || preparedExperimentStart.prepared.thermal != experimentThermalConfig
                || preparedExperimentStart.store.state() != com.example.alpr_v1.experiment.ResearchSessionStore.State.PREPARED))
            invalidatePreparedExperiment();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        Size requested = chooseAnalysisSize();
        if (cameraPreviewActive && !requested.equals(experimentPreviewSize)) stopCameraPreview(false);
        if (!cameraPreviewActive) startCameraPreview();
        if(tvSetup!=null&&tvSetup.editing())return;
        CameraSetupReadiness current=tvCameraPreparationEnabled()?currentCameraConfirmation():null;
        PreparationCameraContext existing=preparedExperimentStart!=null?preparedExperimentStart.cameraContext:preparingCameraContext;
        if(existing!=null){
            CameraSetupReadiness.Reason changed=preparationCameraFailure(existing,current,false);
            if(changed!=CameraSetupReadiness.Reason.READY){
                cameraPreparationNotice=changed.description();logCameraSetup("preparation_invalidated",changed,current,true);invalidatePreparedExperiment();
            }
        }
        String inputReason=tvBboxRequested()?tvBboxStartReason():"";
        if(!inputReason.isEmpty()){
            boolean transientCamera=cameraPreparationWait(existing,current,inputReason);
            if(transientCamera){cameraPreparationNotice=current.reason.description();}
            else if(preparedExperimentStart!=null||analysisStartGate.preparing()||experimentStartRequest.pending())invalidatePreparedExperiment();
            return;
        }
        if(tvCameraPreparationEnabled()&&(current==null||!current.ready())){
            if(!cameraPreparationWait(existing,current,current==null?"":current.reason.description()))experimentStartRequest.cancel();
            cameraPreparationNotice=current==null?missingCameraReason().description():current.reason.description();return;
        }
        cameraPreparationNotice="";
        if(focusBlocksF6Start())return;
        if(preparedExperimentStart!=null&&tvCameraPreparationEnabled()
                &&(preparedExperimentStart.config==null||preparedExperimentStart.config.cameraSetup==null
                ||!preparedExperimentStart.config.cameraSetup.same(current.applied)))invalidatePreparedExperiment();
        // Preview/readiness, history and onResume are passive. Only an explicit START
        // may allocate a durable session; finishing the previous one is not a request.
        if (experimentStartRequest.pending() && !experimentStartRequest.waitingForPermission()
                && preparedExperimentStart == null && !analysisStartGate.preparing()
                && !experimentPreparationFailed && researchSessions.ready()) prepareCameraStart(false);
    }

    private boolean isExperimentStartReady() {
        return experimentStartBlocker() == StartBlocker.READY;
    }

    private StartBlocker experimentStartBlocker() {
        if (!analysisScreenForeground) return StartBlocker.FOREGROUND;
        if (experimentStartRequest.waitingForPermission()
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            return StartBlocker.PERMISSION;
        if (sessionWriteBusy()&&!analysisStartGate.preparing()) return StartBlocker.PREVIOUS_SESSION;
        if(focusBlocksF6Start())return StartBlocker.FOCUS;
        if(tvBboxRequested()&&!tvBboxStartReason().isEmpty())return StartBlocker.INPUT_MODE;
        if (experimentPreparationFailed) return StartBlocker.ERROR;
        if (experimentPreviewPaused || !cameraPreviewActive || cameraController == null) return StartBlocker.CAMERA;
        if(tvCameraPreparationEnabled()){
            CameraSetupReadiness current=currentCameraConfirmation();
            if(current==null||!current.ready()){
                cameraPreparationNotice=current==null?missingCameraReason().description():current.reason.description();return StartBlocker.CAMERA;
            }
        }
        PreparedAnalysisStart ready = preparedExperimentStart;
        if (ready == null) return !analysisStartGate.preparing() && !researchSessions.ready()
                ? StartBlocker.PREVIOUS_SESSION : StartBlocker.MODELS;
        if (ready.settingsRevision != uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0)
                || ready.prepared.timer != experimentTimerConfig || ready.prepared.thermal != experimentThermalConfig
                || ready.store.state() != com.example.alpr_v1.experiment.ResearchSessionStore.State.PREPARED) return StartBlocker.MODELS;
        Size size = chooseAnalysisSize();
        boolean highResolution = cameraResolutionCatalog != null && cameraResolutionCatalog.isHighResolution(size);
        if (!cameraController.isStreamReady(size, highResolution)) return StartBlocker.CAMERA;
        if(!cameraSetupMatches(ready.config))return StartBlocker.CAMERA;
        return !experimentThermalConfig.enabled() || prestartThermalReady ? StartBlocker.READY : StartBlocker.THERMAL;
    }

    private void invalidatePreparedExperiment() {
        invalidatePreparedExperiment(true);
    }

    private void invalidatePreparedExperiment(boolean cancelStartRequest) {
        if (cancelStartRequest) experimentStartRequest.cancel();
        if (analysisStartGate.preparing()) analysisStartGate.cancel();
        preparingCameraContext=null;
        PreparedAnalysisStart ready = preparedExperimentStart;
        preparedExperimentStart = null;
        experimentPreparationFailed = false;
        experimentPreparationError = "";
        prestartThermalSince = -1L;
        prestartThermalReady = false;
        if (ready != null) discardPreparedStart(ready);
    }

    private static final class PreparedAnalysisStart {
        final ResearchExecutionConfig config;
        final ExperimentSession.Prepared prepared;
        final com.example.alpr_v1.experiment.ResearchSessionStore store;
        final int settingsRevision;
        final PreparationCameraContext cameraContext;
        TimerConfig timer=TimerConfig.disabled();
        long startWall, startElapsed, startMonotonic;
        PreparedAnalysisStart(ResearchExecutionConfig config, ExperimentSession.Prepared prepared,
                com.example.alpr_v1.experiment.ResearchSessionStore store, int revision,PreparationCameraContext cameraContext) {
            this.config = config; this.prepared = prepared; this.store = store;
            settingsRevision = revision;
            this.cameraContext=cameraContext;
        }
    }

    private void startCamera(boolean beginNewMeasurement) {
        if(ordinaryDynamicResourcesEnabled()) {
            if(ordinaryMemoryPaused && !beginNewMeasurement)return;
            if(ordinaryMemoryCritical()) { pauseOrdinaryForMemory(); return; }
            if(beginNewMeasurement)ordinaryMemoryPaused=false;
        }
        if (cameraStarted) return;
        if (beginNewMeasurement && experimentModeEnabled) { requestExperimentStart(); return; }
        if (analysisStartGate.preparing()) return;
        if (!beginNewMeasurement) {
            if (!researchSessions.timerState().running || !metricsCollector.isMeasurementSessionActive()) return;
            ResearchExecutionConfig frozen=experimentSession.isRunning()?experimentSession.frozenExecutionConfig():null;
            if(experimentSession.isRunning()&&!experimentSession.admitsWork(android.os.SystemClock.elapsedRealtimeNanos()))return;
            startPreparedCamera(false,new PreparedAnalysisStart(frozen,null,null,knownSettingsRevision,null));
            return;
        }
        if (!experimentStartRequest.pending()) {
            experimentStartRequest.request();
            analysisStartRequestedElapsedNanos=android.os.SystemClock.elapsedRealtimeNanos();
        }
        if(researchSessions.finalizing()) {renderAnalysisControls();requestExperimentReadinessRefresh();return;}
        prepareCameraStart(true);
    }

    private void prepareCameraStart(boolean startWhenReady) {
        final long requestGeneration=experimentStartRequest.pendingGeneration();
        if(requestGeneration<0L || analysisStartGate.preparing())return;
        if (experimentModeEnabled && !researchSessions.ready()) {
            Toast.makeText(this, "Trwa przygotowanie lub zapis poprzedniej sesji", Toast.LENGTH_LONG).show();
            return;
        }
        final PreparationCameraContext capturedCamera;
        try{capturedCamera=capturePreparationCameraContext();}
        catch(CameraPreparationWait wait){
            if(wait.reason!=CameraSetupReadiness.Reason.NO_FRESH_CAMERA_FRAME)experimentStartRequest.cancel();
            experimentPreparationFailed=false;experimentPreparationError="";cameraPreparationNotice=wait.getMessage();
            logCameraSetup("preparation_wait",wait.reason,null,true);renderAnalysisControls();return;
        }
        final long ticket = analysisStartGate.begin();
        preparingCameraContext=capturedCamera;
        final int revision = uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0);
        final boolean research = experimentModeEnabled;
        final TimerConfig timer = experimentTimerConfig;
        final ThermalConfig thermal = experimentThermalConfig;
        final double maximumZoom = cameraController == null ? 1.0 : cameraController.maximumZoomRatio();
        renderAnalysisControls();
        livePresentation.showUserStatus(LivePresentationController.State.SEARCHING,
                "Przygotowanie modeli i zapisu…", "Odliczanie rozpocznie się po przygotowaniu");
        pipelineInferenceExecutor.execute(() -> {
            com.example.alpr_v1.experiment.ResearchSessionStore store = null;
            try {
                if (!analysisStartGate.isCurrent(ticket)) return;
                if (!experimentStartRequest.isCurrent(requestGeneration))
                    throw new java.util.concurrent.CancellationException("START request no longer current");
                long began = android.os.SystemClock.elapsedRealtime();
                ResearchExecutionConfig config = research ? buildResearchExecutionConfig(capturedCamera) : null;
                ExperimentSession.Prepared prepared = null;
                if (research) {
                    if(!analysisStartGate.isCurrent(ticket)||!experimentStartRequest.isCurrent(requestGeneration))
                        throw new java.util.concurrent.CancellationException("START cancelled before session allocation");
                    String defaultSeries = "ROI-" + new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
                    ExperimentIdentity identity = new ExperimentIdentity(
                            uiPreferences.getString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID, defaultSeries),
                            uiPreferences.getString(SettingsActivity.KEY_EXPERIMENT_SCENARIO_ID, "live_camera"),
                            Math.max(1, uiPreferences.getInt(SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX, 1)),
                            uiPreferences.getString(SettingsActivity.KEY_EXPERIMENT_NOTES, ""), config.autoZoomEnabled, maximumZoom);
                    prepared = experimentSession.prepare(config.experimentType, config.variant, timer, thermal, identity, config);
                    store = researchSessions.prepare(prepared);
                    AppLog.info(this,"RESEARCH_START","event=session_prepared request_generation="+requestGeneration
                            +" preparation_ticket="+ticket+" session_id="+prepared.sessionId);
                }
                pipeline.setResearchExecutionConfig(config);
                pipeline.setResearchCollector(store);
                pipeline.setSceneHandlingMode(config == null ? effectiveSceneHandlingMode() : config.sceneHandlingMode);
                pipeline.prepareModels();
                if (!requiredRecognitionModelsAvailable()) throw new IllegalStateException("Brak wymaganych modeli MT/MZ");
                if (!analysisStartGate.isCurrent(ticket)||!experimentStartRequest.isCurrent(requestGeneration))
                    throw new java.util.concurrent.CancellationException("START cancelled during preparation");
                if (revision != uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0))
                    throw new IllegalStateException("Przygotowanie anulowane lub ustawienia zmienione");
                pipeline.finishCameraTransform();
                pipeline.resetTracking();
                if (store != null) store.awaitPreparationWrites();
                PreparedAnalysisStart ready = new PreparedAnalysisStart(config, prepared, store, revision,capturedCamera);
                ready.timer=timer;
                android.util.Log.i("ALPR_PIPELINE_START", "preparation_ready_ms="
                        + (android.os.SystemClock.elapsedRealtime() - began));
                runOnUiThread(() -> {
                    if (!analysisStartGate.isCurrent(ticket) || !experimentStartRequest.isCurrent(requestGeneration)
                            || isFinishing() || isDestroyed()
                            || revision != uiPreferences.getInt(SettingsActivity.KEY_REVISION, 0)) {
                        discardPreparedStart(ready);
                        if (analysisStartGate.complete(ticket)){preparingCameraContext=null;renderAnalysisControls();}
                        return;
                    }
                    analysisStartGate.complete(ticket);
                    preparingCameraContext=null;
                    if(research&&tvCameraPreparationEnabled()){
                        CameraSetupReadiness current=currentCameraConfirmation();
                        CameraSetupReadiness.Reason failure=preparedCameraFailure(ready,current);
                        if(failure!=CameraSetupReadiness.Reason.READY){rejectPreparedCamera(ready,failure,current);return;}
                    }
                    if (startWhenReady) {
                        if(experimentStartRequest.pending()&&!experimentStartRequest.waitingForPermission())startPreparedCamera(true, ready);
                        else discardPreparedStart(ready);
                    }
                    else {
                        preparedExperimentStart = ready;
                        livePresentation.showUserStatus(LivePresentationController.State.PREVIEW,
                                "Modele gotowe", "START uruchomi timer i analizę");
                    }
                    if (research) {
                        refreshExperimentStartReadiness();
                        requestExperimentReadinessRefresh();
                    } else renderAnalysisControls();
                });
            } catch (java.util.concurrent.CancellationException cancelled) {
                if(store!=null){
                    store.abortPreparation("start_cancelled_before_camera");
                    pipeline.setResearchCollector(null);
                    pipeline.setResearchExecutionConfig(null);
                }
                runOnUiThread(()->{
                    if(analysisStartGate.complete(ticket)){
                        preparingCameraContext=null;renderAnalysisControls();
                    }
                });
            } catch (Exception | LinkageError error) {
                if(capturedCamera!=null&&capturedCamera.f6Enabled)AppLog.error(this,"F6_SESSION_PREPARATION",
                        "START preparation failed; captured TV="+(capturedCamera.input==null?"MISSING":capturedCamera.input.digest),error);
                if (store != null) store.abortPreparation("start_preparation_failed: " + error.getClass().getSimpleName());
                pipeline.setResearchCollector(null);
                pipeline.setResearchExecutionConfig(null);
                runOnUiThread(() -> {
                    if (!analysisStartGate.complete(ticket) || isDestroyed()) return;
                    preparingCameraContext=null;
                    if(!experimentStartRequest.isCurrent(requestGeneration)){renderAnalysisControls();return;}
                    boolean cameraWait=error instanceof CameraPreparationWait;
                    experimentPreparationFailed = !startWhenReady&&!cameraWait;
                    experimentPreparationError = cameraWait?"":error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    if(cameraWait)cameraPreparationNotice=error.getMessage();
                    experimentStartRequest.cancel();
                    renderAnalysisControls();
                    if(!cameraWait)livePresentation.showState(LivePresentationController.State.ERROR, "Nie przygotowano analizy");
                    Toast.makeText(this, "START zablokowany: " + com.example.alpr_v1.ui.CalibrationOperatorText.preparationError(this,error.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void discardPreparedStart(PreparedAnalysisStart ready) {
        Runnable discard = () -> {
            if(ready.store!=null)AppLog.info(this,"RESEARCH_START","event=prepared_session_discarded session_id="+ready.store.sessionId()
                    +" reason=start_cancelled_before_camera");
            if (ready.store != null) ready.store.abortPreparation("start_cancelled_before_camera");
            if (pipeline.researchExecutionConfig() == ready.config) {
                pipeline.setResearchCollector(null);
                pipeline.setResearchExecutionConfig(null);
            }
        };
        // An Activity can be destroyed after preparation posted its completion callback.
        if (!pipelineInferenceExecutor.isShutdown()) pipelineInferenceExecutor.execute(discard);
        else java.util.concurrent.CompletableFuture.runAsync(discard);
    }

    private void cancelAnalysisPreparation() {
        if (!analysisStartGate.preparing()) return;
        analysisStartGate.cancel();
        preparingCameraContext=null;
        renderAnalysisControls();
        livePresentation.showUserStatus(LivePresentationController.State.SEARCHING,
                "Przygotowanie anulowane", "");
    }

    private void startPreparedCamera(boolean beginNewMeasurement, PreparedAnalysisStart ready) {
        if (cameraStarted) return;
        if(beginNewMeasurement&&ready.config!=null&&"roi_budget".equals(ready.config.experimentType)){
            CameraSetupReadiness current=currentCameraConfirmation();
            CameraSetupReadiness.Reason failure=preparedCameraFailure(ready,current);
            if(failure!=CameraSetupReadiness.Reason.READY){rejectPreparedCamera(ready,failure,current);return;}
        }
        if(beginNewMeasurement&&ready.config!=null&&ready.config.tvBboxCrop()!=null&&!tvBboxStartReason(ready.config,ready.cameraContext).isEmpty()){
            CameraSetupReadiness current=currentCameraConfirmation();
            if(transientFrameWait(ready.cameraContext,current))rejectPreparedCamera(ready,current.reason,current);
            else {discardPreparedStart(ready);experimentStartRequest.cancel();requestExperimentReadinessRefresh();renderAnalysisControls();}
            return;
        }
        if(!beginNewMeasurement&&ready.config!=null&&ready.config.cameraSetup!=null)
            cameraController.apply(ready.config.cameraSetup.selection,ready.config.cameraSetup.zoomRatio);
        if(ready.config!=null&&ready.config.cameraSetup!=null)currentCameraZoomRatio=ready.config.cameraSetup.zoomRatio;
        if (beginNewMeasurement) {
            ready.startWall = System.currentTimeMillis();
            ready.startElapsed = android.os.SystemClock.elapsedRealtimeNanos();
            ready.startMonotonic = System.nanoTime();
            firstFrameAfterStart.set(ready.startElapsed);
            android.util.Log.i("ALPR_PIPELINE_START", "start_clicked elapsed_ns=" + ready.startElapsed);
        }
        if (beginNewMeasurement) captureGalleryState.recentReads.clear();
        liveCameraFrameRate.reset();
        lastRenderedContinuityState =
                com.example.alpr_v1.continuity.SceneContinuityState.STABLE;
        if (beginNewMeasurement) {
            autoZoomHandler.removeCallbacksAndMessages(null);
            autoZoomController.resetSession();
            clearAutoZoomRecognitionMemory();
            cameraTransformInProgress = false;
            currentCameraZoomRatio = ready.config!=null&&ready.config.cameraSetup!=null?ready.config.cameraSetup.zoomRatio:1f;
        }
        if (beginNewMeasurement) {
            if (!beginAnalysisMeasurement(ready)) return;
            experimentStartRequest.consumeIfReady(true);
            if(analysisStartRequestedElapsedNanos>0L)android.util.Log.i("ALPR_PIPELINE_START",
                    "request_to_measurement_ms="+(ready.startElapsed-analysisStartRequestedElapsedNanos)/1_000_000L);
        }
        cameraPreviewActive = false;
        previewView.setVisibility(View.VISIBLE);

        /*
         * Każde ręczne uruchomienie analizy rozpoczyna się
         * bez stanu trackingowego poprzedniego przebiegu.
         */
        if (!beginNewMeasurement) deferPipelineControl(AlprPipeline::resetTracking);
        restartScanAcquisitionForConfiguration(
                true,
                beginNewMeasurement ? "analysis_start" : "camera_restart"
        );
        targetStateMachine.reset();

        overlayTracker.reset();

        previewPlateTracker.reset();
        previewVehicleTracker.reset();
        resetGlobalPreviewMotionState();

        latestDiagnosticOverlayItems =
                java.util.Collections.emptyList();

        latestPipelinePlateItems =
                java.util.Collections.emptyList();
        latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
        latestPreviewMotionItems = java.util.Collections.emptyList();
        latestPipelinePlateEntityId = 0L;
        plateOverlayFreshness.reset();

        previewSceneAnchorGuard.reset();

        previewSceneAnchorPending =
                false;

        lastCaptureByTrack.clear();

        overlayView.clearPlateItems();
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(
                java.util.Collections.emptyList()
        );
        overlayView.hardResetForNewScene(currentPreviewSourceFrameStamp().continuityStamp());
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.setAnalysisViewportEnabled(true);

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


        autoZoomOperationGeneration++;
        autoZoomPreferenceRevision++;
        autoZoomReturnInProgress = false;
        cameraStarted = true;
        normalCollectionReady = false;
        final long controlsGeneration = ++analysisControlGeneration;
        final java.util.concurrent.atomic.AtomicBoolean collectionFrameSeen =
                new java.util.concurrent.atomic.AtomicBoolean();

        startPreviewSceneMonitor();

        renderAnalysisControls();

        if (cameraMotionMonitor != null) {
            cameraMotionMonitor.start();
        }

        livePresentation.clearResult();
        if (!syncMissingModelsStatus()) {
            showSearchingStatusForCurrentPipeline();
        }

        recordInfo(
                beginNewMeasurement
                        ? "Uruchamianie kamery i pipeline'u"
                        : "Restart kamery w aktywnej analizie"
        );
        Size requestedCameraSize =
                chooseAnalysisSize();

        boolean allowHighResolution =
                !ordinaryDynamicResourcesEnabled() && cameraResolutionCatalog != null
                        && cameraResolutionCatalog
                        .isHighResolution(
                                requestedCameraSize
                        );
        lastDirectLumaFrameNanos = 0L;
        lastDirectLumaSourceTimestampNanos = 0L;
        pendingDirectLumaFrame.set(null);
        directLumaTrackingUnavailable = false;
        AlprPipeline startingPipeline = pipeline;
        if (!beginNewMeasurement) pipelineInferenceExecutor.execute(() -> {
            try {
                startingPipeline.prepareModels();
            } catch (RuntimeException error) {
                // The first measured frame uses the regular runtime error reporting path.
                android.util.Log.w("ALPR_PIPELINE_START", "Model preparation failed", error);
            }
        });
        cameraController.start(
                (image, cameraFrameStamp) -> {
                    if(!cameraAnalysisAdmitted(controlsGeneration))return;
                    if (collectionFrameSeen.compareAndSet(false, true)) {
                        runOnUiThread(() -> onNormalAnalysisFrame(controlsGeneration));
                    }
                    long clicked = firstFrameAfterStart.getAndSet(0L);
                    if (BuildConfig.DEBUG && clicked > 0L) android.util.Log.d("ALPR_PIPELINE_START",
                            "click_to_first_frame_ms=" + (android.os.SystemClock.elapsedRealtimeNanos() - clicked) / 1_000_000L);

                    /*
                     * Zapamiętujemy scenę, do której należała klatka
                     * w chwili rozpoczęcia ciężkiej inferencji.
                     */
                    final long sceneGenerationAtStart =
                            uiSceneGeneration.get();
                    final long transformGenerationAtStart =
                            uiCameraTransformGeneration.get();
                    final long presentationGenerationAtStart =
                            previewPresentationBarrier.capture();

                    if (!pipeline.researchWorkAdmitted()) return;
                    final com.example.alpr_v1.experiment.ResearchSessionStore sceneEvidenceStore=researchSessions.store();
                    final com.example.alpr_v1.experiment.F5aEvidence sceneEvidence=sceneEvidenceStore==null ? null : sceneEvidenceStore.f5aEvidence();
                    final SourceFrameStamp sceneEvidenceStamp=sceneEvidence==null ? null : pipeline.sceneEvidenceSourceStamp(cameraFrameStamp);
                    final String sceneEvidenceTicket=sceneEvidence==null ? "" : sceneEvidence.ticket(sceneEvidenceStamp.continuityStamp());
                    final boolean inferenceSlot=!pipeline.isStaticIdle() && pipelineFrameInFlight.compareAndSet(false,true);
                    if(!inferenceSlot && sceneEvidenceTicket.isEmpty())return;
                    final SourceFrameStamp sourceFrameStamp =
                            cameraSourceFrameStamp(
                                    cameraFrameStamp.sourceSequence,
                                    cameraFrameStamp.sourceTimestampNanos,
                                    cameraFrameStamp.domain
                            );
                    final long sourceTimestampNanos =
                            sourceFrameStamp.sourceTimestampNanos;
                    final LumaFrame candidateLuma=latestCameraLuma;
                    final LumaFrame inferenceSourceLuma=ordinaryDynamicResourcesEnabled()&&candidateLuma!=null
                            &&candidateLuma.sourceSequence==sourceFrameStamp.sourceSequence
                            &&candidateLuma.timestampNanos==sourceTimestampNanos?candidateLuma:null;
                    final CameraImageConverter.Result conversion;
                    final com.example.alpr_v1.camera.BitmapMemoryBudget.Lease sourceLease;
                    if(ordinaryDynamicResourcesEnabled()) {
                        dynamicResourcePolicy();
                        sourceLease=ordinaryBitmapBudget.acquire(12L*image.getWidth()*image.getHeight());
                        if(sourceLease==null) { pipelineFrameInFlight.set(false); return; }
                    } else sourceLease=null;
                    try {
                        conversion = CameraImageConverter.convert(image);
                    } catch (RuntimeException | OutOfMemoryError conversionError) {
                        if(sourceLease!=null)sourceLease.close();
                        if(conversionError instanceof OutOfMemoryError && ordinaryDynamicResourcesEnabled()) {
                            pipelineFrameInFlight.set(false);runOnUiThread(this::pauseOrdinaryForMemory);return;
                        }
                        if(sceneEvidence!=null)sceneEvidence.captureMissing(sceneEvidenceTicket,"source_frame_unavailable");
                        ResearchExecutionConfig failedInput=pipeline.researchExecutionConfig();
                        if(failedInput!=null&&failedInput.tvBboxCrop()!=null&&sceneEvidenceStore!=null&&sceneEvidenceStore.tvBboxEvidence()!=null){
                            sceneEvidenceStore.tvBboxEvidence().rejected(null,sourceFrameStamp.continuityStamp(),"source_frame_unavailable")
                                    .complete("SOURCE_UNAVAILABLE","source_frame_unavailable");
                            sceneEvidenceStore.closeAdmission("TV_BBOX_SOURCE_UNAVAILABLE",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos());
                        }
                        if(!inferenceSlot)return;
                        pipelineFrameInFlight.set(false);
                        throw conversionError;
                    }
                    final Bitmap pipelineBitmap = conversion.bitmap;
                    if(!sceneEvidenceTicket.isEmpty())sceneEvidenceStore.captureSceneEvidence(
                            sceneEvidenceTicket,sceneEvidenceStamp.continuityStamp(),pipelineBitmap);
                    if(!inferenceSlot) {
                        if(pipeline.isStaticIdle() && sceneEvidenceTicket.equals(sceneEvidence.sceneFor(sceneEvidenceStamp.continuityStamp())))
                            sceneEvidence.observation(sceneEvidenceTicket,null,"static_idle");
                        ResearchExecutionConfig evidenceInput=pipeline.researchExecutionConfig();
                        if(evidenceInput!=null&&evidenceInput.tvBboxCrop()!=null&&sceneEvidenceStore!=null&&sceneEvidenceStore.tvBboxEvidence()!=null){
                            try(com.example.alpr_v1.setup.TvBboxCrop.Frame input=tvSetup.openInput(evidenceInput.tvBboxCrop(),conversion)){
                                sceneEvidenceStore.tvBboxEvidence().capture(input,sceneEvidenceStamp.continuityStamp()).complete("SCENE_EVIDENCE_ONLY","");
                            }catch(RuntimeException invalid){handleTvBboxFrameFailure(pipelineBitmap,sceneEvidenceStamp.continuityStamp(),invalid);}
                        }
                        if(!pipelineBitmap.isRecycled())pipelineBitmap.recycle();
                        return;
                    }

                    try {
                        pipelineInferenceExecutor.execute(() -> {
                            com.example.alpr_v1.setup.TvBboxCrop.Frame tvInput=null;
                            try {
                    if (!cameraAnalysisAdmitted(controlsGeneration) || !pipeline.researchWorkAdmitted()) return;
                    if (experimentSession.isRunning() && researchSessions.store() != null
                            && !researchSessions.store().accepting()) return;

                    long observationNanos =
                            System.nanoTime();


                    pipeline.setCameraMotionEvidence(
                            cameraMotionMonitor.isGyroscopeAvailable(),
                            cameraMotionMonitor.isMoving(),
                            cameraMotionMonitor.isRapidMotion(),
                            cameraMotionMonitor.magnitude()
                    );
                    pipeline.setCurrentCameraZoomRatio(currentCameraZoomRatio);
                    if(ordinaryDynamicResourcesEnabled())pipeline.setDynamicBitmapBudget(ordinaryBitmapBudget);


                    ResearchExecutionConfig inputConfig=pipeline.researchExecutionConfig();
                    if(inputConfig!=null&&inputConfig.tvBboxCrop()!=null){
                        try{tvInput=tvSetup.openInput(inputConfig.tvBboxCrop(),conversion);}
                        catch(RuntimeException invalid){handleTvBboxFrameFailure(pipelineBitmap,sourceFrameStamp.continuityStamp(),invalid);return;}
                    }
                    PipelineResult result =
                            pipeline.processBitmap(
                                    pipelineBitmap,
                                    sourceFrameStamp,
                                    conversion.toBitmapNanos,
                                    conversion.rotationNanos,
                                    conversion.rotationDegrees,
                                    (overlayItems, sourceWidth, sourceHeight, sourceStamp) -> {
                                        if (pipeline == null
                                                || !previewPresentationBarrier.permits(
                                                        presentationGenerationAtStart
                                                )
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
                                        final boolean zoomedMtStage =
                                                autoZoomController.state()
                                                == AutoZoomController.State.ZOOMED_RETRY;
                                        runOnUiThread(() -> {
                                            if (!cameraAnalysisAdmitted(controlsGeneration)
                                                    || !previewPresentationBarrier.permits(
                                                            presentationGenerationAtStart
                                                    )
                                                    || pipeline == null
                                                    || !pipeline.isCurrentContinuityStamp(
                                                            sourceStamp
                                                    )
                                                    || sceneGenerationAtStart
                                                    != uiSceneGeneration.get()
                                                    || transformGenerationAtStart
                                                    != uiCameraTransformGeneration.get()
                                                    || cameraTransformInProgress
                                                    || zoomedMtStage
                                                    && autoZoomController.state()
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
                                            presentIntermediateGeometryStage(overlayItems,sourceWidth,sourceHeight,
                                                    sourceStamp,inferenceSourceLuma,zoomedMtStage);
                                        });
                                    },
                                    (score, changedFraction) -> runOnUiThread(() -> {
                                        if (!cameraAnalysisAdmitted(controlsGeneration)
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
                                    }),
                                    observation -> publishGalleryObservation(
                                            observation, sceneGenerationAtStart,
                                            transformGenerationAtStart, presentationGenerationAtStart),tvInput
                            );


                    if (result == null) {
                        return;
                    }

                    if (!PipelineResultDispatchGate.isCurrent(pipeline, result)) {
                        recordStaleFinalResult("after_pipeline_return", result);
                        result.close();
                        return;
                    }


                    /*
                     * Podczas MP/MT/MZ użytkownik mógł już skierować
                     * kamerę na zupełnie inną scenę.
                     *
                     * Takiego wyniku nie wolno ani pokazać,
                     * ani zapisać jako crop.
                     */
                    if (!cameraAnalysisAdmitted(controlsGeneration)
                            || !previewPresentationBarrier.permits(
                                    presentationGenerationAtStart
                            )
                            || sceneGenerationAtStart
                            != uiSceneGeneration.get()
                            || transformGenerationAtStart
                            != uiCameraTransformGeneration.get()) {

                        result.close();

                        return;
                    }

                    if (!PipelineResultDispatchGate.isCurrent(pipeline, result)) {
                        recordStaleFinalResult("before_recognition_telemetry", result);
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
                                    try {
                                        /*
                                         * Generacja mogła zmienić się również
                                         * pomiędzy post() a wykonaniem callbacku UI.
                                         */
                                        if (!cameraAnalysisAdmitted(controlsGeneration)
                                                || !previewPresentationBarrier.permits(
                                                        presentationGenerationAtStart
                                                )
                                                || sceneGenerationAtStart
                                                != uiSceneGeneration.get()
                                                || transformGenerationAtStart
                                                != uiCameraTransformGeneration.get()) {
                                            return;
                                        }
                                        if (!PipelineResultDispatchGate.isCurrent(
                                                pipeline, result
                                        )) {
                                            recordStaleFinalResult(
                                                    "before_ui_present", result
                                            );
                                            return;
                                        }
                                        presentingSourceLuma=inferenceSourceLuma;
                                        presentingSourceStamp=result.continuityStamp();
                                        try { if (presentResultIfCurrent(result,observationNanos)) {
                                            metricsCollector
                                                    .finalResultDispatchAccepted();
                                        } } finally {presentingSourceLuma=null;presentingSourceStamp=null;}

                                    } finally {

                                        result.close();
                                    }
                                }
                        );

                    } else if (containsNewCrop(
                            result.plateObservations
                    )) {

                        runOnUiThread(
                                () -> {
                                    try {
                                        /*
                                         * Stara scena nie może również trafić
                                         * do kolekcji cropów.
                                         */
                                        if (!cameraAnalysisAdmitted(controlsGeneration)
                                                || !previewPresentationBarrier.permits(
                                                        presentationGenerationAtStart
                                                )
                                                || sceneGenerationAtStart
                                                != uiSceneGeneration.get()
                                                || transformGenerationAtStart
                                                != uiCameraTransformGeneration.get()) {
                                            return;
                                        }
                                        if (!PipelineResultDispatchGate.isCurrent(
                                                pipeline, result
                                        )) {
                                            recordStaleFinalResult(
                                                    "before_crop_collect", result
                                            );
                                            return;
                                        }
                                        collectRecognitionHistory(result.plateObservations);
                                        collectCrops(
                                                result.plateObservations
                                        );
                                        metricsCollector
                                                .finalResultDispatchAccepted();

                                    } finally {

                                        result.close();
                                    }
                                }
                        );

                    } else {

                        result.close();
                    }
                            } catch(OutOfMemoryError memoryError) {
                                if(!ordinaryDynamicResourcesEnabled())throw memoryError;
                                ordinaryMemoryPaused=true;
                                android.util.Log.e("ALPR_DYNAMIC_RESOURCES","inference_memory_exhausted",memoryError);
                                runOnUiThread(this::pauseOrdinaryForMemory);
                            } finally {
                                if(tvInput!=null)tvInput.close();
                                if (!pipelineBitmap.isRecycled()) {
                                    pipelineBitmap.recycle();
                                }
                                if(sourceLease!=null)sourceLease.close();
                                if(!queuePendingDynamicMz(controlsGeneration))pipelineFrameInFlight.set(false);
                            }
                        });
                    } catch (RuntimeException dispatchError) {
                        if (!pipelineBitmap.isRecycled()) pipelineBitmap.recycle();
                        pipelineFrameInFlight.set(false);
                        if(sourceLease!=null)sourceLease.close();
                        throw dispatchError;
                    }
                },
                new CameraController.LumaFrameHandler() {
                    @Override
                    public void onLumaFrame(LumaFrame frame) {
                        directLumaTrackingUnavailable = false;
                        submitDirectLumaFrame(frame);
                    }

                    @Override
                    public void onUnavailable() {
                        directLumaTrackingUnavailable = true;
                        pendingDirectLumaFrame.set(null);
                        lastDirectLumaFrameNanos = 0L;
                        android.util.Log.w(
                                "ALPR_PREVIEW_TRACK",
                                "Oddzielny strumień YUV niedostępny; używam PreviewView fallback"
                        );
                    }
                },
                error -> runOnUiThread(() -> {
                    if (!cameraStarted || controlsGeneration != analysisControlGeneration) return;
                    cameraStarted = false;
                    cameraPreviewActive = false;
                    pauseCropCollectionForStoppedAnalysis();
                    resetAutoZoomForStoppedCamera();
                    stopPreviewSceneMonitor();

                    uiSceneGeneration.incrementAndGet();
                    finishAnalysisMeasurement(
                            ExperimentSession.CompletionReason.ERROR
                    );

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

    private boolean cameraAnalysisAdmitted(long generation) {
        return cameraStarted && generation==analysisControlGeneration
                && (!ordinaryMemoryPaused || !ordinaryDynamicResourcesEnabled())
                && metricsCollector.isMeasurementSessionActive() && pipeline.researchWorkAdmitted();
    }

    private void handleTvBboxFrameFailure(Bitmap frame,ContinuityStamp stamp,RuntimeException error){
        com.example.alpr_v1.experiment.ResearchSessionStore store=researchSessions.store();
        if(store!=null&&store.tvBboxEvidence()!=null){
            store.tvBboxEvidence().rejected(frame,stamp,error.getMessage()).complete("REJECTED_GEOMETRY",error.getMessage()==null?"input_rejected":error.getMessage());
            store.closeAdmission("TV_BBOX_GEOMETRY_CHANGED",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos());
        }
        runOnUiThread(()->{if(cameraStarted){recordError("TV BBOX: geometria wejścia zmieniła się",error);stopAnalysis(ExperimentSession.CompletionReason.ERROR);}});
    }
    private Size chooseAnalysisSize() {
        boolean scanActive = pipeline != null
                && pipeline.scanAcquisitionSnapshot().runState.active();
        return chooseAnalysisSize(scanActive);
    }

    private boolean ordinaryDynamicResourcesEnabled() {
        return !experimentModeEnabled && (pipeline==null || pipeline.researchExecutionConfig()==null)
                && effectiveSceneHandlingMode()==SceneHandlingMode.DYNAMIC_CONTINUITY;
    }

    private synchronized com.example.alpr_v1.camera.OrdinaryDynamicResourcePolicy dynamicResourcePolicy() {
        if(ordinaryResourcePolicy==null) {
            android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
            if(manager!=null)manager.getMemoryInfo(memory);
            ordinaryResourcePolicy=new com.example.alpr_v1.camera.OrdinaryDynamicResourcePolicy(memory.totalMem,
                    manager!=null&&manager.isLowRamDevice(),Runtime.getRuntime().maxMemory());
            ordinaryBitmapBudget=new com.example.alpr_v1.camera.BitmapMemoryBudget(ordinaryResourcePolicy.bitmapBytes);
        }
        return ordinaryResourcePolicy;
    }

    private boolean ordinaryMemoryCritical() {
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(manager==null)return false;
        android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
        return memory.lowMemory || memory.totalMem>0 && memory.availMem<Math.max(256L*1024*1024,memory.threshold);
    }

    /** Transfers the occupied neural slot; the full camera bitmap has already been released. */
    private boolean queuePendingDynamicMz(long controlsGeneration) {
        AlprPipeline scheduled=pipeline;
        if(scheduled==null)return false;
        if(!ordinaryDynamicResourcesEnabled()||!cameraAnalysisAdmitted(controlsGeneration)) {
            scheduled.discardPendingDynamicMz();return false;
        }
        if(!scheduled.hasPendingDynamicMz())return false;
        try {
            pipelineInferenceExecutor.execute(()->{
                try {
                    if(!cameraAnalysisAdmitted(controlsGeneration)||pipeline!=scheduled){scheduled.discardPendingDynamicMz();return;}
                    PipelineResult result=scheduled.processPendingDynamicMz();
                    if(result==null)return;
                    runOnUiThread(()->{
                        try {
                            if(!cameraAnalysisAdmitted(controlsGeneration)||pipeline!=scheduled
                                    ||!PipelineResultDispatchGate.isCurrent(scheduled,result))return;
                            collectRecognitionHistory(result.plateObservations);
                            collectCrops(result.plateObservations);
                            refreshVehicleIdentityPresentation();
                            if("pipeline_error".equals(result.status))livePresentation.showState(LivePresentationController.State.ERROR,"");
                        } finally {result.close();}
                    });
                } catch(OutOfMemoryError error) {
                    ordinaryMemoryPaused=true;runOnUiThread(this::pauseOrdinaryForMemory);
                } catch(RuntimeException error) {
                    android.util.Log.e("ALPR_DYNAMIC_MZ","deferred_mz_failed",error);
                } finally {pipelineFrameInFlight.set(false);}
            });
            return true;
        } catch(java.util.concurrent.RejectedExecutionException stopped) {scheduled.discardPendingDynamicMz();return false;}
    }

    private void pauseOrdinaryForMemory() {
        if(!ordinaryDynamicResourcesEnabled())return;
        ordinaryMemoryPaused=true;
        if(cameraStarted)stopAnalysis(ExperimentSession.CompletionReason.ERROR);
        queuePausedOrdinaryRuntimeRelease();
        if(livePresentation!=null)livePresentation.showUserStatus(LivePresentationController.State.ERROR,
                getString(R.string.dynamic_memory_paused),getString(R.string.dynamic_memory_resume));
        recordInfo("Analiza dynamiczna wstrzymana: mało pamięci RAM");
    }

    private void queuePausedOrdinaryRuntimeRelease() {
        AlprPipeline target=pipeline;long generation=analysisControlGeneration;
        if(target==null||pipelineInferenceExecutor.isShutdown())return;
        try {
            pipelineInferenceExecutor.execute(()->{
                if(pipeline!=target||cameraStarted||!ordinaryMemoryPaused
                        ||generation!=analysisControlGeneration||!ordinaryDynamicResourcesEnabled())return;
                // Native model heaps must be returned after the cancelled pass,
                // rather than retained by a screen already paused for low RAM.
                target.close();
                android.util.Log.i("ALPR_DYNAMIC_RESOURCES","paused_runtime_released generation="+generation);
            });
        } catch(RejectedExecutionException stopped) { /* Activity teardown already owns runtime cleanup. */ }
    }

    private void recycleBudgetedPreviewBitmap(Bitmap bitmap) {
        if(bitmap==null)return;
        com.example.alpr_v1.camera.BitmapMemoryBudget.Lease lease=previewBitmapLeases.remove(bitmap);
        recycleBitmap(bitmap);
        if(lease!=null)lease.close();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(level==android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level==android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            runOnUiThread(this::pauseOrdinaryForMemory);
    }
    @Override public void onLowMemory() { super.onLowMemory();runOnUiThread(this::pauseOrdinaryForMemory); }

    private Size choosePreviewSize() {
        boolean scanWillBeActive = vehicleCascadeEnabled
                && !experimentModeEnabled
                && requiredRecognitionModelsAvailable();
        return chooseAnalysisSize(scanWillBeActive);
    }

    private Size chooseAnalysisSize(boolean scanActive) {
        Size requested=chooseRequestedAnalysisSize(scanActive);
        boolean limited=ordinaryDynamicResourcesEnabled();
        if(cameraController!=null)cameraController.setOrdinaryDynamicResourcePolicy(limited?dynamicResourcePolicy():null);
        return limited?dynamicResourcePolicy().analysisSize(cameraResolutionCatalog.resolutions(),requested):requested;
    }

    private Size chooseRequestedAnalysisSize(boolean scanActive) {

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


        Size target = new Size(
                AnalysisResolutionPolicy.autoWidth(constrained, scanActive),
                AnalysisResolutionPolicy.autoHeight(constrained, scanActive)
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
        try {

        if (liveHud == null) {
            return;
        }

        renderHudModelInfo();


        if (!cameraStarted) {
            ((com.example.alpr_v1.ui.LiveHudView) liveHudRow).clearMetrics();
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
        ((com.example.alpr_v1.ui.LiveHudView) liveHudRow).render(snapshot, resolution, liveHudAwaitingFreshResult,
                liveCameraFrameRate.rate(android.os.SystemClock.elapsedRealtimeNanos()));
        StringBuilder hudText = new StringBuilder();
        ScanAcquisitionSnapshot scan = pipeline == null
                ? null : pipeline.scanAcquisitionSnapshot();
        boolean scanActive = scan != null && scan.runState.active();
        if (autoZoomController.enabled()) {
            hudText.append('\n').append(autoZoomHudLabel());
        }
        if (scanActive) {
            hudText.append('\n').append(getString(
                    R.string.scan_diagnostics,
                    scan.queue.size(),
                    scan.mtAttempts,
                    scan.freshMzAttempts
            ));
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
        boolean missingModels = syncMissingModelsStatus();
        if (missingModels) {
            overlayView.setActiveVehicleEntityId(0L);
        } else if (scanActive) {
            showScanUserStatus(scan);
        } else if (presentationState == LivePresentationController.State.SEARCHING
                && effectiveRoiBudgetPolicy().usesVehicleCascade()) {
            overlayView.setActiveVehicleEntityId(0L);
            showSearchingStatusForCurrentPipeline();
        } else {
            overlayView.setActiveVehicleEntityId(0L);
            livePresentation.showState(presentationState, "");
        }
        livePresentation.updateDiagnostics(hudText.toString().trim());
        updateAutoZoomButton();
        } finally {
            if (cameraStarted && pipeline != null && livePresentation != null) {
                if (!renderStaticIdle()) renderSearchControls();
            }
        }
    }

    private void renderHudModelInfo() {
        com.example.alpr_v1.ui.LiveModelInfoView info = liveModelInfo!=null?liveModelInfo:findViewById(R.id.live_model_info);
        if (info == null || info.getVisibility() != View.VISIBLE) return;
        MetricsCollector.LiveSnapshot snapshot = metricsCollector.liveSnapshot();
        boolean measured = cameraStarted && snapshot.sourceWidth > 0 && snapshot.sourceHeight > 0;
        Size requested = measured ? null : chooseAnalysisSize();
        String resolution = getString(measured ? R.string.hud_source_actual : R.string.hud_source_requested,
                measured ? snapshot.sourceWidth : requested.getWidth(),
                measured ? snapshot.sourceHeight : requested.getHeight());
        info.render(pipeline == null ? java.util.Collections.emptyList()
                : pipeline.activeModelRuntimeSummaries(), resolution);
    }

    private void showScanUserStatus(ScanAcquisitionSnapshot scan) {
        if(ordinaryDynamicResourcesEnabled()) {
            if(livePresentation!=null&&dynamicDisplaySnapshot!=null)
                livePresentation.renderDynamicResult(dynamicDisplaySnapshot,overlayView.dynamicSelectedEntity(),scan);
            return;
        }
        if (cameraStarted && pipeline != null && pipeline.isStaticIdle()) {
            livePresentation.showState(LivePresentationController.State.STATIC_IDLE, "");
            return;
        }
        if (renderSearchControls()) return;
        if (syncMissingModelsStatus()) return;
        overlayView.setVehicleEntityProgress(
                scan.identifiedEntityIds,
                java.util.Collections.emptySet()
        );
        if (scan.entityRecognitions.isEmpty()
                && scan.completedEntityIds.isEmpty()) {
            livePresentation.clearResult();
        }
        overlayView.setActiveVehicleGeometryMaximumAgeNanos(
                vehicleOverlayMaximumAgeNanos(
                        pipeline == null ? 0L : pipeline.lastMpObservationGapNanos()
                )
        );
        long markerEntityId = PreviewContinuityUiPolicy.activeVehicleMarkerEntityId(
                scan.activeEntityId,
                latestPipelinePlateEntityId,
                !latestPipelinePlateItems.isEmpty()
        );
        overlayView.setActiveVehicleEntityId(markerEntityId);
        android.util.Log.d(
                "ALPR_ACTIVE_MARKER",
                "scan_entity=" + scan.activeEntityId
                        + " marker_entity=" + markerEntityId
                        + " session=" + scan.activeSessionId
                        + " directive_revision=" + scan.directive.revision
        );
        if (scan.activeSessionState
                == com.example.alpr_v1.domain.TargetSessionState.RECOVERING) {
            livePresentation.showUserStatus(
                    LivePresentationController.State.RECOVERING,
                    getString(R.string.scan_status_recovering),
                    getString(R.string.scan_hint_recovering)
            );
        } else if (scan.activeEntityId > 0L) {
            livePresentation.showUserStatus(
                    LivePresentationController.State.RECOGNIZING,
                    getString(R.string.scan_status_reading),
                    getString(R.string.scan_hint_reading)
            );
        } else if (scan.queue.size() > 0) {
            livePresentation.showUserStatus(
                    LivePresentationController.State.SEARCHING,
                    getString(R.string.scan_status_selecting),
                    getString(R.string.scan_hint_selecting)
            );
        } else {
            livePresentation.showUserStatus(
                    LivePresentationController.State.SEARCHING,
                    getString(R.string.scan_status_searching),
                    getString(R.string.scan_hint_searching)
            );
        }
    }

    private void showSearchingStatusForCurrentPipeline() {
        if (renderStaticIdle() || renderSearchControls()) return;
        if (effectiveRoiBudgetPolicy().usesVehicleCascade()) {
            livePresentation.showUserStatus(
                    LivePresentationController.State.SEARCHING,
                    getString(R.string.scan_status_searching),
                    getString(R.string.scan_hint_searching)
            );
        } else {
            livePresentation.showState(
                    LivePresentationController.State.SEARCHING,
                    ""
            );
        }
    }

    private boolean requiredRecognitionModelsAvailable() {
        return modelRegistry != null && modelRegistry.hasCompleteAlprComposition();
    }

    private boolean syncMissingModelsStatus() {
        if (requiredRecognitionModelsAvailable()) {
            livePresentation.clearMissingModelsStatus();
            return false;
        }
        showMissingModelsStatus();
        return true;
    }

    private void showMissingModelsStatus() {
        overlayView.setActiveVehicleEntityId(0L);
        overlayView.clearPlateItems();
        livePresentation.clearResult();
        livePresentation.showMissingModelsStatus(
                missingModelsStatusText(),
                getString(R.string.live_hint_models_missing)
        );
    }

    private String missingModelsStatusText() {
        boolean plateMissing = modelRegistry == null
                || modelRegistry.getActive(
                com.example.alpr_v1.model.ModelRole.PLATE
        ) == null;
        boolean characterMissing = modelRegistry == null
                || modelRegistry.getActive(
                com.example.alpr_v1.model.ModelRole.CHARACTER
        ) == null;
        if (plateMissing && characterMissing) {
            return getString(R.string.live_state_models_missing_both);
        }
        return getString(
                plateMissing
                        ? R.string.live_state_plate_model_missing
                        : R.string.live_state_character_model_missing
        );
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
        if(!experimentModeEnabled&&pipeline!=null&&effectiveSceneHandlingMode()==SceneHandlingMode.DYNAMIC_CONTINUITY
                &&autoZoomController.state()==AutoZoomController.State.READY)return dynamicAutoZoomWaitLabel();
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
    private String dynamicAutoZoomWaitLabel() {
        if(pipeline==null)return getString(R.string.dynamic_az_wait_pick);
        switch(pipeline.dynamicZoomWaitReason()) {
            case "PICK":return getString(R.string.dynamic_az_wait_pick);
            case "MOTION":return getString(R.string.dynamic_az_wait_motion);
            case "BUDGET":return getString(R.string.dynamic_az_cycle_used);
            case "RECOVERY":return getString(R.string.dynamic_az_wait_recovery);
            default:return getString(R.string.dynamic_az_wait_geometry);
        }
    }
    private String hudRoiLabel() {
        if(ordinaryDynamicResourcesEnabled()) {
            Size actual=cameraController==null?null:cameraController.actualAnalysisSize();
            if(actual!=null)return (effectiveRoiBudgetPolicy()==RoiBudgetPolicy.ONE_ROI?"R1":
                    effectiveRoiBudgetPolicy()==RoiBudgetPolicy.TWO_ROI?"R2":"R0")+" · "+
                    getString(R.string.dynamic_memory_resolution,actual.getWidth(),actual.getHeight(),dynamicResourcePolicy().analysisPixels/1_000_000.0);
        }

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
    private boolean presentResultIfCurrent(
            PipelineResult result,
            long observationNanos
    ) {
        if (!PipelineResultDispatchGate.isCurrent(pipeline, result)) {
            recordStaleFinalResult("present_result_guard", result);
            return false;
        }
        presentCurrentResult(result, observationNanos);
        return true;
    }

    private void presentCurrentResult(PipelineResult result, long observationNanos) {
        refreshVehicleIdentityPresentation();
        presentIdentityCurrentResult(pipeline == null ? result : pipeline.currentIdentityResult(result), observationNanos);
    }

    private void refreshVehicleIdentityPresentation() {
        if (overlayView == null) return;
        boolean enabled = cameraStarted && pipeline != null && ordinaryDynamicResourcesEnabled();
        dynamicDisplaySnapshot=enabled?pipeline.dynamicDisplaySnapshot(currentPreviewSourceFrameStamp().continuityStamp()):null;
        Map<Long, com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation> values =
                enabled ? dynamicDisplaySnapshot==null?java.util.Collections.emptyMap():dynamicDisplaySnapshot.identities : null;
        normalIdentityPresentationStarted = enabled;
        overlayView.setVehicleIdentityPresentation(values,
                enabled && dynamicDisplaySnapshot!=null ? dynamicDisplaySnapshot.plateOwners : null);
        overlayView.setDynamicVehicleSnapshot(enabled,enabled?dynamicDisplaySnapshot:null,
                enabled?pipeline.targetFocus().entityId:0L);
        if(livePresentation!=null) {
            livePresentation.setDynamicPresentation(enabled);
            if(enabled)livePresentation.renderDynamicResult(dynamicDisplaySnapshot,overlayView.dynamicSelectedEntity(),pipeline.scanAcquisitionSnapshot());
        }
        if(enabled)for(long entity:overlayView.consumeDynamicPlateRefresh())pipeline.requestDynamicPlateRefresh(entity);
    }

    private boolean normalIdentityPresentationStarted;

    private void presentIdentityCurrentResult(PipelineResult result, long observationNanos) {
        overlayView.setPresentationStamp(result.continuityStamp());
        if ("models_missing".equals(result.status)) {
            showMissingModelsStatus();
            return;
        }
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
            previewVehicleTracker.reset();
            resetGlobalPreviewMotionState();
            targetStateMachine.reset();
            plateOverlayFreshness.reset();
            /*
             * TrackId w pipeline może po resecie zacząć się ponownie od 1.
             * Stary stan próbkowania cropów nie może zostać przypisany
             * do tablicy z nowego zdjęcia.
             */
            lastCaptureByTrack.clear();

            /*
             * Natychmiast usuwamy ewentualny stary overlay.
             */
            overlayView.clearPlateItems();
            overlayView.setStationaryScene(stationarySceneSupported());
            overlayView.setItems(
                    java.util.Collections.emptyList()
            );
        }

        // Publish the first OCR crop before overlay/auto-zoom presentation can return early.
        collectRecognitionHistory(result.plateObservations);
        boolean scanReleaseBarrierActive = applyScanTargetReleaseIfNeeded();

        if (autoZoomController.state()
                == AutoZoomController.State.ZOOMED_RETRY) {
            // MT geometry is useful even when MZ has no new reading or loses to the badge.
            presentZoomedMtStage(result.overlayItems, result.sourceWidth, result.sourceHeight,
                    result.continuityStamp());
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
        if ("pipeline_error".equals(result.status)
                || "runtime_contract_failed".equals(result.status)) {
            refreshPersistentLogThrottled();
        }
        long presentationNanos = System.nanoTime();
        List<OverlayItem> scanScopedItems = scanScopedOverlayItems(result);
        FrameMotionTransform inferenceLatencyMotion =
                result.sourceTimestampDomain.cameraDerived()
                        ? frameMotionHistory.transformAfter(
                        result.sourceTimestampNanos
                )
                        : FrameMotionTransform.invalid();
        List<OverlayItem> motionCompensatedItems =
                entityOverlayMotionProjector.compensateInferenceLatency(
                        scanScopedItems,
                        inferenceLatencyMotion
                );
        if (motionCompensatedItems != scanScopedItems) {
            android.util.Log.d(
                    "ALPR_RESULT_MOTION",
                    String.format(
                            Locale.ROOT,
                            "source_timestamp_ns=%d dx=%.5f dy=%.5f items=%d",
                            result.sourceTimestampNanos,
                            inferenceLatencyMotion.mapX(0.5f, 0.5f) - 0.5f,
                            inferenceLatencyMotion.mapY(0.5f, 0.5f) - 0.5f,
                            motionCompensatedItems.size()
                    )
            );
        }
        TargetSnapshot liveTarget = targetStateMachine.snapshot();
        long alignmentEntityId = EntityAwareOverlayAlignment.resolveSourceEntityId(
                result.plateObservations,
                liveTarget.trackId
        );
        List<OverlayItem> latencyAlignedItems = EntityAwareOverlayAlignment.align(
                motionCompensatedItems,
                liveTarget,
                alignmentEntityId
        );
        boolean geometryAlignedToPresentation =
                latencyAlignedItems != motionCompensatedItems
                        || motionCompensatedItems != scanScopedItems;
        List<OverlayItem> visibleOverlayItems =
                effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY
                        ? latencyAlignedItems : overlayTracker.update(
                        latencyAlignedItems,
                        geometryAlignedToPresentation
                                ? presentationNanos : observationNanos,
                        presentationNanos
                );
        visibleOverlayItems = withZoomVehicleGeometry(visibleOverlayItems);
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

        long vehiclePresentationNowNanos =
                android.os.SystemClock.elapsedRealtimeNanos();
        long vehiclePresentationMaximumAgeNanos = vehicleOverlayMaximumAgeNanos(
                        pipeline == null ? 0L : pipeline.lastMpObservationGapNanos()
                );
        long previewVehicleGeometryAgeNanos =
                previewVehicleGeometryFreshAtNanos <= 0L
                        ? Long.MAX_VALUE
                        : Math.max(
                                0L,
                                vehiclePresentationNowNanos
                                        - previewVehicleGeometryFreshAtNanos
                        );
        boolean preserveDynamicVehiclePresentation = PreviewContinuityUiPolicy
                .shouldPreserveDynamicVehiclePresentation(
                        effectiveSceneHandlingMode()
                                == SceneHandlingMode.DYNAMIC_CONTINUITY,
                        countKind(diagnosticItems, OverlayItem.Kind.VEHICLE) > 0,
                        countKind(latestPreviewMotionItems, OverlayItem.Kind.VEHICLE) > 0,
                        directLumaTrackingFresh(),
                        previewPresentationBarrier.active(),
                        previewVehicleGeometryAgeNanos,
                        vehiclePresentationMaximumAgeNanos
                );
        if(ordinaryDynamicResourcesEnabled())preserveDynamicVehiclePresentation &=
                hasLocalVehicleGeometry(scanScopedItems,dynamicDisplaySnapshot);
        if (preserveDynamicVehiclePresentation) {
            diagnosticItems.clear();
            diagnosticItems.addAll(nonPlateOverlayItems(latestPreviewMotionItems));
            android.util.Log.d(
                    "ALPR_OVERLAY_AUTHORITY",
                    String.format(
                            Locale.ROOT,
                            "preserve=direct_luma age_ms=%.1f vehicles=%d",
                            previewVehicleGeometryAgeNanos / 1_000_000.0,
                            countKind(diagnosticItems, OverlayItem.Kind.VEHICLE)
                    )
            );
        }


        latestDiagnosticOverlayItems =
                java.util.Collections.unmodifiableList(
                        diagnosticItems
                );
        latestDiagnosticOverlayAnchorTimestampNanos = Math.max(
                result.sourceTimestampNanos,
                lastDirectLumaSourceTimestampNanos
        );
        if(!ordinaryDynamicResourcesEnabled()||!preserveDynamicVehiclePresentation)
            anchorPreviewVehicleTracker(ordinaryDynamicResourcesEnabled()?scanScopedItems:latestDiagnosticOverlayItems,
                    result.sourceWidth,result.sourceHeight);


        long freshPlateEntityId = plateEntityId(result, pipelinePlateItems);
        Set<Long> freshMzPlateTracks = new HashSet<>();
        for (PlateObservation observation : result.plateObservations) {
            if (!observation.freshMzAttempted) continue;
            for (OverlayItem plate : pipelinePlateItems) {
                if (plate.trackId == observation.plateTrackId
                        || plate.trackId == observation.trackId) {
                    freshMzPlateTracks.add(plate.trackId);
                }
            }
        }
        latestFreshMzPlateTrackIds = java.util.Collections.unmodifiableSet(
                freshMzPlateTracks
        );
        if (!pipelinePlateItems.isEmpty()) {
            latestPipelinePlateItems =
                    java.util.Collections.unmodifiableList(
                            pipelinePlateItems
                    );
            latestPipelinePlateEntityId = freshPlateEntityId;
            plateOverlayFreshness.recordFresh(
                    pipelinePlateItems,
                    android.os.SystemClock.elapsedRealtimeNanos()
            );
        } else if (!hasCarriedPlatePrediction
                && !shouldHoldScanPlateGeometry()) {
            latestPipelinePlateItems = java.util.Collections.emptyList();
            latestPipelinePlateEntityId = 0L;
        }

        List<OverlayItem> presentationSourceItems = visibleOverlayItems;
        if (preserveDynamicVehiclePresentation) {
            List<OverlayItem> preserved = new ArrayList<>(diagnosticItems);
            for (OverlayItem item : visibleOverlayItems) {
                if (item.kind == OverlayItem.Kind.PLATE) preserved.add(item);
            }
            presentationSourceItems = java.util.Collections.unmodifiableList(
                    preserved
            );
        }
        ScanAcquisitionSnapshot presentationScan = pipeline == null
                ? null : pipeline.scanAcquisitionSnapshot();
        boolean scanPresentationActive = presentationScan != null
                && (presentationScan.runState.active()
                || presentationScan.directive.action
                == AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET);
        presentationSourceItems = preserveFreshScanVehicleItems(
                presentationSourceItems,
                latestPreviewMotionItems,
                scanPresentationActive,
                previewVehicleGeometryAgeNanos
                        <= vehiclePresentationMaximumAgeNanos
        );
        /*
         * scanScopedOverlayItems() jest jedynym właścicielem bariery encji.
         * Przy RELEASE może przepuścić wyłącznie świeżą geometrię z bieżącego
         * wyniku MT jako nieruchomą migawkę do fade. Ponowne usunięcie całej
         * warstwy PLATE w tym miejscu kasowałoby także tę poprawną ramkę.
         */
        List<OverlayItem> presentedOverlayItems = presentationSourceItems;
        if (!containsPlate(presentationSourceItems)
                && !scanReleaseBarrierActive
                && shouldHoldScanPlateGeometry()) {
            presentedOverlayItems = new ArrayList<>(presentationSourceItems);
            for (OverlayItem plate : latestPipelinePlateItems) {
                presentedOverlayItems.add(new OverlayItem(
                        plate.kind,
                        plate.normalizedBounds,
                        plate.normalizedKeypoints,
                        plate.label,
                        plate.trackId,
                        true
                ));
            }
        }

        latestOverlaySourceWidth = result.sourceWidth;
        latestOverlaySourceHeight = result.sourceHeight;



        overlayView.setFocusedTrackId(targetStateMachine.snapshot().trackId);
        overlayView.setStationaryScene(stationarySceneSupported());
        overlayView.setItems(
                presentedOverlayItems,
                result.sourceWidth,
                result.sourceHeight
        );
        if (pipeline != null) {
            ScanAcquisitionSnapshot recognitionScan =
                    pipeline.scanAcquisitionSnapshot();
            if (recognitionScan.runState.active()
                    || recognitionScan.directive.action
                    == AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET) {
                // Jedyna ścieżka publikacji wyniku encji: świeża geometria
                // PLATE jest już w View, więc może stać się źródłem transferu.
                overlayView.setVehicleEntityStates(
                        recognitionScan.identifiedEntityIds,
                        recognitionScan.completedEntityIds,
                        recognitionScan.entityRecognitions
                );
            }
        }
        if (scanReleaseBarrierActive) {
            // RELEASE kończy własność sesji, ale świeża migawka MT z tego
            // samego wyniku znika spokojnie zamiast błysnąć i zgasnąć.
            overlayView.fadeOutPlateItems();
        }
        latestPreviewMotionItems = java.util.Collections.unmodifiableList(
                new ArrayList<>(presentedOverlayItems)
        );
        recordOverlaySnapshot(
                containsPlate(presentedOverlayItems) ? "MT" : "MP",
                presentedOverlayItems,
                firstObservationFrameId(result.plateObservations),
                result.sourceSequence,
                android.os.SystemClock.elapsedRealtimeNanos()
        );


        /*
         * Każdy nowy rzeczywisty wynik MT ponownie
         * ustawia dokładną pozycję trackera Preview.
         *
         * Jeżeli w tej konkretnej inferencji MT niczego
         * nie zwrócił, tracker zachowa poprzednią kotwicę
         * przez krótki czas.
         */
        List<OverlayItem> targetAnchorItems = scanTargetAnchorItems(
                ordinaryDynamicResourcesEnabled()?scanScopedItems:visibleOverlayItems
        );
        boolean previewTrackerAnchored =
                anchorOwnedPlatePreview(
                        targetAnchorItems,
                        result.sourceWidth,
                        result.sourceHeight,
                        result.continuityStamp()
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
                            targetAnchorItems,
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
        if (collectionActive) {
            if (!PipelineResultDispatchGate.isCurrent(pipeline, result)) {
                recordStaleFinalResult("before_crop_collect", result);
                return;
            }
            collectCrops(result.plateObservations);
        }
        if ("pipeline_error".equals(result.status)
                || "runtime_contract_failed".equals(result.status)) {
            livePresentation.showState(
                    LivePresentationController.State.ERROR,
                    hudRoiLabel()
            );
            livePresentation.showTransient(result.message);
        } else if(ordinaryDynamicResourcesEnabled()) {
            refreshVehicleIdentityPresentation();
        } else {
            PlateObservation bestObservation = null;
            ScanAcquisitionSnapshot resultScan = pipeline == null
                    ? null : pipeline.scanAcquisitionSnapshot();
            boolean scanOwnsResults = resultScan != null
                    && resultScan.runState.active();
            for (PlateObservation observation : result.plateObservations) {
                if (!observation.confirmed
                        || !RegistrationTextPolicy.displayable(observation.text)) continue;
                if (scanOwnsResults
                        && !resultScan.entityRecognitions.containsKey(
                        observation.entityId
                )) continue;
                if (bestObservation == null
                        || observation.confirmed && !bestObservation.confirmed
                        || observation.confirmed == bestObservation.confirmed
                        && observation.recognitionConfidence
                        > bestObservation.recognitionConfidence) {
                    bestObservation = observation;
                }
            }
            if (bestObservation != null) {
                if(bestObservation.identityOwner()!=null)livePresentation.showCurrentResult(bestObservation.trackId,
                        bestObservation.text,bestObservation.recognitionConfidence,bestObservation.confirmed,bestObservation.observations,
                        bestObservation.sourceTimestampNanos,bestObservation.identityOwner().epoch);
                else livePresentation.showResult(
                        bestObservation.trackId,
                        bestObservation.text,
                        bestObservation.recognitionConfidence,
                        bestObservation.confirmed,
                        bestObservation.observations
                );
                overlayView.setPresentedResultTrackId(livePresentation.presentedTrackId());
            } else if ("stabilizing".equals(result.status)
                    || !result.overlayItems.isEmpty()) {
                livePresentation.showState(
                        LivePresentationController.State.RECOGNIZING,
                        hudRoiLabel() + " · CEL"
                );
            } else {
                showSearchingStatusForCurrentPipeline();
            }
        }

        handleAutoZoomResult(result);
        if (pipeline != null) {
            ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
            if (scan.runState.active()) showScanUserStatus(scan);
        }
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
        if(sample.entityId>0) {
            autoZoomBestText=sample.text;autoZoomBestConfidence=sample.recognitionConfidence;return;
        }
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
        if(sample.entityId>0)return !sample.text.isEmpty();
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

    private boolean requestedAutoZoomEnabled() {
        return experimentSession.isRunning() ? autoZoomController.enabled()
                : uiPreferences.getBoolean(KEY_AUTO_ZOOM_ENABLED, false);
    }

    private boolean autoZoomSettingsLocked() {
        return sessionWriteBusy() || experimentSession.isRunning()
                || (experimentModeEnabled && analysisStartGate.preparing());
    }

    private void toggleAutoZoom() {
        if (autoZoomSettingsLocked()) {
            livePresentation.showTransient(getString(R.string.research_settings_locked));
            return;
        }
        boolean enable = !requestedAutoZoomEnabled();
        uiPreferences.edit().putBoolean(KEY_AUTO_ZOOM_ENABLED, enable).apply();
        autoZoomPreferenceRevision++;
        staticRefinementRequestPending = false;
        if (enable) {
            autoZoomController.setEnabled(true);
            // Enabling during a physical return must wait for that return to settle.
            if (autoZoomController.state() != AutoZoomController.State.RETURNING) {
                autoZoomController.resetSession();
                if (cameraStarted && pipeline != null
                        && effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
                    pipeline.enableStaticRefinement();
                    handleStaticRefinement(null);
                }
            }
            recordInfo(getString(R.string.auto_zoom_enabled_log));
        } else {
            disableAutoZoomByUser();
            recordInfo(getString(R.string.auto_zoom_disabled_log));
        }
        if (cameraStarted) metricsCollector.recordEvent(enable ? "auto_zoom_enabled" : "auto_zoom_disabled", 0L, 0L, null);
        updateAutoZoomButton();
        renderLiveHud();
    }

    private void disableAutoZoomByUser() {
        AutoZoomController.State previous = autoZoomController.state();
        boolean cycle = previous == AutoZoomController.State.ZOOM_SETTLING
                || previous == AutoZoomController.State.ZOOMED_RETRY
                || previous == AutoZoomController.State.RETURNING;
        cancelPendingAutoZoomStart();
        if (!cameraStarted || !cycle) {
            autoZoomOperationGeneration++;
            autoZoomController.setEnabled(false);
            return; // Manual zoom is not an AZ cycle.
        }
        autoZoomController.disableForUser();
        if (autoZoomReturnInProgress) return;
        if (previous == AutoZoomController.State.ZOOM_SETTLING && !cameraTransformInProgress) {
            autoZoomOperationGeneration++;
            autoZoomController.onRequestFailed();
            clearAutoZoomRecognitionMemory();
            if (effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY)
                deferPipelineControl(AlprPipeline::finishStaticRefinement);
            else pipeline.finishDynamicZoom();
            hideAutoZoomTarget();
            return;
        }
        requestAutoZoomReturn(null, true);
    }

    private void showAutoZoomModeDialog() {
        String[] modes = getResources().getStringArray(R.array.auto_zoom_modes);
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
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
        boolean enabled = requestedAutoZoomEnabled();
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
        autoZoomButton.setContentDescription(
                getString(enabled
                        ? R.string.auto_zoom_disable
                        : R.string.auto_zoom_enable)
        );
        autoZoomButton.setEnabled(!autoZoomSettingsLocked());
        tintCameraAction(autoZoomButton,enabled?R.color.alpr_success:R.color.alpr_accent);

        autoZoomButton.setActivated(enabled && autoZoomController.enabled());
        if(enabled && !autoZoomController.enabled())autoZoomButton.setContentDescription(
                "AZ: ustawienie ON; funkcja nieaktywna. Kliknij, aby wyłączyć ustawienie.");
        if(enabled&&autoZoomController.enabled()&&!experimentModeEnabled&&pipeline!=null
                &&effectiveSceneHandlingMode()==SceneHandlingMode.DYNAMIC_CONTINUITY
                &&autoZoomController.state()==AutoZoomController.State.READY)
            autoZoomButton.setContentDescription(dynamicAutoZoomWaitLabel()+". "+getString(R.string.auto_zoom_disable));
        autoZoomButton.setTooltipText(autoZoomButton.getContentDescription());
        if (autoZoomControl != null) {
            autoZoomControl.setVisibility(View.VISIBLE);
        }
        updateAutoZoomGlow(enabled);
    }

    private void updateAutoZoomGlow(boolean enabled) {
        if (autoZoomGlow == null) return;
        if (autoZoomGlowAnimator != null) autoZoomGlowAnimator.cancel();
        autoZoomGlow.setAlpha(0f);
        autoZoomGlow.setVisibility(View.GONE);
    }

    private void handleAutoZoomResult(PipelineResult result) {
        if (!cameraStarted) return;
        if (effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
            handleStaticRefinement(result);
            return;
        }
        if (!autoZoomController.enabled()) return;
        if (pipeline == null) return;
        if (pipeline.foregroundEntityId() <= 0L) {
            if (currentCameraZoomRatio > 1.01f && autoZoomController.state() != AutoZoomController.State.RETURNING)
                requestAutoZoomReturn(null);
            return;
        }
        if (autoZoomController.state() == AutoZoomController.State.READY) {
            long entityId = pipeline.foregroundEntityId();
            for (PlateObservation observation : result.plateObservations) {
                if (observation.entityId != entityId) continue;
                for (AutoZoomController.Sample sample : autoZoomSamples(result)) {
                    if (sample.trackId != observation.plateTrackId || !pipeline.claimDynamicZoom(sample)) continue;
                    AutoZoomController.Decision decision = autoZoomController.requestRefinement(sample);
                    if (decision.action == AutoZoomController.Action.REQUEST_ZOOM) requestAutoZoom(decision);
                    else pipeline.finishDynamicZoom();
                    return;
                }
            }
            return;
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

    private boolean staticRefinementRequestPending;
    private void handleStaticRefinement(PipelineResult result) {
        if (!cameraStarted || staticRefinementRequestPending || pipeline == null
                || !pipeline.staticBaselineComplete() || cameraTransformInProgress) return;
        if (autoZoomController.state() == AutoZoomController.State.ZOOMED_RETRY) {
            AutoZoomController.Decision decision = autoZoomController.evaluate(
                    result == null ? java.util.Collections.emptyList() : autoZoomSamples(result), System.nanoTime());
            if (decision.action == AutoZoomController.Action.RETURN_NORMAL) requestAutoZoomReturn(decision);
            return;
        }
        if (currentCameraZoomRatio > 1.01f || autoZoomController.state() == AutoZoomController.State.RETURNING) return;
        staticRefinementRequestPending = true;
        AlprPipeline active = pipeline;
        ContinuityStamp stamp = active.currentContinuityStamp(0L);
        boolean enabled = autoZoomController.enabled();
        long preferenceRevision = autoZoomPreferenceRevision;
        pipelineInferenceExecutor.execute(() -> {
            AutoZoomController.Sample sample = preferenceRevision == autoZoomPreferenceRevision
                    && active.isCurrentContinuityStamp(stamp) ? active.nextStaticRefinement(enabled) : null;
            runOnUiThread(() -> {
                if (preferenceRevision != autoZoomPreferenceRevision) {
                    // A selected sample owns a paused static cycle even before any camera command.
                    // Release that old reservation on the inference executor, then use the latest choice.
                    if (sample != null && cameraStarted && pipeline == active
                            && active.isCurrentContinuityStamp(stamp) && !cameraTransformInProgress) {
                        deferPipelineControl(p -> {
                            if (p != active) return;
                            p.finishStaticRefinement();
                            runOnUiThread(() -> {
                                if (cameraStarted && pipeline == active) handleStaticRefinement(null);
                            });
                        });
                    }
                    return;
                }
                staticRefinementRequestPending = false;
                if (!cameraStarted || pipeline != active || !active.isCurrentContinuityStamp(stamp)) return;
                applyStaticRefinementSample(sample);
            });
        });
    }

    private void applyStaticRefinementSample(AutoZoomController.Sample sample) {
        if (sample == null) {
            renderStaticIdle();
            return;
        }
        AutoZoomController.Decision decision = autoZoomController.requestStaticRefinement(sample);
        if (decision.action == AutoZoomController.Action.REQUEST_ZOOM) {
            pipeline.setAutoZoomTargetLock(sample.trackId, Math.max(0f, sample.centerX - sample.normalizedWidth),
                    Math.max(0f, sample.centerY - sample.normalizedWidth * 0.5f),
                    Math.min(1f, sample.centerX + sample.normalizedWidth),
                    Math.min(1f, sample.centerY + sample.normalizedWidth * 0.5f));
            requestAutoZoom(decision);
        } else {
            pipelineInferenceExecutor.execute(() -> {
                pipeline.finishStaticRefinement();
                runOnUiThread(() -> handleStaticRefinement(null));
            });
        }
    }

    private ColorStateList cameraActionColors(int active,int disabled) {
        return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{ContextCompat.getColor(this,disabled),ContextCompat.getColor(this,active)});
    }
    private void tintCameraAction(MaterialButton button, int colorResource) {
        int id=button.getId();
        if(id==R.id.live_scene_mode_toggle||id==R.id.search_plate_button||id==R.id.release_target_button
                ||id==R.id.auto_zoom_button||id==R.id.live_diagnostics_toggle||id==R.id.live_model_info_toggle
                ||id==R.id.analysis_start_button||id==R.id.collection_toggle||id==R.id.camera_preview_button
                ||id==R.id.gallery_open_button||id==R.id.experiment_timer_button||id==R.id.export_report_button){
            com.example.alpr_v1.ui.CockpitControlStyle.State state=com.example.alpr_v1.ui.CockpitControlStyle.State.OFF;
            if(id==R.id.live_scene_mode_toggle || (id==R.id.collection_toggle&&colorResource==R.color.alpr_magenta)
                    || ((id==R.id.search_plate_button||id==R.id.camera_preview_button||id==R.id.live_diagnostics_toggle||id==R.id.live_model_info_toggle)&&colorResource==R.color.alpr_success)
                    || (id==R.id.auto_zoom_button&&colorResource==R.color.alpr_success&&autoZoomController.enabled()))
                state=com.example.alpr_v1.ui.CockpitControlStyle.State.ON;
            if(id==R.id.experiment_timer_button){
                com.example.alpr_v1.experiment.ResearchSessionViewModel.TimerState timer=researchSessions==null?null:researchSessions.timerState();
                if(timer!=null&&timer.running?timer.enabled:experimentTimerConfig.enabled())state=com.example.alpr_v1.ui.CockpitControlStyle.State.ON;
            }
            if(id==R.id.analysis_start_button){
                if(cameraStarted)state=com.example.alpr_v1.ui.CockpitControlStyle.State.STOP;
                else if(waitingForThermalStart||analysisStartGate.preparing()||experimentStartRequest.pending())state=com.example.alpr_v1.ui.CockpitControlStyle.State.BUSY;
            }
            com.example.alpr_v1.ui.CockpitControlStyle.apply(button,state);
            return;
        }
        ColorStateList color=cameraActionColors(colorResource,R.color.alpr_text_muted);
        button.setIconTint(color);button.setTextColor(color);
        button.setStrokeColor(cameraActionColors(colorResource,R.color.alpr_outline));
        button.setAlpha(1f);
    }

    /** UI labels only: reading the frozen configuration cannot select an input or change admission. */
    private String tvInputSummary() {
        boolean research=experimentSession!=null&&experimentSession.isRunning();
        ResearchExecutionConfig frozen=pipeline==null?null:pipeline.researchExecutionConfig();
        int value=R.string.main_ui_input_full;
        if(research) {
            value=frozen==null?R.string.main_ui_input_unavailable:
                    frozen.inputMode()==com.example.alpr_v1.setup.ResearchInputMode.TV_BBOX_CROP?R.string.main_ui_input_tv:
                    frozen.inputMode()==com.example.alpr_v1.setup.ResearchInputMode.CAMERA_FULL_FRAME?R.string.main_ui_input_full:R.string.main_ui_input_unavailable;
        } else if(!cameraStarted&&uiPreferences!=null&&tvBboxRequested()) value=R.string.main_ui_input_tv;
        return getString(cameraStarted||research?R.string.main_ui_input_active:R.string.main_ui_input_planned,getString(value));
    }

    private final android.view.ViewTreeObserver.OnGlobalLayoutListener overlayReadabilityLayoutListener=this::updateOverlayLabelExclusions;
    private void updateOverlayLabelExclusions() {
        if(overlayView==null||overlayView.getWidth()==0||overlayView.getHeight()==0)return;
        int[] origin=new int[2];overlayView.getLocationOnScreen(origin);
        List<RectF> exclusions=new ArrayList<>();
        for(int id:new int[]{R.id.top_app_bar,R.id.live_status_strip,R.id.live_diagnostics_toggle,R.id.live_model_info_toggle,
                R.id.live_event,R.id.phone_orientation_panel,
                R.id.confirmed_result_tray,R.id.auto_zoom_control,R.id.experiment_timer_button,R.id.experiment_thermal_button}) {
            View view=findViewById(id);android.graphics.Rect visible=new android.graphics.Rect();
            if(view!=null&&view.isShown()&&view.getGlobalVisibleRect(visible)) {
                RectF rect=new RectF(visible);rect.offset(-origin[0],-origin[1]);
                if(rect.intersect(0,0,overlayView.getWidth(),overlayView.getHeight()))exclusions.add(rect);
            }
        }
        overlayView.setLabelExclusionRects(exclusions);
        overlayView.setPresentedResultTrackId(livePresentation==null?0L:livePresentation.presentedTrackId());
    }

    private long renderedStaticIdleScene = -1L;

    private boolean renderStaticIdle() {
        if (!cameraStarted || pipeline == null || !pipeline.isStaticIdle()) {
            renderedStaticIdleScene = -1L;
            return false;
        }
        long scene = pipeline.sceneContinuitySnapshot().sceneGeneration;
        if (renderedStaticIdleScene != scene) {
            renderedStaticIdleScene = scene;
            List<OverlayItem> vehicles = new ArrayList<>();
            for (com.example.alpr_v1.tracking.VehicleCandidate vehicle : pipeline.staticBaselineVehicles()) {
                com.example.alpr_v1.domain.NormalizedBounds b = vehicle.bounds;
                vehicles.add(new OverlayItem(OverlayItem.Kind.VEHICLE,
                        new RectF(b.left, b.top, b.right, b.bottom), java.util.Collections.emptyList(),
                        "P" + vehicle.entityId, vehicle.entityId, false));
            }
            for (OverlayItem item : currentOverlayItems())
                if (item.kind == OverlayItem.Kind.PLATE) vehicles.add(item);
            applyVisibleOverlay(vehicles, latestOverlaySourceWidth, latestOverlaySourceHeight);
            overlayView.setStationaryScene(true);
            overlayView.setActiveVehicleEntityId(0L);
            overlayView.setFocusedTrackId(0L);
            ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
            overlayView.setVehicleEntityStates(scan.identifiedEntityIds, scan.completedEntityIds, scan.entityRecognitions);
        }
        livePresentation.showState(LivePresentationController.State.STATIC_IDLE, "");
        return true;
    }

    private List<AutoZoomController.Sample> autoZoomSamples(PipelineResult result) {
        List<AutoZoomController.Sample> samples = new ArrayList<>();
        TargetSnapshot target = targetStateMachine.snapshot();
        for (PlateObservation observation : result.plateObservations) {
            if(pipeline!=null&&!pipeline.isCurrentIdentityObservation(observation))continue;
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
                    (target.state == TargetSnapshot.State.LOCKED || target.state == TargetSnapshot.State.TRACKING)
                            && target.trackId == observation.trackId,
                    observation.identityOwner()==null?0L:observation.entityId,
                    observation.identityOwner()==null?0L:observation.identityOwner().epoch,
                    observation.identityOwner()==null||pipeline==null?0L:pipeline.targetFocus().sessionId,
                    observation.identityOwner()==null||pipeline==null?0L:pipeline.targetFocus().revision
            ));
        }
        return samples;
    }

    private void requestAutoZoom(AutoZoomController.Decision decision) {
        if (cameraTransformInProgress || cameraController == null) {
            if(!experimentModeEnabled&&pipeline!=null&&effectiveSceneHandlingMode()==SceneHandlingMode.DYNAMIC_CONTINUITY) {
                autoZoomController.onRequestFailed();pipeline.finishDynamicZoom();
            }
            return;
        }
        if (cameraController.maximumZoomRatio() <= 1.01f) {
            autoZoomController.onRequestFailed();
            if (effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
                deferPipelineControl(p -> {
                    p.finishStaticRefinement();
                    runOnUiThread(() -> { if (cameraStarted && pipeline == p) handleStaticRefinement(null); });
                });
            } else pipeline.finishDynamicZoom();
            livePresentation.showTransient(getString(R.string.auto_zoom_unavailable));
            recordWarning(getString(R.string.auto_zoom_unavailable));
            return;
        }

        TargetSnapshot trackedTarget = targetStateMachine.snapshot();
        fastAutoZoomVerification = effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY
                && pipeline.searchMatchState() == com.example.alpr_v1.domain.SearchMatchState.POSSIBLE_MATCH;
        boolean useTrackedGeometry = trackedTarget.trackId == decision.trackId
                && trackedTarget.hasTrack();
        autoZoomTargetSceneX = useTrackedGeometry
                ? trackedTarget.normalizedBounds.centerX()
                : decision.centerX;
        autoZoomTargetSceneY = useTrackedGeometry
                ? trackedTarget.normalizedBounds.centerY()
                : decision.centerY;

        // CameraX zoom is centered on the sensor in both scene modes.
        final float requestedZoomRatio = autoZoomRatioKeepingTargetVisible(decision);
        overlayView.setZoomPresentationActive(true);
        com.example.alpr_v1.domain.NormalizedBounds baselinePlate = pipeline.staticRefinementBounds();
        if (baselinePlate != null) {
            // Baseline presentation may already have absorbed/expired this plate.
            // The static cycle still owns its actual measured bounds at base zoom.
            overlayView.setTrackedPlateItems(java.util.Collections.singletonList(new OverlayItem(
                    OverlayItem.Kind.PLATE, new RectF(baselinePlate.left, baselinePlate.top,
                    baselinePlate.right, baselinePlate.bottom), java.util.Collections.emptyList(),
                    "", decision.trackId, false)));
        } else if (!latestPipelinePlateItems.isEmpty()) overlayView.setTrackedPlateItems(latestPipelinePlateItems);
        autoZoomBestText = autoZoomController.targetText();
        autoZoomBestConfidence = decision.beforeConfidence;
        OverlayItem returnPlate = null;
        for (OverlayItem item : latestPipelinePlateItems) {
            if (item.kind == OverlayItem.Kind.PLATE && item.trackId == decision.trackId && !item.carriedPrediction) {
                returnPlate = item; break;
            }
        }
        if (returnPlate == null && useTrackedGeometry) returnPlate = trackedTarget.overlayItem;
        if (returnPlate == null && baselinePlate != null) returnPlate = new OverlayItem(OverlayItem.Kind.PLATE,
                new RectF(baselinePlate.left,baselinePlate.top,baselinePlate.right,baselinePlate.bottom),
                java.util.Collections.emptyList(),"",decision.trackId,false);
        if (returnPlate == null) for (OverlayItem item : overlayView.snapshotVisibleItems()) {
            if (item.kind == OverlayItem.Kind.PLATE && item.trackId == decision.trackId) { returnPlate = item; break; }
        }
        autoZoomReturnGeometry.capture(returnPlate, currentPreviewSourceFrameStamp().continuityStamp(),
                pipeline.plateOwnerEntityId(decision.trackId));
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
                autoZoomHandler.postDelayed(pending, fastAutoZoomVerification
                        ? PRE_ZOOM_OVERLAY_HOLD_MS / 2L : PRE_ZOOM_OVERLAY_HOLD_MS);
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
            com.example.alpr_v1.domain.NormalizedBounds baseline = pipeline.staticRefinementBounds();
            if (baseline == null) return AutoZoomController.REQUESTED_ZOOM_RATIO;
            bounds = new RectF(baseline.left, baseline.top, baseline.right, baseline.bottom);
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

        final long operation = ++autoZoomOperationGeneration;
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
                        if (!isCurrentAutoZoomOperation(operation)) return;
                        if(appliedZoomRatio>1.01f)pipeline.dynamicZoomPhysicallyStarted();
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
                        if (!isCurrentAutoZoomOperation(operation)) return;
                        if(appliedZoomRatio>1.01f)pipeline.dynamicZoomPhysicallyStarted();
                        currentCameraZoomRatio = appliedZoomRatio;
                        float zoomedCenterX = com.example.alpr_v1.pipeline.ZoomTargetGeometry.map(
                                autoZoomTargetSceneX,
                                appliedZoomRatio
                        );
                        float zoomedCenterY = com.example.alpr_v1.pipeline.ZoomTargetGeometry.map(
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
                            if (!isCurrentAutoZoomOperation(operation)) return;
                            if (abortAutoZoomAfterTransform) {
                                abortAutoZoomAfterTransform = false;
                                requestAutoZoomReturn(null, true);
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
                        }, zoomSettlingMillis());
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!isCurrentAutoZoomOperation(operation)) return;
                        abortAutoZoomAfterTransform = false;
                        autoZoomController.onRequestFailed();
                        metricsCollector.recordEvent(
                                "lock_lost",
                                0L,
                                decision.trackId,
                                null
                        );
                        refreshAppliedCameraZoom();
                        finishControlledCameraTransform(false);
                        recordError("Nie udało się ustawić auto zoom", error);
                    }
                }, fastAutoZoomVerification ? 150L : 300L
        );
    }

    private void requestAutoZoomReturn(AutoZoomController.Decision decision) {
        requestAutoZoomReturn(decision, false);
    }

    private void requestAutoZoomReturn(AutoZoomController.Decision decision, boolean interruptZoom) {
        if (!cameraStarted || cameraController == null || autoZoomReturnInProgress
                || (cameraTransformInProgress && !interruptZoom)) return;
        final long operation = ++autoZoomOperationGeneration;
        autoZoomReturnInProgress = true;
        abortAutoZoomAfterTransform = false;
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
        prepareAutoZoomReturnGeometry();
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
        // An interrupted zoom and its return share the original transform window.
        if (!cameraTransformInProgress) beginControlledCameraTransform();
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
                        if (!isCurrentAutoZoomOperation(operation)) return;
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
                        if (!isCurrentAutoZoomOperation(operation)) return;
                        currentCameraZoomRatio = appliedZoomRatio;
                        autoZoomHandler.postDelayed(() -> {
                            if (!isCurrentAutoZoomOperation(operation)) return;
                            autoZoomReturnInProgress = false;
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
                        }, zoomSettlingMillis());
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!isCurrentAutoZoomOperation(operation)) return;
                        autoZoomReturnInProgress = false;
                        autoZoomController.onRequestFailed();
                        refreshAppliedCameraZoom();
                        finishControlledCameraTransform(false);
                        recordError("Nie udało się przywrócić zoom 1.0", error);
                    }
                }, fastAutoZoomVerification ? 280L : 560L
        );
    }

    private boolean isCurrentAutoZoomOperation(long operation) {
        return cameraStarted && operation == autoZoomOperationGeneration;
    }

    private void refreshAppliedCameraZoom() {
        float observed = cameraController.appliedZoomRatio();
        if (Float.isFinite(observed) && observed > 0f) currentCameraZoomRatio = observed;
    }

    private boolean hasPresentedFreshMzForAutoZoomTarget() {
        long targetTrackId = autoZoomController.targetTrackId();
        return targetTrackId > 0L
                && latestFreshMzPlateTrackIds.contains(targetTrackId);
    }

    private boolean fastAutoZoomVerification;

    private long zoomSettlingMillis() {
        return fastAutoZoomVerification ? AutoZoomController.SETTLING_MILLIS / 2L : AutoZoomController.SETTLING_MILLIS;
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
        resetGlobalPreviewMotionState();
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
        resetGlobalPreviewMotionState();
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
        if (!keepTarget && effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
            deferPipelineControl(AlprPipeline::finishStaticRefinement);
            autoZoomHandler.postDelayed(() -> handleStaticRefinement(null), 250L);
        }
        if (!keepTarget && effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY) pipeline.finishDynamicZoom();
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
            SceneTransitionDecision decision = pipeline.onPreviewSceneEvidenceFromUi(
                    currentPreviewSourceFrameStamp(),
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

    private boolean applyScanTargetReleaseIfNeeded() {
        if (pipeline == null) return false;
        ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
        boolean releaseActive = scan.directive.action
                == AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET;
        if (!releaseActive
                || scan.directive.revision <= appliedScanTargetReleaseRevision) {
            return releaseActive;
        }
        appliedScanTargetReleaseRevision = scan.directive.revision;
        JSONObject releaseDetails = new JSONObject();
        try {
            releaseDetails.put("scan_release_barrier_active", true);
            releaseDetails.put("scan_presentation_entity_id", 0L);
            releaseDetails.put("directive_revision", scan.directive.revision);
            releaseDetails.put("reason", scan.directive.reason);
        } catch (Exception ignored) {
            // Best-effort acceptance telemetry.
        }
        metricsCollector.recordEvent(
                "overlay_release_barrier",
                0L,
                0L,
                releaseDetails
        );
        targetStateMachine.reset();
        setScanOverlayPresentationEntity(0L);
        overlayView.setFocusedTrackId(0L);
        overlayView.setActiveVehicleEntityId(0L);
        /*
         * Bariera musi dotyczyć również aktualnego stanu View. Samo wyczyszczenie
         * pamięci prezentacji nie usuwa ramki, jeżeli wynik RELEASE nie przejdzie
         * dalej przez zwykłą ścieżkę setItems().
         */
        // Stan celu jest już zwolniony natychmiast. View wygasza tylko nieruchomą
        // migawkę starej ramki, która nie uczestniczy dalej w trackingu.
        return true;
    }

    private void resetScanPlateVisualState(boolean fadeVisiblePlate) {
        if(!ordinaryDynamicResourcesEnabled())previewPlateTracker.reset();
        overlayTracker.reset();
        plateOverlayFreshness.reset();
        latestPipelinePlateItems = java.util.Collections.emptyList();
        latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
        latestPipelinePlateEntityId = 0L;
        latestPreviewMotionItems = java.util.Collections.unmodifiableList(
                new ArrayList<>(nonPlateOverlayItems(latestPreviewMotionItems))
        );
        if (fadeVisiblePlate) {
            overlayView.fadeOutPlateItems();
        } else {
            overlayView.clearPlateItems();
        }
    }

    private void clearScanPlateVisualState() {
        resetScanPlateVisualState(false);
    }

    private void refreshPipelineCameraMotionEvidence() {
        refreshPipelineCameraMotionEvidence(currentPreviewMotionEvidence());
    }

    private void refreshPipelineCameraMotionEvidence(
            MotionExplanationEvidence evidence
    ) {
        AlprPipeline activePipeline = pipeline;
        if (activePipeline == null || evidence == null) return;
        activePipeline.setPreviewMotionEvidence(evidence);
    }

    private MotionExplanationEvidence currentPreviewMotionEvidence() {
        CameraMotionMonitor activeMonitor = cameraMotionMonitor;
        boolean sensorAvailable = activeMonitor != null
                && activeMonitor.isGyroscopeAvailable();
        VisualMotionEvidenceDecay.Snapshot visualMotion =
                visualMotionEvidenceDecay.snapshot(
                        android.os.SystemClock.elapsedRealtimeNanos()
                );
        boolean visualMoving = visualMotion.motionEstimated;
        boolean moving = visualMoving
                || activeMonitor != null && activeMonitor.isMoving();
        boolean rapid = visualMotion.rapid
                || activeMonitor != null && activeMonitor.isRapidMotion();
        float angular = activeMonitor == null ? 0f : activeMonitor.magnitude();
        return new MotionExplanationEvidence(
                sensorAvailable,
                moving,
                rapid,
                angular,
                cameraTransformInProgress,
                visualMoving,
                visualMotion.quality.coherenceScore,
                normalizedMotionResidual(visualMotion.quality),
                0f,
                0f,
                visualMotion.settling
        );
    }

    private static float normalizedMotionResidual(FrameMotionQuality quality) {
        if (quality == null || !Float.isFinite(quality.meanResidual)) return 1f;
        return Math.max(0f, Math.min(1f, quality.meanResidual / 32f));
    }

    private static boolean isVisualCameraMotion(
            FrameMotionTransform frameMotion
    ) {
        if (frameMotion == null
                || !frameMotion.valid
                || frameMotion.quality == null
                || !frameMotion.quality.reliableCameraMotion()) return false;
        float dx = frameMotion.mapX(0.5f, 0.5f) - 0.5f;
        float dy = frameMotion.mapY(0.5f, 0.5f) - 0.5f;
        return dx * dx + dy * dy >= 0.0025f * 0.0025f;
    }

    private static boolean isSensorConfirmedFrameMotion(
            FrameMotionTransform frameMotion
    ) {
        if (frameMotion == null
                || !frameMotion.valid
                || frameMotion.quality == null
                || !frameMotion.quality.supportsSensorConfirmedCameraMotion()) {
            return false;
        }
        float dx = frameMotion.mapX(0.5f, 0.5f) - 0.5f;
        float dy = frameMotion.mapY(0.5f, 0.5f) - 0.5f;
        return dx * dx + dy * dy >= 0.0025f * 0.0025f;
    }

    private void freezeCurrentOverlayAsMemory() {
        if (latestOverlaySourceWidth <= 0 || latestOverlaySourceHeight <= 0) return;
        List<OverlayItem> visible = overlayView.snapshotVisibleItems();
        autoZoomBaseVehicleItems = com.example.alpr_v1.ui.ZoomVehicleOverlay.snapshot(visible);
        List<OverlayItem> frozen = memoryOverlayItems(
                overlayItemsWithAutoZoomRecognitionMemory(visible),
                false
        );
        autoZoomBaseMemoryOverlayItems = java.util.Collections.unmodifiableList(frozen);
        autoZoomMemoryVisible = !frozen.isEmpty();
        applyVisibleOverlay(frozen, latestOverlaySourceWidth, latestOverlaySourceHeight);
        livePresentation.showState(
                LivePresentationController.State.RECOGNIZING,
                hudRoiLabel() + " · AZ"
        );
    }

    private List<OverlayItem> autoZoomBaseVehicleItems = java.util.Collections.emptyList();

    private List<OverlayItem> withZoomVehicleGeometry(List<OverlayItem> items) {
        List<OverlayItem> safe = items == null ? java.util.Collections.emptyList() : items;
        if (autoZoomBaseVehicleItems.isEmpty() || currentCameraZoomRatio <= 1.01f) return safe;
        return com.example.alpr_v1.ui.ZoomVehicleOverlay.atZoom(safe, autoZoomBaseVehicleItems, currentCameraZoomRatio);
    }

    private void transformMemoryOverlay(float zoomRatio) {
        if (!autoZoomMemoryVisible
                || latestOverlaySourceWidth <= 0
                || latestOverlaySourceHeight <= 0) return;
        List<OverlayItem> returned = autoZoomReturnGeometry.atZoom(zoomRatio,
                currentPreviewSourceFrameStamp().continuityStamp());
        if (returned != null) autoZoomBaseMemoryOverlayItems = returned;
        List<OverlayItem> transformed = transformOverlayItems(
                autoZoomBaseMemoryOverlayItems,
                Math.max(0.1f, zoomRatio),
                true
        );
        transformed = com.example.alpr_v1.ui.ZoomVehicleOverlay.atZoom(transformed, autoZoomBaseVehicleItems, zoomRatio);
        applyVisibleOverlay(
                transformed,
                latestOverlaySourceWidth,
                latestOverlaySourceHeight
        );
    }

    private void prepareAutoZoomReturnGeometry() {
        long track = autoZoomController.targetTrackId();
        if (autoZoomReturnGeometry.beginReturn(autoZoomBaseMemoryOverlayItems, currentCameraZoomRatio,
                currentPreviewSourceFrameStamp().continuityStamp(), track,
                pipeline == null ? 0L : pipeline.plateOwnerEntityId(track))) {
            autoZoomMemoryVisible = true;
            android.util.Log.i("ALPR_AZ_GEOMETRY", "return_to_pre_zoom_outline track=" + track);
        }
    }

    private void freezeZoomResultForReturn() {
        List<OverlayItem> visible = withZoomVehicleGeometry(overlayView.snapshotVisibleItems());
        if (visible.isEmpty()) return;
        float inverseRatio = 1f / Math.max(1f, currentCameraZoomRatio);
        List<OverlayItem> sceneItems = transformOverlayItems(
                memoryOverlayItems(visible, true),
                inverseRatio,
                true
        );
        sceneItems = com.example.alpr_v1.ui.ZoomVehicleOverlay.atZoom(sceneItems, autoZoomBaseVehicleItems, 1f);
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

        if (anchorOwnedPlatePreview(visibleItems, sourceWidth, sourceHeight,sourceStamp)) {
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
        items = withZoomVehicleGeometry(items);
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
                "%s %.0f%%",
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

    private boolean shouldHoldScanPlateGeometry() {
        if (pipeline == null
                || latestPipelinePlateEntityId <= 0L
                || latestPipelinePlateItems.isEmpty()) return false;
        ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
        return scan.runState.active()
                && scan.activeEntityId == latestPipelinePlateEntityId;
    }

    private static long plateEntityId(
            PipelineResult result,
            List<OverlayItem> plateItems
    ) {
        if (result == null || plateItems == null || plateItems.isEmpty()) return 0L;
        for (OverlayItem item : plateItems) {
            for (PlateObservation observation : result.plateObservations) {
                if (observation.entityId <= 0L) continue;
                if (observation.trackId == item.trackId
                        || observation.plateTrackId == item.trackId) {
                    return observation.entityId;
                }
            }
        }
        for (PlateObservation observation : result.plateObservations) {
            if (observation.entityId > 0L) return observation.entityId;
        }
        return 0L;
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
        // Ten callback publikuje geometrię MT przed próbą OCR.
        latestFreshMzPlateTrackIds = java.util.Collections.emptySet();
        overlayView.setStationaryScene(stationarySceneSupported());
        if (cameraTransformInProgress) overlayView.setOpticalTransformItems(items, sourceWidth, sourceHeight);
        else overlayView.setItems(items, sourceWidth, sourceHeight);
        latestPreviewMotionItems = java.util.Collections.unmodifiableList(
                new ArrayList<>(items)
        );
    }

    private static List<OverlayItem> memoryOverlayItems(
            List<OverlayItem> items,
            boolean zoomResult
    ) {
        List<OverlayItem> memory = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            memory.add(new OverlayItem(
                    item.kind,
                    item.normalizedBounds,
                    item.normalizedKeypoints,
                    item.label,
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

    private List<OverlayItem> scanTargetAnchorItems(
            List<OverlayItem> items
    ) {
        if (pipeline == null) return items;
        ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
        if (!scan.runState.active()) return items;
        PlateAnchor anchor = scan.plateAnchor;
        if (scan.activeEntityId <= 0L || anchor == null
                || anchor.entityId != scan.activeEntityId) {
            return java.util.Collections.emptyList();
        }
        List<OverlayItem> focused = new ArrayList<>();
        for (OverlayItem item : items) {
            if (item.kind == OverlayItem.Kind.PLATE
                    && item.trackId == anchor.plateTrackId) {
                focused.add(item);
            }
        }
        return focused;
    }

    /**
     * W trybie Scan warstwa PLATE nalezy w danej chwili tylko do jednej encji.
     * Wynik temporalny pipeline moze nadal zawierac poprawne, ale historyczne
     * obserwacje innych pojazdow. Nie wolno ich ponownie prezentowac podczas
     * domykania lub przelaczania sesji celu.
     */
    private List<OverlayItem> scanScopedOverlayItems(PipelineResult result) {
        if (pipeline == null || result == null) {
            return result == null
                    ? java.util.Collections.emptyList()
                    : result.overlayItems;
        }
        if(ordinaryDynamicResourcesEnabled())return dynamicOwnedPlateItems(result.overlayItems,
                pipeline.vehicleIdentityPlateStamps(result.continuityStamp()));

        ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
        boolean releaseBarrier = scan.directive.action
                == AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET;
        if (!scan.runState.active() && !releaseBarrier) {
            setScanOverlayPresentationEntity(0L);
            return result.overlayItems;
        }

        long presentationEntityId = scanPresentationEntityId(
                scan,
                result.plateObservations
        );
        setScanOverlayPresentationEntity(presentationEntityId);

        Set<Long> scopedPlateTrackIds = releaseBarrier
                ? terminalFreshReleasePlateTrackIds(scan, result)
                : scanPlateTrackIds(
                        presentationEntityId,
                        scan.plateAnchor,
                        result.plateObservations
                );
        Set<Long> allowedPlateTrackIds = new HashSet<>(scopedPlateTrackIds);
        // Pełnoklatkowy fallback może potwierdzić inną encję niż bieżący cel.
        // Dopuszczamy wyłącznie jej potwierdzoną ramkę, aby transfer miał
        // widoczne źródło; robocze ramki innych pojazdów nadal są blokowane.
        allowedPlateTrackIds.addAll(
                confirmedScanPlateTrackIds(result.plateObservations)
        );
        if (releaseBarrier) {
            android.util.Log.d(
                    "ALPR_TERMINAL_PLATE",
                    "raw_plates="
                            + countKind(result.overlayItems, OverlayItem.Kind.PLATE)
                            + " observations=" + result.plateObservations.size()
                            + " allowed=" + allowedPlateTrackIds.size()
                            + " release_revision=" + scan.directive.revision
            );
        }
        List<OverlayItem> filtered = filterScanOverlayItems(
                result.overlayItems,
                allowedPlateTrackIds
        );
        if (countKind(result.overlayItems, OverlayItem.Kind.PLATE)
                > countKind(filtered, OverlayItem.Kind.PLATE)) {
            JSONObject details = new JSONObject();
            try {
                details.put("focused_entity_id", scan.activeEntityId);
                details.put("scan_presentation_entity_id", presentationEntityId);
                details.put("directive_revision", scan.directive.revision);
            } catch (Exception ignored) {
                // Best-effort acceptance telemetry.
            }
            metricsCollector.recordEvent(
                    "overlay_exact_entity_mismatch_blocked",
                    firstObservationFrameId(result.plateObservations),
                    0L,
                    details
            );
        }
        return filtered;
    }

    /**
     * RELEASE blokuje pamięć temporalną, ale nie może skasować świeżej ramki
     * wyprodukowanej przez MT w tym samym PipelineResult. Zgodność pełnego
     * stempla źródłowego oraz rewizji sprzed RELEASE odróżnia taki wynik od
     * historycznej obserwacji, która mogłaby przeskoczyć na kolejną encję.
     */
    static Set<Long> terminalFreshReleasePlateTrackIds(
            ScanAcquisitionSnapshot scan,
            PipelineResult result
    ) {
        if (scan == null
                || result == null
                || scan.directive.action
                != AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET
                || scan.directive.revision <= 0L
                || result.plateObservations.isEmpty()
                || result.overlayItems.isEmpty()) {
            return java.util.Collections.emptySet();
        }

        Set<Long> freshObservationTracks = new HashSet<>();
        for (PlateObservation observation : result.plateObservations) {
            if (observation == null
                    || observation.entityId <= 0L
                    || observation.plateTrackId <= 0L
                    || observation.acquisitionDirectiveRevision <= 0L
                    || observation.acquisitionDirectiveRevision
                    >= scan.directive.revision
                    || observation.sceneGeneration != result.sceneGeneration
                    || observation.visualEpoch != result.visualEpoch
                    || observation.cameraTransformGeneration
                    != result.cameraTransformGeneration
                    || observation.sourceSequence != result.sourceSequence
                    || observation.sourceTimestampNanos
                    != result.sourceTimestampNanos) {
                continue;
            }
            freshObservationTracks.add(observation.plateTrackId);
        }
        if (freshObservationTracks.isEmpty()) {
            return java.util.Collections.emptySet();
        }

        Set<Long> visibleFreshTracks = new HashSet<>();
        for (OverlayItem item : result.overlayItems) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && !item.carriedPrediction
                    && freshObservationTracks.contains(item.trackId)) {
                visibleFreshTracks.add(item.trackId);
            }
        }
        return java.util.Collections.unmodifiableSet(visibleFreshTracks);
    }

    static Set<Long> confirmedScanPlateTrackIds(
            List<PlateObservation> observations
    ) {
        if (observations == null || observations.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        Set<Long> tracks = new HashSet<>();
        for (PlateObservation observation : observations) {
            if (observation != null
                    && observation.entityId > 0L
                    && observation.plateTrackId > 0L
                    && observation.confirmed
                    && RegistrationTextPolicy.displayable(observation.text)) {
                tracks.add(observation.plateTrackId);
            }
        }
        return java.util.Collections.unmodifiableSet(tracks);
    }

    static List<OverlayItem> filterScanOverlayItems(
            List<OverlayItem> items,
            Set<Long> allowedPlateTrackIds
    ) {
        if (items == null || items.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Set<Long> allowed = allowedPlateTrackIds == null
                ? java.util.Collections.emptySet()
                : allowedPlateTrackIds;
        List<OverlayItem> scoped = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item != null
                    && (item.kind != OverlayItem.Kind.PLATE
                    || allowed.contains(item.trackId))) {
                scoped.add(item);
            }
        }
        return java.util.Collections.unmodifiableList(scoped);
    }

    private void setScanOverlayPresentationEntity(long entityId) {
        long safeEntityId = Math.max(0L, entityId);
        if (scanOverlayPresentationEntityId == safeEntityId) return;

        long previousEntityId = scanOverlayPresentationEntityId;
        scanOverlayPresentationEntityId = safeEntityId;
        resetScanPlateVisualState(safeEntityId == 0L && previousEntityId > 0L);
        JSONObject details = new JSONObject();
        try {
            details.put("previous_entity_id", previousEntityId);
            details.put("overlay_entity_id", safeEntityId);
        } catch (Exception ignored) {
            // Best-effort acceptance telemetry.
        }
        metricsCollector.recordEvent(
                "overlay_entity_switch",
                0L,
                0L,
                details
        );
    }

    private void presentIntermediateGeometryStage(List<OverlayItem> items,int width,int height,
            ContinuityStamp stamp,LumaFrame source,boolean zoomed) {
        LumaFrame previousLuma=presentingSourceLuma;
        ContinuityStamp previousStamp=presentingSourceStamp;
        presentingSourceLuma=source;presentingSourceStamp=stamp;
        try {
            if(ordinaryDynamicResourcesEnabled()&&pipeline!=null) {
                refreshVehicleIdentityPresentation();
                if(!containsPlate(items)||!hasLocalVehicleGeometry(items,dynamicDisplaySnapshot))
                    anchorPreviewVehicleTracker(items,width,height);
                List<OverlayItem> owned=dynamicOwnedPlateItems(items,pipeline.vehicleIdentityPlateStamps(stamp));
                if(containsPlate(owned))anchorOwnedPlatePreview(owned,width,height,stamp);
                latestOverlaySourceWidth=width;latestOverlaySourceHeight=height;
                if(overlayView!=null)overlayView.setItems(java.util.Collections.emptyList(),width,height);
                return;
            }
            if(zoomed)presentZoomedMtStage(items,width,height,stamp);
            else presentScanMtStage(items,width,height,stamp);
        } finally {presentingSourceLuma=previousLuma;presentingSourceStamp=previousStamp;}
    }

    private void presentScanMtStage(
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
        ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
        if (!shouldPresentScanMtStage(scan)) return;
        overlayView.setPresentationStamp(sourceStamp);

        setScanOverlayPresentationEntity(scan.activeEntityId);
        List<OverlayItem> scopedStage = scanScopedMtStageItems(
                overlayItems,
                scan.activeEntityId
        );
        if (!containsPlate(scopedStage)) {
            android.util.Log.d(
                    "ALPR_MT_STAGE",
                    "blocked entity=" + scan.activeEntityId
                            + " raw_plates="
                            + countKind(overlayItems, OverlayItem.Kind.PLATE)
                            + " reason=no_plate_inside_active_vehicle"
                            + " directive_revision=" + scan.directive.revision
            );
            return;
        }
        List<OverlayItem> freshStage = activeTrackingOverlayItems(scopedStage);
        latestOverlaySourceWidth = sourceWidth;
        latestOverlaySourceHeight = sourceHeight;
        latestPipelinePlateEntityId = scan.activeEntityId;
        plateOverlayFreshness.recordFresh(
                freshStage,
                android.os.SystemClock.elapsedRealtimeNanos()
        );
        applyVisibleOverlay(freshStage, sourceWidth, sourceHeight);
        overlayView.setActiveVehicleEntityId(scan.activeEntityId);
        anchorOwnedPlatePreview(freshStage, sourceWidth, sourceHeight,sourceStamp);
        livePresentation.showState(
                LivePresentationController.State.TRACKING,
                ""
        );
        android.util.Log.d(
                "ALPR_MT_STAGE",
                "presented entity=" + scan.activeEntityId
                        + " raw_plates="
                        + countKind(overlayItems, OverlayItem.Kind.PLATE)
                        + " scoped_plates="
                        + countKind(freshStage, OverlayItem.Kind.PLATE)
                        + " directive_revision=" + scan.directive.revision
        );
    }

    static boolean shouldPresentScanMtStage(ScanAcquisitionSnapshot scan) {
        return scan != null
                && scan.runState.active()
                && scan.activeEntityId > 0L
                && scan.directive.requestsMt()
                && scan.directive.entityId == scan.activeEntityId;
    }

    /**
     * Pośredni callback MT nie ma jeszcze PlateObservation z przypisaną encją.
     * Wiążemy geometrię z aktywnym pojazdem przestrzennie i publikujemy najwyżej
     * jedną ramkę. Kandydaty innych pojazdów mogą znaleźć się w rozszerzonym
     * cropie MT, ale nie należą do bieżącej sesji Scan.
     */
    static List<OverlayItem> scanScopedMtStageItems(
            List<OverlayItem> items,
            long activeEntityId
    ) {
        if (items == null || items.isEmpty() || activeEntityId <= 0L) {
            return java.util.Collections.emptyList();
        }

        RectF activeVehicle = null;
        for (OverlayItem item : items) {
            if (item != null
                    && item.kind == OverlayItem.Kind.VEHICLE
                    && item.trackId == activeEntityId) {
                activeVehicle = item.normalizedBounds;
                break;
            }
        }
        if (activeVehicle == null) {
            for (OverlayItem item : items) {
                if (item != null
                        && item.kind == OverlayItem.Kind.VEHICLE_ROI
                        && item.trackId == activeEntityId) {
                    activeVehicle = item.normalizedBounds;
                    break;
                }
            }
        }
        if (activeVehicle == null) return java.util.Collections.emptyList();

        OverlayItem selectedPlate = null;
        float selectedConfidence = -1f;
        float selectedDistance = Float.MAX_VALUE;
        float vehicleWidth = Math.max(0f, activeVehicle.right - activeVehicle.left);
        float vehicleHeight = Math.max(0f, activeVehicle.bottom - activeVehicle.top);
        float expectedX = (activeVehicle.left + activeVehicle.right) * 0.5f;
        float expectedY = activeVehicle.top + vehicleHeight * 0.78f;
        for (OverlayItem item : items) {
            if (item == null || item.kind != OverlayItem.Kind.PLATE) continue;
            float centerX = (item.normalizedBounds.left
                    + item.normalizedBounds.right) * 0.5f;
            float centerY = (item.normalizedBounds.top
                    + item.normalizedBounds.bottom) * 0.5f;
            if (centerX < activeVehicle.left || centerX > activeVehicle.right
                    || centerY < activeVehicle.top || centerY > activeVehicle.bottom) {
                continue;
            }

            float confidence = overlayLabelConfidence(item.label);
            float dx = (centerX - expectedX) / Math.max(0.01f, vehicleWidth);
            float dy = (centerY - expectedY) / Math.max(0.01f, vehicleHeight);
            float distance = dx * dx + dy * dy;
            if (selectedPlate == null
                    || confidence > selectedConfidence + 0.0001f
                    || (Math.abs(confidence - selectedConfidence) <= 0.0001f
                    && distance < selectedDistance)) {
                selectedPlate = item;
                selectedConfidence = confidence;
                selectedDistance = distance;
            }
        }
        if (selectedPlate == null) return java.util.Collections.emptyList();

        List<OverlayItem> scoped = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item != null && item.kind != OverlayItem.Kind.PLATE) {
                scoped.add(item);
            }
        }
        scoped.add(selectedPlate);
        return java.util.Collections.unmodifiableList(scoped);
    }

    private static float overlayLabelConfidence(String label) {
        String safe = label == null ? "" : label;
        int percent = safe.lastIndexOf('%');
        if (percent <= 0) return 0f;
        int start = percent - 1;
        while (start >= 0 && Character.isDigit(safe.charAt(start))) start--;
        try {
            return Math.max(0f, Math.min(
                    1f,
                    Float.parseFloat(safe.substring(start + 1, percent)) / 100f
            ));
        } catch (RuntimeException ignored) {
            return 0f;
        }
    }

    static long scanPresentationEntityId(
            ScanAcquisitionSnapshot scan,
            List<PlateObservation> observations
    ) {
        if (scan == null) return 0L;
        /*
         * RELEASE jest twarda bariera wizualna. Ten sam PipelineResult może
         * nadal zawierać prawidłowy tekst/crop zakończonej sesji, ale żadna
         * historyczna PlateObservation nie może już odtworzyć jej pływającej
         * ramki na obrazie.
         */
        if (scan.directive.action
                == AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET) {
            return 0L;
        }

        long newestEntityId = 0L;
        long newestRevision = 0L;
        if (observations != null) {
            for (PlateObservation observation : observations) {
                if (observation == null
                        || observation.entityId <= 0L
                        || observation.acquisitionDirectiveRevision <= 0L) {
                    continue;
                }
                if (observation.acquisitionDirectiveRevision > newestRevision
                        || (observation.acquisitionDirectiveRevision == newestRevision
                        && observation.entityId == scan.activeEntityId)) {
                    newestRevision = observation.acquisitionDirectiveRevision;
                    newestEntityId = observation.entityId;
                }
            }
        }

        if (scan.activeEntityId > 0L) {
            if (newestEntityId == scan.activeEntityId) {
                return scan.activeEntityId;
            }
            if (newestRevision > 0L
                    && newestRevision >= scan.directive.revision) {
                return newestEntityId;
            }
            return scan.activeEntityId;
        }
        return newestEntityId;
    }

    static Set<Long> scanPlateTrackIds(
            long entityId,
            PlateAnchor anchor,
            List<PlateObservation> observations
    ) {
        if (entityId <= 0L) return java.util.Collections.emptySet();

        if (anchor != null
                && anchor.entityId == entityId
                && anchor.plateTrackId > 0L) {
            return java.util.Collections.singleton(anchor.plateTrackId);
        }

        long newestRevision = 0L;
        if (observations != null) {
            for (PlateObservation observation : observations) {
                if (observation != null && observation.entityId == entityId) {
                    newestRevision = Math.max(
                            newestRevision,
                            observation.acquisitionDirectiveRevision
                    );
                }
            }
        }

        Set<Long> trackIds = new HashSet<>();
        if (observations != null) {
            for (PlateObservation observation : observations) {
                if (observation == null || observation.entityId != entityId) continue;
                if (newestRevision > 0L
                        && observation.acquisitionDirectiveRevision != newestRevision) {
                    continue;
                }
                long trackId = observation.plateTrackId > 0L
                        ? observation.plateTrackId : observation.trackId;
                if (trackId > 0L) trackIds.add(trackId);
            }
        }
        return java.util.Collections.unmodifiableSet(trackIds);
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

    private void clearAutoZoomRecognitionMemory() {
        autoZoomReturnGeometry.clear();
        if (overlayView != null) overlayView.setZoomPresentationActive(false);
        cancelPendingAutoZoomStart();
        if (pipeline != null) pipeline.clearAutoZoomTargetRoi();
        autoZoomMemoryVisible = false;
        autoZoomBaseMemoryOverlayItems = java.util.Collections.emptyList();
        autoZoomBaseVehicleItems = java.util.Collections.emptyList();
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
            SceneTransitionDecision decision = pipeline.onPreviewSceneEvidenceFromUi(
                    currentPreviewSourceFrameStamp(),
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
        transformMemoryOverlay(1f);
        previewSceneDetector.reset();
        previewSceneDetector.update(previewBitmap);
        previewSceneAnchorGuard.anchor(previewBitmap);
        previewSceneAnchorPending = false;

        if (autoZoomMemoryVisible) {
            ContinuityStamp sourceStamp = currentPreviewSourceFrameStamp()
                    .continuityStamp();
            List<OverlayItem> returnedOverlay = transformOverlayItems(
                autoZoomBaseMemoryOverlayItems,
                1f,
                true
            );
            returnedOverlay = com.example.alpr_v1.ui.ZoomVehicleOverlay.atZoom(returnedOverlay, autoZoomBaseVehicleItems, 1f);
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
                // The next tracking-worker frame initializes the newly queued anchor.
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
        presentTrackedPreviewOverlay(
                trackedItems,
                FrameMotionTransform.invalid()
        );
    }

    private static boolean hasLocalVehicleGeometry(List<OverlayItem> measured,
            com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot snapshot) {
        if(snapshot==null||measured==null)return false;
        boolean vehicle=false;
        for(OverlayItem item:measured)if(item!=null&&item.kind==OverlayItem.Kind.VEHICLE) {
            vehicle=true;if(!snapshot.localGeometry.containsKey(item.trackId))return false;
        }
        return vehicle;
    }

    private void anchorPreviewVehicleTracker(
            List<OverlayItem> diagnostics,
            int sourceWidth,
            int sourceHeight
    ) {
        anchorPreviewVehicleTracker(
                diagnostics,
                sourceWidth,
                sourceHeight,
                "MP"
        );
    }

    private void anchorPreviewVehicleTracker(
            List<OverlayItem> diagnostics,
            int sourceWidth,
            int sourceHeight,
            String source
    ) {
        if (diagnostics == null || diagnostics.isEmpty()) return;
        if ("MP".equals(source) && !ordinaryDynamicResourcesEnabled()) {
            VisualMotionEvidenceDecay.Snapshot visualMotion =
                    visualMotionEvidenceDecay.snapshot(
                            android.os.SystemClock.elapsedRealtimeNanos()
                    );
            if (vehiclePreviewMotionPolicy.globalOwnsGeometry()
                    || isDynamicCameraMotion() || visualMotion.motionEstimated) {
                previewVehicleTrackerAwaitingFreshMpAnchor = true;
                previewVehicleTrackerAwaitingFirstLocalFrame = false;
                android.util.Log.d(
                        "ALPR_VEHICLE_PREVIEW_TRACK",
                        "mode=GLOBAL_FRAME_MOTION defer_mp_anchor=true"
                );
                return;
            }
        }
        List<OverlayItem> anchors = new ArrayList<>();
        for (OverlayItem item : diagnostics) {
            if (item == null
                    || item.kind != OverlayItem.Kind.VEHICLE
                    || item.carriedPrediction
                    || item.trackId <= 0L) continue;
            RectF bounds = item.normalizedBounds;
            List<android.graphics.PointF> corners = java.util.Arrays.asList(
                    new android.graphics.PointF(bounds.left, bounds.top),
                    new android.graphics.PointF(bounds.right, bounds.top),
                    new android.graphics.PointF(bounds.right, bounds.bottom),
                    new android.graphics.PointF(bounds.left, bounds.bottom)
            );
            anchors.add(new OverlayItem(
                    OverlayItem.Kind.PLATE,
                    new RectF(bounds),
                    corners,
                    item.label,
                    item.trackId,
                    false
            ));
        }
        if (!anchors.isEmpty()) {
            boolean anchored;
            if(ordinaryDynamicResourcesEnabled()&&"MP".equals(source)) {
                LumaFrame measured=presentingSourceLuma;
                if(measured==null) {
                    android.util.Log.d("ALPR_VEHICLE_PREVIEW_TRACK","anchor=false reason=source_luma_unavailable");return;
                }
                anchored=previewVehicleTracker.anchorLuma(anchors,sourceWidth,sourceHeight,pipeline==null?java.util.Collections.emptyMap():pipeline.vehicleIdentityStamps(anchors),
                        measured.gray,measured.width,measured.height,presentingSourceStamp==null?null:presentingSourceStamp.sourceFrameStamp());
            } else anchored=previewVehicleTracker.anchor(anchors, sourceWidth, sourceHeight);
            if (anchored) {
                previewVehicleTrackerAwaitingFreshMpAnchor = false;
                previewVehicleTrackerAwaitingFirstLocalFrame =
                        "GLOBAL_PRESENTATION".equals(source);
                android.util.Log.d(
                        "ALPR_VEHICLE_PREVIEW_TRACK",
                        "mode=LOCAL_KLT anchor=true source="
                                + (source == null ? "UNKNOWN" : source)
                                + " vehicles=" + anchors.size()
                );
            }
        }
    }

    private void presentTrackedPreviewOverlay(
            List<OverlayItem> trackedItems,
            FrameMotionTransform frameMotion
    ) {
        presentTrackedPreviewOverlay(
                trackedItems,
                java.util.Collections.emptyList(),
                frameMotion
        );
    }

    private void presentTrackedPreviewOverlay(
            List<OverlayItem> trackedItems,
            List<OverlayItem> locallyTrackedVehicles,
            FrameMotionTransform frameMotion
    ) {
        List<OverlayItem> ownedTrackedItems = scanOwnedTrackedPlateItems(trackedItems);
        overlayView.setFocusedTrackId(targetStateMachine.snapshot().trackId);
        // Vehicle KLT updates remain useful when the camera is still and only
        // the vehicle moves. Plate visibility must not control the vehicle layer.
        boolean dynamicCameraMotion = effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY;
        long nowNanos = android.os.SystemClock.elapsedRealtimeNanos();
        plateOverlayFreshness.recordFresh(
                ownedTrackedItems,
                nowNanos
        );
        List<OverlayItem> displayableTrackedItems =
                plateOverlayFreshness.retainDisplayable(ownedTrackedItems, nowNanos, plateOverlayMaximumAgeNanos());

        if (displayableTrackedItems.isEmpty()) {
            expireStalePlateOverlayIfNeeded();
            if (dynamicCameraMotion) {
                overlayView.setStationaryScene(stationarySceneSupported());
                overlayView.setPreviewItems(
                        dynamicCameraMotionOverlayItems(
                                displayableTrackedItems,
                                locallyTrackedVehicles,
                                frameMotion
                        )
                );
            }
            return;
        }

        if (dynamicCameraMotion) {
            overlayView.setStationaryScene(stationarySceneSupported());
            overlayView.setPreviewItems(
                    dynamicCameraMotionOverlayItems(
                            displayableTrackedItems,
                            locallyTrackedVehicles,
                            frameMotion
                    )
            );
        } else {
            overlayView.setTrackedPlateItems(displayableTrackedItems);
        }
        if (autoZoomController.state()
                == AutoZoomController.State.ZOOMED_RETRY) {
            updateAutoZoomTargetFromOverlayItems(displayableTrackedItems);
        }
    }

    private boolean isDynamicCameraMotion() {
        return effectiveSceneHandlingMode()
                == SceneHandlingMode.DYNAMIC_CONTINUITY
                && cameraMotionMonitor != null
                && cameraMotionMonitor.isMoving();
    }

    private boolean stationarySceneSupported() {
        stationarySceneSupport.setObservationInterval(
                pipeline == null ? 0L : pipeline.lastMpObservationGapNanos());
        return cameraStarted && !cameraTransformInProgress && !previewPresentationBarrier.active()
                && (cameraMotionMonitor == null || !cameraMotionMonitor.isMoving())
                && stationarySceneSupport.supported(android.os.SystemClock.elapsedRealtimeNanos());
    }

    private long vehicleOverlayMaximumAgeNanos(long measuredInterval) {
        return stationarySceneSupported()
                ? com.example.alpr_v1.ui.StationarySceneSupport.maximumOverlayAge(measuredInterval)
                : PreviewContinuityUiPolicy.vehicleOverlayMaximumAgeNanos(measuredInterval);
    }

    private long plateOverlayMaximumAgeNanos() {
        return stationarySceneSupported()
                ? com.example.alpr_v1.ui.StationarySceneSupport.maximumOverlayAge(
                        pipeline == null ? 0L : pipeline.lastMpObservationGapNanos())
                : PlateOverlayFreshness.MAXIMUM_AGE_NANOS;
    }

    private List<OverlayItem> scanOwnedTrackedPlateItems(List<OverlayItem> trackedItems) {
        if(ordinaryDynamicResourcesEnabled()&&pipeline!=null) {
            java.util.Map<Long,Long> owners=pipeline.vehicleIdentityPlateOwners();
            long selected=pipeline.targetFocus().entityId;
            List<OverlayItem> result=new ArrayList<>();
            if(trackedItems!=null)for(OverlayItem item:trackedItems)
                if(item.kind!=OverlayItem.Kind.PLATE||owners.containsKey(item.trackId)
                        &&(selected<=0||owners.get(item.trackId)==selected))result.add(item);
            return result;
        }
        if (trackedItems == null || trackedItems.isEmpty() || pipeline == null) {
            return trackedItems == null
                    ? java.util.Collections.emptyList()
                    : trackedItems;
        }
        ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
        boolean accepted = PreviewContinuityUiPolicy.acceptsTrackedScanPlate(
                scan.runState.active(),
                scan.activeEntityId,
                latestPipelinePlateEntityId,
                !latestPipelinePlateItems.isEmpty()
        );
        if (!accepted) return java.util.Collections.emptyList();
        if (!scan.runState.active()) return trackedItems;

        Set<Long> allowedTrackIds = new HashSet<>();
        for (OverlayItem plate : latestPipelinePlateItems) {
            if (plate != null
                    && plate.kind == OverlayItem.Kind.PLATE
                    && plate.trackId > 0L) {
                allowedTrackIds.add(plate.trackId);
            }
        }
        List<OverlayItem> owned = new ArrayList<>();
        for (OverlayItem plate : trackedItems) {
            if (plate != null
                    && plate.kind == OverlayItem.Kind.PLATE
                    && allowedTrackIds.contains(plate.trackId)) {
                owned.add(plate);
            }
        }
        return java.util.Collections.unmodifiableList(owned);
    }

    private List<OverlayItem> dynamicCameraMotionOverlayItems(
            List<OverlayItem> trackedPlates
    ) {
        return dynamicCameraMotionOverlayItems(
                trackedPlates,
                FrameMotionTransform.invalid()
        );
    }

    private List<OverlayItem> dynamicCameraMotionOverlayItems(
            List<OverlayItem> trackedPlates,
            FrameMotionTransform frameMotion
    ) {
        return dynamicCameraMotionOverlayItems(
                trackedPlates,
                java.util.Collections.emptyList(),
                frameMotion
        );
    }

    private List<OverlayItem> dynamicCameraMotionOverlayItems(
            List<OverlayItem> trackedPlates,
            List<OverlayItem> locallyTrackedVehicles,
            FrameMotionTransform frameMotion
    ) {
        trackedPlates = scanOwnedTrackedPlateItems(trackedPlates);
        long nowNanos = android.os.SystemClock.elapsedRealtimeNanos();
        plateOverlayFreshness.recordFresh(
                trackedPlates,
                nowNanos
        );
        List<OverlayItem> displayableTrackedPlates =
                plateOverlayFreshness.retainDisplayable(trackedPlates, nowNanos, plateOverlayMaximumAgeNanos());
        if ((trackedPlates != null && !trackedPlates.isEmpty())
                && displayableTrackedPlates.isEmpty()) {
            expireStalePlateOverlayIfNeeded();
        }
        TargetSnapshot focusedTarget = targetStateMachine.snapshot();
        long focusedEntityId = latestPipelinePlateEntityId;
        long focusedPlateTrackId = focusedTarget.trackId;
        VehicleTrackingFrame vehicleFrame = pipeline == null
                ? null : pipeline.latestVehicleTrackingFrame();
        long maximumVehicleAgeNanos = vehicleOverlayMaximumAgeNanos(
                        pipeline == null ? 0L : pipeline.lastMpObservationGapNanos()
                );
        if (pipeline != null) {
            ScanAcquisitionSnapshot scan = pipeline.scanAcquisitionSnapshot();
            if (scan.runState.active() && scan.activeEntityId > 0L) {
                focusedEntityId = scan.activeEntityId;
                if (scan.plateAnchor != null
                        && scan.plateAnchor.entityId == focusedEntityId) {
                    focusedPlateTrackId = scan.plateAnchor.plateTrackId;
                }
            }
        }
        FrameMotionTransform diagnosticMotion = frameMotion == null
                ? FrameMotionTransform.invalid() : frameMotion;
        FrameMotionTransform candidateMotion = diagnosticMotion;
        List<OverlayItem> projectionBase = latestPreviewMotionItems.isEmpty()
                ? currentOverlayItems() : latestPreviewMotionItems;
        if (!latestDiagnosticOverlayItems.isEmpty()
                && latestDiagnosticOverlayAnchorTimestampNanos > 0L) {
            diagnosticMotion = frameMotionHistory.transformAfter(
                    latestDiagnosticOverlayAnchorTimestampNanos
            );
            List<OverlayItem> anchored = new ArrayList<>(
                    latestDiagnosticOverlayItems.size()
                            + latestPipelinePlateItems.size()
            );
            anchored.addAll(latestDiagnosticOverlayItems);
            anchored.addAll(latestPipelinePlateItems);
            projectionBase = java.util.Collections.unmodifiableList(anchored);
            if (vehicleFrame != null
                    && vehicleFrame.sourceTimestampDomain.cameraDerived()
                    && vehicleFrame.snapshotTimestampNanos > 0L) {
                candidateMotion = frameMotionHistory.transformAfter(
                        vehicleFrame.snapshotTimestampNanos
                );
            } else {
                candidateMotion = diagnosticMotion;
            }
        }
        List<OverlayItem> platesForProjection = displayableTrackedPlates;
        boolean holdingScanPlate = platesForProjection.isEmpty()
                && shouldHoldScanPlateGeometry();
        if (holdingScanPlate) {
            platesForProjection = entityOverlayMotionProjector.compensateInferenceLatency(
                    latestPipelinePlateItems,
                    diagnosticMotion
            );
        }
        List<OverlayItem> motionTrackedVehicles = locallyTrackedVehicles;
        long globalGeometryAgeNanos = previewVehicleGeometryFreshAtNanos <= 0L
                ? Long.MAX_VALUE
                : Math.max(
                        0L,
                        nowNanos - previewVehicleGeometryFreshAtNanos
                );
        boolean freshGlobalVehicleGeometry =
                diagnosticMotion.valid
                        && globalGeometryAgeNanos
                        <= maximumVehicleAgeNanos;
        if (freshGlobalVehicleGeometry) {
            List<OverlayItem> globalVehicleBase = latestDiagnosticOverlayItems;
            FrameMotionTransform globalVehicleMotion = diagnosticMotion;
            if (globalVehicleBase.isEmpty() && !projectionBase.isEmpty()) {
                /*
                 * Pojedynczy pusty MP/Scan nie moĹĽe usuwaÄ‡ ostatniej
                 * geometrii w tej samej chwili, w ktĂłrej bieĹĽÄ…ca klatka daje
                 * Ĺ›wieĹĽy, wiarygodny ruch kamery. latestPreviewMotionItems sÄ…
                 * juĹĽ w ukĹ‚adzie poprzedniej klatki, dlatego stosujemy do nich
                 * tylko transformacjÄ™ frame-to-frame, bez podwĂłjnego cumulative.
                 */
                globalVehicleBase = projectionBase;
                globalVehicleMotion = frameMotion == null
                        ? FrameMotionTransform.invalid() : frameMotion;
            }
            List<OverlayItem> globalVehicles = globallyTrackedVehicleOverlayItems(
                    globalVehicleBase,
                    globalVehicleMotion
            );
            // A local miss (or a callback without local results) must not erase a
            // vehicle while recent camera-motion evidence still supports its geometry.
            // Keep each successful local measurement; fill only its missing neighbors.
            motionTrackedVehicles = entityOverlayMotionProjector.mergeVehicleGeometry(
                    motionTrackedVehicles, globalVehicles);
            freshGlobalVehicleGeometry = motionTrackedVehicles.size() > locallyTrackedVehicles.size();
        }
        List<OverlayItem> projected = entityOverlayMotionProjector.project(
                projectionBase,
                plateOnlyOverlayItems(platesForProjection),
                motionTrackedVehicles,
                focusedEntityId,
                focusedPlateTrackId,
                vehicleFrame,
                maximumVehicleAgeNanos,
                diagnosticMotion,
                candidateMotion
        );
        if (!experimentModeEnabled && effectiveSceneHandlingMode()
                == com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY) {
            projected = com.example.alpr_v1.ui.VehicleMeasurementOverlay.retainSupportedVehicles(projected, vehicleFrame,dynamicDisplaySnapshot);
        }
        latestPreviewMotionItems = projected;
        String projectionEvent = focusedEntityId > 0L
                ? "overlay_dynamic_projection_applied"
                : "overlay_dynamic_projection_skipped_no_identity";
        recordOverlaySnapshot(
                holdingScanPlate
                        ? "SCAN_HOLD"
                        : displayableTrackedPlates.isEmpty() ? "KALMAN" : "KLT",
                projected,
                vehicleFrame == null ? 0L : vehicleFrame.sourceFrameId,
                vehicleFrame == null ? 0L : vehicleFrame.sourceSequence,
                nowNanos
        );
        if (nowNanos - lastDynamicProjectionEventNanos >= 250_000_000L) {
            lastDynamicProjectionEventNanos = nowNanos;
            JSONObject details = new JSONObject();
            try {
                details.put("overlay_entity_id", focusedEntityId);
                details.put("overlay_plate_track_id", focusedPlateTrackId);
                details.put("vehicle_overlay_count",
                        countKind(projected, OverlayItem.Kind.VEHICLE));
                details.put("plate_overlay_count",
                        countKind(projected, OverlayItem.Kind.PLATE));
                details.put("local_vehicle_overlay_count",
                        countKind(motionTrackedVehicles, OverlayItem.Kind.VEHICLE));
                details.put(
                        "vehicle_geometry_source",
                        freshGlobalVehicleGeometry ? "GLOBAL_FRAME_MOTION" : "LOCAL_KLT"
                );
                details.put("global_vehicle_geometry_age_ms",
                        globalGeometryAgeNanos == Long.MAX_VALUE
                                ? -1.0 : globalGeometryAgeNanos / 1_000_000.0);
                details.put(
                        "diagnostic_anchor_timestamp_ns",
                        latestDiagnosticOverlayAnchorTimestampNanos
                );
                details.put(
                        "diagnostic_motion_dx",
                        diagnosticMotion.mapX(0.5f, 0.5f) - 0.5f
                );
                details.put(
                        "candidate_motion_dx",
                        candidateMotion.mapX(0.5f, 0.5f) - 0.5f
                );
            } catch (Exception ignored) {
                // Best-effort acceptance telemetry.
            }
            metricsCollector.recordEvent(
                    projectionEvent,
                    vehicleFrame == null ? 0L : vehicleFrame.sourceFrameId,
                    focusedPlateTrackId,
                    details
            );
            android.util.Log.d(
                    "ALPR_OVERLAY_MOTION",
                    String.format(
                            Locale.ROOT,
                            "anchor_ns=%d diagnostic_dx=%.5f candidate_dx=%.5f "
                                    + "vehicles=%d plates=%d tracked_vehicles=%d source=%s",
                            latestDiagnosticOverlayAnchorTimestampNanos,
                            diagnosticMotion.mapX(0.5f, 0.5f) - 0.5f,
                            candidateMotion.mapX(0.5f, 0.5f) - 0.5f,
                            countKind(projected, OverlayItem.Kind.VEHICLE),
                            countKind(projected, OverlayItem.Kind.PLATE),
                            countKind(motionTrackedVehicles, OverlayItem.Kind.VEHICLE),
                            freshGlobalVehicleGeometry
                                    ? "GLOBAL_FRAME_MOTION" : "LOCAL_KLT"
                    )
            );
        }
        return withZoomVehicleGeometry(projected);
    }

    private List<OverlayItem> globallyTrackedVehicleOverlayItems(
            List<OverlayItem> diagnostics,
            FrameMotionTransform accumulatedMotion
    ) {
        if (diagnostics == null
                || diagnostics.isEmpty()
                || accumulatedMotion == null
                || !accumulatedMotion.valid) {
            return java.util.Collections.emptyList();
        }
        List<OverlayItem> vehicles = new ArrayList<>();
        for (OverlayItem item : diagnostics) {
            if (item != null && item.kind == OverlayItem.Kind.VEHICLE) {
                vehicles.add(item);
            }
        }
        return entityOverlayMotionProjector.compensateInferenceLatency(
                vehicles,
                accumulatedMotion
        );
    }

    private List<OverlayItem> trackedVehicleOverlayItems(
            PreviewTrackingFrame frame
    ) {
        if (frame == null || frame.overlayItems.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<OverlayItem> vehicles = new ArrayList<>(frame.overlayItems.size());
        for (OverlayItem item : frame.overlayItems) {
            if (item == null || item.trackId <= 0L) continue;
            if(!experimentModeEnabled&&effectiveSceneHandlingMode()==com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY
                    &&item.carriedPrediction)continue;
            vehicles.add(new OverlayItem(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(item.normalizedBounds),
                    item.normalizedKeypoints,
                    item.label,
                    item.trackId,
                    false
            ));
        }
        return java.util.Collections.unmodifiableList(vehicles);
    }

    private boolean anchorOwnedPlatePreview(List<OverlayItem> items,int width,int height,ContinuityStamp source) {
        if(pipeline!=null&&!experimentModeEnabled&&effectiveSceneHandlingMode()==SceneHandlingMode.DYNAMIC_CONTINUITY) {
            long selected=pipeline.targetFocus().entityId;
            if(selected>0) {
                java.util.Set<Long> tracks=new java.util.HashSet<>();
                for(java.util.Map.Entry<Long,Long> owner:pipeline.vehicleIdentityPlateOwners().entrySet())
                    if(owner.getValue()==selected)tracks.add(owner.getKey());
                previewPlateTracker.retainOnly(tracks);
            } else previewPlateTracker.retainOnly(null);
        }
        Map<Long,com.example.alpr_v1.tracking.DynamicVehicleIdentity.Stamp> owners=
                pipeline==null?java.util.Collections.emptyMap():pipeline.vehicleIdentityPlateStamps(source);
        if(ordinaryDynamicResourcesEnabled()) {
            LumaFrame measured=presentingSourceLuma;
            return measured!=null&&source!=null&&measured.timestampNanos==source.sourceTimestampNanos
                    &&previewPlateTracker.anchorLuma(items,width,height,owners,measured.gray,measured.width,measured.height,source.sourceFrameStamp());
        }
        return previewPlateTracker.anchor(items,width,height,owners);
    }

    private List<OverlayItem> dynamicOwnedPlateItems(List<OverlayItem> items,
            Map<Long,com.example.alpr_v1.tracking.DynamicVehicleIdentity.Stamp> owners) {
        List<OverlayItem> result=new ArrayList<>();long selected=pipeline.targetFocus().entityId;
        if(items!=null)for(OverlayItem item:items) {
            if(item.kind!=OverlayItem.Kind.PLATE){result.add(item);continue;}
            com.example.alpr_v1.tracking.DynamicVehicleIdentity.Stamp owner=owners.get(item.trackId);
            if(owner!=null&&(selected<=0||owner.entityId==selected))result.add(item);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    private void applyDynamicOverlayDisposition(
            PreviewContinuityUiPolicy.DynamicOverlayDisposition disposition,
            List<OverlayItem> trackedItems
    ) {
        PreviewContinuityUiPolicy.DynamicOverlayDisposition safe = disposition == null
                ? PreviewContinuityUiPolicy.DynamicOverlayDisposition.KEEP
                : disposition;
        switch (safe) {
            case CLEAR:
                overlayView.clearPlateItems();
                overlayView.setStationaryScene(stationarySceneSupported());
                overlayView.setPreviewItems(java.util.Collections.emptyList());
                break;
            case PLATE_ONLY:
                overlayView.setStationaryScene(stationarySceneSupported());
                overlayView.setPreviewItems(plateOnlyOverlayItems(trackedItems));
                break;
            case KEEP_PREDICTED_VEHICLES:
                overlayView.setStationaryScene(stationarySceneSupported());
                overlayView.setPreviewItems(
                        dynamicCameraMotionOverlayItems(trackedItems)
                );
                break;
            case KEEP:
            default:
                if (isDynamicCameraMotion()) {
                    overlayView.setStationaryScene(stationarySceneSupported());
                    overlayView.setPreviewItems(
                            dynamicCameraMotionOverlayItems(trackedItems)
                    );
                }
                break;
        }
    }

    private void expireStalePlateOverlayIfNeeded() {
        if (latestPipelinePlateItems.isEmpty()) return;
        List<OverlayItem> retained = plateOverlayFreshness.retainDisplayable(
                latestPipelinePlateItems,
                android.os.SystemClock.elapsedRealtimeNanos(),
                plateOverlayMaximumAgeNanos()
        );
        if (retained.size() == latestPipelinePlateItems.size()) return;

        latestPipelinePlateItems = retained;
        overlayView.fadeOutPlateItems(retained);
        if (retained.isEmpty()) {
            JSONObject details = new JSONObject();
            try {
                details.put("overlay_entity_id", latestPipelinePlateEntityId);
                details.put("overlay_plate_track_id", targetStateMachine.snapshot().trackId);
                details.put("overlay_age_ms", PlateOverlayFreshness.MAXIMUM_AGE_NANOS
                        / 1_000_000.0);
            } catch (Exception ignored) {
                // Best-effort acceptance telemetry.
            }
            metricsCollector.recordEvent(
                    "overlay_plate_hidden_stale",
                    0L,
                    targetStateMachine.snapshot().trackId,
                    details
            );
            recordInfo("Ukryto nieświeżą ramkę tablicy");
        }
    }

    private static List<OverlayItem> plateOnlyOverlayItems(
            List<OverlayItem> items
    ) {
        if (items == null || items.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<OverlayItem> plates = new ArrayList<>();
        for (OverlayItem item : items) {
            if (item != null && item.kind == OverlayItem.Kind.PLATE) {
                plates.add(item);
            }
        }
        return java.util.Collections.unmodifiableList(plates);
    }

    private static List<OverlayItem> nonPlateOverlayItems(
            List<OverlayItem> items
    ) {
        if (items == null || items.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<OverlayItem> retained = new ArrayList<>();
        for (OverlayItem item : items) {
            if (item != null && item.kind != OverlayItem.Kind.PLATE) {
                retained.add(item);
            }
        }
        return java.util.Collections.unmodifiableList(retained);
    }

    static List<OverlayItem> preserveFreshScanVehicleItems(
            List<OverlayItem> current,
            List<OverlayItem> previous,
            boolean scanActive,
            boolean previousGeometryFresh
    ) {
        List<OverlayItem> safeCurrent = current == null
                ? java.util.Collections.emptyList() : current;
        if (!scanActive || !previousGeometryFresh
                || previous == null || previous.isEmpty()) {
            return safeCurrent;
        }
        List<OverlayItem> merged = new ArrayList<>(safeCurrent);
        Set<String> present = new HashSet<>();
        for (OverlayItem item : safeCurrent) {
            if (item != null && (item.kind == OverlayItem.Kind.VEHICLE
                    || item.kind == OverlayItem.Kind.VEHICLE_ROI)) {
                present.add(item.kind.name() + ":" + item.trackId);
            }
        }
        for (OverlayItem item : previous) {
            if (item == null || (item.kind != OverlayItem.Kind.VEHICLE
                    && item.kind != OverlayItem.Kind.VEHICLE_ROI)) continue;
            String key = item.kind.name() + ":" + item.trackId;
            if (present.add(key)) merged.add(item);
        }
        return java.util.Collections.unmodifiableList(merged);
    }

    private void recordOverlaySnapshot(
            String source,
            List<OverlayItem> items,
            long sourceFrameId,
            long sourceSequence,
            long nowNanos
    ) {
        if (metricsCollector == null) return;
        long safeNow = Math.max(0L, nowNanos);
        if (safeNow - lastOverlayTelemetryNanos < 250_000_000L) return;
        lastOverlayTelemetryNanos = safeNow;

        TargetSnapshot focused = targetStateMachine.snapshot();
        ScanAcquisitionSnapshot scan = pipeline == null
                ? null : pipeline.scanAcquisitionSnapshot();
        long plateTrackId = focused.trackId;
        boolean predicted = false;
        if (items != null) {
            for (OverlayItem item : items) {
                if (item != null && item.carriedPrediction) predicted = true;
                if (item != null && item.kind == OverlayItem.Kind.PLATE
                        && item.trackId > 0L) plateTrackId = item.trackId;
            }
        }

        JSONObject details = new JSONObject();
        try {
            details.put("overlay_revision", overlayPresentationRevision.incrementAndGet());
            details.put("overlay_source", source == null ? "UNKNOWN" : source);
            details.put("overlay_entity_id", latestPipelinePlateEntityId);
            details.put("overlay_plate_track_id", plateTrackId);
            details.put("overlay_age_ms",
                    plateOverlayFreshness.ageMillis(plateTrackId, safeNow));
            details.put("overlay_predicted", predicted);
            details.put("overlay_geometry_source_frame_id", sourceFrameId);
            details.put("overlay_geometry_source_sequence", sourceSequence);
            details.put("focused_entity_id", scan == null ? 0L : scan.activeEntityId);
            details.put("scan_presentation_entity_id", scanOverlayPresentationEntityId);
            details.put("scan_release_barrier_active", scan != null
                    && scan.directive.action
                    == AcquisitionDirectiveAction.RELEASE_ACTIVE_TARGET);
            details.put("vehicle_overlay_count",
                    countKind(items, OverlayItem.Kind.VEHICLE));
            details.put("plate_overlay_count",
                    countKind(items, OverlayItem.Kind.PLATE));
            details.put("roi_overlay_count",
                    countKind(items, OverlayItem.Kind.VEHICLE_ROI));
        } catch (Exception ignored) {
            // Best-effort acceptance telemetry.
        }
        metricsCollector.recordEvent(
                "overlay_snapshot",
                sourceFrameId,
                plateTrackId,
                details
        );

        if (geometryCalibrationEnabled && !geometryCalibrationEventRecorded) {
            geometryCalibrationEventRecorded = true;
            metricsCollector.recordEvent(
                    "overlay_geometry_calibration",
                    sourceFrameId,
                    plateTrackId,
                    details
            );
        }
    }

    private static int countKind(
            List<OverlayItem> items,
            OverlayItem.Kind kind
    ) {
        if (items == null || kind == null) return 0;
        int count = 0;
        for (OverlayItem item : items) {
            if (item != null && item.kind == kind) count++;
        }
        return count;
    }

    private static long firstObservationFrameId(
            List<PlateObservation> observations
    ) {
        if (observations == null) return 0L;
        for (PlateObservation observation : observations) {
            if (observation != null && observation.frameId > 0L) {
                return observation.frameId;
            }
        }
        return 0L;
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
        if (pipeline != null && effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY
                && (pipeline.foregroundEntityId() == 0L
                || pipeline.plateOwnerEntityId(autoZoomController.targetTrackId()) != pipeline.foregroundEntityId())) {
            hideAutoZoomTarget();
            return;
        }
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
        autoZoomOperationGeneration++;
        autoZoomPreferenceRevision++;
        autoZoomReturnInProgress = false;
        staticRefinementRequestPending = false;
        cancelPendingAutoZoomStart();
        if (overlayView != null) overlayView.setZoomPresentationActive(false);
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
        if (pipeline != null) deferPipelineControl(AlprPipeline::finishCameraTransform);
        if (liveHudRow != null) ((com.example.alpr_v1.ui.LiveHudView) liveHudRow).clearMetrics();
        if (autoZoomControl != null) autoZoomControl.setVisibility(View.VISIBLE);
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


        recognitionHistoryAdapter =
                new RecognitionHistoryAdapter(
                        this::showRecognitionHistoryDetails
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
        if (galleryBottomSheet != null
                && galleryBottomSheet.isShowing()) {
            return;
        }
        galleryResearchMode = experimentModeEnabled;
        View content = getLayoutInflater().inflate(
                R.layout.bottom_sheet_gallery,
                (ViewGroup) findViewById(android.R.id.content),
                false
        );
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(content);
        galleryBottomSheet = dialog;
        bindGallerySheet(content);
        dialog.setOnDismissListener(ignored -> {
            commitVerificationNote();
            clearGallerySheetReferences();
        });
        dialog.show();
        com.example.alpr_v1.ui.CameraWindowUi.install(dialog.getWindow(),content);
        View bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet
        );
        if (bottomSheet != null) {
            ViewGroup.LayoutParams parameters = bottomSheet.getLayoutParams();
            parameters.height = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomSheet.setLayoutParams(parameters);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setHideable(true);
            behavior.setDraggable(true);
        }
        renderCapturedCrops();
    }

    private void bindGallerySheet(View content) {
        View sessionExport = content.findViewById(R.id.gallery_export_crop_session);
        sessionExport.setVisibility(galleryResearchMode ? View.GONE : View.VISIBLE);
        sessionExport.setOnClickListener(view -> requestCropSessionExport());
        galleryResultsList = content.findViewById(R.id.gallery_sheet_list);
        galleryResultsEmpty = content.findViewById(R.id.gallery_sheet_empty);
        gallerySheetCount = content.findViewById(R.id.gallery_sheet_count);
        gallerySheetTitle = content.findViewById(R.id.gallery_sheet_title);
        gallerySheetModeLabel = content.findViewById(R.id.gallery_sheet_mode_label);
        galleryCollectionStats = content.findViewById(R.id.gallery_sheet_collection_stats);
        galleryRecentContainer = content.findViewById(R.id.gallery_recent_container);
        galleryResearchContainer = content.findViewById(R.id.gallery_research_container);
        if (verificationSessionId.isEmpty() && uiPreferences != null)
            verificationSessionId=uiPreferences.getString("gallery_session_id", "");
        verificationSessionPicker = new MaterialButton(this);
        verificationSessionPicker.setMaxLines(3);
        verificationSessionPicker.setOnClickListener(view -> showVerificationSessionPicker());
        ((ViewGroup) galleryResearchContainer).addView(verificationSessionPicker, 0);
        if (researchSessions != null) researchSessions.refreshGallerySessions();
        galleryClearHistoryButton = content.findViewById(R.id.gallery_clear_history);
        gallerySaveSelectedCropsButton = content.findViewById(
                R.id.gallery_sheet_save_selected
        );
        galleryResultsList.setLayoutManager(new LinearLayoutManager(
                this,
                RecyclerView.VERTICAL,
                false
        ));
        galleryResultsList.setAdapter(recognitionHistoryAdapter);
        galleryClearHistoryButton.setOnClickListener(view -> confirmClearRecognitionHistory());

        verificationEmpty = content.findViewById(R.id.verification_empty);
        verificationEmptyText = content.findViewById(R.id.verification_empty_text);
        verificationScroll = content.findViewById(R.id.verification_scroll);
        verificationCrop = content.findViewById(R.id.verification_crop);
        verificationShowBoxes = content.findViewById(R.id.verification_show_boxes);
        verificationStatus = content.findViewById(R.id.verification_status);
        verificationModelText = content.findViewById(R.id.verification_model_text);
        verificationConsensusText = content.findViewById(R.id.verification_consensus_text);
        verificationTiming = content.findViewById(R.id.verification_timing);
        verificationCharacters = content.findViewById(R.id.verification_characters);
        verificationMetrics = content.findViewById(R.id.verification_metrics);
        verificationAcceptButton = content.findViewById(R.id.verification_accept);
        verificationCorrectButton = content.findViewById(R.id.verification_correct);
        verificationRejectButton = content.findViewById(R.id.verification_reject);
        verificationSaveCurrentButton = content.findViewById(
                R.id.verification_save_current
        );
        verificationPreviousButton = content.findViewById(R.id.verification_previous);
        verificationNextButton = content.findViewById(R.id.verification_next);
        verificationDesktopReview = content.findViewById(
                R.id.verification_desktop_review
        );
        verificationNote = content.findViewById(R.id.verification_note);

        bindVerificationFilter(content, R.id.verification_filter_all, VerificationFilter.ALL);
        bindVerificationFilter(
                content,
                R.id.verification_filter_unreviewed,
                VerificationFilter.UNREVIEWED
        );
        bindVerificationFilter(
                content,
                R.id.verification_filter_changed,
                VerificationFilter.CHANGED
        );
        bindVerificationFilter(
                content,
                R.id.verification_filter_desktop,
                VerificationFilter.DESKTOP
        );
        verificationIssueChips.clear();
        bindVerificationIssue(content, R.id.issue_plate_not_visible, VerificationIssue.PLATE_NOT_VISIBLE);
        bindVerificationIssue(content, R.id.issue_mt_region, VerificationIssue.MT_REGION);
        bindVerificationIssue(content, R.id.issue_mt_corners, VerificationIssue.MT_CORNERS);
        bindVerificationIssue(content, R.id.issue_rectification, VerificationIssue.RECTIFICATION);
        bindVerificationIssue(content, R.id.issue_mz_missing, VerificationIssue.MZ_MISSING_CHARACTER);
        bindVerificationIssue(content, R.id.issue_mz_wrong, VerificationIssue.MZ_WRONG_CLASS);
        bindVerificationIssue(content, R.id.issue_blur, VerificationIssue.BLUR);
        bindVerificationIssue(content, R.id.issue_occlusion, VerificationIssue.OCCLUSION);
        bindVerificationIssue(
                content,
                R.id.issue_vehicle_association,
                VerificationIssue.VEHICLE_ASSOCIATION
        );
        bindVerificationIssue(content, R.id.issue_other, VerificationIssue.OTHER);

        verificationShowBoxes.setOnCheckedChangeListener(
                (button, checked) -> verificationCrop.setBoxesVisible(checked)
        );
        verificationAcceptButton.setOnClickListener(view -> {
            if (boundVerificationItem == null) return;
            commitVerificationNote();
            applyHumanVerification(
                    boundVerificationItem,
                    CapturedPlateItem.VerificationStatus.ACCEPTED,
                    ""
            );
            advanceAfterVerification();
        });
        verificationCorrectButton.setOnClickListener(view -> {
            if (boundVerificationItem != null) {
                commitVerificationNote();
                showCorrectionDialog(boundVerificationItem);
            }
        });
        verificationRejectButton.setOnClickListener(view -> {
            if (boundVerificationItem == null) return;
            commitVerificationNote();
            applyHumanVerification(
                    boundVerificationItem,
                    CapturedPlateItem.VerificationStatus.REJECTED,
                    ""
            );
            advanceAfterVerification();
        });
        verificationDesktopReview.setOnCheckedChangeListener((button, checked) -> {
            if (bindingVerificationControls || boundVerificationItem == null) return;
            boundVerificationItem.needsDesktopReview = checked;
            persistVerificationMetadata(boundVerificationItem);
            if (verificationFilter == VerificationFilter.DESKTOP) renderResearchVerification();
            else updateVerificationHeader();
        });
        verificationNote.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable value) {
                if (!bindingVerificationControls && boundVerificationItem != null) {
                    boundVerificationItem.verificationNote = value.toString();
                }
            }
        });
        verificationNote.setOnFocusChangeListener((view, focused) -> {
            if (!focused) commitVerificationNote();
        });
        verificationPreviousButton.setOnClickListener(view -> moveVerification(-1));
        verificationNextButton.setOnClickListener(view -> moveVerification(1));
        verificationSaveCurrentButton.setOnClickListener(view -> saveCurrentVerificationCrop());
        gallerySaveSelectedCropsButton.setOnClickListener(view -> saveReviewedCrops());
    }

    private void clearGallerySheetReferences() {
        galleryBottomSheet = null;
        galleryResultsList = null;
        galleryResultsEmpty = null;
        gallerySheetCount = null;
        gallerySheetTitle = null;
        gallerySheetModeLabel = null;
        galleryCollectionStats = null;
        gallerySaveSelectedCropsButton = null;
        galleryRecentContainer = null;
        galleryResearchContainer = null;
        galleryClearHistoryButton = null;
        verificationEmpty = null;
        verificationEmptyText = null;
        verificationScroll = null;
        verificationCrop = null;
        verificationShowBoxes = null;
        verificationStatus = null;
        verificationModelText = null;
        verificationConsensusText = null;
        verificationTiming = null;
        verificationCharacters = null;
        verificationMetrics = null;
        verificationAcceptButton = null;
        verificationCorrectButton = null;
        verificationRejectButton = null;
        verificationSaveCurrentButton = null;
        verificationPreviousButton = null;
        verificationNextButton = null;
        verificationDesktopReview = null;
        verificationNote = null;
        verificationIssueChips.clear();
        boundVerificationItem = null;
    }

    private void renderCollectionControl() {
        if (collectionToggle == null) return;
        boolean automatic = experimentModeEnabled || experimentSession.isRunning();
        boolean armed = normalCollectionArmed();
        collectionToggle.setEnabled(!automatic && !exportInProgress && !sessionWriteBusy());
        collectionToggle.setText(automatic ? R.string.camera_action_crops_automatic
                : armed ? R.string.camera_action_crops_on : R.string.camera_action_crops_off);
        collectionToggle.setActivated(armed || automatic);
        collectionToggle.setContentDescription(getString(automatic ? R.string.research_collection_automatic
                : armed ? (collectionActive ? R.string.collection_stop : R.string.collection_armed)
                        : R.string.collection_start));
        collectionToggle.setIconResource(R.drawable.ic_plate_crops_32);
        styleDashboardAction(collectionToggle, armed || automatic ? R.color.alpr_magenta : R.color.alpr_accent);
    }

    private void styleDashboardAction(MaterialButton button, int color) {
        tintCameraAction(button, color);
        button.setTooltipText(button.getContentDescription());
    }

    private boolean normalCollectionArmed() {
        return uiPreferences.getBoolean(KEY_COLLECTION_ARMED, false);
    }

    private void toggleCollection() {
        if (experimentModeEnabled || experimentSession.isRunning() || exportInProgress || sessionWriteBusy()) return;
        uiPreferences.edit().putBoolean(KEY_COLLECTION_ARMED, !normalCollectionArmed()).apply();
        applyNormalCollectionSelection();
        renderCapturedCrops();
    }

    private void onNormalAnalysisFrame(long generation) {
        if (!cameraStarted || generation != analysisControlGeneration || experimentSession.isRunning()) return;
        normalCollectionReady = true;
        applyNormalCollectionSelection();
    }

    private void applyNormalCollectionSelection() {
        if (experimentModeEnabled || experimentSession.isRunning()) return;
        boolean active = cameraStarted && normalCollectionReady && !exportInProgress && normalCollectionArmed();
        if (active == collectionActive) return;
        collectionActive = active;
        if (collectionActive) {
            captureGalleryState.beginCollectionWindow();
        }
        if (collectionActive && (collectionSessionId.isEmpty() || collectionSessionId.startsWith("exp-"))) {
            collectionSessionId = "s-" + new SimpleDateFormat(
                    "yyyyMMdd-HHmmss", Locale.ROOT
            ).format(new Date()) + "-" + UUID.randomUUID().toString().substring(0, 6);
            collectionSessionStartedElapsedNanos = android.os.SystemClock.elapsedRealtimeNanos();
            collectionSequence = 0;
            metricsCollector.startCropSession(collectionSessionId, resolvedCropLimit);
            recordInfo("Rozpoczęto sesję cropów " + collectionSessionId);
        } else if (collectionActive) {
            metricsCollector.setCropCollectionActive(true);
            recordInfo("Wznowiono sesję cropów " + collectionSessionId);
        } else {
            metricsCollector.setCropCollectionActive(false);
            recordInfo("Wstrzymano sesję cropów " + collectionSessionId);
        }
        if (collectionActive) importCachedReads();
        renderCapturedCrops();
    }

    private void recordStaleFinalResult(String phase, PipelineResult result) {
        if (result == null) return;
        String safePhase = phase == null || phase.trim().isEmpty()
                ? "unknown" : phase.trim();
        metricsCollector.finalResultDropped(safePhase);
        try {
            JSONObject details = new JSONObject();
            ContinuityStamp resultStamp = result.continuityStamp();
            AlprPipeline currentPipeline = pipeline;
            ContinuityStamp currentStamp = currentPipeline == null
                    ? null
                    : currentPipeline.currentContinuityStamp(
                            resultStamp.sourceFrameStamp()
                    );
            details.put("final_result_dispatch_status", "dropped");
            details.put("final_result_drop_phase", safePhase);
            details.put("drop_phase", safePhase);
            details.put(
                    "result_scene_generation", resultStamp.sceneGeneration
            );
            details.put("result_visual_epoch", resultStamp.visualEpoch);
            details.put(
                    "result_camera_transform_generation",
                    resultStamp.cameraTransformGeneration
            );
            details.put(
                    "current_scene_generation",
                    currentStamp == null
                            ? JSONObject.NULL : currentStamp.sceneGeneration
            );
            details.put(
                    "current_visual_epoch",
                    currentStamp == null
                            ? JSONObject.NULL : currentStamp.visualEpoch
            );
            details.put(
                    "current_camera_transform_generation",
                    currentStamp == null
                            ? JSONObject.NULL
                            : currentStamp.cameraTransformGeneration
            );
            details.put(
                    "source_timestamp_nanos", resultStamp.sourceTimestampNanos
            );
            details.put("source_sequence", resultStamp.sourceSequence);
            details.put(
                    "source_timestamp_domain",
                    resultStamp.sourceTimestampDomain.name()
            );
            details.put(
                    "current_source_sequence",
                    currentStamp == null
                            ? JSONObject.NULL : currentStamp.sourceSequence
            );
            metricsCollector.recordEvent(
                    "final_pipeline_result_dropped", 0L, 0L, details
            );
            android.util.Log.d(
                    "ALPR_FINAL_RESULT_GATE",
                    "dropped phase=" + safePhase
                            + " result_scene=" + resultStamp.sceneGeneration
                            + " result_visual=" + resultStamp.visualEpoch
                            + " result_transform="
                            + resultStamp.cameraTransformGeneration
                            + " current_scene="
                            + (currentStamp == null
                            ? "unavailable" : currentStamp.sceneGeneration)
                            + " current_visual="
                            + (currentStamp == null
                            ? "unavailable" : currentStamp.visualEpoch)
                            + " current_transform="
                            + (currentStamp == null
                            ? "unavailable"
                            : currentStamp.cameraTransformGeneration)
            );
        } catch (Exception error) {
            AppLog.warning(
                    this,
                    LOG_TAG,
                    "Nie udało się zapisać odrzucenia starego wyniku końcowego"
            );
        }
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
        for (PlateObservation observation : observations) {
            if (pipeline != null && !pipeline.isCurrentIdentityObservation(observation)) continue;
            if (!observation.hasFreshMzRead()) continue;
            if (!experimentModeEnabled) {
                String numberKey = RecognitionHistoryStore.numberKey(observation.freshMzAttempted
                        ? observation.freshPrediction : observation.text);
                if (numberKey.isEmpty() || captureGalleryState.previewNumberPending(numberKey)) continue;
                boolean alreadyStored = !numberKey.isEmpty() && capturedCrops.stream()
                        .anyMatch(crop -> numberKey.equals(RecognitionHistoryStore.numberKey(
                                crop.freshPrediction.isEmpty() ? crop.text : crop.freshPrediction)));
                if (alreadyStored) continue;
            }
            if (observation.previewBitmap == null || observation.previewBitmap.isRecycled()
                    || observation.timing == null) continue;
            if (!experimentModeEnabled && observation.identityOwner() != null) {
                com.example.alpr_v1.tracking.DynamicVehicleIdentity.Stamp owner = observation.identityOwner();
                String token = owner.entityId + ":" + owner.vehicleTrackId + ":" + owner.epoch;
                String old = normalCropIdentityOwners.put(observation.trackId, token);
                if (!token.equals(old)) lastCaptureByTrack.remove(observation.trackId);
            }
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
            final String sessionId = collectionSessionId;
            final long sessionStart = collectionSessionStartedElapsedNanos;
            final float zoom = currentCameraZoomRatio;
            final String source = autoZoomController.captureSource();
            final DeviceProfile device = deviceProfile;
            final ModelRegistry registry = modelRegistry;
            final AutoTuneManager tuning = autoTuneManager;
            final String profile = recognitionProfile.wireName();
            final String resolution = cameraResolutionSelection.wireName();
            final boolean cascade = vehicleCascadeEnabled, experiment = experimentModeEnabled;
            final RoiBudgetPolicy researchRoi = experimentRoiBudgetPolicy, effectiveRoi = effectiveRoiBudgetPolicy();
            final android.content.Context application = getApplicationContext();
            final com.example.alpr_v1.experiment.ResearchSampleIdentity researchIdentity = observation.researchIdentity;
            boolean queued = captureGalleryState.prepareCrop(() -> prepareCapturedCrop(observation, bitmap,
                    captureId, sessionId, sessionStart, zoom, source, device, registry, tuning,
                    profile, resolution, cascade, experiment, researchRoi, effectiveRoi,
                    application, researchIdentity), () -> recycleBitmap(bitmap), resolvedCropLimit,
                    experiment ? "" : RecognitionHistoryStore.numberKey(observation.freshMzAttempted
                            ? observation.freshPrediction : observation.text));
            if (!queued) continue; // Only a RAM preview is omitted; canonical evidence is already independent.
            lastCaptureByTrack.put(observation.trackId, new CropSamplingPolicy.Previous(
                    observation.text,
                    observation.confirmed,
                    observation.recognitionConfidence,
                    observation.sharpness,
                    observation.capturedElapsedNanos
            ));
        }
        retainCaptureGalleryState();
    }

    private static CapturedPlateItem prepareCapturedCrop(PlateObservation observation, Bitmap bitmap,
            String captureId, String sessionId, long sessionStart, float zoom, String captureSource,
            DeviceProfile deviceProfile, ModelRegistry modelRegistry, AutoTuneManager autoTuneManager,
            String profile, String resolution, boolean cascade, boolean experiment,
            RoiBudgetPolicy researchRoi, RoiBudgetPolicy effectiveRoi,
            android.content.Context application, com.example.alpr_v1.experiment.ResearchSampleIdentity researchIdentity) {
        CapturedPlateItem captured = new CapturedPlateItem(
                captureId,
                sessionId,
                observation.trackId,
                bitmap,
                observation.text,
                observation.plateConfidence,
                observation.freshRecognitionConfidence(),
                observation.confirmed,
                observation.characters,
                observation.capturedAtMillis,
                observation.capturedElapsedNanos,
                observation.sharpness,
                observation.timing,
                zoom,
                captureSource,
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
        captured.researchIdentity = researchIdentity;
        try {
            captured.miniReportJson = CropMiniReport.create(
                    captured,
                    sessionStart,
                    deviceProfile,
                    modelRegistry,
                    autoTuneManager,
                    profile,
                    resolution,
                    cascade,
                    experiment,
                    researchRoi,
                    effectiveRoi
            );
        } catch (Exception error) {
            AppLog.warning(application, LOG_TAG, "Nie udało się zamrozić miniraportu cropu");
        }
        return captured;
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
        if (experimentSession.isRunning()) collectionActive=true;
        if (recognitionHistoryAdapter != null) {
            recognitionHistoryAdapter.setItems(recognitionHistory.newestFirst());
        }
        if (galleryOpenButton != null) {
            galleryOpenButton.setText(experimentModeEnabled ? R.string.camera_action_review : R.string.camera_action_recent);
            galleryOpenButton.setContentDescription(experimentModeEnabled
                    ? getString(R.string.f16_gallery_open_count,currentResearchCropCount())
                    : getString(R.string.history_open_count,recognitionHistory.size()));
            String count = experimentModeEnabled ? currentResearchCropCount() : String.valueOf(recognitionHistory.size());
            TextView badge = findViewById(R.id.gallery_count_badge);
            badge.setText(String.valueOf(count));
            styleDashboardAction(galleryOpenButton, R.color.alpr_accent);
        }
        renderCollectionControl();
        if (galleryBottomSheet == null || !galleryBottomSheet.isShowing()
                || galleryRecentContainer == null || galleryResearchContainer == null) return;
        galleryResearchMode = experimentModeEnabled;
        galleryRecentContainer.setVisibility(galleryResearchMode ? View.GONE : View.VISIBLE);
        galleryResearchContainer.setVisibility(galleryResearchMode ? View.VISIBLE : View.GONE);
        if (galleryResearchMode) renderResearchVerification();
        else renderRecognitionHistory();
    }

    private void renderRecognitionHistory() {
        List<RecognitionHistoryItem> items = recognitionHistory.newestFirst();
        boolean empty = items.isEmpty();
        gallerySheetModeLabel.setText(R.string.history_mode_label);
        gallerySheetTitle.setText(R.string.history_title);
        gallerySheetCount.setText(String.valueOf(items.size()));
        galleryCollectionStats.setText(R.string.history_subtitle);
        galleryResultsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        galleryResultsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        galleryClearHistoryButton.setVisibility(empty ? View.GONE : View.VISIBLE);
        galleryClearHistoryButton.setEnabled(!empty);
        recognitionHistoryAdapter.setItems(items);
    }

    private void renderResearchVerification() {
        if (verificationScroll == null) return;
        filteredVerificationItems.clear();
        for (int index = capturedCrops.size() - 1; index >= 0; index--) {
            CapturedPlateItem item = capturedCrops.get(index);
            if (inVerificationScope(item) && matchesVerificationFilter(item)) filteredVerificationItems.add(item);
        }
        int selectedIndex = selectedVerificationIndex();
        if (selectedIndex < 0 && !filteredVerificationItems.isEmpty()) {
            selectedIndex = 0;
            selectedVerificationCaptureId = filteredVerificationItems.get(0).captureId;
        }
        boolean empty = selectedIndex < 0;
        verificationEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        boolean retainedInScope=false;
        for(CapturedPlateItem item:capturedCrops)if(inVerificationScope(item)){retainedInScope=true;break;}
        verificationEmptyText.setText(retainedInScope ? "Brak podglądów pasujących do filtra tej sesji."
                : getString(R.string.f16_gallery_empty_scope));
        verificationScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        verificationPreviousButton.setEnabled(!empty && selectedIndex > 0);
        verificationNextButton.setEnabled(
                !empty && selectedIndex + 1 < filteredVerificationItems.size()
        );
        boundVerificationItem = empty ? null : filteredVerificationItems.get(selectedIndex);
        bindVerificationItem(boundVerificationItem);
        updateVerificationHeader();
        gallerySaveSelectedCropsButton.setEnabled(
                pendingBatchWrites == 0 && hasReviewedCropToSave()
        );
    }

    private void bindVerificationItem(CapturedPlateItem item) {
        bindingVerificationControls = true;
        try {
            if (item == null) {
                persistedVerificationNote = "";
                return;
            }
            verificationCrop.setPlate(item.bitmap, item.characters);
            verificationCrop.setBoxesVisible(verificationShowBoxes.isChecked());
            verificationModelText.setText(item.text.isEmpty()
                    ? getString(R.string.result_placeholder) : item.text);
            verificationConsensusText.setText(item.consensusText.isEmpty()
                    ? getString(R.string.result_placeholder) : item.consensusText);
            verificationTiming.setTiming(item.timing);
            verificationCharacters.setText(characterBoxesText(item.characters));
            verificationMetrics.setText(getString(
                    R.string.verification_metrics_format,
                    percent(item.plateConfidence),
                    percent(item.recognitionConfidence),
                    item.sharpness,
                    item.mzAttemptIndex,
                    item.layout,
                    item.captureSource
            ));
            verificationMetrics.append("\nSesja: " + item.sessionId + "\nPróbka: " + item.captureId);
            int statusColor = R.color.alpr_text_muted;
            switch (item.verificationStatus) {
                case ACCEPTED:
                    verificationStatus.setText(R.string.verification_accepted);
                    statusColor = R.color.alpr_success;
                    break;
                case CORRECTED:
                    verificationStatus.setText(getString(
                            R.string.verification_corrected,
                            item.groundTruthText
                    ));
                    statusColor = R.color.alpr_primary;
                    break;
                case REJECTED:
                    verificationStatus.setText(R.string.verification_rejected);
                    statusColor = R.color.alpr_warning;
                    break;
                case NOT_REVIEWED:
                default:
                    verificationStatus.setText(R.string.verification_not_reviewed);
                    break;
            }
            verificationStatus.setTextColor(ContextCompat.getColor(this, statusColor));
            boolean saving = item.saveState == CapturedPlateItem.SaveState.SAVING;
            verificationAcceptButton.setEnabled(!experimentSession.isRunning() && !saving && !item.text.isEmpty());
            verificationCorrectButton.setEnabled(!experimentSession.isRunning() && !saving);
            verificationRejectButton.setEnabled(!experimentSession.isRunning() && !saving);
            for (Map.Entry<VerificationIssue, Chip> entry : verificationIssueChips.entrySet()) {
                entry.getValue().setEnabled(!saving);
                entry.getValue().setChecked(item.verificationIssues.contains(entry.getKey()));
            }
            verificationDesktopReview.setEnabled(!experimentSession.isRunning() && !saving);
            verificationNote.setEnabled(!experimentSession.isRunning() && !saving);
            verificationDesktopReview.setChecked(item.needsDesktopReview);
            if (!verificationNote.getText().toString().equals(item.verificationNote)) {
                verificationNote.setText(item.verificationNote);
            }
            persistedVerificationNote = item.verificationNote;
            verificationSaveCurrentButton.setEnabled(
                    pendingBatchWrites == 0 && isSelectableForSave(item)
            );
        } finally {
            bindingVerificationControls = false;
        }
    }

    private void updateVerificationHeader() {
        gallerySheetModeLabel.setText(R.string.verification_mode_label);
        gallerySheetTitle.setText(R.string.verification_title);
        int selectedIndex = selectedVerificationIndex();
        gallerySheetCount.setText(getString(
                R.string.verification_progress,
                selectedIndex < 0 ? 0 : selectedIndex + 1,
                filteredVerificationItems.size()
        ));
        int reviewed = 0;
        int retained = 0;
        for (CapturedPlateItem item : capturedCrops) {
            if (!inVerificationScope(item)) continue;
            retained++;
            if (item.verificationStatus != CapturedPlateItem.VerificationStatus.NOT_REVIEWED) {
                reviewed++;
            }
        }
        com.example.alpr_v1.capture.GallerySessionScope scope=verificationScope();
        galleryCollectionStats.setText(scope.header(retained,reviewed,resolvedCropLimit)
                + "\nFiltr: " + verificationFilterLabel());
        if(verificationSessionPicker!=null){
            verificationSessionPicker.setText(R.string.f16_gallery_choose_session);
            verificationSessionPicker.setContentDescription(getString(R.string.f16_gallery_session,scope.label()));
        }
    }

    private String verificationSessionId() {
        if(verificationSessionId.isEmpty() && uiPreferences!=null)
            verificationSessionId=uiPreferences.getString("gallery_session_id", "");
        if(!verificationSessionId.isEmpty())return verificationSessionId;
        if(experimentSession!=null && !experimentSession.sessionId().isEmpty())return experimentSession.sessionId();
        if(researchSessions!=null){
            java.util.List<com.example.alpr_v1.capture.GallerySessionScope> available=researchSessions.gallerySessions();
            for(com.example.alpr_v1.capture.GallerySessionScope entry:available)
                if(!"PREPARED".equals(entry.state)){verificationSessionId=entry.sessionId;return verificationSessionId;}
            if(!available.isEmpty()){verificationSessionId=available.get(0).sessionId;return verificationSessionId;}
        }
        return collectionSessionId==null ? "" : collectionSessionId;
    }
    private com.example.alpr_v1.capture.GallerySessionScope verificationScope() {
        String id=verificationSessionId();
        return researchSessions==null ? com.example.alpr_v1.capture.GallerySessionScope.unavailable(id,"UNKNOWN","Magazyn sesji niedostępny")
                : researchSessions.gallerySession(id);
    }
    private void selectVerificationSession(String id) {
        verificationSessionId=id;
        if(uiPreferences!=null)uiPreferences.edit().putString("gallery_session_id",id).apply();
        selectedVerificationCaptureId="";lastRenderedResearchCropCount="";
    }
    private void showVerificationSessionPicker() {
        java.util.LinkedHashMap<String,com.example.alpr_v1.capture.GallerySessionScope> entries=new java.util.LinkedHashMap<>();
        if(researchSessions!=null)for(com.example.alpr_v1.capture.GallerySessionScope scope:researchSessions.gallerySessions())entries.put(scope.sessionId,scope);
        for(CapturedPlateItem item:capturedCrops)if(!entries.containsKey(item.sessionId))
            entries.put(item.sessionId,com.example.alpr_v1.capture.GallerySessionScope.unavailable(item.sessionId,"UNKNOWN","Podglądy zachowane tylko w RAM"));
        if(entries.isEmpty()){Toast.makeText(this,"Brak dostępnych sesji",Toast.LENGTH_SHORT).show();return;}
        java.util.List<com.example.alpr_v1.capture.GallerySessionScope> choices=new java.util.ArrayList<>(entries.values());
        String[] labels=new String[choices.size()];int selected=-1;
        for(int i=0;i<labels.length;i++){labels[i]=choices.get(i).label();if(choices.get(i).contains(verificationSessionId()))selected=i;}
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this).setTitle(R.string.f16_gallery_choose_session)
                .setSingleChoiceItems(labels,selected,(dialog,index)->{
                    commitVerificationNote();selectVerificationSession(choices.get(index).sessionId);
                    renderResearchVerification();renderCapturedCrops();dialog.dismiss();
                }).setNegativeButton(R.string.menu_close,null).show();
    }
    private boolean inVerificationScope(CapturedPlateItem item) {return verificationScope().contains(item.sessionId);}
    private String currentResearchCropCount() {return verificationScope().countText();}

    private String lastRenderedResearchCropCount = "";
    private void refreshResearchCropCount() {
        if (!experimentModeEnabled) return;
        String count = currentResearchCropCount();
        String revision=verificationScope().label()+":"+count;
        if (revision.equals(lastRenderedResearchCropCount)) return;
        lastRenderedResearchCropCount = revision;
        TextView badge = findViewById(R.id.gallery_count_badge);
        badge.setText(String.valueOf(count));
        galleryOpenButton.setContentDescription(getString(R.string.f16_gallery_open_count,count));
        if (galleryBottomSheet != null && galleryBottomSheet.isShowing()) updateVerificationHeader();
    }

    private void bindVerificationFilter(View content, int viewId, VerificationFilter filter) {
        Chip chip = content.findViewById(viewId);
        chip.setChecked(verificationFilter == filter);
        chip.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) return;
            commitVerificationNote();
            verificationFilter = filter;
            selectedVerificationCaptureId = "";
            renderResearchVerification();
        });
    }

    private void bindVerificationIssue(View content, int viewId, VerificationIssue issue) {
        Chip chip = content.findViewById(viewId);
        verificationIssueChips.put(issue, chip);
        chip.setOnCheckedChangeListener((button, checked) -> {
            if (bindingVerificationControls || boundVerificationItem == null) return;
            if (checked) boundVerificationItem.verificationIssues.add(issue);
            else boundVerificationItem.verificationIssues.remove(issue);
            if (checked && issue.desktopReviewRecommended()) {
                boundVerificationItem.needsDesktopReview = true;
                bindingVerificationControls = true;
                verificationDesktopReview.setChecked(true);
                bindingVerificationControls = false;
            }
            persistVerificationMetadata(boundVerificationItem);
            if (verificationFilter == VerificationFilter.DESKTOP) renderResearchVerification();
            else updateVerificationHeader();
        });
    }

    private boolean matchesVerificationFilter(CapturedPlateItem item) {
        switch (verificationFilter) {
            case UNREVIEWED:
                return item.verificationStatus
                        == CapturedPlateItem.VerificationStatus.NOT_REVIEWED;
            case CHANGED:
                return item.verificationStatus == CapturedPlateItem.VerificationStatus.CORRECTED
                        || item.verificationStatus
                        == CapturedPlateItem.VerificationStatus.REJECTED;
            case DESKTOP:
                return item.needsDesktopReview;
            case ALL:
            default:
                return true;
        }
    }

    private String verificationFilterLabel() {
        switch (verificationFilter) {
            case UNREVIEWED:
                return getString(R.string.verification_filter_unreviewed);
            case CHANGED:
                return getString(R.string.verification_filter_changed);
            case DESKTOP:
                return getString(R.string.verification_filter_desktop);
            case ALL:
            default:
                return getString(R.string.verification_filter_all);
        }
    }

    private int selectedVerificationIndex() {
        for (int index = 0; index < filteredVerificationItems.size(); index++) {
            if (filteredVerificationItems.get(index).captureId.equals(
                    selectedVerificationCaptureId
            )) return index;
        }
        return -1;
    }

    private void moveVerification(int offset) {
        commitVerificationNote();
        int current = selectedVerificationIndex();
        if (current < 0) return;
        int next = Math.max(0, Math.min(
                filteredVerificationItems.size() - 1,
                current + offset
        ));
        selectedVerificationCaptureId = filteredVerificationItems.get(next).captureId;
        renderResearchVerification();
        verificationScroll.scrollTo(0, 0);
    }

    private void advanceAfterVerification() {
        if (verificationFilter == VerificationFilter.ALL) moveVerification(1);
    }

    private void commitVerificationNote() {
        if (boundVerificationItem == null) return;
        String normalized = boundVerificationItem.verificationNote == null
                ? "" : boundVerificationItem.verificationNote.trim();
        boundVerificationItem.verificationNote = normalized;
        if (normalized.equals(persistedVerificationNote)) return;
        persistedVerificationNote = normalized;
        persistVerificationMetadata(boundVerificationItem);
    }

    private void persistVerificationMetadata(CapturedPlateItem item) {
        HumanVerificationEditor.touchMetadata(item, System.currentTimeMillis());
        persistVerificationState(item);
    }

    private void persistVerificationState(CapturedPlateItem item) {
        metricsCollector.markHumanVerification(item);
        refreshPersistedVerification(item);
    }

    private boolean hasReviewedCropToSave() {
        for (CapturedPlateItem item : capturedCrops) {
            if (item.verificationStatus != CapturedPlateItem.VerificationStatus.NOT_REVIEWED
                    && isSelectableForSave(item)) return true;
        }
        return false;
    }

    private String characterBoxesText(List<PlateCharacter> characters) {
        if (characters == null || characters.isEmpty()) {
            return getString(R.string.gallery_character_boxes_empty);
        }
        StringBuilder values = new StringBuilder();
        for (PlateCharacter character : characters) {
            if (character == null) continue;
            if (values.length() > 0) values.append(" · ");
            values.append(character.label.isEmpty() ? "?" : character.label)
                    .append(' ')
                    .append(percent(character.confidence))
                    .append('%');
        }
        return getString(R.string.gallery_character_boxes, values.toString());
    }

    private static int percent(double confidence) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, confidence)) * 100.0);
    }

    private void applyHumanVerification(
            CapturedPlateItem item,
            CapturedPlateItem.VerificationStatus status,
            String correctedText
    ) {
        HumanVerificationEditor.applyStatus(
                item,
                status,
                correctedText,
                System.currentTimeMillis()
        );
        persistVerificationState(item);
        renderResearchVerification();
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
        try {
            backgroundExecutor.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(
                        item.savedReportUri, "wt"
                )) {
                    if (output == null) {
                        throw new IllegalStateException("Brak strumienia raportu cropu");
                    }
                    output.write(refreshed.getBytes(StandardCharsets.UTF_8));
                } catch (Exception error) {
                    AppLog.error(
                            this,
                            LOG_TAG,
                            "Nie udało się zapisać manualnej walidacji cropu: "
                                    + error.getMessage(),
                            error
                    );
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Activity kończy pracę; odświeżony JSON pozostaje w stanie ViewModelu.
        }
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
        androidx.appcompat.app.AlertDialog dialog = new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
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
        captureGalleryState.invalidatePendingCropPreviews();
        collectionActive = false;
        for (CapturedPlateItem item : capturedCrops) item.recycle();
        capturedCrops.clear();
        lastCaptureByTrack.clear();
        collectionSessionId = "";
        collectionSequence = 0;
        metricsCollector.clearCropSession();
        renderCapturedCrops();
    }

    private void publishGalleryObservation(
            PlateObservation observation,
            long sceneGeneration,
            long transformGeneration,
            long presentationGeneration
    ) {
        if (!cameraStarted || pipeline == null
                || !pipeline.isCurrentContinuityStamp(observation.continuityStamp())
                || !pipeline.isCurrentIdentityObservation(observation)) return;
        PlateObservation snapshot = observation.copyForGallery();
        if (snapshot == null) return;
        runOnUiThread(() -> {
            try {
                if (!cameraStarted || pipeline == null
                        || sceneGeneration != uiSceneGeneration.get()
                        || transformGeneration != uiCameraTransformGeneration.get()
                        || !previewPresentationBarrier.permits(presentationGeneration)
                        || !pipeline.isCurrentContinuityStamp(snapshot.continuityStamp())
                        || !pipeline.isCurrentIdentityObservation(snapshot)) return;
                refreshVehicleIdentityPresentation();
                List<PlateObservation> crops = java.util.Collections.singletonList(snapshot);
                overlayView.setPresentationStamp(snapshot.continuityStamp());
                overlayView.animatePlateObservation(snapshot);
                collectRecognitionHistory(crops);
                collectCrops(crops);
                android.util.Log.d("ALPR_GALLERY", "mz_crop_published frame=" + snapshot.frameId
                        + " entity=" + snapshot.entityId + " chars=" + snapshot.characters.size()
                        + " plate_track=" + snapshot.plateTrackId + " epoch=" + snapshot.visualEpoch
                        + " association=" + snapshot.associationReason
                        + " confirmed=" + snapshot.confirmed + " dispatch_ms="
                        + (android.os.SystemClock.elapsedRealtimeNanos()
                        - snapshot.capturedElapsedNanos) / 1_000_000L);
            } finally {
                snapshot.previewBitmap.recycle();
            }
        });
    }

    private void collectRecognitionHistory(List<PlateObservation> observations) {
        if (experimentModeEnabled
                || observations == null || observations.isEmpty()) return;
        boolean changed = false;
        if(ordinaryDynamicResourcesEnabled()&&pipeline!=null) {
            com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot snapshot=
                    pipeline.dynamicDisplaySnapshot(currentPreviewSourceFrameStamp().continuityStamp());
            if(snapshot!=null)changed=recognitionHistory.promoteDynamicOwners(snapshot.analysisRunId,snapshot.sceneGeneration,
                    snapshot.canonicalOwners,snapshot.identities);
        }
        long telemetryAt = android.os.SystemClock.elapsedRealtimeNanos();
        com.example.alpr_v1.capture.ObservationTelemetry telemetry =
                new com.example.alpr_v1.capture.ObservationTelemetry(
                        liveCameraFrameRate.rate(telemetryAt),
                        latestThermalSnapshot == null ? Double.NaN : latestThermalSnapshot.batteryTemperatureC,
                        liveHudRow == null ? Double.NaN : ((com.example.alpr_v1.ui.LiveHudView) liveHudRow).cpuPercent(),
                        telemetryAt, pipeline == null ? java.util.Collections.emptyList() : pipeline.activeModelRuntimeSummaries());
        for (PlateObservation observation : observations) {
            if (pipeline != null && !pipeline.isCurrentIdentityObservation(observation)) continue;
            String source = autoZoomController.captureSource();
            captureGalleryState.recentReads.remember(observation, telemetry, source);
            changed |= storeCollectedRead(observation, telemetry, source);
        }
        if (changed) renderCapturedCrops();
    }

    private boolean storeCollectedRead(PlateObservation observation,
            com.example.alpr_v1.capture.ObservationTelemetry telemetry, String source) {
        if (!collectionActive || experimentModeEnabled || observation == null
                || !observation.hasFreshMzRead()) return false;
        if (!collectionSessionId.isEmpty()) {
            captureGalleryState.cropSessions(this).record(collectionSessionId, observation, telemetry,
                    source).whenComplete((ignored, error) -> {
                if (error != null) captureGalleryState.cropSessionMessages.postValue(
                        getString(R.string.crop_session_capture_failed, error.getMessage()));
            });
        }
        return recognitionHistory.upsertObservation(observation, telemetry,
                effectiveSceneHandlingMode() == SceneHandlingMode.DYNAMIC_CONTINUITY, source,collectionSessionId);
    }

    private void importCachedReads() {
        for (com.example.alpr_v1.capture.RecentReadCache.Entry entry
                : captureGalleryState.recentReads.entries()) {
            storeCollectedRead(entry.observation, entry.telemetry, entry.source);
            collectCrops(java.util.Collections.singletonList(entry.observation));
        }
    }

    private void confirmClearRecognitionHistory() {
        if (recognitionHistory.size() == 0) return;
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_message)
                .setNegativeButton(R.string.menu_close, null)
                .setPositiveButton(R.string.history_clear_action, (dialog, which) -> {
                    recognitionHistory.clear();
                    renderCapturedCrops();
                })
                .show();
    }

    private void showRecognitionHistoryDetails(RecognitionHistoryItem sourceItem) {
        RecognitionHistoryItem item = sourceItem == null ? null : sourceItem.snapshot();
        if (item == null) return;
        View content = getLayoutInflater().inflate(
                R.layout.dialog_recognition_history_detail,
                (ViewGroup) findViewById(android.R.id.content),
                false
        );
        PlateCropView preview = content.findViewById(R.id.history_detail_preview);
        TextView number = content.findViewById(R.id.history_detail_number);
        TextView meta = content.findViewById(R.id.history_detail_meta);
        MaterialButton copy = content.findViewById(R.id.history_detail_copy);
        MaterialButton save = content.findViewById(R.id.history_detail_save);
        MaterialButton delete = content.findViewById(R.id.history_detail_delete);
        ((TextView) content.findViewById(R.id.history_detail_characters)).setText(characterBoxesText(item.characters));
        preview.setPlate(item.previewBitmap, item.characters);
        preview.setBoxesVisible(!item.dynamicOwner);
        content.findViewById(R.id.history_detail_characters).setVisibility(item.dynamicOwner?View.GONE:View.VISIBLE);
        number.setText(item.displayText().isEmpty() ? getString(R.string.result_placeholder) : item.displayText());
        copy.setEnabled(!(item.dynamicOwner?item.currentText:item.text).isEmpty());
        com.example.alpr_v1.ui.RecognitionEntityPager attempts=content.findViewById(R.id.history_detail_entities);
        meta.setText(getString(
                R.string.history_detail_full,
                getString(
                        R.string.history_detail_meta,
                        new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(
                                new Date(item.capturedAtMillis)
                        ),
                        percent(item.confidence)
                ),
                getResources().getQuantityString(
                        R.plurals.history_observation_count,
                        Math.max(1, item.observations),
                        Math.max(1, item.observations)
                )
        ));
        if(item.dynamicOwner)meta.setText(getString(R.string.dynamic_history_best_detail,percent(item.currentConfidence),
                Math.max(1,item.observations)));
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        android.app.Dialog[] rawPhoto={null};java.util.concurrent.atomic.AtomicBoolean photoLoading=new java.util.concurrent.atomic.AtomicBoolean();
        if(item.dynamicOwner)attempts.setPhotoListener(observation->showHistoryObservationPhoto(observation,
                item.currentText,item.currentConfidence,dialog,rawPhoto,photoLoading));
        attempts.setItem(item);
        content.findViewById(R.id.history_detail_close).setOnClickListener(view -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if(rawPhoto[0]!=null)rawPhoto[0].dismiss();
            preview.setPlate(null, java.util.Collections.emptyList());
            item.close();
        });
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(
                    CLIPBOARD_SERVICE
            );
            if (clipboard != null) {
                String copied=item.dynamicOwner?item.currentText:item.text;
                clipboard.setPrimaryClip(ClipData.newPlainText("plate_number", copied));
                Toast.makeText(
                        this,
                        getString(R.string.history_copied, copied),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        save.setOnClickListener(view -> saveRecognitionHistoryImage(item));
        delete.setOnClickListener(view -> {
            recognitionHistory.remove(item.historyId);
            dialog.dismiss();
            renderCapturedCrops();
        });
        dialog.show();
        android.view.Window detailWindow = dialog.getWindow();
        if (detailWindow != null) {
            detailWindow.setBackgroundDrawableResource(R.color.alpr_background);
            com.example.alpr_v1.ui.CameraWindowUi.install(detailWindow,content);
            detailWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void showHistoryObservationPhoto(com.example.alpr_v1.capture.RecognitionHistoryObservation observation,
            String bestReading,double bestConfidence,android.app.Dialog parent,android.app.Dialog[] current,
            java.util.concurrent.atomic.AtomicBoolean loading) {
        if(!loading.compareAndSet(false,true))return;
        captureGalleryState.cropSessions(this).previewForObservation(observation.captureSessionId,observation.key())
                .whenComplete((photo,error)->runOnUiThread(()->{
                    loading.set(false);
                    if(!parent.isShowing()) {if(photo!=null)photo.close();return;}
                    if(error!=null||photo==null) {
                        Toast.makeText(this,R.string.dynamic_history_photo_unavailable,Toast.LENGTH_SHORT).show();return;
                    }
                    if(current[0]!=null)current[0].dismiss();
                    View content=getLayoutInflater().inflate(R.layout.dialog_recognition_history_detail,null);
                    PlateCropView image=content.findViewById(R.id.history_detail_preview);
                    image.setPlate(photo.bitmap,photo.characters);image.setBoxesVisible(false);
                    ((TextView)content.findViewById(R.id.history_detail_number)).setText(bestReading);
                    ((TextView)content.findViewById(R.id.history_detail_meta)).setText(getString(R.string.dynamic_history_best_photo,
                            new SimpleDateFormat("HH:mm:ss.SSS",Locale.getDefault()).format(new Date(photo.capturedAtMillis)),percent(bestConfidence)));
                    for(int id:new int[]{R.id.history_detail_entities,R.id.history_detail_copy,R.id.history_detail_save,R.id.history_detail_delete,
                            R.id.history_detail_characters})content.findViewById(id).setVisibility(View.GONE);
                    android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);dialog.setContentView(content);
                    content.findViewById(R.id.history_detail_close).setOnClickListener(v->dialog.dismiss());
                    dialog.setOnDismissListener(ignored->{image.setPlate(null,java.util.Collections.emptyList());photo.close();if(current[0]==dialog)current[0]=null;});
                    current[0]=dialog;
                    try {dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}
                    catch(RuntimeException staleWindow){current[0]=null;photo.close();}
                }));
    }

    private void saveRecognitionHistoryImage(RecognitionHistoryItem item) {
        if (item.previewBitmap == null || item.previewBitmap.isRecycled()) return;
        Bitmap snapshot = item.previewBitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (snapshot == null) return;
        try {
            backgroundExecutor.execute(() -> {
                Uri imageUri = null;
                try {
                    String timestamp = new SimpleDateFormat(
                            "yyyyMMdd_HHmmss_SSS",
                            Locale.ROOT
                    ).format(new Date(item.capturedAtMillis));
                    String text = (item.dynamicOwner?item.currentText:item.text).replaceAll("[^A-Za-z0-9_-]", "");
                    if (text.isEmpty()) text = "odczyt";
                    imageUri = createDocument(
                            "image/jpeg",
                            timestamp + "_" + text + "_history.jpg"
                    );
                    try (OutputStream output = getContentResolver().openOutputStream(
                            imageUri,
                            "wt"
                    )) {
                        if (output == null
                                || !snapshot.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                            throw new IllegalStateException("Nie udało się zapisać obrazu");
                        }
                    }
                    CaptureDirectoryStore.publish(getContentResolver(), imageUri);
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            R.string.history_saved,
                            Toast.LENGTH_SHORT
                    ).show());
                } catch (Exception error) {
                    deleteCreatedDocument(imageUri);
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            R.string.history_save_failed,
                            Toast.LENGTH_LONG
                    ).show());
                } finally {
                    snapshot.recycle();
                }
            });
        } catch (RejectedExecutionException rejected) {
            snapshot.recycle();
        }
    }

    private void saveCurrentVerificationCrop() {
        commitVerificationNote();
        if (boundVerificationItem == null || !isSelectableForSave(boundVerificationItem)) {
            return;
        }
        saveCropBatch(java.util.Collections.singletonList(boundVerificationItem));
    }

    private void saveReviewedCrops() {
        commitVerificationNote();
        List<CapturedPlateItem> reviewed = new ArrayList<>();
        for (CapturedPlateItem item : capturedCrops) {
            if (item.verificationStatus != CapturedPlateItem.VerificationStatus.NOT_REVIEWED
                    && isSelectableForSave(item)) {
                reviewed.add(item);
            }
        }
        if (reviewed.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.verification_no_reviewed_to_save,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        saveCropBatch(reviewed);
    }

    private void saveCropBatch(List<CapturedPlateItem> items) {
        pendingBatchWrites = items.size();
        successfulBatchWrites = 0;
        failedBatchWrites = 0;
        for (CapturedPlateItem item : items) {
            item.selectedForSave = false;
            saveCapturedCrop(item);
        }
        renderCapturedCrops();
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
        backgroundExecutor.execute(() -> {
            Uri imageUri = null;
            Uri reportUri = null;
            try {
                String baseName = captureFileBaseName(item);
                imageUri = createDocument("image/jpeg", baseName + ".jpg");
                try (OutputStream output = getContentResolver().openOutputStream(imageUri, "wt")) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                        throw new IllegalStateException("Nie udało się zapisać obrazu JPEG");
                    }
                }
                reportUri = createDocument("application/json", baseName + ".json");
                try (OutputStream output = getContentResolver().openOutputStream(reportUri, "wt")) {
                    if (output == null) throw new IllegalStateException("Nie można otworzyć raportu");
                    output.write(report.getBytes(StandardCharsets.UTF_8));
                }
                CaptureDirectoryStore.publish(getContentResolver(), imageUri);
                CaptureDirectoryStore.publish(getContentResolver(), reportUri);
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
        renderCapturedCrops();
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
            getContentResolver().delete(uri, null, null);
        } catch (Exception ignored) {
            // Plik był tworzony wyłącznie przez bieżącą próbę; provider mógł już go usunąć.
        }
    }

    private Uri createDocument(String mimeType, String name) {
        Uri created = CaptureDirectoryStore.createDocument(
                getContentResolver(),
                mimeType,
                name
        );
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
                } catch (CancellationException cancelled) {
                    return;
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

    private void requestCropSessionExport() {
        if (captureGalleryState.cropSessionExportBusy) {
            Toast.makeText(this, R.string.crop_session_writing, Toast.LENGTH_SHORT).show(); return;
        }
        captureGalleryState.cropSessionExportBusy = true;
        Toast.makeText(this, R.string.crop_session_loading, Toast.LENGTH_SHORT).show();
        captureGalleryState.cropSessions(this).sessions().whenComplete((sessions, error) -> runOnUiThread(() -> {
            captureGalleryState.cropSessionExportBusy = false;
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, getString(R.string.crop_session_failed,error.getMessage()),Toast.LENGTH_LONG).show(); return;
            }
            if (sessions.isEmpty()) {
                Toast.makeText(this,R.string.crop_session_empty,Toast.LENGTH_LONG).show(); return;
            }
            String[] labels = new String[sessions.size()];
            for (int index=0;index<labels.length;index++) {
                com.example.alpr_v1.capture.CropSessionStore.Summary session = sessions.get(index);
                labels[index] = getString(R.string.crop_session_row,
                        new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date(session.startedAtMillis)),
                        session.crops, session.observations);
            }
            new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this).setTitle(R.string.crop_session_choose)
                    .setItems(labels,(dialog,index) -> {
                        String id = sessions.get(index).id;
                        captureGalleryState.pendingCropSessionExport = id;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"alpr_cropy_"+id+".zip");
                        cropSessionDestination.launch(intent);
                    }).setNegativeButton(R.string.menu_close,null).show();
        }));
    }

    private void writeCropSessionExport(String sessionId, Uri destination) {
        final CaptureGalleryViewModel state = captureGalleryState;
        final android.content.Context app = getApplicationContext();
        state.cropSessionExportBusy = true;
        Toast.makeText(this,R.string.crop_session_writing,Toast.LENGTH_SHORT).show();
        state.cropSessions(app).export(sessionId, () -> app.getContentResolver().openOutputStream(destination,"w"))
                .whenComplete((count,error) -> {
                    state.cropSessionExportBusy = false;
                    if (error != null) {
                        try { android.provider.DocumentsContract.deleteDocument(app.getContentResolver(),destination); }
                        catch (Exception ignored) { }
                    }
                    state.cropSessionMessages.postValue(error == null ? app.getString(R.string.crop_session_saved,count)
                            : app.getString(R.string.crop_session_failed,error.getMessage()));
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
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
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
        if(researchArchivePickerLoading)return;
        if(kind==ResearchArchive.Kind.RESEARCH_SESSION){
            if(pendingExportKind!=null){Toast.makeText(this,"Dokończ lub anuluj otwarte okno zapisu",Toast.LENGTH_LONG).show();return;}
            researchArchivePickerLoading=true;
            backgroundExecutor.execute(()->{
                List<com.example.alpr_v1.experiment.ResearchSessionSummary> sessions=com.example.alpr_v1.experiment.ResearchSessionSummary.catalog(
                        com.example.alpr_v1.experiment.ResearchSessionStore.sessionsRoot(getApplicationContext()));
                runOnUiThread(()->{
                    researchArchivePickerLoading=false;if(isFinishing()||isDestroyed())return;
                    com.example.alpr_v1.ui.ResearchSessionPicker.showHistory(this,sessions,researchSessions::operationHistoryDescription,selected->{
                        if(!researchDocumentDestinationAvailable())return;
                        if(selected.isFile()){pendingPersistentResearchArchive=selected;launchExportDestination(kind);return;}
                        researchArchivePickerLoading=true;
                        researchSessions.recoverArchive(selected).whenComplete((archive,error)->runOnUiThread(()->{
                            researchArchivePickerLoading=false;if(isFinishing()||isDestroyed())return;
                            if(error!=null){new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this).setTitle("Nie utworzono eksportu; pliki sesji zachowano")
                                    .setMessage(com.example.alpr_v1.experiment.ResearchOperationState.chain(error)).setPositiveButton("OK",null).show();return;}
                            pendingPersistentResearchArchive=archive;launchExportDestination(kind);
                        }));
                    },backgroundExecutor);
                });
            });
            return;
        }
        if(experimentSession.isRunning()||researchSessions.finalizing()){Toast.makeText(this,researchSessions.operationDescription(),Toast.LENGTH_LONG).show();return;}
        pendingPersistentResearchArchive=null;launchExportDestination(kind);
    }

    /** Browsing history stays available; leaving for the file picker must not interrupt a live run. */
    private boolean researchDocumentDestinationAvailable(){
        if(!experimentSession.isRunning()&&!sessionWriteBusy()&&!analysisStartGate.preparing()&&!experimentStartRequest.pending())return true;
        String message=experimentSession.isRunning()?"Trwa pomiar "+experimentSession.snapshot().sessionId+". Dokument można zapisać po STOP i zakończeniu zapisu sesji.":researchSessions.operationDescription();
        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this).setTitle("Historia dostępna — zapis dokumentu oczekuje").setMessage(message).setPositiveButton("OK",null).show();return false;
    }

    private void launchExportDestination(ResearchArchive.Kind kind) {
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
        if (kind == ResearchArchive.Kind.RESEARCH_SESSION) {
            java.io.File source=pendingPersistentResearchArchive;pendingPersistentResearchArchive=null;
            if(source==null){Toast.makeText(this,"Brak zachowanego wyboru sesji. Otwórz Sesje / Eksport; nie tworzono nowego pomiaru.",Toast.LENGTH_LONG).show();return;}
            researchSessions.exportArchive(source,destination);renderAnalysisControls();return;
        }
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
                                modelRegistry,
                                experimentSnapshot.frozenExecutionConfig
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

    private void configureSceneModeHudToggle() {
        if (liveSceneModeToggle == null) return;
        liveSceneModeToggle.setOnClickListener(view -> toggleSceneHandlingModeFromHud());
        renderSceneModeHudToggle();
    }

    private void toggleSceneHandlingModeFromHud() {
        if (analysisStartGate.preparing() || sessionWriteBusy()) return;
        if (!SceneModeHudPolicy.isToggleEnabled(experimentModeEnabled || experimentSession.isRunning())) {
            livePresentation.showTransient(
                    getString(R.string.scene_mode_experiment_locked_hud)
            );
            renderSceneModeHudToggle();
            return;
        }

        SceneHandlingMode nextMode = SceneModeHudPolicy.nextUserMode(
                effectiveSceneHandlingMode()
        );
        int nextRevision = uiPreferences.getInt(
                SettingsActivity.KEY_REVISION,
                0
        ) + 1;
        uiPreferences.edit()
                .putString(
                        SettingsActivity.KEY_SCENE_HANDLING_MODE,
                        nextMode.wireName()
                )
                .putInt(SettingsActivity.KEY_REVISION, nextRevision)
                .apply();
        knownSettingsRevision = nextRevision;

        if (cameraStarted) activateAbruptScenePresentationBarrier(0f);
        pipeline.setSceneHandlingMode(nextMode);
        if (cameraStarted) invalidateUiForPreviewSceneChange(0f, 0f);
        previewSceneAnchorPending = true;
        renderSceneModeHudToggle();
        livePresentation.showTransient(getString(
                nextMode == SceneHandlingMode.DYNAMIC_CONTINUITY
                        ? R.string.scene_mode_changed_dynamic
                        : R.string.scene_mode_changed_static
        ));
        recordInfo("Zmieniono tryb sceny z HUD: " + nextMode.wireName());
    }

    private void renderSceneModeHudToggle() {
        renderSearchControls();
        if (liveSceneModeToggle == null) return;
        SceneHandlingMode mode = effectiveSceneHandlingMode();
        boolean dynamic = mode == SceneHandlingMode.DYNAMIC_CONTINUITY;
        overlayView.setDynamicVehicleMotion(dynamic);
        boolean enabled = !sessionWriteBusy() && !analysisStartGate.preparing()
                && SceneModeHudPolicy.isToggleEnabled(experimentModeEnabled || experimentSession.isRunning());
        int icon = dynamic
                ? R.drawable.ic_scene_dynamic_24
                : R.drawable.ic_scene_static_24;
        int tint = dynamic ? R.color.alpr_accent : R.color.alpr_warning;
        int description = !enabled
                ? R.string.scene_mode_experiment_action
                : dynamic
                ? R.string.scene_mode_dynamic_action
                : R.string.scene_mode_static_action;

        liveSceneModeToggle.setIconResource(icon);
        liveSceneModeToggle.setText(dynamic ? R.string.main_grid_scene_dynamic : R.string.main_grid_scene_static);
        liveSceneModeToggle.setEnabled(enabled);
        tintCameraAction(liveSceneModeToggle, tint);
        liveSceneModeToggle.setContentDescription(getString(description));
        ViewCompat.setTooltipText(liveSceneModeToggle, getString(description));
    }

    @Override
    protected void onResume() {
        super.onResume();
        analysisScreenForeground = true;
        if(configurationReadiness!=null)configurationReadiness.resume();
        getWindow().getDecorView().post(this::continueReadinessNavigation);
        experimentPreparationFailed = false;

        applySettingsRevision();
        restoreTvCameraSetup();
        if (tvSetup != null) getWindow().getDecorView().post(tvSetup::openRequested);
        startThermalUiMonitor();
        if (experimentSession.isRunning()) {
            scheduleExperimentTimer(experimentTimerConfig);
            if (tvSetup != null && researchSessions.store() != null) tvSetup.resumeSession(researchSessions.store().tvSetupSnapshot());
        }
        if (!experimentSession.isRunning() && researchSessions.timerState().running) {
            scheduleExperimentTimer(experimentTimerConfig);
            if(!cameraStarted)startCamera(false);
        }
        if (experimentSession.admitsWork(android.os.SystemClock.elapsedRealtimeNanos())
                && !cameraStarted && researchSessions.store() != null) {
            ResearchExecutionConfig frozen = experimentSession.frozenExecutionConfig();
            pipeline.setResearchExecutionConfig(frozen);
            pipeline.setSceneHandlingMode(frozen.sceneHandlingMode);
            pipeline.resumeResearchAfterRecreation(researchSessions.previousSceneGeneration);
            pipeline.setResearchCollector(researchSessions.store());
            metricsCollector.setResearchCollector(researchSessions.store());
            collectionActive=true; collectionSessionId=experimentSession.sessionId();
            targetStateMachine.setEnabled(frozen.lockEnabled);
            autoZoomController.setEnabled(frozen.autoZoomEnabled);
            startCamera(false);
            ExperimentSession.Snapshot snapshot=experimentSession.snapshot();
            scheduleExperimentTimer(TimerConfig.of(snapshot.timerEnabled,(int)(snapshot.timerDurationMillis/1000)));
        }

        startPhoneOrientationUi();
        if (cameraStarted) {
            startPreviewSceneMonitor();
        }
        refreshExperimentStartReadiness();
        requestExperimentReadinessRefresh();
        renderAnalysisControls();
    }

    private void applySettingsRevision() {
        if (uiPreferences == null || pipeline == null) return;

        int revision = uiPreferences.getInt(
                SettingsActivity.KEY_REVISION,
                0
        );

        if (revision == knownSettingsRevision) return;

        knownSettingsRevision = revision;

        if (experimentSession.isRunning()) {
            recordWarning(
                    "Pominięto zmianę ustawień podczas aktywnej sesji badawczej"
            );
            return;
        }


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
        experimentLockEnabled = uiPreferences.getBoolean(
                SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,
                true
        );
        experimentAutoZoomEnabled = uiPreferences.getBoolean(
                SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,
                false
        );

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
        renderSceneModeHudToggle();
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
         * Ustawienia mogły zmienić modele lub warianty,
         * dlatego odświeżamy rejestr i pipeline.
         */
        modelRegistry.reload();
        pipeline.invalidateModels();
        invalidatePresentationAfterSettingsChange(revision);
        restartScanAcquisitionForConfiguration(cameraStarted, "settings_applied");

        if (!syncMissingModelsStatus() && cameraStarted) {
            showSearchingStatusForCurrentPipeline();
        }

        scheduleMissingAutotuning();
    }

    private void restartScanAcquisitionForConfiguration(
            boolean cameraActive,
            String reason
    ) {
        if (pipeline == null) return;
        boolean modelsAvailable = requiredRecognitionModelsAvailable();
        boolean shouldRun = shouldRunNormalScanAcquisition(
                cameraActive,
                vehicleCascadeEnabled,
                experimentModeEnabled,
                modelsAvailable
        );
        shouldRun |= cameraActive && modelsAvailable && effectiveSceneHandlingMode() == SceneHandlingMode.STRICT_SCENE_BOUNDARY
                && (experimentModeEnabled ? experimentRoiBudgetPolicy.usesVehicleCascade() : vehicleCascadeEnabled);
        long nowRuntimeNanos = android.os.SystemClock.elapsedRealtimeNanos();
        if (shouldRun) {
            pipeline.startScanRun(Math.max(1L, nowRuntimeNanos), nowRuntimeNanos);
        } else if (pipeline.scanAcquisitionSnapshot().runState.active()) {
            pipeline.stopScanRun(nowRuntimeNanos);
        }
        android.util.Log.i(
                "ALPR_SCAN_LIFECYCLE",
                "reason=" + reason
                        + " camera=" + cameraActive
                        + " cascade=" + vehicleCascadeEnabled
                        + " experiment=" + experimentModeEnabled
                        + " models=" + modelsAvailable
                        + " action=" + (shouldRun ? "start" : "stop_or_idle")
        );
    }

    static boolean shouldRunNormalScanAcquisition(
            boolean cameraActive,
            boolean vehicleCascadeEnabled,
            boolean experimentModeEnabled,
            boolean requiredModelsAvailable
    ) {
        return cameraActive
                && vehicleCascadeEnabled
                && !experimentModeEnabled
                && requiredModelsAvailable;
    }

    @Override
    protected void onPause() {
        if(configurationReadiness!=null)configurationReadiness.suspend();
        readinessConfiguration=null;
        if (tvSetup != null) tvSetup.onBackground();
        analysisScreenForeground = false;
        experimentReadinessHandler.removeCallbacks(experimentReadinessRunnable);
        experimentStartRequest.backgrounded();
        invalidatePreparedExperiment(!experimentStartRequest.waitingForPermission());
        cancelAnalysisPreparation();
        /*
         * Monitor termiczny nie może odświeżać UI ani uruchomić
         * kamery, kiedy Activity nie znajduje się na pierwszym planie.
         * stopThermalUiMonitor() zeruje również rozpoczęty okres
         * stabilizacji, więc po powrocie warunek musi być spełniony
         * ponownie przez pełny wymagany czas.
         */
        stopThermalUiMonitor();
        cancelExperimentTimer(); // Detach views; retained session deadline continues independently.
        stopPhoneOrientationUi();
        retainCaptureGalleryState();
        stopPreviewSceneMonitor();
        if (cameraMotionMonitor != null) cameraMotionMonitor.stop();
        super.onPause();
    }

    private void restoreResearchExportSelection(Bundle state){
        if(state!=null){
            String selected=state.getString("research_export_source","");
            if(!selected.isEmpty())pendingPersistentResearchArchive=new java.io.File(selected);
            try{String kind=state.getString("research_export_kind","");if(!kind.isEmpty())pendingExportKind=ResearchArchive.Kind.valueOf(kind);}
            catch(IllegalArgumentException invalid){pendingPersistentResearchArchive=null;}
        }
    }
    private void saveResearchExportSelection(Bundle state){
        if(pendingPersistentResearchArchive!=null)state.putString("research_export_source",pendingPersistentResearchArchive.getAbsolutePath());
        if(pendingExportKind!=null)state.putString("research_export_kind",pendingExportKind.name());
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if(readinessReturn!=null)state.putBundle("readiness_main_return",readinessReturn.toBundle());
        state.putString("readiness_destination",readinessDestination);state.putBoolean("readiness_editor_started",readinessEditorStarted);
        state.putBoolean("readiness_return_due",readinessReturnDue);state.putBoolean("readiness_return_manual",readinessReturnManual);
        if (captureGalleryState != null) state.putString("crop_session_export_id", captureGalleryState.pendingCropSessionExport);
        state.putString("timer_configured_after_finished_session",timerConfiguredAfterFinishedSession);
        saveResearchExportSelection(state);
        super.onSaveInstanceState(state);
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
        if(configurationReadiness!=null)configurationReadiness.close();
        for(String name:new String[]{SettingsActivity.PREFERENCES,"autotune","model_registry"})
            getSharedPreferences(name,MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(configurationReadinessChanges);
        if(livePresentation!=null)livePresentation.dispose();
        if(previewView!=null&&previewView.getViewTreeObserver().isAlive())
            previewView.getViewTreeObserver().removeOnGlobalLayoutListener(overlayReadabilityLayoutListener);
        experimentStartRequest.cancel();
        experimentReadinessHandler.removeCallbacks(experimentReadinessRunnable);
        stopPhoneOrientationUi();
        if (researchSessions != null && pipeline != null)
            researchSessions.previousSceneGeneration=pipeline.sceneContinuitySnapshot().sceneGeneration;
        if (researchSessions.timerState().running && !isChangingConfigurations())
            finishAnalysisMeasurement(ExperimentSession.CompletionReason.MANUAL);
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
        if (pipeline != null) pipelineInferenceExecutor.execute(pipeline::finishCameraTransform);

        AppLog.info(
                this,
                LOG_TAG,
                "Zamykanie aplikacji"
        );
        commitVerificationNote();
        stopPreviewSceneMonitor();

        uiSceneGeneration.incrementAndGet();

        /*
         * CameraX przestaje dostarczać klatki, a zamknięcie pipeline'u trafia
         * za ewentualną inferencję już oczekującą na jego własnym executorze.
         */
        if (cameraController != null) cameraController.close();
        closePipelineAfterPendingInference();

        backgroundExecutor.shutdownNow();
        previewTrackingExecutor.shutdownNow();
        pendingDirectLumaFrame.set(null);
        directLumaTrackingExecutor.shutdownNow();
        previewCoordinationExecutor.shutdownNow();
        pipelineInferenceExecutor.shutdown();
        cancelExperimentTimer();

        if (galleryBottomSheet != null) {

            galleryBottomSheet.setOnDismissListener(
                    null
            );

            galleryBottomSheet.dismiss();
            clearGallerySheetReferences();
        }

        super.onDestroy();

        /*
         * Zwykły onDestroy, np. przy odtworzeniu Activity,
         * NIE może kończyć procesu.
         *
         * Proces kończymy tylko po świadomym wybraniu
         * pozycji "Zamknij aplikację".
         */
        if (explicitExitRequested && !researchSessions.finalizing()) {
            android.os.Process.killProcess(
                    android.os.Process.myPid()
            );
        }
    }

    private void deferPipelineControl(java.util.function.Consumer<AlprPipeline> command) {
        AlprPipeline target = pipeline;
        if (target == null || pipelineInferenceExecutor.isShutdown()) return;
        pipelineInferenceExecutor.execute(() -> command.accept(target));
    }

    private void closePipelineAfterPendingInference() {
        if (pipeline == null) return;
        try {
            pipelineInferenceExecutor.execute(pipeline::close);
        } catch (RejectedExecutionException rejected) {
            pipeline.close();
        }
    }
    private SceneHandlingMode effectiveSceneHandlingMode() {
        ResearchExecutionConfig frozen = pipeline == null ? null : pipeline.researchExecutionConfig();
        if (experimentSession.isRunning() && frozen != null) return frozen.sceneHandlingMode;
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
        renderSceneModeHudToggle();

        overlayTracker.reset();
        deferPipelineControl(AlprPipeline::resetTracking);
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
    private void restoreTimerConfiguration() {
        experimentTimerConfig=TimerConfig.of(uiPreferences.getBoolean(KEY_ANALYSIS_TIMER_ENABLED,false),
                uiPreferences.getInt(KEY_LAST_EXPERIMENT_TIMER_SECONDS,TimerConfig.DEFAULT_DURATION_SECONDS));
    }

    private boolean timerSettingsAvailable() {
        return !cameraStarted && !waitingForThermalStart && !analysisStartGate.preparing()
                && !experimentStartRequest.pending() && !researchSessions.timerState().running
                && !sessionWriteBusy();
    }

    private void toggleAnalysisTimer() {
        if (!timerSettingsAvailable()) return;
        applyTimerConfiguration(TimerConfig.of(!experimentTimerConfig.enabled(),experimentTimerConfig.durationSeconds()));
    }

    private void applyTimerConfiguration(TimerConfig next) {
        if (!timerSettingsAvailable()) return;
        experimentTimerConfig=next;
        uiPreferences.edit().putInt(KEY_LAST_EXPERIMENT_TIMER_SECONDS,next.durationSeconds())
                .putBoolean(KEY_ANALYSIS_TIMER_ENABLED,next.enabled()).apply();
        timerConfiguredAfterFinishedSession=researchSessions.timerState().id;
        invalidatePreparedExperiment();
        recordInfo(next.enabled()?getString(R.string.experiment_timer_armed_log,next.durationSeconds())
                :getString(R.string.experiment_timer_disabled_log));
        ensureExperimentPrepared();
        renderAnalysisControls();
    }

    private void showExperimentTimerDialog() {
        if (!timerSettingsAvailable()) return;
        final int[] durations = {15, 60, 180, 300, -1};
        CharSequence[] labels = {"15 s", "1 min", "3 min", "5 min", "Własny czas (minuty)"};
        int seconds = experimentTimerConfig.durationSeconds();
        int initial = durations.length - 1;
        for (int i = 0; i < durations.length - 1; i++) if (durations[i] == seconds) initial = i;
        final int[] selected = {initial};
        com.google.android.material.textfield.TextInputLayout field =
                new com.google.android.material.textfield.TextInputLayout(this);
        field.setHint("Czas w minutach, np. 2,5");
        float density = getResources().getDisplayMetrics().density;
        field.setPadding(Math.round(24 * density), Math.round(8 * density), Math.round(24 * density), 0);
        com.google.android.material.textfield.TextInputEditText minutes =
                new com.google.android.material.textfield.TextInputEditText(this);
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minutes.setSingleLine(true);
        minutes.setText(java.math.BigDecimal.valueOf(seconds).divide(java.math.BigDecimal.valueOf(60),
                6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        field.addView(minutes);
        minutes.setEnabled(initial == durations.length - 1);
        androidx.appcompat.app.AlertDialog timerDialog = new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(R.string.main_timer_duration_title)
                .setSingleChoiceItems(labels, initial, (dialog, which) -> {
                    selected[0] = which;
                    minutes.setEnabled(which == durations.length - 1);
                    field.setError(null);
                    if (minutes.isEnabled()) minutes.requestFocus();
                })
                .setView(field)
                .setNegativeButton(R.string.menu_close, null)
                .setPositiveButton(R.string.settings_apply, null).create();
        timerDialog.setOnShowListener(ignored -> timerDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    TimerConfig next;
                    try {
                        int preset = durations[selected[0]];
                        int duration = preset == -1 ? TimerConfig.fromMinutes(String.valueOf(minutes.getText())).durationSeconds() : preset;
                        next = TimerConfig.of(experimentTimerConfig.enabled(), duration);
                    } catch (IllegalArgumentException invalid) {
                        field.setError(invalid.getMessage());
                        return;
                    }
                    applyTimerConfiguration(next);
                    timerDialog.dismiss();
                }));
        timerDialog.show();
    }

    /** Clock presentation only: never creates, extends or restarts a deadline. */
    private void updateExperimentTimerButton() {
        com.example.alpr_v1.experiment.ResearchSessionViewModel.TimerState timer=researchSessions.timerState();
        boolean enabled=timer.running?timer.enabled:experimentTimerConfig.enabled();
        if(experimentTimerButton!=null){
            String tile=getString(enabled?R.string.main_timer_tile_on:R.string.main_timer_tile_off);
            if(getResources().getConfiguration().screenWidthDp>getResources().getConfiguration().screenHeightDp)tile=tile.replace('\n',' ');
            experimentTimerButton.setText(tile);
            experimentTimerButton.setContentDescription(getString(enabled?R.string.main_timer_disable:R.string.main_timer_enable));
            tintCameraAction(experimentTimerButton,R.color.alpr_accent);
        }
        View clock=findViewById(R.id.experiment_timer_status);
        TextView value=findViewById(R.id.experiment_timer_value),caption=findViewById(R.id.experiment_timer_caption);
        if(clock==null||value==null||caption==null)return;
        long seconds=experimentTimerConfig.durationSeconds();
        int label=enabled?R.string.main_timer_set:R.string.main_timer_off;
        boolean completed=timer.expired&&!timer.running&&!timer.id.equals(timerConfiguredAfterFinishedSession);
        if(timer.running){
            seconds=timer.durationSeconds;
            if(timer.enabled){
                seconds=com.example.alpr_v1.ui.ExperimentCountdown.remainingSeconds(timer.deadlineElapsedNanos/1_000_000L,android.os.SystemClock.elapsedRealtime());
                label=R.string.main_timer_remaining;
            }
        }else if(completed){seconds=0L;label=R.string.main_timer_expired;}
        if(livePresentation!=null)livePresentation.setTimerCompleted(completed);
        clock.setVisibility(View.VISIBLE);
        value.setTextColor(getColor(label==R.string.main_timer_remaining?R.color.cockpit_cyan:R.color.white));
        String text=formatTimerDuration(seconds),description=getString(label);
        if(!text.contentEquals(value.getText()))value.setText(text);
        if(!description.contentEquals(caption.getText()))caption.setText(description);
        long widest=Math.max(seconds,Math.max(experimentTimerConfig.durationSeconds(),timer.durationSeconds));
        float density=getResources().getDisplayMetrics().density;
        float captionWidth=0f;
        for(int id:new int[]{R.string.main_timer_remaining,R.string.main_timer_set,R.string.main_timer_expired,R.string.main_timer_off})
            captionWidth=Math.max(captionWidth,caption.getPaint().measureText(getString(id)));
        float numberWidth=value.getPaint().measureText(formatTimerDuration(widest))+30*density;
        boolean inline=clock instanceof LinearLayout&&((LinearLayout)clock).getOrientation()==LinearLayout.HORIZONTAL;
        int width=(int)Math.ceil(Math.max(48*density,inline?numberWidth+captionWidth+4*density:Math.max(numberWidth,captionWidth+8*density)));
        if(clock.getLayoutParams().width!=width){clock.getLayoutParams().width=width;clock.requestLayout();}
        boolean editable=timerSettingsAvailable();
        clock.setEnabled(editable);clock.setClickable(editable);clock.setFocusable(editable);
        clock.setContentDescription(getString(editable?R.string.main_timer_duration_action:R.string.main_timer_locked,text,description));
    }

    private static String formatTimerDuration(long seconds) {
        long safe=Math.max(0L,seconds);
        return safe<3600L?String.format(Locale.ROOT,"%d:%02d",safe/60L,safe%60L)
                :String.format(Locale.ROOT,"%d:%02d:%02d",safe/3600L,safe/60L%60L,safe%60L);
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
            com.example.alpr_v1.ui.CameraWindowUi.keepImmersive(getWindow());
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

                    if (liveHudRow != null) {
                        ((com.example.alpr_v1.ui.LiveHudView) liveHudRow)
                                .renderDeviceMetrics(latestThermalSnapshot.batteryTemperatureC);
                    }

                    if (cameraStarted && metricsCollector != null) {
                        metricsCollector.recordThermalSample(
                                latestThermalSnapshot
                        );
                    }


                    refreshResearchCropCount();
                    updateExperimentThermalButton();

                    if (experimentModeEnabled && !cameraStarted && analysisScreenForeground) {
                        requestExperimentReadinessRefresh();
                    }

                    renderHudModelInfo();


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

        if (liveHudRow != null) {
            ((com.example.alpr_v1.ui.LiveHudView) liveHudRow).resetDeviceSampling();
        }

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

        TextView normalDevice=findViewById(R.id.main_device_status);
        if(normalDevice!=null){
            normalDevice.setVisibility(experimentModeEnabled?View.GONE:View.VISIBLE);
            ThermalMonitor.Snapshot device=latestThermalSnapshot;
            String caption=device==null||!device.available()?getString(R.string.main_device_na):String.format(Locale.ROOT,
                    "BAT %.1f°C · TH%d · HEAD %s",device.batteryTemperatureC,device.thermalStatus,
                    device.headroomAvailable()?String.format(Locale.ROOT,"%.2f",device.thermalHeadroom):"—");
            if(!caption.contentEquals(normalDevice.getText()))normalDevice.setText(caption);
            normalDevice.setContentDescription(caption);normalDevice.setTooltipText(caption);
        }

        if (experimentThermalButton == null) {
            return;
        }

        tintCameraAction(experimentThermalButton,R.color.alpr_accent);
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
                    R.string.main_device_na
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
        experimentThermalButton.setContentDescription(text);
        experimentThermalButton.setTooltipText(text);
        String compact=String.format(Locale.ROOT,"BAT %.1f°C · TH%d · HEAD %s",snapshot.batteryTemperatureC,snapshot.thermalStatus,headroomText);
        experimentThermalButton.setText(compact);

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
        if (analysisStartGate.preparing()) return;

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


        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
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


                            invalidatePreparedExperiment();
                            updateExperimentThermalButton();
                            ensureExperimentPrepared();
                            renderAnalysisControls();
                        }
                )
                .show();
    }

    private void requestAnalysisStart() {
        if (experimentModeEnabled) {
            startCamera(true);
            return;
        }

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
