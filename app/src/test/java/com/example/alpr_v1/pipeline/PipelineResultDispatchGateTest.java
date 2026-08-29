package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SceneContinuityProfile;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PipelineResultDispatchGateTest {
    @Test
    public void delayedFinalResultCannotReachUiOrCropAfterVisualEpochChanges()
            throws Exception {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityProfile.INITIAL
        );
        ContinuityStamp resultStamp = coordinator.stamp(8_000_000_000L);
        PipelineResult result = new PipelineResult(
                "recognized",
                "test",
                Collections.emptyList(),
                Collections.emptyList(),
                1280,
                720,
                Collections.emptyList(),
                false,
                resultStamp
        );
        CountDownLatch dispatchQueued = new CountDownLatch(1);
        CountDownLatch allowDispatch = new CountDownLatch(1);
        AtomicBoolean uiPresented = new AtomicBoolean(false);
        AtomicBoolean cropCollected = new AtomicBoolean(false);

        Thread delayedDispatch = new Thread(() -> {
            dispatchQueued.countDown();
            try {
                if (!allowDispatch.await(5L, TimeUnit.SECONDS)) return;
                ContinuityStamp current = coordinator.stamp(
                        resultStamp.sourceTimestampNanos
                );
                if (PipelineResultDispatchGate.isCurrent(current, result)) {
                    uiPresented.set(true);
                    cropCollected.set(true);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                result.close();
            }
        }, "delayed-final-result-dispatch-test");
        delayedDispatch.start();

        assertTrue(dispatchQueued.await(2L, TimeUnit.SECONDS));
        coordinator.requestSoftReacquire(
                "dispatch_race_test",
                1_000_000_000L
        );
        allowDispatch.countDown();
        delayedDispatch.join(5_000L);

        assertFalse(delayedDispatch.isAlive());
        assertFalse(uiPresented.get());
        assertFalse(cropCollected.get());
        assertTrue(result.isClosed());
        assertEquals(1, result.effectiveCloseCountForTest());

        result.close();
        assertEquals(1, result.effectiveCloseCountForTest());
    }
}
