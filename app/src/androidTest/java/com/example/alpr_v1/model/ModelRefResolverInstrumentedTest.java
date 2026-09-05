package com.example.alpr_v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class ModelRefResolverInstrumentedTest {
    private static final String HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void partialTrainingAndNullRemainUnchanged() throws Exception {
        JSONObject training = new JSONObject()
                .put("run_epochs_completed", 10)
                .put("lineage_total_epochs", JSONObject.NULL)
                .put("lineage_total_epochs_known", false)
                .put("known_epochs_minimum", 10)
                .put("known_sample_presentations_minimum", 9000)
                .put("provenance_status", "partial");
        ModelManifest manifest = ModelManifest.parse(modelManifest(training).toString());
        InstalledModel installed = new InstalledModel(manifest, new File("unused"), "local-1234");
        ModelVariant variant = manifest.variants().get(0);

        ModelRefSnapshot snapshot = ModelRefResolver.resolve(
                null, ModelRole.PLATE, installed, variant
        );
        JSONObject json = snapshot.toJson();
        JSONObject parsedTraining = json.getJSONObject("training");

        assertEquals("local-1234", json.getString("local_manifest_fingerprint"));
        assertEquals(HASH, json.getString("checkpoint_sha256"));
        assertTrue(parsedTraining.isNull("lineage_total_epochs"));
        assertTrue(parsedTraining.isNull("total_epochs"));
        assertTrue(parsedTraining.isNull("run_nominal_sample_presentations"));
        assertFalse(parsedTraining.getBoolean("lineage_total_epochs_known"));
        assertEquals(10, parsedTraining.getInt("known_epochs_minimum"));
        assertEquals(9000, parsedTraining.getInt("known_sample_presentations_minimum"));
        assertEquals("partial", parsedTraining.getString("provenance_status"));
        assertEquals(HASH, json.getJSONArray("variant_artifact_sha256").getString(0));
    }

    private static JSONObject modelManifest(JSONObject training) throws Exception {
        return new JSONObject()
                .put("schema", ModelManifest.SCHEMA)
                .put("model_id", "plate-test")
                .put("name", "Plate test")
                .put("version", "1")
                .put("role", "plate")
                .put("task", "pose")
                .put("source", new JSONObject().put("checkpoint_sha256", HASH))
                .put("training", training)
                .put("input", new JSONObject()
                        .put("width", 640).put("height", 640).put("channels", 3)
                        .put("layout", "NHWC").put("color", "RGB")
                        .put("data_type", "FLOAT32").put("scale", 1.0 / 255.0)
                        .put("offset", 0.0))
                .put("output", new JSONObject()
                        .put("decoder", "ultralytics_pose_raw_v1")
                        .put("class_count", 1).put("keypoint_count", 4)
                        .put("has_objectness", false).put("tensor_layout", "channels_first")
                        .put("normalized_coordinates", false).put("nms_in_graph", false)
                        .put("confidence_threshold", 0.25).put("iou_threshold", 0.45))
                .put("labels", new JSONArray().put("plate"))
                .put("variants", new JSONArray().put(new JSONObject()
                        .put("id", "tflite-int8").put("runtime", "tflite")
                        .put("precision", "int8").put("file", "variants/model.tflite")
                        .put("sha256", new JSONObject().put("variants/model.tflite", HASH))));
    }
}
