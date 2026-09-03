package com.example.alpr_v1.experiment;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/** Trwały marker pozwalający wykryć poprzedni niedomknięty przebieg pomiarowy. */
public final class CrashSessionMarker {
    private static final String PREFERENCES = "research_session_marker";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PROCESS_TOKEN = "process_token";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_STARTED_AT = "started_at_ms";
    private static final String KEY_PENDING_FAILURES = "pending_failures";
    private static final String KEY_PENDING_SESSION_ID = "pending_session_id";
    private static final String KEY_PENDING_STARTED_AT = "pending_started_at_ms";
    private static final String PROCESS_TOKEN = UUID.randomUUID().toString();

    private final SharedPreferences preferences;

    public static final class Recovery {
        public final int count;
        public final String lastSessionId;
        public final long lastSessionStartedAtMillis;

        Recovery(int count, String lastSessionId, long lastSessionStartedAtMillis) {
            this.count = Math.max(0, count);
            this.lastSessionId = lastSessionId == null ? "" : lastSessionId.trim();
            this.lastSessionStartedAtMillis = Math.max(0L, lastSessionStartedAtMillis);
        }
    }

    public CrashSessionMarker(Context context) {
        if (context == null) throw new IllegalArgumentException("context");
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        );
        recoverPreviousProcess();
    }

    public synchronized void markStarted(String sessionId) {
        preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_PROCESS_TOKEN, PROCESS_TOKEN)
                .putString(KEY_SESSION_ID, sessionId == null ? "" : sessionId)
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .commit();
    }

    public synchronized void markFinished() {
        preferences.edit()
                .remove(KEY_ACTIVE)
                .remove(KEY_PROCESS_TOKEN)
                .remove(KEY_SESSION_ID)
                .remove(KEY_STARTED_AT)
                .commit();
    }

    public synchronized int consumeRecoveredFailureCount() {
        return consumeRecovery().count;
    }

    public synchronized Recovery consumeRecovery() {
        Recovery recovery = new Recovery(
                preferences.getInt(KEY_PENDING_FAILURES, 0),
                preferences.getString(KEY_PENDING_SESSION_ID, ""),
                preferences.getLong(KEY_PENDING_STARTED_AT, 0L)
        );
        preferences.edit()
                .remove(KEY_PENDING_FAILURES)
                .remove(KEY_PENDING_SESSION_ID)
                .remove(KEY_PENDING_STARTED_AT)
                .commit();
        return recovery;
    }

    private void recoverPreviousProcess() {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return;
        String owner = preferences.getString(KEY_PROCESS_TOKEN, "");
        if (PROCESS_TOKEN.equals(owner)) return;
        int pending = Math.max(0, preferences.getInt(KEY_PENDING_FAILURES, 0));
        String sessionId = preferences.getString(KEY_SESSION_ID, "");
        long startedAtMillis = preferences.getLong(KEY_STARTED_AT, 0L);
        preferences.edit()
                .putInt(KEY_PENDING_FAILURES, pending + 1)
                .putString(KEY_PENDING_SESSION_ID, sessionId)
                .putLong(KEY_PENDING_STARTED_AT, startedAtMillis)
                .remove(KEY_ACTIVE)
                .remove(KEY_PROCESS_TOKEN)
                .remove(KEY_SESSION_ID)
                .remove(KEY_STARTED_AT)
                .commit();
    }
}
