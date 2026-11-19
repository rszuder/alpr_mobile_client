package com.example.alpr_v1.camera;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Size;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraControl;
import androidx.camera.core.ZoomState;
import androidx.camera.view.PreviewView;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.lifecycle.MutableLiveData;
import com.example.alpr_v1.R;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32) @LooperMode(LooperMode.Mode.PAUSED)
public class CameraSetupControlTest {
    private final List<CallbackToFutureAdapter.Completer<androidx.camera.core.FocusMeteringResult>> focusFutures=new ArrayList<>();
    private final List<androidx.camera.core.FocusMeteringAction> focusActions=new ArrayList<>();
    private boolean focusSupported=true;
    private int focusCancels;
    public static class Host extends AppCompatActivity {@Override public void onCreate(Bundle saved){setTheme(R.style.Theme_ALPR_v1);super.onCreate(saved);setContentView(new PreviewView(this));}}
    private ActivityController<Host> host;
    private CameraController controller;
    private final MutableLiveData<ZoomState> zoom=new MutableLiveData<>(zoom(1));
    private final List<CallbackToFutureAdapter.Completer<Void>> futures=new ArrayList<>();
    private final List<Float> requests=new ArrayList<>();
    private static ZoomState zoom(float ratio){return new ZoomState(){public float getZoomRatio(){return ratio;}public float getMinZoomRatio(){return .5f;}public float getMaxZoomRatio(){return 4;}public float getLinearZoom(){return 0;}};}
    private static Object object(Object instance,String name,Object[] args){if(name.equals("hashCode"))return System.identityHashCode(instance);if(name.equals("equals"))return instance==args[0];if(name.equals("toString"))return "Synthetic camera IO";throw new UnsupportedOperationException(name);}
    private void start(){
        host=Robolectric.buildActivity(Host.class).setup().visible();controller=new CameraController(host.get(),host.get(),new PreviewView(host.get()));
        CameraInfo info=(CameraInfo)Proxy.newProxyInstance(CameraInfo.class.getClassLoader(),new Class[]{CameraInfo.class},(p,m,a)->{
            if(m.getName().equals("getZoomState"))return zoom;if(m.getName().equals("isFocusMeteringSupported"))return focusSupported;return object(p,m.getName(),a);});
        CameraControl controls=(CameraControl)Proxy.newProxyInstance(CameraControl.class.getClassLoader(),new Class[]{CameraControl.class},(p,m,a)->{
            if(m.getName().equals("setZoomRatio")){requests.add((Float)a[0]);return CallbackToFutureAdapter.<Void>getFuture(c->{futures.add(c);return "controlled zoom acknowledgement";});}
            if(m.getName().equals("startFocusAndMetering")){focusActions.add((androidx.camera.core.FocusMeteringAction)a[0]);return CallbackToFutureAdapter.<androidx.camera.core.FocusMeteringResult>getFuture(c->{focusFutures.add(c);return "SYNTHETIC focus acknowledgement";});}
            if(m.getName().equals("cancelFocusAndMetering")){focusCancels++;return CallbackToFutureAdapter.<Void>getFuture(c->{c.set(null);return "synthetic AF cancel";});}
            return object(p,m.getName(),a);});
        Camera camera=(Camera)Proxy.newProxyInstance(Camera.class.getClassLoader(),new Class[]{Camera.class},(p,m,a)->{
            if(m.getName().equals("getCameraInfo"))return info;if(m.getName().equals("getCameraControl"))return controls;return object(p,m.getName(),a);});
        ReflectionHelpers.setField(controller,"camera",camera);ReflectionHelpers.setField(controller,"boundRequestedSelection",CameraSelection.defaultBack());
        ReflectionHelpers.setField(controller,"boundSelection",new CameraSelection("0",null));ReflectionHelpers.setField(controller,"setupZoomAcknowledged",true);
        ((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(controller,"setupFrameSequence")).set(1);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(10));ReflectionHelpers.setField(controller,"lastStreamFrameNanos",SystemClock.elapsedRealtimeNanos());
        ReflectionHelpers.setField(controller,"boundAnalysisSize",new Size(640,480));
    }
    @Test public void final176FocusResultLifecycleAndFrozenSessionObservation()throws Exception{
        start();ReflectionHelpers.setField(controller,"focusAfAutoSupported",true);
        com.example.alpr_v1.setup.TvBboxCrop tv=com.example.alpr_v1.setup.TvBboxCropTest.selection(640,480);
        com.example.alpr_v1.setup.CameraPreviewGeometry geometry=tv.geometry;
        ReflectionHelpers.setField(controller,"bindingGeneration",geometry.bindingGeneration);
        com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
        android.content.SharedPreferences prefs=host.get().getSharedPreferences("synthetic-af-main-guard",0);
        prefs.edit().putBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,true).commit();
        ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"experimentModeEnabled",true);ReflectionHelpers.setField(main,"cameraController",controller);
        assertFalse((Boolean)ReflectionHelpers.callInstanceMethod(main,"focusBlocksF6Start"));
        controller.focusOnSource(geometry,320,240);assertEquals("PENDING",controller.focusStatus());assertFalse(controller.focusHeld());
        assertTrue((Boolean)ReflectionHelpers.callInstanceMethod(main,"focusBlocksF6Start"));
        prefs.edit().putBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,false).commit();
        assertFalse("Historical mode has no new AF gate",(Boolean)ReflectionHelpers.callInstanceMethod(main,"focusBlocksF6Start"));
        prefs.edit().putBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,true).commit();
        assertFalse(focusActions.get(0).isAutoCancelEnabled());assertEquals(1,focusActions.get(0).getMeteringPointsAf().size());
        assertTrue(focusActions.get(0).getMeteringPointsAe().isEmpty());assertTrue(focusActions.get(0).getMeteringPointsAwb().isEmpty());
        focusFutures.get(0).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();assertTrue(controller.focusHeld());
        assertFalse((Boolean)ReflectionHelpers.callInstanceMethod(main,"focusBlocksF6Start"));assertTrue(requests.isEmpty());
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(7));assertTrue(controller.focusHeld());assertEquals(0,focusCancels);
        int binding=ReflectionHelpers.getField(controller,"bindingGeneration");
        controller.start((image,stamp)->{},error->{throw new AssertionError(error);},new Size(640,480),false);
        assertTrue(controller.focusHeld());assertEquals(binding,(int)ReflectionHelpers.getField(controller,"bindingGeneration"));assertEquals(1,focusActions.size());
        org.json.JSONObject frozen=controller.observation();
        assertEquals("HELD",frozen.getJSONObject("focus").getString("status"));
        // Real serializer/exporter: optional diagnostics survive future changes independently.
        com.example.alpr_v1.experiment.ResearchExecutionConfig base=ReflectionHelpers.callInstanceMethod(new com.example.alpr_v1.experiment.Final180EvidenceTest(),"config");
        com.example.alpr_v1.experiment.ResearchExecutionConfig config=new com.example.alpr_v1.experiment.ResearchExecutionConfig("roi_budget","R0",com.example.alpr_v1.pipeline.RoiBudgetPolicy.FULL_FRAME,
                com.example.alpr_v1.pipeline.RecognitionProfile.BALANCED,"640x480",false,false,false,true,false,false,
                base.vehicle,base.plate,base.character).withCameraSetup(tv.cameraSetup).withTvBboxCrop(tv).withFinal180(
                com.example.alpr_v1.experiment.Final180PhotoRoi.prepare(new org.json.JSONArray("[{x:.25,y:.25},{x:.75,y:.25},{x:.75,y:.75},{x:.25,y:.75}]"),tv));
        java.io.File root=java.nio.file.Files.createTempDirectory("synthetic-f6-focus").toFile();
        com.example.alpr_v1.experiment.ResearchSessionStore store=com.example.alpr_v1.experiment.TvBboxProducerFixture.prepareOnly(root,config);
        try{
            store.setCameraObservationBeforeStart(frozen);frozen.getJSONObject("focus").put("status","MUTATED_TEST_VALUE");
            store.startAt(System.currentTimeMillis(),System.nanoTime());
            controller.releaseFocus("operator");assertFalse(controller.focusRequested());assertFalse(controller.focusHeld());
            java.io.File archive=com.example.alpr_v1.experiment.TvBboxProducerFixture.finish(store,config);
            assertEquals("PARTIAL",com.example.alpr_v1.experiment.F6ProducerContractVerifier.validate(archive));
            try(java.util.zip.ZipFile z=new java.util.zip.ZipFile(archive)){
                org.json.JSONObject metadata=new org.json.JSONObject(new String(z.getInputStream(z.getEntry("session.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                assertEquals("HELD",metadata.getJSONObject("camera_observation").getJSONObject("focus").getString("status"));
            }
            String out=System.getenv("F6_CAMERA_FIXTURES");if(out!=null){java.nio.file.Files.createDirectories(java.nio.file.Paths.get(out));java.nio.file.Files.copy(archive.toPath(),java.nio.file.Paths.get(out,"synthetic-focus-snapshot-stop-no-frame-PARTIAL.alprsession"));}
        }finally{com.example.alpr_v1.experiment.TvBboxProducerFixture.close(store);}
        controller.focusOnSource(geometry,320,240);int pending=focusFutures.size()-1;assertEquals("PENDING",controller.focusStatus());
        controller.releaseFocus("cancel");focusFutures.get(pending).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();assertFalse(controller.focusHeld());
        focusSupported=false;controller.focusOnSource(geometry,320,240);assertEquals("UNAVAILABLE",controller.focusStatus());assertFalse(controller.focusHeld());
        controller.releaseFocus("cancel");focusSupported=true;controller.focusOnSource(geometry,320,240);
        focusFutures.get(focusFutures.size()-1).set(androidx.camera.core.FocusMeteringResult.create(false));ShadowLooper.shadowMainLooper().idle();assertEquals("FAILED",controller.focusStatus());
        controller.releaseFocus("retry");ReflectionHelpers.setField(controller,"focusAfAutoSupported",false);
        controller.focusOnSource(geometry,320,240);assertEquals("UNAVAILABLE",controller.focusStatus());assertFalse(controller.focusHeld());
        controller.releaseFocus("retry");ReflectionHelpers.setField(controller,"focusAfAutoSupported",null);
        controller.focusOnSource(geometry,320,240);assertEquals("UNAVAILABLE",controller.focusStatus());assertTrue(controller.observation().getJSONObject("focus").isNull("supported"));
        controller.releaseFocus("retry");ReflectionHelpers.setField(controller,"focusAfAutoSupported",true);controller.focusOnSource(geometry,320,240);
        focusFutures.get(focusFutures.size()-1).setException(new IllegalStateException("SYNTHETIC_AF_FAILURE"));ShadowLooper.shadowMainLooper().idle();assertEquals("FAILED",controller.focusStatus());
        controller.releaseFocus("retry");controller.focusOnSource(geometry,320,240);int timedOut=focusFutures.size()-1;
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(6100));assertEquals("FAILED",controller.focusStatus());
        focusFutures.get(timedOut).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();assertFalse(controller.focusHeld());
        controller.releaseFocus("retry");controller.focusOnSource(geometry,320,240);int late=focusFutures.size()-1;
        controller.stop();focusFutures.get(late).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();
        assertFalse(controller.focusHeld());assertFalse(controller.focusRequested());assertEquals("RELEASED",controller.focusStatus());
    }
    @After public void stop(){if(controller!=null)controller.close();if(host!=null)host.pause().stop().destroy();}
    @Test public void staleZoomAcknowledgementCannotAdmitAnOlderRequest(){
        start();assertEquals(1,controller.setupSnapshot().zoomRatio,0);controller.apply(CameraSelection.defaultBack(),2);controller.apply(CameraSelection.defaultBack(),3);
        assertNull(controller.setupSnapshot());assertEquals(2,requests.size());
        zoom.setValue(zoom(2));futures.get(0).set(null);ShadowLooper.shadowMainLooper().idle();assertNull(controller.setupSnapshot());
        zoom.setValue(zoom(3));futures.get(1).set(null);ShadowLooper.shadowMainLooper().idle();
        assertNull("Zoom acknowledgement alone must not admit an old frame",controller.setupSnapshot());
        ((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(controller,"setupFrameSequence")).incrementAndGet();
        assertEquals(3,controller.setupSnapshot().zoomRatio,0);assertEquals("0",controller.setupSnapshot().selection.logicalCameraId);
    }
    @Test public void stoppedCameraCannotBeRevivedByDelayedZoomCompletion(){
        start();controller.apply(CameraSelection.defaultBack(),2);controller.stop();zoom.setValue(zoom(2));futures.get(0).set(null);ShadowLooper.shadowMainLooper().idle();
        assertNull(controller.setupSnapshot());assertFalse(controller.isStreamReady(new Size(640,480),false));
    }
    @Test public void selectingAnotherRouteCannotReuseOldReadyStream(){
        start();assertTrue(controller.isStreamReady(new Size(640,480),false));controller.apply(new CameraSelection("0","2"),1);
        assertNull(controller.setupSnapshot());assertFalse(controller.isStreamReady(new Size(640,480),false));assertTrue(requests.isEmpty());
    }
    @Test public void matchingAnalysisStartReusesPreviewBindingWithoutChangingCalibrationGeneration(){
        start();Object bound=ReflectionHelpers.getField(controller,"camera");
        int generation=ReflectionHelpers.getField(controller,"bindingGeneration");
        controller.start((image,stamp)->{},error->{throw new AssertionError(error);},new Size(640,480),false);
        assertSame(bound,ReflectionHelpers.getField(controller,"camera"));
        assertEquals(generation,(int)ReflectionHelpers.getField(controller,"bindingGeneration"));
        assertNotNull(ReflectionHelpers.getField(controller,"activeCallbacks"));assertTrue(controller.isStreamReady(new Size(640,480),false));
    }
}
