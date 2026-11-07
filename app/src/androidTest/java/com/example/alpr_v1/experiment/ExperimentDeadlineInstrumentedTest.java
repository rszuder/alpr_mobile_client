package com.example.alpr_v1.experiment;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExperimentDeadlineInstrumentedTest {
    @Test public void deadlineRejectsAdmissionWhileUiAndStopCallbackAreDelayedAndRetainsInflightEvidence() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File root = new File(context.getCacheDir(), "deadline-store-" + UUID.randomUUID());
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        ExperimentSession domain = new ExperimentSession();
        ExperimentSession.Prepared prepared = domain.prepare("quality", "R0", TimerConfig.of(true, 5),
                ThermalConfig.disabled(), ExperimentIdentity.defaults(), null);
        ResearchSessionStore store = ResearchSessionStore.prepare(root, prepared, 32, 16, 0L,
                ResearchSessionStore::encodeJpeg, clock::get);
        try {
            store.startAt(10_000L, clock.get());
            ResearchAttemptBatch inFlight = new ResearchAttemptBatch(store, new ContinuityStamp(1L, 0L, 0L, 1L), "R0", 1f);
            AcquisitionAttemptRecord call = inFlight.beginMt(1L, 1L, 0, 0, 32, 16, 32, 16);
            call.put("mt_executed", true); call.put("mt_invocation_id", "in-flight-mt");
            call.put("mt_status", "NO_DETECTION");
            Bitmap image = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
            call.copyEvidence(image); image.recycle();
            assertTrue(store.accepting());
            clock.set(6_000_000_000L); // The deadline, with no STOP callback executed.
            assertFalse(store.accepting());
            assertNull(store.beginAttempt(new ContinuityStamp(1L, 0L, 0L, 2L), "R0", 1f, 1L, 1L));
            inFlight.finish("", "");
            clock.set(8_000_000_000L);
            store.closeAdmission("manual", 17_000L, clock.get());
            File archive = store.finish(new ResearchSessionStore.Telemetry("{}", "frame_id\n", "thermal\n",
                    "flow\n", "", "test"), null);
            assertTrue(archive.isFile());
            JSONObject session = new JSONObject(ResearchSessionStore.read(new File(store.directory(), "session.json")));
            assertEquals("timer", session.getString("completion_reason"));
            assertEquals(6_000_000_000L, session.getLong("finished_elapsed_nanos"));
            assertEquals(2000.0, session.getDouble("timer_stop_lateness_ms"), 0.001);
            String attempts = ResearchSessionStore.read(new File(store.directory(), "samples/attempts.jsonl"));
            assertTrue(attempts.contains("in-flight-mt"));
            assertTrue(attempts.contains("session_stopped"));
        } finally {
            shutdownStore(store); deleteTestRoot(root);
        }
    }

    @Test public void retainedSessionClosesAtDeadlineWhileAndroidMainLooperIsBlocked() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File root = new File(context.getCacheDir(), "deadline-viewmodel-" + UUID.randomUUID());
        Files.createDirectories(root.toPath());
        Application app = new Application() {
            @Override public File getFilesDir() { return root; }
        };
        ResearchSessionViewModel retained = new ResearchSessionViewModel(app);
        ResearchSessionStore store = null;
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ScheduledExecutorService verifier = Executors.newSingleThreadScheduledExecutor();
        try {
            ExperimentSession.Prepared prepared = retained.experiment.prepare("quality", "R0", TimerConfig.of(true, 5),
                    ThermalConfig.disabled(), ExperimentIdentity.defaults(), null);
            long t0 = SystemClock.elapsedRealtimeNanos() - 4_500_000_000L;
            store = ResearchSessionStore.prepare(ResearchSessionStore.sessionsRoot(app), prepared, 32, 16, 0L,
                    ResearchSessionStore::encodeJpeg, () -> t0);
            store.startAt(System.currentTimeMillis() - 4500L, t0);
            java.lang.reflect.Field field = ResearchSessionViewModel.class.getDeclaredField("store");
            field.setAccessible(true); field.set(retained, store);
            retained.experiment.startPrepared(prepared, System.currentTimeMillis() - 4500L, t0);
            MetricsCollector metrics = new MetricsCollector();
            metrics.startMeasurementSession(System.currentTimeMillis() - 4500L, t0, System.nanoTime());
            new Handler(Looper.getMainLooper()).post(() -> {
                held.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(held.await(2, TimeUnit.SECONDS));
            retained.armTimer(metrics);
            verifier.schedule(() -> { }, 1L, TimeUnit.SECONDS).get(2L, TimeUnit.SECONDS);
            assertFalse(retained.experiment.isRunning());
            assertFalse(metrics.isMeasurementSessionActive());
            assertEquals(5_000L, retained.experiment.durationMillis());
            assertEquals(ResearchSessionStore.State.FINALIZING, store.state());
            assertNull(retained.timerExpired.getValue()); // UI notification is still waiting on the blocked Looper.
        } finally {
            release.countDown(); verifier.shutdownNow();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { });
            java.lang.reflect.Method clear = ResearchSessionViewModel.class.getDeclaredMethod("onCleared");
            clear.setAccessible(true); clear.invoke(retained);
            java.lang.reflect.Field finalizerField = ResearchSessionViewModel.class.getDeclaredField("finalizer");
            finalizerField.setAccessible(true);
            assertTrue(((ExecutorService) finalizerField.get(retained)).awaitTermination(3, TimeUnit.SECONDS));
            if (store != null) shutdownStore(store);
            deleteTestRoot(root);
        }
    }

    private static void shutdownStore(ResearchSessionStore store) throws Exception {
        java.lang.reflect.Field field = ResearchSessionStore.class.getDeclaredField("writer");
        field.setAccessible(true);
        ExecutorService writer = (ExecutorService) field.get(store);
        writer.shutdown(); assertTrue(writer.awaitTermination(3, TimeUnit.SECONDS));
    }

    private static void deleteTestRoot(File root) throws Exception {
        if (!root.isDirectory()) return;
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(path);
        }
    }
}
