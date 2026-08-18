package com.example.alpr_v1.metrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReportArchive {
    private ReportArchive() {}

    public static byte[] create(String json, String csv) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            write(zip, "report.json", json);
            write(zip, "traces.csv", csv);
            write(
                    zip,
                    "README.txt",
                    "Raport sesji mobilnego ALPR. report.json zawiera metadane urządzenia, modeli, "
                            + "autotuningu i agregaty, a traces.csv zawiera jeden wiersz na przetworzoną klatkę.\n"
            );
        }
        return bytes.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
