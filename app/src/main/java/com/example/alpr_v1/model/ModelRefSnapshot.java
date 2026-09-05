package com.example.alpr_v1.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Niezmienny snapshot portable identity i provenance modelu użytego w sesji. */
public final class ModelRefSnapshot {
    private final ModelRole role;
    private final String json;
    private final String modelManifestJson;

    ModelRefSnapshot(ModelRole role, JSONObject json, String modelManifestJson)
            throws JSONException {
        if (role == null || json == null) throw new IllegalArgumentException("role/json");
        this.role = role;
        this.json = new JSONObject(json.toString()).toString();
        this.modelManifestJson = modelManifestJson == null ? "" : modelManifestJson;
    }

    public static ModelRefSnapshot legacy(
            ModelRole role,
            String modelId,
            String localManifestFingerprint
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("role", role.wireName());
            json.put("model_id", safe(modelId));
            json.put("local_manifest_fingerprint", safe(localManifestFingerprint));
            json.put("installed_model_fingerprint", JSONObject.NULL);
            json.put("checkpoint_sha256", JSONObject.NULL);
            json.put("package_sha256", JSONObject.NULL);
            JSONObject training = new JSONObject();
            training.put("run_id", JSONObject.NULL);
            training.put("run_epochs_completed", JSONObject.NULL);
            training.put("total_epochs", JSONObject.NULL);
            training.put("total_epochs_known", false);
            training.put("known_epochs_minimum", JSONObject.NULL);
            training.put("total_epochs_scope", JSONObject.NULL);
            training.put("lineage_total_epochs", JSONObject.NULL);
            training.put("lineage_total_epochs_known", false);
            training.put("lineage_stage_count", JSONObject.NULL);
            training.put("lineage_stage_count_known", false);
            training.put("known_stage_count_minimum", JSONObject.NULL);
            training.put("run_train_images", JSONObject.NULL);
            training.put("run_nominal_sample_presentations", JSONObject.NULL);
            training.put("lineage_nominal_sample_presentations", JSONObject.NULL);
            training.put("sample_presentations_known", false);
            training.put("known_sample_presentations_minimum", JSONObject.NULL);
            training.put("best_epoch_source", JSONObject.NULL);
            training.put("dataset_id", JSONObject.NULL);
            training.put("provenance_capture", "legacy_unknown");
            training.put("provenance_status", "legacy_unknown");
            json.put("training", training);
            return new ModelRefSnapshot(role, json, "");
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    public ModelRole role() { return role; }

    public String modelId() {
        return toJson().optString("model_id", "");
    }

    public String localManifestFingerprint() {
        return toJson().optString("local_manifest_fingerprint", "");
    }

    public String installedModelFingerprint() {
        return nullableString(toJson(), "installed_model_fingerprint");
    }

    public String checkpointSha256() {
        return nullableString(toJson(), "checkpoint_sha256");
    }

    public String packageSha256() {
        return nullableString(toJson(), "package_sha256");
    }

    public JSONObject training() {
        JSONObject value = toJson().optJSONObject("training");
        try {
            return value == null ? new JSONObject() : new JSONObject(value.toString());
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    public JSONObject toJson() {
        try {
            return new JSONObject(json);
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    public String modelManifestJson() { return modelManifestJson; }

    private static String nullableString(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        String value = json.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
