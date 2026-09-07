package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.acquisition.StaticSceneCycle;
import com.example.alpr_v1.camera.LumaFrame;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.pipeline.AlprPipeline;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class StaticSceneResetInstrumentedTest {
    @Test public void lumaCutsRepeatedStaticIdleScenesEvenWithAnActivePresentationBarrier() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences("alpr_ui", Context.MODE_PRIVATE);
        String previousMode = preferences.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE,
                SceneHandlingMode.DYNAMIC_CONTINUITY.wireName());
        boolean previousExperiment = preferences.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false);
        preferences.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,
                SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName())
                .putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    AlprPipeline pipeline = (AlprPipeline) field(activity,"pipeline").get(activity);
                    pipeline.setSceneHandlingMode(SceneHandlingMode.STRICT_SCENE_BOUNDARY);
                    StaticSceneCycle cycle = (StaticSceneCycle) field(pipeline,"staticCycle").get(pipeline);
                    PreviewPresentationBarrier barrier = (PreviewPresentationBarrier)
                            field(activity,"previewPresentationBarrier").get(activity);
                    DetectionOverlayView overlay = (DetectionOverlayView) field(activity,"overlayView").get(activity);
                    Method process = MainActivity.class.getDeclaredMethod("processDirectLumaFrame",LumaFrame.class);
                    process.setAccessible(true);
                    Field started = field(activity,"cameraStarted");
                    boolean wasStarted = started.getBoolean(activity);
                    started.setBoolean(activity,true);
                    try {
                        for (int scene = 0; scene < 3; scene++) {
                            long before = pipeline.sceneContinuitySnapshot().sceneGeneration;
                            byte[] base = pattern(scene % 2 == 0);
                            process.invoke(activity,luma(base,scene*2L+1));
                            cycle.finishBaseline(); cycle.nextRefinement(false);
                            assertTrue(pipeline.isStaticIdle());
                            overlay.setItems(Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                                    new RectF(.2f,.2f,.6f,.6f),Collections.emptyList(),"P1",1L,false)));
                            barrier.activate();
                            long oldPresentation = barrier.capture();
                            long began = android.os.SystemClock.elapsedRealtimeNanos();
                            process.invoke(activity,luma(pattern(scene % 2 != 0),scene*2L+2));
                            assertEquals(before+1,pipeline.sceneContinuitySnapshot().sceneGeneration);
                            assertFalse(pipeline.isStaticIdle());
                            assertFalse(barrier.active());
                            assertFalse(barrier.permits(oldPresentation));
                            assertTrue(((List<?>)field(overlay,"items").get(overlay)).isEmpty());
                            assertTrue(field(pipeline,"trackingResetRequested").getBoolean(pipeline));
                            assertTrue("Reset must not wait for MP/MT/MZ or preview polling",
                                    android.os.SystemClock.elapsedRealtimeNanos()-began < 500_000_000L);
                        }
                    } finally { started.setBoolean(activity,wasStarted); }
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        } finally {
            preferences.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,previousMode)
                    .putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,previousExperiment).commit();
        }
    }

    private static Field field(Object object,String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static LumaFrame luma(byte[] gray,long sequence) throws ReflectiveOperationException {
        java.lang.reflect.Constructor<LumaFrame> constructor = LumaFrame.class.getDeclaredConstructor(
                byte[].class,int.class,int.class,long.class,long.class,SourceTimestampDomain.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gray,100,100,sequence,sequence*100_000_000L,
                SourceTimestampDomain.UNKNOWN);
    }
    private static byte[] pattern(boolean inverted) {
        byte[] gray = new byte[10000];
        for (int y=0;y<100;y++) for (int x=0;x<100;x++)
            gray[y*100+x]=(byte)((((x/4+y/4)%2==0)^inverted)?40:160);
        return gray;
    }
}
