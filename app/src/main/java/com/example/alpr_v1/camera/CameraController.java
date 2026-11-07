package com.example.alpr_v1.camera;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.lifecycle.Observer;
import java.util.Collections;
import java.util.function.Consumer;

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
import java.util.concurrent.TimeUnit;

@ExperimentalCamera2Interop
public final class CameraController implements AutoCloseable,CameraSetupControl {
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

    public interface PreviewGeometryListener {
        void onGeometry(int width, int height);
    }

    private PreviewGeometryListener previewGeometryListener;
    private Size boundPreviewGeometry;

    public void setPreviewGeometryListener(PreviewGeometryListener listener) {
        previewGeometryListener = listener;
    }

    private void publishPreviewGeometry() {
        if (previewGeometryListener != null && boundPreviewGeometry != null) {
            previewGeometryListener.onGeometry(boundPreviewGeometry.getWidth(), boundPreviewGeometry.getHeight());
        }
    }

    public interface ControlCallback {
        void onSuccess(float appliedZoomRatio);
        void onError(Throwable error);
        default void onProgress(float appliedZoomRatio) {}
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final CameraGeometryProvider setupGeometry;
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor();
    private final CameraSourceTimeline sourceTimeline = new CameraSourceTimeline();
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private volatile FrameCallbacks activeCallbacks;
    private Size boundAnalysisSize;
    private boolean boundHighResolution;
    private int boundRotation;
    private volatile long lastStreamFrameNanos;

    /** Ready means a matching, already running camera stream, not merely a bound use case. */
    public boolean isStreamReady(Size size, boolean highResolution) {
        int rotation = previewView.getDisplay() == null ? Surface.ROTATION_0 : previewView.getDisplay().getRotation();
        long age = android.os.SystemClock.elapsedRealtimeNanos() - lastStreamFrameNanos;
        return camera != null && size.equals(boundAnalysisSize) && highResolution == boundHighResolution
                && rotation == boundRotation && requestedSelection.equals(boundRequestedSelection)
                && lastStreamFrameNanos > 0L && age >= 0L && age < 500_000_000L;
    }

    private static final class FrameCallbacks {
        final FrameHandler frame;
        final LumaFrameHandler luma;

        FrameCallbacks(FrameHandler frame, LumaFrameHandler luma) {
            this.frame = frame;
            this.luma = luma;
        }
    }
    private int bindingGeneration;
    private final Handler cameraControlHandler = new Handler(Looper.getMainLooper());
    private int cameraControlGeneration;
    private CameraSelection requestedSelection=CameraSelection.defaultBack(),boundRequestedSelection;
    private CameraSelection boundSelection;
    private float requestedSetupZoom=1f;
    private boolean setupZoomAcknowledged;
    private long setupRevision;
    private String setupFailure="";
    private List<CameraSetupControl.Choice> setupChoices=Collections.emptyList();
    private Consumer<CameraSetupControl.State> setupListener=state->{};
    private volatile CameraSetupControl.State publishedSetup=CameraSetupControl.State.unavailable();
    private Observer<ZoomState> setupZoomObserver;
    private Camera zoomObservedCamera;
    private StartRequest lastRequest;
    private final java.util.concurrent.atomic.AtomicReferenceArray<RouteEvidence> routes=new java.util.concurrent.atomic.AtomicReferenceArray<>(8);
    private final java.util.concurrent.atomic.AtomicInteger routeSequence=new java.util.concurrent.atomic.AtomicInteger();
    private volatile long lastSetupFrameTimestamp;
    private final java.util.concurrent.atomic.AtomicLong setupFrameSequence=new java.util.concurrent.atomic.AtomicLong();
    private volatile long setupAckFrameSequence;
    private volatile boolean setupFreshFramePublished;
    private volatile String confirmedPhysical;
    private static final class RouteEvidence {final long timestamp;final boolean matches;
        RouteEvidence(long timestamp,boolean matches){this.timestamp=timestamp;this.matches=matches;}}
    private static final class StartRequest {
        final FrameHandler frame;final LumaFrameHandler luma;final ErrorHandler error;final Size size;final boolean high;
        StartRequest(FrameHandler frame,LumaFrameHandler luma,ErrorHandler error,Size size,boolean high){this.frame=frame;this.luma=luma;this.error=error;this.size=size;this.high=high;}
    }

    @Override public CameraSetupControl.State state(){
        if(Looper.myLooper()!=Looper.getMainLooper())return publishedSetup;
        CameraSetupControl.State state=computeSetupState();publishedSetup=state;return state;
    }
    private CameraSetupControl.State computeSetupState(){
        Camera target=camera;
        ZoomState zoomState=target==null?null:target.getCameraInfo().getZoomState().getValue();
        float current=zoomState==null?1:zoomState.getZoomRatio(),min=zoomState==null?1:zoomState.getMinZoomRatio(),max=zoomState==null?1:zoomState.getMaxZoomRatio();
        boolean routeReady=boundSelection!=null&&(boundSelection.physicalCameraId==null||boundSelection.physicalCameraId.equals(confirmedPhysical));
        long age=android.os.SystemClock.elapsedRealtimeNanos()-lastStreamFrameNanos;
        boolean validZoom=zoomState!=null&&Float.isFinite(current)&&Float.isFinite(min)&&Float.isFinite(max)&&min>0&&max>=min;
        boolean ready=setupFailure.isEmpty()&&target!=null&&validZoom&&requestedSelection.equals(boundRequestedSelection)&&lastStreamFrameNanos>0&&age>=0&&age<500_000_000L&&routeReady&&setupZoomAcknowledged
                &&setupFrameSequence.get()>setupAckFrameSequence&&Math.abs(current-requestedSetupZoom)<1e-4;
        CameraSetupSnapshot applied=ready?new CameraSetupSnapshot(boundSelection,current):null;
        String reason=!setupFailure.isEmpty()?setupFailure:target==null?"Uruchamianie wybranego obiektywu":!validZoom?"Brak wiarygodnego zakresu zoomu":!routeReady?"Oczekiwanie na potwierdzenie fizycznego obiektywu":!ready?"Oczekiwanie na aktualną klatkę i zastosowanie zoomu":"";
        return new CameraSetupControl.State(setupChoices,requestedSelection,requestedSetupZoom,min,max,applied,!ready&&setupFailure.isEmpty(),reason,setupRevision);
    }
    @Override public void setListener(Consumer<CameraSetupControl.State> listener){setupListener=listener==null?state->{}:listener;publishSetup();}
    private void publishSetup(){CameraSetupControl.State state=computeSetupState();publishedSetup=state;setupListener.accept(state);}
    public CameraSetupSnapshot setupSnapshot(){return state().applied;}
    /** Request only; MainActivity restricts this port to preparation before START. */
    @Override public void apply(CameraSelection selection,float zoom){
        apply(selection,zoom,lastRequest==null?null:lastRequest.size,lastRequest!=null&&lastRequest.high);
    }
    public void apply(CameraSelection selection,float zoom,Size size,boolean high){
        if(selection==null||!Float.isFinite(zoom)||zoom<=0)throw new IllegalArgumentException("Invalid setup request");
        boolean routeChanged=!requestedSelection.equals(selection);
        if(!routeChanged&&Math.abs(requestedSetupZoom-zoom)<1e-4&&setupFailure.isEmpty())return;
        requestedSelection=selection;requestedSetupZoom=zoom;setupRevision++;setupFailure="";setupZoomAcknowledged=false;setupFreshFramePublished=false;
        if((routeChanged||camera==null)&&lastRequest!=null){StartRequest previous=lastRequest;start(previous.frame,previous.luma,previous.error,size==null?previous.size:size,high);}
        else if(camera!=null)applySetupZoom(camera,bindingGeneration);
        publishSetup();
    }
    private void applySetupZoom(Camera target,int binding){
        final long revision=setupRevision;requestedSetupZoom=clampZoomRatio(target,requestedSetupZoom);setupZoomAcknowledged=false;
        ZoomState current=target.getCameraInfo().getZoomState().getValue();
        if(current!=null&&Math.abs(current.getZoomRatio()-requestedSetupZoom)<1e-4){setupZoomAcknowledged=true;publishSetup();return;}
        ListenableFuture<Void> future;
        try{future=target.getCameraControl().setZoomRatio(requestedSetupZoom);}catch(RuntimeException error){setupFailure="Nie udało się ustawić zoomu";publishSetup();return;}
        future.addListener(()->{
            if(binding!=bindingGeneration||target!=camera||revision!=setupRevision)return;
            try{future.get();setupAckFrameSequence=setupFrameSequence.get();setupZoomAcknowledged=true;setupFailure="";setupGeometry.refresh();}
            catch(Exception error){setupFailure="Nie udało się ustawić zoomu: "+error.getClass().getSimpleName();}
            publishSetup();
        },ContextCompat.getMainExecutor(context));
    }
    private void observeSetupZoom(Camera target,int binding){
        if(zoomObservedCamera!=null&&setupZoomObserver!=null)zoomObservedCamera.getCameraInfo().getZoomState().removeObserver(setupZoomObserver);
        zoomObservedCamera=target;setupZoomObserver=state->{if(camera==target&&binding==bindingGeneration)publishSetup();};
        target.getCameraInfo().getZoomState().observe(lifecycleOwner,setupZoomObserver);
    }
    private void verifySetupFrame(int binding,long timestamp){
        if(binding!=bindingGeneration)return;
        if(setupZoomAcknowledged&&setupFrameSequence.get()>setupAckFrameSequence)setupFreshFramePublished=true;
        if(boundSelection!=null&&boundSelection.physicalCameraId!=null){
            for(int i=0;i<8;i++){RouteEvidence evidence=routes.get(i);if(evidence!=null&&evidence.timestamp==timestamp){
                if(evidence.matches){confirmedPhysical=boundSelection.physicalCameraId;if(setupFailure.startsWith("Urządzenie nie potwierdziło"))setupFailure="";}
                else{confirmedPhysical=null;setupFailure="Urządzenie nie potwierdziło wybranego obiektywu";}break;}}
        }
        publishSetup();
    }

    public CameraController(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        setupGeometry = new CameraGeometryProvider(previewView, lifecycleOwner);
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
        lastRequest=new StartRequest(frameHandler,lumaFrameHandler,errorHandler,analysisSize,allowHighResolution);
        int rotation = previewView.getDisplay() == null
                ? Surface.ROTATION_0 : previewView.getDisplay().getRotation();
        if (camera != null && analysisSize.equals(boundAnalysisSize)
                && allowHighResolution == boundHighResolution && rotation == boundRotation&&requestedSelection.equals(boundRequestedSelection)) {
            activeCallbacks = new FrameCallbacks(frameHandler, lumaFrameHandler);
            publishPreviewGeometry();
            android.util.Log.d("ALPR_CAMERA_START", "reused_preview_binding");
            return;
        }
        activeCallbacks = null;
        cameraControlGeneration++;
        cameraControlHandler.removeCallbacksAndMessages(null);
        lastStreamFrameNanos = 0L;
        camera=null;
        if(zoomObservedCamera!=null&&setupZoomObserver!=null)zoomObservedCamera.getCameraInfo().getZoomState().removeObserver(setupZoomObserver);
        zoomObservedCamera=null;setupZoomObserver=null;
        setupZoomAcknowledged=false;confirmedPhysical=null;setupFailure="";boundSelection=null;lastSetupFrameTimestamp=0;
        setupFrameSequence.set(0);setupAckFrameSequence=0;setupFreshFramePublished=false;
        for(int i=0;i<8;i++)routes.set(i,null);
        setupGeometry.onStopped();publishSetup();
        final int requestedGeneration = ++bindingGeneration;
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                if (requestedGeneration != bindingGeneration) return;
                cameraProvider = providerFuture.get();
                setupChoices=CameraLensCatalog.read(context,cameraProvider.getAvailableCameraInfos());
                bind(
                        frameHandler,
                        lumaFrameHandler,
                        analysisSize,
                        allowHighResolution,
                        requestedGeneration
                );
            } catch (Exception e) {
                if(requestedGeneration!=bindingGeneration)return;
                setupFailure="Nie można uruchomić wybranego obiektywu: "+e.getMessage();publishSetup();
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
        startPreview(errorHandler, analysisSize, allowHighResolution, null);
    }

    /** Notify readiness on the main thread after the first actual preview frame. */
    public void startPreview(ErrorHandler errorHandler, Size analysisSize, boolean allowHighResolution,
            Runnable onFirstFrame) {
        java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean();
        start(
                (image, sourceFrameStamp) -> {
                    if (onFirstFrame != null && notified.compareAndSet(false, true))
                        cameraControlHandler.post(onFirstFrame);
                },
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

        CameraSelection selection=requestedSelection;
        Preview.Builder previewBuilder=new Preview.Builder();
        if(selection.physicalCameraId!=null)new androidx.camera.camera2.interop.Camera2Interop.Extender<>(previewBuilder).setPhysicalCameraId(selection.physicalCameraId);
        Preview preview=previewBuilder.build();


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


        ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder();
        CameraCaptureSession.CaptureCallback geometryCapture=setupGeometry.captureCallback(requestedGeneration,selection.physicalCameraId);
        androidx.camera.camera2.interop.Camera2Interop.Extender<ImageAnalysis> interop=new androidx.camera.camera2.interop.Camera2Interop.Extender<>(analysisBuilder);
        if(selection.physicalCameraId!=null)interop.setPhysicalCameraId(selection.physicalCameraId);
        interop.setSessionCaptureCallback(new CameraCaptureSession.CaptureCallback(){
            @Override public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){
                geometryCapture.onCaptureCompleted(session,request,result);
                if(requestedGeneration!=bindingGeneration||selection.physicalCameraId==null)return;
                Long timestamp=result.get(CaptureResult.SENSOR_TIMESTAMP);if(timestamp==null)return;
                String active=result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                boolean matches=selection.physicalCameraId.equals(active)||result.getPhysicalCameraResults().containsKey(selection.physicalCameraId);
                routes.set(routeSequence.getAndIncrement()&7,new RouteEvidence(timestamp,matches));
                if(lastSetupFrameTimestamp==timestamp)cameraControlHandler.post(()->verifySetupFrame(requestedGeneration,timestamp));
            }
        });
        ImageAnalysis analysis =
                analysisBuilder
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
                        FrameCallbacks callbacks = activeCallbacks;
                        if (requestedGeneration != bindingGeneration || callbacks == null) return;
                        lastStreamFrameNanos = android.os.SystemClock.elapsedRealtimeNanos();
                        long timestamp=image.getImageInfo().getTimestamp();
                        boolean firstSetupFrame=lastSetupFrameTimestamp==0;
                        lastSetupFrameTimestamp=timestamp;
                        long setupSequence=setupFrameSequence.incrementAndGet();
                        if(firstSetupFrame||selection.physicalCameraId!=null&&confirmedPhysical==null||setupZoomAcknowledged&&!setupFreshFramePublished&&setupSequence>setupAckFrameSequence)
                            cameraControlHandler.post(()->verifySetupFrame(requestedGeneration,timestamp));
                        setupGeometry.observeFrame(image, requestedGeneration);
                        SourceFrameStamp sourceFrameStamp =
                                sourceTimeline.observeCameraFrame(
                                        image.getImageInfo().getTimestamp()
                                );
                        // A rebound stream must reach its frozen route/zoom before any consumer.
                        // Default 1x retains the original first-frame path; no settling delay is added.
                        if(!setupZoomAcknowledged||setupSequence<=setupAckFrameSequence||selection.physicalCameraId!=null&&confirmedPhysical==null)return;
                        if (callbacks.luma != null) {
                            LumaFrame lumaFrame = LumaFrame.copyFrom(
                                    image,
                                    240,
                                    sourceFrameStamp
                            );
                            if (lumaFrame != null) {
                                callbacks.luma.onLumaFrame(lumaFrame);
                            }
                        }
                        callbacks.frame.onFrame(
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
                selection.isDefault()?CameraSelector.DEFAULT_BACK_CAMERA:new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .addCameraFilter(infos->{List<androidx.camera.core.CameraInfo> matching=new ArrayList<>();
                            for(androidx.camera.core.CameraInfo info:infos)if(selection.logicalCameraId.equals(Camera2CameraInfo.from(info).getCameraId()))matching.add(info);
                            return matching;}).build(),
                useCaseGroup
        );
        String actualId=Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
        if(!selection.isDefault()&&!selection.logicalCameraId.equals(actualId))throw new IllegalStateException("Camera id mismatch");
        boundRequestedSelection=selection;boundSelection=new CameraSelection(actualId,selection.physicalCameraId);
        setupGeometry.onBound(camera, requestedGeneration, () -> {
            androidx.camera.core.ResolutionInfo info = preview.getResolutionInfo();
            if (info == null) return null;
            android.graphics.Rect crop = info.getCropRect();
            return new Size(crop.width(), crop.height());
        },()->{androidx.camera.core.ResolutionInfo info=analysis.getResolutionInfo();
            return info==null?null:new AnalysisTransformResolver.RawGeometry(info.getResolution().getWidth(),info.getResolution().getHeight(),info.getCropRect(),info.getRotationDegrees(),true);
        },selection.physicalCameraId);
        observeSetupZoom(camera,requestedGeneration);applySetupZoom(camera,requestedGeneration);
        boundAnalysisSize = analysisSize;
        boundHighResolution = allowHighResolution;
        boundRotation = displayRotation;
        androidx.camera.core.ResolutionInfo geometry = analysis.getResolutionInfo();
        if (geometry != null) {
            Size size = geometry.getResolution();
            int rotation = geometry.getRotationDegrees();
            boundPreviewGeometry = rotation == 90 || rotation == 270
                    ? new Size(size.getHeight(), size.getWidth()) : size;
            publishPreviewGeometry();
        }
        activeCallbacks = new FrameCallbacks(frameHandler, lumaFrameHandler);
    }

    public void zoomAndFocus(
            float requestedZoomRatio,
            float normalizedX,
            float normalizedY,
            ControlCallback callback
    ) {
        zoomAndFocus(requestedZoomRatio, normalizedX, normalizedY, callback, 300L);
    }

    public void zoomAndFocus(float requestedZoomRatio, float normalizedX, float normalizedY,
            ControlCallback callback, long animationMillis) {
        Camera boundCamera = camera;
        if (boundCamera == null) {
            callback.onError(new IllegalStateException("Kamera nie jest jeszcze związana"));
            return;
        }

        float appliedRatio = clampZoomRatio(
                boundCamera,
                requestedZoomRatio
        );
        animateZoomRatio(boundCamera, appliedRatio, animationMillis, () -> {
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
        setZoomRatio(requestedZoomRatio, callback, requestedZoomRatio <= 1.01f ? 560L : 300L);
    }

    public void setZoomRatio(float requestedZoomRatio, ControlCallback callback, long animationMillis) {
        Camera boundCamera = camera;
        if (boundCamera == null) {
            callback.onError(new IllegalStateException("Kamera nie jest jeszcze związana"));
            return;
        }
        float appliedRatio = clampZoomRatio(boundCamera, requestedZoomRatio);
        animateZoomRatio(
                boundCamera,
                appliedRatio,
                animationMillis,
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
        int steps = Math.max(2, (int) (durationMillis / 16L));
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
                            if (generation == cameraControlGeneration && camera == boundCamera)
                                callback.onError(error);
                        }
                    }, ContextCompat.getMainExecutor(context));
                } catch (RuntimeException error) {
                    if (scheduledStep == steps && generation == cameraControlGeneration && camera == boundCamera)
                        callback.onError(error);
                }
            }, delay);
        }
    }

    /** Observed CameraX zoom; NaN means that no bound camera reports a valid value. */
    public float appliedZoomRatio() {
        Camera boundCamera = camera;
        ZoomState state = boundCamera == null ? null : boundCamera.getCameraInfo().getZoomState().getValue();
        float ratio = state == null ? Float.NaN : state.getZoomRatio();
        return Float.isFinite(ratio) && ratio > 0f ? ratio : Float.NaN;
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

    public void setSetupGeometryListener(CameraGeometryProvider.Listener listener) { setupGeometry.setListener(listener); }
    public void refreshSetupGeometry() { setupGeometry.refresh(); }

    public void stop() {
        setupGeometry.onStopped();
        activeCallbacks = null;
        lastRequest=null;
        lastStreamFrameNanos = 0L;
        boundAnalysisSize = null;
        bindingGeneration++;
        cameraControlGeneration++;
        cameraControlHandler.removeCallbacksAndMessages(null);
        if (cameraProvider != null) cameraProvider.unbindAll();
        camera = null;
        boundSelection=null;boundRequestedSelection=null;setupZoomAcknowledged=false;confirmedPhysical=null;
        if(zoomObservedCamera!=null&&setupZoomObserver!=null)zoomObservedCamera.getCameraInfo().getZoomState().removeObserver(setupZoomObserver);
        zoomObservedCamera=null;setupZoomObserver=null;publishSetup();
    }

    @Override
    public void close() {
        stop();
        analyzerExecutor.shutdownNow();
    }
}
