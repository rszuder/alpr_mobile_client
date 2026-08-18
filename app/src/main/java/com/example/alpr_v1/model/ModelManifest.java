package com.example.alpr_v1.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ModelManifest {
    public static final String SCHEMA = "alpr.model.v1";
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}");

    private final String modelId;
    private final String name;
    private final String version;
    private final ModelRole role;
    private final String task;
    private final ModelInputSpec input;
    private final ModelOutputSpec output;
    private final List<String> labels;
    private final List<ModelVariant> variants;
    private final String rawJson;

    private ModelManifest(
            String modelId,
            String name,
            String version,
            ModelRole role,
            String task,
            ModelInputSpec input,
            ModelOutputSpec output,
            List<String> labels,
            List<ModelVariant> variants,
            String rawJson
    ) {
        this.modelId = modelId;
        this.name = name;
        this.version = version;
        this.role = role;
        this.task = task;
        this.input = input;
        this.output = output;
        this.labels = Collections.unmodifiableList(new ArrayList<>(labels));
        this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
        this.rawJson = rawJson;
    }

    public static ModelManifest parse(String jsonText) throws JSONException {
        JSONObject json = new JSONObject(jsonText);
        if (!SCHEMA.equals(json.optString("schema"))) {
            throw new JSONException("Nieobsługiwany schemat pakietu: " + json.optString("schema"));
        }
        String modelId = json.getString("model_id").trim();
        if (!SAFE_ID.matcher(modelId).matches()) {
            throw new JSONException("Nieprawidłowy model_id: " + modelId);
        }

        ModelRole role;
        try {
            role = ModelRole.fromWire(json.getString("role"));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
        String task = json.getString("task").trim().toLowerCase(Locale.ROOT);
        if (!task.equals("detect") && !task.equals("pose")) {
            throw new JSONException("Nieobsługiwane zadanie modelu: " + task);
        }
        if (role == ModelRole.PLATE && !task.equals("pose")) {
            throw new JSONException("Model tablic musi być modelem pose z czterema narożnikami");
        }
        if ((role == ModelRole.VEHICLE || role == ModelRole.CHARACTER) && !task.equals("detect")) {
            throw new JSONException("Model pojazdu i model znaków muszą być modelami detect");
        }
        JSONObject output = json.getJSONObject("output");
        ModelOutputSpec outputSpec = ModelOutputSpec.fromJson(output);
        int keypointCount = outputSpec.keypointCount();
        if (role == ModelRole.PLATE && task.equals("pose") && keypointCount < 4) {
            throw new JSONException("Model tablic typu pose musi zwracać co najmniej 4 keypointy");
        }

        List<String> labels = new ArrayList<>();
        JSONArray labelsJson = json.optJSONArray("labels");
        if (labelsJson != null) {
            for (int i = 0; i < labelsJson.length(); i++) {
                labels.add(labelsJson.getString(i));
            }
        }

        List<ModelVariant> variants = new ArrayList<>();
        JSONArray variantsJson = json.getJSONArray("variants");
        for (int i = 0; i < variantsJson.length(); i++) {
            variants.add(ModelVariant.fromJson(variantsJson.getJSONObject(i)));
        }
        if (variants.isEmpty()) {
            throw new JSONException("Pakiet nie zawiera żadnego wariantu modelu");
        }
        int classCount = outputSpec.classCount();
        if (labels.size() != classCount) {
            throw new JSONException(
                    "Liczba etykiet (" + labels.size() + ") nie odpowiada class_count (" + classCount + ")"
            );
        }
        ModelInputSpec defaultInput = ModelInputSpec.fromJson(json.getJSONObject("input"));
        for (ModelVariant variant : variants) {
            ModelOutputSpec resolvedOutput = variant.output(outputSpec);
            if (resolvedOutput.classCount() != labels.size()) {
                throw new JSONException("Wariant " + variant.id() + " ma class_count niezgodny z labels");
            }
            if (role == ModelRole.PLATE && task.equals("pose") && resolvedOutput.keypointCount() < 4) {
                throw new JSONException("Wariant " + variant.id() + " nie zwraca czterech narożników tablicy");
            }
            variant.input(defaultInput); // Wymusza walidację obecności poprawnego wejścia.
        }

        return new ModelManifest(
                modelId,
                json.optString("name", modelId),
                json.optString("version", "1"),
                role,
                task,
                defaultInput,
                outputSpec,
                labels,
                variants,
                json.toString()
        );
    }

    public String modelId() { return modelId; }
    public String name() { return name; }
    public String version() { return version; }
    public ModelRole role() { return role; }
    public String task() { return task; }
    public ModelInputSpec input() { return input; }
    public ModelOutputSpec output() { return output; }
    public String decoder() { return output.decoder(); }
    public int classCount() { return output.classCount(); }
    public int keypointCount() { return output.keypointCount(); }
    public boolean hasObjectness() { return output.hasObjectness(); }
    public boolean channelsFirst() { return output.channelsFirst(); }
    public boolean normalizedCoordinates() { return output.normalizedCoordinates(); }
    public boolean nmsInGraph() { return output.nmsInGraph(); }
    public float confidenceThreshold() { return output.confidenceThreshold(); }
    public float iouThreshold() { return output.iouThreshold(); }
    public List<String> labels() { return labels; }
    public List<ModelVariant> variants() { return variants; }
    public String rawJson() { return rawJson; }
}
