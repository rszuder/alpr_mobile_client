package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.ui.OverlayItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class IntermediateMtCallbackInstrumentedTest {
    @Test
    public void delayedMtFromOldVisualEpochCannotPublishOrReanchorTarget()
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AlprPipeline pipeline = new AlprPipeline(
                context,
                new ModelRegistry(context),
                new MetricsCollector(),
                new AutoTuneManager(context)
        );
        try {
            ContinuityStamp mtStartStamp = ContinuityStamp.initial(
                    System.nanoTime()
            );
            assertTrue(pipeline.isCurrentContinuityStamp(mtStartStamp));

            TargetStateMachine staleMtState = new TargetStateMachine();
            OverlayItem stalePlate = new OverlayItem(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.40f, 0.60f, 0.52f),
                    Collections.emptyList(),
                    "STALE",
                    701L,
                    false
            );
            staleMtState.onMtAnchor(Collections.singletonList(stalePlate));
            staleMtState.onMtAnchor(Collections.singletonList(stalePlate));
            TargetSnapshot staleLockedTarget = staleMtState.onMtAnchor(
                    Collections.singletonList(stalePlate)
            );
            assertTrue(staleLockedTarget.locked());

            CountDownLatch callbackStarted = new CountDownLatch(1);
            CountDownLatch allowCallbackToFinish = new CountDownLatch(1);
            AtomicBoolean callbackAccepted = new AtomicBoolean(false);
            AtomicBoolean staleOverlayPublished = new AtomicBoolean(false);

            Thread delayedCallback = new Thread(() -> {
                callbackStarted.countDown();
                try {
                    if (!allowCallbackToFinish.await(5L, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                boolean accepted = pipeline.setTargetSnapshotIfCurrent(
                        staleLockedTarget,
                        mtStartStamp
                );
                callbackAccepted.set(accepted);
                if (accepted) staleOverlayPublished.set(true);
            }, "delayed-mt-callback-test");
            delayedCallback.start();

            assertTrue(callbackStarted.await(2L, TimeUnit.SECONDS));
            pipeline.requestImmediateTargetRecovery();
            assertFalse(pipeline.isCurrentContinuityStamp(mtStartStamp));

            allowCallbackToFinish.countDown();
            delayedCallback.join(5_000L);

            assertFalse(delayedCallback.isAlive());
            assertFalse(callbackAccepted.get());
            assertFalse(staleOverlayPublished.get());
        } finally {
            pipeline.close();
        }
    }
}
