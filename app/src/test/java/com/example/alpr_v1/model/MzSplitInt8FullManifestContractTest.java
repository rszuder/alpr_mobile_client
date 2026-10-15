package com.example.alpr_v1.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** T2-I-6D: synthetic manifest tests; no actual model execution or packaging. */
public final class MzSplitInt8FullManifestContractTest {
    private static JSONArray labels() {
        JSONArray a = new JSONArray();
        for (int i = 0; i < 36; i++) a.put(i < 10 ? Integer.toString(i) : Character.toString((char) ('A' + i - 10)));
        return a;
    }
    private static JSONObject fixture() throws JSONException {
        JSONObject input = new JSONObject().put("width",320).put("height",320)
                .put("channels",3).put("layout","NHWC").put("color","RGB")
                .put("data_type","INT8").put("quantization",new JSONObject()
                        .put("scale",0.003921568859368563).put("zero_point",-128));
        JSONObject output = new JSONObject().put("decoder","ultralytics_detect_split_ltrb_scores_int8_v1")
                .put("output_format","raw_split_ltrb_scores_v1").put("box_format","ltrb")
                .put("tensor_layout","channels_first").put("normalized_coordinates",true)
                .put("class_count",36).put("keypoint_count",0).put("has_objectness",false)
                .put("nms_in_graph",false).put("nms_required",false);
        JSONObject variant = new JSONObject().put("id","signed-int8").put("runtime","tflite")
                .put("precision","int8").put("file","model.tflite")
                .put("sha256",new JSONObject().put("model.tflite","a2f715399bdcb574da899e202388b5324d66f574b8519626cb611bf0e263e792"));
        return new JSONObject().put("schema","alpr.model.v1").put("model_id","t2i6d-synthetic")
                .put("role","character").put("task","detect").put("input",input)
                .put("output",output).put("labels",labels())
                .put("variants",new JSONArray().put(variant));
    }
    private static void rejection(JSONObject manifest) {
        try { ModelManifest.parse(manifest.toString()); fail("Expected manifest rejection"); }
        catch (JSONException expected) { assertNotNull(expected.getMessage()); }
    }
    @Test public void acceptsValidSplitManifest() throws Exception {
        ModelManifest m=ModelManifest.parse(fixture().toString());
        assertEquals(ModelRole.CHARACTER,m.role());
        assertEquals(36,m.labels().size());
        assertEquals("Q",m.labels().get(26));
        assertEquals("raw_split_ltrb_scores_v1",m.output().outputFormat());
    }
    @Test public void rejectsVehicleRole() throws Exception { JSONObject m=fixture(); m.put("role","vehicle"); rejection(m); }
    @Test public void rejectsPlateRole() throws Exception { JSONObject m=fixture(); m.put("role","plate"); rejection(m); }
    @Test public void rejectsOnnxRuntime() throws Exception { JSONObject m=fixture(); m.getJSONArray("variants").getJSONObject(0).put("runtime","onnx"); rejection(m); }
    @Test public void rejectsFp32Precision() throws Exception { JSONObject m=fixture(); m.getJSONArray("variants").getJSONObject(0).put("precision","fp32"); rejection(m); }
    @Test public void rejectsUint8Precision() throws Exception { JSONObject m=fixture(); m.getJSONArray("variants").getJSONObject(0).put("precision","uint8"); rejection(m); }
    @Test public void rejectsFloatInput() throws Exception { JSONObject m=fixture(); JSONObject input=m.getJSONObject("input");input.put("data_type","FLOAT32");input.remove("quantization");rejection(m); }
    @Test public void rejectsIncorrectResolution() throws Exception { JSONObject m=fixture();m.getJSONObject("input").put("width",416);rejection(m); }
    @Test public void rejectsChannelFirstInput() throws Exception { JSONObject m=fixture();m.getJSONObject("input").put("layout","NCHW");rejection(m); }
    @Test public void rejectsBgrInput() throws Exception { JSONObject m=fixture();m.getJSONObject("input").put("color","BGR");rejection(m); }
    @Test public void rejectsWrongClassOrder() throws Exception { JSONObject m=fixture();m.getJSONArray("labels").put(26,"P");rejection(m); }
    @Test public void rejectsVariantOverrideOfPrecision() throws Exception {
        JSONObject m=fixture();m.getJSONArray("variants").getJSONObject(0).put("precision","fp16");rejection(m);
    }
}
