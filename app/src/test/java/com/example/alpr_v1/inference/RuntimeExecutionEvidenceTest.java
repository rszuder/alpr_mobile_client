package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class RuntimeExecutionEvidenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final List<JSONObject> events = new ArrayList<>();
    private static TensorInfo input(String dtype) { return new TensorInfo(0,new int[]{1,2,2,3},dtype,dtype.equals("FLOAT32") ? 48 : 12,dtype.equals("FLOAT32") ? 0 : .05f,dtype.equals("FLOAT32") ? 0 : -128); }
    private static TensorInfo output(String dtype) { return new TensorInfo(0,new int[]{1,5,2},dtype,dtype.equals("FLOAT32") ? 40 : 10,dtype.equals("FLOAT32") ? 0 : .02f,0); }
    private static class FakeDriver implements TfliteBackend.Driver {
        TensorInfo input = input("FLOAT32"), output = output("FLOAT32"); int runs; boolean closed; RuntimeException failure;
        public List<TensorInfo> inputs() { return Collections.singletonList(input); }
        public Map<Integer,TensorInfo> outputs() { return Collections.singletonMap(0,output); }
        public void run(ByteBuffer input,Map<Integer,ByteBuffer> outputs) { runs++; if (failure != null) throw failure; }
        public void close() { closed = true; }
    }
    private TfliteBackend backend(InstalledModel model, FakeDriver driver) {
        return new TfliteBackend(model,model.manifest().variants().get(0),ExecutionProfile.tfliteCpu(2),events::add,
                (bytes,profile,evidence) -> {
                    evidence.put("runtime_version","SYNTHETIC_DRIVER"); evidence.put("cpu_threads_option_accepted",profile.cpuThreads);
                    return driver;
                });
    }
    @Test public void fp32IoIsNotFp32KernelProofAndBufferSizesAreObserved() throws Exception {
        InstalledModel model=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32","FP32"); FakeDriver d=new FakeDriver();
        try (TfliteBackend b=backend(model,d)) {
            b.run(ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder()));
            JSONObject event=events.get(1);
            assertEquals("FIRST_INVOKE_COMPLETED",event.getString("phase"));
            assertEquals("FP32",event.getString("public_io_precision_status"));
            assertEquals("UNVERIFIED",event.getString("kernel_execution_status"));
            assertEquals(48,event.getInt("input_buffer_capacity"));
            assertEquals(40,event.getJSONArray("output_buffer_capacities").getInt(0));
            assertEquals("FLOAT32",event.getJSONArray("inputs").getJSONObject(0).getString("dtype"));
            assertEquals("MATCH",event.getString("contract_validation"));
            assertFalse(event.getBoolean("int8_execution_proven"));
        }
        assertTrue(d.closed);
    }
    @Test public void integerGraphAndIntegerIoStillCannotProveIntegerKernels() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","INT8","INT8"); FakeDriver d=new FakeDriver();d.input=input("INT8");d.output=output("INT8");
        try(TfliteBackend b=backend(m,d)) { b.run(ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())); }
        JSONObject e=events.get(1);
        assertEquals("INT8",e.getString("static_graph_classification"));
        assertEquals("INT8_NOT_PROVEN",e.getString("kernel_execution_status"));
        assertEquals(-128,e.getJSONArray("inputs").getJSONObject(0).getInt("zero_point"));
        assertEquals(.05,e.getJSONArray("inputs").getJSONObject(0).getDouble("scale"),.00001);
        assertFalse(e.getBoolean("int8_execution_proven"));
    }
    @Test public void mixedDesktopGraphIsScopedAndDoesNotPromoteKernelEvidence() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","FLOAT32","MIXED");
        try(TfliteBackend b=backend(m,new FakeDriver())) {
            JSONObject e=b.executionEvidence(); assertEquals("MIXED",e.getString("static_graph_classification"));
            assertEquals("FP32",e.getString("public_io_precision_status"));
            assertEquals("INT8_NOT_PROVEN",e.getString("kernel_execution_status"));
            assertTrue(e.getString("static_graph_scope").contains("NOT_ANDROID_KERNEL_PROOF"));
        }
    }
    @Test public void missingGraphDoesNotTreatManifestInt8AsProof() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","FLOAT32",null);
        try(TfliteBackend b=backend(m,new FakeDriver())) {
            assertEquals("NOT_AVAILABLE",b.executionEvidence().getString("static_graph_evidence_status"));
            assertEquals("INT8_NOT_PROVEN",b.executionEvidence().getString("kernel_execution_status"));
        }
    }
    @Test public void mixedIoAndUnknownTypesHaveDistinctStatuses() {
        assertEquals(PrecisionEvidence.Status.MIXED,PrecisionEvidence.publicIo(Arrays.asList(input("FLOAT32"),output("INT8"))));
        assertEquals(PrecisionEvidence.Status.UNVERIFIED,PrecisionEvidence.publicIo(Collections.emptyList()));
        assertEquals(PrecisionEvidence.Status.UNVERIFIED,PrecisionEvidence.publicIo(Collections.singletonList(new TensorInfo(0,new int[]{1},"FLOAT16",2,0,0))));
        for(PrecisionEvidence.Status io:PrecisionEvidence.Status.values())
            assertNotEquals(PrecisionEvidence.Status.INT8_PROVEN,PrecisionEvidence.javaKernelStatus("int8","INT8",io));
    }
    @Test public void manifestRuntimeDtypeMismatchRecordsActualIoThenFails() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","INT8",null); FakeDriver d=new FakeDriver();
        assertThrows(RuntimeModelContractException.class,()->backend(m,d));
        JSONObject e=events.get(0); assertEquals("ERROR",e.getString("phase"));assertEquals("MISMATCH",e.getString("contract_validation"));
        assertEquals("FLOAT32",e.getJSONArray("inputs").getJSONObject(0).getString("dtype"));assertTrue(d.closed);assertEquals(0,d.runs);
    }
    @Test public void shapeQuantizerAndStorageMismatchesAreRejected() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","INT8",null);
        for(TensorInfo wrong:Arrays.asList(new TensorInfo(0,new int[]{1,2,3,3},"INT8",18,.05f,-128),
                new TensorInfo(0,new int[]{1,2,2,3},"INT8",12,.08f,-128),new TensorInfo(0,new int[]{1,2,2,3},"INT8",12,.05f,2),
                new TensorInfo(0,new int[]{1,2,2,3},"INT8",99,.05f,-128))) {
            FakeDriver d=new FakeDriver();d.input=wrong;assertThrows(RuntimeModelContractException.class,()->backend(m,d));assertEquals(0,d.runs);
        }
    }
    @Test public void delegateFailureHasReasonAndCannotRetryOnCpu() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","FLOAT32",null);int[] opened={0};
        assertThrows(IllegalStateException.class,()->new TfliteBackend(m,m.manifest().variants().get(0),ExecutionProfile.tfliteGpu(),events::add,
                (bytes,profile,evidence)->{opened[0]++;throw new IllegalStateException("GPU_DELEGATE_UNAVAILABLE");}));
        assertEquals(1,opened[0]);assertEquals("GPU_DELEGATE_UNAVAILABLE",events.get(0).getString("error_reason"));
        assertFalse(events.get(0).getBoolean("fallback_performed"));assertTrue(events.get(0).isNull("backend_effective"));
    }
    @Test public void failedNativeLibraryLoadHasEvidence() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32",null);
        assertThrows(IllegalStateException.class,()->new TfliteBackend(m,m.manifest().variants().get(0),ExecutionProfile.tfliteCpu(1),events::add,
                (bytes,profile,evidence)->{throw new UnsatisfiedLinkError("SYNTHETIC_LIBRARY_MISSING");}));
        assertEquals("java.lang.UnsatisfiedLinkError",events.get(0).getString("error_type"));
    }
    @Test public void badInputBufferAndChangedTensorNeverReachDriver() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32",null);FakeDriver d=new FakeDriver();
        try(TfliteBackend b=backend(m,d)) {
            assertThrows(IllegalStateException.class,()->b.run(ByteBuffer.allocateDirect(44).order(ByteOrder.nativeOrder())));
            assertEquals(0,d.runs);d.input=new TensorInfo(0,new int[]{1,4,4,3},"FLOAT32",192,0,0);
            assertThrows(IllegalStateException.class,()->b.run(ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder())));
            assertEquals(0,d.runs);
            assertEquals(4,events.get(events.size()-1).getJSONArray("inputs").getJSONObject(0).getJSONArray("shape").getInt(1));
            assertEquals("RUNTIME_TENSOR_CHANGED",events.get(events.size()-1).getString("contract_validation"));
        }
    }
    @Test public void runtimeFailureIsJournaledWithoutVariantChange() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","FLOAT32",null);FakeDriver d=new FakeDriver();d.failure=new IllegalStateException("SYNTHETIC_INVOKE_FAILURE");
        try(TfliteBackend b=backend(m,d)) {assertThrows(IllegalStateException.class,()->b.run(ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder())));}
        JSONObject e=events.get(1);assertEquals("selected",e.getString("variant_id"));assertEquals("INVOKE",e.getString("error_phase"));assertFalse(e.getBoolean("fallback_performed"));
    }
    @Test public void artifactHashTamperingFailsBeforeRuntimeCreation() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32",null);
        Files.write(m.resolve("synthetic.tflite").toPath(),new byte[]{1,2,3});int[] calls={0};
        assertThrows(IllegalStateException.class,()->new TfliteBackend(m,m.manifest().variants().get(0),ExecutionProfile.tfliteCpu(1),events::add,
                (bytes,profile,evidence)->{calls[0]++;return new FakeDriver();}));
        assertEquals(0,calls[0]);assertFalse(events.get(0).getBoolean("model_sha_matches_manifest"));
    }
    @Test public void graphReportHashTamperingFailsBeforeRuntimeCreation() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","INT8","INT8");Files.write(m.resolve("graph.json").toPath(),new byte[]{1});
        assertThrows(IllegalStateException.class,()->backend(m,new FakeDriver()));
        assertEquals("INVALID",events.get(0).getString("static_graph_evidence_status"));
    }
    @Test public void interpreterReceivesPrivateCopyOfExactlyHashedBytes() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32",null);ByteBuffer[] seen={null};
        try(TfliteBackend b=new TfliteBackend(m,m.manifest().variants().get(0),ExecutionProfile.tfliteCpu(1),events::add,
                (bytes,profile,evidence)->{seen[0]=bytes;return new FakeDriver();})) {
            String original=RuntimeExecutionEvidence.sha256(seen[0]);Files.write(m.resolve("synthetic.tflite").toPath(),new byte[]{1});
            assertEquals(original,RuntimeExecutionEvidence.sha256(seen[0]));assertEquals(original,b.executionEvidence().getString("model_sha256"));
        }
    }
    @Test public void tensorStorageRejectsInvalidQuantizationAndOverflow() {
        for(TensorInfo bad:Arrays.asList(new TensorInfo(0,new int[]{1},"INT8",1,0,0),new TensorInfo(0,new int[]{1},"INT8",1,.1f,200),
                new TensorInfo(0,new int[]{-1},"FLOAT32",4,0,0),new TensorInfo(0,new int[]{Integer.MAX_VALUE,9},"FLOAT32",4,0,0)))
            assertThrows(RuntimeException.class,()->PrecisionEvidence.validateStorage(bad));
    }
}
