package com.example.alpr_v1.pipeline;

/** Immutable description of a model whose backend was actually opened. */
public final class ModelRuntimeSummary {
    public final String stage, modelName, variantId, precision, backend;

    public ModelRuntimeSummary(String stage, String modelName, String variantId,
            String precision, String backend) {
        this.stage = stage;
        this.modelName = modelName;
        this.variantId = variantId;
        this.precision = precision;
        this.backend = backend;
    }

    public String compactLabel() {
        return stage + " · " + variantId + " · " + precision + " · "
                + backend.replace("ONNX Runtime", "ONNX");
    }

    public String description() { return modelName + " · " + compactLabel(); }
}
