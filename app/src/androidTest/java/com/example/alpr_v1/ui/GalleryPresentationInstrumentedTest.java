package com.example.alpr_v1.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ContextThemeWrapper;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.capture.CapturedPlateItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

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
                            1L, 2L, 3L, 4L, 4L, "KR12345", 0.93, 0.90,
                            1L, source, true, 4, 0.8f, "normal"
                    ));
            onView(withId(R.id.gallery_open_button)).perform(click());
            onView(withId(R.id.gallery_sheet_title)).check(matches(withText(
                    R.string.history_title
            )));
            onView(withId(R.id.gallery_sheet_list)).check(matches(isDisplayed()));
            onView(withId(R.id.history_number)).check(matches(withText("KR12345")));
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
            onView(withId(R.id.verification_correct)).check(matches(isDisplayed()));
            onView(withId(R.id.verification_reject)).check(matches(isDisplayed()));
            onView(withId(R.id.verification_accept)).perform(click());
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
                Collections.emptyList(),
                System.currentTimeMillis(),
                android.os.SystemClock.elapsedRealtimeNanos(),
                0.78f,
                null
        );
    }
}
