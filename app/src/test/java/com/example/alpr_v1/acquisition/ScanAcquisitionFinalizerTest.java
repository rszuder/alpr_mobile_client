package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScanAcquisitionFinalizerTest {
    @Test
    public void dedupScopeIsResetWithEachScanRun() {
        ScanAcquisitionFinalizer finalizer = new ScanAcquisitionFinalizer();
        AcquisitionRecord first = finalizer.finalizeAcquisition(
                1L, 10L, 100L, 1000L, "KR 12345",
                0.9, 3, 20L, 10L, 100L, "crop-1"
        );
        AcquisitionRecord duplicate = finalizer.finalizeAcquisition(
                1L, 11L, 101L, 1001L, "kr-12345",
                0.8, 2, 120L, 110L, 200L, "crop-2"
        );

        assertTrue(first.uniqueSaved);
        assertFalse(duplicate.uniqueSaved);
        assertEquals(first.recordId, duplicate.duplicateOfRecordId);
        assertEquals(1, finalizer.uniqueSavedCount());

        finalizer.reset();
        AcquisitionRecord nextRun = finalizer.finalizeAcquisition(
                2L, 20L, 200L, 2000L, "KR12345",
                0.95, 4, 220L, 210L, 300L, "crop-3"
        );

        assertTrue(nextRun.uniqueSaved);
        assertEquals(1, finalizer.finalizedCount());
        assertEquals(1, finalizer.uniqueSavedCount());
    }
}
