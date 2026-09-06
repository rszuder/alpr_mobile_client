package com.example.alpr_v1.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class RuntimeContractFailureGateTest {
    @Test
    public void reportsFailureOnceAndBlocksUntilModelInvalidation() {
        RuntimeContractFailureGate gate = new RuntimeContractFailureGate();

        assertTrue(gate.record("first"));
        assertFalse(gate.record("second"));
        assertTrue(gate.isBlocked());
        assertEquals("first", gate.message());

        gate.clear();

        assertFalse(gate.isBlocked());
        assertNull(gate.message());
        assertTrue(gate.record("after reload"));
    }
}
