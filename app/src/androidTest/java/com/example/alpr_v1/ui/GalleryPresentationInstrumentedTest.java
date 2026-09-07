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
    public void normalModeShowsLogicalRecognitionHistory() {
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
            onView(withId(R.id.history_detail_timing)).check(matches(withText(
                    containsString("MT — inferencja tablicy")
            )));
            onView(withId(R.id.history_detail_characters)).check(matches(withText(
                    containsString("W 97%")
            )));
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
            onView(withId(R.id.verification_accept)).check(matches(isDisplayed()));
            onView(withId(R.id.verification_timing)).check(matches(withText(
                    containsString("Pipeline do wyniku")
            )));
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
            view.setPlate(plate, characters());
            view.draw(new Canvas(rendered));

            boolean orangeBoxPixel = false;
            boolean greenConfidencePixel = false;
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    int color = rendered.getPixel(x, y);
                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);
                    orangeBoxPixel |= red > 180 && green > 70 && green < 210 && blue < 100;
                    greenConfidencePixel |= green > 150 && red < 160 && blue > 80;
                }
            }
            assertTrue(orangeBoxPixel);
            assertTrue(greenConfidencePixel);
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
