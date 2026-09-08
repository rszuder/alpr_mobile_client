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
    @Test public void featureOnlyBoundaryInvalidatesIdleSceneAndLateOcrWithoutDetectorRun() throws Exception {
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
                    watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(
                            new NormalizedBounds(.15f,.15f,.85f,.85f)),null,0f));
                    ContinuityStamp old=pipeline.currentContinuityStamp(1L);
                    long now=android.os.SystemClock.elapsedRealtimeNanos();
                    assertNull(pipeline.observeStaticLuma(old,corners(),200,200,now,false));
                    cycle.finishBaseline();cycle.nextRefinement(false);
                    assertTrue(pipeline.isStaticIdle());
                    byte[] blank=new byte[40000];Arrays.fill(blank,(byte)100);
                    assertNull(pipeline.observeStaticLuma(old,blank,200,200,now+100_000_000L,false));
                    assertNull(pipeline.observeStaticLuma(old,blank,200,200,now+200_000_000L,false));
                    long began=android.os.SystemClock.elapsedRealtimeNanos();
                    SceneTransitionDecision decision=pipeline.observeStaticLuma(old,blank,200,200,now+300_000_000L,false);
                    assertNotNull(decision);assertEquals(SceneTransitionAction.HARD_RESET,decision.action);
                    assertEquals("static_alpr_features_changed",decision.reason);
                    assertEquals(old.sceneGeneration+1,pipeline.sceneContinuitySnapshot().sceneGeneration);
                    assertFalse(pipeline.isStaticIdle());assertFalse(pipeline.isCurrentContinuityStamp(old));
                    assertTrue((Boolean)get(pipeline,"trackingResetRequested"));
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
    private static byte[] corners() {
        byte[] image=new byte[40000];Arrays.fill(image,(byte)100);
        for(int row=0;row<4;row++)for(int col=0;col<4;col++) {
            int left=42+col*31,top=42+row*31;
            for(int y=top;y<top+9;y++)for(int x=left;x<left+9;x++)image[y*200+x]=(byte)(x<left+5?50:170);
        }
        return image;
    }
}
