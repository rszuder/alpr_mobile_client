package com.example.alpr_v1.capture;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

/** Zapisuje wszystkie publiczne cropy w jednym katalogu zarządzanym przez aplikację. */
public final class CaptureDirectoryStore {
    public static final String DIRECTORY_NAME = "Mobilny ALPR - cropy";
    public static final String RELATIVE_PATH =
            "Download/" + DIRECTORY_NAME + "/";

    private CaptureDirectoryStore() {}

    public static Uri createDocument(
            ContentResolver resolver,
            String mimeType,
            String displayName
    ) {
        if (resolver == null) throw new IllegalArgumentException("resolver");
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName");
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(
                MediaStore.MediaColumns.MIME_TYPE,
                mimeType == null ? "application/octet-stream" : mimeType
        );
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri created = resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
        );
        if (created == null) {
            throw new IllegalStateException("MediaStore odrzucił utworzenie pliku cropu");
        }
        return created;
    }

    public static void publish(ContentResolver resolver, Uri document) {
        if (resolver == null || document == null) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        if (resolver.update(document, values, null, null) <= 0) {
            throw new IllegalStateException("Nie udało się opublikować pliku cropu");
        }
    }
}
