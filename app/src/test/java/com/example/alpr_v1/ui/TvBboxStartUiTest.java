package com.example.alpr_v1.ui;

import android.Manifest;
import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Looper;
import android.util.Size;
import android.view.View;
import android.widget.TextView;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.*;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.metrics.*;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.*;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real Activity readiness, buttons and measurement START; only camera IO is replaced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvBboxStartUiTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    MainActivity main;TvSetupController tv;CameraIo camera;ResearchSessionViewModel sessions;CaptureGalleryViewModel gallery;
    SharedPreferences prefs;CameraPreviewGeometry geometry;Object ready;ResearchSessionStore store;ResearchExecutionConfig config;
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{
        Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);try{return m.invoke(o,args);}catch(InvocationTargetException e){throw new AssertionError(n,e.getCause());}
    }
    Object call(String n)throws Exception{return call(main,n,new Class<?>[0]);}
    @Before public void setup()throws Exception{
        main=Robolectric.buildActivity(MainActivity.class).get();main.setTheme(R.style.Theme_ALPR_v1);main.setContentView(R.layout.activity_main);
        prefs=main.getSharedPreferences("f22-ui-synthetic",0);prefs.edit().clear().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true)
                .putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget").putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").commit();
        set(main,"uiPreferences",prefs);set(main,"experimentModeEnabled",true);set(main,"analysisScreenForeground",true);set(main,"cameraPreviewActive",true);
        sessions=new ResearchSessionViewModel(RuntimeEnvironment.getApplication());set(main,"researchSessions",sessions);set(main,"experimentSession",sessions.experiment);
        ((ExecutorService)get(sessions,"finalizer")).submit(()->{}).get(10,TimeUnit.SECONDS);
        gallery=new CaptureGalleryViewModel();set(main,"captureGalleryState",gallery);set(main,"metricsCollector",gallery.metricsCollector());
        set(main,"capturedCrops",gallery.capturedCrops());set(main,"lastCaptureByTrack",gallery.lastCaptureByTrack());set(main,"recognitionHistory",gallery.recognitionHistory());
        set(main,"analysisStartButton",main.findViewById(R.id.analysis_start_button));set(main,"collectionToggle",new MaterialButton(main));set(main,"resolvedCropLimit",49);
        PreviewView preview=main.findViewById(R.id.camera_preview);set(main,"previewView",preview);set(main,"overlayView",main.findViewById(R.id.detection_overlay));
        set(main,"livePresentation",new LivePresentationController(new View(main),new View(main),new TextView(main),new TextView(main),new View(main),new TextView(main),new View(main),new TextView(main),new TextView(main),new View(main),new TextView(main),new TextView(main),new TextView(main)));
        ModelRegistry registry=new ModelRegistry(main);set(main,"modelRegistry",registry);set(main,"pipeline",new AlprPipeline(main,registry,gallery.metricsCollector(),new AutoTuneManager(main)));
        set(main,"deviceProfile",DeviceProfile.capture(main));set(main,"cameraResolutionCatalog",new CameraResolutionCatalog(main));set(main,"cameraResolutionSelection",CameraResolutionSelection.exact(new Size(640,400)));
        set(main,"experimentPreviewSize",new Size(640,400));set(main,"crashSessionMarker",new CrashSessionMarker(main));
        CameraController control=new CameraController(main,main,preview);set(main,"cameraController",control);camera=org.robolectric.shadow.api.Shadow.extract(control);
        tv=new TvSetupController(main,preview,main.findViewById(R.id.detection_overlay),()->true,()->{},(n,d)->{});set(main,"tvSetup",tv);
        geometry=TvBboxCropTest.geometry(640,400);set(tv,"geometry",geometry);confirm("2026-11-06T20:00:00Z");camera.applied=TvBboxCropTest.setup(1);
        set(tv,"cameraState",new CameraSetupControl.State(Collections.emptyList(),camera.applied.selection,1,1,4,camera.applied,false,"",1));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);
    }
    void confirm(String at)throws Exception{
        set(tv,"calibration",TvSetupCalibration.confirm(Arrays.asList(new Point2(.125f,.15f),new Point2(.875f,.20f),new Point2(.85f,.85f),new Point2(.15f,.80f)),geometry,1,at));
    }
    void prepare(RoiBudgetPolicy policy)throws Exception{
        config=TvBboxAdmissionTest.config(policy,tv.selectionForInput());
        ExperimentSession.Prepared p=sessions.experiment.prepare("roi_budget",config.variant,(TimerConfig)get(main,"experimentTimerConfig"),(ThermalConfig)get(main,"experimentThermalConfig"),new ExperimentIdentity("UNOFFICIAL-TV-BBOX","SYNTHETIC",1,"synthetic=true"),config);
        store=TvBboxProducerFixture.prepareFor(temp.newFolder(),p);set(sessions,"store",store);
        Class<?> type=Class.forName(MainActivity.class.getName()+"$PreparedAnalysisStart");Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        ready=ctor.newInstance(config,p,store,0);set(main,"preparedExperimentStart",ready);((AlprPipeline)get(main,"pipeline")).setResearchExecutionConfig(config);
    }
    String blocker()throws Exception{return call("experimentStartBlocker").toString();}
    MaterialButton button()throws Exception{return (MaterialButton)get(main,"analysisStartButton");}
    ExperimentStartRequest intent()throws Exception{return (ExperimentStartRequest)get(main,"experimentStartRequest");}
    @After public void close()throws Exception{
        sessions.experiment.finish(ExperimentSession.CompletionReason.MANUAL);
        ((android.os.Handler)get(main,"experimentReadinessHandler")).removeCallbacksAndMessages(null);
        ((android.os.Handler)get(main,"experimentTimerHandler")).removeCallbacksAndMessages(null);
        ((ExecutorService)get(main,"pipelineInferenceExecutor")).shutdownNow();
        ((ExecutorService)get(main,"directLumaTrackingExecutor")).shutdownNow();
        ((ExecutorService)get(main,"previewTrackingExecutor")).shutdownNow();
        if(store!=null)TvBboxProducerFixture.close(store);
        Method a=ResearchSessionViewModel.class.getDeclaredMethod("onCleared");a.setAccessible(true);a.invoke(sessions);
        Method b=CaptureGalleryViewModel.class.getDeclaredMethod("onCleared");b.setAccessible(true);b.invoke(gallery);
    }
    void begin(RoiBudgetPolicy policy)throws Exception{
        prepare(policy);assertEquals("READY",blocker());call("renderAnalysisControls");assertTrue(button().isEnabled());assertFalse(sessions.experiment.isRunning());assertFalse(store.accepting());
        set(ready,"startWall",System.currentTimeMillis());set(ready,"startElapsed",System.nanoTime());set(ready,"startMonotonic",System.nanoTime());
        assertEquals(true,call(main,"beginAnalysisMeasurement",new Class<?>[]{ready.getClass()},ready));TvBboxProducerFixture.drain(store);
        assertTrue(sessions.experiment.isRunning());assertTrue(store.accepting());assertEquals(policy,sessions.experiment.frozenExecutionConfig().roiBudgetPolicy);
        assertEquals(store.sessionId(),sessions.experiment.sessionId());
        assertEquals(config.tvBboxCrop().calibrationDigest,store.tvSetupSnapshot().getString("digest_sha256"));
        set(main,"preparedExperimentStart",null);call("requestExperimentStart");assertFalse(intent().pending());
    }
    @Test public void readyR0StartsThroughActualMeasurementBoundary()throws Exception{begin(RoiBudgetPolicy.FULL_FRAME);}
    @Test public void readyR1StartsThroughActualMeasurementBoundary()throws Exception{begin(RoiBudgetPolicy.ONE_ROI);}
    @Test public void readyR2StartsThroughActualMeasurementBoundary()throws Exception{begin(RoiBudgetPolicy.TWO_ROI);}
    @Test public void missingStaleAndDraftCalibrationCannotArmStart()throws Exception{
        set(tv,"calibration",TvSetupCalibration.empty());assertEquals("INPUT_MODE",blocker());call("requestExperimentStart");assertFalse(intent().pending());assertFalse(button().isEnabled());
        confirm("2026-11-06T20:00:00Z");set(tv,"calibration",((TvSetupCalibration)get(tv,"calibration")).invalidated("REQUIRES_CONFIRMATION"));assertEquals("INPUT_MODE",blocker());
        Class<? extends Enum> phase=(Class<? extends Enum>)get(tv,"phase").getClass();set(tv,"phase",Enum.valueOf(phase,"EDITING"));assertTrue(call("tvBboxStartReason").toString().contains("zatwierdź"));
    }
    @Test public void autoZoomAndUnconfirmedCameraBlockWithoutChangingPreferences()throws Exception{
        prefs.edit().putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true).commit();assertEquals("INPUT_MODE",blocker());assertTrue(prefs.getBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,false));
        prefs.edit().putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,false).commit();camera.applied=null;assertEquals("INPUT_MODE",blocker());
    }
    @Test public void actualCameraAndZoomMismatchCannotReuseReadyGeometry()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);
        for(CameraSetupSnapshot wrong:new CameraSetupSnapshot[]{new CameraSetupSnapshot(new CameraSelection("other",null),1),TvBboxCropTest.setup(2)}){
            camera.applied=wrong;assertEquals("INPUT_MODE",blocker());call("renderAnalysisControls");assertFalse(button().isEnabled());
        }
    }
    @Test public void rotationCropViewportAndSourceSizeChangesBlockStart()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);
        for(CameraPreviewGeometry wrong:new CameraPreviewGeometry[]{
                TvBboxCropTest.geometry(400,640,90,640,400,new double[]{0,0,640,400},800,600,1),
                TvBboxCropTest.geometry(620,360,0,640,400,new double[]{10,20,630,380},800,600,1),
                TvBboxCropTest.geometry(640,400,0,640,400,new double[]{0,0,640,400},900,600,1),TvBboxCropTest.geometry(1280,720)}){
            set(tv,"geometry",wrong);assertEquals("INPUT_MODE",blocker());assertFalse(tv.matchesInputSelection(config.tvBboxCrop()));
        }
    }
    @Test public void newConfirmationInvalidatesPreparedDigestEvenWithSameCorners()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);assertEquals("READY",blocker());confirm("2026-11-06T20:01:00Z");
        assertEquals("INPUT_MODE",blocker());assertThrows(IllegalStateException.class,()->tv.freezeInputSelection(config.tvBboxCrop()));
    }
    @Test public void invalidationCancelsPendingIntentAndDiscardsPreparedStore()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);intent().request();set(tv,"calibration",((TvSetupCalibration)get(tv,"calibration")).invalidated("SYNTHETIC_STALE"));
        call("refreshExperimentStartReadiness");((ExecutorService)get(main,"pipelineInferenceExecutor")).submit(()->{}).get(10,TimeUnit.SECONDS);
        assertFalse(intent().pending());assertNull(get(main,"preparedExperimentStart"));assertFalse(sessions.experiment.isRunning());assertFalse(store.accepting());assertEquals(ResearchSessionStore.State.ERROR,store.state());
    }
    @Test public void preparedModelsWithoutOperatorIntentNeverStart()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);call("refreshExperimentStartReadiness");assertFalse(intent().pending());assertFalse(sessions.experiment.isRunning());assertFalse(store.accepting());
        intent().request();intent().cancel();call("refreshExperimentStartReadiness");assertFalse(sessions.experiment.isRunning());assertFalse(store.accepting());
    }
    @Test public void resumeInvalidationClosesAdmissionEvenWhenCameraIsNotStarted()throws Exception{
        begin(RoiBudgetPolicy.FULL_FRAME);assertFalse((boolean)get(main,"cameraStarted"));
        TvSetupController resumed=new TvSetupController(main,main.findViewById(R.id.camera_preview),main.findViewById(R.id.detection_overlay),()->true,()->{},(name,details)->{
            try{call(main,"onTvSetupEvent",new Class<?>[]{String.class,org.json.JSONObject.class},name,details);}catch(Exception e){throw new AssertionError(e);}
        });
        ((ExecutorService)get(main,"pipelineInferenceExecutor")).submit(()->resumed.resumeSession(store.tvSetupSnapshot())).get(10,TimeUnit.SECONDS);
        assertFalse(store.accepting());assertEquals(ResearchSessionStore.State.FINALIZING,store.state());
        assertFalse(resumed.matchesInputSelection(config.tvBboxCrop()));
    }
    @Test public void commonStorageModelsThermalAndFreshStreamGatesRemain()throws Exception{
        assertEquals("MODELS",blocker());prepare(RoiBudgetPolicy.FULL_FRAME);assertEquals("READY",blocker());
        set(sessions,"finalizing",true);assertEquals("PREVIOUS_SESSION",blocker());set(sessions,"finalizing",false);
        camera.streamReady=false;assertEquals("CAMERA",blocker());camera.streamReady=true;
        set(main,"experimentPreparationFailed",true);assertEquals("ERROR",blocker());set(main,"experimentPreparationFailed",false);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA);assertEquals("PERMISSION",blocker());
    }
    @Test public void thermalGateMustStabilizeBeforeButtonEnables()throws Exception{
        set(main,"experimentThermalConfig",ThermalConfig.of(true,40,1,1));prepare(RoiBudgetPolicy.FULL_FRAME);assertEquals("THERMAL",blocker());call("renderAnalysisControls");assertFalse(button().isEnabled());
        set(main,"prestartThermalReady",true);assertEquals("READY",blocker());
    }
    @Test public void converterMetadataIsCheckedBeforeAnyCropOrModels()throws Exception{
        TvBboxCrop selected=tv.selectionForInput();GeometryProviderFixture hardware=new GeometryProviderFixture(main.findViewById(R.id.camera_preview),main);
        hardware.width=640;hardware.height=400;hardware.rotation=0;hardware.crop=new Rect(0,0,640,400);hardware.bitmap=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
        CameraImageConverter.Result converted=CameraImageConverter.convert(hardware.image());
        try(TvBboxCrop.Frame input=tv.openInput(selected,converted)){assertEquals(selected.width(),input.input.getWidth());}
        converted.bitmap.recycle();hardware.crop=new Rect(5,5,635,395);CameraImageConverter.Result changed=CameraImageConverter.convert(hardware.image());
        try{assertThrows(IllegalArgumentException.class,()->tv.openInput(selected,changed));}finally{changed.bitmap.recycle();hardware.bitmap.recycle();}
    }
    @Test public void tvObservationMissingMaskAndLegacyImageEntryAreRejectedBeforeDecisions()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);AlprPipeline p=(AlprPipeline)get(main,"pipeline");
        assertNotNull(p.sceneObservationDomain());
        assertThrows(IllegalArgumentException.class,()->p.observeStaticLuma(p.currentContinuityStamp(1),new byte[10000],100,100,1,false));
        assertThrows(IllegalArgumentException.class,()->p.process(null,null,null));
        assertEquals(ResearchSessionStore.State.PREPARED,store.state());
    }
    @Test public void fullFrameConfigurationHasNoTvObservationDomain()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);AlprPipeline p=(AlprPipeline)get(main,"pipeline");
        com.example.alpr_v1.continuity.SceneObservationDomain old=p.sceneObservationDomain();
        p.setResearchExecutionConfig(config.withInputMode(ResearchInputMode.CAMERA_FULL_FRAME));assertNull(p.sceneObservationDomain());
        p.observeStaticLuma(p.currentContinuityStamp(1),new byte[10000],100,100,1,false);
        p.setResearchExecutionConfig(config);assertNotSame(old,p.sceneObservationDomain());
        call(main,"handleObservationFailure",new Class<?>[]{com.example.alpr_v1.continuity.SceneObservationDomain.class,com.example.alpr_v1.continuity.ContinuityStamp.class,RuntimeException.class},
                old,p.currentContinuityStamp(2),new IllegalArgumentException("stale_worker"));
        assertEquals(ResearchSessionStore.State.PREPARED,store.state());assertFalse(sessions.finalizing());
    }
    @Test public void actualStaticLumaR0CannotWakeForExterior()throws Exception{assertStaticExterior(RoiBudgetPolicy.FULL_FRAME);}
    @Test public void actualStaticLumaR1CannotWakeForExterior()throws Exception{assertStaticExterior(RoiBudgetPolicy.ONE_ROI);}
    @Test public void actualStaticLumaR2CannotWakeForExterior()throws Exception{assertStaticExterior(RoiBudgetPolicy.TWO_ROI);}
    void assertStaticExterior(RoiBudgetPolicy policy)throws Exception{
        prefs.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,com.example.alpr_v1.continuity.SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName()).commit();
        prepare(policy);AlprPipeline p=(AlprPipeline)get(main,"pipeline");
        com.example.alpr_v1.acquisition.StaticSceneCycle cycle=(com.example.alpr_v1.acquisition.StaticSceneCycle)get(p,"staticCycle");
        cycle.reset(p.sceneContinuitySnapshot().sceneGeneration);cycle.finishBaseline();p.nextStaticRefinement(false);assertTrue(p.isStaticIdle());
        Object gate=get(p,"frameGate");assertFalse((boolean)get(gate,"immediateFrameRequested"));
        set(main,"cameraStarted",true);
        com.example.alpr_v1.continuity.SceneObservationDomain d=p.sceneObservationDomain();
        com.example.alpr_v1.continuity.ObservationMask mask=d.sourceMask(640,400);
        byte[] a=new byte[640*400];for(int y=0;y<400;y++)for(int x=0;x<640;x++)a[y*640+x]=(byte)(40+((x/12+y/12)%2)*150);
        long scene=p.sceneContinuitySnapshot().sceneGeneration;
        for(int i=0;i<8;i++){
            byte[] b=a.clone();Random random=new Random(i);
            if(i>0)for(int y=0;y<400;y++)for(int x=0;x<640;x++)if(!mask.contains(x,y))b[y*640+x]=(byte)random.nextInt(256);
            LumaFrame frame=TvObservationMappingTest.frame(geometry,b,240,i+1);
            call(main,"processDirectLumaFrame",new Class<?>[]{LumaFrame.class,com.example.alpr_v1.continuity.SceneObservationDomain.class},frame,d);
        }
        assertEquals(scene,p.sceneContinuitySnapshot().sceneGeneration);
        assertEquals(8L,get(p,"staticLumaCallbacks"));
        assertTrue(p.isStaticIdle());assertFalse((boolean)get(gate,"immediateFrameRequested"));
        assertTrue((long)get(main,"lastDirectLumaFrameNanos")>0);
        assertFalse(((java.util.concurrent.atomic.AtomicBoolean)get(main,"pipelineFrameInFlight")).get());
        set(main,"cameraStarted",false);
    }
    @Test public void interiorStaticChangeWakesIdleAndRequestsExactlyOneNewFrame()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);AlprPipeline p=(AlprPipeline)get(main,"pipeline");
        com.example.alpr_v1.acquisition.StaticSceneCycle cycle=(com.example.alpr_v1.acquisition.StaticSceneCycle)get(p,"staticCycle");
        long scene=p.sceneContinuitySnapshot().sceneGeneration;cycle.reset(scene);cycle.finishBaseline();p.nextStaticRefinement(false);assertTrue(p.isStaticIdle());
        com.example.alpr_v1.continuity.SceneObservationDomain d=p.sceneObservationDomain();
        byte[] a=new byte[640*400],b=new byte[a.length];for(int y=0;y<400;y++)for(int x=0;x<640;x++){int i=y*640+x;a[i]=(byte)(40+((x/12+y/12)%2)*150);b[i]=(byte)(230-(a[i]&255));}
        com.example.alpr_v1.continuity.SceneObservationDomain.Frame first=d.luma(TvObservationMappingTest.frame(geometry,a,240,1)),second=d.luma(TvObservationMappingTest.frame(geometry,b,240,2));
        p.observeStaticLuma(p.currentContinuityStamp(1),first.gray,first.width,first.height,0,false,first.mask);
        assertNotNull(p.observeStaticLuma(p.currentContinuityStamp(2),second.gray,second.width,second.height,150_000_000L,false,second.mask));
        assertEquals(scene+1,p.sceneContinuitySnapshot().sceneGeneration);assertFalse(p.isStaticIdle());assertTrue((boolean)get(get(p,"frameGate"),"immediateFrameRequested"));
        assertNull(p.observeStaticLuma(p.currentContinuityStamp(3),second.gray,second.width,second.height,300_000_000L,false,second.mask));
        assertEquals(scene+1,p.sceneContinuitySnapshot().sceneGeneration);
    }
    @Test public void actualDynamicPreviewFallbackIgnoresExteriorWithoutDirectLuma()throws Exception{
        prepare(RoiBudgetPolicy.TWO_ROI);AlprPipeline p=(AlprPipeline)get(main,"pipeline");
        ResearchExecutionConfig b=TvBboxAdmissionTest.base(RoiBudgetPolicy.TWO_ROI,false);
        ResearchExecutionConfig dynamic=new ResearchExecutionConfig(b.experimentType,b.variant,b.roiBudgetPolicy,b.recognitionProfile,b.cameraRequestedResolution,
                b.lockEnabled,b.autoZoomEnabled,b.vehicleTrackingEnabled,b.plateTrackingEnabled,b.temporalMzEnabled,b.adaptiveFrameGateEnabled,
                b.vehicle,b.plate,b.character,null,false,0,com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY)
                .withCameraSetup(config.cameraSetup).withTvBboxCrop(config.tvBboxCrop());
        p.setResearchExecutionConfig(dynamic);p.setSceneHandlingMode(com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY);
        set(main,"cameraStarted",true);set(main,"previewSceneAnchorPending",true);
        com.example.alpr_v1.continuity.SceneObservationDomain d=p.sceneObservationDomain();long scene=p.sceneContinuitySnapshot().sceneGeneration;
        long ui=((java.util.concurrent.atomic.AtomicLong)get(main,"uiSceneGeneration")).get(),transform=((java.util.concurrent.atomic.AtomicLong)get(main,"uiCameraTransformGeneration")).get();
        com.example.alpr_v1.ui.PreviewPresentationBarrier barrier=(com.example.alpr_v1.ui.PreviewPresentationBarrier)get(main,"previewPresentationBarrier");
        for(int i=0;i<4;i++){
            Bitmap image=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);Random random=new Random(i);
            com.example.alpr_v1.continuity.ObservationMask mask=d.sourceMask(640,400);
            for(int y=0;y<600;y++)for(int x=0;x<800;x++){
                double[] point=geometry.viewToNormalized(x+.5,y+.5);int sx=(int)Math.floor(point[0]*640),sy=(int)Math.floor(point[1]*400);
                int value=mask.contains(sx,sy)?((x/8+y/8)%2)*150+40:random.nextInt(256);image.setPixel(x,y,android.graphics.Color.rgb(value,value,value));
            }
            call(main,"processPreviewTrackingFrame",new Class<?>[]{Bitmap.class,long.class,long.class,long.class,com.example.alpr_v1.continuity.ContinuityStamp.class,
                    com.example.alpr_v1.continuity.SceneObservationDomain.class,CameraPreviewGeometry.class},image,ui,transform,barrier.capture(),p.currentContinuityStamp(i+1),d,geometry);
        }
        assertTrue(((com.example.alpr_v1.vision.SceneAnchorGuard)get(main,"previewSceneAnchorGuard")).hasAnchor());
        assertEquals(scene,p.sceneContinuitySnapshot().sceneGeneration);assertEquals(0L,get(main,"lastDirectLumaFrameNanos"));assertFalse(barrier.active());
        set(main,"cameraStarted",false);Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void invalidObservationClosesAdmissionBeforeUiStopAndHasNoFakeMt()throws Exception{
        begin(RoiBudgetPolicy.FULL_FRAME);AlprPipeline p=(AlprPipeline)get(main,"pipeline");
        com.example.alpr_v1.continuity.SceneObservationDomain d=p.sceneObservationDomain();
        ((ExecutorService)get(main,"directLumaTrackingExecutor")).submit(()->{
            try{call(main,"handleObservationFailure",new Class<?>[]{com.example.alpr_v1.continuity.SceneObservationDomain.class,com.example.alpr_v1.continuity.ContinuityStamp.class,RuntimeException.class},
                    d,p.currentContinuityStamp(1),new IllegalArgumentException("TV_OBSERVATION_STALE_GEOMETRY"));}catch(Exception e){throw new AssertionError(e);}
        }).get(10,TimeUnit.SECONDS);
        assertFalse(store.accepting());
        // The UI runnable remains queued: admission is already closed without waiting for it.
        TvBboxProducerFixture.drain(store);
        java.io.File attempts=new java.io.File(store.directory(),"samples/attempts.jsonl");
        assertTrue(!attempts.exists()||attempts.length()==0);
    }
    @Implements(value=CameraController.class,isInAndroidSdk=false)
    public static class CameraIo {
        CameraSetupSnapshot applied;boolean streamReady=true;
        @Implementation public CameraSetupSnapshot setupSnapshot(){return applied;}
        @Implementation public boolean isStreamReady(Size size,boolean high){return streamReady;}
    }
}
