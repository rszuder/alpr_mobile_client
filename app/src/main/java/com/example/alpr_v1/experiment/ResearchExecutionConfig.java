package com.example.alpr_v1.experiment;

import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;

import org.json.JSONException;
import org.json.JSONObject;

/** Niezmienny snapshot efektywnej konfiguracji całego przebiegu badawczego. */
public final class ResearchExecutionConfig {
    public final String experimentType;
    public final String variant;
    public final RoiBudgetPolicy roiBudgetPolicy;
    public final RecognitionProfile recognitionProfile;
    public final String cameraRequestedResolution;
    public final boolean lockEnabled;
    public final boolean autoZoomEnabled;
    public final boolean vehicleTrackingEnabled;
    public final boolean plateTrackingEnabled;
    public final boolean temporalMzEnabled;
    public final boolean adaptiveFrameGateEnabled;
    public final ResearchStageExecutionConfig vehicle;
    public final ResearchStageExecutionConfig plate;
    public final ResearchStageExecutionConfig character;

    public ResearchExecutionConfig(
            String experimentType,
            String variant,
            RoiBudgetPolicy roiBudgetPolicy,
            RecognitionProfile recognitionProfile,
            String cameraRequestedResolution,
            boolean lockEnabled,
            boolean autoZoomEnabled,
            boolean vehicleTrackingEnabled,
            boolean plateTrackingEnabled,
            boolean temporalMzEnabled,
            boolean adaptiveFrameGateEnabled,
            ResearchStageExecutionConfig vehicle,
            ResearchStageExecutionConfig plate,
            ResearchStageExecutionConfig character
    ) {
        this.experimentType = required(experimentType, "experimentType");
        this.variant = required(variant, "variant");
        this.roiBudgetPolicy = roiBudgetPolicy == null
                ? RoiBudgetPolicy.TWO_ROI : roiBudgetPolicy;
        this.recognitionProfile = recognitionProfile == null
                ? RecognitionProfile.BALANCED : recognitionProfile;
        this.cameraRequestedResolution = required(
                cameraRequestedResolution,
                "cameraRequestedResolution"
        );
        this.lockEnabled = lockEnabled;
        this.autoZoomEnabled = autoZoomEnabled;
        this.vehicleTrackingEnabled = vehicleTrackingEnabled;
        this.plateTrackingEnabled = plateTrackingEnabled;
        this.temporalMzEnabled = temporalMzEnabled;
        this.adaptiveFrameGateEnabled = adaptiveFrameGateEnabled;
        this.vehicle = requireRole(vehicle, ModelRole.VEHICLE);
        this.plate = requireRole(plate, ModelRole.PLATE);
        this.character = requireRole(character, ModelRole.CHARACTER);
        if (this.roiBudgetPolicy.usesVehicleCascade() && !this.vehicle.enabled) {
            throw new IllegalArgumentException("R1/R2 wymagają aktywnego etapu MP");
        }
        if (!this.plate.enabled || !this.character.enabled) {
            throw new IllegalArgumentException("Research Mode wymaga etapów MT i MZ");
        }
        if (this.autoZoomEnabled && !this.lockEnabled) {
            throw new IllegalArgumentException("Autozoom wymaga włączonego locka");
        }
    }

    public ResearchStageExecutionConfig stage(ModelRole role) {
        if (role == ModelRole.VEHICLE) return vehicle;
        if (role == ModelRole.PLATE) return plate;
        if (role == ModelRole.CHARACTER) return character;
        throw new IllegalArgumentException("role");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("experiment_type", experimentType);
        json.put("variant", variant);
        json.put("roi_budget_policy", roiBudgetPolicy.wireName());
        json.put("recognition_profile", recognitionProfile.wireName());
        json.put("camera_requested_resolution", cameraRequestedResolution);
        JSONObject flags = new JSONObject();
        flags.put("lock", lockEnabled);
        flags.put("autozoom", autoZoomEnabled);
        flags.put("vehicle_tracking", vehicleTrackingEnabled);
        flags.put("plate_tracking", plateTrackingEnabled);
        flags.put("temporal_mz", temporalMzEnabled);
        flags.put("adaptive_frame_gate", adaptiveFrameGateEnabled);
        json.put("feature_flags", flags);
        JSONObject stages = new JSONObject();
        stages.put("mp", vehicle.toJson());
        stages.put("mt", plate.toJson());
        stages.put("mz", character.toJson());
        json.put("stages", stages);
        return json;
    }

    private static ResearchStageExecutionConfig requireRole(
            ResearchStageExecutionConfig config,
            ModelRole role
    ) {
        if (config == null || config.role != role) {
            throw new IllegalArgumentException("Brak konfiguracji etapu " + role.wireName());
        }
        return config;
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field);
        return normalized;
    }
}
