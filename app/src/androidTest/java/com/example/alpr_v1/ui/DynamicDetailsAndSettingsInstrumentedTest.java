package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.*;
import com.example.alpr_v1.acquisition.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;

@RunWith(AndroidJUnit4.class)
public class DynamicDetailsAndSettingsInstrumentedTest {
    @Test public void cropCollectionRejectsEmptyMzAndCarriedTextBeforeCopyingImages() {
        Bitmap bitmap = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        try(ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field active = MainActivity.class.getDeclaredField("collectionActive");
                    active.setAccessible(true); active.setBoolean(activity,true);
                    java.lang.reflect.Field crops = MainActivity.class.getDeclaredField("capturedCrops");
                    crops.setAccessible(true);
                    int before = ((java.util.List<?>)crops.get(activity)).size();
                    java.lang.reflect.Method collect = MainActivity.class.getDeclaredMethod("collectCrops",java.util.List.class);
                    collect.setAccessible(true);
                    collect.invoke(activity,java.util.Arrays.asList(
                            DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,10,"WI1234A",true,""),
                            DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,11,"WI1234A",false,"WI1234A")));
                    assertEquals(before,((java.util.List<?>)crops.get(activity)).size());
                    assertFalse(bitmap.isRecycled());
                    active.setBoolean(activity,false);
                } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        } finally { bitmap.recycle(); }
    }
    @Test public void readingDetailsUseHalfHeightCanvasAndShowEveryEntityAndHudSnapshot() {
        Bitmap bitmap = Bitmap.createBitmap(320,70,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas plateCanvas = new android.graphics.Canvas(bitmap);
        plateCanvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.BLACK); paint.setTextSize(49); paint.setTypeface(android.graphics.Typeface.MONOSPACE);
        plateCanvas.drawText("WI1234A", 42, 52, paint);
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        store.upsertObservation(DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,10,"WI1234A"),
                DynamicRecognitionHistoryInstrumentedTest.telemetry(),true,"normal");
        store.upsertObservation(DynamicRecognitionHistoryInstrumentedTest.observation(null,2,9,8,11,"WI1234A"),
                DynamicRecognitionHistoryInstrumentedTest.telemetry(),true,"zoom");
        try(ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Method open = MainActivity.class.getDeclaredMethod("showRecognitionHistoryDetails", RecognitionHistoryItem.class);
                    open.setAccessible(true); open.invoke(activity,store.newestFirst().get(0));
                } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            onView(withId(R.id.history_detail_preview)).check((view, error) -> {
                if (error != null) throw error;
                assertEquals(Math.round(90 * view.getResources().getDisplayMetrics().density),view.getHeight());
            });
            int[] previewPosition = new int[2];
            onView(withId(R.id.history_detail_preview)).check((view,error) -> view.getLocationOnScreen(previewPosition));
            onView(withText("P4")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isSelected()));
            onView(withId(R.id.history_detail_pager)).perform(swipeLeft());
            onView(withText("P9")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isSelected()));
            onView(withId(R.id.history_detail_pager)).check((view, error) -> {
                if (error != null) throw error;
                String text = allText(view);
                for (String expected : new String[]{"29,5","38,2","67,0","onnx_int8","960 × 1280"})
                    assertTrue(expected + " missing", text.contains(expected));
                save(view.getRootView(),"dynamic-reading-details.png");
            });
            onView(withId(R.id.history_detail_pager)).perform(swipeUp());
            onView(withId(R.id.history_detail_pager)).check((view,error) -> {
                androidx.recyclerview.widget.RecyclerView pager = (androidx.recyclerview.widget.RecyclerView)view;
                android.widget.ScrollView page = (android.widget.ScrollView)pager.getLayoutManager().findViewByPosition(1);
                assertNotNull(page);
                assertTrue("Observation content must actually scroll", page.getScrollY() > 0);
            });
            onView(withId(R.id.history_detail_preview)).check((view,error) -> {
                int[] current = new int[2]; view.getLocationOnScreen(current);
                assertArrayEquals(previewPosition,current);
                assertTrue(view.isShown());
            });
            Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screen);
            try(java.io.FileOutputStream output = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .openFileOutput("dynamic-reading-details-scrolled.png",Context.MODE_PRIVATE)) {
                screen.compress(Bitmap.CompressFormat.PNG,100,output);
            } catch(java.io.IOException error) { throw new AssertionError(error); }
            finally { screen.recycle(); }
            onView(withText("P4")).perform(click());
            onView(withText("P4")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isSelected()));
            onView(withId(R.id.history_detail_close)).perform(click());
        } finally { store.clear(); bitmap.recycle(); }
    }
    @Test public void optionsValidateAndPersistSizeThresholdsAndPrimaryRegion() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences(SettingsActivity.PREFERENCES,Context.MODE_PRIVATE);
        DynamicMtConfig previous = DynamicMtSettings.read(preferences);
        try(ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.settings_dynamic_mt).performClick());
            onView(withId(R.id.dynamic_mt_enter_width)).perform(replaceText("0"),closeSoftKeyboard());
            onView(withText(R.string.settings_dynamic_mt_save)).perform(click());
            onView(withId(R.id.dynamic_mt_enter_width)).check((view,error) -> assertNotNull(((TextView)view).getError()));
            int[] ids = {R.id.dynamic_mt_enter_width,R.id.dynamic_mt_enter_height,R.id.dynamic_mt_keep_width,
                    R.id.dynamic_mt_keep_height,R.id.dynamic_mt_primary_top};
            String[] values = {"160","120","140","100","40"};
            for (int i=0;i<ids.length;i++) onView(withId(ids[i])).perform(replaceText(values[i]),closeSoftKeyboard());
            onView(withText(R.string.settings_dynamic_mt_save)).perform(click());
            DynamicMtConfig saved = DynamicMtSettings.read(preferences);
            assertEquals(160,saved.size.enterWidth); assertEquals(120,saved.size.enterHeight);
            assertEquals(140,saved.size.keepWidth); assertEquals(100,saved.size.keepHeight);
            assertEquals(.4f,saved.primaryTopFraction,.001f);
            scenario.onActivity(activity -> activity.findViewById(R.id.settings_dynamic_mt).performClick());
            onView(withId(R.id.dynamic_mt_enter_width)).check((view,error) -> save(view.getRootView(),"dynamic-mt-options.png"));
            onView(withText(R.string.menu_close)).perform(click());
        } finally { DynamicMtSettings.save(preferences,previous); }
    }
    static String allText(View view) {
        StringBuilder text = new StringBuilder(view instanceof TextView ? ((TextView)view).getText() : "");
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) text.append("\n").append(allText(group.getChildAt(i)));
        }
        return text.toString();
    }
    private static void save(View root,String name) {
        Bitmap image = Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        try(java.io.FileOutputStream stream = root.getContext().openFileOutput(name,Context.MODE_PRIVATE)) {
            image.compress(Bitmap.CompressFormat.PNG,100,stream);
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        finally { image.recycle(); }
    }
}
