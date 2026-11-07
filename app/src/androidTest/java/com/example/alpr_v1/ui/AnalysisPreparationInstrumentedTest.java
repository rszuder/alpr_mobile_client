package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.ExperimentSession;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AnalysisPreparationInstrumentedTest {
    @Test public void experimentStartKeepsLabelAndResumesPausedOrFailedPreparationWithRealButtons() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("alpr_ui",Context.MODE_PRIVATE);
        boolean exp=prefs.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false);
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(),android.Manifest.permission.CAMERA);
        CountDownLatch occupied=new CountDownLatch(1),release=new CountDownLatch(1);
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            try {
                scenario.onActivity(activity -> {
                    ((ExecutorService)get(activity,"pipelineInferenceExecutor")).execute(() -> {
                        occupied.countDown();
                        try { release.await(); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
                    });
                });
                assertTrue(occupied.await(2,TimeUnit.SECONDS));
                scenario.onActivity(activity -> {
                    set(activity,"experimentModeEnabled",true);
                    set(activity,"experimentPreviewPaused",true);
                    // Supply preview configuration without opening a camera or running a model.
                    set(activity,"cameraPreviewActive",true);
                    set(activity,"experimentPreviewSize",invoke(activity,"chooseAnalysisSize",new Class<?>[0]));
                    com.google.android.material.button.MaterialButton start=activity.findViewById(R.id.analysis_start_button);
                    invoke(activity,"renderAnalysisControls",new Class<?>[0]);
                    assertTrue(start.isEnabled());
                    assertEquals(activity.getString(R.string.camera_action_start),start.getText().toString());
                    start.performClick();
                    assertFalse((Boolean)get(activity,"experimentPreviewPaused"));
                    assertTrue(((ExperimentStartRequest)get(activity,"experimentStartRequest")).pending());
                    assertEquals(activity.getString(R.string.camera_action_cancel),start.getText().toString());
                    assertFalse(((ExperimentSession)get(activity,"experimentSession")).isRunning());
                    start.performClick();
                    assertFalse(((ExperimentStartRequest)get(activity,"experimentStartRequest")).pending());
                    assertEquals(activity.getString(R.string.camera_action_start),start.getText().toString());
                    assertTrue(start.isEnabled());

                    set(activity,"experimentPreparationFailed",true);
                    set(activity,"experimentPreparationError","synthetic preparation failure");
                    set(activity,"cameraPreviewActive",true);
                    invoke(activity,"renderAnalysisControls",new Class<?>[0]);
                    assertTrue(start.isEnabled());
                    assertEquals(activity.getString(R.string.camera_action_start),start.getText().toString());
                    start.performClick();
                    assertFalse((Boolean)get(activity,"experimentPreparationFailed"));
                    assertTrue(((ExperimentStartRequest)get(activity,"experimentStartRequest")).pending());
                    start.performClick();
                    assertFalse(((ExperimentStartRequest)get(activity,"experimentStartRequest")).pending());
                });
            } finally { release.countDown(); }
        } finally { prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,exp).commit(); }
    }
    @Test public void slowPreparationDoesNotBlockUiOrStartTimerAndCancelPreventsLateCameraStart() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("alpr_ui",Context.MODE_PRIVATE);
        boolean exp=prefs.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false);
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();
        CountDownLatch occupied=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicReference<ExecutorService> executor=new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ExecutorService worker=(ExecutorService)get(activity,"pipelineInferenceExecutor");
                executor.set(worker);
                worker.execute(() -> {
                    occupied.countDown();
                    try { release.await(); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
                });
            });
            assertTrue(occupied.await(2,TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                invoke(activity,"startCamera",new Class<?>[]{boolean.class},true);
                assertTrue(((AnalysisStartGate)get(activity,"analysisStartGate")).preparing());
                assertFalse(((ExperimentSession)get(activity,"experimentSession")).isRunning());
                assertFalse((Boolean)get(activity,"cameraStarted"));
                activity.findViewById(R.id.analysis_start_button).performClick();
                assertFalse(((AnalysisStartGate)get(activity,"analysisStartGate")).preparing());
            });
            release.countDown();
            executor.get().submit(() -> {}).get(3,TimeUnit.SECONDS);
            scenario.onActivity(activity -> {
                assertFalse((Boolean)get(activity,"cameraStarted"));
                assertFalse(((ExperimentSession)get(activity,"experimentSession")).isRunning());
            });
        } finally {
            release.countDown();
            prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,exp).commit();
        }
    }
    private static Object get(Object target,String name) {
        try { Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private static void set(Object target,String name,Object value) {
        try { Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args) {
        try { Method method=target.getClass().getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(target,args); }
        catch(Exception error) { throw new AssertionError(error); }
    }
}
