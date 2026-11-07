package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.acquisition.StaticSceneCycle;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.pipeline.AlprPipeline;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StaticSceneFeatureResetInstrumentedTest {
    private enum Boundary { ALPR_FEATURE_LOSS, SCENE_FEATURE_LOSS, SCENE_FEATURE_ADDITION }
    @Test public void featureOnlyBoundaryInvalidatesIdleSceneAndLateOcrWithoutDetectorRun() throws Exception {
        verifyBoundary(Boundary.ALPR_FEATURE_LOSS);
    }
    @Test public void unchangedPlateChangedBackgroundUnblocksNextFrameAndRejectsLateResult() throws Exception {
        verifyBoundary(Boundary.SCENE_FEATURE_LOSS);
    }
    @Test public void newLowContrastBackgroundUnblocksIdleAndRejectsOldGeneration() throws Exception {
        verifyBoundary(Boundary.SCENE_FEATURE_ADDITION);
    }
    private void verifyBoundary(Boundary boundary) throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("alpr_ui",Context.MODE_PRIVATE);
        String oldMode=prefs.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE,SceneHandlingMode.DYNAMIC_CONTINUITY.wireName());
        boolean oldExperiment=prefs.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false);
        prefs.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName())
                .putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                try {
                    AlprPipeline pipeline=(AlprPipeline)get(activity,"pipeline");
                    pipeline.setSceneHandlingMode(SceneHandlingMode.STRICT_SCENE_BOUNDARY);
                    StaticSceneWatcher watcher=(StaticSceneWatcher)get(pipeline,"staticWatcher");
                    StaticSceneCycle cycle=(StaticSceneCycle)get(pipeline,"staticCycle");
                    boolean addition = boundary == Boundary.SCENE_FEATURE_ADDITION;
                    watcher.arm(addition ? new StaticSceneWatchRegions(null, Collections.singletonList(
                            new NormalizedBounds(.30f,.30f,.70f,.70f)), 0f)
                            : boundary == Boundary.SCENE_FEATURE_LOSS ? new StaticSceneWatchRegions(null, Collections.singletonList(
                            new NormalizedBounds(.02f,.02f,.10f,.10f)), 0f)
                            : new StaticSceneWatchRegions(Collections.singletonList(
                            new NormalizedBounds(.15f,.15f,.85f,.85f)),null,0f));
                    ContinuityStamp old=pipeline.currentContinuityStamp(1L);
                    long now=android.os.SystemClock.elapsedRealtimeNanos();
                    byte[] baseline = addition ? originalScene() : corners();
                    assertNull(pipeline.observeStaticLuma(old,baseline,200,200,now,false));
                    cycle.finishBaseline();cycle.nextRefinement(false);
                    assertTrue(pipeline.isStaticIdle());
                    byte[] blank = addition ? addedBackground(baseline) : new byte[40000];
                    if (!addition) Arrays.fill(blank,(byte)100);
                    assertNull(pipeline.observeStaticLuma(old,blank,200,200,now+100_000_000L,false));
                    assertNull(pipeline.observeStaticLuma(old,blank,200,200,now+200_000_000L,false));
                    long began=android.os.SystemClock.elapsedRealtimeNanos();
                    SceneTransitionDecision decision=pipeline.observeStaticLuma(old,blank,200,200,now+300_000_000L,false);
                    assertNotNull(decision);assertEquals(SceneTransitionAction.HARD_RESET,decision.action);
                    assertEquals(addition ? "static_scene_features_added"
                            : boundary == Boundary.SCENE_FEATURE_LOSS ? "static_scene_features_changed"
                            : "static_alpr_features_changed",decision.reason);
                    assertEquals(old.sceneGeneration+1,pipeline.sceneContinuitySnapshot().sceneGeneration);
                    assertFalse(pipeline.isStaticIdle());assertFalse(pipeline.isCurrentContinuityStamp(old));
                    assertEquals(ContinuityResultDisposition.REJECT_ALL, new ContinuityGenerationGate().evaluate(
                            pipeline.currentContinuityStamp(2L), old));
                    assertTrue((Boolean)get(pipeline,"trackingResetRequested"));
                    java.lang.reflect.Method waiting = AlprPipeline.class.getDeclaredMethod("staticWaiting");
                    waiting.setAccessible(true);
                    assertFalse((Boolean)waiting.invoke(pipeline));
                    com.example.alpr_v1.autotune.AdaptiveFrameGate gate =
                            (com.example.alpr_v1.autotune.AdaptiveFrameGate)get(pipeline,"frameGate");
                    assertTrue((Boolean)get(gate,"immediateFrameRequested"));
                    assertTrue(gate.shouldProcess(1L));
                    assertFalse((Boolean)get(gate,"immediateFrameRequested"));
                    assertNull(pipeline.observeStaticLuma(old,blank,200,200,now+400_000_000L,false));
                    assertEquals(old.sceneGeneration+1,pipeline.sceneContinuitySnapshot().sceneGeneration);
                    ContinuityStamp current=pipeline.currentContinuityStamp(2L);
                    for (int i=5;i<10;i++) assertNull(pipeline.observeStaticLuma(current,blank,200,200,
                            now+i*100_000_000L,false));
                    assertEquals(old.sceneGeneration+1,pipeline.sceneContinuitySnapshot().sceneGeneration);
                    long duration=android.os.SystemClock.elapsedRealtimeNanos()-began;
                    android.util.Log.i("ALPR_STATIC_FEATURE_QA","feature_reset_ms="+duration/1_000_000.0);
                    assertTrue("Feature reset cannot wait for inference",duration<500_000_000L);
                } catch(Exception error) {throw new AssertionError(error);}
            });
        } finally {
            prefs.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,oldMode)
                    .putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,oldExperiment).commit();
        }
    }
    private static Object get(Object instance,String name)throws Exception {
        Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);
    }
    private static byte[] originalScene() {
        byte[] image = new byte[40000]; Arrays.fill(image, (byte)100);
        for (int y : new int[]{72,112}) for (int x : new int[]{72,112}) corner(image,x,y,50,170);
        return image;
    }
    private static byte[] addedBackground(byte[] baseline) {
        byte[] image = baseline.clone();
        for (int y=22;y<180;y+=31) for (int x=22;x<180;x+=31) {
            if (x>=50&&x<=140&&y>=50&&y<=140) continue;
            corner(image,x,y,90,110);
        }
        return image;
    }
    private static void corner(byte[] image,int left,int top,int low,int high) {
        for (int y=top;y<top+9;y++) for (int x=left;x<left+9;x++)
            image[y*200+x]=(byte)(x<left+5?low:high);
    }
    private static byte[] corners() {
        byte[] image=new byte[40000];Arrays.fill(image,(byte)100);
        for(int row=0;row<4;row++)for(int col=0;col<4;col++) {
            int left=42+col*31,top=42+row*31;
            for(int y=top;y<top+9;y++)for(int x=left;x<left+9;x++)image[y*200+x]=(byte)(x<left+5?50:170);
        }
        return image;
    }
}
