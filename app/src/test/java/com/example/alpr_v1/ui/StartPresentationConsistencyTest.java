package com.example.alpr_v1.ui;

import android.view.View;
import android.widget.TextView;
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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StartPresentationConsistencyTest {
    MainUiSessionLabelsTest harness;TvBboxStartUiTest f;LivePresentationController presentation;
    @Before public void setup()throws Exception{
        harness=new MainUiSessionLabelsTest();harness.setup();f=harness.f;
        ((LivePresentationController)TvBboxStartUiTest.get(f.main,"livePresentation")).dispose();
        presentation=MainUiReadabilityTest.presenter(f.main);TvBboxStartUiTest.set(f.main,"livePresentation",presentation);
        f.prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME").commit();
        ResearchExecutionConfig config=TvBboxAdmissionTest.base(RoiBudgetPolicy.FULL_FRAME,false).withCameraSetup(TvBboxCropTest.setup(1));
        ExperimentSession.Prepared prepared=f.sessions.experiment.prepare("roi_budget",config.variant,(TimerConfig)TvBboxStartUiTest.get(f.main,"experimentTimerConfig"),(ThermalConfig)TvBboxStartUiTest.get(f.main,"experimentThermalConfig"),ExperimentIdentity.defaults(),config);
        f.store=TvBboxProducerFixture.prepareFor(f.temp.newFolder(),prepared);TvBboxStartUiTest.set(f.sessions,"store",f.store);
        Class<?> type=Class.forName(MainActivity.class.getName()+"$PreparedAnalysisStart");Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        TvBboxStartUiTest.set(f.main,"preparedExperimentStart",ctor.newInstance(config,prepared,f.store,0));
        ((AlprPipeline)TvBboxStartUiTest.get(f.main,"pipeline")).setResearchExecutionConfig(config);
    }
    @After public void close()throws Exception{if(presentation!=null)presentation.dispose();if(harness!=null)harness.close();}
    Object call(String name)throws Exception{return TvBboxStartUiTest.call(f.main,name,new Class<?>[0]);}
    String status(){return ((TextView)f.main.findViewById(R.id.live_status)).getText().toString();}
    MaterialButton start(){return f.main.findViewById(R.id.analysis_start_button);}
    @Test public void readyFullFrameIsVisuallyAvailableDespiteStaleTv()throws Exception{
        TvBboxStartUiTest.set(f.tv,"calibration",((TvSetupCalibration)TvBboxStartUiTest.get(f.tv,"calibration")).invalidated("HOST_STALE"));
        assertEquals("READY",call("experimentStartBlocker").toString());call("renderAnalysisControls");
        assertEquals("Gotowe do START",status());assertTrue(start().isEnabled());assertFalse(start().isActivated());
        assertEquals(f.main.getColor(R.color.alpr_text_primary),start().getIconTint().getColorForState(start().getDrawableState(),0));
        assertFalse(f.sessions.experiment.isRunning());assertFalse(f.store.accepting());
    }
    @Test public void finalTvEditorLockCannotLeaveReadyStatus()throws Exception{
        assertEquals("READY",call("experimentStartBlocker").toString());
        Object phase=TvBboxStartUiTest.get(f.tv,"phase");TvBboxStartUiTest.set(f.tv,"phase",Enum.valueOf((Class)phase.getClass(),"EDITING"));
        call("renderAnalysisControls");assertFalse(start().isEnabled());assertEquals("Zakończ konfigurację TV",status());
        assertTrue(((TextView)f.main.findViewById(R.id.live_status_secondary)).getText().toString().contains("zablokowany"));
        assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void enabledPreparationActionDoesNotMeanReady()throws Exception{
        TvBboxStartUiTest.set(f.main,"cameraPreviewActive",false);call("renderAnalysisControls");
        assertTrue(start().isEnabled());assertFalse(status().contains("Gotowe do START"));
        assertTrue(status().contains("kamery"));assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void pendingIntentShowsCancelAndCannotClaimReady()throws Exception{
        f.intent().request();call("renderAnalysisControls");assertTrue(start().isEnabled());
        assertEquals(f.main.getString(R.string.camera_action_cancel),start().getText().toString());assertEquals("Uruchamianie eksperymentu",status());
        assertFalse(f.sessions.experiment.isRunning());assertFalse(f.store.accepting());
    }
}
