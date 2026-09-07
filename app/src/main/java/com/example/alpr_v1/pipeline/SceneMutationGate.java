package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.*;
import java.util.function.Supplier;

/** Makes the generation check atomic with a short domain/consensus mutation. */
final class SceneMutationGate {
    private final SceneTransitionCoordinator coordinator;
    SceneMutationGate(SceneTransitionCoordinator coordinator) { this.coordinator = coordinator; }
    <T> T run(ContinuityStamp stamp, Supplier<T> mutation) {
        synchronized (coordinator) {
            if (new ContinuityGenerationGate().evaluate(coordinator.stamp(stamp.sourceFrameStamp()), stamp)
                    != ContinuityResultDisposition.ACCEPT_ALL) throw new MobileAlprEngine.ProcessingCancelledException();
            return mutation.get();
        }
    }
}
