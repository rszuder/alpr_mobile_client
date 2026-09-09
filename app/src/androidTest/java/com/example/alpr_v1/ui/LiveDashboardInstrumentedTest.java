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
                root.findViewById(R.id.recognition_hint).setVisibility(View.GONE);
                root.findViewById(R.id.export_report_button).setVisibility(View.GONE);
                root.findViewById(R.id.live_status_strip).setVisibility(View.VISIBLE);
                root.findViewById(R.id.auto_zoom_control).setVisibility(View.VISIBLE);
                root.findViewById(R.id.confirmed_result_tray).setVisibility(View.VISIBLE);
                root.findViewById(R.id.live_hud_row).setVisibility(View.VISIBLE);
                showModelInfo(root);
                TextView event = root.findViewById(R.id.live_event);
                event.setVisibility(View.VISIBLE);
                event.setText("Cel ustabilizowany");
                ((TextView)root.findViewById(R.id.hud_camera_fps)).setText("29,5");
                ((TextView)root.findViewById(R.id.hud_temperature)).setText("38,2°");
                ((TextView)root.findViewById(R.id.hud_resources)).setText("67%");
                ((TextView)root.findViewById(R.id.hud_mp_time)).setText("132 ms");
                ((TextView)root.findViewById(R.id.hud_mt_time)).setText("83 ms");
                ((TextView)root.findViewById(R.id.hud_mz_time)).setText("1,42 s");
                new PhoneOrientationHud(root.findViewById(R.id.phone_orientation_panel)).render(orientation(0f));
                ((TextView)root.findViewById(R.id.confirmed_result_text)).setText("RJA12455");
                float density = activity.getResources().getDisplayMetrics().density;
                int width = Math.round(360 * density);
                for(int heightDp:new int[]{800,640}) {
                    int height = Math.round(heightDp * density);
                    measure(root, width, height);
                    int tileHeight=root.findViewById(R.id.auto_zoom_control).getHeight();
                    int previous=-1;
                    for(int id:new int[]{R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button}) {
                        View tile=root.findViewById(id);assertEquals(tileHeight,tile.getHeight());
                        if(previous>0)assertTrue(Math.abs(previous-tile.getWidth())<=2);previous=tile.getWidth();
                    }
                    View hud=root.findViewById(R.id.live_hud_row), result=root.findViewById(R.id.confirmed_result_tray);
                    View modelInfo = root.findViewById(R.id.live_model_info);
                    assertTrue(hud.getHeight()>0);assertTrue(hud.getBottom()<result.getTop());
                    assertTrue(hud.getWidth() < width * .65f);
                    assertTrue(hud.getHeight() < 190 * density);
                    assertNull(hud.getBackground());
                    assertFalse(hud.isClickable());
                    assertTrue(event.getRight() < hud.getLeft());
                    assertTrue(root.findViewById(R.id.phone_orientation_panel).getBottom() <= result.getTop());
                    assertTrue(result.getBottom()<root.findViewById(R.id.auto_zoom_control).getTop());
                    assertTrue(result.getBottom() < modelInfo.getTop());
                    assertTrue(modelInfo.getBottom() < root.findViewById(R.id.auto_zoom_control).getTop());
                    int previewHeight = root.findViewById(R.id.camera_preview).getHeight();
                    hud.setVisibility(View.GONE);
                    root.findViewById(R.id.phone_orientation_panel).setVisibility(View.GONE);
                    modelInfo.setVisibility(View.GONE);
                    measure(root, width, height);
                    assertEquals(previewHeight, root.findViewById(R.id.camera_preview).getHeight());
                    hud.setVisibility(View.VISIBLE);
                    root.findViewById(R.id.phone_orientation_panel).setVisibility(View.VISIBLE);
                    modelInfo.setVisibility(View.VISIBLE);
                    measure(root, width, height);
                    savePreview(activity, root, "hud-compact-" + heightDp + ".png");
                }
            });
        }
    }
    @Test public void hudKeepsValuesReadableWithLargerTextOnNarrowScreen() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
                config.fontScale = 1.3f;
                android.content.Context context = new android.view.ContextThemeWrapper(
                        activity.createConfigurationContext(config), R.style.Theme_ALPR_v1);
                View root = android.view.LayoutInflater.from(context).inflate(R.layout.activity_main, null);
                root.findViewById(R.id.recognition_hint).setVisibility(View.GONE);
                root.findViewById(R.id.export_report_button).setVisibility(View.GONE);
                root.findViewById(R.id.auto_zoom_control).setVisibility(View.VISIBLE);
                root.findViewById(R.id.live_hud_row).setVisibility(View.VISIBLE);
                showModelInfo(root);
                ((TextView)root.findViewById(R.id.hud_camera_fps)).setText("120,0");
                ((TextView)root.findViewById(R.id.hud_temperature)).setText("38,2°");
                ((TextView)root.findViewById(R.id.hud_resources)).setText("100%");
                ((TextView)root.findViewById(R.id.hud_mp_time)).setText("12,34 s");
                float density = context.getResources().getDisplayMetrics().density;
                measure(root, Math.round(320 * density), Math.round(640 * density));
                View hud = root.findViewById(R.id.live_hud_row);
                assertTrue(hud.getLeft() >= 0);
                assertTrue(hud.getBottom() < root.findViewById(R.id.auto_zoom_control).getTop());
                assertTrue(root.findViewById(R.id.live_model_info).getBottom()
                        < root.findViewById(R.id.auto_zoom_control).getTop());
                for(int id : new int[]{R.id.hud_camera_fps,R.id.hud_temperature,R.id.hud_resources,R.id.hud_mp_time}) {
                    TextView value = root.findViewById(id);
                    assertTrue(value.getLayout().getEllipsisCount(0) == 0);
                    assertTrue(value.getPaint().measureText(value.getText().toString()) <= value.getWidth() + 1);
                }
                savePreview(activity, root, "hud-compact-large-text.png");
            });
        }
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
                LiveHudView hud = activity.findViewById(R.id.live_hud_row);
                hud.clearMetrics();
                hud.render(metrics.liveSnapshot(), "960×1280", false, 30);
                assertEquals("420 ms", ((TextView)hud.findViewById(R.id.hud_mz_time)).getText().toString());
                assertFalse(mp.durationsNanos().containsKey("character_inference"));
                activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                assertEquals(View.VISIBLE, hud.getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.live_model_info).getVisibility());
                activity.findViewById(R.id.live_model_info_toggle).performClick();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.live_model_info).getVisibility());
                showModelInfo(activity.findViewById(R.id.main));
                String variant = ((TextView)activity.findViewById(R.id.hud_mz_variant)).getText().toString();
                assertTrue(variant.contains("onnx_int8"));
                assertTrue(variant.contains("CPU"));
                assertEquals("Kadr: 960×1280", ((TextView)activity.findViewById(R.id.hud_source_resolution)).getText().toString());
                activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                assertEquals(View.GONE, hud.getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.live_model_info).getVisibility());
                activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                activity.findViewById(R.id.live_model_info_toggle).performClick();
                assertEquals(View.VISIBLE, hud.getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.live_model_info).getVisibility());
                activity.findViewById(R.id.live_diagnostics_toggle).performClick();
                assertEquals(View.GONE, hud.getVisibility());
                metrics.startMeasurementSession();
                assertTrue(Double.isNaN(metrics.liveSnapshot().characterInferenceMs));
            });
        }
    }
    private static void showModelInfo(View root) {
        LiveModelInfoView info = root.findViewById(R.id.live_model_info);
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
                assertCircleColor(level, 0xFF50E3A4);
                level.render(orientation(40f));
                assertCircleColor(level, 0xFFFF5252);
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
                    assertEquals("—",((TextView)activity.findViewById(R.id.hud_mp_time)).getText().toString());
                    presentation.stop();
                    java.lang.reflect.Method resetZoom=MainActivity.class.getDeclaredMethod("resetAutoZoomForStoppedCamera");
                    resetZoom.setAccessible(true);resetZoom.invoke(activity);
                    assertTrue(activity.findViewById(R.id.live_diagnostics_toggle).isShown());
                    assertEquals(View.VISIBLE,activity.findViewById(R.id.live_hud_row).getVisibility());
                    assertEquals("—",((TextView)activity.findViewById(R.id.hud_camera_fps)).getText().toString());
                    activity.findViewById(R.id.live_diagnostics_toggle).performClick();
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
