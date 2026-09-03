package com.example.alpr_v1.camera;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import com.example.alpr_v1.continuity.SourceFrameStamp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CameraController implements AutoCloseable {
    public interface FrameHandler {
        void onFrame(@NonNull ImageProxy image, @NonNull SourceFrameStamp sourceFrameStamp);
    }

    public interface LumaFrameHandler {
        void onLumaFrame(@NonNull LumaFrame frame);
        default void onUnavailable() {}
    }

    public interface ErrorHandler {
        void onError(Throwable error);
    }

    public interface ControlCallback {
        void onSuccess(float appliedZoomRatio);
        void onError(Throwable error);
        default void onProgress(float appliedZoomRatio) {}
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();
    private final CameraSourceTimeline sourceTimeline = new CameraSourceTimeline();
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private int bindingGeneration;
    private final Handler cameraControlHandler = new Handler(Looper.getMainLooper());
    private int cameraControlGeneration;

    public CameraController(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    }

    public void start(FrameHandler frameHandler, ErrorHandler errorHandler, Size analysisSize,  boolean allowHighResolution) {
        start(frameHandler, null, errorHandler, analysisSize, allowHighResolution);
    }

    public void start(
            FrameHandler frameHandler,
            LumaFrameHandler lumaFrameHandler,
            ErrorHandler errorHandler,
            Size analysisSize,
            boolean allowHighResolution
    ) {
        final int requestedGeneration = ++bindingGeneration;
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                if (requestedGeneration != bindingGeneration) return;
                cameraProvider = providerFuture.get();
                bind(
                        frameHandler,
                        lumaFrameHandler,
                        analysisSize,
                        allowHighResolution,
                        requestedGeneration
                );
            } catch (Exception e) {
                errorHandler.onError(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /** Wiąże kamerę z PreviewView, ale każdą klatkę zamyka bez inferencji i kopii luma. */
    public void startPreview(
            ErrorHandler errorHandler,
            Size analysisSize,
            boolean allowHighResolution
    ) {
        start(
                (image, sourceFrameStamp) -> { },
                null,
                errorHandler,
                analysisSize,
                allowHighResolution
        );
    }

    private void bind(
            FrameHandler frameHandler,
            LumaFrameHandler lumaFrameHandler,
            Size analysisSize,
            boolean allowHighResolution,
            int requestedGeneration
    ) {
        if (requestedGeneration != bindingGeneration) return;

        Preview preview =
                new Preview.Builder()
                        .build();


        preview.setSurfaceProvider(
                previewView.getSurfaceProvider()
        );


        ResolutionSelector.Builder selectorBuilder =
                new ResolutionSelector.Builder();


        /*
         * Zwykłe rozdzielczości:
         * priorytet FPS.
         *
         * Świadomie wybrana rozdzielczość high-res:
         * dopuszczamy również wolniejsze formaty urządzenia.
         */
        selectorBuilder.setAllowedResolutionMode(
                allowHighResolution

                        ? ResolutionSelector
                          .PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE

                        : ResolutionSelector
                          .PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION
        );


        selectorBuilder.setResolutionStrategy(
                new ResolutionStrategy(
                        analysisSize,
                        ResolutionStrategy
                                .FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
        );


        /*
         * To jest ważne dla formatów innych niż typowe 4:3 / 16:9.
         *
         * ResolutionFilter jest wykonywany po standardowym sortowaniu
         * CameraX i pozwala nam postawić dokładnie wybraną przez
         * użytkownika rozdzielczość na pierwszym miejscu.
         */
        selectorBuilder.setResolutionFilter(
                (supportedSizes, rotationDegrees) -> {

                    List<Size> ordered =
                            new ArrayList<>(
                                    supportedSizes
                            );

                    for (int index = 0;
                         index < ordered.size();
                         index++) {

                        Size candidate =
                                ordered.get(
                                        index
                                );

                        if (candidate.getWidth()
                                == analysisSize.getWidth()
                                && candidate.getHeight()
                                == analysisSize.getHeight()) {

                            if (index > 0) {
                                ordered.remove(
                                        index
                                );

                                ordered.add(
                                        0,
                                        candidate
                                );
                            }

                            break;
                        }
                    }

                    return ordered;
                }
        );


        ResolutionSelector resolutionSelector =
                selectorBuilder.build();


        ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                        .setResolutionSelector(
                                resolutionSelector
                        )
                        .setOutputImageFormat(
                                ImageAnalysis
                                        .OUTPUT_IMAGE_FORMAT_YUV_420_888
                        )
                        .setOutputImageRotationEnabled(
                                true
                        )
                        .setBackpressureStrategy(
                                ImageAnalysis
                                        .STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build();


        analysis.setAnalyzer(
                analyzerExecutor,
                image -> {

                    try {
                        SourceFrameStamp sourceFrameStamp =
                                sourceTimeline.observeCameraFrame(
                                        image.getImageInfo().getTimestamp()
                                );
                        if (lumaFrameHandler != null) {
                            LumaFrame lumaFrame = LumaFrame.copyFrom(
                                    image,
                                    240,
                                    sourceFrameStamp
                            );
                            if (lumaFrame != null) {
                                lumaFrameHandler.onLumaFrame(lumaFrame);
                            }
                        }
                        frameHandler.onFrame(
                                image,
                                sourceFrameStamp
                        );

                    } finally {
                        image.close();
                    }
                }
        );

        cameraProvider.unbindAll();

        int displayRotation = previewView.getDisplay() == null
                ? Surface.ROTATION_0 : previewView.getDisplay().getRotation();
        ViewPort viewPort = new ViewPort.Builder(
                new Rational(
                        Math.max(1, analysisSize.getWidth()),
                        Math.max(1, analysisSize.getHeight())
                ),
                displayRotation
        ).setScaleType(ViewPort.FIT).build();
        UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .addUseCase(analysis)
                .build();

        camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup
        );
    }

    public void zoomAndFocus(
            float requestedZoomRatio,
            float normalizedX,
            float normalizedY,
            ControlCallback callback
    ) {
        Camera boundCamera = camera;
        if (boundCamera == null) {
            callback.onError(new IllegalStateException("Kamera nie jest jeszcze związana"));
            return;
        }

        float appliedRatio = clampZoomRatio(
                boundCamera,
                requestedZoomRatio
        );
        animateZoomRatio(boundCamera, appliedRatio, 300L, () -> {
            try {
                /*
                 * Zoom CameraX jest wykonywany względem środka sensora.
                 * Punkt tablicy przesuwa się więc w znormalizowanym obrazie
                 * zgodnie z transformacją 0.5 + zoom * (p - 0.5).
                 */
                float x = zoomedCoordinate(normalizedX, appliedRatio);
                float y = zoomedCoordinate(normalizedY, appliedRatio);
                MeteringPoint point = previewView
                        .getMeteringPointFactory()
                        .createPoint(
                                x * Math.max(1, previewView.getWidth()),
                                y * Math.max(1, previewView.getHeight())
                        );
                FocusMeteringAction action = new FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF
                                | FocusMeteringAction.FLAG_AE
                                | FocusMeteringAction.FLAG_AWB
                )
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build();

                try {
                    boundCamera.getCameraControl().startFocusAndMetering(action);
                } catch (RuntimeException ignored) {
                    /*
                     * Zoom pozostaje użyteczny także wtedy, gdy dany aparat
                     * nie obsługuje wskazanego zestawu regionów AF/AE/AWB.
                     */
                }
                /*
                 * Nie czekamy na pełny wynik AF, ponieważ część urządzeń
                 * kończy future dopiero po automatycznym anulowaniu.
                 * Kontroler wyżej i tak zapewnia osobny okres stabilizacji.
                 */
                callback.onSuccess(appliedRatio);
            } catch (Exception error) {
                callback.onError(error);
            }
        }, callback);
    }

    public void setZoomRatio(
            float requestedZoomRatio,
            ControlCallback callback
    ) {
        Camera boundCamera = camera;
        if (boundCamera == null) {
            callback.onError(new IllegalStateException("Kamera nie jest jeszcze związana"));
            return;
        }
        float appliedRatio = clampZoomRatio(boundCamera, requestedZoomRatio);
        animateZoomRatio(
                boundCamera,
                appliedRatio,
                requestedZoomRatio <= 1.01f ? 560L : 300L,
                () -> callback.onSuccess(appliedRatio),
                callback
        );
    }

    private void animateZoomRatio(
            Camera boundCamera,
            float targetRatio,
            long durationMillis,
            Runnable completion,
            ControlCallback callback
    ) {
        ZoomState state = boundCamera.getCameraInfo().getZoomState().getValue();
        float startRatio = state == null ? 1f : state.getZoomRatio();
        int generation = ++cameraControlGeneration;
        cameraControlHandler.removeCallbacksAndMessages(null);
        int steps = Math.max(18, (int) (durationMillis / 16L));
        boolean returning = targetRatio < startRatio;
        for (int step = 1; step <= steps; step++) {
            final int scheduledStep = step;
            long delay = Math.round(durationMillis * (step / (double) steps));
            cameraControlHandler.postDelayed(() -> {
                if (generation != cameraControlGeneration || camera != boundCamera) return;
                float progress = scheduledStep / (float) steps;
                float eased = returning
                        ? 1f - (float) Math.pow(1f - progress, 3)
                        : progress * progress * (3f - 2f * progress);
                float ratio = startRatio + (targetRatio - startRatio) * eased;
                try {
                    callback.onProgress(ratio);
                    ListenableFuture<Void> future =
                            boundCamera.getCameraControl().setZoomRatio(ratio);
                    if (scheduledStep != steps) return;
                    future.addListener(() -> {
                        try {
                            future.get();
                            if (generation == cameraControlGeneration
                                    && camera == boundCamera) completion.run();
                        } catch (Exception error) {
                            callback.onError(error);
                        }
                    }, ContextCompat.getMainExecutor(context));
                } catch (RuntimeException error) {
                    if (scheduledStep == steps) callback.onError(error);
                }
            }, delay);
        }
    }

    public float maximumZoomRatio() {
        Camera boundCamera = camera;
        ZoomState zoomState = boundCamera == null
                ? null
                : boundCamera.getCameraInfo().getZoomState().getValue();
        return zoomState == null ? 1f : zoomState.getMaxZoomRatio();
    }

    public CameraSourceTimeline sourceTimeline() {
        return sourceTimeline;
    }

    private static float clampZoomRatio(Camera camera, float requested) {
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null) return Math.max(1f, requested);
        return Math.max(
                zoomState.getMinZoomRatio(),
                Math.min(zoomState.getMaxZoomRatio(), requested)
        );
    }

    public static float zoomedCoordinate(float coordinate, float zoomRatio) {
        return scaledCoordinate(coordinate, Math.max(1f, zoomRatio));
    }

    public static float scaledCoordinate(float coordinate, float scaleRatio) {
        float transformed = 0.5f
                + Math.max(0.1f, scaleRatio) * (coordinate - 0.5f);
        return Math.max(0f, Math.min(1f, transformed));
    }

    /**
     * Ogranicza zoom centrowany przez CameraX tak, aby cały prostokąt celu
     * pozostał w widocznym fragmencie PreviewView wraz z niewielkim marginesem.
     */
    public static float centeredZoomKeepingBoundsVisible(
            float requestedRatio,
            float targetLeft,
            float targetTop,
            float targetRight,
            float targetBottom,
            float visibleLeft,
            float visibleTop,
            float visibleRight,
            float visibleBottom,
            float marginFraction
    ) {
        float requested = Math.max(1f, requestedRatio);
        float left = Math.min(targetLeft, targetRight);
        float right = Math.max(targetLeft, targetRight);
        float top = Math.min(targetTop, targetBottom);
        float bottom = Math.max(targetTop, targetBottom);
        float viewportLeft = Math.max(0f, Math.min(1f, visibleLeft));
        float viewportRight = Math.max(viewportLeft, Math.min(1f, visibleRight));
        float viewportTop = Math.max(0f, Math.min(1f, visibleTop));
        float viewportBottom = Math.max(viewportTop, Math.min(1f, visibleBottom));
        float margin = Math.max(0f, Math.min(0.20f, marginFraction));
        float safeLeft = viewportLeft + (viewportRight - viewportLeft) * margin;
        float safeRight = viewportRight - (viewportRight - viewportLeft) * margin;
        float safeTop = viewportTop + (viewportBottom - viewportTop) * margin;
        float safeBottom = viewportBottom - (viewportBottom - viewportTop) * margin;

        float maximum = requested;
        if (left < 0.5f) {
            maximum = Math.min(maximum, (0.5f - safeLeft) / (0.5f - left));
        }
        if (right > 0.5f) {
            maximum = Math.min(maximum, (safeRight - 0.5f) / (right - 0.5f));
        }
        if (top < 0.5f) {
            maximum = Math.min(maximum, (0.5f - safeTop) / (0.5f - top));
        }
        if (bottom > 0.5f) {
            maximum = Math.min(maximum, (safeBottom - 0.5f) / (bottom - 0.5f));
        }
        if (!Float.isFinite(maximum)) return 1f;
        return Math.max(1f, Math.min(requested, maximum));
    }

    public void stop() {
        bindingGeneration++;
        cameraControlGeneration++;
        cameraControlHandler.removeCallbacksAndMessages(null);
        if (cameraProvider != null) cameraProvider.unbindAll();
        camera = null;
    }

    @Override
    public void close() {
        close(null);
    }

    public void close(Runnable analyzerCleanup) {
        stop();
        if (analyzerCleanup != null && !analyzerExecutor.isShutdown()) {
            try {
                Future<?> cleanup = analyzerExecutor.submit(analyzerCleanup);
                cleanup.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Proces kończy działanie; zasoby natywne zwolni również system.
            }
        }
        analyzerExecutor.shutdownNow();
    }
}
