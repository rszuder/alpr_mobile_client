package com.example.alpr_v1.tracking;

import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewTrackerControlConcurrencyTest {
    @Test public void stopResetDoesNotWaitForOpticalFlowMonitor() throws Exception {
        PreviewPlateTracker tracker = new PreviewPlateTracker();
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        workers.execute(() -> { synchronized (tracker) {
            held.countDown();
            try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }});
        try {
            assertTrue(held.await(2, TimeUnit.SECONDS));
            workers.submit(tracker::reset).get(1, TimeUnit.SECONDS);
            assertEquals(0, tracker.sourceWidth());
            assertEquals(0, tracker.sourceHeight());
        } finally {
            release.countDown(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
