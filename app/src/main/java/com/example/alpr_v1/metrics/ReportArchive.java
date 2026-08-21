package com.example.alpr_v1.metrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReportArchive {
    private ReportArchive() {}

    public static byte[] create(String json, String csv) throws IOException {
        return createArchive(json, csv, null);
    }

    public static byte[] create(String json, String csv, String applicationLog) throws IOException {
        return createArchive(json, csv, applicationLog == null ? "" : applicationLog);
    }

    private static byte[] createArchive(String json, String csv, String applicationLog) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            write(zip, "report.json", json);
            write(zip, "traces.csv", csv);
            if (applicationLog != null) write(zip, "application.log", applicationLog);
            write(
                    zip,
                    "README.txt",
                    "Raport mobilnego ALPR zgodny z alpr.mobile_benchmark_report.v1. "
                            + "report.json zawiera osobne konfiguracje MP, MT i MZ, metadane urządzenia, "
                            + "czas pierwszego wyniku, sesję cropów, ich daty i czasy inferencji, "
                            + "autotuning, opóźnienia i pamięć. traces.csv zawiera jeden wiersz "
                            + "na przetworzoną klatkę. application.log zawiera trwały dziennik aplikacji, "
                            + "jeśli został dołączony. Metryki jakości wymagają osobnego testu z ground truth.\n"
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
