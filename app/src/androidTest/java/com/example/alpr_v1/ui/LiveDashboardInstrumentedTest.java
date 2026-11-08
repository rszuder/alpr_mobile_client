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
    @Test public void compactOverlayFitsShortAndTallScreensWithoutResizingPreview() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                View root=activity.getLayoutInflater().inflate(R.layout.activity_main,null);
                LivePresentationController.configureLayout(root);
                for(int[] row:actionIds())for(int id:row)root.findViewById(id).setVisibility(View.VISIBLE);
                root.findViewById(R.id.confirmed_result_tray).setVisibility(View.VISIBLE);
                ((TextView)root.findViewById(R.id.confirmed_result_text)).setText("RJA12455");
                float density=activity.getResources().getDisplayMetrics().density;
                for(int heightDp:new int[]{800,640}){
                    measure(root,Math.round(360*density),Math.round(heightDp*density));
                    assertGrid(root,density);
                    android.graphics.Rect preview=rect(root,R.id.camera_preview);
                    root.findViewById(R.id.experiment_timer_status).setVisibility(View.VISIBLE);
                    ((TextView)root.findViewById(R.id.experiment_timer_value)).setText("2:34");
                    measure(root,Math.round(360*density),Math.round(heightDp*density));assertEquals(preview,rect(root,R.id.camera_preview));
                    root.findViewById(R.id.experiment_timer_status).setVisibility(View.GONE);
                    root.findViewById(R.id.confirmed_result_tray).setVisibility(View.GONE);
                    measure(root,Math.round(360*density),Math.round(heightDp*density));assertEquals(preview,rect(root,R.id.camera_preview));
                    savePreview(activity,root,"main-2x6-"+heightDp+".png");
                }
            });
        }
    }
    @Test public void hudKeepsValuesReadableWithLargerTextOnNarrowScreen() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
                config.fontScale=1.3f;config.screenWidthDp=320;config.screenHeightDp=640;
                android.content.Context context=new android.view.ContextThemeWrapper(activity.createConfigurationContext(config),R.style.Theme_ALPR_v1);
                View root=android.view.LayoutInflater.from(context).inflate(R.layout.activity_main,null);
                LivePresentationController.configureLayout(root);
                for(int[] row:actionIds())for(int id:row)root.findViewById(id).setVisibility(View.VISIBLE);
                float density=context.getResources().getDisplayMetrics().density;
                measure(root,Math.round(320*density),Math.round(640*density));LivePresentationController.fitGridLabels(root);
                measure(root,Math.round(320*density),Math.round(640*density));assertGrid(root,density);
                assertEquals(View.VISIBLE,root.findViewById(R.id.main_hud_storage).getVisibility());
                assertEquals(View.GONE,root.findViewById(R.id.hud1_viewport).getVisibility());
                assertEquals(View.VISIBLE,root.findViewById(R.id.hud2_viewport).getVisibility());
                assertEquals(rect(root,R.id.camera_preview),rect(root,R.id.main_hud_storage));
                savePreview(activity,root,"main-2x6-large-text.png");
            });
        }
    }
    private static int[][] actionIds(){return new int[][]{
        {R.id.live_scene_mode_toggle,R.id.search_plate_button,R.id.release_target_button,R.id.auto_zoom_button,R.id.live_diagnostics_toggle,R.id.live_model_info_toggle},
        {R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.experiment_timer_button,R.id.export_report_button}};}
    private static android.graphics.Rect rect(View root,int id){
        View view=root.findViewById(id);android.graphics.Rect r=new android.graphics.Rect(0,0,view.getWidth(),view.getHeight());
        ((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(view,r);return r;
    }
    private static void assertGrid(View root,float density){
        android.graphics.Rect preview=rect(root,R.id.camera_preview);assertTrue(preview.height()>0);
        for(int row=0;row<2;row++){
            android.view.ViewGroup bar=root.findViewById(row==0?R.id.auto_zoom_control:R.id.camera_action_row);assertEquals(6,bar.getChildCount());
            int minimum=Integer.MAX_VALUE,maximum=0;
            for(int id:actionIds()[row]){
                View tile=root.findViewById(id);minimum=Math.min(minimum,tile.getWidth());maximum=Math.max(maximum,tile.getWidth());
                assertTrue(tile.getWidth()+1>=48*density);assertTrue(tile.getHeight()+1>=48*density);
                assertFalse(android.graphics.Rect.intersects(preview,rect(root,id)));
            }assertTrue(maximum-minimum<=1);
        }
        for(int id:new int[]{R.id.top_app_bar,R.id.normal_camera_hud,R.id.tv_frame_summary,R.id.main_device_result_row})
            assertFalse(android.graphics.Rect.intersects(preview,rect(root,id)));
    }
    @Test public void mzTimingSurvivesSkippedFrameAndHudTogglesAreIndependent() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MetricsCollector metrics = new MetricsCollector();
                metrics.startMeasurementSession();
                com.example.alpr_v1.metrics.InferenceTrace mz = new com.example.alpr_v1.metrics.InferenceTrace(2);
                mz.putDurationNanos("character_inference", 420_000_000L);
                metrics.add(mz);
                com.example.alpr_v1.metrics.InferenceTrace mp = new com.example.alpr_v1.metrics.InferenceTrace(3);
                mp.putDurationNanos("vehicle_inference", 85_000_000L);
                metrics.add(mp);
                LiveHudView hud = hudView(activity,R.id.live_hud_row);
                hud.clearMetrics();
                hud.render(metrics.liveSnapshot(), "960×1280", false, 30);
                assertEquals("420", ((TextView)hud.findViewById(R.id.hud_mz_time)).getText().toString());
                assertFalse(mp.durationsNanos().containsKey("character_inference"));
                activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                assertEquals(View.VISIBLE,hud.getVisibility());
                assertEquals(View.INVISIBLE,hudView(activity,R.id.live_model_info).getVisibility());
                activity.findViewById(R.id.live_model_info_toggle).performClick();
                assertEquals(View.VISIBLE,hud.getVisibility());
                assertEquals(View.VISIBLE,hudView(activity,R.id.live_model_info).getVisibility());
                showModelInfo((LiveModelInfoView)hudView(activity,R.id.live_model_info));
                String variant=((TextView)hudView(activity,R.id.hud_mz_variant)).getText().toString();
                assertTrue(variant.contains("INT8"));assertTrue(variant.contains("CPU"));
                assertTrue(hudView(activity,R.id.hud_mz_variant).getContentDescription().toString().contains("onnx_int8"));
                assertEquals("Kadr: 960×1280",((TextView)hudView(activity,R.id.hud_source_resolution)).getText().toString());
                activity.findViewById(R.id.live_model_info_toggle).performClick();
                assertEquals(View.INVISIBLE,hudView(activity,R.id.live_model_info).getVisibility());
                assertEquals(View.VISIBLE,hud.getVisibility());
                assertEquals("420",((TextView)hud.findViewById(R.id.hud_mz_time)).getText().toString());
                activity.findViewById(R.id.live_diagnostics_toggle).performClick();assertEquals(View.GONE,hud.getVisibility());
                metrics.startMeasurementSession();
                assertTrue(Double.isNaN(metrics.liveSnapshot().characterInferenceMs));
            });
        }
    }
    private static void showModelInfo(LiveModelInfoView info) {
        info.setVisibility(View.VISIBLE);
        info.render(java.util.Arrays.asList(
                new com.example.alpr_v1.pipeline.ModelRuntimeSummary("MP", "Vehicle", "tflite_fp32", "FP32", "LiteRT/CPU"),
                new com.example.alpr_v1.pipeline.ModelRuntimeSummary("MT", "Plate", "tflite_fp16", "FP16", "LiteRT/GPU"),
                new com.example.alpr_v1.pipeline.ModelRuntimeSummary("MZ", "Characters", "onnx_int8", "INT8", "ONNX Runtime/CPU")
        ), "Kadr: 960×1280");
    }
    @Test public void levelShowsValidWarningAndUnavailableStatesAndAnimatesBall() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Method stop = MainActivity.class.getDeclaredMethod("stopPhoneOrientationUi");
                    stop.setAccessible(true); stop.invoke(activity);
                } catch(Exception error) { throw new AssertionError(error); }
                PhoneLevelView level = activity.findViewById(R.id.phone_orientation_panel);
                level.setVisibility(View.VISIBLE);
                level.render(orientation(0f));
                assertTrue(level.getContentDescription().toString().contains("poprawnie"));
                assertCircleColor(level, activity.getColor(R.color.cockpit_cyan));
                level.render(orientation(40f));
                assertCircleColor(level, activity.getColor(R.color.alpr_warning));
                try { assertEquals(0f, field(level, "ballX").getFloat(level), .01f); }
                catch(Exception e) { throw new AssertionError(e); }
            });
            android.os.SystemClock.sleep(300L);
            scenario.onActivity(activity -> {
                PhoneLevelView level = activity.findViewById(R.id.phone_orientation_panel);
                try { assertTrue(field(level, "ballX").getFloat(level) > .9f); }
                catch(Exception e) { throw new AssertionError(e); }
                level.render(com.example.alpr_v1.camera.PhoneOrientationEstimator.Snapshot.unavailable());
                assertCircleColor(level, 0xFF8C9AAA);
                assertTrue(level.getContentDescription().toString().contains("brak"));
            });
        }
    }
    private static com.example.alpr_v1.camera.PhoneOrientationEstimator.Snapshot orientation(float degrees) {
        com.example.alpr_v1.camera.PhoneOrientationEstimator estimator =
                new com.example.alpr_v1.camera.PhoneOrientationEstimator();
        double radians = Math.toRadians(degrees);
        estimator.update((float)(-9.81 * Math.sin(radians)), (float)(9.81 * Math.cos(radians)), 0f, 1L);
        return estimator.snapshot(1L, 0);
    }
    private static void measure(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }
    private static void assertCircleColor(PhoneLevelView level, int expected) {
        int size = Math.round(56 * level.getResources().getDisplayMetrics().density);
        measure(level, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        level.draw(new android.graphics.Canvas(bitmap));
        int x = Math.round(3 * level.getResources().getDisplayMetrics().density);
        assertEquals(expected, bitmap.getPixel(x, size / 2));
        bitmap.recycle();
    }
    private static void savePreview(MainActivity activity, View root, String name) {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bitmap));
        try(java.io.FileOutputStream output = activity.openFileOutput(name, android.content.Context.MODE_PRIVATE)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch(java.io.IOException e) { throw new AssertionError(e); }
        finally { bitmap.recycle(); }
    }
    @Test public void hudToggleCloseAndMeasuredFpsPreserveUnknownTimings() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                try {
                    LivePresentationController presentation=(LivePresentationController)field(activity,"livePresentation").get(activity);
                    presentation.showState(LivePresentationController.State.STOPPED,"");
                    assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                    activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                    assertEquals(View.VISIBLE,hudView(activity,R.id.live_hud_row).getVisibility());
                    java.lang.reflect.Method renderHud=MainActivity.class.getDeclaredMethod("renderLiveHud");
                    renderHud.setAccessible(true);renderHud.invoke(activity);
                    assertEquals(View.VISIBLE,hudView(activity,R.id.live_hud_row).getVisibility());
                    assertEquals("—",((TextView)hudView(activity,R.id.hud_camera_fps)).getText().toString());
                    for(LivePresentationController.State state:new LivePresentationController.State[]{
                            LivePresentationController.State.PREVIEW,LivePresentationController.State.SETUP_REQUIRED,
                            LivePresentationController.State.SEARCHING}) {
                        presentation.showState(state,"");
                        assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                        assertEquals(View.VISIBLE,hudView(activity,R.id.live_hud_row).getVisibility());
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
                    ((LiveHudView)hudView(activity,R.id.live_hud_row)).render(snapshot,"960 × 1280",false,29.5);
                    assertEquals("29,5",((TextView)hudView(activity,R.id.hud_camera_fps)).getText().toString());
                    assertEquals("—",((TextView)hudView(activity,R.id.hud_mp_time)).getText().toString());
                    presentation.stop();
                    java.lang.reflect.Method resetZoom=MainActivity.class.getDeclaredMethod("resetAutoZoomForStoppedCamera");
                    resetZoom.setAccessible(true);resetZoom.invoke(activity);
                    assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                    assertEquals(View.VISIBLE,hudView(activity,R.id.live_hud_row).getVisibility());
                    assertEquals("—",((TextView)hudView(activity,R.id.hud_camera_fps)).getText().toString());
                    activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                    assertEquals(View.GONE,hudView(activity,R.id.live_hud_row).getVisibility());
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
    private static <T extends View>T hudView(MainActivity activity,int id){
        return activity.findViewById(id);
    }
    private static Field field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
}
