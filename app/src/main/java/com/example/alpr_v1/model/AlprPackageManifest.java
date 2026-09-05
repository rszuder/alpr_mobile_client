package com.example.alpr_v1.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class AlprPackageManifest {
    public static final String SCHEMA = "alpr.package.v1";
    public static final String KIND = "complete_alpr_pipeline";
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}");

    private final String packageId;
    private final String name;
    private final String version;
    private final AlprPackageModelEntry vehicle;
    private final AlprPackageModelEntry plate;
    private final AlprPackageModelEntry character;
    private final List<PipelineStage> pipeline;
    private final Map<ModelRole, String> modelRefs;
    private final String rawJson;

    private AlprPackageManifest(
            String packageId,
            String name,
            String version,
            AlprPackageModelEntry vehicle,
            AlprPackageModelEntry plate,
            AlprPackageModelEntry character,
            List<PipelineStage> pipeline,
            Map<ModelRole, String> modelRefs,
            String rawJson
    ) {
        this.packageId = packageId;
        this.name = name;
        this.version = version;
        this.vehicle = vehicle;
        this.plate = plate;
        this.character = character;
        this.pipeline = Collections.unmodifiableList(new ArrayList<>(pipeline));
        this.modelRefs = Collections.unmodifiableMap(new EnumMap<>(modelRefs));
        this.rawJson = rawJson;
    }

    public static AlprPackageManifest parse(String jsonText) throws JSONException {
        JSONObject json = new JSONObject(jsonText);
        if (!SCHEMA.equals(json.optString("schema"))) {
            throw new JSONException("Nieobsługiwany schemat kompletnego pakietu: " + json.optString("schema"));
        }
        if (!KIND.equals(json.optString("kind"))) {
            throw new JSONException("Nieobsługiwany rodzaj pakietu ALPR: " + json.optString("kind"));
        }
        String packageId = json.getString("package_id").trim();
        if (!SAFE_ID.matcher(packageId).matches()) {
            throw new JSONException("Nieprawidłowy package_id: " + packageId);
        }

        JSONObject models = json.getJSONObject("models");
        JSONObject vehicleJson = models.optJSONObject("vehicle");
        AlprPackageModelEntry vehicle = vehicleJson == null
                ? null
                : AlprPackageModelEntry.fromJson(vehicleJson, ModelRole.VEHICLE);
        AlprPackageModelEntry plate = AlprPackageModelEntry.fromJson(
                models.getJSONObject("plate"), ModelRole.PLATE
        );
        AlprPackageModelEntry character = AlprPackageModelEntry.fromJson(
                models.getJSONObject("character"), ModelRole.CHARACTER
        );

        JSONArray pipelineJson = json.getJSONArray("pipeline");
        List<PipelineStage> pipeline = new ArrayList<>();
        for (int i = 0; i < pipelineJson.length(); i++) {
            pipeline.add(PipelineStage.fromJson(pipelineJson.getJSONObject(i)));
        }
        validatePipeline(pipeline, vehicle != null);
        Map<ModelRole, String> modelRefs = parseModelRefs(
                json.optJSONObject("model_refs"), vehicle, plate, character
        );
        return new AlprPackageManifest(
                packageId,
                json.optString("name", packageId),
                json.optString("version", "1"),
                vehicle,
                plate,
                character,
                pipeline,
                modelRefs,
                json.toString()
        );
    }

    private static Map<ModelRole, String> parseModelRefs(
            JSONObject json,
            AlprPackageModelEntry vehicle,
            AlprPackageModelEntry plate,
            AlprPackageModelEntry character
    ) throws JSONException {
        Map<ModelRole, String> result = new EnumMap<>(ModelRole.class);
        if (json == null) return result;
        for (ModelRole role : ModelRole.values()) {
            JSONObject value = json.optJSONObject(role.wireName());
            if (value == null) continue;
            AlprPackageModelEntry entry = role == ModelRole.VEHICLE
                    ? vehicle : role == ModelRole.PLATE ? plate : character;
            if (entry == null) {
                throw new JSONException("model_refs zawiera brakującą rolę: " + role.wireName());
            }
            if (value.has("model_id") && !value.isNull("model_id")
                    && !entry.modelId().equals(value.optString("model_id").trim())) {
                throw new JSONException("Niezgodny model_id w model_refs." + role.wireName());
            }
            result.put(role, new JSONObject(value.toString()).toString());
        }
        return result;
    }

    private static void validatePipeline(List<PipelineStage> pipeline, boolean hasVehicle)
            throws JSONException {
        int expectedSize = hasVehicle ? 5 : 4;
        if (pipeline.size() != expectedSize) {
            throw new JSONException(
                    "Pipeline v1 musi zawierać dokładnie " + expectedSize + " obsługiwanych etapów"
            );
        }
        int offset = 0;
        if (hasVehicle) {
            requireModelStage(
                    pipeline.get(0),
                    "vehicle_detection",
                    "vehicle",
                    "vehicle",
                    "detect"
            );
            offset = 1;
        }
        requireModelStage(pipeline.get(offset), "plate_detection", "plate", "plate", "pose");
        requireImplementation(
                pipeline.get(offset + 1),
                "plate_rectification",
                "android_alpr_rectifier"
        );
        requireModelStage(
                pipeline.get(offset + 2),
                "character_detection",
                "character",
                "character",
                "detect"
        );
        requireImplementation(
                pipeline.get(offset + 3),
                "sequence_assembly",
                "android_alpr_sequence_decoder"
        );
    }

    private static void requireModelStage(
            PipelineStage stage,
            String name,
            String model,
            String role,
            String task
    ) throws JSONException {
        if (!name.equals(stage.stage()) || !model.equals(stage.model())
                || !role.equals(stage.role()) || !task.equals(stage.task())) {
            throw new JSONException("Nieobsługiwany etap pipeline'u: " + stage.stage());
        }
    }

    private static void requireImplementation(PipelineStage stage, String name, String implementation)
            throws JSONException {
        if (!name.equals(stage.stage()) || !implementation.equals(stage.implementation())) {
            throw new JSONException("Nieobsługiwany etap pipeline'u: " + stage.stage());
        }
    }

    public String packageId() { return packageId; }
    public String name() { return name; }
    public String version() { return version; }
    public AlprPackageModelEntry vehicle() { return vehicle; }
    public AlprPackageModelEntry plate() { return plate; }
    public AlprPackageModelEntry character() { return character; }
    public List<PipelineStage> pipeline() { return pipeline; }
    public JSONObject modelRef(ModelRole role) {
        String value = modelRefs.get(role);
        if (value == null) return null;
        try {
            return new JSONObject(value);
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }
    public boolean hasModelRefs() { return !modelRefs.isEmpty(); }
    public String createdAt() {
        try {
            return new JSONObject(rawJson).optString("created_at", "");
        } catch (JSONException ignored) {
            return "";
        }
    }
    public String rawJson() { return rawJson; }
}
