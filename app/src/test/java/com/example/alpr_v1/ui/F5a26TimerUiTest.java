package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.example.alpr_v1.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.Assert.*;

/** Real timer controls and native layouts; all run metadata and preview content are synthetic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class F5a26TimerUiTest {
    MainUiTimerStatusTest h;TvBboxStartUiTest f;
    @Before public void setup()throws Exception{create();}
    void create()throws Exception{
        h=new MainUiTimerStatusTest();h.setup();f=h.f;
        set("experimentPreviewPaused",true);call("configureAnalysisControls");
    }
    @After public void close()throws Exception{if(h!=null)h.close();}
    void set(String name,Object value)throws Exception{TvBboxStartUiTest.set(f.main,name,value);}
    Object call(String name)throws Exception{return TvBboxStartUiTest.call(f.main,name,new Class<?>[0]);}
    TextView text(int id){return f.main.findViewById(id);}
    MaterialButton tile(){return f.main.findViewById(R.id.experiment_timer_button);}
    TimerConfig config()throws Exception{return (TimerConfig)TvBboxStartUiTest.get(f.main,"experimentTimerConfig");}
    String key(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return (String)field.get(null);}
    void ordinary()throws Exception{set("experimentModeEnabled",false);f.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();}
    void preset(boolean on,int seconds)throws Exception{set("experimentTimerConfig",TimerConfig.of(on,seconds));call("renderAnalysisControls");}
    AlertDialog dialog(){return (AlertDialog)ShadowDialog.getLatestDialog();}
    void duration(int index)throws Exception{
        assertTrue(f.main.findViewById(R.id.experiment_timer_status).performClick());AlertDialog d=dialog();assertTrue(d.isShowing());ShadowLooper.shadowMainLooper().idle();
        d.getListView().performItemClick(null,index,index);d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();ShadowLooper.shadowMainLooper().idle();assertFalse(d.isShowing());
    }
    void startOrdinary(int seconds)throws Exception{
        ordinary();preset(true,seconds);MetricsCollector m=(MetricsCollector)TvBboxStartUiTest.get(f.main,"metricsCollector");
        long now=SystemClock.elapsedRealtimeNanos(),wall=System.currentTimeMillis();m.startMeasurementSession(wall,now,System.nanoTime());
        f.sessions.startOrdinaryTimer(config(),wall,now,m);set("cameraStarted",true);call("renderAnalysisControls");
    }
    @Test public void offClockAndTimerTileAreVisibleInBothModes()throws Exception{
        for(boolean research:new boolean[]{false,true}){
            set("experimentModeEnabled",research);preset(false,300);
            assertEquals(View.VISIBLE,tile().getVisibility());assertEquals(View.VISIBLE,text(R.id.experiment_timer_value).getVisibility());
            visibleInLayout(f.main.findViewById(R.id.experiment_timer_status));assertEquals("5:00",text(R.id.experiment_timer_value).getText().toString());
            assertEquals("wyłączony",text(R.id.experiment_timer_caption).getText().toString());assertFalse(tile().isActivated());assertTrue(tile().isEnabled());
        }
    }
    @Test public void tileTogglesOnOffWithoutDialogAndNeverResetsPreset()throws Exception{
        ordinary();preset(false,300);assertNull(ShadowDialog.getLatestDialog());
        assertTrue(tile().performClick());assertTrue(config().enabled());assertEquals(300,config().durationSeconds());assertTrue(tile().isActivated());
        assertEquals(f.main.getColor(R.color.cockpit_cyan),tile().getIconTint().getColorForState(tile().getDrawableState(),0));
        assertTrue(tile().performClick());assertFalse(config().enabled());assertEquals(300,config().durationSeconds());assertFalse(tile().isActivated());
        assertNull(ShadowDialog.getLatestDialog());assertEquals("5:00",text(R.id.experiment_timer_value).getText().toString());
    }
    @Test public void durationDialogDoesNotEnableAnOffTimerAndStoresDuration()throws Exception{
        ordinary();duration(3);assertEquals(300,config().durationSeconds());assertFalse(config().enabled());
        assertEquals(300,f.prefs.getInt(key("KEY_LAST_EXPERIMENT_TIMER_SECONDS"),0));
        assertEquals("wyłączony",text(R.id.experiment_timer_caption).getText().toString());
        assertEquals(5,dialog().getListView().getCount()); // OFF lives exclusively on the tile.
    }
    @Test public void durationDialogPreservesOnAndCancelPreservesBothSettings()throws Exception{
        ordinary();tile().performClick();duration(2);assertTrue(config().enabled());assertEquals(180,config().durationSeconds());
        f.main.findViewById(R.id.experiment_timer_status).performClick();AlertDialog d=dialog();ShadowLooper.shadowMainLooper().idle();d.getListView().performItemClick(null,0,0);
        d.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();assertTrue(config().enabled());assertEquals(180,config().durationSeconds());
    }
    @Test public void restoreUsesLegacyDurationAndPersistsBothOnOffAcrossSessionConfigurationReads()throws Exception{
        f.prefs.edit().putInt(key("KEY_LAST_EXPERIMENT_TIMER_SECONDS"),150).remove(key("KEY_ANALYSIS_TIMER_ENABLED")).commit();
        call("restoreTimerConfiguration");assertEquals(150,config().durationSeconds());assertFalse(config().enabled());
        tile().performClick();assertTrue(f.prefs.getBoolean(key("KEY_ANALYSIS_TIMER_ENABLED"),false));
        set("experimentTimerConfig",TimerConfig.disabled());call("restoreTimerConfiguration");assertTrue(config().enabled());assertEquals(150,config().durationSeconds());
        tile().performClick();set("experimentTimerConfig",TimerConfig.of(true,5));call("restoreTimerConfiguration");assertFalse(config().enabled());assertEquals(150,config().durationSeconds());
    }
    @Test public void switchingResearchModeKeepsTheChosenTimerConfiguration()throws Exception{
        preset(true,150);TimerConfig chosen=config();
        for(boolean research:new boolean[]{false,true,false}){
            TvBboxStartUiTest.call(f.main,"applyExperimentConfiguration",new Class<?>[]{boolean.class,RoiBudgetPolicy.class},research,RoiBudgetPolicy.FULL_FRAME);
            assertSame(chosen,config());assertTrue(config().enabled());assertEquals(150,config().durationSeconds());
        }
    }
    @Test public void normalDeadlineAndReadOnlyTileKeepFrozenStateAndContinueInStaticIdle()throws Exception{
        startOrdinary(300);long deadline=f.sessions.timerState().deadlineElapsedNanos;
        assertFalse(tile().isEnabled());assertTrue(tile().isActivated());assertTrue(tile().getText().toString().contains("ON"));
        assertFalse(f.main.findViewById(R.id.experiment_timer_status).isEnabled());
        call("toggleAnalysisTimer");call("showExperimentTimerDialog");assertTrue(config().enabled());assertNull(ShadowDialog.getLatestDialog());
        h.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");ShadowSystemClock.advanceBy(Duration.ofMillis(1750));h.render();
        assertEquals("4:59",text(R.id.experiment_timer_value).getText().toString());assertEquals("pozostało",text(R.id.experiment_timer_caption).getText().toString());
        assertEquals(deadline,f.sessions.timerState().deadlineElapsedNanos);assertEquals("Scena gotowa",text(R.id.live_status).getText().toString());
    }
    @Test public void pendingStartLocksToggleAndDurationBeforeMeasurementStarts()throws Exception{
        preset(true,180);f.intent().request();call("renderAnalysisControls");assertFalse(tile().isEnabled());
        call("toggleAnalysisTimer");call("showExperimentTimerDialog");assertTrue(config().enabled());assertEquals(180,config().durationSeconds());assertNull(ShadowDialog.getLatestDialog());
        assertFalse(f.sessions.timerState().running);assertEquals(-1,f.sessions.timerState().deadlineElapsedNanos);
    }
    @Test public void stopShowsPresetAndExpiredStateDoesNotOverwriteIt()throws Exception{
        startOrdinary(5);MetricsCollector m=(MetricsCollector)TvBboxStartUiTest.get(f.main,"metricsCollector");
        f.sessions.closeMeasurement(ExperimentSession.CompletionReason.MANUAL,m);set("cameraStarted",false);h.render();
        assertEquals("0:05",text(R.id.experiment_timer_value).getText().toString());assertEquals("ustawiono",text(R.id.experiment_timer_caption).getText().toString());
        startOrdinary(5);ShadowSystemClock.advanceBy(Duration.ofSeconds(6));f.sessions.closeMeasurement(ExperimentSession.CompletionReason.TIMER,m);set("cameraStarted",false);h.render();
        assertEquals("0:00",text(R.id.experiment_timer_value).getText().toString());assertEquals(5,config().durationSeconds());
        tile().performClick();assertEquals("0:05",text(R.id.experiment_timer_value).getText().toString());assertEquals("wyłączony",text(R.id.experiment_timer_caption).getText().toString());
    }
    @Test public void presetsCustomTimesAndDisabledStateNeverMovePreviewOrControlSlots()throws Exception{
        ordinary();call("renderAnalysisControls");h.layout();Rect preview=h.rect(R.id.camera_preview),slot=h.rect(R.id.main_slot_timer);
        for(int seconds:new int[]{5,60,150,300,3600,Integer.MAX_VALUE})for(boolean on:new boolean[]{false,true}){
            preset(on,seconds);h.layout();assertEquals(preview,h.rect(R.id.camera_preview));assertEquals(slot,h.rect(R.id.main_slot_timer));
            visibleInLayout(f.main.findViewById(R.id.experiment_timer_status));assertEquals(on,tile().isActivated());
        }
    }
    @Test public void customMinutesAreValidatedAndDoNotChangeOnOff()throws Exception{
        ordinary();f.main.findViewById(R.id.experiment_timer_status).performClick();AlertDialog d=dialog();ShadowLooper.shadowMainLooper().idle();
        d.getListView().performItemClick(null,4,4);TextInputEditText field=findInput(d.getWindow().getDecorView());assertNotNull(field);
        field.setText("0");d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();assertTrue(d.isShowing());assertEquals(60,config().durationSeconds());
        field.setText("2,5");d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();ShadowLooper.shadowMainLooper().idle();assertFalse(d.isShowing());assertEquals(150,config().durationSeconds());assertFalse(config().enabled());
    }
    TextInputEditText findInput(View view){
        if(view instanceof TextInputEditText)return (TextInputEditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextInputEditText found=findInput(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    @Test public void nativePortraitFontAndShortLandscapeMatrix()throws Exception{
        for(int[] window:new int[][]{{320,720},{360,800},{720,360}})for(float font:new float[]{1,1.3f}){
            h.close();h=null;RuntimeEnvironment.setQualifiers("w"+window[0]+"dp-h"+window[1]+"dp-mdpi");
            Configuration c=new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());c.fontScale=font;
            RuntimeEnvironment.getApplication().getResources().updateConfiguration(c,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());create();ordinary();
            for(boolean on:new boolean[]{false,true}){
                preset(on,300);layout(window[0],window[1]);checkText(tile());checkText(text(R.id.experiment_timer_value));checkText(text(R.id.experiment_timer_caption));
                Rect preview=h.rect(R.id.camera_preview);assertTrue(preview.height()>0);assertTrue(tile().getWidth()>=48);assertTrue(tile().getHeight()>=48);
                save(window,font,on?"set":"off");
            }
            startOrdinary(300);h.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");ShadowSystemClock.advanceBy(Duration.ofSeconds(12));h.render();layout(window[0],window[1]);
            assertEquals("4:48",text(R.id.experiment_timer_value).getText().toString());checkText(tile());checkText(text(R.id.experiment_timer_value));checkText(text(R.id.experiment_timer_caption));save(window,font,"running");
        }
    }
    void layout(int width,int height){
        h.layout(width,height);f.main.findViewById(R.id.main).getViewTreeObserver().dispatchOnGlobalLayout();h.layout(width,height);
    }
    void visibleInLayout(View view){
        View root=f.main.findViewById(R.id.main);
        while(true){
            assertEquals(View.VISIBLE,view.getVisibility());
            if(view==root)return;
            assertTrue(view.getParent() instanceof View);view=(View)view.getParent();
        }
    }
    void checkText(TextView view){
        assertNotNull(view.getLayout());assertTrue(view.getText().toString(),view.getLayout().getHeight()+view.getCompoundPaddingTop()+view.getCompoundPaddingBottom()<=view.getHeight());
        for(int i=0;i<view.getLineCount();i++)assertEquals(view.getText().toString(),0,view.getLayout().getEllipsisCount(i));
        assertTrue(view.getText().toString(),view.getPaint().measureText(view.getText().toString().replace('\n',' '))<=view.getWidth()||view.getLineCount()>1);
    }
    void save(int[] window,float font,String state)throws Exception{
        String dir=System.getenv("F5A_COCKPIT_EVIDENCE");if(dir==null)return;Files.createDirectories(Paths.get(dir));
        Bitmap bitmap=Bitmap.createBitmap(window[0],window[1],Bitmap.Config.ARGB_8888);f.main.findViewById(R.id.main).draw(new Canvas(bitmap));
        try(OutputStream out=Files.newOutputStream(Paths.get(dir,"f26_timer_"+window[0]+"_"+window[1]+"_font"+Math.round(font*10)+"_"+state+"_synthetic.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
}
