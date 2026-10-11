package com.example.alpr_v1.inference;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelManifest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

/** Explicit future device acceptance only. Tiny schema-built RESHAPE/QUANTIZE fixtures, no ALPR model. */
@RunWith(AndroidJUnit4.class)
public class TfliteRuntimeEvidenceInstrumentedTest {
    private static final String FP32 = "CAAAAFRGTDOe////FAAAACAAAABMAAAAaAEAAAMAAAABAAAACAAAAAQABAAEAAAAKQAAAFN5bnRoZXRpYyBGNEIzIGZpeHR1cmU7IG5vdCBhbiBBTFBSIG1vZGVsAAAAAQAAABQAAAAAAA4AGAAUABAADAAIAAQADgAAABQAAAAgAAAAeAAAAHwAAACAAAAACQAAAHN5bnRoZXRpYwAAAAEAAAAUAAAAAAAOABYAAAAQAAwACwAEAA4AAAAYAAAAAAAAESgAAAAsAAAAAAAGAAgABAAGAAAABAAAAAMAAAABAAAABgAAAAEAAAABAAAAAQAAAAEAAAAAAAAAAQAAAAEAAAABAAAAAAAAAAIAAABAAAAABAAAANT///8IAAAAFAAAAAsAAABzeW50aGV0aWNfMQADAAAAAQAAAAYAAAABAAAADAAMAAgAAAAAAAQADAAAAAgAAAAUAAAACwAAAHN5bnRoZXRpY18wAAQAAAABAAAAAQAAAAIAAAADAAAAAQAAABAAAAAMAAwACwAAAAAABAAMAAAAFgAAAAAAABY=";
    private static final String INT8 = "DAAAAFRGTDMAAAAAnv///xQAAAAgAAAATAAAAMwBAAADAAAAAQAAAAgAAAAEAAQABAAAACkAAABTeW50aGV0aWMgRjRCMyBmaXh0dXJlOyBub3QgYW4gQUxQUiBtb2RlbAAAAAEAAAAUAAAAAAAOABgAFAAQAAwACAAEAA4AAAAUAAAAIAAAAHgAAAB8AAAAgAAAAAkAAABzeW50aGV0aWMAAAABAAAAFAAAAAAADgAWAAAAEAAMAAsABAAOAAAAGAAAAAAAABEoAAAALAAAAAAABgAIAAQABgAAAAQAAAADAAAAAQAAAAYAAAABAAAAAQAAAAEAAAABAAAAAAAAAAEAAAABAAAAAQAAAAAAAAACAAAAbAAAAAQAAACq////EAAAACwAAAAAAAAJNAAAAJz///8IAAAAEAAAAAEAAAAAAAAAAAAAAAEAAAAAAAA/CwAAAHN5bnRoZXRpY18xAAMAAAABAAAABgAAAAEAAAAAAA4AFAAQAA8AAAAIAAQADgAAABwAAAA8AAAAAAAACUQAAAAMAAwAAAAAAAgABAAMAAAACAAAABQAAAABAAAAAAAAAAAAAAAAAAAAAQAAAAAAAD8LAAAAc3ludGhldGljXzAABAAAAAEAAAABAAAAAgAAAAMAAAABAAAAEAAAAAwADAALAAAAAAAEAAwAAAAWAAAAAAAAFg==";
    private static final String MIXED = "DAAAAFRGTDMAAAAAnv///xQAAAAgAAAATAAAAAwCAAADAAAAAQAAAAgAAAAEAAQABAAAACkAAABTeW50aGV0aWMgRjRCMyBmaXh0dXJlOyBub3QgYW4gQUxQUiBtb2RlbAAAAAEAAAAUAAAAAAAOABgAFAAQAAwACAAEAA4AAAAUAAAAIAAAAKgAAACsAAAAsAAAAAkAAABzeW50aGV0aWMAAAACAAAARAAAABAAAAAAAAoAEAAMAAgABAAKAAAADAAAABAAAAABAAAAAQAAAAIAAAABAAAAAQAAAAAADgAWAAAAEAAMAAsABAAOAAAAGAAAAAAAABEoAAAALAAAAAAABgAIAAQABgAAAAQAAAADAAAAAQAAAAYAAAABAAAAAQAAAAEAAAABAAAAAAAAAAEAAAACAAAAAQAAAAAAAAADAAAAtAAAAHgAAAAUAAAAAAAOABQAEAAPAAAACAAEAA4AAAAcAAAAOAAAAAAAAAlAAAAADAAMAAAAAAAIAAQADAAAAAgAAAAQAAAAAQAAAAAAAAAAAAAAAQAAAAAAAD8LAAAAc3ludGhldGljXzIAAwAAAAEAAAAGAAAAAQAAANT///8IAAAAFAAAAAsAAABzeW50aGV0aWNfMQADAAAAAQAAAAYAAAABAAAADAAMAAgAAAAAAAQADAAAAAgAAAAUAAAACwAAAHN5bnRoZXRpY18wAAQAAAABAAAAAQAAAAIAAAADAAAAAgAAACAAAAAEAAAA9P///3IAAAAAAAByDAAMAAsAAAAAAAQADAAAABYAAAAAAAAW";

    @Test public void nativeFp32IoIsScopedAndRuntimeVersionIsObserved() throws Exception {
        List<JSONObject> events=execute(FP32,"FLOAT32","fp32");
        JSONObject e=events.get(1);
        assertEquals("FP32",e.getString("public_io_precision_status"));
        assertEquals("UNVERIFIED",e.getString("kernel_execution_status"));
        assertEquals(24,e.getInt("input_buffer_capacity"));
        assertEquals("OBSERVED_NATIVE_RUNTIME",e.getString("runtime_version_reason"));
        assertFalse(e.getString("runtime_version").isEmpty());
        exportEvidence("nativeFp32IoIsScopedAndRuntimeVersionIsObserved",FP32,"FLOAT32",events);
    }
    @Test public void nativeInt8IoDoesNotProveIntegerKernelExecution() throws Exception {
        List<JSONObject> events=execute(INT8,"INT8","int8");
        JSONObject e=events.get(1);
        assertEquals("INT8_NOT_PROVEN",e.getString("public_io_precision_status"));
        assertEquals("INT8_NOT_PROVEN",e.getString("kernel_execution_status"));
        assertEquals("INT8",e.getJSONArray("inputs").getJSONObject(0).getString("dtype"));
        assertEquals(.5,e.getJSONArray("inputs").getJSONObject(0).getDouble("scale"),.000001);
        assertFalse(e.getBoolean("int8_execution_proven"));
        exportEvidence("nativeInt8IoDoesNotProveIntegerKernelExecution",INT8,"INT8",events);
    }
    @Test public void nativeMixedIoIsReportedSeparatelyFromKernelProof() throws Exception {
        List<JSONObject> events=execute(MIXED,"FLOAT32","int8");
        JSONObject e=events.get(1);
        assertEquals("MIXED",e.getString("public_io_precision_status"));
        assertEquals("INT8_NOT_PROVEN",e.getString("kernel_execution_status"));
        assertFalse(e.getBoolean("int8_execution_proven"));
        exportEvidence("nativeMixedIoIsReportedSeparatelyFromKernelProof",MIXED,"FLOAT32",events);
    }
    private List<JSONObject> execute(String encoded,String dtype,String declared) throws Exception {
        File root=Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"f4b3-synthetic-").toFile();
        File file=new File(root,"model.tflite");Files.write(file.toPath(),Base64.getDecoder().decode(encoded));
        JSONObject input=new JSONObject().put("width",2).put("height",1).put("channels",3).put("layout","NHWC").put("data_type",dtype);
        if (dtype.equals("INT8")) input.put("quantization",new JSONObject().put("scale",.5).put("zero_point",0));
        JSONObject manifest=new JSONObject().put("schema","alpr.model.v1").put("model_id","synthetic-f4b3").put("role","character")
                .put("task","detect").put("input",input).put("labels",new JSONArray().put("A").put("B"))
                .put("output",new JSONObject().put("decoder","yolo_char_v1").put("class_count",2))
                .put("variants",new JSONArray().put(new JSONObject().put("id","synthetic").put("runtime","tflite").put("precision",declared)
                        .put("file",file.getName()).put("sha256",new JSONObject().put(file.getName(),RuntimeExecutionEvidence.sha256(file)))));
        InstalledModel model=new InstalledModel(ModelManifest.parse(manifest.toString()),root,"synthetic-only");
        List<JSONObject> events=new ArrayList<>();
        try(TfliteBackend backend=new TfliteBackend(model,model.manifest().variants().get(0),ExecutionProfile.tfliteCpu(1),events::add)) {
            backend.run(ByteBuffer.allocateDirect(backend.inputByteSize()).order(ByteOrder.nativeOrder()));
        } finally { file.delete(); root.delete(); }
        assertEquals(2,events.size());assertEquals("FIRST_INVOKE_COMPLETED",events.get(1).getString("phase"));
        assertCompleteEvents(events,RuntimeExecutionEvidence.sha256(ByteBuffer.wrap(Base64.getDecoder().decode(encoded))));
        return events;
    }

    private static String string(JSONObject object,String key) throws Exception {
        assertTrue(key,object.has(key) && object.get(key) instanceof String);
        String value=object.getString(key);assertFalse(key,value.isEmpty());return value;
    }
    private static Number number(JSONObject object,String key) throws Exception {
        assertTrue(key,object.has(key) && object.get(key) instanceof Number);
        Number value=(Number)object.get(key);assertTrue(key,Double.isFinite(value.doubleValue()));return value;
    }
    private static boolean bool(JSONObject object,String key) throws Exception {
        assertTrue(key,object.has(key) && object.get(key) instanceof Boolean);return object.getBoolean(key);
    }
    private static void assertTensor(JSONObject tensor,int index) throws Exception {
        assertEquals(index,number(tensor,"index").intValue());
        String dtype=string(tensor,"dtype");assertTrue(dtype.equals("FLOAT32") || dtype.equals("INT8"));
        JSONArray shape=tensor.getJSONArray("shape");assertTrue(shape.length()>0);
        long size=dtype.equals("FLOAT32") ? 4 : 1;
        for(int n=0;n<shape.length();n++) {
            assertTrue(shape.get(n) instanceof Number);int dimension=shape.getInt(n);
            assertTrue(dimension>0);size=Math.multiplyExact(size,dimension);
        }
        assertEquals(size,number(tensor,"byte_size").longValue());
        double scale=number(tensor,"scale").doubleValue();
        int zeroPoint=number(tensor,"zero_point").intValue();
        if(dtype.equals("INT8")) { assertTrue(scale>0);assertTrue(zeroPoint>=-128 && zeroPoint<=127); }
        // Float scale/zeroPoint remain the API's actual values, not an invented quantizer.
    }
    private static void assertCompleteEvents(List<JSONObject> events,String fixtureSha) throws Exception {
        assertEquals(2,events.size());
        String instance=string(events.get(0),"runtime_instance_id");UUID.fromString(instance);
        long previousTime=Long.MIN_VALUE;
        for(int n=0;n<events.size();n++) {
            JSONObject e=events.get(n);
            assertEquals("alpr.runtime_execution_evidence.v1",string(e,"schema"));
            assertEquals(instance,string(e,"runtime_instance_id"));
            assertEquals(n+1,number(e,"sequence").intValue());
            assertEquals(n==0 ? "ALLOCATED" : "FIRST_INVOKE_COMPLETED",string(e,"phase"));
            assertTrue(number(e,"observed_at_epoch_ms").longValue()>0);
            long timestamp=number(e,"observed_monotonic_ns").longValue();assertTrue(timestamp>=previousTime);previousTime=timestamp;
            string(e,"runtime_version");string(e,"runtime_schema_version");
            assertEquals("OBSERVED_NATIVE_RUNTIME",string(e,"runtime_version_reason"));
            assertEquals("FROM_APPLICATION_ONLY",string(e,"runtime_source"));
            assertEquals("synthetic",string(e,"variant_id"));
            assertEquals("tflite",string(e,"runtime"));
            assertEquals("tflite/CPU",string(e,"backend_requested"));
            assertEquals("LiteRT/CPU",string(e,"backend_effective"));
            assertEquals(1,number(e,"cpu_threads_requested").intValue());
            assertEquals(1,number(e,"cpu_threads_option_accepted").intValue());
            assertTrue(e.has("cpu_threads_effective") && e.isNull("cpu_threads_effective"));
            string(e,"cpu_threads_effective_reason");
            assertTrue(bool(e,"xnnpack_requested"));assertTrue(bool(e,"xnnpack_option_accepted"));
            assertFalse(bool(e,"nnapi_option_accepted"));assertFalse(bool(e,"allow_fp16_precision_for_fp32"));
            string(e,"backend_effective_scope");string(e,"backend_effective_reason");
            string(e,"delegate_requested");string(e,"delegate_effective");string(e,"delegate_effective_reason");
            string(e,"xnnpack_effective");string(e,"xnnpack_effective_reason");
            assertEquals("MATCH",string(e,"contract_validation"));
            assertTrue(bool(e,"model_sha_matches_manifest"));assertEquals(fixtureSha,string(e,"model_sha256"));
            assertEquals(fixtureSha,string(e,"manifest_model_sha256"));
            assertTrue(bool(e,"io_tensor_set_complete"));
            JSONArray inputs=e.getJSONArray("inputs"),outputs=e.getJSONArray("outputs"),buffers=e.getJSONArray("output_buffer_capacities");
            assertEquals(1,inputs.length());assertTrue(outputs.length()>0);assertEquals(outputs.length(),buffers.length());
            for(int i=0;i<inputs.length();i++) assertTensor(inputs.getJSONObject(i),i);
            for(int i=0;i<outputs.length();i++) {
                assertTensor(outputs.getJSONObject(i),i);assertTrue(buffers.get(i) instanceof Number);
                assertEquals(outputs.getJSONObject(i).getInt("byte_size"),buffers.getInt(i));
            }
            assertEquals("PUBLIC_IO_TENSORS_ONLY",string(e,"public_io_precision_scope"));
            assertEquals("EXECUTED_KERNEL_ARITHMETIC",string(e,"kernel_execution_scope"));
            assertFalse(bool(e,"int8_execution_proven"));
            assertEquals("UNVERIFIED",string(e,"static_graph_classification"));
            assertEquals("NOT_AVAILABLE",string(e,"static_graph_evidence_status"));
            assertFalse(e.has("error_reason"));assertFalse(e.has("error_type"));
            if(e.has("fallback_performed")) assertFalse(bool(e,"fallback_performed"));
            assertEquals(n==1,bool(e,"invoke_observed"));
            assertEquals(n,number(e,"invocation_count_at_observation").intValue());
            if(n==1) {
                int bytes=inputs.getJSONObject(0).getInt("byte_size");
                assertEquals(bytes,number(e,"input_buffer_capacity").intValue());
                assertEquals(bytes,number(e,"input_buffer_limit").intValue());
            }
        }
    }

    /** sendStatus carries bounded ASCII frames; host acceptance additionally requires full SHA reassembly. */
    private static void exportEvidence(String testCase,String encoded,String dtype,List<JSONObject> events) throws Exception {
        String runId=InstrumentationRegistry.getArguments().getString("f4b3e_run_id");
        assertNotNull("Host must bind this run",runId);assertEquals(runId,UUID.fromString(runId).toString());
        String fixtureSha=RuntimeExecutionEvidence.sha256(ByteBuffer.wrap(Base64.getDecoder().decode(encoded)));
        File testApk=new File(InstrumentationRegistry.getInstrumentation().getContext().getApplicationInfo().sourceDir);
        assertEquals("com.example.alpr_v1.test",InstrumentationRegistry.getInstrumentation().getContext().getPackageName());
        JSONObject unavailable=new JSONObject()
                .put("static_graph_scope","NOT_EXPOSED: fixture has no attached B1 report")
                .put("gpu_options","NOT_EXPOSED: CPU profile; GPU not requested")
                .put("fallback_performed",events.get(1).has("fallback_performed") ? "ASSERTED_FALSE" : "NOT_VERIFIED: success events do not emit this field");
        JSONObject payload=new JSONObject().put("schema","alpr.f4b3e.test_runtime_events.v1")
                .put("run_id",runId).put("test_case",testCase).put("fixture_sha256",fixtureSha)
                .put("test_apk_sha256",RuntimeExecutionEvidence.sha256(testApk))
                .put("expected_fixture_input",new JSONObject().put("dtype",dtype).put("shape",new JSONArray(new int[]{1,1,2,3})))
                .put("event_count",events.size()).put("events",new JSONArray(events))
                .put("assertions","COMMON_AND_CASE_ASSERTIONS_PASSED")
                .put("source_field_availability",unavailable)
                .put("variant_identity_check","ASSERTED_SYNTHETIC_IN_BOTH_EVENTS")
                .put("transport_acceptance","HOST_MUST_VERIFY_ALL_FRAMES_AND_SHA");
        byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
        String sha=RuntimeExecutionEvidence.sha256(ByteBuffer.wrap(bytes));
        int chunkSize=1200,count=(bytes.length+chunkSize-1)/chunkSize;
        List<JSONObject> frames=new ArrayList<>();ByteArrayOutputStream roundTrip=new ByteArrayOutputStream();
        for(int index=0;index<count;index++) {
            byte[] chunk=java.util.Arrays.copyOfRange(bytes,index*chunkSize,Math.min(bytes.length,(index+1)*chunkSize));
            String data=Base64.getEncoder().encodeToString(chunk);
            roundTrip.write(Base64.getDecoder().decode(data));
            frames.add(new JSONObject().put("schema","alpr.f4b3e.frame.v1").put("run_id",runId).put("test_case",testCase)
                    .put("index",index).put("count",count).put("utf8_bytes",bytes.length).put("sha256",sha).put("data_base64",data));
        }
        assertArrayEquals(bytes,roundTrip.toByteArray());
        assertEquals(sha,RuntimeExecutionEvidence.sha256(ByteBuffer.wrap(roundTrip.toByteArray())));
        for(JSONObject frame:frames) {
            Bundle bundle=new Bundle();bundle.putString("f4b3e_frame",frame.toString());
            InstrumentationRegistry.getInstrumentation().sendStatus(2,bundle);
        }
        Bundle complete=new Bundle();complete.putString("f4b3e_complete",new JSONObject().put("run_id",runId)
                .put("test_case",testCase).put("count",count).put("utf8_bytes",bytes.length).put("sha256",sha).toString());
        InstrumentationRegistry.getInstrumentation().sendStatus(2,complete);
    }
}
