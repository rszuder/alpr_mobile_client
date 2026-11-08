package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.example.alpr_v1.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.setup.*;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.lang.reflect.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Reuses the accepted F22 synthetic Activity fixture without modifying its source. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainUiSessionLabelsTest {
    TvBboxStartUiTest f;
    static void set(Object o,String n,Object v)throws Exception{TvBboxStartUiTest.set(o,n,v);}
    static Object get(Object o,String n)throws Exception{return TvBboxStartUiTest.get(o,n);}
    Object call(String n)throws Exception{return TvBboxStartUiTest.call(f.main,n,new Class<?>[0]);}
    @Before public void setup()throws Exception{
        f=new TvBboxStartUiTest();f.temp.create();f.setup();
        set(f.main,"topAppBar",f.main.findViewById(R.id.top_app_bar));
        String[] fields={"collectionToggle","cameraPreviewButton","galleryOpenButton","searchPlateButton","releaseTargetButton","liveSceneModeToggle","autoZoomButton","experimentTimerButton","experimentThermalButton"};
        int[] ids={R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.search_plate_button,R.id.release_target_button,R.id.live_scene_mode_toggle,R.id.auto_zoom_button,R.id.experiment_timer_button,R.id.experiment_thermal_button};
        for(int i=0;i<ids.length;i++)set(f.main,fields[i],f.main.findViewById(ids[i]));
        f.tv.setInputSummaryProvider(()->{try{return call("tvInputSummary").toString();}catch(Exception e){throw new AssertionError(e);}});
    }
    @After public void close()throws Exception{if(f!=null){f.close();f.temp.delete();}}
    MaterialButton button(int id){return f.main.findViewById(id);}
    @Test public void readyCalibrationAndFullFrameAreSeparateBeforeStart()throws Exception{
        f.prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME").commit();f.tv.refreshInputSummary();
        String label=((TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString();
        assertTrue(label,label.contains("skalibrowany"));assertTrue(label.contains("Planowane wejście"));assertTrue(label.contains("pełna klatka"));assertFalse(label.contains("rektyfikacja"));
        assertEquals("READY",f.tv.bboxPreparationStatus());assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void activeTvUsesFrozenModeEvenIfPreferenceSaysFullFrame()throws Exception{
        f.begin(RoiBudgetPolicy.FULL_FRAME);String digest=f.config.tvBboxCrop().digest;
        f.prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME").commit();f.tv.refreshInputSummary();
        String label=((TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString();
        assertTrue(label.contains("Wejście modeli"));assertTrue(label.contains("wycinek TV (BBOX)"));assertFalse(label.contains("Planowane"));
        assertEquals(digest,((AlprPipeline)get(f.main,"pipeline")).researchExecutionConfig().tvBboxCrop().digest);assertTrue(f.store.accepting());
    }
    @Test public void fullFrameActiveInputDoesNotFollowPlannedTvSelection()throws Exception{
        // Normal analysis has no frozen research input, regardless of its next planned preference.
        set(f.main,"experimentModeEnabled",false);set(f.main,"cameraStarted",true);
        String label=call("tvInputSummary").toString();assertTrue(label.contains("Wejście modeli"));assertTrue(label.contains("pełna klatka"));assertFalse(label.contains("wycinek"));
        set(f.main,"cameraStarted",false);
    }
    @Test public void draftStaleAndMissingNeverClaimReady()throws Exception{
        set(f.tv,"calibration",TvSetupCalibration.empty());f.tv.refreshInputSummary();
        assertTrue(((TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString().contains("brak kalibracji"));
        f.confirm("2026-11-06T20:00:00Z");TvEditDraft draft=TvEditDraft.fromCalibration((TvSetupCalibration)get(f.tv,"calibration"));
        set(f.tv,"calibration",TvSetupCalibration.empty());set(f.tv,"draft",draft);f.tv.refreshInputSummary();
        assertTrue(((TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString().contains("DRAFT"));
        assertNotEquals("READY",f.tv.bboxPreparationStatus());set(f.tv,"draft",null);
        f.confirm("2026-11-06T20:00:00Z");set(f.tv,"calibration",((TvSetupCalibration)get(f.tv,"calibration")).invalidated("SYNTHETIC_CHANGED_CAMERA"));f.tv.refreshInputSummary();
        String label=((TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString();assertTrue(label.contains("NIEAKTUALNY"));assertFalse(label.contains("skalibrowany"));
        assertNotEquals("READY",f.blocker());
    }
    @Test public void acquisitionIsStillArmedBeforeStartAndAutomaticInResearch()throws Exception{
        set(f.main,"experimentModeEnabled",false);f.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();
        Field key=MainActivity.class.getDeclaredField("KEY_COLLECTION_ARMED");key.setAccessible(true);String preference=(String)key.get(null);
        f.prefs.edit().putBoolean(preference,true).commit();call("renderCollectionControl");
        assertFalse((boolean)get(f.main,"cameraStarted"));assertTrue(button(R.id.collection_toggle).isEnabled());assertEquals("Akwizycja\nWŁ.",button(R.id.collection_toggle).getText().toString());
        set(f.main,"experimentModeEnabled",true);call("renderCollectionControl");assertFalse(button(R.id.collection_toggle).isEnabled());assertEquals("Akwizycja\nAUTO",button(R.id.collection_toggle).getText().toString());
        assertTrue(f.prefs.getBoolean(preference,false));
    }
    @Test public void searchReleaseSceneModeAzAndTimerRetainTheirActualGates()throws Exception{
        set(f.main,"experimentModeEnabled",false);call("renderSearchControls");call("renderSceneModeHudToggle");call("updateAutoZoomButton");
        assertFalse(button(R.id.search_plate_button).isEnabled());assertFalse(button(R.id.release_target_button).isEnabled());assertTrue(button(R.id.live_scene_mode_toggle).isEnabled());assertTrue(button(R.id.auto_zoom_button).isEnabled());
        set(f.main,"cameraStarted",true);call("renderSearchControls");assertTrue(button(R.id.search_plate_button).isEnabled());assertFalse(button(R.id.release_target_button).isEnabled());
        set(f.main,"experimentModeEnabled",true);call("renderSceneModeHudToggle");call("updateAutoZoomButton");call("renderAnalysisControls");
        assertFalse(button(R.id.live_scene_mode_toggle).isEnabled());assertTrue(button(R.id.auto_zoom_button).isEnabled());assertFalse(button(R.id.experiment_timer_button).isEnabled());
        set(f.main,"cameraStarted",false);call("renderAnalysisControls");assertTrue(button(R.id.experiment_timer_button).isEnabled());
    }
    @Test public void stopLabelDescribesAnalysisAndResearchSessionWithoutChangingAction()throws Exception{
        set(f.main,"experimentModeEnabled",false);set(f.main,"cameraStarted",true);call("renderAnalysisControls");
        assertEquals("Stop",button(R.id.analysis_start_button).getText().toString());assertTrue(button(R.id.analysis_start_button).getContentDescription().toString().contains("podgląd"));
        set(f.main,"cameraStarted",false);set(f.main,"experimentModeEnabled",true);f.begin(RoiBudgetPolicy.FULL_FRAME);set(f.main,"cameraStarted",true);call("renderAnalysisControls");
        assertEquals("Stop",button(R.id.analysis_start_button).getText().toString());assertTrue(button(R.id.analysis_start_button).getContentDescription().toString().contains("zakończ bieżącą sesję"));
        assertTrue(f.sessions.experiment.isRunning());assertTrue(f.store.accepting());set(f.main,"cameraStarted",false);
        call("updateAutoZoomButton");assertFalse(button(R.id.auto_zoom_button).isEnabled());
    }
    @Test public void activeResearchFullFrameUsesFrozenConfigurationOverTvPreference()throws Exception{
        ResearchExecutionConfig config=TvBboxAdmissionTest.base(RoiBudgetPolicy.FULL_FRAME,false).withCameraSetup(TvBboxCropTest.setup(1));
        ExperimentSession.Prepared prepared=f.sessions.experiment.prepare("roi_budget",config.variant,(TimerConfig)get(f.main,"experimentTimerConfig"),(ThermalConfig)get(f.main,"experimentThermalConfig"),new ExperimentIdentity("MAIN-UI","SYNTHETIC",1,"synthetic=true"),config);
        f.store=TvBboxProducerFixture.prepareFor(f.temp.newFolder(),prepared);set(f.sessions,"store",f.store);((AlprPipeline)get(f.main,"pipeline")).setResearchExecutionConfig(config);
        Class<?> type=Class.forName(MainActivity.class.getName()+"$PreparedAnalysisStart");Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object ready=ctor.newInstance(config,prepared,f.store,0);
        set(f.main,"preparedExperimentStart",ready);set(ready,"startWall",System.currentTimeMillis());set(ready,"startElapsed",System.nanoTime());set(ready,"startMonotonic",System.nanoTime());
        assertEquals(true,TvBboxStartUiTest.call(f.main,"beginAnalysisMeasurement",new Class<?>[]{type},ready));TvBboxProducerFixture.drain(f.store);
        assertTrue(f.sessions.experiment.isRunning());assertEquals("TV_BBOX_CROP",f.prefs.getString(SettingsActivity.KEY_TV_INPUT_MODE,""));
        String label=call("tvInputSummary").toString();assertTrue(label.contains("Wejście modeli"));assertTrue(label.contains("pełna klatka"));assertFalse(label.contains("wycinek"));
        assertEquals(ResearchInputMode.CAMERA_FULL_FRAME,((AlprPipeline)get(f.main,"pipeline")).researchExecutionConfig().inputMode());
    }
    @Test public void disabledTextIsReadableAndRemainsDisabled()throws Exception{
        for(int id:new int[]{R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.search_plate_button,R.id.release_target_button,R.id.auto_zoom_button,R.id.experiment_timer_button}){
            MaterialButton button=button(id);button.setEnabled(false);
            TvBboxStartUiTest.call(f.main,"tintCameraAction",new Class<?>[]{MaterialButton.class,int.class},button,R.color.alpr_accent);
            assertFalse(button.isEnabled());assertEquals(1f,button.getAlpha(),0);
            int color=button.getTextColors().getColorForState(new int[]{-android.R.attr.state_enabled},Color.TRANSPARENT);
            assertEquals(255,Color.alpha(color));assertTrue(ColorUtils.calculateContrast(color,f.main.getColor(R.color.alpr_surface))>=4.5);
            assertNotEquals(button.getTextColors().getColorForState(new int[]{android.R.attr.state_enabled},0),color);
        }
    }
}
