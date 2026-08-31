package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

import org.junit.Test;

public final class PreviewMotionGenerationGateTest {
    @Test
    public void visualEpochChangeRequiresFreshReference() {
        PreviewMotionGenerationGate gate = new PreviewMotionGenerationGate();

        assertTrue(gate.enter(stamp(2L, 4L, 1L, 10L)));
        assertFalse(gate.enter(stamp(2L, 4L, 1L, 11L)));
        assertTrue(gate.enter(stamp(2L, 5L, 1L, 12L)));
    }

    @Test
    public void sceneAndCameraTransformChangesAlsoRequireFreshReference() {
        PreviewMotionGenerationGate gate = new PreviewMotionGenerationGate();
        gate.enter(stamp(2L, 4L, 1L, 10L));

        assertTrue(gate.enter(stamp(3L, 4L, 1L, 11L)));
        assertTrue(gate.enter(stamp(3L, 4L, 2L, 12L)));
    }

    @Test
    public void explicitResetInvalidatesThePreviousGeneration() {
        PreviewMotionGenerationGate gate = new PreviewMotionGenerationGate();
        ContinuityStamp stamp = stamp(2L, 4L, 1L, 10L);
        gate.enter(stamp);
        gate.reset();

        assertTrue(gate.enter(stamp));
    }

    private static ContinuityStamp stamp(
            long scene,
            long visual,
            long transform,
            long sequence
    ) {
        return new ContinuityStamp(
                scene,
                visual,
                transform,
                sequence,
                sequence,
                SourceTimestampDomain.CAMERAX_SENSOR
        );
    }
}
