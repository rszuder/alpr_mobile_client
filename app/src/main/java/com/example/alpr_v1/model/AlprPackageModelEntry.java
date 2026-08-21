package com.example.alpr_v1.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AlprPackageModelEntry {
    private final ModelRole role;
    private final String task;
    private final String modelId;
    private final String schema;
    private final String packageFile;
    private final String manifestFile;
    private final Map<String, String> sha256;

    private AlprPackageModelEntry(
            ModelRole role,
            String task,
            String modelId,
            String schema,
            String packageFile,
            String manifestFile,
            Map<String, String> sha256
    ) {
        this.role = role;
        this.task = task;
        this.modelId = modelId;
        this.schema = schema;
        this.packageFile = packageFile;
        this.manifestFile = manifestFile;
        this.sha256 = Collections.unmodifiableMap(new LinkedHashMap<>(sha256));
    }

    public static AlprPackageModelEntry fromJson(JSONObject json, ModelRole expectedRole) throws JSONException {
        ModelRole role;
        try {
            role = ModelRole.fromWire(json.getString("role"));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
        if (role != expectedRole) {
            throw new JSONException("Model " + expectedRole.wireName() + " ma niezgodną rolę: " + role.wireName());
        }
        String expectedTask = role == ModelRole.PLATE ? "pose" : "detect";
        String task = json.getString("task").trim();
        if (!expectedTask.equals(task)) {
            throw new JSONException("Model " + role.wireName() + " ma niezgodne zadanie: " + task);
        }
        String schema = json.getString("schema").trim();
        if (!ModelManifest.SCHEMA.equals(schema)) {
            throw new JSONException("Model " + role.wireName() + " nie używa schematu " + ModelManifest.SCHEMA);
        }
        String packageFile = requireSafePath(json.getString("package_file"), ".alprmodel");
        String manifestFile = requireSafePath(json.getString("manifest_file"), ".json");
        if (packageFile.equals(manifestFile)) {
            throw new JSONException("Plik modelu i manifestu bocznego nie mogą być tym samym plikiem");
        }

        JSONObject hashes = json.getJSONObject("sha256");
        Map<String, String> sha256 = new LinkedHashMap<>();
        for (String path : new String[]{packageFile, manifestFile}) {
            String hash = hashes.optString(path, "").trim().toLowerCase(java.util.Locale.ROOT);
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new JSONException("Brak prawidłowej sumy SHA-256 dla pliku: " + path);
            }
            sha256.put(path, hash);
        }
        return new AlprPackageModelEntry(
                role,
                task,
                json.getString("model_id").trim(),
                schema,
                packageFile,
                manifestFile,
                sha256
        );
    }

    static String requireSafePath(String value, String suffix) throws JSONException {
        String path = value.trim();
        if (path.isEmpty() || path.startsWith("/") || path.startsWith("\\") || path.contains("\\")) {
            throw new JSONException("Niedozwolona ścieżka POSIX: " + value);
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.contains(":")) {
                throw new JSONException("Niedozwolona ścieżka POSIX: " + value);
            }
        }
        if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
            throw new JSONException("Plik " + path + " musi kończyć się na " + suffix);
        }
        return path;
    }

    public ModelRole role() { return role; }
    public String task() { return task; }
    public String modelId() { return modelId; }
    public String schema() { return schema; }
    public String packageFile() { return packageFile; }
    public String manifestFile() { return manifestFile; }
    public Map<String, String> sha256() { return sha256; }
}
