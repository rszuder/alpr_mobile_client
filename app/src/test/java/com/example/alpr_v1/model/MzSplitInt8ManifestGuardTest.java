package com.example.alpr_v1.model;

import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** T2-I-5C: parser & fail-closed guards. No physical LiteRT invoke. */
public final class MzSplitInt8ManifestGuardTest {
    private static final String FORMAT="raw_split_ltrb_scores_v1";
    private static final String DECODER="ultralytics_detect_split_ltrb_scores_int8_v1";

    private static JSONObject split() throws Exception {
        return new JSONObject()
                .put("class_count",36).put("decoder",DECODER)
                .put("output_format",FORMAT).put("box_format","ltrb")
                .put("tensor_layout","channels_first")
                .put("normalized_coordinates",true)
                .put("nms_required",false).put("nms_in_graph",false);
    }
    private static void invalid(JSONObject json) throws Exception {
        try { ModelOutputSpec.fromJson(json); fail("Malformed split manifest accepted"); }
        catch(org.json.JSONException expected) { assertNotNull(expected.getMessage()); }
    }
    @Test public void validSplitParsesButIsNotEndToEnd() throws Exception {
        ModelOutputSpec s=ModelOutputSpec.fromJson(split());
        assertEquals(FORMAT,s.outputFormat());
        assertEquals(DECODER,s.decoder());
        assertEquals("ltrb",s.boxFormat());
        assertEquals(36,s.classCount());
        assertFalse(s.endToEnd());
        assertFalse(s.nmsRequired());
    }
    @Test public void rejectsExplicitlyUnnormalizedSplitCoordinates() throws Exception {
        invalid(split().put("normalized_coordinates", false));
    }
    @Test public void rejectsMissingNormalizedCoordinatesForSplit() throws Exception {
        JSONObject candidate = split();
        candidate.remove("normalized_coordinates");
        invalid(candidate);
    }
    @Test public void rejectsWrongDecoderAndClassCount() throws Exception {
        invalid(split().put("decoder","ultralytics_detect_raw_v1"));
        invalid(split().put("class_count",35));
    }
    @Test public void rejectsWrongLayoutAndBoxFormat() throws Exception {
        invalid(split().put("tensor_layout","anchors_first"));
        invalid(split().put("box_format","xywh"));
    }
    @Test public void rejectsObjectnessAndInGraphNms() throws Exception {
        invalid(split().put("has_objectness",true));
        invalid(split().put("nms_in_graph",true));
    }
    @Test public void rejectsNmsRequiredForSplit() throws Exception {
        invalid(split().put("nms_required",true));
    }
    @Test public void legacyRawAndEndToEndStillParse() throws Exception {
        JSONObject raw=new JSONObject().put("class_count",36).put("decoder","ultralytics_detect_raw_v1");
        ModelOutputSpec r=ModelOutputSpec.fromJson(raw);
        assertEquals("raw_yolo",r.outputFormat());
        assertEquals("xywh",r.boxFormat());
        JSONObject e=new JSONObject().put("class_count",36).put("decoder","ultralytics_detect_end2end_v1");
        ModelOutputSpec s=ModelOutputSpec.fromJson(e);
        assertEquals("end2end_detections",s.outputFormat());
        assertEquals("xyxy",s.boxFormat());
    }
    @Test public void characterRoleAcceptsSplitButGenericDecoderRejectsIt() throws Exception {
        ModelOutputSpec spec=ModelOutputSpec.fromJson(split());
        Method yes=Class.forName("com.example.alpr_v1.pipeline.MobileAlprEngine").getDeclaredMethod("validateCharacterDecoder",ModelOutputSpec.class);
        Method no=Class.forName("com.example.alpr_v1.pipeline.MobileAlprEngine").getDeclaredMethod("validateDecoder",ModelOutputSpec.class);
        yes.setAccessible(true);no.setAccessible(true);
        yes.invoke(null,spec);
        try { no.invoke(null,spec);fail("Split allowed as MT/MP"); }
        catch(InvocationTargetException x){assertTrue(x.getCause() instanceof IllegalArgumentException);}
    }
    @Test public void backendPinnedDebugGuardAndTensorValidationRemainEffective() throws Exception {
        // T2-I-9: debug/CPU/pinned SHA research path replaced the obsolete unconditional rejection.
        Path root=Paths.get(System.getProperty("user.dir"));
        Path file=root.resolve("src/main/java/com/example/alpr_v1/inference/TfliteBackend.java");
        if(!Files.isRegularFile(file)) file=root.resolve("app/src/main/java/com/example/alpr_v1/inference/TfliteBackend.java");
        assertTrue("Backend source unavailable",Files.isRegularFile(file));
        String s=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        // Actual T2-I-9 contract: pinned artifact + manifest before opening LiteRT.
        int split=s.indexOf("boolean mzRawSplit = \"raw_split_ltrb_scores_v1\".equals(outputSpec.outputFormat());");
        int pin=s.indexOf("MzDayCleanInt8RuntimeGate.requirePinnedArtifact(");
        int manifest=s.indexOf("MzDayCleanInt8RuntimeGate.requireManifest(");
        int open=s.indexOf("opened = factory.open(modelBytes, profile, evidence);");
        int guard=s.indexOf("MzSplitLtrbScoresInt8TensorValidator.validate(inputInfo, outputs);");
        int legacy=s.indexOf("ModelTensorContractValidator.validateOutput(outputSpec,");
        assertTrue("Missing split-specific branch or pinned artifact guard", split >= 0 && pin > split);
        assertTrue("Pinned SHA and manifest validation must precede opening LiteRT", manifest > pin && open > manifest);
        assertTrue("Signed INT8 ports must be validated in split branch, not by legacy output validator", guard > open && legacy > guard);
        assertTrue("Guard must receive actual build scope and GPU request", s.contains("BuildConfig.DEBUG, profile.gpu);"));
        assertTrue("Research model must not become approved", s.contains("evidence.put(\"mz_split_int8_model_approved\", false);"));
        assertTrue("Research model must not become import-approved", s.contains("evidence.put(\"mz_split_int8_android_import_approved\", false);"));
        // Functional fail-closed checks; do not reintroduce the obsolete always-throw guard.
        String sha=com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.MODEL_SHA256;
        int bytes=com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.MODEL_BYTES;
        com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.requirePinnedArtifact(sha,bytes,true,false);
        try {
            com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.requirePinnedArtifact(sha,bytes,false,false);
            fail("Release accepted research-only split MZ model");
        } catch (IllegalArgumentException expected) {
            assertEquals("MZ_SPLIT_INT8_RELEASE_NOT_APPROVED",expected.getMessage());
        }
        try {
            com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.requirePinnedArtifact(sha,bytes,true,true);
            fail("GPU accepted CPU-only research model");
        } catch (IllegalArgumentException expected) {
            assertEquals("MZ_SPLIT_INT8_CPU_ONLY",expected.getMessage());
        }
        try {
            com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.requirePinnedArtifact("not-the-pinned-sha",bytes,true,false);
            fail("Unpinned model accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("MZ_SPLIT_INT8_FROZEN_ARTIFACT_SHA_MISMATCH",expected.getMessage());
        }
        try {
            com.example.alpr_v1.inference.MzDayCleanInt8RuntimeGate.requirePinnedArtifact(sha,bytes+1,true,false);
            fail("Different model byte count accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("MZ_SPLIT_INT8_FROZEN_ARTIFACT_SHA_MISMATCH",expected.getMessage());
        }
    }
}
