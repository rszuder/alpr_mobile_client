package com.example.alpr_v1.metrics;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReportArchiveTest {
    @Test
    public void containsJsonCsvAndReadme() throws Exception {
        byte[] archive = ReportArchive.create("{\"ok\":true}", "frame_id\n1\n");
        Set<String> names = new HashSet<>();
        String readme = "";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if ("README.txt".equals(entry.getName())) {
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read > 0) output.write(buffer, 0, read);
                    }
                    readme = output.toString(java.nio.charset.StandardCharsets.UTF_8.name());
                }
            }
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList("report.json", "traces.csv", "README.txt")), names);
        assertTrue(readme.contains(MetricsCollector.REPORT_SCHEMA));
    }

    @Test
    public void includesPersistentApplicationLogWhenProvided() throws Exception {
        byte[] archive = ReportArchive.create("{}", "frame_id\n", "2026-08-20 INFO/Main start\n");
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
        }
        assertTrue(names.contains("application.log"));
    }
}
