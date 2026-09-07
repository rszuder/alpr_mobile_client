package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneMutationGateTest {
    @Test public void s11LateOldSceneCannotMutateDomainOrConsensus() {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(SceneHandlingMode.STRICT_SCENE_BOUNDARY, SceneContinuityProfile.INITIAL);
        SceneMutationGate gate = new SceneMutationGate(coordinator);
        AtomicInteger writes = new AtomicInteger();
        ContinuityStamp first = coordinator.stamp(1L);
        gate.run(first, writes::incrementAndGet);
        coordinator.requestStructuralReset("static_cut", 2L);
        try { gate.run(first, writes::incrementAndGet); fail("old result must be rejected"); }
        catch (MobileAlprEngine.ProcessingCancelledException expected) { }
        assertEquals(1,writes.get());
        gate.run(coordinator.stamp(3L),writes::incrementAndGet);
        assertEquals(2,writes.get());
    }
}
