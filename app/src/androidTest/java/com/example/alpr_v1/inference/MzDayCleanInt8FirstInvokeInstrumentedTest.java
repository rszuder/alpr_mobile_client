package com.example.alpr_v1.inference;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelManifest;
import com.example.alpr_v1.model.ModelVariant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.InterpreterApi;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.TensorFlowLite;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * One real LiteRT CPU invoke of the exact frozen MZ-DAY CLEAN model on Android.
 * RESEARCH ONLY: direct instrumented Interpreter, NOT production TfliteBackend,
 * NOT a deployed/approved .alprmodel, NOT an ALPR E2E or FP32/INT8 experiment.
 * Production backend T2-I-3 fail-closed guard remains unchanged.
 *
 * Host stages the verified model at:
 * /data/user/0/com.example.alpr_v1/files/f4_mz_day_int8_first_invoke/model.tflite
 * (the existing /Download/Ecper is only an exchange/staging area).
 */
@RunWith(AndroidJUnit4.class)
public final class MzDayCleanInt8FirstInvokeInstrumentedTest {
    private static final String MODEL_SHA =
            "a2f715399bdcb574da899e202388b5324d66f574b8519626cb611bf0e263e792";
    private static final long MODEL_BYTES = 10255456L;
    private static final String DIR = "f4_mz_day_int8_first_invoke";
    private static final String MODEL_FILE = "model.tflite";
    private static final String REPORT_FILE = "first_invoke_evidence.json";

    @Test public void pinnedCleanInt8InvokesExactlyOnceOnCpu() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir = new File(target.getFilesDir(), DIR);
        File modelFile = new File(dir, MODEL_FILE);
        assertTrue("Host must stage pinned model in target-app private files dir", modelFile.isFile());
        assertEquals("Unexpected frozen MZ model length", MODEL_BYTES, modelFile.length());
        assertEquals("Frozen MZ bytes differ", MODEL_SHA, RuntimeExecutionEvidence.sha256(modelFile));

        JSONObject inputSpec = new JSONObject()
                .put("width", 320).put("height", 320).put("channels", 3)
                .put("layout", "NHWC").put("color", "RGB")
                .put("data_type", "INT8")
                .put("resize_mode", "letterbox").put("padding_value", 114)
                .put("interpolation", "bilinear")
                .put("quantization", new JSONObject()
                        .put("scale", 0.003921568859368563).put("zero_point", -128));
        JSONObject outputSpec = new JSONObject()
                .put("decoder", "ultralytics_detect_split_ltrb_scores_int8_v1")
                .put("output_format", "raw_split_ltrb_scores_v1")
                .put("box_format", "ltrb").put("tensor_layout", "channels_first")
                .put("class_count", 36).put("keypoint_count", 0)
                .put("has_objectness", false).put("normalized_coordinates", true)
                .put("nms_in_graph", false).put("nms_required", false)
                .put("confidence_threshold", 0.25).put("iou_threshold", 0.45);
        JSONArray labels = new JSONArray();
        for (int i = 0; i < 10; i++) labels.put(Integer.toString(i));
        for (char c = 'A'; c <= 'Z'; c++) labels.put(Character.toString(c));
        JSONObject variant = new JSONObject()
                .put("id", "mz-day-clean-int8-first-invoke")
                .put("runtime", "tflite").put("precision", "int8")
                .put("file", MODEL_FILE)
                .put("sha256", new JSONObject().put(MODEL_FILE, MODEL_SHA));
        JSONObject manifest = new JSONObject()
                .put("schema", ModelManifest.SCHEMA)
                .put("model_id", "mz-day-clean-int8-research")
                .put("role", "character").put("task", "detect")
                .put("input", inputSpec).put("output", outputSpec)
                .put("labels", labels).put("variants", new JSONArray().put(variant));
        InstalledModel installed = new InstalledModel(ModelManifest.parse(manifest.toString()),
                dir, "mz-day-clean-int8-sha-pinned");
        ModelVariant selected = installed.manifest().variants().get(0);
        List<JSONObject> events = new ArrayList<>();
        RuntimeExecutionEvidence evidence = new RuntimeExecutionEvidence(
                installed, selected, ExecutionProfile.tfliteCpu(1), events::add);
        evidence.put("test_scope", "FIRST_INVOKE_RESEARCH_ONLY");
        evidence.put("execution_path", "DIRECT_INSTRUMENTED_LITERT_CPU_NOT_PRODUCTION_BACKEND");
        evidence.put("device_model", Build.MODEL);
        evidence.put("device_sdk", Build.VERSION.SDK_INT);
        evidence.put("model_source", "EXISTING_FROZEN_MZ_DAY_CLEAN_SHA_PINNED");
        evidence.put("source_exchange_directory", "/storage/emulated/0/Download/Ecper");
        evidence.put("ground_truth_scoring", "NOT_PERFORMED");
        evidence.put("latency_benchmark", "NOT_PERFORMED");
        evidence.put("full_int8_kernel_execution_proof", "NOT_AVAILABLE_FROM_PUBLIC_JAVA_API");

        Interpreter interpreter = null;
        try {
            // Reuse the existing immutable hashed-buffer and evidence machinery.
            ByteBuffer modelBytes = evidence.loadModel();
            Interpreter.Options opts = new Interpreter.Options();
            opts.setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY);
            opts.setNumThreads(1);
            opts.setUseNNAPI(false);
            opts.setUseXNNPACK(true);
            opts.setAllowFp16PrecisionForFp32(false);
            evidence.put("runtime_source", "FROM_APPLICATION_ONLY");
            evidence.put("runtime_version", TensorFlowLite.runtimeVersion(
                    InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY));
            evidence.put("runtime_version_reason", "OBSERVED_NATIVE_RUNTIME");
            evidence.put("cpu_threads_option_accepted", 1);
            evidence.put("nnapi_option_accepted", false);
            evidence.put("xnnpack_option_accepted", true);
            evidence.put("allow_fp16_precision_for_fp32", false);
            interpreter = new Interpreter(modelBytes, opts);
            interpreter.allocateTensors();
            assertEquals("Exactly one MZ input expected", 1, interpreter.getInputTensorCount());
            assertEquals("Exactly two MZ output ports expected", 2, interpreter.getOutputTensorCount());

            TensorInfo input = tensorInfo(0, interpreter.getInputTensor(0));
            Map<Integer, TensorInfo> outputs = new LinkedHashMap<>();
            outputs.put(0, tensorInfo(0, interpreter.getOutputTensor(0)));
            outputs.put(1, tensorInfo(1, interpreter.getOutputTensor(1)));
            MzSplitLtrbScoresInt8TensorValidator.validate(input, outputs);

            Map<Integer, Object> bufferMap = new LinkedHashMap<>();
            List<Integer> capacities = new ArrayList<>();
            for (int port : new int[]{0, 1}) {
                int byteSize = outputs.get(port).byteSize;
                bufferMap.put(port, ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder()));
                capacities.add(byteSize);
            }
            evidence.tensorInfo(Collections.singletonList(input), outputs.values(), capacities);
            evidence.put("contract_validation", "MATCH");
            evidence.put("contract_validation_scope", "T2_I_2_PINNED_TWO_OUTPUT_PORTS_AND_QUANTIZERS");
            evidence.put("backend_effective", "LiteRT/CPU");
            evidence.put("backend_effective_scope", "INTERPRETER_CONFIGURATION_NOT_OPERATOR_PLACEMENT");
            evidence.put("backend_effective_reason", "DIRECT_CPU_INTERPRETER_INITIALIZED");
            evidence.emit("ALLOCATED");

            // Deterministic uniform RGB=114 input; no camera/GT and no quality claim.
            int q114 = Math.round((114f / 255f) / input.quantizationScale)
                    + input.quantizationZeroPoint;
            assertTrue(q114 >= -128 && q114 <= 127);
            ByteBuffer pixels = ByteBuffer.allocateDirect(input.byteSize).order(ByteOrder.nativeOrder());
            for (int i = 0; i < input.byteSize; i++) pixels.put((byte) q114);
            pixels.rewind();
            evidence.put("synthetic_rgb_value", 114);
            evidence.put("synthetic_int8_pixel", q114);
            evidence.put("input_buffer_capacity", pixels.capacity());
            evidence.put("input_buffer_limit", pixels.limit());
            evidence.put("invocation_count_at_observation", 0);
            evidence.put("invoke_observed", false);
            interpreter.runForMultipleInputsOutputs(new Object[]{pixels}, bufferMap);

            // Fail closed on changed descriptors after invoke.
            TensorInfo finalInput = tensorInfo(0, interpreter.getInputTensor(0));
            Map<Integer, TensorInfo> finalOutputs = new LinkedHashMap<>();
            finalOutputs.put(0, tensorInfo(0, interpreter.getOutputTensor(0)));
            finalOutputs.put(1, tensorInfo(1, interpreter.getOutputTensor(1)));
            MzSplitLtrbScoresInt8TensorValidator.validate(finalInput, finalOutputs);
            assertTrue(Arrays.equals(input.shape, finalInput.shape));
            for (int port : new int[]{0, 1}) {
                assertTrue(Arrays.equals(outputs.get(port).shape, finalOutputs.get(port).shape));
                ByteBuffer observed = ((ByteBuffer) bufferMap.get(port)).asReadOnlyBuffer();
                observed.rewind();
                assertEquals(outputs.get(port).byteSize, observed.remaining());
                evidence.put("output_port_" + port + "_sha256",
                        RuntimeExecutionEvidence.sha256(observed));
            }
            evidence.put("invocation_count_at_observation", 1);
            evidence.put("invoke_observed", true);
            evidence.emit("FIRST_INVOKE_COMPLETED");
            assertEquals(2, events.size());
            assertEquals("ALLOCATED", events.get(0).getString("phase"));
            assertEquals("FIRST_INVOKE_COMPLETED", events.get(1).getString("phase"));
            assertEquals(MODEL_SHA, events.get(1).getString("model_sha256"));
            assertEquals(1, events.get(1).getInt("invocation_count_at_observation"));
            assertFalse(events.get(1).getBoolean("int8_execution_proven"));

            JSONObject result = new JSONObject()
                    .put("schema", "alpr.f4.mz_int8_first_invoke_research.v1")
                    .put("status", "FIRST_INVOKE_RESEARCH_ONLY")
                    .put("scientific_f4_status", "NOT_RUN")
                    .put("model_sha256", MODEL_SHA)
                    .put("model_byte_size", MODEL_BYTES)
                    .put("device_model", Build.MODEL)
                    .put("android_api", Build.VERSION.SDK_INT)
                    .put("events", new JSONArray(events));
            // Bind the device evidence to the actual installed app/test APKs where readable.
            try {
                File baseApk = new File(target.getApplicationInfo().sourceDir);
                File testApk = new File(InstrumentationRegistry.getInstrumentation()
                        .getContext().getApplicationInfo().sourceDir);
                result.put("app_apk_sha256", RuntimeExecutionEvidence.sha256(baseApk));
                result.put("test_apk_sha256", RuntimeExecutionEvidence.sha256(testApk));
                result.put("apk_identity_status", "ACTUAL_INSTALLED_APK_BYTES_HASHED");
            } catch (Exception apkError) {
                result.put("apk_identity_status", "UNAVAILABLE_" + apkError.getClass().getSimpleName());
            }
            byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(new File(dir, REPORT_FILE).toPath(), bytes);
            JSONObject completion = new JSONObject()
                    .put("schema", "alpr.f4.mz_int8_first_invoke_completion.v1")
                    .put("model_sha256", MODEL_SHA)
                    .put("report_filename", REPORT_FILE)
                    .put("report_sha256", RuntimeExecutionEvidence.sha256(ByteBuffer.wrap(bytes)))
                    .put("report_bytes", bytes.length)
                    .put("scope", "FIRST_INVOKE_RESEARCH_ONLY_NOT_F4_COMPLETE");
            Bundle frame = new Bundle();
            frame.putString("f4_mz_first_invoke_complete", completion.toString());
            InstrumentationRegistry.getInstrumentation().sendStatus(2, frame);
        } catch (Exception | LinkageError error) {
            evidence.failure("FIRST_INVOKE_RESEARCH", error);
            // Preserve failures in the private app files for later ADB inspection.
            JSONObject failed = new JSONObject().put("schema", "alpr.f4.mz_int8_first_invoke_research.v1")
                    .put("status", "FIRST_INVOKE_FAILED_NOT_F4")
                    .put("model_sha256_expected", MODEL_SHA)
                    .put("events", new JSONArray(events));
            Files.write(new File(dir, REPORT_FILE).toPath(),
                    failed.toString().getBytes(StandardCharsets.UTF_8));
            throw error;
        } finally {
            if (interpreter != null) interpreter.close();
        }
    }

    private static TensorInfo tensorInfo(int port, Tensor tensor) {
        Tensor.QuantizationParams quant = tensor.quantizationParams();
        return new TensorInfo(port, tensor.shape(), tensor.dataType().toString(),
                tensor.numBytes(), quant.getScale(), quant.getZeroPoint());
    }
}
