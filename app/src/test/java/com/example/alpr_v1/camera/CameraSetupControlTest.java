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
        CameraInfo info=(CameraInfo)Proxy.newProxyInstance(CameraInfo.class.getClassLoader(),new Class[]{CameraInfo.class},(p,m,a)->m.getName().equals("getZoomState")?zoom:object(p,m.getName(),a));
        CameraControl controls=(CameraControl)Proxy.newProxyInstance(CameraControl.class.getClassLoader(),new Class[]{CameraControl.class},(p,m,a)->{
            if(m.getName().equals("setZoomRatio")){requests.add((Float)a[0]);return CallbackToFutureAdapter.<Void>getFuture(c->{futures.add(c);return "controlled zoom acknowledgement";});}return object(p,m.getName(),a);});
        Camera camera=(Camera)Proxy.newProxyInstance(Camera.class.getClassLoader(),new Class[]{Camera.class},(p,m,a)->{
            if(m.getName().equals("getCameraInfo"))return info;if(m.getName().equals("getCameraControl"))return controls;return object(p,m.getName(),a);});
        ReflectionHelpers.setField(controller,"camera",camera);ReflectionHelpers.setField(controller,"boundRequestedSelection",CameraSelection.defaultBack());
        ReflectionHelpers.setField(controller,"boundSelection",new CameraSelection("0",null));ReflectionHelpers.setField(controller,"setupZoomAcknowledged",true);
        ((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(controller,"setupFrameSequence")).set(1);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(10));ReflectionHelpers.setField(controller,"lastStreamFrameNanos",SystemClock.elapsedRealtimeNanos());
        ReflectionHelpers.setField(controller,"boundAnalysisSize",new Size(640,480));
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
