package com.example.alpr_v1.ui;

import android.app.Application;
import android.view.View;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.*;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.time.Duration;
import static org.junit.Assert.*;

/** Actual MainActivity HUD callbacks using the accepted synthetic camera/session fixture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NonModalHudControlsTest {
    TvBboxStartUiTest f;LivePresentationController presentation;
    android.view.WindowManager window;
    @Before public void setup()throws Exception{
        f=new TvBboxStartUiTest();f.temp.create();f.setup();
        ((LivePresentationController)TvBboxStartUiTest.get(f.main,"livePresentation")).dispose();
        call("bindViews");presentation=(LivePresentationController)TvBboxStartUiTest.get(f.main,"livePresentation");
        window=f.main.getWindowManager();
        window.addView(f.main.getWindow().getDecorView(),new android.view.WindowManager.LayoutParams());
        layout();
    }
    @After public void close()throws Exception{if(presentation!=null)presentation.dispose();if(window!=null)window.removeViewImmediate(f.main.getWindow().getDecorView());if(f!=null){f.close();f.temp.delete();}}
    Object call(String method)throws Exception{return TvBboxStartUiTest.call(f.main,method,new Class<?>[0]);}
    View view(int id){return f.main.findViewById(id);}
    String text(int id){return ((TextView)view(id)).getText().toString();}
    int tint(int id){return ((MaterialButton)view(id)).getIconTint().getDefaultColor();}
    void layout(){View root=f.main.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800);root.getViewTreeObserver().dispatchOnPreDraw();}
    void bothOn(){view(R.id.live_diagnostics_toggle).performClick();view(R.id.live_model_info_toggle).performClick();layout();}
    @Test public void actualBottomButtonsKeepIndependentTintsAndUseExistingMetricViews()throws Exception{
        View first=view(R.id.live_hud_row),second=view(R.id.live_model_info);bothOn();
        assertSame(first,view(R.id.live_hud_row));assertSame(second,view(R.id.live_model_info));
        assertEquals(View.VISIBLE,first.getVisibility());assertEquals(View.VISIBLE,second.getVisibility());
        assertEquals(f.main.getColor(R.color.cockpit_cyan),tint(R.id.live_diagnostics_toggle));
        assertEquals(f.main.getColor(R.color.cockpit_cyan),tint(R.id.live_model_info_toggle));
        view(R.id.live_diagnostics_toggle).performClick();assertEquals(View.GONE,first.getVisibility());assertEquals(View.VISIBLE,second.getVisibility());
        assertEquals(f.main.getColor(R.color.alpr_text_primary),tint(R.id.live_diagnostics_toggle));
        assertEquals(f.main.getColor(R.color.cockpit_cyan),tint(R.id.live_model_info_toggle));
        view(R.id.live_model_info_toggle).performClick();assertEquals(View.INVISIBLE,second.getVisibility());
        assertEquals(f.main.getColor(R.color.alpr_text_primary),tint(R.id.live_model_info_toggle));assertNull(ShadowDialog.getLatestDialog());
    }
    @Test public void runningTimerAndFrozenTvStateContinueAcrossHudToggles()throws Exception{
        TimerConfig timer=TimerConfig.of(true,300);TvBboxStartUiTest.set(f.main,"experimentTimerConfig",timer);
        ExperimentSession session=f.sessions.experiment;
        ExperimentSession.Prepared prepared=session.prepare("pipeline_comparison","SYNTHETIC",timer,ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        assertTrue(session.startPrepared(prepared,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos()));
        TvBboxStartUiTest.set(f.main,"cameraStarted",true);call("updateExperimentTimerButton");
        long deadline=session.timerDeadlineElapsedNanos();String digest=f.tv.snapshot(1794080000000L).toString();
        Object pipeline=TvBboxStartUiTest.get(f.main,"pipeline"),geometry=TvBboxStartUiTest.get(f.tv,"geometry");
        assertEquals("5:00",text(R.id.experiment_timer_value));presentation.showState(LivePresentationController.State.STATIC_IDLE,"");bothOn();
        ShadowSystemClock.advanceBy(Duration.ofMillis(2100));call("updateExperimentTimerButton");
        assertEquals("4:58",text(R.id.experiment_timer_value));assertEquals(deadline,session.timerDeadlineElapsedNanos());assertTrue(session.isRunning());
        view(R.id.live_model_info_toggle).performClick();view(R.id.live_diagnostics_toggle).performClick();
        assertEquals(deadline,session.timerDeadlineElapsedNanos());assertEquals(true,TvBboxStartUiTest.get(f.main,"cameraStarted"));
        assertSame(pipeline,TvBboxStartUiTest.get(f.main,"pipeline"));assertSame(geometry,TvBboxStartUiTest.get(f.tv,"geometry"));
        assertEquals(digest,f.tv.snapshot(1794080000000L).toString());assertNull(ShadowDialog.getLatestDialog());
        TvBboxStartUiTest.set(f.main,"cameraStarted",false);
    }
}
