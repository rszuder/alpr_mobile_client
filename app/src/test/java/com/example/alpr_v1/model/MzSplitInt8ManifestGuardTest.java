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
    @Test public void backendGuardStaysInstalledAndBeforeLegacyValidation() throws Exception {
        // Source-level invariant: until a controlled runtime test, the backend must reject.
        Path root=Paths.get(System.getProperty("user.dir"));
        Path file=root.resolve("src/main/java/com/example/alpr_v1/inference/TfliteBackend.java");
        if(!Files.isRegularFile(file)) file=root.resolve("app/src/main/java/com/example/alpr_v1/inference/TfliteBackend.java");
        assertTrue("Backend source unavailable",Files.isRegularFile(file));
        String s=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        int guard=s.indexOf("MzSplitLtrbScoresInt8TensorValidator.validate(inputInfo, outputs);");
        int fail=s.indexOf("MZ_RAW_SPLIT_INT8_DECODER_NOT_CONNECTED");
        int old=s.indexOf("ModelTensorContractValidator.validateOutput(outputSpec,");
        assertTrue(guard>=0 && fail>guard && old>fail);
    }
}
