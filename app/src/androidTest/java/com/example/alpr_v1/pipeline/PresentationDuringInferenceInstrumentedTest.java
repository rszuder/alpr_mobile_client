package com.example.alpr_v1.pipeline;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PresentationDuringInferenceInstrumentedTest {
    @Test public void targetPresentationDoesNotWaitForSlowInferenceAndStillRejectsOldScene() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        AlprPipeline pipeline=new AlprPipeline(context,new ModelRegistry(context),new MetricsCollector(),new AutoTuneManager(context));
        ContinuityStamp stamp=pipeline.currentContinuityStamp(1L);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        workers.submit(() -> { synchronized(pipeline) {
            held.countDown();
            try { release.await(); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
        }});
        try {
            assertTrue(held.await(2,TimeUnit.SECONDS));
            assertTrue(workers.submit(() -> pipeline.setTargetSnapshotIfCurrent(TargetSnapshot.searching(),stamp))
                    .get(2,TimeUnit.SECONDS));
        } finally {
            release.countDown(); workers.shutdownNow(); workers.awaitTermination(2,TimeUnit.SECONDS);
        }
        pipeline.resetTracking();
        assertFalse(pipeline.setTargetSnapshotIfCurrent(TargetSnapshot.searching(),stamp));
        pipeline.close();
    }
}
