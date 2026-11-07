package com.example.alpr_v1.experiment;

import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;

import org.json.JSONException;
import org.json.JSONObject;

/** Niezmienny snapshot efektywnej konfiguracji całego przebiegu badawczego. */
public final class ResearchExecutionConfig {
    public boolean mzInputEvidenceEnabled(){return false;}
    public ResearchExecutionConfig withMzInputEvidence(boolean enabled){
        if(enabled)MzInputEvidence.requireDiagnosticStartAllowed();return this;
    }
    /** TV crop uses the accepted F21 contract; TV rectification remains a test-only prototype. */
    private final com.example.alpr_v1.setup.ResearchInputMode researchInputMode;
    private final com.example.alpr_v1.setup.TvBboxCrop tvBboxCrop;
    public com.example.alpr_v1.setup.ResearchInputMode inputMode(){return researchInputMode;}
    public com.example.alpr_v1.setup.TvBboxCrop tvBboxCrop(){return tvBboxCrop;}
    public ResearchExecutionConfig withTvBboxCrop(com.example.alpr_v1.setup.TvBboxCrop snapshot){
        if(!"roi_budget".equals(experimentType))throw new IllegalStateException("TV_BBOX_F5A_ONLY");
        if(autoZoomEnabled)throw new IllegalStateException("TV_BBOX_REQUIRES_FIXED_ZOOM");
        if(snapshot==null||!snapshot.cameraSetup.same(cameraSetup))throw new IllegalArgumentException("TV_BBOX_CAMERA_SETUP_MISMATCH");
        return new ResearchExecutionConfig(this,cameraSetup,com.example.alpr_v1.setup.ResearchInputMode.TV_BBOX_CROP,snapshot);
    }
    public ResearchExecutionConfig withInputMode(com.example.alpr_v1.setup.ResearchInputMode mode){
        java.util.Objects.requireNonNull(mode).requireSessionSupported();
        if(mode==com.example.alpr_v1.setup.ResearchInputMode.TV_BBOX_CROP){
            if(tvBboxCrop==null)throw new IllegalStateException("TV_BBOX_SNAPSHOT_REQUIRED");
            requireSessionInputReady();return this;
        }
        return researchInputMode==mode?this:new ResearchExecutionConfig(this,cameraSetup,mode,null);
    }
    /** Frozen input admission; live CameraX geometry is checked separately before START and per frame. */
    public void requireSessionInputReady(){
        researchInputMode.requireSessionSupported();
        if(researchInputMode!=com.example.alpr_v1.setup.ResearchInputMode.TV_BBOX_CROP)return;
        if(tvBboxCrop==null)throw new IllegalStateException("TV_BBOX_SNAPSHOT_REQUIRED");
        if(!"roi_budget".equals(experimentType)||autoZoomEnabled)throw new IllegalStateException("TV_BBOX_REQUIRES_F5A_FIXED_ZOOM");
        if(!tvBboxCrop.cameraSetup.same(cameraSetup))throw new IllegalStateException("TV_BBOX_CAMERA_SETUP_MISMATCH");
        com.example.alpr_v1.setup.TvBboxCrop.fromJson(tvBboxCrop.toJson());
    }
    public final com.example.alpr_v1.acquisition.DynamicMtConfig dynamicMtConfig;
    public final com.example.alpr_v1.continuity.SceneHandlingMode sceneHandlingMode;
    public final String experimentType;
    public final String variant;
    public final RoiBudgetPolicy roiBudgetPolicy;
    public final RecognitionProfile recognitionProfile;
    public final String cameraRequestedResolution;
    public final com.example.alpr_v1.camera.CameraSetupSnapshot cameraSetup;
    public final boolean lockEnabled;
    public final boolean autoZoomEnabled;
    public final boolean vehicleTrackingEnabled;
    public final boolean plateTrackingEnabled;
    public final boolean temporalMzEnabled;
    public final boolean adaptiveFrameGateEnabled;
    public final ResearchStageExecutionConfig vehicle;
    public final ResearchStageExecutionConfig plate;
    public final ResearchStageExecutionConfig character;
    public final String basePackageId;
    public final String basePackageFingerprint;
    public final String basePackageVersion;
    public final String basePackageCreatedAt;
    public final String basePackageSourceSha256;
    public final String basePackageManifestJson;
    public final String basePackageObservedSha256;
    public final String basePackageShaEvidence;
    public final boolean compositionModified;
    public final long packageSizeBytes;

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
        this(
                experimentType, variant, roiBudgetPolicy, recognitionProfile,
                cameraRequestedResolution, lockEnabled, autoZoomEnabled,
                vehicleTrackingEnabled, plateTrackingEnabled, temporalMzEnabled,
                adaptiveFrameGateEnabled, vehicle, plate, character, null, false, 0L
        );
    }

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
            ResearchStageExecutionConfig character,
            long packageSizeBytes
    ) {
        this(
                experimentType, variant, roiBudgetPolicy, recognitionProfile,
                cameraRequestedResolution, lockEnabled, autoZoomEnabled,
                vehicleTrackingEnabled, plateTrackingEnabled, temporalMzEnabled,
                adaptiveFrameGateEnabled, vehicle, plate, character, null, false,
                packageSizeBytes
        );
    }

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
            ResearchStageExecutionConfig character,
            InstalledAlprPackage basePackage,
            boolean compositionModified,
            long packageSizeBytes
    ) {
        this(experimentType, variant, roiBudgetPolicy, recognitionProfile, cameraRequestedResolution,
                lockEnabled, autoZoomEnabled, vehicleTrackingEnabled, plateTrackingEnabled, temporalMzEnabled,
                adaptiveFrameGateEnabled, vehicle, plate, character, basePackage, compositionModified, packageSizeBytes,
                com.example.alpr_v1.continuity.SceneHandlingMode.STRICT_SCENE_BOUNDARY);
    }

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
            ResearchStageExecutionConfig character,
            InstalledAlprPackage basePackage,
            boolean compositionModified,
            long packageSizeBytes,
            com.example.alpr_v1.continuity.SceneHandlingMode sceneHandlingMode
    ) {
        this(experimentType, variant, roiBudgetPolicy, recognitionProfile, cameraRequestedResolution,
                lockEnabled, autoZoomEnabled, vehicleTrackingEnabled, plateTrackingEnabled, temporalMzEnabled,
                adaptiveFrameGateEnabled, vehicle, plate, character, basePackage, compositionModified,
                packageSizeBytes, sceneHandlingMode, com.example.alpr_v1.acquisition.DynamicMtConfig.INITIAL);
    }

    public ResearchExecutionConfig(
            String experimentType, String variant, RoiBudgetPolicy roiBudgetPolicy,
            RecognitionProfile recognitionProfile, String cameraRequestedResolution,
            boolean lockEnabled, boolean autoZoomEnabled, boolean vehicleTrackingEnabled,
            boolean plateTrackingEnabled, boolean temporalMzEnabled, boolean adaptiveFrameGateEnabled,
            ResearchStageExecutionConfig vehicle, ResearchStageExecutionConfig plate, ResearchStageExecutionConfig character,
            InstalledAlprPackage basePackage, boolean compositionModified, long packageSizeBytes,
            com.example.alpr_v1.continuity.SceneHandlingMode sceneHandlingMode,
            com.example.alpr_v1.acquisition.DynamicMtConfig dynamicMtConfig
    ) {
        this.dynamicMtConfig = java.util.Objects.requireNonNull(dynamicMtConfig);
        this.cameraSetup=null;
        this.sceneHandlingMode = java.util.Objects.requireNonNull(sceneHandlingMode);
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
        this.researchInputMode=com.example.alpr_v1.setup.ResearchInputMode.CAMERA_FULL_FRAME;
        this.tvBboxCrop=null;
        this.lockEnabled = lockEnabled;
        this.autoZoomEnabled = autoZoomEnabled;
        this.vehicleTrackingEnabled = vehicleTrackingEnabled;
        this.plateTrackingEnabled = plateTrackingEnabled;
        this.temporalMzEnabled = temporalMzEnabled;
        this.adaptiveFrameGateEnabled = adaptiveFrameGateEnabled;
        this.vehicle = requireRole(vehicle, ModelRole.VEHICLE);
        this.plate = requireRole(plate, ModelRole.PLATE);
        this.character = requireRole(character, ModelRole.CHARACTER);
        this.basePackageId = basePackage == null ? "" : basePackage.manifest().packageId();
        this.basePackageFingerprint = basePackage == null ? "" : basePackage.fingerprint();
        this.basePackageVersion = basePackage == null ? "" : basePackage.manifest().version();
        this.basePackageCreatedAt = basePackage == null ? "" : basePackage.manifest().createdAt();
        this.basePackageSourceSha256 = basePackage == null ? "" : basePackage.sourceSha256();
        this.basePackageManifestJson = basePackage == null ? "" : basePackage.manifest().rawJson();
        String observedSha = "", shaEvidence = "NO_BASE_PACKAGE";
        if (basePackage != null) {
            try {
                observedSha = com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(basePackage.sourceArchive());
                shaEvidence = observedSha.equals(basePackageSourceSha256) ? "OBSERVED_MATCH" : "OBSERVED_MISMATCH";
                if (!observedSha.equals(basePackageSourceSha256)) throw new IllegalArgumentException("RESEARCH_PACKAGE_SHA_CHANGED");
            } catch (java.io.IOException error) { shaEvidence = "SOURCE_ARCHIVE_UNAVAILABLE: " + error.getMessage(); }
        }
        basePackageObservedSha256 = observedSha; basePackageShaEvidence = shaEvidence;
        this.compositionModified = basePackage != null && compositionModified;
        this.packageSizeBytes = Math.max(0L, packageSizeBytes);
        if (this.roiBudgetPolicy.usesVehicleCascade() && !this.vehicle.enabled) {
            throw new IllegalArgumentException("R1/R2 wymagają aktywnego etapu MP");
        }
        if (!this.plate.enabled || !this.character.enabled) {
            throw new IllegalArgumentException("Research Mode wymaga etapów MT i MZ");
        }
        if (this.autoZoomEnabled && !this.lockEnabled
                && sceneHandlingMode == com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY) {
            throw new IllegalArgumentException("Autozoom wymaga włączonego locka");
        }
    }

    public ResearchStageExecutionConfig stage(ModelRole role) {
        if (role == ModelRole.VEHICLE) return vehicle;
        if (role == ModelRole.PLATE) return plate;
        if (role == ModelRole.CHARACTER) return character;
        throw new IllegalArgumentException("role");
    }
    private ResearchExecutionConfig(ResearchExecutionConfig source,com.example.alpr_v1.camera.CameraSetupSnapshot setup){
        this(source,setup,source.researchInputMode,source.tvBboxCrop);
    }
    private ResearchExecutionConfig(ResearchExecutionConfig source,com.example.alpr_v1.camera.CameraSetupSnapshot setup,
            com.example.alpr_v1.setup.ResearchInputMode mode,com.example.alpr_v1.setup.TvBboxCrop crop){
        researchInputMode=mode;tvBboxCrop=crop;
        dynamicMtConfig=source.dynamicMtConfig;sceneHandlingMode=source.sceneHandlingMode;experimentType=source.experimentType;variant=source.variant;
        roiBudgetPolicy=source.roiBudgetPolicy;recognitionProfile=source.recognitionProfile;cameraRequestedResolution=source.cameraRequestedResolution;cameraSetup=setup;
        lockEnabled=source.lockEnabled;autoZoomEnabled=source.autoZoomEnabled;vehicleTrackingEnabled=source.vehicleTrackingEnabled;
        plateTrackingEnabled=source.plateTrackingEnabled;temporalMzEnabled=source.temporalMzEnabled;adaptiveFrameGateEnabled=source.adaptiveFrameGateEnabled;
        vehicle=source.vehicle;plate=source.plate;character=source.character;basePackageId=source.basePackageId;basePackageFingerprint=source.basePackageFingerprint;
        basePackageVersion=source.basePackageVersion;basePackageCreatedAt=source.basePackageCreatedAt;basePackageSourceSha256=source.basePackageSourceSha256;
        basePackageManifestJson=source.basePackageManifestJson;basePackageObservedSha256=source.basePackageObservedSha256;basePackageShaEvidence=source.basePackageShaEvidence;
        compositionModified=source.compositionModified;packageSizeBytes=source.packageSizeBytes;
    }
    public ResearchExecutionConfig withCameraSetup(com.example.alpr_v1.camera.CameraSetupSnapshot setup){
        if(!"roi_budget".equals(experimentType))throw new IllegalStateException("Camera setup is F5A-only");
        if(tvBboxCrop!=null&&!tvBboxCrop.cameraSetup.same(setup))throw new IllegalStateException("TV_BBOX_CAMERA_SETUP_MISMATCH");
        return new ResearchExecutionConfig(this,java.util.Objects.requireNonNull(setup));
    }

    public static void requireUnchangedWhileRunning(ResearchExecutionConfig current, ResearchExecutionConfig replacement, boolean running) {
        if (running && current != null && replacement != current)
            throw new IllegalStateException("RESEARCH_CONFIG_FROZEN: zakończ sesję przed zmianą konfiguracji");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("experiment_type", experimentType);
        json.put("analysis_mode", sceneHandlingMode.analysisMode());
        json.put("scene_handling_mode", sceneHandlingMode.wireName());
        json.put("variant", variant);
        json.put("roi_budget_policy", roiBudgetPolicy.wireName());
        JSONObject dynamicMt = new JSONObject();
        dynamicMt.put("policy", com.example.alpr_v1.acquisition.DynamicMtConfig.POLICY);
        dynamicMt.put("local_margin_x", com.example.alpr_v1.acquisition.DynamicMtConfig.LOCAL_MARGIN_X);
        dynamicMt.put("local_margin_y", com.example.alpr_v1.acquisition.DynamicMtConfig.LOCAL_MARGIN_Y);
        dynamicMt.put("local_max_age_ms", com.example.alpr_v1.acquisition.DynamicMtConfig.LOCAL_MAX_AGE_NANOS / 1_000_000L);
        dynamicMt.put("primary_margin_x", com.example.alpr_v1.acquisition.DynamicMtConfig.PRIMARY_MARGIN_X);
        dynamicMt.put("primary_margin_bottom", com.example.alpr_v1.acquisition.DynamicMtConfig.PRIMARY_MARGIN_BOTTOM);
        dynamicMt.put("enter_width_px", dynamicMtConfig.size.enterWidth);
        dynamicMt.put("enter_height_px", dynamicMtConfig.size.enterHeight);
        dynamicMt.put("keep_width_px", dynamicMtConfig.size.keepWidth);
        dynamicMt.put("keep_height_px", dynamicMtConfig.size.keepHeight);
        dynamicMt.put("primary_plate_region_top_fraction", dynamicMtConfig.primaryTopFraction);
        json.put("dynamic_mt", dynamicMt);
        json.put("recognition_profile", recognitionProfile.wireName());
        json.put("camera_requested_resolution", cameraRequestedResolution);
        if(cameraSetup!=null)json.put("camera_setup",cameraSetup.toJson());
        if(tvBboxCrop!=null)json.put("input_mode",inputMode().name()).put("tv_bbox_crop",tvBboxCrop.toJson());
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
        if (!basePackageId.isEmpty()) json.put("composition", compositionJson());
        json.put("package_size_bytes", packageSizeBytes);
        return json;
    }

    public JSONObject modelRefsJson() throws JSONException {
        JSONObject refs = new JSONObject();
        if (vehicle.enabled) refs.put("vehicle", vehicle.modelRef.toJson());
        refs.put("plate", plate.modelRef.toJson());
        refs.put("character", character.modelRef.toJson());
        return refs;
    }

    public JSONObject compositionJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("base_package_id", basePackageId);
        json.put("base_package_fingerprint", basePackageFingerprint);
        json.put("base_package_sha256", basePackageSourceSha256);
        json.put("base_package_sha256_observed", basePackageObservedSha256.isEmpty() ? JSONObject.NULL : basePackageObservedSha256);
        json.put("base_package_sha_evidence", basePackageShaEvidence);
        json.put("modified", compositionModified);
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
