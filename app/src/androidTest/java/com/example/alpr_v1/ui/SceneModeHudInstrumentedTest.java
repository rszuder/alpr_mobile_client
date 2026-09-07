package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import com.google.android.material.button.MaterialButton;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.continuity.SceneHandlingMode;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SceneModeHudInstrumentedTest {
    @Test public void releasingTargetBeforeOpticalAnimationCancelsPendingZoomAndUnblocksPipeline() {
        java.util.concurrent.atomic.AtomicBoolean animated = new java.util.concurrent.atomic.AtomicBoolean();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field zoomField = MainActivity.class.getDeclaredField("autoZoomController");
                    zoomField.setAccessible(true);
                    com.example.alpr_v1.camera.AutoZoomController zoom =
                            (com.example.alpr_v1.camera.AutoZoomController) zoomField.get(activity);
                    zoom.setEnabled(true);
                    zoom.requestRefinement(new com.example.alpr_v1.camera.AutoZoomController.Sample(
                            1L,.5f,.5f,.1f,.3,false,1,true,true,true,"WI1234A",true));
                    java.lang.reflect.Field pipelineField = MainActivity.class.getDeclaredField("pipeline");
                    pipelineField.setAccessible(true);
                    Object pipeline = pipelineField.get(activity);
                    java.lang.reflect.Field owner = pipeline.getClass().getDeclaredField("dynamicZoomEntity");
                    owner.setAccessible(true); owner.setLong(pipeline,1L);
                    Runnable pending = () -> animated.set(true);
                    java.lang.reflect.Field pendingField = MainActivity.class.getDeclaredField("pendingAutoZoomStartRunnable");
                    pendingField.setAccessible(true); pendingField.set(activity,pending);
                    java.lang.reflect.Field handlerField = MainActivity.class.getDeclaredField("autoZoomHandler");
                    handlerField.setAccessible(true);
                    ((android.os.Handler)handlerField.get(activity)).postDelayed(pending,300L);
                    java.lang.reflect.Method release = MainActivity.class.getDeclaredMethod("returnZoomBeforeUserTargetChange");
                    release.setAccessible(true); release.invoke(activity);
                    assertEquals(com.example.alpr_v1.camera.AutoZoomController.State.READY,zoom.state());
                    assertEquals(0L,owner.getLong(pipeline));
                    org.junit.Assert.assertNull(pendingField.get(activity));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            android.os.SystemClock.sleep(450L);
            org.junit.Assert.assertFalse(animated.get());
        }
    }
    @Test
    public void hudIcon_switchesStaticAndDynamicMode() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(
                "alpr_ui",
                Context.MODE_PRIVATE
        );
        String previousMode = preferences.getString(
                SettingsActivity.KEY_SCENE_HANDLING_MODE,
                SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()
        );
        boolean previousExperiment = preferences.getBoolean(
                SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,
                false
        );
        int previousRevision = preferences.getInt(SettingsActivity.KEY_REVISION, 0);

        preferences.edit()
                .putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED, false)
                .putString(
                        SettingsActivity.KEY_SCENE_HANDLING_MODE,
                        SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()
                )
                .putInt(SettingsActivity.KEY_REVISION, previousRevision + 1)
                .commit();

        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MaterialButton toggle = activity.findViewById(
                        R.id.live_scene_mode_toggle
                );
                assertTrue(toggle.isEnabled());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.search_plate_button).getVisibility());
                assertEquals(
                        activity.getString(R.string.scene_mode_dynamic_action),
                        toggle.getContentDescription().toString()
                );

                assertTrue(toggle.performClick());
                assertEquals(
                        SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName(),
                        preferences.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE, "")
                );
                assertEquals(View.INVISIBLE, activity.findViewById(R.id.search_plate_button).getVisibility());
                assertEquals(activity.getString(R.string.scene_control_static), toggle.getText().toString());
                assertEquals(
                        activity.getString(R.string.scene_mode_static_action),
                        toggle.getContentDescription().toString()
                );

                assertTrue(toggle.performClick());
                assertEquals(
                        SceneHandlingMode.DYNAMIC_CONTINUITY.wireName(),
                        preferences.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE, "")
                );
                assertEquals(
                        activity.getString(R.string.scene_mode_dynamic_action),
                        toggle.getContentDescription().toString()
                );
            });
        } finally {
            preferences.edit()
                    .putBoolean(
                            SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,
                            previousExperiment
                    )
                    .putString(SettingsActivity.KEY_SCENE_HANDLING_MODE, previousMode)
                    .putInt(SettingsActivity.KEY_REVISION, previousRevision)
                    .commit();
        }
    }
}
