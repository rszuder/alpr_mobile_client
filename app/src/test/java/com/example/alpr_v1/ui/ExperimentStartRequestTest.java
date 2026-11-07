package com.example.alpr_v1.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExperimentStartRequestTest {
    @Test public void pausedPreviewCanBecomeReadyAndStartExactlyOnceAfterOneRequest() {
        ExperimentStartRequest start = new ExperimentStartRequest();
        long first = start.request();
        assertEquals(first, start.request());
        assertFalse(start.consumeIfReady(false)); // Camera/models are still being prepared.
        assertTrue(start.pending());
        assertTrue(start.consumeIfReady(true));
        assertFalse(start.pending());
        assertFalse(start.consumeIfReady(true)); // Duplicate camera/model callback.
    }
    @Test public void backgroundPreparationWithoutOperatorRequestNeverStartsMeasurement() {
        ExperimentStartRequest start = new ExperimentStartRequest();
        assertFalse(start.consumeIfReady(true));
    }
    @Test public void permissionDialogPausePreservesIntentButDoesNotStartBeforePermissionResult() {
        ExperimentStartRequest start = new ExperimentStartRequest();
        start.request(); start.permissionStarted(); start.backgrounded();
        assertTrue(start.pending());
        assertFalse(start.consumeIfReady(true));
        assertTrue(start.permissionResult(true));
        assertTrue(start.consumeIfReady(true));
    }
    @Test public void deniedPermissionCancelsIntentAndAllowsAnExplicitRetry() {
        ExperimentStartRequest start = new ExperimentStartRequest();
        start.request(); start.permissionStarted();
        assertFalse(start.permissionResult(false));
        assertFalse(start.pending());
        start.request(); start.permissionStarted();
        assertTrue(start.permissionResult(true));
        assertTrue(start.consumeIfReady(true));
    }
    @Test public void latePermissionResultCannotStartCancelledOrNewerRequest() {
        ExperimentStartRequest start = new ExperimentStartRequest();
        start.request(); start.permissionStarted(); start.cancel();
        start.request();
        assertFalse(start.permissionResult(true));
        assertTrue(start.pending());
        assertTrue(start.consumeIfReady(true));
    }
    @Test public void cancellingOrLeavingScreenRejectsLateReadinessAndFailureCanBeRetried() {
        ExperimentStartRequest start = new ExperimentStartRequest();
        start.request(); start.cancel();
        assertFalse(start.consumeIfReady(true));
        start.request(); start.backgrounded();
        assertFalse(start.consumeIfReady(true));
        start.request();
        assertTrue(start.consumeIfReady(true));
    }
}
