package com.example.alpr_v1.inference;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelManifest;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Yolo26SplitLtrbScoresInt8Decoder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * T2-I-9: real debug-only production TfliteBackend + split decoder on the
 * already staged frozen model. Research proof, NOT approved import, F4, GT
 * evaluation or proof that native kernels execute wholly in INT8 arithmetic.
 * Do not run this automatically in the F4/final_test suite.
 */
@RunWith(AndroidJUnit4.class)
public final class MzDayCleanInt8BackendRouteInstrumentedTest {
    @Test public void frozenModelRunsThroughActualBackendAndSplitDecoder() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getFilesDir(), "f4_mz_day_int8_first_invoke");
        File artifact = new File(directory, "model.tflite");
        assertTrue("Run existing private ADB staging first; this test never copies model", artifact.isFile());
        assertEquals(MzDayCleanInt8RuntimeGate.MODEL_BYTES, artifact.length());
        assertEquals(MzDayCleanInt8RuntimeGate.MODEL_SHA256,
                RuntimeExecutionEvidence.sha256(artifact));

        JSONObject input = new JSONObject()
                .put("width", 320).put("height", 320).put("channels", 3)
                .put("layout", "NHWC").put("color", "RGB").put("data_type", "INT8")
                .put("resize_mode", "letterbox").put("padding_value", 114)
                .put("interpolation", "bilinear").put("scale", 1.0 / 255.0).put("offset", 0)
                .put("quantization", new JSONObject().put("scale", 0.003921568859368563)
                        .put("zero_point", -128));
        JSONObject output = new JSONObject()
                .put("decoder", "ultralytics_detect_split_ltrb_scores_int8_v1")
                .put("output_format", "raw_split_ltrb_scores_v1").put("box_format", "ltrb")
                .put("tensor_layout", "channels_first").put("class_count", 36)
                .put("keypoint_count", 0).put("has_objectness", false)
                .put("normalized_coordinates", true).put("nms_in_graph", false)
                .put("nms_required", false).put("confidence_threshold", 0.25)
                .put("iou_threshold", 0.45);
        JSONArray labels = new JSONArray();
        for (int i = 0; i < 10; i++) labels.put(Integer.toString(i));
        for (char c = 'A'; c <= 'Z'; c++) labels.put(Character.toString(c));
        JSONObject variant = new JSONObject()
                .put("id", "mz-day-clean-int8-t2i9-research")
                .put("runtime", "tflite").put("precision", "int8")
                .put("file", "model.tflite")
                .put("sha256", new JSONObject().put("model.tflite",
                        MzDayCleanInt8RuntimeGate.MODEL_SHA256));
        JSONObject manifestJson = new JSONObject()
                .put("schema", ModelManifest.SCHEMA)
                .put("model_id", "mz-day-clean-int8-t2i9-research")
                .put("role", "character").put("task", "detect")
                .put("input", input).put("output", output).put("labels", labels)
                .put("variants", new JSONArray().put(variant));
        InstalledModel model = new InstalledModel(ModelManifest.parse(manifestJson.toString()),
                directory, "mz-day-clean-int8-t2i9-research");
        ModelVariant selected = model.manifest().variants().get(0);
        try (TfliteBackend backend = new TfliteBackend(model, selected,
                ExecutionProfile.tfliteCpu(1))) {
            TensorInfo inputInfo = backend.inputInfo();
            Map<Integer, TensorInfo> tensorInfo = backend.outputInfo();
            MzSplitLtrbScoresInt8TensorValidator.validate(inputInfo, tensorInfo);
            int pixel = Math.round((114f / 255f) / inputInfo.quantizationScale)
                    + inputInfo.quantizationZeroPoint;
            assertTrue(pixel >= -128 && pixel <= 127);
            ByteBuffer sample = ByteBuffer.allocateDirect(inputInfo.byteSize)
                    .order(ByteOrder.nativeOrder());
            for (int i = 0; i < inputInfo.byteSize; i++) sample.put((byte) pixel);
            sample.rewind();
            InferenceRunResult result = backend.run(sample);
            assertEquals(2, result.outputs().size());
            assertTrue(result.outputs().containsKey(0));
            assertTrue(result.outputs().containsKey(1));
            TensorInfo boxes = result.tensorInfo().get(0);
            TensorInfo scores = result.tensorInfo().get(1);
            List<Detection> detections = Yolo26SplitLtrbScoresInt8Decoder.decode(
                    result.outputs().get(0), boxes.shape,
                    boxes.quantizationScale, boxes.quantizationZeroPoint,
                    result.outputs().get(1), scores.shape,
                    scores.quantizationScale, scores.quantizationZeroPoint,
                    .10f, true, true, .45f);
            assertNotNull(detections);
            assertTrue(detections.size() <= 300);
            for (Detection hit : detections) {
                assertTrue(hit.classId >= 0 && hit.classId < 36);
                assertTrue(Float.isFinite(hit.confidence));
            }
            // Exercise the already present production pipeline dispatcher too.
            // This is only a research method-level check, not UI/E2E approval.
            Method dispatch = Class.forName("com.example.alpr_v1.pipeline.MobileAlprEngine")
                    .getDeclaredMethod("decodeSplitMZ",
                    InferenceRunResult.class, ModelInputSpec.class, ModelOutputSpec.class, float.class);
            dispatch.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Detection> routed = (List<Detection>) dispatch.invoke(null, result,
                    selected.input(model.manifest().input()),
                    selected.output(model.manifest().output()), .10f);
            assertEquals(detections.size(), routed.size());
            for (int i = 0; i < detections.size(); i++) {
                Detection direct = detections.get(i), viaEngine = routed.get(i);
                assertEquals(direct.classId, viaEngine.classId);
                assertEquals(direct.confidence, viaEngine.confidence, 1e-6f);
                assertEquals(direct.left, viaEngine.left, 1e-5f);
                assertEquals(direct.top, viaEngine.top, 1e-5f);
                assertEquals(direct.right, viaEngine.right, 1e-5f);
                assertEquals(direct.bottom, viaEngine.bottom, 1e-5f);
            }
            JSONObject evidence = backend.executionEvidence();
            assertEquals("MATCH", evidence.optString("contract_validation"));
            assertEquals("RESEARCH_DEBUG_CPU_PINNED_SHA_ONLY",
                    evidence.optString("mz_split_int8_scope"));
            assertEquals(MzDayCleanInt8RuntimeGate.MODEL_SHA256,
                    evidence.optString("model_sha256"));
            assertTrue(evidence.optBoolean("invoke_observed", false));
            assertFalse(evidence.optBoolean("mz_split_int8_android_import_approved", true));
            assertFalse(evidence.optBoolean("int8_execution_proven", true));
            assertEquals("NOT_RUN", evidence.optString("mz_split_int8_final_test"));
        }
    }
}
