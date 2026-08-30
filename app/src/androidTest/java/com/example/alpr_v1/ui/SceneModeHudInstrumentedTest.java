package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageButton;

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
                ImageButton toggle = activity.findViewById(
                        R.id.live_scene_mode_toggle
                );
                assertTrue(toggle.isEnabled());
                assertEquals(
                        activity.getString(R.string.scene_mode_dynamic_action),
                        toggle.getContentDescription().toString()
                );

                assertTrue(toggle.performClick());
                assertEquals(
                        SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName(),
                        preferences.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE, "")
                );
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
