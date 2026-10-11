package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

public final class RuntimeBackendFactory {
    private RuntimeBackendFactory() {}

    public static boolean isRuntimeAvailable(ModelRuntime runtime) {
        return runtime == ModelRuntime.TFLITE
                || runtime == ModelRuntime.ONNX
                || (runtime == ModelRuntime.NCNN && NcnnBackend.isAvailable());
    }

    public static String unavailableReason(ModelRuntime runtime) {
        switch (runtime) {
            case NCNN:
                return NcnnBackend.unavailableReason();
            default:
                return "Runtime jest dostępny";
        }
    }

    public static InferenceBackend create(
            InstalledModel model,
            ModelVariant variant,
            ExecutionProfile profile
    ) {
        return create(model, variant, profile, RuntimeEvidenceSink.NONE);
    }

    public static InferenceBackend create(InstalledModel model, ModelVariant variant,
                                           ExecutionProfile profile, RuntimeEvidenceSink sink) {
        if (variant.runtime() != profile.runtime || profile.gpu && variant.runtime() != ModelRuntime.TFLITE) {
            new RuntimeExecutionEvidence(model, variant, profile, sink).failure("PROFILE",
                    new IllegalArgumentException("Profil i wariant używają różnych runtime'ów"));
            throw new IllegalArgumentException("Profil i wariant używają różnych runtime'ów");
        }
        if (variant.runtime() == ModelRuntime.TFLITE) {
            return new TfliteBackend(model, variant, profile, sink);
        }
        RuntimeExecutionEvidence evidence = new RuntimeExecutionEvidence(model, variant, profile, sink);
        try {
            evidence.captureArtifactFiles();
            InferenceBackend backend;
            if (variant.runtime() == ModelRuntime.ONNX) backend = new OnnxBackend(model, variant, profile);
            else if (variant.runtime() == ModelRuntime.NCNN) backend = new NcnnBackend(model, variant, profile);
            else throw new UnsupportedOperationException(unavailableReason(variant.runtime()));
            evidence.put("backend_effective", backend.runtimeName());
            evidence.put("backend_effective_scope", "OPENED_BACKEND_NOT_OPERATOR_PLACEMENT");
            evidence.put("backend_effective_reason", "BACKEND_CREATED");
            evidence.put("runtime_version_reason", "NON_LITERT_PROVIDER_NOT_INSTRUMENTED");
            evidence.put("xnnpack_effective", "NOT_APPLICABLE"); evidence.put("delegate_effective", "NOT_APPLICABLE");
            evidence.tensorInfo(java.util.Collections.singletonList(backend.inputInfo()), java.util.Collections.emptyList(), java.util.Collections.emptyList());
            evidence.put("io_evidence_reason", "NON_LITERT_OUTPUTS_CAPTURED_AFTER_INVOKE");
            evidence.emit("ALLOCATED");
            return new InferenceBackend() {
                private boolean first = true;
                public int inputByteSize() { return backend.inputByteSize(); }
                public TensorInfo inputInfo() { return backend.inputInfo(); }
                public String runtimeName() { return backend.runtimeName(); }
                public org.json.JSONObject executionEvidence() { return evidence.snapshot(); }
                public InferenceRunResult run(java.nio.ByteBuffer input) {
                    try {
                        InferenceRunResult result = backend.run(input);
                        if (first) {
                            java.util.List<Integer> sizes = new java.util.ArrayList<>();
                            for (java.nio.ByteBuffer output : result.outputs().values()) sizes.add(output.capacity());
                            evidence.tensorInfo(java.util.Collections.singletonList(inputInfo()), result.tensorInfo().values(), sizes);
                            evidence.put("input_buffer_capacity", input.capacity()); evidence.put("invoke_observed", true);
                            evidence.emit("FIRST_INVOKE_COMPLETED"); first = false;
                        }
                        return result;
                    } catch (RuntimeException | LinkageError error) { evidence.failure("INVOKE", error); throw error; }
                }
                public void close() { backend.close(); }
            };
        } catch (Exception | LinkageError error) {
            evidence.failure("INITIALIZATION", error);
            if (error instanceof RuntimeException) throw (RuntimeException) error;
            throw new IllegalStateException("Nie otwarto wymaganego runtime; bez fallbacku", error);
        }
    }
}
