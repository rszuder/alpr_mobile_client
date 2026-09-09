package com.example.alpr_v1.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;
import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class GalleryPresentationInstrumentedTest {
    @Test
    public void cropsButtonControlsBothHistoryAndVerificationCopies() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra("debug_baseline_profile", "live");
        Bitmap bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field started = MainActivity.class.getDeclaredField("cameraStarted");
                    started.setAccessible(true);
                    // Enable the real button without starting camera inference.
                    started.setBoolean(activity, true);
                    java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("renderCollectionControl");
                    render.setAccessible(true);
                    render.invoke(activity);
                    MaterialButton button = activity.findViewById(R.id.collection_toggle);
                    CaptureGalleryViewModel gallery = new ViewModelProvider(activity)
                            .get(CaptureGalleryViewModel.class);
                    assertEquals(activity.getString(R.string.camera_action_crops_off), button.getText().toString());

                    deliverCrop(activity, bitmap, 1L, "OFF1234", 1L);
                    assertEquals(0, gallery.recognitionHistory().size());
                    assertEquals(0, gallery.capturedCrops().size());

                    button.performClick();
                    assertEquals(activity.getString(R.string.camera_action_crops_on), button.getText().toString());
                    deliverCrop(activity, bitmap, 1L, "ON12345", 2L);
                    assertEquals(1, gallery.recognitionHistory().size());
                    assertEquals(1, gallery.capturedCrops().size());

                    button.performClick();
                    // Exercise both a late update of an existing row and a new entity.
                    deliverCrop(activity, bitmap, 1L, "CHANGED", 3L);
                    deliverCrop(activity, bitmap, 2L, "OFF5678", 4L);
                    assertEquals(1, gallery.recognitionHistory().size());
                    assertEquals("ON12345", gallery.recognitionHistory().newestFirst().get(0).text);
                    assertEquals(1, gallery.capturedCrops().size());
                    assertFalse(button.isActivated());

                    button.performClick();
                    deliverCrop(activity, bitmap, 2L, "ON56789", 5L);
                    assertEquals(2, gallery.recognitionHistory().size());
                    assertEquals(2, gallery.capturedCrops().size());
                    button.performClick();
                    started.setBoolean(activity, false);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
        } finally {
            bitmap.recycle();
        }
    }

    private static void deliverCrop(MainActivity activity, Bitmap bitmap, long entity,
            String text, long sequence) throws ReflectiveOperationException {
        com.example.alpr_v1.pipeline.PlateObservation observation =
                new com.example.alpr_v1.pipeline.PlateObservation(
                        entity, com.example.alpr_v1.pipeline.PlateVehicleAssociation.direct(entity, entity, "test"),
                        com.example.alpr_v1.pipeline.MtWorkKind.VEHICLE_ROI,
                        com.example.alpr_v1.pipeline.MtReason.SCAN_NEXT_CANDIDATE,
                        sequence, bitmap, text, 0.9, 0.8, false, 1,
                        java.util.Collections.emptyList(), sequence, sequence * 1_000_000_000L,
                        0.5f, null, timing(),
                        com.example.alpr_v1.pipeline.PlateGeometry.unavailable(),
                        true, true, text, false, 1, "single_row",
                        java.util.Collections.emptyList(), "", text);
        for (String method : new String[]{"collectRecognitionHistory", "collectCrops"}) {
            java.lang.reflect.Method collect = MainActivity.class.getDeclaredMethod(method, List.class);
            collect.setAccessible(true);
            collect.invoke(activity, java.util.Collections.singletonList(observation));
        }
    }

    @Test
    public void galleryRejectsEmptyMzButCollectsPartialReadWithoutWaitingForConsensus() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra("debug_baseline_profile", "live");
        Bitmap bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field active = MainActivity.class.getDeclaredField("collectionActive");
                    active.setAccessible(true);
                    active.setBoolean(activity, true);
                    java.lang.reflect.Method collect = MainActivity.class.getDeclaredMethod(
                            "collectRecognitionHistory", List.class);
                    collect.setAccessible(true);
                    com.example.alpr_v1.capture.RecognitionHistoryStore history =
                            new ViewModelProvider(activity).get(CaptureGalleryViewModel.class)
                                    .recognitionHistory();
                    int expectedReadings = 0;
                    for (String raw : new String[]{"", "W", "WI1"}) {
                        com.example.alpr_v1.pipeline.PlateObservation observation =
                                new com.example.alpr_v1.pipeline.PlateObservation(
                                        1L, com.example.alpr_v1.pipeline.PlateVehicleAssociation
                                                .direct(1L, 1L, "test"),
                                        com.example.alpr_v1.pipeline.MtWorkKind.VEHICLE_ROI,
                                        com.example.alpr_v1.pipeline.MtReason.SCAN_NEXT_CANDIDATE,
                                        1L, bitmap, "OLD1234", 0.9, 0.2, false, 1,
                                        java.util.Collections.emptyList(), raw.length() + 1L, 1L,
                                        0.5f, null, timing(),
                                        com.example.alpr_v1.pipeline.PlateGeometry.unavailable(),
                                        true, !raw.isEmpty(), raw, false, 1, "single_row",
                                        java.util.Collections.emptyList(), "", "OLD1234");
                        collect.invoke(activity, java.util.Collections.singletonList(observation));
                        if (raw.isEmpty()) {
                            assertEquals(0, history.size());
                            continue;
                        }
                        assertEquals(++expectedReadings, history.size());
                        assertEquals(raw, history.newestFirst().get(0).text);
                        assertFalse(history.newestFirst().get(0).confirmed);
                    }
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void narrowDetailKeepsActionLabelsAndIconsVisible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.res.Configuration config = new android.content.res.Configuration(
                context.getResources().getConfiguration());
        config.fontScale = 1.3f;
        Context themed = new ContextThemeWrapper(context.createConfigurationContext(config),
                R.style.Theme_ALPR_v1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View detail = LayoutInflater.from(themed).inflate(
                    R.layout.dialog_recognition_history_detail, null, false);
            float density = themed.getResources().getDisplayMetrics().density;
            int width = Math.round(280 * density);
            detail.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            detail.layout(0, 0, width, detail.getMeasuredHeight());
            for (int id : new int[]{R.id.history_detail_copy, R.id.history_detail_save,
                    R.id.history_detail_delete}) {
                MaterialButton button = detail.findViewById(id);
                assertTrue(button.getIcon() != null);
                assertEquals(MaterialButton.ICON_GRAVITY_TEXT_TOP, button.getIconGravity());
                assertEquals(0, button.getLayout().getEllipsisCount(0));
                assertTrue(button.getPaint().measureText(button.getText().toString())
                        <= button.getWidth() - button.getCompoundPaddingLeft()
                        - button.getCompoundPaddingRight());
                assertTrue(button.getLayout().getHeight() <= button.getHeight()
                        - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
            }
        });
    }

    @Test
    public void sheetStartsInHistoryModeAndResearchOverlayIsReadOnly() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_ALPR_v1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View sheet = LayoutInflater.from(themedContext).inflate(
                    R.layout.bottom_sheet_gallery,
                    null,
                    false
            );
            assertTrue(sheet.findViewById(R.id.gallery_recent_container).getVisibility()
                    == View.VISIBLE);
            assertTrue(sheet.findViewById(R.id.gallery_research_container).getVisibility()
                    == View.GONE);
            PlateCropView crop = sheet.findViewById(R.id.verification_crop);
            assertTrue(crop.boxesVisible());
            crop.setBoxesVisible(false);
            assertFalse(crop.boxesVisible());
            assertFalse(crop.hasOnClickListeners());
            MaterialButton correct = sheet.findViewById(R.id.verification_correct);
            MaterialButton save = sheet.findViewById(R.id.verification_save_current);
            assertEquals(1, correct.getMaxLines());
            assertEquals(1, save.getMaxLines());
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, correct.getLayoutParams().width);
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, save.getLayoutParams().width);
        });
    }

    @Test
    public void normalModeShowsLogicalRecognitionHistory() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra("debug_baseline_profile", "live");
        Bitmap source = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity)
                    .get(CaptureGalleryViewModel.class)
                    .recognitionHistory()
                    .upsert(
                            1L, 2L, 3L, 4L, 4L, "WI1234A", 0.93, 0.90,
                            1L, source, characters(), timing(), true, 4, 0.8f, "normal"
                    ));
            onView(withId(R.id.gallery_open_button)).perform(click());
            onView(withId(R.id.gallery_sheet_title)).check(matches(withText(
                    R.string.history_title
            )));
            onView(withId(R.id.gallery_sheet_list)).check(matches(isDisplayed()));
            onView(withId(R.id.history_number)).check(matches(withText("WI1234A")));
            onView(withId(R.id.history_number)).perform(click());
            onView(withId(R.id.history_detail_pager)).check((view,error) -> {
                if(error != null) throw error;
                String details = DynamicDetailsAndSettingsInstrumentedTest.allText(view);
                for(String expected : new String[]{"2,0 ms","4,0 ms","8,0 ms","40,0 ms"})
                    assertTrue(details.contains(expected));
            });
            onView(withId(R.id.history_detail_characters)).check(matches(withText(
                    containsString("W 97%")
            )));
            onView(withId(R.id.history_detail_scroll)).check((view, error) -> {
                if (error != null) throw error;
                assertTrue(view.getWidth() >= context.getResources().getDisplayMetrics().widthPixels * 0.95f);
                assertTrue(view.getHeight() >= context.getResources().getDisplayMetrics().heightPixels * 0.85f);
            });
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new java.io.File(context.getExternalFilesDir(null), "gallery-detail-qa.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally {
                screenshot.recycle();
            }
            onView(withId(R.id.history_detail_close)).perform(click());
        } finally {
            source.recycle();
        }
    }

    @Test
    public void researchModeShowsSingleSampleVerification() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra("debug_baseline_profile", "r0");
        CapturedPlateItem sample = crop();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity)
                    .get(CaptureGalleryViewModel.class)
                    .capturedCrops()
                    .add(sample));
            onView(withId(R.id.gallery_open_button)).perform(click());
            onView(withId(R.id.gallery_sheet_title)).check(matches(withText(
                    R.string.verification_title
            )));
            onView(withId(R.id.verification_scroll)).check(matches(isDisplayed()));
            onView(withId(R.id.verification_model_text)).check(matches(withText("WI1234A")));
            onView(withId(R.id.verification_accept)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.crop_timing_pipeline)).check(matches(withText(String.format("%.1f", 40.0))));
            onView(withId(R.id.verification_characters)).check(matches(withText(
                    containsString("W 97%")
            )));
            onView(withId(R.id.verification_accept)).perform(scrollTo(), click());
            onView(withId(R.id.verification_correct)).perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withId(R.id.verification_reject)).perform(scrollTo())
                    .check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                assertTrue(sample.verificationStatus
                        == CapturedPlateItem.VerificationStatus.ACCEPTED);
                assertTrue(sample.eligibleForTextMetrics());
            });
            onView(withId(R.id.issue_rectification)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertTrue(sample.verificationIssues.contains(
                        com.example.alpr_v1.capture.VerificationIssue.RECTIFICATION
                ));
                assertTrue(sample.needsDesktopReview);
            });
        }
    }

    @Test
    public void cropDrawsCharacterBoxAndConfidenceAboveIt() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_ALPR_v1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PlateCropView view = new PlateCropView(themedContext, null);
            Bitmap plate = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888);
            plate.eraseColor(Color.BLACK);
            Bitmap rendered = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888);
            view.layout(0, 0, rendered.getWidth(), rendered.getHeight());
            view.setPlate(plate, java.util.Collections.singletonList(
                    new PlateCharacter("W", 0.97, 0.2f, 0f, 0.4f, 0.82f)
            ));
            view.draw(new Canvas(rendered));

            boolean orangeBoxPixel = false;
            boolean greenConfidencePixel = false;
            int firstBoxY = rendered.getHeight();
            int lastConfidenceY = -1;
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    int color = rendered.getPixel(x, y);
                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);
                    orangeBoxPixel |= red > 180 && green > 70 && green < 210 && blue < 100;
                    greenConfidencePixel |= green > 150 && red < 160 && blue > 80;
                    if (red > 180 && green > 70 && green < 210 && blue < 100) {
                        firstBoxY = Math.min(firstBoxY, y);
                    }
                    if (green > 150 && red < 160 && blue > 80) {
                        lastConfidenceY = Math.max(lastConfidenceY, y);
                    }
                }
            }
            assertTrue(orangeBoxPixel);
            assertTrue(greenConfidencePixel);
            assertTrue("Confidence must stay above a box touching the crop's top edge",
                    lastConfidenceY < firstBoxY);
            plate.recycle();
            rendered.recycle();
        });
    }

    private static CapturedPlateItem crop() {
        return new CapturedPlateItem(
                "visual-crop",
                "visual-session",
                1L,
                Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888),
                "WI1234A",
                0.92,
                0.91,
                true,
                characters(),
                System.currentTimeMillis(),
                android.os.SystemClock.elapsedRealtimeNanos(),
                0.78f,
                timing()
        );
    }

    private static List<PlateCharacter> characters() {
        String labels = "WI1234A";
        List<PlateCharacter> characters = new ArrayList<>();
        for (int index = 0; index < labels.length(); index++) {
            float left = 0.04f + index * 0.135f;
            characters.add(new PlateCharacter(
                    String.valueOf(labels.charAt(index)),
                    0.97 - index * 0.01,
                    left,
                    0.22f,
                    left + 0.10f,
                    0.82f
            ));
        }
        return characters;
    }

    private static CropInferenceTiming timing() {
        return new CropInferenceTiming(
                1L,
                1_000_000L,
                2_000_000L,
                2_000_000L,
                3_000_000L,
                4_000_000L,
                5_000_000L,
                6_000_000L,
                7_000_000L,
                8_000_000L,
                9_000_000L,
                40_000_000L
        );
    }
}
