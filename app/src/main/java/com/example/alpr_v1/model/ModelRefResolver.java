package com.example.alpr_v1.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/** Centralnie odczytuje provenance; nie rekonstruuje historii treningu. */
public final class ModelRefResolver {
    private ModelRefResolver() {}

    public static ModelRefSnapshot resolve(
            ModelRegistry registry,
            ModelRole role,
            InstalledModel model,
            ModelVariant variant
    ) {
        if (role == null || model == null || variant == null) {
            throw new IllegalArgumentException("Niepełne dane model reference");
        }
        if (model.manifest().role() != role) {
            throw new IllegalArgumentException("Model nie pasuje do roli " + role.wireName());
        }
        try {
            JSONObject resolved = new JSONObject();
            AlprPackageModelEntry packageEntry = null;
            if (registry != null && registry.isModelFromBase(role)) {
                InstalledAlprPackage basePackage = registry.getBasePackage();
                if (basePackage != null) {
                    JSONObject packageRef = basePackage.manifest().modelRef(role);
                    if (packageRef != null) mergeMissing(resolved, packageRef);
                    packageEntry = packageEntry(basePackage.manifest(), role);
                }
            }

            JSONObject manifestRoot = new JSONObject(model.manifest().rawJson());
            JSONObject manifestRef = manifestRoot.optJSONObject("model_ref");
            if (manifestRef != null) mergeMissing(resolved, manifestRef);
            copyPortableFields(resolved, manifestRoot);
            copyPortableFields(resolved, manifestRoot.optJSONObject("model"));
            copyPortableFields(resolved, manifestRoot.optJSONObject("source"));
            if (!resolved.has("training")) {
                JSONObject training = manifestRoot.optJSONObject("training");
                if (training != null) resolved.put("training", new JSONObject(training.toString()));
            }

            resolved.put("role", role.wireName());
            resolved.put("model_id", model.manifest().modelId());
            resolved.put("local_manifest_fingerprint", model.fingerprint());
            resolved.put("variant_id", variant.id());
            resolved.put("runtime", variant.runtime().wireName());
            resolved.put("precision", variant.precision());
            resolved.put("task", model.manifest().task());
            if (!resolved.has("model_display_id")) {
                resolved.put("model_display_id", model.manifest().name());
            }
            if (!resolved.has("variant_ids")) {
                JSONArray variantIds = new JSONArray();
                for (ModelVariant available : model.manifest().variants()) {
                    variantIds.put(available.id());
                }
                resolved.put("variant_ids", variantIds);
            }
            JSONArray artifactHashes = new JSONArray();
            for (String file : variant.files()) {
                String hash = variant.sha256().get(file);
                if (isSha256(hash)) artifactHashes.put(hash);
            }
            resolved.put("variant_artifact_sha256", artifactHashes);

            if (!isSha256(resolved.optString("package_sha256", ""))) {
                String packageSha = packageEntry == null
                        ? sourceArchiveSha256(model.sourceArchive())
                        : packageEntry.sha256().get(packageEntry.packageFile());
                if (isSha256(packageSha) || !resolved.has("package_sha256")) {
                    putNullable(resolved, "package_sha256", packageSha);
                }
            }
            if (packageEntry != null) {
                putIfAbsent(resolved, "package_file", packageEntry.packageFile());
                putIfAbsent(resolved, "manifest_file", packageEntry.manifestFile());
            }
            putNullableIfAbsent(resolved, "installed_model_fingerprint", null);
            putNullableIfAbsent(resolved, "checkpoint_sha256", null);
            normalizeLegacyTraining(resolved);
            return new ModelRefSnapshot(role, resolved, model.manifest().rawJson());
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Nie udało się zamrozić model reference dla " + role.wireName(),
                    error
            );
        }
    }

    private static void copyPortableFields(JSONObject destination, JSONObject source)
            throws JSONException {
        if (source == null) return;
        for (String key : new String[]{
                "model_display_id", "installed_model_fingerprint", "checkpoint_sha256",
                "package_sha256", "package_file", "manifest_file"
        }) {
            if (!destination.has(key) && source.has(key)) {
                destination.put(key, JSONObject.wrap(source.get(key)));
            }
        }
    }

    private static void mergeMissing(JSONObject destination, JSONObject source)
            throws JSONException {
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!destination.has(key)) destination.put(key, JSONObject.wrap(source.get(key)));
        }
    }

    private static void normalizeLegacyTraining(JSONObject resolved) throws JSONException {
        JSONObject training = resolved.optJSONObject("training");
        if (training == null) {
            training = new JSONObject();
            resolved.put("training", training);
        }
        putNullableIfAbsent(training, "run_id", null);
        putNullableIfAbsent(training, "run_epochs_completed", null);
        putNullableIfAbsent(training, "total_epochs", null);
        putIfAbsent(training, "total_epochs_known", false);
        putNullableIfAbsent(training, "known_epochs_minimum", null);
        putNullableIfAbsent(training, "total_epochs_scope", null);
        putNullableIfAbsent(training, "lineage_total_epochs", null);
        putIfAbsent(training, "lineage_total_epochs_known", false);
        putNullableIfAbsent(training, "lineage_stage_count", null);
        putIfAbsent(training, "lineage_stage_count_known", false);
        putNullableIfAbsent(training, "known_stage_count_minimum", null);
        putNullableIfAbsent(training, "run_train_images", null);
        putNullableIfAbsent(training, "run_nominal_sample_presentations", null);
        putNullableIfAbsent(training, "lineage_nominal_sample_presentations", null);
        putIfAbsent(training, "sample_presentations_known", false);
        putNullableIfAbsent(training, "known_sample_presentations_minimum", null);
        putNullableIfAbsent(training, "best_epoch_source", null);
        putNullableIfAbsent(training, "dataset_id", null);
        putIfAbsent(training, "provenance_capture", "legacy_unknown");
        putIfAbsent(training, "provenance_status", "legacy_unknown");
    }

    private static AlprPackageModelEntry packageEntry(
            AlprPackageManifest manifest,
            ModelRole role
    ) {
        if (role == ModelRole.VEHICLE) return manifest.vehicle();
        if (role == ModelRole.PLATE) return manifest.plate();
        return manifest.character();
    }

    private static String sourceArchiveSha256(File sourceArchive) throws Exception {
        return sourceArchive != null && sourceArchive.isFile()
                ? Hashing.sha256(sourceArchive)
                : null;
    }

    private static void putNullable(JSONObject json, String key, String value) throws JSONException {
        json.put(key, value == null || value.trim().isEmpty() ? JSONObject.NULL : value.trim());
    }

    private static void putNullableIfAbsent(JSONObject json, String key, String value)
            throws JSONException {
        if (!json.has(key)) putNullable(json, key, value);
    }

    private static void putIfAbsent(JSONObject json, String key, Object value) throws JSONException {
        if (!json.has(key)) json.put(key, value);
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }
}
