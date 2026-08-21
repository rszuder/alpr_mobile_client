package com.example.alpr_v1.logging;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Trwały, rotowany dziennik aplikacji, zapisywany równolegle do Logcat. */
public final class AppLog {
    private static final Object LOCK = new Object();
    private static final long MAX_FILE_BYTES = 512L * 1024L;
    private static final String DIRECTORY = "logs";
    private static final String CURRENT_FILE = "alpr.log";
    private static final String PREVIOUS_FILE = "alpr.log.1";
    private static final Map<String, Long> LAST_RATE_LIMITED_WRITE = new HashMap<>();

    private AppLog() {}

    public static void info(Context context, String tag, String message) {
        Log.i(tag, message);
        persist(context, "INFO", tag, message, null);
    }

    public static void warning(Context context, String tag, String message) {
        Log.w(tag, message);
        persist(context, "WARN", tag, message, null);
    }

    public static void error(Context context, String tag, String message, Throwable error) {
        Log.e(tag, message, error);
        persist(context, "ERROR", tag, message, error);
    }

    public static void errorRateLimited(
            Context context,
            String rateLimitKey,
            String tag,
            String message,
            Throwable error,
            long minimumIntervalMillis
    ) {
        long now = SystemClock.elapsedRealtime();
        synchronized (LOCK) {
            Long previous = LAST_RATE_LIMITED_WRITE.get(rateLimitKey);
            if (previous != null && now - previous < minimumIntervalMillis) return;
            if (LAST_RATE_LIMITED_WRITE.size() > 100) LAST_RATE_LIMITED_WRITE.clear();
            LAST_RATE_LIMITED_WRITE.put(rateLimitKey, now);
        }
        error(context, tag, message, error);
    }

    /** Zwraca ostatnie nagłówki zdarzeń bez wielowierszowych stack trace'ów. */
    public static String recentEvents(Context context, int maximumEvents) {
        String contents = contents(context);
        ArrayDeque<String> events = new ArrayDeque<>();
        for (String line : contents.split("\\R")) {
            if (!line.matches("^\\d{4}-.*")) continue;
            events.addLast(line);
            while (events.size() > maximumEvents) events.removeFirst();
        }
        if (events.isEmpty()) return "Brak zapisanych zdarzeń";
        return String.join("\n", events);
    }

    /** Zwraca starszy i bieżący plik, gotowe do dołączenia do raportu. */
    public static String contents(Context context) {
        synchronized (LOCK) {
            File directory = logDirectory(context);
            StringBuilder result = new StringBuilder();
            appendFile(result, new File(directory, PREVIOUS_FILE));
            appendFile(result, new File(directory, CURRENT_FILE));
            return result.toString();
        }
    }

    private static void persist(
            Context context,
            String level,
            String tag,
            String message,
            Throwable error
    ) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT
        ).format(new Date());
        StringBuilder entry = new StringBuilder()
                .append(timestamp).append(' ')
                .append(level).append('/')
                .append(tag).append(' ')
                .append(message == null ? "" : message)
                .append('\n');
        if (error != null) entry.append(Log.getStackTraceString(error));
        byte[] bytes = entry.toString().getBytes(StandardCharsets.UTF_8);

        synchronized (LOCK) {
            File directory = logDirectory(context);
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e("AppLog", "Nie można utworzyć katalogu logów: " + directory);
                return;
            }
            File current = new File(directory, CURRENT_FILE);
            if (current.length() + bytes.length > MAX_FILE_BYTES) rotate(directory, current);
            try (FileOutputStream output = new FileOutputStream(current, true)) {
                output.write(bytes);
                output.flush();
            } catch (IOException writeError) {
                Log.e("AppLog", "Nie można zapisać trwałego logu", writeError);
            }
        }
    }

    private static void rotate(File directory, File current) {
        File previous = new File(directory, PREVIOUS_FILE);
        if (previous.exists() && !previous.delete()) {
            Log.w("AppLog", "Nie można usunąć poprzedniego pliku logu");
        }
        if (current.exists() && !current.renameTo(previous)) {
            Log.w("AppLog", "Nie można obrócić pliku logu; bieżący plik zostanie wyczyszczony");
            if (!current.delete()) Log.e("AppLog", "Nie można wyczyścić pliku logu");
        }
    }

    private static File logDirectory(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
    }

    private static void appendFile(StringBuilder destination, File file) {
        if (!file.isFile()) return;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) bytes.write(buffer, 0, read);
            }
            destination.append(bytes.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException readError) {
            Log.e("AppLog", "Nie można odczytać trwałego logu", readError);
        }
    }
}
