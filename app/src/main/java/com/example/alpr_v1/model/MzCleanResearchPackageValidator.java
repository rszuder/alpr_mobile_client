package com.example.alpr_v1.model;

import com.example.alpr_v1.BuildConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * T2-I-11: read-only verifier of one SHA-pinned exploratory MZ CLEAN INT8
 * .alprmodel. It does NOT approve, install, or register the model.
 *
 * The TFLite graph was independently checked on Desktop and the exact bytes
 * have already run through debug/CPU on A12. This class validates package
 * bytes/manifest/evidence binding, NOT Android kernel arithmetic or GT quality.
 */
final class MzCleanResearchPackageValidator {
    static final String MODEL_SHA = "a2f715399bdcb574da899e202388b5324d66f574b8519626cb611bf0e263e792";
    static final String CHECKPOINT_SHA = "631bb1453554cdb6b15eca724baa392e2acffb350899107f892dc11f937dbbf8";
    static final String LABELS_SHA = "f93925af23277c69c2bac02d0ac546c236a6a7c3a647169ad83b69c103c12e92";
    static final String GRAPH_ANALYSIS_SHA = "68281d2b8f76305085e41f1b2282d1fd108e3575f1fc8c301b69e34bebb4508b";
    static final String CALIBRATION_SHA = "b9702376149883f1a217f7efcf8f504a22d72570fbf3929d0aaf85e6ad3948e0";
    static final String CALIBRATION_FINGERPRINT = "161001f4aff0c6b6c0123cafcb9c19ffa52edbc1f6ec36e18661f60f0ee528ce";
    static final String MODEL_MEMBER = "variants/tflite_int8/model.tflite";
    static final String REPORT_MEMBER = "precision/tflite-int8.json";
    static final String STAGED_MEMBER = "research/staged_contract.json";
    static final String CLEAN_MEMBER = "research/clean_evidence.json";
    static final String TRAIN_MEMBER = "research/train_parity.json";
    static final String CALIBRATION_MEMBER = "research/calibration_manifest.json";
    static final int MODEL_BYTES = 10255456;
    private static final String RESEARCH_SCHEMA = "alpr.mz_clean_int8_research_package.v1";
    private static final String OUTPUT_FORMAT = "raw_split_ltrb_scores_v1";
    private static final String MODEL_ID = "mz-day-clean-int8-research-only";

    private MzCleanResearchPackageValidator() {}

    static boolean isResearchOrSplit(ModelManifest manifest) {
        if (OUTPUT_FORMAT.equals(manifest.output().outputFormat()) || MODEL_ID.equals(manifest.modelId())) {
            return true;
        }
        try { return new JSONObject(manifest.rawJson()).has("research_mz_int8"); }
        catch (JSONException error) { return true; } // malformed manifest: fail closed
    }

    static void rejectInstallation(ModelManifest manifest) throws ModelPackageException {
        if (isResearchOrSplit(manifest)) {
            throw new ModelPackageException("MZ_CLEAN_INT8_RESEARCH_IMPORT_NOT_APPROVED: "
                    + "validatePackage is read-only; installation into ModelRegistry is prohibited");
        }
    }

    static void validateReadOnly(File staging, ModelManifest manifest) throws ModelPackageException {
        if (!isResearchOrSplit(manifest)) return; // legacy packages unchanged
        if (!BuildConfig.DEBUG) {
            throw new ModelPackageException("MZ_CLEAN_RESEARCH_VALIDATION_DEBUG_ONLY");
        }
        try {
            JSONObject root = new JSONObject(manifest.rawJson());
            checkMetadata(root, manifest);
            checkMembers(staging, root);
        } catch (Exception e) {
            throw new ModelPackageException("MZ_CLEAN_RESEARCH_PACKAGE_INVALID: " + e.getMessage(), e);
        }
    }

    /** Also used by synthetic negative JVM tests: no model files or interpreter. */
    static void checkMetadata(JSONObject root, ModelManifest manifest) throws JSONException {
        require(MODEL_ID.equals(manifest.modelId()), "MODEL_ID");
        require(manifest.role() == ModelRole.CHARACTER && "detect".equals(manifest.task()), "ROLE_TASK");
        ModelInputSpec input = manifest.input();
        require(input.width() == 320 && input.height() == 320 && input.channels() == 3
                && "NHWC".equals(input.layout()) && "RGB".equals(input.colorSpace())
                && "INT8".equals(input.dataType()), "INPUT");
        require(input.quantizationScale() != null && input.quantizationZeroPoint() != null
                && Math.abs(input.quantizationScale() - 0.003921568859368563f) < 1e-8f
                && input.quantizationZeroPoint() == -128, "INPUT_QUANTIZATION");
        ModelOutputSpec output = manifest.output();
        require(OUTPUT_FORMAT.equals(output.outputFormat())
                && "ultralytics_detect_split_ltrb_scores_int8_v1".equals(output.decoder())
                && "ltrb".equals(output.boxFormat()) && output.classCount() == 36
                && output.keypointCount() == 0 && output.keypointDimensions() == 0
                && output.channelsFirst() && output.normalizedCoordinates()
                && !output.hasObjectness() && !output.nmsInGraph() && !output.nmsRequired()
                && Float.compare(output.confidenceThreshold(), .25f) == 0
                && Float.compare(output.iouThreshold(), .45f) == 0, "SPLIT_OUTPUT_CONTRACT");
        require(manifest.labels().size() == 36, "LABEL_COUNT");
        for (int i = 0; i < 36; i++) {
            String expected = i < 10 ? Integer.toString(i) : Character.toString((char) ('A' + i - 10));
            require(expected.equals(manifest.labels().get(i)), "LABEL_ORDER_" + i);
        }
        List<ModelVariant> variants = manifest.variants();
        require(variants.size() == 1, "VARIANT_COUNT");
        ModelVariant variant = variants.get(0);
        require("tflite-int8".equals(variant.id()) && variant.runtime() == ModelRuntime.TFLITE
                && "int8".equals(variant.precision())
                && variant.files().size() == 1 && MODEL_MEMBER.equals(variant.primaryFile())
                && variant.sha256().size() == 1 && MODEL_SHA.equals(variant.sha256().get(MODEL_MEMBER)),
                "VARIANT_ID_RUNTIME_PINNED_SHA");
        JSONObject source = root.getJSONObject("source");
        require(CHECKPOINT_SHA.equals(source.getString("checkpoint_sha256"))
                && "EXISTING_SHA_PINNED_MODEL_NO_CONVERSION".equals(source.getString("source_kind")),
                "CHECKPOINT_PROVENANCE");
        JSONObject exporter = root.getJSONObject("exporter");
        require(Boolean.FALSE.equals(exporter.get("conversion_performed")), "NO_CONVERSION");
        JSONObject research = root.getJSONObject("research_mz_int8");
        require(RESEARCH_SCHEMA.equals(research.getString("schema"))
                && "EXPLORATORY_ONLY".equals(research.getString("status"))
                && Boolean.FALSE.equals(research.get("android_import_approved"))
                && Boolean.FALSE.equals(research.get("production_deployment_approved"))
                && "NOT_VERIFIED".equals(research.getString("final_test"))
                && "NOT_ATTESTED_DURING_REUSE".equals(research.getString("calibration_usage"))
                && "PINNED_SHA_DEBUG_CPU_RESEARCH_ONLY".equals(research.getString("backend_scope"))
                && LABELS_SHA.equals(research.getString("class_order_sha256")),
                "RESEARCH_RESTRICTIONS");
        JSONArray shapes = research.getJSONArray("split_outputs");
        require(shapes.length() == 2 && shape(shapes.getJSONArray(0), 1, 4, 2100)
                && shape(shapes.getJSONArray(1), 1, 36, 2100), "DUAL_OUTPUT_SHAPES");
        JSONArray limits = research.getJSONArray("limitations");
        require(contains(limits, "ANDROID_IMPORT_NOT_APPROVED")
                && contains(limits, "FINAL_TEST_NOT_VERIFIED")
                && contains(limits, "CALIBRATION_LOADER_NOT_REEXECUTED_DURING_PACKAGING"),
                "LIMITATIONS_NOT_RETAINED");
        JSONObject evidence = research.getJSONObject("evidence");
        reference(evidence, "staged_contract", STAGED_MEMBER,
                "610775c88d60e0c6798f49b9575ce75e320b02f48a2e34b8e2d23af5003cef56");
        reference(evidence, "clean_evidence", CLEAN_MEMBER,
                "bdf93990c1f971f571b281b73c02882ebf84adf680050ec31278ef66130678c3");
        reference(evidence, "train_parity", TRAIN_MEMBER,
                "f2f1b89ec18fd9d71c40c1dbd280bfcef9ea32f3c78d7faa4be9ba8d53976b45");
        reference(evidence, "train_calibration", CALIBRATION_MEMBER, CALIBRATION_SHA);
        require(CALIBRATION_FINGERPRINT.equals(evidence.getJSONObject("train_calibration")
                .getString("input_fingerprint")), "CALIBRATION_FINGERPRINT");
        JSONObject gp = root.getJSONArray("variants").getJSONObject(0).getJSONObject("graph_precision");
        require(GRAPH_ANALYSIS_SHA.equals(gp.getString("analysis_sha256"))
                && "INT8".equals(gp.getString("classification"))
                && Boolean.TRUE.equals(gp.get("full_integer_graph"))
                && MODEL_SHA.equals(gp.getString("artifact_sha256"))
                && REPORT_MEMBER.equals(gp.getString("report_file"))
                && gp.getString("report_sha256").matches("[0-9a-f]{64}"), "GRAPH_REPORT_REFERENCE");
    }

    private static void checkMembers(File staging, JSONObject root) throws Exception {
        Set<String> expected = new HashSet<>(Arrays.asList(
                "manifest.json", MODEL_MEMBER, REPORT_MEMBER, STAGED_MEMBER,
                CLEAN_MEMBER, TRAIN_MEMBER, CALIBRATION_MEMBER));
        Set<String> observed = new HashSet<>();
        try (Stream<Path> walk = Files.walk(staging.toPath())) {
            walk.filter(Files::isRegularFile).forEach(file ->
                    observed.add(staging.toPath().relativize(file).toString().replace('\\', '/')));
        }
        require(observed.equals(expected), "PACKAGE_MEMBERS");
        File model = new File(staging, MODEL_MEMBER);
        require(model.isFile() && model.length() == MODEL_BYTES
                && MODEL_SHA.equals(Hashing.sha256(model)), "MODEL_SHA_AND_BYTES");
        JSONObject research = root.getJSONObject("research_mz_int8");
        JSONObject refs = research.getJSONObject("evidence");
        for (String key : new String[]{"staged_contract", "clean_evidence", "train_parity", "train_calibration"}) {
            JSONObject ref = refs.getJSONObject(key);
            File file = new File(staging, ref.getString("file"));
            require(file.isFile() && ref.getString("sha256").equals(Hashing.sha256(file)),
                    "RESEARCH_EVIDENCE_SHA_" + key);
        }
        JSONObject graphRef = root.getJSONArray("variants").getJSONObject(0).getJSONObject("graph_precision");
        File reportFile = new File(staging, REPORT_MEMBER);
        require(graphRef.getString("report_sha256").equals(Hashing.sha256(reportFile)),
                "GRAPH_REPORT_SHA");
        JSONObject graph = new JSONObject(new String(Files.readAllBytes(reportFile.toPath()),
                java.nio.charset.StandardCharsets.UTF_8));
        require(GRAPH_ANALYSIS_SHA.equals(graph.getString("analysis_sha256"))
                && "INT8".equals(graph.getString("classification"))
                && Boolean.TRUE.equals(graph.get("full_integer_graph"))
                && MODEL_SHA.equals(graph.getString("artifact_sha256")), "GRAPH_REPORT_CONTENT");
        JSONObject calibration = new JSONObject(new String(Files.readAllBytes(
                new File(staging, CALIBRATION_MEMBER).toPath()), java.nio.charset.StandardCharsets.UTF_8));
        require("EXPLORATORY_ONLY".equals(calibration.getString("status"))
                && "character".equals(calibration.getString("role"))
                && "train".equals(calibration.getString("split"))
                && CALIBRATION_FINGERPRINT.equals(calibration.getString("input_fingerprint"))
                && CHECKPOINT_SHA.equals(calibration.getJSONObject("checkpoint").getString("sha256")),
                "TRAIN_CALIBRATION_CONTENT");
    }

    private static void reference(JSONObject refs, String key, String file, String sha) throws JSONException {
        JSONObject ref = refs.getJSONObject(key);
        require(file.equals(ref.getString("file")) && sha.equals(ref.getString("sha256")),
                "EVIDENCE_REF_" + key);
    }
    private static boolean contains(JSONArray array, String key) throws JSONException {
        for (int i = 0; i < array.length(); i++) if (key.equals(array.getString(i))) return true;
        return false;
    }
    private static boolean shape(JSONArray a, int x, int y, int z) throws JSONException {
        return a.length() == 3 && a.getInt(0) == x && a.getInt(1) == y && a.getInt(2) == z;
    }
    private static void require(boolean condition, String code) throws JSONException {
        if (!condition) throw new JSONException("T2I11_" + code);
    }
}
