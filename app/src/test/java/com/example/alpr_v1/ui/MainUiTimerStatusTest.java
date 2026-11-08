package com.example.alpr_v1.ui;

import android.app.Application;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.example.alpr_v1.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.lang.reflect.*;
import java.time.Duration;
import static org.junit.Assert.*;

/** Real timer/session/UI boundaries with synthetic sessions, no backend, camera or measurement. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainUiTimerStatusTest {
    MainUiSessionLabelsTest owner;TvBboxStartUiTest f;LivePresentationController presentation;
    @Before public void setup()throws Exception{
        owner=new MainUiSessionLabelsTest();owner.setup();f=owner.f;
        presentation=MainUiReadabilityTest.presenter(f.main);set(f.main,"livePresentation",presentation);
        set(f.main,"exportReportButton",f.main.findViewById(R.id.export_report_button));
    }
    @After public void close()throws Exception{if(presentation!=null)presentation.dispose();if(owner!=null)owner.close();}
    void set(Object o,String name,Object value)throws Exception{TvBboxStartUiTest.set(o,name,value);}
    Object call(String method)throws Exception{return TvBboxStartUiTest.call(f.main,method,new Class<?>[0]);}
    String text(int id){return ((TextView)f.main.findViewById(id)).getText().toString();}
    void render()throws Exception{call("updateExperimentTimerButton");}
    void configured(int seconds)throws Exception{set(f.main,"experimentTimerConfig",TimerConfig.of(true,seconds));}
    void running(int seconds)throws Exception{
        configured(seconds);
        ExperimentSession session=f.sessions.experiment;
        ExperimentSession.Prepared prepared=session.prepare("pipeline_comparison","SYNTHETIC",TimerConfig.of(true,seconds),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        assertTrue(session.startPrepared(prepared,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos()));render();
    }
    @Test public void disabledUnavailableAndConfiguredHaveDistinctClockCopy()throws Exception{
        render();assertEquals(View.GONE,f.main.findViewById(R.id.experiment_timer_status).getVisibility());assertEquals("Timer",text(R.id.experiment_timer_button));
        configured(300);render();assertEquals("5:00",text(R.id.experiment_timer_value));assertEquals("ustawiono",text(R.id.experiment_timer_caption));
        assertFalse(f.sessions.experiment.isRunning());assertEquals(-1,f.sessions.experiment.timerDeadlineElapsedNanos());
        set(f.main,"experimentModeEnabled",false);render();assertEquals(View.GONE,f.main.findViewById(R.id.experiment_timer_status).getVisibility());
    }
    @Test public void runningAndStaticIdleUseTheSameRetainedDeadline()throws Exception{
        running(300);long deadline=f.sessions.experiment.timerDeadlineElapsedNanos();assertEquals("5:00",text(R.id.experiment_timer_value));
        presentation.showState(LivePresentationController.State.STATIC_IDLE,"");ShadowSystemClock.advanceBy(Duration.ofMillis(1750));render();
        assertEquals("4:59",text(R.id.experiment_timer_value));assertEquals("pozostało",text(R.id.experiment_timer_caption));
        assertEquals("Scena gotowa",text(R.id.live_status));assertEquals("Czekam na zmianę",text(R.id.live_status_secondary));
        assertEquals(deadline,f.sessions.experiment.timerDeadlineElapsedNanos());assertTrue(f.sessions.experiment.isRunning());
    }
    @Test public void timeoutStaysZeroDuringFinalizationAndPreparationOfNextSession()throws Exception{
        running(5);long deadline=f.sessions.experiment.timerDeadlineElapsedNanos();
        f.sessions.experiment.finishAt(ExperimentSession.CompletionReason.TIMER,f.sessions.experiment.timerDeadlineWallMillis(),deadline);
        presentation.stop();render();assertEquals("0:00",text(R.id.experiment_timer_value));assertEquals("czas minął",text(R.id.experiment_timer_caption));
        assertEquals("Sesja zakończona",text(R.id.live_status));assertFalse(f.sessions.experiment.isRunning());
        String old=f.sessions.experiment.sessionId();f.prepare(RoiBudgetPolicy.FULL_FRAME);assertNotEquals(old,f.store.sessionId());render();
        assertEquals("0:00",text(R.id.experiment_timer_value));assertEquals("czas minął",text(R.id.experiment_timer_caption));
        assertFalse(f.store.accepting());assertEquals(deadline,f.sessions.experiment.timerDeadlineElapsedNanos());
    }
    @Test public void manualStopErrorAndCancelledPreparationDoNotLookLikeRunningTimers()throws Exception{
        running(300);f.sessions.experiment.finish(ExperimentSession.CompletionReason.MANUAL);presentation.stop();render();
        assertEquals(View.GONE,f.main.findViewById(R.id.experiment_timer_status).getVisibility());assertEquals("Analiza zatrzymana",text(R.id.live_status));
        f.sessions.experiment.reset();running(300);f.sessions.experiment.finish(ExperimentSession.CompletionReason.ERROR);
        presentation.showUserStatus(LivePresentationController.State.ERROR,"Obserwacja TV zatrzymana","Sprawdź kalibrację TV");render();
        assertEquals(View.GONE,f.main.findViewById(R.id.experiment_timer_status).getVisibility());assertEquals("Obserwacja TV zatrzymana",text(R.id.live_status));
        f.sessions.experiment.reset();render();assertEquals("ustawiono",text(R.id.experiment_timer_caption));assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void configuredNextTimerIsPresentationOnlyAndPreservesOldDeadline()throws Exception{
        running(5);long deadline=f.sessions.experiment.timerDeadlineElapsedNanos();
        f.sessions.experiment.finishAt(ExperimentSession.CompletionReason.TIMER,f.sessions.experiment.timerDeadlineWallMillis(),deadline);render();
        configured(180);set(f.main,"timerConfiguredAfterFinishedSession",f.sessions.experiment.sessionId());render();
        assertEquals("3:00",text(R.id.experiment_timer_value));assertEquals("ustawiono",text(R.id.experiment_timer_caption));assertEquals(deadline,f.sessions.experiment.timerDeadlineElapsedNanos());assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void uiDetachAndReattachmentNeverRearmOrReplayTheClock()throws Exception{
        running(300);long deadline=f.sessions.experiment.timerDeadlineElapsedNanos();call("cancelExperimentTimer");ShadowSystemClock.advanceBy(Duration.ofMillis(5700));render();
        assertEquals("4:55",text(R.id.experiment_timer_value));assertEquals(deadline,f.sessions.experiment.timerDeadlineElapsedNanos());
        presentation.dispose();presentation=MainUiReadabilityTest.presenter(f.main);set(f.main,"livePresentation",presentation);render();
        assertEquals("4:55",text(R.id.experiment_timer_value));assertEquals(deadline,f.sessions.experiment.timerDeadlineElapsedNanos());
    }
    @Test public void hourBoundaryAndTickDoNotResizePreviewOrClockColumn()throws Exception{
        running(3600);layout();android.graphics.Rect camera=rect(R.id.camera_preview);int width=f.main.findViewById(R.id.experiment_timer_status).getWidth();
        assertEquals("1:00:00",text(R.id.experiment_timer_value));ShadowSystemClock.advanceBy(Duration.ofMillis(1001));render();layout();
        assertEquals("59:59",text(R.id.experiment_timer_value));assertEquals(width,f.main.findViewById(R.id.experiment_timer_status).getWidth());assertEquals(camera,rect(R.id.camera_preview));
        f.sessions.experiment.finish(ExperimentSession.CompletionReason.MANUAL);render();layout();assertEquals(camera,rect(R.id.camera_preview));
    }
    @Test public void exactClockFormatsCoverZeroHoursAndMaximumAcceptedDuration()throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("formatTimerDuration",long.class);m.setAccessible(true);
        long[] seconds={-1,0,5,59,60,3599,3600,3661,Integer.MAX_VALUE};String[] expected={"0:00","0:00","0:05","0:59","1:00","59:59","1:00:00","1:01:01","596523:14:07"};
        for(int i=0;i<seconds.length;i++)assertEquals(expected[i],m.invoke(null,seconds[i]));
    }
    @Test @Config(qualifiers="w720dp-h360dp-mdpi") public void landscapeClockFitsAndKeepsItsDeadlineAcrossTheHourBoundary()throws Exception{
        running(3600);layout(720,360);long deadline=f.sessions.experiment.timerDeadlineElapsedNanos();
        android.graphics.Rect preview=rect(R.id.camera_preview);View clock=f.main.findViewById(R.id.experiment_timer_status);
        assertEquals(android.widget.LinearLayout.HORIZONTAL,((android.widget.LinearLayout)clock).getOrientation());int width=clock.getWidth();
        TextView caption=f.main.findViewById(R.id.experiment_timer_caption),value=f.main.findViewById(R.id.experiment_timer_value);
        assertTrue(caption.getLayout().getHeight()<=caption.getHeight());assertTrue(value.getLayout().getHeight()<=value.getHeight());
        ShadowSystemClock.advanceBy(Duration.ofMillis(1001));render();layout(720,360);
        assertEquals("59:59",text(R.id.experiment_timer_value));assertEquals(width,clock.getWidth());assertEquals(preview,rect(R.id.camera_preview));assertEquals(deadline,f.sessions.experiment.timerDeadlineElapsedNanos());
    }
    @Test public void realTimerAndThermalTilesKeepTheirOriginalDialogCallbacks()throws Exception{
        set(f.main,"experimentPreviewPaused",true);call("configureAnalysisControls");
        assertTrue(f.main.findViewById(R.id.experiment_timer_button).performClick());android.app.Dialog timer=ShadowDialog.getLatestDialog();assertTrue(timer.isShowing());timer.dismiss();
        assertTrue(f.main.findViewById(R.id.experiment_thermal_button).performClick());android.app.Dialog thermal=ShadowDialog.getLatestDialog();assertNotSame(timer,thermal);assertTrue(thermal.isShowing());thermal.dismiss();
        assertEquals("Timer",text(R.id.experiment_timer_button));
    }
    void layout(){layout(360,800);}
    void layout(int width,int height){View v=f.main.findViewById(R.id.main);v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);}
    android.graphics.Rect rect(int id){View v=f.main.findViewById(id);return new android.graphics.Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom());}
}
