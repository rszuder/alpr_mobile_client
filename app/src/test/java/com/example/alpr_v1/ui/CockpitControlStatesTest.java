package com.example.alpr_v1.ui;

import android.view.View;
import com.example.alpr_v1.*;
import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual MainActivity render functions and all twelve control slots; no model or camera IO. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CockpitControlStatesTest {
    MainUiSessionLabelsTest fixture;MainActivity main;
    @Before public void setup()throws Exception{fixture=new MainUiSessionLabelsTest();fixture.setup();main=fixture.f.main;set("experimentModeEnabled",false);}
    @After public void close()throws Exception{if(fixture!=null)fixture.close();}
    Object call(String method)throws Exception{return TvBboxStartUiTest.call(main,method,new Class<?>[0]);}
    void set(String name,Object value)throws Exception{TvBboxStartUiTest.set(main,name,value);}
    MaterialButton button(int id){return main.findViewById(id);}
    int tint(int id){MaterialButton b=button(id);return b.getIconTint().getColorForState(b.getDrawableState(),0);}
    void cyan(int id){assertEquals(main.getColor(R.color.cockpit_cyan),tint(id));}
    void neutral(int id){assertEquals(main.getColor(R.color.alpr_text_primary),tint(id));}
    void disabled(int id){assertFalse(button(id).isEnabled());assertEquals(main.getColor(R.color.alpr_text_muted),tint(id));}
    @Test public void bothSelectedSceneModesUseTheSameCyanAndResearchLockStaysGray()throws Exception{
        for(SceneHandlingMode mode:SceneHandlingMode.values()){
            fixture.f.prefs.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,mode.wireName()).commit();
            ((AlprPipeline)TvBboxStartUiTest.get(main,"pipeline")).setSceneHandlingMode(mode);call("renderSceneModeHudToggle");cyan(R.id.live_scene_mode_toggle);
            assertTrue(button(R.id.live_scene_mode_toggle).getText().toString().contains(mode==SceneHandlingMode.DYNAMIC_CONTINUITY?"DYN":"STAT"));
        }
        set("experimentModeEnabled",true);call("renderSceneModeHudToggle");disabled(R.id.live_scene_mode_toggle);
        assertTrue(button(R.id.live_scene_mode_toggle).getStateDescription().toString().contains("zablokowane"));
    }
    @Test public void acquisitionReflectsOffArmedOnAndLockedAutoWithoutStartingRecording()throws Exception{
        call("renderCollectionControl");neutral(R.id.collection_toggle);assertEquals(main.getString(R.string.camera_action_crops_off),button(R.id.collection_toggle).getText().toString());
        fixture.f.prefs.edit().putBoolean("collection_armed",true).commit();call("renderCollectionControl");cyan(R.id.collection_toggle);
        assertFalse((boolean)TvBboxStartUiTest.get(main,"collectionActive"));assertFalse(fixture.f.sessions.experiment.isRunning());
        set("experimentModeEnabled",true);call("renderCollectionControl");disabled(R.id.collection_toggle);assertTrue(button(R.id.collection_toggle).getText().toString().contains("AUTO"));
        assertTrue(fixture.f.prefs.getBoolean("collection_armed",false));
    }
    @Test public void previewAndStartAreDifferentStatesAndStopUsesRealRunningFlag()throws Exception{
        set("cameraPreviewActive",false);call("renderAnalysisControls");neutral(R.id.camera_preview_button);neutral(R.id.analysis_start_button);
        set("cameraPreviewActive",true);call("renderAnalysisControls");cyan(R.id.camera_preview_button);neutral(R.id.analysis_start_button);
        set("cameraStarted",true);call("renderAnalysisControls");disabled(R.id.camera_preview_button);
        assertEquals(main.getColor(R.color.alpr_error),tint(R.id.analysis_start_button));assertEquals("Stop",button(R.id.analysis_start_button).getText().toString());set("cameraStarted",false);
    }
    @Test public void autoZoomNeedsActualEnabledControllerAndLockedControlIsGray()throws Exception{
        AutoZoomController az=(AutoZoomController)TvBboxStartUiTest.get(main,"autoZoomController");fixture.f.prefs.edit().putBoolean("auto_zoom_enabled",true).commit();
        az.setEnabled(false);call("updateAutoZoomButton");neutral(R.id.auto_zoom_button);
        az.setEnabled(true);call("updateAutoZoomButton");cyan(R.id.auto_zoom_button);assertEquals(View.GONE,main.findViewById(R.id.auto_zoom_glow).getVisibility());
        ExperimentSession e=fixture.f.sessions.experiment;ExperimentSession.Prepared p=e.prepare("pipeline_comparison","SYNTHETIC",TimerConfig.disabled(),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        assertTrue(e.startPrepared(p,1000,1000));call("updateAutoZoomButton");disabled(R.id.auto_zoom_button);assertTrue(az.enabled());
    }
    @Test public void configuredTimerIsNeutralAndRealDeadlineUsesCyanStatus()throws Exception{
        set("experimentModeEnabled",true);set("experimentTimerConfig",TimerConfig.of(true,300));call("renderAnalysisControls");neutral(R.id.experiment_timer_button);
        assertEquals("ustawiono",((android.widget.TextView)main.findViewById(R.id.experiment_timer_caption)).getText().toString());
        ExperimentSession e=fixture.f.sessions.experiment;ExperimentSession.Prepared p=e.prepare("pipeline_comparison","SYNTHETIC",TimerConfig.of(true,300),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        assertTrue(e.startPrepared(p,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos()));set("cameraStarted",true);call("renderAnalysisControls");
        disabled(R.id.experiment_timer_button);assertEquals(main.getColor(R.color.cockpit_cyan),((android.widget.TextView)main.findViewById(R.id.experiment_timer_value)).getCurrentTextColor());set("cameraStarted",false);
    }
    @Test public void galleryCountDoesNotActivateTheReviewAction()throws Exception{
        call("renderCapturedCrops");neutral(R.id.gallery_open_button);((android.widget.TextView)main.findViewById(R.id.gallery_count_badge)).setText("19");
        neutral(R.id.gallery_open_button);assertFalse(button(R.id.gallery_open_button).isActivated());
    }
    @Test public void allTwelveSlotsHaveUniformOnOffAndDisabledAppearanceWithoutChangingActions(){
        int[] ids={R.id.live_scene_mode_toggle,R.id.search_plate_button,R.id.release_target_button,R.id.auto_zoom_button,R.id.live_diagnostics_toggle,R.id.live_model_info_toggle,
                R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.experiment_timer_button,R.id.export_report_button};
        for(int id:ids){MaterialButton b=button(id);final int[] clicks={0};b.setOnClickListener(v->clicks[0]++);
            b.setEnabled(true);CockpitControlStyle.apply(b,CockpitControlStyle.State.ON);cyan(id);assertEquals(main.getColor(R.color.cockpit_cyan),b.getStrokeColor().getColorForState(b.getDrawableState(),0));
            assertTrue(b.isActivated());assertTrue(b.performClick());assertEquals(1,clicks[0]);
            CockpitControlStyle.apply(b,CockpitControlStyle.State.OFF);neutral(id);assertFalse(b.isActivated());
            b.setEnabled(false);CockpitControlStyle.apply(b,CockpitControlStyle.State.ON);disabled(id);assertEquals(1,clicks[0]);
        }
    }
}
