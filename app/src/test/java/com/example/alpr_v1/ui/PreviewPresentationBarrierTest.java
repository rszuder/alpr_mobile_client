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

    @Test
    public void staleBitmapCannotCommitAcrossNewerBarrierActivation() {
        PreviewPresentationBarrier barrier = new PreviewPresentationBarrier();
        long staleBitmapGeneration = barrier.capture();

        barrier.activate();

        assertFalse(barrier.matchesGeneration(staleBitmapGeneration));
        assertTrue(barrier.commitRebase(staleBitmapGeneration) < 0L);
        assertTrue(barrier.active());
    }

    @Test
    public void bitmapCapturedInsideActiveBarrierCommitsNewReference() {
        PreviewPresentationBarrier barrier = new PreviewPresentationBarrier();
        barrier.activate();
        long recoveryBitmapGeneration = barrier.capture();

        long committedGeneration = barrier.commitRebase(
                recoveryBitmapGeneration
        );

        assertTrue(committedGeneration > recoveryBitmapGeneration);
        assertFalse(barrier.active());
        assertTrue(barrier.permits(barrier.capture()));
    }
}
