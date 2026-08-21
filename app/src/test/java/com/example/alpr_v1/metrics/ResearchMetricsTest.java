package com.example.alpr_v1.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ResearchMetricsTest {
    @Test
    public void levenshteinCountsInsertDeleteAndSubstitute() {
        assertEquals(0, MetricsCollector.levenshtein("KR12345", "KR12345"));
        assertEquals(1, MetricsCollector.levenshtein("KR12345", "KR1234"));
        assertEquals(1, MetricsCollector.levenshtein("KR12345", "KR1234S"));
        assertEquals(2, MetricsCollector.levenshtein("KR12345", "KRA234S"));
    }

    @Test
    public void texEscapesUserAndModelTextWithoutEscapingItsOwnCommands() {
        String escaped = ResearchArchive.tex("MT_1 & 95% {FP32} \\ test");

        assertEquals(
                "MT\\_1 \\& 95\\% \\{FP32\\} \\textbackslash{} test",
                escaped
        );
        assertTrue(escaped.contains("\\textbackslash{}"));
        assertFalse(escaped.contains("\\textbackslash\\{"));
    }
}
