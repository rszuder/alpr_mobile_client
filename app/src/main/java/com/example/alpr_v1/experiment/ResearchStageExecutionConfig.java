package com.example.alpr_v1.experiment;

import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelRefResolver;
import com.example.alpr_v1.model.ModelRefSnapshot;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

import org.json.JSONException;
import org.json.JSONObject;

/** Niezmienna, efektywna konfiguracja pojedynczego etapu MP/MT/MZ. */
public final class ResearchStageExecutionConfig {
    public final ModelRole role;
    public final boolean enabled;
    public final String modelId;
    public final String modelFingerprint;
    public final String variantId;
    public final ModelRuntime runtime;
    public final String precision;
    public final int cpuThreads;
    public final boolean gpu;
    public final String delegate;
    public final int inputWidth;
    public final int inputHeight;
    public final int inputChannels;
    public final String inputLayout;
    public final String inputColorSpace;
    public final String inputDataType;
    public final ModelRefSnapshot modelRef;

    ResearchStageExecutionConfig(
            ModelRole role,
            boolean enabled,
            String modelId,
            String modelFingerprint,
            String variantId,
            ModelRuntime runtime,
            String precision,
            int cpuThreads,
            boolean gpu,
            String delegate,
            int inputWidth,
            int inputHeight,
            int inputChannels,
            String inputLayout,
            String inputColorSpace,
            String inputDataType
    ) {
        this(
                role, enabled, modelId, modelFingerprint, variantId, runtime, precision,
                cpuThreads, gpu, delegate, inputWidth, inputHeight, inputChannels,
                inputLayout, inputColorSpace, inputDataType,
                enabled ? ModelRefSnapshot.legacy(role, modelId, modelFingerprint) : null
        );
    }

    ResearchStageExecutionConfig(
            ModelRole role,
            boolean enabled,
            String modelId,
            String modelFingerprint,
            String variantId,
            ModelRuntime runtime,
            String precision,
            int cpuThreads,
            boolean gpu,
            String delegate,
            int inputWidth,
            int inputHeight,
            int inputChannels,
            String inputLayout,
            String inputColorSpace,
            String inputDataType,
            ModelRefSnapshot modelRef
    ) {
        this.role = role;
        this.enabled = enabled;
        this.modelId = safe(modelId);
        this.modelFingerprint = safe(modelFingerprint);
        this.variantId = safe(variantId);
        this.runtime = runtime;
        this.precision = safe(precision);
        this.cpuThreads = Math.max(0, cpuThreads);
        this.gpu = gpu;
        this.delegate = safe(delegate);
        this.inputWidth = Math.max(0, inputWidth);
        this.inputHeight = Math.max(0, inputHeight);
        this.inputChannels = Math.max(0, inputChannels);
        this.inputLayout = safe(inputLayout);
        this.inputColorSpace = safe(inputColorSpace);
        this.inputDataType = safe(inputDataType);
        this.modelRef = enabled ? requireModelRef(modelRef, role, this.modelId) : null;
    }

    public static ResearchStageExecutionConfig disabled(ModelRole role) {
        if (role == null) throw new IllegalArgumentException("role");
        return new ResearchStageExecutionConfig(
                role, false, "", "", "", null, "", 0,
                false, "none", 0, 0, 0, "", "", ""
        );
    }

    public static ResearchStageExecutionConfig enabled(
            ModelRole role,
            InstalledModel model,
            ModelVariant variant,
            ExecutionProfile profile
    ) {
        return enabled(role, model, variant, profile, null);
    }

    public static ResearchStageExecutionConfig enabled(
            ModelRole role,
            InstalledModel model,
            ModelVariant variant,
            ExecutionProfile profile,
            ModelRegistry registry
    ) {
        if (role == null || model == null || variant == null || profile == null) {
            throw new IllegalArgumentException("Niepełna konfiguracja etapu badawczego");
        }
        if (model.manifest().role() != role) {
            throw new IllegalArgumentException("Model nie pasuje do etapu " + role.wireName());
        }
        if (variant.runtime() != profile.runtime) {
            throw new IllegalArgumentException("Wariant i profil mają różne runtime");
        }
        if (profile.gpu && profile.runtime != ModelRuntime.TFLITE) {
            throw new IllegalArgumentException("GPU jest obsługiwane wyłącznie przez TFLite");
        }
        ModelInputSpec input = variant.input(model.manifest().input());
        return new ResearchStageExecutionConfig(
                role,
                true,
                model.manifest().modelId(),
                model.fingerprint(),
                variant.id(),
                variant.runtime(),
                variant.precision(),
                profile.cpuThreads,
                profile.gpu,
                profile.gpu ? "gpu" : "cpu",
                input.width(),
                input.height(),
                input.channels(),
                input.layout(),
                input.colorSpace(),
                input.dataType(),
                ModelRefResolver.resolve(registry, role, model, variant)
        );
    }

    public ModelVariant requireVariant(InstalledModel activeModel) {
        if (!enabled) return null;
        if (activeModel == null
                || activeModel.manifest().role() != role
                || !modelId.equals(activeModel.manifest().modelId())
                || !modelFingerprint.equals(activeModel.fingerprint())) {
            throw new IllegalStateException(
                    "Aktywny model " + role.wireName()
                            + " nie odpowiada zamrożonej konfiguracji"
            );
        }
        for (ModelVariant variant : activeModel.manifest().variants()) {
            if (variantId.equals(variant.id()) && runtime == variant.runtime()) {
                return variant;
            }
        }
        throw new IllegalStateException(
                "Brak zamrożonego wariantu " + variantId
                        + " dla etapu " + role.wireName()
        );
    }

    public ExecutionProfile executionProfile() {
        if (!enabled || runtime == null) {
            throw new IllegalStateException("Etap " + role.wireName() + " jest wyłączony");
        }
        return new ExecutionProfile(runtime, Math.max(1, cpuThreads), gpu);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("role", role.wireName());
        json.put("enabled", enabled);
        if (!enabled) return json;
        json.put("model_id", modelId);
        json.put("model_fingerprint", modelFingerprint);
        json.put("fingerprint", modelFingerprint);
        json.put("variant_id", variantId);
        json.put("runtime", runtime.wireName());
        json.put("precision", precision);
        json.put("cpu_threads", cpuThreads);
        json.put("gpu", gpu);
        json.put("delegate", delegate);
        JSONObject input = new JSONObject();
        input.put("width", inputWidth);
        input.put("height", inputHeight);
        input.put("channels", inputChannels);
        input.put("layout", inputLayout);
        input.put("color", inputColorSpace);
        input.put("data_type", inputDataType);
        json.put("input", input);
        return json;
    }

    private static ModelRefSnapshot requireModelRef(
            ModelRefSnapshot modelRef,
            ModelRole role,
            String modelId
    ) {
        if (modelRef == null || modelRef.role() != role || !modelId.equals(modelRef.modelId())) {
            throw new IllegalArgumentException("Niezgodny modelRef etapu " + role.wireName());
        }
        return modelRef;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
