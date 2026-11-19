package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.example.alpr_v1.*;
import com.example.alpr_v1.acquisition.DynamicMtSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class DynamicMtSettingsUiTest {
    @Test public void impossibleThresholdCannotBeSavedAgainstTheActualAnalysisFrame() {
        Application app=RuntimeEnvironment.getApplication();
        SharedPreferences prefs=app.getSharedPreferences(SettingsActivity.PREFERENCES,0);
        prefs.edit().putInt(DynamicMtSettings.ENTER_WIDTH,256).putInt(DynamicMtSettings.ENTER_HEIGHT,112)
                .putInt(DynamicMtSettings.KEEP_WIDTH,100).putInt(DynamicMtSettings.KEEP_HEIGHT,64).commit();
        Intent intent=new Intent(app,SettingsActivity.class).putExtra(SettingsActivity.EXTRA_MT_FRAME_WIDTH,1080)
                .putExtra(SettingsActivity.EXTRA_MT_FRAME_HEIGHT,1440);
        org.robolectric.android.controller.ActivityController<SettingsActivity> activity=Robolectric.buildActivity(SettingsActivity.class,intent).setup();
        try {
            activity.get().findViewById(R.id.settings_dynamic_mt).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);
            assertTrue(((TextView)dialog.findViewById(R.id.dynamic_mt_frame_info)).getText().toString().contains("1080 × 1440"));
            EditText width=dialog.findViewById(R.id.dynamic_mt_enter_width);width.setText("2000");
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing());assertNotNull(width.getError());assertEquals(256,prefs.getInt(DynamicMtSettings.ENTER_WIDTH,0));
            width.setText("256");dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            assertFalse(dialog.isShowing());assertEquals(256,prefs.getInt(DynamicMtSettings.ENTER_WIDTH,0));
            assertEquals(112,prefs.getInt(DynamicMtSettings.ENTER_HEIGHT,0));
        }finally{activity.pause().stop().destroy();}
    }
}
