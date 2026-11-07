package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.SharedPreferences;
import android.view.View;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.*;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.setup.*;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TvBboxPreparationUiTest {
    MainActivity main;
    SharedPreferences prefs;
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
    @Before public void setup()throws Exception{
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences(SettingsActivity.PREFERENCES,0);prefs.edit().clear().commit();
        main=Robolectric.buildActivity(MainActivity.class).get();main.setTheme(R.style.Theme_ALPR_v1);set(main,"uiPreferences",prefs);
    }
    @After public void close()throws Exception{((ExecutorService)get(main,"pipelineInferenceExecutor")).shutdownNow();prefs.edit().clear().commit();}
    @Test public void selectedPreferenceIsIgnoredOutsideF5aPreparation()throws Exception{
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").commit();
        for(boolean experiment:new boolean[]{false,true})for(String type:new String[]{"roi_budget","runtime","continuity"}){
            prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,experiment).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,type).commit();
            assertEquals(experiment&&type.equals("roi_budget"),call(main,"tvBboxRequested"));
        }
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME").commit();assertEquals(false,call(main,"tvBboxRequested"));
    }
    @Test public void fixedZoomGuardReadsResearchAzAndKeepsNormalAzIndependent()throws Exception{
        prefs.edit().putBoolean("auto_zoom_enabled",true).putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,false).commit();
        assertFalse(call(main,"tvBboxStartReason").toString().contains("FIXED_ZOOM"));
        prefs.edit().putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true).commit();assertTrue(call(main,"tvBboxStartReason").toString().contains("FIXED_ZOOM"));
    }
    @Test public void settingsShowsExplicitUnofficialSelectionOnlyForF5aAndKeepsCalibration()throws Exception{
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget")
                .putString("tv_setup_calibration_v1","preserve").commit();
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup();
        try{
            SettingsActivity activity=controller.get();assertEquals(View.VISIBLE,activity.findViewById(R.id.settings_tv_input_container).getVisibility());
            MaterialAutoCompleteTextView input=activity.findViewById(R.id.settings_tv_input_mode);assertEquals(2,input.getAdapter().getCount());
            input.getOnItemClickListener().onItemClick(null,null,1,1);assertEquals("TV_BBOX_CROP",prefs.getString(SettingsActivity.KEY_TV_INPUT_MODE,""));
            assertEquals("preserve",prefs.getString("tv_setup_calibration_v1",""));
            prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();call(activity,"refreshTvTools");assertEquals(View.GONE,activity.findViewById(R.id.settings_tv_input_container).getVisibility());
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void controllerReportsReadyThenStaleWithoutChangingModelSpace()throws Exception{
        main.setContentView(R.layout.activity_main);TvSetupController controller=new TvSetupController(main,main.findViewById(R.id.camera_preview),main.findViewById(R.id.detection_overlay),()->true,()->{},(n,d)->{});
        CameraPreviewGeometry geometry=TvBboxCropTest.geometry(640,400);set(controller,"geometry",geometry);
        set(controller,"calibration",TvSetupCalibration.confirm(Arrays.asList(new com.example.alpr_v1.vision.Point2(.125f,.15f),new com.example.alpr_v1.vision.Point2(.875f,.20f),new com.example.alpr_v1.vision.Point2(.85f,.85f),new com.example.alpr_v1.vision.Point2(.15f,.80f)),geometry,1,"2026-11-06T20:00:00Z"));
        CameraSetupSnapshot applied=TvBboxCropTest.setup(1);set(controller,"cameraState",new CameraSetupControl.State(Collections.emptyList(),applied.selection,1,1,4,applied,false,"",1));
        assertEquals("READY",controller.bboxPreparationStatus());TvBboxCrop selected=controller.selectionForInput();assertEquals(640,selected.sourceWidth);
        assertEquals(selected.calibrationDigest,controller.freezeInputSelection(selected).getString("digest_sha256"));
        set(controller,"geometry",TvBboxCropTest.geometry(640,400,0,640,400,new double[]{0,0,640,400},800,600,2));
        assertTrue(controller.bboxPreparationStatus().contains("STALE"));assertThrows(Exception.class,controller::selectionForInput);
    }
}
