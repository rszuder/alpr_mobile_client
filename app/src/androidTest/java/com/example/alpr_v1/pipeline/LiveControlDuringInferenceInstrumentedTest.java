package com.example.alpr_v1.pipeline;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.tracking.PreviewPlateTracker;
import com.example.alpr_v1.ui.OverlayItem;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveControlDuringInferenceInstrumentedTest {
    @Test public void recoveryAndPreviewEvidenceDoNotWaitForInferenceMonitor() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AlprPipeline pipeline = new AlprPipeline(context, new ModelRegistry(context), new MetricsCollector(), new AutoTuneManager(context));
        ContinuityStamp old = pipeline.currentContinuityStamp(1L);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        workers.execute(() -> { synchronized (pipeline) { held.countDown(); await(release); } });
        try {
            assertTrue(held.await(2, TimeUnit.SECONDS));
            workers.submit(pipeline::requestImmediateTargetRecovery).get(1, TimeUnit.SECONDS);
            assertNotNull(workers.submit(() -> pipeline.onPreviewSceneEvidenceFromUi(
                    pipeline.currentContinuityStamp(2L).sourceFrameStamp(), false, 0f, 0f, 0f, 0f, 0f, false, false))
                    .get(1, TimeUnit.SECONDS));
        } finally { release.countDown(); workers.shutdownNow(); workers.awaitTermination(2, TimeUnit.SECONDS); }
        pipeline.resetTracking();
        assertFalse(pipeline.isCurrentContinuityStamp(old));
        pipeline.close();
    }

    @Test public void trackerAnchorAndResetDoNotWaitForOpticalFlowAndResetCannotResurrectAnchor() throws Exception {
        PreviewPlateTracker tracker = new PreviewPlateTracker();
        OverlayItem plate = new OverlayItem(OverlayItem.Kind.PLATE, new RectF(.2f, .2f, .8f, .5f),
                Arrays.asList(new PointF(.2f, .2f), new PointF(.8f, .2f), new PointF(.8f, .5f), new PointF(.2f, .5f)),
                "synthetic", 1L, false);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        workers.execute(() -> { synchronized (tracker) { held.countDown(); await(release); } });
        try {
            assertTrue(held.await(2, TimeUnit.SECONDS));
            assertTrue(workers.submit(() -> tracker.anchor(Collections.singletonList(plate), 1920, 1080)).get(1, TimeUnit.SECONDS));
            assertEquals(1920, tracker.sourceWidth());
            workers.submit(tracker::reset).get(1, TimeUnit.SECONDS);
        } finally { release.countDown(); workers.shutdownNow(); workers.awaitTermination(2, TimeUnit.SECONDS); }
        assertEquals(0, tracker.sourceWidth());
        assertNull(tracker.updateLuma(new byte[64 * 32], 64, 32));
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
