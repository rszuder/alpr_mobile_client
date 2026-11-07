package com.example.alpr_v1.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnalysisStartGateTest {
    @Test public void repeatedStartCannotCreateConcurrentPreparations() {
        AnalysisStartGate gate = new AnalysisStartGate();
        long first = gate.begin();
        assertTrue(gate.preparing());
        assertEquals(-1L, gate.begin());
        assertTrue(gate.complete(first));
        assertFalse(gate.complete(first));
        assertFalse(gate.preparing());
    }
    @Test public void cancelledSlowCompletionCannotStartOrCancelNewRequest() {
        AnalysisStartGate gate = new AnalysisStartGate();
        long old = gate.begin();
        gate.cancel();
        long current = gate.begin();
        assertFalse(gate.isCurrent(old));
        assertFalse(gate.complete(old));
        assertTrue(gate.isCurrent(current));
        assertTrue(gate.complete(current));
    }
    @Test public void leavingScreenInvalidatesCompletionBeforeCameraAndClockStart() {
        AnalysisStartGate gate = new AnalysisStartGate();
        long request = gate.begin();
        gate.cancel();
        assertFalse(gate.complete(request));
        assertFalse(gate.preparing());
    }
}
