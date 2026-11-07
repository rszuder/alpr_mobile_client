package com.example.alpr_v1;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.camera.CameraController.ControlCallback;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.ui.*;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Actual Activity controls with synthetic state, without onCreate/model loading or a camera. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=AcquisitionAutoZoomControlsTest.FakeCamera.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AcquisitionAutoZoomControlsTest {
    MainActivity ui;
    CaptureGalleryViewModel gallery;
    MetricsCollector metrics;
    SharedPreferences prefs;
    FakeCamera camera;
    AutoZoomController az;
    final List<MainActivity> activities=new ArrayList<>();

    @Before public void setup()throws Exception {
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("controls-f16-synthetic",0);
        prefs.edit().clear().commit();
        gallery=new CaptureGalleryViewModel();metrics=gallery.metricsCollector();
        ui=activity();CameraController control=new CameraController(ui,ui,new PreviewView(ui));
        camera=org.robolectric.shadow.api.Shadow.extract(control);set(ui,"cameraController",control);
        az=(AutoZoomController)get(ui,"autoZoomController");
    }

    MainActivity activity()throws Exception {
        MainActivity a=Robolectric.buildActivity(MainActivity.class).get();a.setTheme(R.style.Theme_ALPR_v1);
        activities.add(a);
        set(a,"uiPreferences",prefs);set(a,"experimentSession",new ExperimentSession());
        set(a,"metricsCollector",metrics);set(a,"captureGalleryState",gallery);
        set(a,"capturedCrops",gallery.capturedCrops());set(a,"lastCaptureByTrack",gallery.lastCaptureByTrack());
        set(a,"recognitionHistory",gallery.recognitionHistory());set(a,"resolvedCropLimit",49);
        set(a,"collectionToggle",new MaterialButton(a));set(a,"autoZoomButton",new MaterialButton(a));
        set(a,"overlayView",new DetectionOverlayView(a,null));
        set(a,"pipeline",new AlprPipeline(a,new ModelRegistry(a),metrics,new AutoTuneManager(a)));
        set(a,"livePresentation",new LivePresentationController(new View(a),new View(a),new TextView(a),new TextView(a),
                new View(a),new TextView(a),new View(a),new TextView(a),new TextView(a),new View(a),
                new TextView(a),new TextView(a),new TextView(a)));
        return a;
    }

    @After public void close()throws Exception {
        for(MainActivity a:activities){
            ((android.os.Handler)get(a,"autoZoomHandler")).removeCallbacksAndMessages(null);
            ((ExecutorService)get(a,"pipelineInferenceExecutor")).shutdownNow();
        }
        Method clear=CaptureGalleryViewModel.class.getDeclaredMethod("onCleared");clear.setAccessible(true);clear.invoke(gallery);
    }
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{
        Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);
        try{return m.invoke(o,args);}catch(InvocationTargetException e){throw new AssertionError(n,e.getCause());}
    }
    void call(String n)throws Exception{call(ui,n,new Class<?>[0]);}
    boolean flag(String n)throws Exception{return (boolean)get(ui,n);}
    MaterialButton button(String n)throws Exception{return (MaterialButton)get(ui,n);}
    void frame()throws Exception{
        set(ui,"cameraStarted",true);long g=(long)get(ui,"analysisControlGeneration");
        call(ui,"onNormalAnalysisFrame",new Class<?>[]{long.class},g);
    }
    void idleReturn(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AutoZoomController.SETTLING_MILLIS));}
    void dynamic(){prefs.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()).commit();}
    AutoZoomController.Decision startCycle()throws Exception{
        dynamic();set(ui,"cameraStarted",true);prefs.edit().putBoolean("auto_zoom_enabled",true).commit();az.setEnabled(true);
        AutoZoomController.Decision d=az.requestRefinement(new AutoZoomController.Sample(7,.5f,.5f,.12f,.4,false,3,true,true,false,"ABC"));
        assertEquals(AutoZoomController.Action.REQUEST_ZOOM,d.action);return d;
    }
    void physicalZoom()throws Exception{
        AutoZoomController.Decision d=startCycle();
        call(ui,"startAutoZoomTransform",new Class<?>[]{AutoZoomController.Decision.class,float.class},d,1.8f);
        assertEquals(1,camera.zoom.size());
    }

    @Test public void acquisitionArmsBeforeStartWithoutCreatingAnActiveSession()throws Exception {
        call("renderCollectionControl");assertTrue(button("collectionToggle").isEnabled());
        call("toggleCollection");assertTrue(prefs.getBoolean("collection_armed",false));
        assertFalse(flag("collectionActive"));assertEquals("",get(ui,"collectionSessionId"));
        assertTrue(gallery.capturedCrops().isEmpty());assertTrue(gallery.recognitionHistory().newestFirst().isEmpty());
        assertTrue(button("collectionToggle").getContentDescription().toString().contains("START"));
        assertFalse(metrics.isMeasurementSessionActive());assertEquals(0,camera.commands());
        call("updateAutoZoomButton");
        render("controls-prestart-armed");
    }
    @Test public void latestSelectionIsAppliedOnlyAfterARealStartFrame()throws Exception {
        call("toggleCollection");set(ui,"cameraStarted",true);
        call("applyNormalCollectionSelection");assertFalse(flag("collectionActive"));
        call("toggleCollection");frame();assertFalse(flag("collectionActive"));assertEquals("",get(ui,"collectionSessionId"));
        call("toggleCollection");assertTrue(flag("collectionActive"));assertTrue(((String)get(ui,"collectionSessionId")).startsWith("s-"));
        assertTrue(gallery.capturedCrops().isEmpty());
    }
    @Test public void stopAndRestartKeepPreferenceAndNormalSessionIdentity()throws Exception {
        call("toggleCollection");frame();String id=(String)get(ui,"collectionSessionId");
        call("toggleCollection");assertFalse(flag("collectionActive"));
        call("toggleCollection");assertTrue(flag("collectionActive"));assertEquals(id,get(ui,"collectionSessionId"));
        set(ui,"cameraStarted",false);call("pauseCropCollectionForStoppedAnalysis");
        assertFalse(flag("collectionActive"));assertTrue(prefs.getBoolean("collection_armed",false));
        assertTrue(button("collectionToggle").isEnabled());frame();assertTrue(flag("collectionActive"));assertEquals(id,get(ui,"collectionSessionId"));
        MainActivity recreated=activity();assertTrue((boolean)call(recreated,"normalCollectionArmed",new Class<?>[0]));
        assertFalse((boolean)get(recreated,"collectionActive"));assertEquals("",get(recreated,"collectionSessionId"));
    }
    @Test public void failedStartAndStaleFirstFrameDoNotCollectOrEraseTheSetting()throws Exception {
        call("toggleCollection");long old=(long)get(ui,"analysisControlGeneration");
        call("pauseCropCollectionForStoppedAnalysis");set(ui,"cameraStarted",true);
        call(ui,"onNormalAnalysisFrame",new Class<?>[]{long.class},old);
        assertFalse(flag("collectionActive"));assertEquals("",get(ui,"collectionSessionId"));assertTrue(prefs.getBoolean("collection_armed",false));
    }
    @Test public void normalAcquisitionDoesNotInheritAnExperimentId()throws Exception {
        set(ui,"collectionSessionId","exp-SYNTHETIC-previous");call("toggleCollection");frame();
        assertTrue(flag("collectionActive"));assertTrue(((String)get(ui,"collectionSessionId")).startsWith("s-"));
    }
    @Test public void experimentalAutoAndExportGatesPreserveNormalPreference()throws Exception {
        call("toggleCollection");set(ui,"experimentModeEnabled",true);call("renderCollectionControl");
        assertFalse(button("collectionToggle").isEnabled());assertTrue(button("collectionToggle").getText().toString().contains("AUTO"));
        call("toggleCollection");assertTrue(prefs.getBoolean("collection_armed",false));assertFalse(flag("collectionActive"));
        set(ui,"experimentModeEnabled",false);set(ui,"exportInProgress",true);call("toggleCollection");call("renderCollectionControl");
        assertFalse(button("collectionToggle").isEnabled());assertTrue(prefs.getBoolean("collection_armed",false));
    }
    @Test public void prestartAutoZoomOnOffDoesNotTouchPipelineOrManualZoom()throws Exception {
        set(ui,"pipeline",null);set(ui,"currentCameraZoomRatio",2f);
        call("updateAutoZoomButton");assertTrue(button("autoZoomButton").isEnabled());
        call("toggleAutoZoom");assertTrue(az.enabled());assertTrue(button("autoZoomButton").isActivated());
        call("toggleAutoZoom");assertFalse(az.enabled());assertFalse(button("autoZoomButton").isActivated());
        assertEquals(0,camera.commands());assertEquals(2f,(float)get(ui,"currentCameraZoomRatio"),0f);
    }
    @Test public void offDoesNotResetManualZoomInRunningAnalysis()throws Exception {
        dynamic();set(ui,"cameraStarted",true);set(ui,"currentCameraZoomRatio",2f);
        prefs.edit().putBoolean("auto_zoom_enabled",true).commit();az.setEnabled(true);
        call("toggleAutoZoom");assertFalse(az.enabled());assertEquals(0,camera.commands());
        assertEquals(2f,(float)get(ui,"currentCameraZoomRatio"),0f);
    }
    @Test public void runningResearchKeepsItsFrozenAutoZoomSetting()throws Exception {
        ExperimentSession session=(ExperimentSession)get(ui,"experimentSession");
        ExperimentSession.Prepared p=session.prepare("pipeline_comparison","SYNTHETIC",TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity("SYNTHETIC-CONTROLS","SYNTHETIC",1,"synthetic=true"),null);
        assertTrue(session.startPrepared(p,1000,1000));az.setEnabled(true);
        call("updateAutoZoomButton");assertFalse(button("autoZoomButton").isEnabled());
        call("toggleAutoZoom");assertTrue(az.enabled());assertFalse(prefs.contains("auto_zoom_enabled"));assertEquals(0,camera.commands());
    }
    @Test public void autoZoomCanBeDisabledDuringNormalStartPreparation()throws Exception {
        call("toggleAutoZoom");Object gate=get(ui,"analysisStartGate");gate.getClass().getMethod("begin").invoke(gate);
        call("updateAutoZoomButton");assertTrue(button("autoZoomButton").isEnabled());call("toggleAutoZoom");
        assertFalse(az.enabled());assertFalse(prefs.getBoolean("auto_zoom_enabled",true));assertEquals(0,camera.commands());
    }
    @Test public void offCancelsPendingAutoZoomWithoutMovingTheCamera()throws Exception {
        startCycle();((DetectionOverlayView)get(ui,"overlayView")).setZoomPresentationActive(true);
        Runnable pending=()->{throw new AssertionError("Cancelled zoom started");};
        set(ui,"pendingAutoZoomStartRunnable",pending);((android.os.Handler)get(ui,"autoZoomHandler")).postDelayed(pending,50);
        call("toggleAutoZoom");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        assertFalse(az.enabled());assertNull(get(ui,"pendingAutoZoomStartRunnable"));assertEquals(AutoZoomController.State.DISABLED,az.state());
        assertFalse((boolean)get(get(ui,"overlayView"),"zoomPresentationActive"));
        assertFalse(flag("autoZoomMemoryVisible"));assertEquals(0,camera.commands());
    }
    @Test public void offDuringZoomReturnsInTheOriginalTransformAndIgnoresOldCallbacks()throws Exception {
        physicalZoom();camera.zoom.get(0).onProgress(1.4f);
        long generation=((java.util.concurrent.atomic.AtomicLong)get(ui,"uiCameraTransformGeneration")).get();
        call("toggleAutoZoom");assertFalse(az.enabled());assertTrue(button("autoZoomButton").isEnabled());
        assertEquals(1,camera.back.size());assertEquals(1f,(float)get(ui,"cameraTransformStartZoomRatio"),0f);
        assertEquals(generation,((java.util.concurrent.atomic.AtomicLong)get(ui,"uiCameraTransformGeneration")).get());
        assertEquals("auto_zoom_retry",az.captureSource());
        render("controls-az-off-returning");
        camera.zoom.get(0).onProgress(1.8f);camera.zoom.get(0).onSuccess(1.8f);camera.zoom.get(0).onError(new IOException("stale"));
        assertEquals(1.4f,(float)get(ui,"currentCameraZoomRatio"),0f);assertTrue(flag("cameraTransformInProgress"));
        camera.back.get(0).onSuccess(1f);idleReturn();
        assertEquals(AutoZoomController.State.DISABLED,az.state());assertFalse(flag("cameraTransformInProgress"));assertFalse(az.enabled());
        assertEquals("normal",az.captureSource());
    }
    @Test public void offDuringRetryReturnsAndOnDuringReturnWaitsForIt()throws Exception {
        startCycle();az.onZoomSettled(10);set(ui,"currentCameraZoomRatio",1.8f);
        call("toggleAutoZoom");assertFalse(az.enabled());assertEquals(1,camera.back.size());
        call("toggleAutoZoom");assertTrue(az.enabled());assertEquals(AutoZoomController.State.RETURNING,az.state());
        assertEquals(0,camera.zoom.size());assertEquals(1,camera.back.size());
        call("toggleAutoZoom");assertFalse(az.enabled());assertEquals(1,camera.back.size());
        camera.back.get(0).onSuccess(1f);idleReturn();assertEquals(AutoZoomController.State.DISABLED,az.state());
    }
    @Test public void onDuringReturnArmsOnlyAfterPhysicalReturnSettles()throws Exception {
        startCycle();az.onZoomSettled(10);set(ui,"currentCameraZoomRatio",1.8f);
        call("toggleAutoZoom");call("toggleAutoZoom");
        assertEquals(AutoZoomController.State.RETURNING,az.state());assertEquals(0,camera.zoom.size());
        camera.back.get(0).onSuccess(1f);idleReturn();
        assertTrue(az.enabled());assertEquals(AutoZoomController.State.READY,az.state());assertFalse(flag("cameraTransformInProgress"));
    }
    @Test public void offReleasesAStaticSampleAlreadySelectedByTheWorker()throws Exception {
        AlprPipeline pipeline=(AlprPipeline)get(ui,"pipeline");
        com.example.alpr_v1.acquisition.StaticSceneCycle cycle=
                (com.example.alpr_v1.acquisition.StaticSceneCycle)get(pipeline,"staticCycle");
        cycle.reset(1);((Map<Long,AutoZoomController.Sample>)get(cycle,"candidates")).put(7L,
                new AutoZoomController.Sample(7,.5f,.5f,.12f,.4,false,3,true,true,false,"ABC"));
        cycle.finishBaseline();set(ui,"cameraStarted",true);az.setEnabled(true);
        prefs.edit().putBoolean("auto_zoom_enabled",true).commit();
        ExecutorService worker=(ExecutorService)get(ui,"pipelineInferenceExecutor");
        call(ui,"handleStaticRefinement",new Class<?>[]{com.example.alpr_v1.pipeline.PipelineResult.class},new Object[]{null});
        worker.submit(()->{}).get(10,TimeUnit.SECONDS);assertTrue(cycle.refining());
        call("toggleAutoZoom");
        for(int i=0;i<3;i++){Shadows.shadowOf(Looper.getMainLooper()).idle();worker.submit(()->{}).get(10,TimeUnit.SECONDS);}
        assertFalse(cycle.refining());assertEquals(com.example.alpr_v1.acquisition.StaticSceneCycle.Phase.STATIC_IDLE,cycle.phase());
        assertFalse(az.enabled());assertEquals(0,camera.commands());
    }
    @Test public void stopInvalidatesEvenSuccessfulCallbacksAndDelayedSettling()throws Exception {
        physicalZoom();CameraController.ControlCallback old=camera.zoom.get(0);old.onSuccess(1.8f);
        call("resetAutoZoomForStoppedCamera");set(ui,"cameraStarted",true); // a subsequent binding
        old.onProgress(1.8f);old.onSuccess(1.8f);old.onError(new IOException("stale after stop"));idleReturn();
        assertFalse(flag("cameraTransformInProgress"));assertEquals(1f,(float)get(ui,"currentCameraZoomRatio"),0f);
        assertEquals(AutoZoomController.State.READY,az.state());assertEquals(0,camera.back.size());
    }
    @Test public void failedReturnLeavesAutoZoomOffAndKeepsObservedZoom()throws Exception {
        startCycle();az.onZoomSettled(10);set(ui,"currentCameraZoomRatio",1.8f);call("toggleAutoZoom");
        camera.observed=1.25f;camera.back.get(0).onError(new IOException("synthetic control error"));
        assertFalse(az.enabled());assertFalse(flag("cameraTransformInProgress"));assertEquals(1.25f,(float)get(ui,"currentCameraZoomRatio"),0f);
        assertTrue(button("autoZoomButton").isEnabled());assertEquals(AutoZoomController.Action.NONE,
                az.requestRefinement(new AutoZoomController.Sample(8,.5f,.5f,.1f,.3,false,3,true,true,false,"DEF")).action);
    }

    void render(String name)throws Exception {
        String target=System.getenv("F16_CONTROLS_RENDERS");if(target==null)return;
        LinearLayout row=new LinearLayout(ui);row.setPadding(16,16,16,16);
        row.addView(button("collectionToggle"),new LinearLayout.LayoutParams(180,64));
        row.addView(button("autoZoomButton"),new LinearLayout.LayoutParams(92,64));
        row.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));row.layout(0,0,360,100);
        Bitmap bitmap=Bitmap.createBitmap(360,100,Bitmap.Config.ARGB_8888);row.draw(new android.graphics.Canvas(bitmap));
        File dir=new File(target);assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream stream=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}bitmap.recycle();
    }
    @Implements(value=CameraController.class,isInAndroidSdk=false)
    public static class FakeCamera {
        final List<ControlCallback> zoom=new ArrayList<>(),back=new ArrayList<>();float observed=1f;
        @Implementation public void zoomAndFocus(float ratio,float x,float y,ControlCallback c,long ms){zoom.add(c);}
        @Implementation public void setZoomRatio(float ratio,ControlCallback c,long ms){assertEquals(1f,ratio,0f);back.add(c);}
        @Implementation public float appliedZoomRatio(){return observed;}
        int commands(){return zoom.size()+back.size();}
    }
}
