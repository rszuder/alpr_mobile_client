package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.capture.CaptureGalleryViewModel;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.google.android.material.button.MaterialButton;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExperimentTimerResponsivenessInstrumentedTest {
    @Test public void realTimerAndStopRemainResponsiveDuringSlowInferenceAndCropPreparation() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("alpr_ui", Context.MODE_PRIVATE);
        boolean exp = prefs.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED, false);
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED, false).commit();
        CountDownLatch pipelineHeld = new CountDownLatch(1), cropHeld = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        ScheduledExecutorService delay = Executors.newSingleThreadScheduledExecutor();
        AtomicReference<MainActivity> active = new AtomicReference<>();
        AtomicReference<String> initialLabel = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            try {
                scenario.onActivity(activity -> {
                    active.set(activity);
                    AlprPipeline pipeline = (AlprPipeline) get(activity, "pipeline");
                    worker.execute(() -> { synchronized (pipeline) {
                        pipelineHeld.countDown(); await(release);
                    }});
                    CaptureGalleryViewModel gallery = (CaptureGalleryViewModel) get(activity, "captureGalleryState");
                    Bitmap bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
                    assertTrue(gallery.prepareCrop(() -> {
                        cropHeld.countDown(); await(release);
                        return new CapturedPlateItem("queued", "synthetic", 1L, bitmap, "", 0, 0, false,
                                Collections.emptyList(), 0L, 0L, 0f, null);
                    }, () -> { if (!bitmap.isRecycled()) bitmap.recycle(); }, 4));
                    ExperimentSession session = (ExperimentSession) get(activity, "experimentSession");
                    ExperimentSession.Prepared ready = session.prepare("quality", "R0", TimerConfig.of(true, 5),
                            ThermalConfig.disabled(), ExperimentIdentity.defaults(), null);
                    session.startPrepared(ready, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos());
                    invoke(activity, "scheduleExperimentTimer", new Class<?>[]{TimerConfig.class}, ready.timer);
                    initialLabel.set(((MaterialButton) activity.findViewById(R.id.experiment_timer_button)).getText().toString());
                });
                assertTrue(pipelineHeld.await(2, TimeUnit.SECONDS));
                assertTrue(cropHeld.await(2, TimeUnit.SECONDS));
                delay.schedule(() -> { }, 1200L, TimeUnit.MILLISECONDS).get(2, TimeUnit.SECONDS);
                onUi(() -> {
                    MaterialButton timer = active.get().findViewById(R.id.experiment_timer_button);
                    assertNotEquals(initialLabel.get(), timer.getText().toString());
                    invoke(active.get(), "invalidateUiForLostPreviewTracking", new Class<?>[0]);
                    invoke(active.get(), "stopAnalysis", new Class<?>[0]);
                    assertFalse(((ExperimentSession) get(active.get(), "experimentSession")).isRunning());
                }).get(2, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        } finally {
            release.countDown(); worker.shutdownNow(); delay.shutdownNow();
            prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED, exp).commit();
        }
    }

    private static FutureTask<Void> onUi(Runnable run) {
        FutureTask<Void> result = new FutureTask<>(run, null);
        new Handler(Looper.getMainLooper()).post(result);
        return result;
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    private static Object get(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
        catch (Exception error) { throw new AssertionError(error); }
    }
}
