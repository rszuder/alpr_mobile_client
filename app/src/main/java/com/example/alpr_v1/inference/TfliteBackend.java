package com.example.alpr_v1.inference;

import com.example.alpr_v1.BuildConfig;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelVariant;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.InterpreterApi;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.TensorFlowLite;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TfliteBackend implements InferenceBackend {
    interface Driver extends AutoCloseable {
        List<TensorInfo> inputs();
        Map<Integer, TensorInfo> outputs();
        void run(ByteBuffer input, Map<Integer, ByteBuffer> outputs);
        void close();
    }
    interface DriverFactory {
        Driver open(ByteBuffer model, ExecutionProfile profile, RuntimeExecutionEvidence evidence);
    }

    private final Driver driver;
    private final TensorInfo inputInfo;
    private final Map<Integer, TensorInfo> outputInfo;
    private final Map<Integer, ByteBuffer> outputBuffers = new LinkedHashMap<>();
    private final RuntimeExecutionEvidence evidence;
    private final InstalledModel contractModel;
    private final ModelVariant contractVariant;
    private final boolean gpu;
    private final List<TensorInfo> frozenInputs, frozenOutputs;
    // Keep the exact hashed private model bytes alive for the native interpreter.
    private final ByteBuffer modelBytes;
    private long invocationCount;

    public TfliteBackend(InstalledModel model, ModelVariant variant, ExecutionProfile profile) {
        this(model, variant, profile, RuntimeEvidenceSink.NONE);
    }
    public TfliteBackend(InstalledModel model, ModelVariant variant, ExecutionProfile profile, RuntimeEvidenceSink sink) {
        this(model, variant, profile, sink, NativeDriver::new);
    }
    TfliteBackend(InstalledModel model, ModelVariant variant, ExecutionProfile profile,
                  RuntimeEvidenceSink sink, DriverFactory factory) {
        evidence = new RuntimeExecutionEvidence(model, variant, profile, sink);
        contractModel = model; contractVariant = variant;
        gpu = profile.gpu;
        Driver opened = null;
        try {
            modelBytes = evidence.loadModel();
            ModelInputSpec inputSpec = variant.input(model.manifest().input());
            ModelOutputSpec outputSpec = variant.output(model.manifest().output());
            boolean mzRawSplit = "raw_split_ltrb_scores_v1".equals(outputSpec.outputFormat());
            if (mzRawSplit) {
                // T2-I-9: diagnostic access through the real backend, *only* for
                // the pinned CLEAN artifact in a debug CPU build. No release,
                // model substitution, generic two-output or silent FP32 fallback.
                if (profile.runtime != variant.runtime()) {
                    throw new IllegalArgumentException("MZ_SPLIT_INT8_PROFILE_RUNTIME_MISMATCH");
                }
                MzDayCleanInt8RuntimeGate.requirePinnedArtifact(
                        RuntimeExecutionEvidence.sha256(modelBytes), modelBytes.remaining(),
                        BuildConfig.DEBUG, profile.gpu);
                MzDayCleanInt8RuntimeGate.requireManifest(model, variant, inputSpec, outputSpec);
                evidence.put("mz_split_int8_scope", "RESEARCH_DEBUG_CPU_PINNED_SHA_ONLY");
                evidence.put("mz_split_int8_model_approved", false);
                evidence.put("mz_split_int8_android_import_approved", false);
                evidence.put("mz_split_int8_final_test", "NOT_RUN");
                evidence.put("mz_split_int8_decode_path", "MOBILE_ALPR_ENGINE_DECODE_SPLIT_MZ");
            }
            opened = factory.open(modelBytes, profile, evidence);
            List<TensorInfo> inputs = opened.inputs();
            Map<Integer, TensorInfo> outputs = opened.outputs();
            evidence.tensorInfo(inputs, outputs.values(), Collections.emptyList());
            if (inputs.size() != 1 || outputs.isEmpty()) throw new IllegalArgumentException("ALPR wymaga jednego wejścia i co najmniej jednego wyjścia");
            inputInfo = inputs.get(0);
            outputInfo = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
            for (TensorInfo input : inputs) PrecisionEvidence.validateStorage(input);
            ModelTensorContractValidator.validateInput(inputSpec, inputInfo);
            for (Map.Entry<Integer, TensorInfo> entry : outputs.entrySet()) {
                PrecisionEvidence.validateStorage(entry.getValue());
                outputBuffers.put(entry.getKey(), ByteBuffer.allocateDirect(entry.getValue().byteSize).order(ByteOrder.nativeOrder()));
            }
            if (mzRawSplit) {
                // T2-I-9: validate both runtime ports, never first map value.
                // The pipeline dispatches this InferenceRunResult to decodeSplitMZ.
                MzSplitLtrbScoresInt8TensorValidator.validate(inputInfo, outputs);
            } else {
                ModelTensorContractValidator.validateOutput(outputSpec, outputs.values().iterator().next());
            }
            evidence.tensorInfo(inputs, outputs.values(), capacities());
            evidence.put("contract_validation", "MATCH");
            evidence.put("contract_validation_scope", mzRawSplit
                    ? "MZ_RESEARCH_PINNED_SHA; debug_cpu_only; input_tensor; both_int8_output_ports_and_quantizers"
                    : "input_shape_dtype_quantizer_when_declared; all_io_storage; primary_output_decoder_layout");
            evidence.put("output_quantizer_manifest_comparison", "NOT_DECLARED_BY_OUTPUT_SPEC");
            evidence.put("manifest_input", new org.json.JSONObject()
                    .put("width", inputSpec.width()).put("height", inputSpec.height())
                    .put("channels", inputSpec.channels()).put("layout", inputSpec.layout())
                    .put("dtype", inputSpec.dataType())
                    .put("quantization_scale", inputSpec.quantizationScale() == null ? org.json.JSONObject.NULL : inputSpec.quantizationScale())
                    .put("quantization_zero_point", inputSpec.quantizationScale() == null ? org.json.JSONObject.NULL : inputSpec.quantizationZeroPoint()));
            frozenInputs = copyInfo(inputs); frozenOutputs = copyInfo(outputs.values());
            driver = opened;
            evidence.emit("ALLOCATED");
        } catch (Exception | LinkageError error) {
            if (opened != null) opened.close();
            evidence.put("contract_validation", error instanceof IllegalArgumentException ? "MISMATCH" : "NOT_COMPLETED");
            evidence.failure("INITIALIZATION", error);
            if (error instanceof IllegalArgumentException)
                throw new RuntimeModelContractException(model, variant, error.getMessage(), error);
            throw new IllegalStateException("Nie otwarto wymaganego backendu; bez fallbacku: " + error.getMessage(), error);
        }
    }

    private List<Integer> capacities() {
        List<Integer> result = new ArrayList<>();
        for (ByteBuffer buffer : outputBuffers.values()) result.add(buffer.capacity());
        return result;
    }
    private static List<TensorInfo> copyInfo(java.util.Collection<TensorInfo> values) {
        List<TensorInfo> copy = new ArrayList<>();
        for (TensorInfo t : values) copy.add(new TensorInfo(t.index,t.shape,t.dataType,t.byteSize,t.quantizationScale,t.quantizationZeroPoint));
        return copy;
    }
    private static boolean sameInfo(List<TensorInfo> frozen, java.util.Collection<TensorInfo> observed) {
        if (frozen.size() != observed.size()) return false;
        int index = 0;
        for (TensorInfo t : observed) {
            TensorInfo expected = frozen.get(index++);
            if (t.index != expected.index || !java.util.Arrays.equals(t.shape,expected.shape)
                    || !t.dataType.equals(expected.dataType) || t.byteSize != expected.byteSize
                    || Float.floatToIntBits(t.quantizationScale) != Float.floatToIntBits(expected.quantizationScale)
                    || t.quantizationZeroPoint != expected.quantizationZeroPoint) return false;
        }
        return true;
    }
    @Override public int inputByteSize() { return inputInfo.byteSize; }
    @Override public TensorInfo inputInfo() { return inputInfo; }
    @Override public Map<Integer, TensorInfo> outputInfo() { return outputInfo; }
    @Override public org.json.JSONObject executionEvidence() { return evidence.snapshot(); }

    @Override public synchronized InferenceRunResult run(ByteBuffer input) {
        try {
            input.rewind();
            evidence.put("input_buffer_capacity", input.capacity()); evidence.put("input_buffer_limit", input.limit());
            requireStableTensors();
            if (!input.isDirect() || input.order() != ByteOrder.nativeOrder()
                    || input.capacity() != inputInfo.byteSize || input.remaining() != inputInfo.byteSize)
                throw new IllegalArgumentException("Rzeczywisty bufor wejścia nie odpowiada TensorInfo");
            for (ByteBuffer buffer : outputBuffers.values()) buffer.rewind();
            driver.run(input, outputBuffers);
            invocationCount++;
            requireStableTensors();
            for (ByteBuffer buffer : outputBuffers.values()) buffer.rewind();
            if (invocationCount == 1) {
                evidence.put("invocation_count_at_observation", invocationCount);
                evidence.put("invoke_observed", true);
                evidence.emit("FIRST_INVOKE_COMPLETED");
            }
            return new InferenceRunResult(outputBuffers, outputInfo);
        } catch (RuntimeException | LinkageError error) {
            evidence.put("invocation_count_at_observation", invocationCount);
            evidence.failure("INVOKE", error);
            if (error instanceof IllegalArgumentException)
                throw new RuntimeModelContractException(contractModel,contractVariant,error.getMessage(),error);
            throw new IllegalStateException("Błąd wykonania zatwierdzonego wariantu; bez fallbacku", error);
        }
    }
    private void requireStableTensors() {
        List<TensorInfo> inputs = driver.inputs();
        Map<Integer,TensorInfo> outputs = driver.outputs();
        if (!sameInfo(frozenInputs,inputs) || !sameInfo(frozenOutputs,outputs.values())) {
            evidence.tensorInfo(inputs,outputs.values(),capacities());
            evidence.put("contract_validation","RUNTIME_TENSOR_CHANGED");
            throw new IllegalArgumentException("Parametry tensorów zmieniły się względem zatwierdzonego backendu");
        }
    }
    @Override public String runtimeName() { return gpu ? "LiteRT/GPU" : "LiteRT/CPU"; }
    @Override public synchronized void close() { driver.close(); }

    private static final class NativeDriver implements Driver {
        private final Interpreter interpreter;
        private final GpuDelegate gpuDelegate;
        NativeDriver(ByteBuffer model, ExecutionProfile profile, RuntimeExecutionEvidence evidence) {
            Interpreter.Options options = new Interpreter.Options();
            options.setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY);
            options.setNumThreads(profile.cpuThreads);
            options.setUseNNAPI(false);
            options.setUseXNNPACK(!profile.gpu);
            options.setAllowFp16PrecisionForFp32(false);
            evidence.put("initialization_step", "RUNTIME_LOAD");
            evidence.put("runtime_dependency_version", BuildConfig.LITERT_RUNTIME_ARTIFACT);
            evidence.put("gpu_dependency_version", BuildConfig.LITERT_GPU_ARTIFACT);
            evidence.put("runtime_version", TensorFlowLite.runtimeVersion(InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY));
            evidence.put("runtime_version_reason", "OBSERVED_NATIVE_RUNTIME");
            evidence.put("runtime_schema_version", TensorFlowLite.schemaVersion(InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY));
            evidence.put("runtime_source", options.getRuntime().name());
            evidence.put("cpu_threads_option_accepted", options.getNumThreads());
            evidence.put("xnnpack_option_accepted", options.getUseXNNPACK());
            evidence.put("nnapi_option_accepted", options.getUseNNAPI());
            evidence.put("allow_fp16_precision_for_fp32", false);
            if (profile.gpu) {
                evidence.put("xnnpack_effective", "DISABLED_BY_OPTIONS");
                evidence.put("xnnpack_effective_reason", "EXPLICIT_GPU_PROFILE_POLICY");
            }
            GpuDelegate created = null;
            Interpreter opened = null;
            try {
                if (profile.gpu) {
                    evidence.put("initialization_step", "GPU_COMPATIBILITY");
                    CompatibilityList compatibility = new CompatibilityList();
                    try {
                        boolean supported = compatibility.isDelegateSupportedOnThisDevice();
                        evidence.put("gpu_compatibility_supported", supported);
                        if (!supported) throw new IllegalStateException("GPU_DELEGATE_UNAVAILABLE");
                        GpuDelegate.Options gpuOptions = compatibility.getBestOptionsForThisDevice();
                        gpuOptions.setPrecisionLossAllowed(false);
                        evidence.put("gpu_precision_loss_allowed", gpuOptions.isPrecisionLossAllowed());
                        evidence.put("gpu_quantized_models_allowed", gpuOptions.areQuantizedModelsAllowed());
                        evidence.put("gpu_inference_preference", gpuOptions.getInferencePreference());
                        evidence.put("gpu_force_backend", String.valueOf(gpuOptions.getForceBackend()));
                        evidence.put("initialization_step", "GPU_DELEGATE_CREATE");
                        created = new GpuDelegate(gpuOptions);
                        options.addDelegate(created);
                        evidence.put("gpu_delegate_created", true);
                    } finally { compatibility.close(); }
                }
                evidence.put("initialization_step", "INTERPRETER_CREATE_AND_ALLOCATE");
                opened = new Interpreter(model, options);
                opened.allocateTensors();
                evidence.put("interpreter_created", true);
                evidence.put("backend_effective", profile.gpu ? "UNVERIFIED" : "LiteRT/CPU");
                evidence.put("backend_effective_scope", "INTERPRETER_CONFIGURATION_NOT_OPERATOR_PLACEMENT");
                evidence.put("backend_effective_reason", profile.gpu ? "GPU_PARTITION_AND_CPU_REMAINDER_NOT_EXPOSED" : "CPU_INTERPRETER_CREATED");
                interpreter = opened; gpuDelegate = created;
            } catch (RuntimeException | LinkageError error) {
                if (opened != null) opened.close();
                if (created != null) created.close();
                throw error;
            }
        }
        @Override public List<TensorInfo> inputs() {
            List<TensorInfo> result = new ArrayList<>();
            for (int i = 0; i < interpreter.getInputTensorCount(); i++) result.add(toInfo(i, interpreter.getInputTensor(i)));
            return result;
        }
        @Override public Map<Integer, TensorInfo> outputs() {
            Map<Integer, TensorInfo> result = new LinkedHashMap<>();
            for (int i = 0; i < interpreter.getOutputTensorCount(); i++) result.put(i, toInfo(i, interpreter.getOutputTensor(i)));
            return result;
        }
        @Override public void run(ByteBuffer input, Map<Integer, ByteBuffer> buffers) {
            Map<Integer, Object> outputs = new LinkedHashMap<>(); outputs.putAll(buffers);
            interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);
        }
        @Override public void close() { interpreter.close(); if (gpuDelegate != null) gpuDelegate.close(); }
    }
    private static TensorInfo toInfo(int index, Tensor tensor) {
        Tensor.QuantizationParams q = tensor.quantizationParams();
        return new TensorInfo(index, tensor.shape(), tensor.dataType().toString(), tensor.numBytes(), q.getScale(), q.getZeroPoint());
    }
}
