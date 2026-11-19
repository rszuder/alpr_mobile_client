package com.example.alpr_v1.pipeline;

import android.app.Application;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class DynamicPreviewConcurrencyTest {
    @Test public void oldPreviewEvidenceCannotResetTheReplacementScene() {
        Application app=RuntimeEnvironment.getApplication();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            SourceFrameStamp old=pipeline.currentContinuityStamp(1_000_000_000L).sourceFrameStamp();
            SceneTransitionCoordinator coordinator=org.robolectric.util.ReflectionHelpers.getField(pipeline,"sceneTransitionCoordinator");
            coordinator.requestStructuralReset("replacement scene",1_100_000_000L);
            long revision=coordinator.snapshot().decisionRevision;
            assertNull(pipeline.onWorkerPreviewSceneEvidence(old,true,1,1,1,1,1,true,true,null));
            assertNull(pipeline.requestAbruptSceneReset(old,1,1));
            assertEquals(revision,coordinator.snapshot().decisionRevision);
        } finally {pipeline.close();}
    }
    @Test public void aFiveSecondInferenceMonitorDoesNotBlockOrdinaryPreviewEvidence()throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
        SourceFrameStamp source=pipeline.currentContinuityStamp(1_000_000_000L).sourceFrameStamp();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread inference=new Thread(()->{synchronized(pipeline){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}});
        ExecutorService preview=Executors.newSingleThreadExecutor();
        try {
            inference.start();assertTrue(entered.await(1,TimeUnit.SECONDS));
            Future<SceneTransitionDecision> decision=preview.submit(()->pipeline.onWorkerPreviewSceneEvidence(
                    source,false,0,0,0,0,0,false,false,null));
            assertNotNull(decision.get(800,TimeUnit.MILLISECONDS));
            assertEquals(1,release.getCount());
        } finally {release.countDown();inference.join(1000);preview.shutdownNow();pipeline.close();}
    }
    @Test public void aDirectLumaCutPublishesItsBoundaryWithoutWaitingForInference()throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
        SourceFrameStamp source=pipeline.currentContinuityStamp(1_000_000_000L).sourceFrameStamp();
        pipeline.onWorkerPreviewSceneEvidence(source,false,0,0,0,0,0,false,false,null);
        long generation=pipeline.sceneContinuitySnapshot().sceneGeneration;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread inference=new Thread(()->{synchronized(pipeline){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}});
        ExecutorService preview=Executors.newSingleThreadExecutor();
        try {
            inference.start();assertTrue(entered.await(1,TimeUnit.SECONDS));
            Future<SceneTransitionDecision> result=preview.submit(()->pipeline.requestAbruptSceneReset(source,.5f,32f));
            SceneTransitionDecision decision=result.get(800,TimeUnit.MILLISECONDS);
            assertEquals(SceneTransitionAction.HARD_RESET,decision.action);
            assertEquals(generation+1,pipeline.sceneContinuitySnapshot().sceneGeneration);
            assertFalse(pipeline.isCurrentContinuityStamp(source.continuityStamp()));
            assertEquals(1,release.getCount());
        } finally {release.countDown();inference.join(1000);preview.shutdownNow();pipeline.close();}
    }
}
