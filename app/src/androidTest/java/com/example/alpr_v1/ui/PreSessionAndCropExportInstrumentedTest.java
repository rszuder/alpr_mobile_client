package com.example.alpr_v1.ui;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.*;
import com.example.alpr_v1.camera.PhoneOrientationEstimator;
import java.io.File;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class PreSessionAndCropExportInstrumentedTest {
    @Test public void enablingAcquisitionImportsEarlierReadsWithOriginalEvidenceOnlyOnce() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(40,12,Bitmap.Config.ARGB_8888);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(liveIntent())) {
            scenario.onActivity(activity -> {
                try {
                    CaptureGalleryViewModel state = new ViewModelProvider(activity).get(CaptureGalleryViewModel.class);
                    state.recognitionHistory().clear(); state.recentReads.clear();
                    set(activity,"collectionActive",false);
                    java.lang.reflect.Method collect = MainActivity.class.getDeclaredMethod("collectRecognitionHistory",java.util.List.class);
                    collect.setAccessible(true);
                    collect.invoke(activity,java.util.Arrays.asList(
                            DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,10,"AAA"),
                            DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,11,"aaa"),
                            DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,12,"AAB"),
                            DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,13,"AAB",true,"")));
                    assertEquals(0,state.recognitionHistory().size());
                    assertEquals(0,state.capturedCrops().size());
                    assertEquals(3,state.recentReads.entries().size());
                    assertEquals("",field(activity,"collectionSessionId"));
                    Object originalTelemetry = state.recentReads.entries().get(0).telemetry;
                    set(activity,"cameraStarted",true);
                    invoke(activity,"toggleCollection");
                    assertEquals(2,state.recognitionHistory().size());
                    assertEquals(2,state.capturedCrops().size());
                    RecognitionHistoryItem aaa = state.recognitionHistory().newestFirst().stream()
                            .filter(item -> item.text.equals("AAA")).findFirst().get();
                    assertEquals(2,aaa.observationRecords().size());
                    assertEquals(4,aaa.observationRecords().get(0).entityId);
                    assertEquals(9,aaa.observationRecords().get(1).entityId);
                    assertEquals("AAA",aaa.text);
                    assertEquals("aaa",aaa.observationRecords().get(1).rawPrediction);
                    assertEquals(10,aaa.observationRecords().get(0).capturedAtMillis);
                    assertSame(originalTelemetry,aaa.observationRecords().get(0).telemetry);
                    invoke(activity,"toggleCollection");
                    invoke(activity,"toggleCollection");
                    assertEquals(2,aaa.observationRecords().size());
                    assertEquals(2,state.capturedCrops().size());
                    assertTrue(activity.getString(R.string.camera_action_crops).startsWith("Akwizycja"));
                    assertEquals("Galeria",activity.getString(R.string.camera_action_recent));
                } catch(Exception error) { throw new AssertionError(error); }
                finally { try { set(activity,"cameraStarted",false); } catch(Exception error) { throw new AssertionError(error); } }
            });
        } finally { bitmap.recycle(); }
    }
    @Test public void bothControlBarsAndLiveLevelWorkBeforeAnalysisAndResumeAfterPause() throws Exception {
        try(ActivityScenario<MainActivity> scenario = ActivityScenario.launch(liveIntent())) {
            scenario.onActivity(activity -> {
                try {
                    assertFalse((boolean)field(activity,"cameraStarted"));
                    assertTrue(activity.findViewById(R.id.auto_zoom_control).isShown());
                    assertTrue(activity.findViewById(R.id.camera_action_row).isShown());
                    for(int id : new int[]{R.id.live_scene_mode_toggle,R.id.search_plate_button,R.id.release_target_button,
                            R.id.auto_zoom_button,R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button})
                        assertTrue(activity.findViewById(id).isShown());
                    View level = activity.findViewById(R.id.phone_orientation_panel);
                    assertTrue(level.isShown());
                    assertFalse(activity.findViewById(R.id.collection_toggle).isEnabled());
                    Object monitor = field(activity,"cameraMotionMonitor");
                    PhoneOrientationEstimator orientation = (PhoneOrientationEstimator)field(monitor,"orientation");
                    orientation.reset();
                    orientation.update(0,9.81f,0,SystemClock.elapsedRealtimeNanos());
                    set(activity,"lastPhoneOrientationUiNanos",0L); invoke(activity,"updatePhoneOrientationHud");
                    assertEquals(activity.getString(R.string.phone_orientation_level,0),level.getContentDescription());
                    orientation.reset(); orientation.update(9.81f,0,0,SystemClock.elapsedRealtimeNanos());
                    set(activity,"lastPhoneOrientationUiNanos",0L); invoke(activity,"updatePhoneOrientationHud");
                    assertEquals(activity.getString(R.string.phone_orientation_tilted,90),level.getContentDescription());
                    assertTrue((boolean)field(activity,"phoneOrientationRunning"));
                } catch(Exception error) { throw new AssertionError(error); }
            });
            Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try(java.io.FileOutputStream output = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .openFileOutput("pre-session-controls.png",android.content.Context.MODE_PRIVATE)) {
                screen.compress(Bitmap.CompressFormat.PNG,100,output);
            } finally { screen.recycle(); }
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(activity -> { try {
                assertFalse((boolean)field(activity,"phoneOrientationRunning"));
                assertFalse((boolean)field(field(activity,"cameraMotionMonitor"),"running"));
            } catch(Exception error) { throw new AssertionError(error); } });
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> { try {
                assertTrue((boolean)field(activity,"phoneOrientationRunning"));
                assertTrue(activity.findViewById(R.id.phone_orientation_panel).isShown());
            } catch(Exception error) { throw new AssertionError(error); } });
        }
    }
    @Test public void galleryOffersSessionPickerAndExportsZipThroughContentResolver() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(40,12,Bitmap.Config.ARGB_8888);
        String id = "ui-test-"+UUID.randomUUID();
        CaptureGalleryViewModel[] state = new CaptureGalleryViewModel[1];
        File target = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),id+".zip");
        try(ActivityScenario<MainActivity> scenario = ActivityScenario.launch(liveIntent())) {
            scenario.onActivity(activity -> state[0] = new ViewModelProvider(activity).get(CaptureGalleryViewModel.class));
            state[0].cropSessions(InstrumentationRegistry.getInstrumentation().getTargetContext())
                    .record(id,DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,10,"AAA"),null,"normal").get();
            scenario.onActivity(activity -> activity.findViewById(R.id.gallery_open_button).performClick());
            onView(withId(R.id.gallery_export_crop_session)).check(matches(isDisplayed())).perform(click());
            waitForExport(state[0]);
            onView(withText(R.string.crop_session_choose)).check(matches(isDisplayed()));
            onView(withText(R.string.menu_close)).perform(click());
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Method export = MainActivity.class.getDeclaredMethod("writeCropSessionExport",String.class,Uri.class);
                    export.setAccessible(true); export.invoke(activity,id,Uri.fromFile(target));
                } catch(Exception error) { throw new AssertionError(error); }
            });
            waitForExport(state[0]);
            assertTrue(target.isFile());
            try(java.util.zip.ZipFile zip = new java.util.zip.ZipFile(target)) {
                assertNotNull(zip.getEntry("session.json")); assertNotNull(zip.getEntry("crop-000001.jpg")); assertEquals(2,zip.size());
            }
        } finally { bitmap.recycle(); }
    }
    private static void waitForExport(CaptureGalleryViewModel state) throws Exception {
        long deadline = SystemClock.elapsedRealtime()+20_000;
        while(state.cropSessionExportBusy && SystemClock.elapsedRealtime()<deadline) Thread.sleep(25);
        assertFalse("Session operation timed out",state.cropSessionExportBusy);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static android.content.Intent liveIntent() {
        return new android.content.Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class)
                .putExtra("debug_baseline_profile","live");
    }
    private static Object field(Object owner,String name) throws Exception {
        java.lang.reflect.Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        java.lang.reflect.Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner,value);
    }
    private static void invoke(Object owner,String name) throws Exception {
        java.lang.reflect.Method method=owner.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(owner);
    }
}
