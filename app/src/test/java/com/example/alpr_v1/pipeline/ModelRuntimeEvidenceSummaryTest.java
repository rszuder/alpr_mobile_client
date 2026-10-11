package com.example.alpr_v1.pipeline;
import org.junit.Test;
import static org.junit.Assert.*;
public class ModelRuntimeEvidenceSummaryTest {
    @Test public void legacyLabelNeverClaimsIntegerExecution() {
        ModelRuntimeSummary value=new ModelRuntimeSummary("MT","model","int8","int8","LiteRT/CPU");
        assertTrue(value.compactLabel().contains("deklaracja int8"));
        assertTrue(value.description().contains("UNVERIFIED"));
    }
    @Test public void runtimeStatusIsSeparateFromDeclaredPrecision() {
        ModelRuntimeSummary value=new ModelRuntimeSummary("MZ","model","int8","int8","LiteRT/GPU","INT8_NOT_PROVEN");
        assertEquals("int8",value.precision);assertTrue(value.description().contains("INT8_NOT_PROVEN"));
        assertFalse(value.description().contains("INT8_PROVEN"));
    }
}
