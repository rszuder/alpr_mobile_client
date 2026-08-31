package com.example.alpr_v1.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PreviewPresentationBarrierTest {
    @Test
    public void activationRejectsCallbackCapturedBeforeAbruptSceneChange() {
        PreviewPresentationBarrier barrier = new PreviewPresentationBarrier();
        long oldCallback = barrier.capture();

        barrier.activate();

        assertFalse(barrier.permits(oldCallback));
    }

    @Test
    public void activeBarrierRejectsCallbacksStartedDuringRecovery() {
        PreviewPresentationBarrier barrier = new PreviewPresentationBarrier();
        barrier.activate();
        long recoveryCallback = barrier.capture();

        assertFalse(barrier.permits(recoveryCallback));
    }

    @Test
    public void releaseAllowsOnlyCallbacksCapturedAfterFreshRebase() {
        PreviewPresentationBarrier barrier = new PreviewPresentationBarrier();
        barrier.activate();
        long recoveryCallback = barrier.capture();
        barrier.release();

        assertFalse(barrier.permits(recoveryCallback));
        assertTrue(barrier.permits(barrier.capture()));
    }
}
