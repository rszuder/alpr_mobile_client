package com.example.alpr_v1.experiment;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CrashSessionMarkerInstrumentedTest {
    @Test
    public void recoversPreviousProcessSessionExactlyOnce() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences(
                "research_session_marker",
                Context.MODE_PRIVATE
        );
        preferences.edit().clear()
                .putBoolean("active", true)
                .putString("process_token", "previous-process")
                .putString("session_id", "exp-crashed")
                .putLong("started_at_ms", 12345L)
                .commit();
        try {
            CrashSessionMarker marker = new CrashSessionMarker(context);
            CrashSessionMarker.Recovery recovery = marker.consumeRecovery();

            assertEquals(1, recovery.count);
            assertEquals("exp-crashed", recovery.lastSessionId);
            assertEquals(12345L, recovery.lastSessionStartedAtMillis);
            assertEquals(0, marker.consumeRecovery().count);
        } finally {
            preferences.edit().clear().commit();
        }
    }
}
