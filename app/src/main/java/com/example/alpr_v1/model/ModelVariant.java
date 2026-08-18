package com.example.alpr_v1.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class ModelVariant {
    private final String id;
    private final ModelRuntime runtime;
    private final String precision;
    private final List<String> files;
    private final Map<String, String> sha256;
    private final ModelInputSpec inputOverride;
    private final ModelOutputSpec outputOverride;

    private ModelVariant(
            String id,
            ModelRuntime runtime,
            String precision,
            List<String> files,
            Map<String, String> sha256,
            ModelInputSpec inputOverride,
            ModelOutputSpec outputOverride
    ) {
        this.id = id;
        this.runtime = runtime;
        this.precision = precision;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.sha256 = Collections.unmodifiableMap(new LinkedHashMap<>(sha256));
        this.inputOverride = inputOverride;
        this.outputOverride = outputOverride;
    }

    public static ModelVariant fromJson(JSONObject json) throws JSONException {
        String id = json.getString("id").trim();
        if (id.isEmpty()) {
            throw new JSONException("Pusty identyfikator wariantu");
        }
        ModelRuntime runtime;
        try {
            runtime = ModelRuntime.fromWire(json.getString("runtime"));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }

        List<String> files = new ArrayList<>();
        if (json.has("file")) {
            files.add(json.getString("file"));
        }
        JSONArray fileArray = json.optJSONArray("files");
        if (fileArray != null) {
            for (int i = 0; i < fileArray.length(); i++) {
                String file = fileArray.getString(i);
                if (!files.contains(file)) {
                    files.add(file);
                }
            }
        }
        if (files.isEmpty()) {
            throw new JSONException("Wariant " + id + " nie wskazuje żadnego pliku");
        }

        Map<String, String> checksums = new LinkedHashMap<>();
        JSONObject checksumJson = json.optJSONObject("sha256");
        if (checksumJson != null) {
            Iterator<String> keys = checksumJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                checksums.put(key, checksumJson.getString(key).trim().toLowerCase(Locale.ROOT));
            }
        } else if (files.size() == 1 && json.has("checksum")) {
            checksums.put(files.get(0), json.getString("checksum").trim().toLowerCase(Locale.ROOT));
        }

        return new ModelVariant(
                id,
                runtime,
                json.optString("precision", "fp32").trim().toLowerCase(Locale.ROOT),
                files,
                checksums,
                json.optJSONObject("input") == null ? null : ModelInputSpec.fromJson(json.getJSONObject("input")),
                json.optJSONObject("output") == null ? null : ModelOutputSpec.fromJson(json.getJSONObject("output"))
        );
    }

    public String id() { return id; }
    public ModelRuntime runtime() { return runtime; }
    public String precision() { return precision; }
    public List<String> files() { return files; }
    public Map<String, String> sha256() { return sha256; }
    public String primaryFile() { return files.get(0); }
    public ModelInputSpec input(ModelInputSpec packageDefault) {
        return inputOverride == null ? packageDefault : inputOverride;
    }
    public ModelOutputSpec output(ModelOutputSpec packageDefault) {
        return outputOverride == null ? packageDefault : outputOverride;
    }
}
