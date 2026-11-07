package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Collections;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticWatcherControlConcurrencyTest {
    @Test public void uiCommandsNeverWaitForTheOpticalFlowMonitor() throws Exception {
        StaticSceneWatcher watcher = new StaticSceneWatcher();
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<?> observation = workers.submit(() -> {
            synchronized (watcher) {
                held.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        });
        try {
            assertTrue(held.await(2, TimeUnit.SECONDS));
            Future<?> control = workers.submit(() -> {
                watcher.arm(new StaticSceneWatchRegions(null, Collections.singletonList(
                        new NormalizedBounds(.1f,.1f,.3f,.2f)),0f));
                watcher.prepareForZoom();
                watcher.reset();
            });
            // A deliberately held observation lock remains held until this succeeds.
            control.get(2, TimeUnit.SECONDS);
            assertFalse(observation.isDone());
        } finally {
            release.countDown(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
    @Test public void queuedResetDropsOldReferenceAndFollowingArmIsNotLost() {
        StaticSceneWatcher watcher = new StaticSceneWatcher();
        byte[] old = StaticSceneFeatureWatcherTest.sparseCorners(0,0);
        watcher.observe(old,200,200,0L,false);
        watcher.reset();
        watcher.arm(new StaticSceneWatchRegions(null,Collections.singletonList(
                new NormalizedBounds(.02f,.02f,.1f,.1f)),0f));
        byte[] current = new byte[40000]; java.util.Arrays.fill(current,(byte)100);
        assertEquals("reference_armed",watcher.observe(current,200,200,100_000_000L,false).reason);
        for (int i=2;i<=8;i++) assertFalse(watcher.observe(current,200,200,i*100_000_000L,false).changed);
    }
}
