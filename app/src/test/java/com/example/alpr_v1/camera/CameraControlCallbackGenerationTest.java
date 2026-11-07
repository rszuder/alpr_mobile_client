package com.example.alpr_v1.camera;

import android.app.Application;
import android.os.Looper;
import androidx.camera.core.*;
import androidx.camera.view.PreviewView;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.lifecycle.MutableLiveData;
import com.example.alpr_v1.MainActivity;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Exercises the actual CameraController animation/future boundary with a synthetic CameraX port. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CameraControlCallbackGenerationTest {
    CameraController control;
    final List<CallbackToFutureAdapter.Completer<Void>> futures=new ArrayList<>();
    @Before public void setup()throws Exception {
        MainActivity a=Robolectric.buildActivity(MainActivity.class).get();
        a.setTheme(com.example.alpr_v1.R.style.Theme_ALPR_v1);
        control=new CameraController(a,a,new PreviewView(a));
        ZoomState zoom=new ZoomState(){
            public float getZoomRatio(){return 1f;}public float getMinZoomRatio(){return 1f;}
            public float getMaxZoomRatio(){return 4f;}public float getLinearZoom(){return 0f;}
        };
        MutableLiveData<ZoomState> live=new MutableLiveData<>(zoom);
        CameraInfo info=(CameraInfo)Proxy.newProxyInstance(CameraInfo.class.getClassLoader(),new Class<?>[]{CameraInfo.class},
                (proxy,method,args)->method.getName().equals("getZoomState")?live:null);
        CameraControl port=(CameraControl)Proxy.newProxyInstance(CameraControl.class.getClassLoader(),new Class<?>[]{CameraControl.class},
                (proxy,method,args)->{
                    if(method.getName().equals("setZoomRatio"))return CallbackToFutureAdapter.<Void>getFuture(c->{futures.add(c);return "synthetic camera zoom";});
                    return null;
                });
        Camera camera=(Camera)Proxy.newProxyInstance(Camera.class.getClassLoader(),new Class<?>[]{Camera.class},
                (proxy,method,args)->method.getName().equals("getCameraInfo")?info:method.getName().equals("getCameraControl")?port:null);
        Field f=CameraController.class.getDeclaredField("camera");f.setAccessible(true);f.set(control,camera);
    }
    @After public void close(){control.close();}
    void animate(float ratio,Result result){control.setZoomRatio(ratio,result,16);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));}
    @Test public void obsoleteFutureFailureCannotFailANewerReturn(){
        Result zoom=new Result(),back=new Result();animate(1.8f,zoom);CallbackToFutureAdapter.Completer<Void> old=futures.get(futures.size()-1);
        animate(1f,back);CallbackToFutureAdapter.Completer<Void> current=futures.get(futures.size()-1);
        old.setException(new IllegalStateException("old zoom cancelled"));Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,zoom.errors);assertEquals(0,zoom.successes);assertEquals(0,back.errors);
        current.set(null);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,back.successes);
    }
    @Test public void stoppedBindingDropsOldSuccessAndFailure(){
        Result zoom=new Result();animate(1.8f,zoom);CallbackToFutureAdapter.Completer<Void> old=futures.get(futures.size()-1);
        control.stop();old.setException(new IllegalStateException("binding stopped"));Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,zoom.errors);assertEquals(0,zoom.successes);assertTrue(Float.isNaN(control.appliedZoomRatio()));
    }
    @Test public void currentFailureIsReportedAndObservedZoomIsSeparate(){
        Result zoom=new Result();animate(1.8f,zoom);futures.get(futures.size()-1).setException(new IllegalStateException("real failure"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,zoom.errors);assertEquals(1f,control.appliedZoomRatio(),0f);
    }
    static class Result implements CameraController.ControlCallback {
        int successes,errors;
        @Override public void onSuccess(float ratio){successes++;}
        @Override public void onError(Throwable error){errors++;}
    }
}
