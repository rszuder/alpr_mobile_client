package com.example.alpr_v1.camera;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CameraController implements AutoCloseable {
    public interface FrameHandler {
        void onFrame(@NonNull ImageProxy image);
    }

    public interface ErrorHandler {
        void onError(Throwable error);
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider cameraProvider;

    public CameraController(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
    }

    public void start(FrameHandler frameHandler, ErrorHandler errorHandler, Size analysisSize,  boolean allowHighResolution) {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bind(frameHandler, analysisSize, allowHighResolution);
            } catch (Exception e) {
                errorHandler.onError(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bind(
            FrameHandler frameHandler,
            Size analysisSize,
            boolean allowHighResolution
    ) {

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
                        frameHandler.onFrame(
                                image
                        );

                    } finally {
                        image.close();
                    }
                }
        );


        cameraProvider.unbindAll();


        cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
        );
    }

    public void stop() {
        if (cameraProvider != null) cameraProvider.unbindAll();
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
