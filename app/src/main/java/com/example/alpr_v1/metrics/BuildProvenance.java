package com.example.alpr_v1.metrics;

import com.example.alpr_v1.BuildConfig;
import org.json.JSONException;
import org.json.JSONObject;

/** Build identity of the producer, recorded when a new artifact is created. */
public final class BuildProvenance {
    private BuildProvenance() { }
    public static JSONObject snapshot() throws JSONException {
        return new JSONObject().put("version_name", BuildConfig.VERSION_NAME)
                .put("version_code", BuildConfig.VERSION_CODE).put("git_commit", BuildConfig.GIT_COMMIT)
                .put("git_dirty", BuildConfig.GIT_DIRTY_AVAILABLE ? BuildConfig.GIT_DIRTY : JSONObject.NULL)
                .put("git_state", BuildConfig.GIT_DIRTY_AVAILABLE ? (BuildConfig.GIT_DIRTY ? "dirty" : "clean") : "unknown");
    }
}
