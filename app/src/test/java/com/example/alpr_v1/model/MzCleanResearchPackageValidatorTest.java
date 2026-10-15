package com.example.alpr_v1.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** T2-I-11 JVM metadata negatives; the actual ZIP is checked on A12. */
public final class MzCleanResearchPackageValidatorTest {
    private static JSONObject reference(String member, String sha) throws Exception {
        return new JSONObject().put("file", member).put("sha256", sha);
    }
    private static JSONObject fixture() throws Exception {
        JSONArray labels = new JSONArray();
        for (int n=0;n<36;n++) labels.put(n<10 ? Integer.toString(n) : Character.toString((char)('A'+n-10)));
        JSONObject input = new JSONObject().put("width",320).put("height",320).put("channels",3)
                .put("layout","NHWC").put("color","RGB").put("data_type","INT8")
                .put("scale",1.0/255.0).put("offset",0.0)
                .put("quantization",new JSONObject().put("scale",0.003921568859368563).put("zero_point",-128));
        JSONObject output = new JSONObject()
                .put("decoder","ultralytics_detect_split_ltrb_scores_int8_v1")
                .put("output_format","raw_split_ltrb_scores_v1").put("box_format","ltrb")
                .put("tensor_layout","channels_first").put("class_count",36)
                .put("keypoint_count",0).put("keypoint_dimensions",0).put("has_objectness",false)
                .put("normalized_coordinates",true).put("nms_in_graph",false)
                .put("nms_required",false).put("confidence_threshold",.25).put("iou_threshold",.45);
        JSONObject graph = new JSONObject().put("classification","INT8").put("full_integer_graph",true)
                .put("artifact_sha256",MzCleanResearchPackageValidator.MODEL_SHA)
                .put("analysis_sha256",MzCleanResearchPackageValidator.GRAPH_ANALYSIS_SHA)
                .put("report_file",MzCleanResearchPackageValidator.REPORT_MEMBER)
                .put("report_sha256","a".repeat(64));
        JSONObject variant = new JSONObject().put("id","tflite-int8").put("runtime","tflite")
                .put("precision","int8").put("file",MzCleanResearchPackageValidator.MODEL_MEMBER)
                .put("sha256",new JSONObject().put(MzCleanResearchPackageValidator.MODEL_MEMBER,
                        MzCleanResearchPackageValidator.MODEL_SHA)).put("graph_precision",graph);
        JSONObject refs = new JSONObject()
                .put("staged_contract",reference(MzCleanResearchPackageValidator.STAGED_MEMBER,
                        "610775c88d60e0c6798f49b9575ce75e320b02f48a2e34b8e2d23af5003cef56"))
                .put("clean_evidence",reference(MzCleanResearchPackageValidator.CLEAN_MEMBER,
                        "bdf93990c1f971f571b281b73c02882ebf84adf680050ec31278ef66130678c3"))
                .put("train_parity",reference(MzCleanResearchPackageValidator.TRAIN_MEMBER,
                        "f2f1b89ec18fd9d71c40c1dbd280bfcef9ea32f3c78d7faa4be9ba8d53976b45"))
                .put("train_calibration",reference(MzCleanResearchPackageValidator.CALIBRATION_MEMBER,
                        MzCleanResearchPackageValidator.CALIBRATION_SHA)
                        .put("input_fingerprint",MzCleanResearchPackageValidator.CALIBRATION_FINGERPRINT));
        JSONArray shapes = new JSONArray().put(new JSONArray().put(1).put(4).put(2100))
                .put(new JSONArray().put(1).put(36).put(2100));
        JSONObject research = new JSONObject()
                .put("schema","alpr.mz_clean_int8_research_package.v1")
                .put("status","EXPLORATORY_ONLY")
                .put("android_import_approved",false).put("production_deployment_approved",false)
                .put("final_test","NOT_VERIFIED").put("calibration_usage","NOT_ATTESTED_DURING_REUSE")
                .put("backend_scope","PINNED_SHA_DEBUG_CPU_RESEARCH_ONLY")
                .put("class_order_sha256",MzCleanResearchPackageValidator.LABELS_SHA)
                .put("split_outputs",shapes).put("evidence",refs)
                .put("limitations",new JSONArray().put("ANDROID_IMPORT_NOT_APPROVED")
                        .put("FINAL_TEST_NOT_VERIFIED")
                        .put("CALIBRATION_LOADER_NOT_REEXECUTED_DURING_PACKAGING"));
        return new JSONObject().put("schema","alpr.model.v1")
                .put("model_id","mz-day-clean-int8-research-only")
                .put("role","character").put("task","detect")
                .put("input",input).put("output",output).put("labels",labels)
                .put("variants",new JSONArray().put(variant))
                .put("source",new JSONObject().put("checkpoint_sha256",MzCleanResearchPackageValidator.CHECKPOINT_SHA)
                        .put("source_kind","EXISTING_SHA_PINNED_MODEL_NO_CONVERSION"))
                .put("exporter",new JSONObject().put("conversion_performed",false))
                .put("research_mz_int8",research);
    }
    private static void rejected(JSONObject manifest, String reason) throws Exception {
        ModelManifest parsed = ModelManifest.parse(manifest.toString());
        try { MzCleanResearchPackageValidator.checkMetadata(manifest, parsed); fail("expected "+reason); }
        catch (JSONException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(reason)); }
    }
    @Test public void acceptsOnlyPinnedResearchMetadata() throws Exception {
        JSONObject data=fixture(); ModelManifest manifest=ModelManifest.parse(data.toString());
        MzCleanResearchPackageValidator.checkMetadata(data,manifest);
    }
    @Test public void refusesResearchInstallationRegardlessOfDebugMode() throws Exception {
        ModelManifest manifest=ModelManifest.parse(fixture().toString());
        try { MzCleanResearchPackageValidator.rejectInstallation(manifest); fail("import must remain denied"); }
        catch(ModelPackageException expected) {assertTrue(expected.getMessage().contains("IMPORT_NOT_APPROVED"));}
    }
    @Test public void rejectsReleaseApprovalFlag() throws Exception {
        JSONObject data=fixture();data.getJSONObject("research_mz_int8").put("android_import_approved",true);
        rejected(data,"RESEARCH_RESTRICTIONS");
    }
    @Test public void rejectsFinalTestClaim() throws Exception {
        JSONObject data=fixture();data.getJSONObject("research_mz_int8").put("final_test","PASS");
        rejected(data,"RESEARCH_RESTRICTIONS");
    }
    @Test public void rejectsFalseCalibrationUsageAttestation() throws Exception {
        JSONObject data=fixture();data.getJSONObject("research_mz_int8").put("calibration_usage","CONSUMED_BY_VERIFIED_LOADER");
        rejected(data,"RESEARCH_RESTRICTIONS");
    }
    @Test public void rejectsReplacedPinnedModel() throws Exception {
        JSONObject data=fixture();data.getJSONArray("variants").getJSONObject(0).getJSONObject("sha256")
                .put(MzCleanResearchPackageValidator.MODEL_MEMBER,"b".repeat(64));
        rejected(data,"VARIANT_ID_RUNTIME_PINNED_SHA");
    }
    @Test public void rejectsReplacedEvidenceReference() throws Exception {
        JSONObject data=fixture();data.getJSONObject("research_mz_int8").getJSONObject("evidence")
                .getJSONObject("clean_evidence").put("sha256","b".repeat(64));
        rejected(data,"EVIDENCE_REF_clean_evidence");
    }
    @Test public void rejectsWrongClassOrderDigest() throws Exception {
        JSONObject data=fixture();data.getJSONObject("research_mz_int8").put("class_order_sha256","b".repeat(64));
        rejected(data,"RESEARCH_RESTRICTIONS");
    }
    @Test public void rejectsAlteredGraphEvidence() throws Exception {
        JSONObject data=fixture();data.getJSONArray("variants").getJSONObject(0)
                .getJSONObject("graph_precision").put("classification","MIXED");
        rejected(data,"GRAPH_REPORT_REFERENCE");
    }
    @Test public void rejectsAlteredThreshold() throws Exception {
        JSONObject data=fixture();data.getJSONObject("output").put("iou_threshold",.49);
        rejected(data,"SPLIT_OUTPUT_CONTRACT");
    }
}
