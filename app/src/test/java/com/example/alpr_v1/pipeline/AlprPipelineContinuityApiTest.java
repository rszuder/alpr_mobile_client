package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.alpr_v1.continuity.SceneContinuitySnapshot;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

public final class AlprPipelineContinuityApiTest {
    @Test
    public void pipelineOwnsOneCoordinatorAndSplitGenerationCounters() throws Exception {
        assertEquals(
                SceneTransitionCoordinator.class,
                field("sceneTransitionCoordinator").getType()
        );
        assertEquals(AtomicLong.class, field("sceneGeneration").getType());
        assertEquals(AtomicLong.class, field("visualEpoch").getType());
        assertEquals(AtomicLong.class, field("hardResetRevision").getType());
        assertEquals(AtomicLong.class, field("visualEpochRevision").getType());
    }

    @Test(expected = NoSuchFieldException.class)
    public void legacyCombinedResetRevisionIsRemoved() throws Exception {
        AlprPipeline.class.getDeclaredField("trackingResetRevision");
    }

    @Test
    public void runtimeExposesReadOnlyContinuitySnapshot() throws Exception {
        assertNotNull(AlprPipeline.class.getMethod("sceneContinuitySnapshot"));
        assertEquals(
                SceneContinuitySnapshot.class,
                AlprPipeline.class.getMethod("sceneContinuitySnapshot").getReturnType()
        );
    }

    private static Field field(String name) throws Exception {
        return AlprPipeline.class.getDeclaredField(name);
    }
}
