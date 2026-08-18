package com.example.alpr_v1.metrics;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;

public class ReportArchiveTest {
    @Test
    public void containsJsonCsvAndReadme() throws Exception {
        byte[] archive = ReportArchive.create("{\"ok\":true}", "frame_id\n1\n");
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList("report.json", "traces.csv", "README.txt")), names);
    }
}
