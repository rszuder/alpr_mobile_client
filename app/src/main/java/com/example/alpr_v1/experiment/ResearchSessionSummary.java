package com.example.alpr_v1.experiment;

import org.json.JSONObject;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Read-only summary of the configuration frozen with the selected archive, never current settings. */
public final class ResearchSessionSummary {
    public final File archive;
    public final JSONObject session;
    public final JSONObject execution;
    public final Instant date;
    public final boolean creationDate, complete, metadataAvailable, approximateDuration;
    public final long durationMillis;

    public ResearchSessionSummary(File archive, JSONObject session) {
        this.archive = archive;
        this.session = session == null ? new JSONObject() : session;
        metadataAvailable = this.session.length() > 0;
        JSONObject execution = this.session.optJSONObject("execution");
        this.execution = execution == null ? new JSONObject() : execution;
        Instant start = instant(this.session.optString("started_at", ""));
        creationDate = start == null;
        date = start == null ? instant(this.session.optString("created_at", "")) : start;
        complete = this.session.optBoolean("collection_complete", false);
        long first = this.session.optLong("started_elapsed_nanos", -1L);
        long last = this.session.optLong("finished_elapsed_nanos", -1L);
        Instant end = instant(this.session.optString("finished_at", ""));
        if (first >= 0 && last >= first) {
            durationMillis = (last - first) / 1_000_000L; approximateDuration = false;
        } else if (complete && !"process_interrupted".equals(this.session.optString("completion_reason"))
                && start != null && end != null && !end.isBefore(start)) {
            durationMillis = java.time.Duration.between(start, end).toMillis(); approximateDuration = true;
        } else { durationMillis = -1L; approximateDuration = false; }
    }

    public String value(String key) {
        String frozen = execution.optString(key, "");
        return frozen.isEmpty() || "null".equals(frozen) ? clean(session.optString(key, "")) : frozen;
    }

    public String dateText(ZoneId zone) {
        if (date == null) return "Data nieznana";
        return (creationDate ? "Utworzono " : "") + DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm:ss",
                Locale.forLanguageTag("pl-PL")).withZone(zone).format(date);
    }

    public String durationText() {
        if (durationMillis < 0) return "Czas nieustalony";
        long seconds = durationMillis / 1_000L;
        String value = seconds < 60 ? seconds + " s"
                : seconds < 3_600 ? String.format(Locale.ROOT,"%d min %02d s",seconds/60,seconds%60)
                : String.format(Locale.ROOT,"%d h %02d min %02d s",seconds/3600,(seconds/60)%60,seconds%60);
        if (durationMillis > 0 && durationMillis < 1_000) value = "< 1 s";
        return approximateDuration ? "≈ " + value : value;
    }

    public static List<ResearchSessionSummary> load(List<File> archives) {
        List<ResearchSessionSummary> result = new ArrayList<>();
        for (File file : archives) result.add(new ResearchSessionSummary(file, readMetadata(file)));
        result.sort(Comparator.comparing((ResearchSessionSummary item) -> item.date,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(item -> item.archive.getName()));
        return result;
    }

    private static JSONObject readMetadata(File archive) {
        try {
            File sidecar = new File(archive.getParentFile().getParentFile(), "session.json");
            return new JSONObject(new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        // An intact archive remains selectable even if the companion metadata file was lost.
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.zip.ZipEntry entry = zip.getEntry("session.json");
            if (entry == null) return new JSONObject();
            try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (Exception ignored) { return new JSONObject(); }
    }

    private static Instant instant(String value) {
        try { return Instant.parse(value); } catch (Exception ignored) { return null; }
    }
    private static String clean(String value) { return "null".equals(value) ? "" : value; }
}
