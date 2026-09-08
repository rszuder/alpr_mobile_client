package com.example.alpr_v1.ui;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveDashboardInstrumentedTest {
    @Test public void fourEqualTilesAndHudFitAboveControlsOnShortAndTallScreens() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                View root=activity.getLayoutInflater().inflate(R.layout.activity_main,null);
                root.findViewById(R.id.recognition_hint).setVisibility(View.GONE);
                root.findViewById(R.id.export_report_button).setVisibility(View.GONE);
                root.findViewById(R.id.live_status_strip).setVisibility(View.VISIBLE);
                root.findViewById(R.id.auto_zoom_control).setVisibility(View.VISIBLE);
                root.findViewById(R.id.confirmed_result_tray).setVisibility(View.VISIBLE);
                root.findViewById(R.id.live_hud_row).setVisibility(View.VISIBLE);
                ((TextView)root.findViewById(R.id.confirmed_result_text)).setText("RJA12455");
                for(int height:new int[]{1400,1050}) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,720,height);
                    int tileHeight=root.findViewById(R.id.auto_zoom_control).getHeight();
                    int previous=-1;
                    for(int id:new int[]{R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button}) {
                        View tile=root.findViewById(id);assertEquals(tileHeight,tile.getHeight());
                        if(previous>0)assertTrue(Math.abs(previous-tile.getWidth())<=2);previous=tile.getWidth();
                    }
                    View hud=root.findViewById(R.id.live_hud_row), result=root.findViewById(R.id.confirmed_result_tray);
                    assertTrue(hud.getHeight()>0);assertTrue(hud.getBottom()<result.getTop());
                    assertTrue(result.getBottom()<root.findViewById(R.id.auto_zoom_control).getTop());
                }
            });
        }
    }
    @Test public void hudToggleCloseAndMeasuredFpsPreserveUnknownTimings() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                try {
                    LivePresentationController presentation=(LivePresentationController)field(activity,"livePresentation").get(activity);
                    presentation.showState(LivePresentationController.State.STOPPED,"");
                    assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                    activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                    assertEquals(View.VISIBLE,activity.findViewById(R.id.live_hud_row).getVisibility());
                    java.lang.reflect.Method renderHud=MainActivity.class.getDeclaredMethod("renderLiveHud");
                    renderHud.setAccessible(true);renderHud.invoke(activity);
                    assertEquals(View.VISIBLE,activity.findViewById(R.id.live_hud_row).getVisibility());
                    assertEquals("—",((TextView)activity.findViewById(R.id.hud_camera_fps)).getText().toString());
                    for(LivePresentationController.State state:new LivePresentationController.State[]{
                            LivePresentationController.State.PREVIEW,LivePresentationController.State.SETUP_REQUIRED,
                            LivePresentationController.State.SEARCHING}) {
                        presentation.showState(state,"");
                        assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                        assertEquals(View.VISIBLE,activity.findViewById(R.id.live_hud_row).getVisibility());
                    }
                    MetricsCollector metrics=new MetricsCollector();
                    metrics.startMeasurementSession(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos()-2_500_000_000L,System.nanoTime());
                    Map<Long,Object> buckets=(Map<Long,Object>)field(metrics,"frameFlowBuckets").get(metrics);
                    Class<?> type=Class.forName("com.example.alpr_v1.metrics.MetricsCollector$FrameFlowBucket");
                    java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor(long.class);ctor.setAccessible(true);
                    for(int i=0;i<2;i++) {
                        Object bucket=ctor.newInstance(i*1000L);
                        field(bucket,"framesReceived").setLong(bucket,i==0?30:24);
                        field(bucket,"framesProcessed").setLong(bucket,i==0?3:1);
                        buckets.put(i*1000L,bucket);
                    }
                    MetricsCollector.LiveSnapshot snapshot=metrics.liveSnapshot();
                    assertEquals(27,snapshot.receivedFps,.001);assertEquals(2,snapshot.processedFps,.001);
                    assertEquals(2,buckets.size());
                    ((LiveHudView)activity.findViewById(R.id.live_hud_row)).render(snapshot,"960 × 1280",false,29.5);
                    assertEquals("29,5",((TextView)activity.findViewById(R.id.hud_camera_fps)).getText().toString());
                    assertEquals("—",((TextView)activity.findViewById(R.id.hud_pipeline_time)).getText().toString());
                    assertEquals("—",((TextView)activity.findViewById(R.id.hud_mp_time)).getText().toString());
                    presentation.stop();
                    java.lang.reflect.Method resetZoom=MainActivity.class.getDeclaredMethod("resetAutoZoomForStoppedCamera");
                    resetZoom.setAccessible(true);resetZoom.invoke(activity);
                    assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                    assertEquals(View.VISIBLE,activity.findViewById(R.id.live_hud_row).getVisibility());
                    assertEquals("—",((TextView)activity.findViewById(R.id.hud_camera_fps)).getText().toString());
                    activity.findViewById(R.id.hud_close).performClick();
                    assertEquals(View.GONE,activity.findViewById(R.id.live_hud_row).getVisibility());
                    metrics.startMeasurementSession();assertTrue(Double.isNaN(metrics.liveSnapshot().receivedFps));
                } catch(Exception e) {throw new AssertionError(e);}
            });
        }
    }
    @Test public void historyCountLivesInBadgeAndAccessibleName() {
        Bitmap bitmap=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                try {
                    field(activity,"experimentModeEnabled").setBoolean(activity,false);
                    com.example.alpr_v1.capture.RecognitionHistoryStore history=(com.example.alpr_v1.capture.RecognitionHistoryStore)field(activity,"recognitionHistory").get(activity);
                    history.clear();
                    history.upsert(1,2,3,4,4,"RJA12455",.94,.9,1,bitmap,Collections.emptyList(),null,true,3,.8f,"normal");
                    java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderCapturedCrops");render.setAccessible(true);render.invoke(activity);
                    assertEquals("1",((TextView)activity.findViewById(R.id.gallery_count_badge)).getText().toString());
                    assertEquals(activity.getString(R.string.camera_action_recent),((TextView)activity.findViewById(R.id.gallery_open_button)).getText().toString());
                    assertTrue(activity.findViewById(R.id.gallery_open_button).getContentDescription().toString().contains("1"));
                    history.clear();render.invoke(activity);
                    assertEquals("0",((TextView)activity.findViewById(R.id.gallery_count_badge)).getText().toString());
                } catch(Exception e) {throw new AssertionError(e);}
            });
        } finally {bitmap.recycle();}
    }
    private static Field field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
}
