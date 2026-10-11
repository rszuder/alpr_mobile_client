package com.example.alpr_v1.experiment;

import com.example.alpr_v1.inference.ExecutionProfile;
import com.example.alpr_v1.inference.RuntimeEvidenceFixture;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.metrics.ApkIdentity;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelManifest;
import com.example.alpr_v1.model.ModelRole;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class ResearchExecutionEvidenceContractTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void researchCannotUseAutoMissingPinOrUnavailableRuntime() throws Exception {
        InstalledModel model=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","INT8",null);
        assertThrows(IllegalStateException.class,()->ResearchVariantSelection.require(model,"",r->true));
        assertThrows(IllegalStateException.class,()->ResearchVariantSelection.require(model,"removed",r->true));
        assertThrows(IllegalStateException.class,()->ResearchVariantSelection.require(model,"selected",r->false));
        assertEquals("selected",ResearchVariantSelection.require(model,"selected",r->true).id());
        assertThrows(IllegalArgumentException.class,()->ResearchExecutionChoice.AUTO.resolveApproved(model.manifest().variants().get(0)));
    }
    @Test public void approvedProfilesRemainExact() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32",null);
        assertEquals(2,ResearchExecutionChoice.CPU_2.resolveApproved(m.manifest().variants().get(0)).cpuThreads);
        assertFalse(ResearchExecutionChoice.CPU_2.resolveApproved(m.manifest().variants().get(0)).gpu);
        assertTrue(ResearchExecutionChoice.GPU.resolveApproved(m.manifest().variants().get(0)).gpu);
    }
    @Test public void activeResearchConfigCannotBeClearedOrReplaced() {
        ResearchStageExecutionConfig mt=new ResearchStageExecutionConfig(ModelRole.PLATE,true,"mt","f","v",com.example.alpr_v1.model.ModelRuntime.TFLITE,"fp32",2,false,"cpu",2,2,3,"NHWC","RGB","FLOAT32");
        ResearchStageExecutionConfig mz=new ResearchStageExecutionConfig(ModelRole.CHARACTER,true,"mz","f","v",com.example.alpr_v1.model.ModelRuntime.TFLITE,"fp32",2,false,"cpu",2,2,3,"NHWC","RGB","FLOAT32");
        ResearchExecutionConfig frozen=new ResearchExecutionConfig("f4","fp32",com.example.alpr_v1.pipeline.RoiBudgetPolicy.FULL_FRAME,
                com.example.alpr_v1.pipeline.RecognitionProfile.BALANCED,"640x480",false,false,false,false,false,false,
                ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE),mt,mz);
        ResearchExecutionConfig.requireUnchangedWhileRunning(frozen,frozen,true);
        assertThrows(IllegalStateException.class,()->ResearchExecutionConfig.requireUnchangedWhileRunning(frozen,null,true));
        ResearchExecutionConfig.requireUnchangedWhileRunning(frozen,null,false);
    }
    @Test public void frozenVariantRejectsChangedPrecisionEvenWithSameIdAndFingerprint() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"int8","INT8",null);
        ResearchStageExecutionConfig frozen=ResearchStageExecutionConfig.enabled(ModelRole.CHARACTER,m,m.manifest().variants().get(0),ExecutionProfile.tfliteCpu(2));
        assertSame(m.manifest().variants().get(0),frozen.requireVariant(m));
        JSONObject changed=new JSONObject(m.manifest().rawJson());changed.getJSONArray("variants").getJSONObject(0).put("precision","fp32");
        InstalledModel replacement=new InstalledModel(ModelManifest.parse(changed.toString()),m.directory(),m.fingerprint());
        assertThrows(IllegalStateException.class,()->frozen.requireVariant(replacement));
        assertEquals("cpu_2",frozen.toJson().getString("requested_execution_choice"));
        assertTrue(frozen.toJson().getBoolean("xnnpack_requested"));
    }
    @Test public void frozenVariantRejectsChangedModelBytesAndInputContract() throws Exception {
        InstalledModel m=RuntimeEvidenceFixture.model(temporary.newFolder(),"fp32","FLOAT32",null);
        ResearchStageExecutionConfig frozen=ResearchStageExecutionConfig.enabled(ModelRole.CHARACTER,m,m.manifest().variants().get(0),ExecutionProfile.tfliteCpu(1));
        JSONObject changed=new JSONObject(m.manifest().rawJson());changed.getJSONObject("input").put("width",4);
        assertThrows(IllegalStateException.class,()->frozen.requireVariant(new InstalledModel(ModelManifest.parse(changed.toString()),m.directory(),m.fingerprint())));
        Files.write(m.resolve("synthetic.tflite").toPath(),new byte[]{1,2});
        assertThrows(IllegalStateException.class,()->frozen.requireVariant(m));
    }
    @Test public void apkAndSplitHashesComeFromFilesAndAreDeterministic() throws Exception {
        File base=temporary.newFile("base.apk"),a=temporary.newFile("a.apk"),b=temporary.newFile("b.apk");
        Files.write(base.toPath(),new byte[]{1,2});Files.write(a.toPath(),new byte[]{3});Files.write(b.toPath(),new byte[]{4});
        JSONObject x=ApkIdentity.captureFiles(base,Arrays.asList(a,b)),y=ApkIdentity.captureFiles(base,Arrays.asList(b,a));
        assertEquals(RuntimeExecutionEvidence.sha256(base),x.getString("base_apk_sha256"));
        assertEquals(x.getString("apk_set_fingerprint"),y.getString("apk_set_fingerprint"));
        Files.write(b.toPath(),new byte[]{5});
        assertNotEquals(x.getString("apk_set_fingerprint"),ApkIdentity.captureFiles(base,Arrays.asList(a,b)).getString("apk_set_fingerprint"));
        assertThrows(Exception.class,()->ApkIdentity.captureFiles(new File(temporary.getRoot(),"missing.apk"),Collections.emptyList()));
    }
    @Test public void emptyAndMalformedJournalsCannotClaimExecutionProof() throws Exception {
        File root=temporary.newFolder();assertEquals("UNVERIFIED",RuntimeEvidenceJournal.summarize(root).getString("status"));
        File journal=new File(root,RuntimeEvidenceJournal.FILE);journal.getParentFile().mkdirs();Files.write(journal.toPath(),"not-json\n".getBytes(StandardCharsets.UTF_8));
        JSONObject summary=RuntimeEvidenceJournal.summarize(root);assertEquals("INCOMPLETE",summary.getString("status"));assertEquals(1,summary.getInt("malformed_records"));
        assertFalse(summary.getBoolean("int8_execution_proven"));
    }
    @Test public void realSessionWriterPersistsRuntimeEventsDuringFinalizingAndRejectsClosedSession() throws Exception {
        ExperimentSession session=new ExperimentSession();
        ExperimentSession.Prepared prepared=session.prepare("synthetic-f4b3","int8",TimerConfig.disabled(),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        ResearchSessionStore store=ResearchSessionStore.prepare(temporary.newFolder(),prepared,16,2,0,
                (bitmap,file)->{throw new AssertionError("Images forbidden in this contract test");},()->100L);
        store.startAt(1000L,200L);
        JSONObject allocated=new JSONObject().put("schema",RuntimeExecutionEvidence.SCHEMA).put("phase","ALLOCATED")
                .put("role","plate").put("kernel_execution_status","INT8_NOT_PROVEN").put("fixture",true);
        store.recordRuntimeEvidence(allocated);
        store.closeAdmission("synthetic",2000L,300L);
        store.recordRuntimeEvidence(new JSONObject(allocated.toString()).put("phase","FIRST_INVOKE_COMPLETED"));
        File archive=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
        assertEquals(ResearchSessionStore.State.COMPLETED,store.state());
        JSONObject evidence=RuntimeEvidenceJournal.summarize(store.directory());assertEquals(2,evidence.getInt("event_count"));
        assertEquals(prepared.sessionId,evidence.getJSONArray("events").getJSONObject(0).getString("session_id"));
        assertTrue(evidence.getBoolean("mt_invoke_observed"));assertFalse(evidence.getBoolean("int8_execution_proven"));
        ResearchArchive.verifyEntryHashes(archive);
        assertThrows(IllegalStateException.class,()->store.recordRuntimeEvidence(allocated));
    }
    @Test public void nativeArchivePreservesFrozenIdentityEvidenceAndEntryShaForDesktop() throws Exception {
        File root=temporary.newFolder();
        for(String child:new String[]{"samples","telemetry","pipeline","final"}) new File(root,child).mkdirs();
        JSONObject apk=new JSONObject().put("apk_sha256",repeat('a',64)).put("fixture",true);
        JSONObject session=new JSONObject().put("schema","alpr.mobile_research_session.v1").put("session_id","synthetic-f4b3")
                .put("state","COMPLETED").put("collection_complete",true).put("app_build",apk)
                .put("app_version","synthetic").put("execution",new JSONObject().put("stages",new JSONObject()))
                .put("sample_contract_version","alpr.mobile_research_samples.v2").put("collection_mode","automatic");
        write(new File(root,"session.json"),session.toString());
        JSONObject report=new JSONObject().put("schema","alpr.mobile_benchmark_report.v1").put("report_id","synthetic-f4b3")
                .put("app_build",new JSONObject().put("apk_sha256",repeat('b',64))).put("fixture",true);
        write(new File(root,"telemetry/report.json"),report.toString());
        JSONObject event=new JSONObject().put("schema",RuntimeExecutionEvidence.SCHEMA).put("phase","FIRST_INVOKE_COMPLETED")
                .put("role","plate").put("variant_id","synthetic-int8").put("kernel_execution_status","INT8_NOT_PROVEN")
                .put("public_io_precision_status","INT8_NOT_PROVEN").put("int8_execution_proven",false)
                .put("runtime_instance_id","synthetic-instance").put("session_id","synthetic-f4b3")
                .put("model_sha256",repeat('c',64)).put("fixture",true);
        write(new File(root,RuntimeEvidenceJournal.FILE),event+"\n");
        File archive=ResearchSessionStore.buildArchive(root,null);
        ResearchArchive.verifyEntryHashes(archive);
        try(ZipFile zip=new ZipFile(archive)) {
            JSONObject restored=new JSONObject(new String(zip.getInputStream(zip.getEntry("report.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(repeat('a',64),restored.getJSONObject("app_build").getString("apk_sha256"));
            JSONObject evidence=restored.getJSONObject("runtime_execution_evidence");
            assertEquals(1,evidence.getInt("event_count"));assertTrue(evidence.getBoolean("mt_invoke_observed"));assertFalse(evidence.getBoolean("mz_invoke_observed"));
            assertFalse(evidence.getBoolean("int8_execution_proven"));
            assertNotNull(zip.getEntry(RuntimeEvidenceJournal.FILE));
            JSONObject manifest=new JSONObject(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(RuntimeExecutionEvidence.sha256(new File(root,RuntimeEvidenceJournal.FILE)),manifest.getJSONObject("entry_sha256").getString(RuntimeEvidenceJournal.FILE));
        }
        String delivery=System.getenv("F4B3_INTEROP_OUTPUT");
        if(delivery!=null && !delivery.isEmpty()) Files.copy(archive.toPath(),new File(delivery).toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    private static void write(File file,String value) throws Exception {Files.write(file.toPath(),value.getBytes(StandardCharsets.UTF_8));}
    private static String repeat(char c,int count) {char[] chars=new char[count];Arrays.fill(chars,c);return new String(chars);}
}
