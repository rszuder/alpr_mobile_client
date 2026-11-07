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
    @Implements(value=CameraController.class,isInAndroidSdk=false)
    public static class CameraIo {
        CameraSetupSnapshot applied;boolean streamReady=true;
        @Implementation public CameraSetupSnapshot setupSnapshot(){return applied;}
        @Implementation public boolean isStreamReady(Size size,boolean high){return streamReady;}
    }
}
