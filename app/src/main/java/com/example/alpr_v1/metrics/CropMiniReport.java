package com.example.alpr_v1.metrics;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.pipeline.PlateCharacter;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;

/** Tworzy samodzielny raport JSON zapisywany obok wybranego cropu. */
public final class CropMiniReport {
    public static final String SCHEMA = "alpr.mobile.crop_report.v1";

    private CropMiniReport() {}

    public static String create(
            CapturedPlateItem item,
            long sessionStartedElapsedNanos,
            DeviceProfile device,
            ModelRegistry registry,
            AutoTuneManager autoTuneManager,
            String recognitionProfile,
            String resolutionProfile,
            boolean normalVehicleCascadeEnabled,
            boolean experimentModeEnabled,
            RoiBudgetPolicy experimentRoiBudgetPolicy,
            RoiBudgetPolicy effectiveRoiBudgetPolicy
    ) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schema", SCHEMA);
        json.put("capture_id", item.captureId);
        json.put("session_id", item.sessionId);
        json.put("captured_at_utc", Instant.ofEpochMilli(item.capturedAtMillis).toString());
        json.put("timezone", ZoneId.systemDefault().getId());
        json.put("session_elapsed_ms", Math.max(
                0L, item.capturedElapsedNanos - sessionStartedElapsedNanos
        ) / 1_000_000.0);
        json.put("track_id", item.trackId);
        json.put("text", item.text);
        json.put("confirmed", item.confirmed);
        json.put("plate_confidence", item.plateConfidence);
        json.put("recognition_confidence", item.recognitionConfidence);
        json.put("sharpness", item.sharpness);
        json.put("camera_zoom_ratio", item.cameraZoomRatio);
        json.put("capture_source", item.captureSource);
        json.put("human_verification", humanVerificationJson(item));
        JSONObject image = new JSONObject();
        image.put("width", item.bitmap.getWidth());
        image.put("height", item.bitmap.getHeight());
        image.put("format", "image/jpeg");
        json.put("image", image);
        JSONArray characters = new JSONArray();
        for (PlateCharacter character : item.characters) {
            JSONObject value = new JSONObject();
            value.put("label", character.label);
            value.put("confidence", character.confidence);
            value.put("left", character.left);
            value.put("top", character.top);
            value.put("right", character.right);
            value.put("bottom", character.bottom);
            characters.put(value);
        }
        json.put("characters", characters);
        if (item.timing != null) json.put("timing", item.timing.toJson());
        JSONObject pipeline = new JSONObject();
        pipeline.put("recognition_profile", recognitionProfile);
        pipeline.put("resolution_profile", resolutionProfile);
        pipeline.put(
                "vehicle_cascade_enabled",
                effectiveRoiBudgetPolicy.usesVehicleCascade()
        );

        pipeline.put(
                "roi_budget_policy",
                effectiveRoiBudgetPolicy.wireName()
        );

        pipeline.put(
                "normal_vehicle_cascade_enabled",
                normalVehicleCascadeEnabled
        );

        JSONObject experiment = new JSONObject();

        experiment.put(
                "enabled",
                experimentModeEnabled
        );

        experiment.put(
                "type",
                "roi_budget"
        );

        experiment.put(
                "roi_budget_policy",
                experimentRoiBudgetPolicy.wireName()
        );

        pipeline.put(
                "experiment",
                experiment
        );
        json.put("pipeline", pipeline);
        JSONObject models = new JSONObject();
        addModel(models, "vehicle", registry.getActive(ModelRole.VEHICLE), autoTuneManager);
        addModel(models, "plate", registry.getActive(ModelRole.PLATE), autoTuneManager);
        addModel(models, "character", registry.getActive(ModelRole.CHARACTER), autoTuneManager);
        json.put("models", models);
        json.put("device", device.toJson());
        return json.toString(2);
    }

    public static String refreshHumanVerification(String report, CapturedPlateItem item)
            throws JSONException {
        JSONObject json = new JSONObject(report);
        json.put("human_verification", humanVerificationJson(item));
        return json.toString(2);
    }

    private static JSONObject humanVerificationJson(CapturedPlateItem item) throws JSONException {
        JSONObject verification = new JSONObject();
        verification.put("status", item.verificationStatus.wireName());
        verification.put("revision", item.verificationRevision);
        verification.put("verified_at_ms", item.verifiedAtMillis);
        verification.put("ground_truth_text", item.groundTruthText);
        verification.put("original_prediction", item.text);
        return verification;
    }

    private static void addModel(
            JSONObject target,
            String name,
            InstalledModel model,
            AutoTuneManager autoTuneManager
    ) throws JSONException {
        if (model == null) return;
        ModelVariant variant = autoTuneManager.chosenVariant(model);
        JSONObject value = new JSONObject();
        value.put("model_id", model.manifest().modelId());
        value.put("fingerprint", model.fingerprint());
        value.put("variant_id", variant.id());
        value.put("runtime", variant.runtime().wireName());
        value.put("precision", variant.precision());
        target.put(name, value);
    }
}
