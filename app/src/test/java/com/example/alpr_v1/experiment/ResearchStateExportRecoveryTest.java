package com.example.alpr_v1.experiment;

import android.app.Application;
import android.net.Uri;
import android.os.SystemClock;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.ui.ResearchConfigurationText;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class ResearchStateExportRecoveryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    ResearchSessionViewModel vm;
    static void drain(ResearchSessionViewModel vm,String field)throws Exception{
        java.lang.reflect.Field f=ResearchSessionViewModel.class.getDeclaredField(field);f.setAccessible(true);
        ((ExecutorService)f.get(vm)).submit(()->{}).get(30,TimeUnit.SECONDS);
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
    }
    @After public void close(){if(vm!=null)vm.onCleared();}

    @Test public void terminalOldCallbackCannotFinishNewOperationAndRestartIsExplicit()throws Exception{
        File root=temp.newFolder();ResearchOperationState ops=new ResearchOperationState(root,s->{});
        String a=ops.begin("A","COPYING_DOCUMENT","content://synthetic/A");
        assertTrue(ops.end(a,"FAILED",null,new IOException("SYNTHETIC_DISK_FULL")));
        String b=ops.begin("B","QUEUE_DRAIN","");
        assertFalse(ops.update(a,"VERIFYING_DOCUMENT",1,1,null));assertFalse(ops.end(a,"SUCCEEDED",null,null));
        assertEquals(b,ops.latest().id);assertTrue(ops.latest().active());
        ResearchOperationState sameProcess=new ResearchOperationState(root,s->{});sameProcess.restore();assertTrue(sameProcess.hasBlockingWork());assertEquals(b,sameProcess.latest().id);
        File deadProcess=temp.newFolder();for(File f:root.listFiles())Files.copy(f.toPath(),new File(deadProcess,f.getName()).toPath());
        ResearchOperationState restored=new ResearchOperationState(deadProcess,s->{});restored.restore();
        assertTrue(restored.active().isEmpty());
        JSONObject previous=new JSONObject(ResearchSessionStore.read(new File(deadProcess,b+".json")));
        assertEquals("INTERRUPTED",previous.getString("status"));assertTrue(previous.getString("error").contains("QUEUE_DRAIN"));ops.end(b,"CANCELLED",null,null);assertFalse(sameProcess.hasBlockingWork());
    }
    static class ClosedOutput extends ByteArrayOutputStream {boolean closed;@Override public void close()throws IOException{closed=true;super.close();}}
    @Test public void documentSuccessRequiresCloseThenExactReadbackAndCanBeRepeated()throws Exception{
        File source=temp.newFile();byte[] bytes=new byte[9*1024*1024];new Random(37).nextBytes(bytes);Files.write(source.toPath(),bytes);
        String expected=RuntimeExecutionEvidence.sha256(source);
        for(int copy=0;copy<2;copy++){
            Uri uri=Uri.parse("content://synthetic/export/"+copy);ClosedOutput out=new ClosedOutput();
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerOutputStream(uri,out);
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int o,int n){assertTrue("close before readback",out.closed);return super.read(b,o,n);}});
            List<String> stages=new ArrayList<>();ResearchDocumentExport.Receipt r=ResearchDocumentExport.copy(RuntimeEnvironment.getApplication().getContentResolver(),uri,source,(stage,done,total)->stages.add(stage));
            assertEquals(bytes.length,r.bytes);assertEquals(expected,r.sha256);assertEquals("VERIFIED_SHA256",r.verification);
            assertArrayEquals(bytes,out.toByteArray());assertTrue(stages.contains("COPYING_DOCUMENT"));assertTrue(stages.contains("VERIFYING_DOCUMENT"));
        }
        assertEquals(expected,RuntimeExecutionEvidence.sha256(source));
    }
    @Test public void documentWriteCloseAndReadbackFailuresNeverReportVerified()throws Exception{
        File source=temp.newFile();Files.write(source.toPath(),new byte[]{1,2,3,4});
        for(String failure:new String[]{"write","close","mismatch","read"}){
            Uri uri=Uri.parse("content://synthetic/failure/"+failure);
            OutputStream out=new ByteArrayOutputStream(){
                @Override public synchronized void write(byte[] b,int off,int len){if(failure.equals("write"))throw new IllegalStateException("SYNTHETIC_NO_SPACE");super.write(b,off,len);}
                @Override public void close()throws IOException{if(failure.equals("close"))throw new IOException("SYNTHETIC_CLOSE_FAILED");super.close();}};
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerOutputStream(uri,out);
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerInputStream(uri,new InputStream(){int n;
                @Override public int read()throws IOException{if(failure.equals("read"))throw new IOException("SYNTHETIC_READ_FAILED");return n++==0?9:-1;}});
            assertThrows(failure,Exception.class,()->ResearchDocumentExport.copy(RuntimeEnvironment.getApplication().getContentResolver(),uri,source,(s,d,t)->{}));
        }
    }
    @Test public void missingReadPermissionRetainsCopyWithUnverifiedStatus()throws Exception{
        File source=temp.newFile();Files.write(source.toPath(),new byte[]{1,2,3});Uri uri=Uri.parse("content://synthetic/denied");
        android.content.ContentResolver resolver=RuntimeEnvironment.getApplication().getContentResolver();
        Shadows.shadowOf(resolver).registerOutputStream(uri,new ByteArrayOutputStream());
        Shadows.shadowOf(resolver).registerInputStreamSupplier(uri,()->{throw new SecurityException("SYNTHETIC_READ_NOT_GRANTED");});
        assertTrue(ResearchDocumentExport.copy(resolver,uri,source,(s,d,t)->{}).verification.startsWith("SAVED_UNVERIFIED"));
    }
    @Test public void inventoryDoesNotRewriteOldPartialSessionsOrBlockAdmission()throws Exception{
        Application app=RuntimeEnvironment.getApplication();File root=ResearchSessionStore.sessionsRoot(app);List<File> metadata=new ArrayList<>();
        for(int n=0;n<39;n++){
            File f=new File(root,"SYNTHETIC-OLD-"+n+"/session.json");f.getParentFile().mkdirs();
            ResearchSessionStore.atomicText(f,new JSONObject().put("session_id","SYNTHETIC-OLD-"+n).put("state","ERROR")
                    .put("created_at","2026-11-09T10:00:00Z").put("finished_at","2026-11-09T10:01:00Z").put("completion_reason","preparation_failed").toString());metadata.add(f);
        }
        List<String> before=new ArrayList<>();for(File f:metadata)before.add(RuntimeExecutionEvidence.sha256(f));
        vm=new ResearchSessionViewModel(app);assertTrue(vm.ready());drain(vm,"historyExecutor");assertTrue(vm.ready());
        List<ResearchSessionSummary> rows=ResearchSessionSummary.catalog(root);assertEquals(39,rows.size());
        for(int i=0;i<metadata.size();i++)assertEquals(before.get(i),RuntimeExecutionEvidence.sha256(metadata.get(i)));
        for(ResearchSessionSummary row:rows){assertFalse(row.hasArchive());assertTrue(row.partialReason().contains("preparation_failed"));}
    }
    @Test public void closedArchiveMetadataWinsOverOriginalRecoverySidecarAndIsImmutable()throws Exception{
        File dir=temp.newFolder(),archive=new File(dir,"final/S.alprsession");archive.getParentFile().mkdirs();
        JSONObject frozen=new JSONObject().put("capture_profile",Final180PhotoRoi.PROFILE).put("experiment_type","roi_budget").put("roi_budget_policy","r0_full_frame");
        JSONObject inside=new JSONObject().put("session_id","S").put("state","PARTIAL").put("collection_complete",false).put("execution",frozen);
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(archive))){zip.putNextEntry(new ZipEntry("session.json"));zip.write(inside.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
        ResearchSessionStore.atomicText(new File(dir,"session.json"),new JSONObject().put("state","ERROR").put("experiment_type","end_to_end").toString());
        ResearchSessionSummary summary=ResearchSessionSummary.load(Collections.singletonList(archive)).get(0);
        assertEquals("PARTIAL",summary.session.getString("state"));assertEquals("F6 · test końcowy",ResearchConfigurationText.title(summary.execution));
        inside.put("state","COMPLETED");frozen.put("experiment_type","end_to_end");
        assertEquals("PARTIAL",summary.session.getString("state"));assertEquals("roi_budget",summary.execution.getString("experiment_type"));
        assertFalse(ResearchConfigurationText.isF6(new JSONObject().put("variant","R0_FINAL").put("experiment_type","roi_budget")));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void selectedF6RecoveryPreservesOriginalAndUsesPartialArchiveAfterRestart()throws Exception{
        Application app=RuntimeEnvironment.getApplication();Final180EvidenceTest producer=new Final180EvidenceTest();
        producer.rootOverride=ResearchSessionStore.sessionsRoot(app);ResearchSessionStore store=producer.start(256,false);
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
        producer.capture(store,image,1);image.recycle();TvBboxProducerFixture.drain(store);TvBboxProducerFixture.close(store);
        File metadata=new File(store.directory(),"session.json");
        // A live owner may not be recovered underneath its writer.
        assertThrows(IOException.class,()->ResearchSessionStore.recoverCopy(store.directory(),app));
        JSONObject raw=new JSONObject(ResearchSessionStore.read(metadata)).put("process_owner","SYNTHETIC_DEAD_PROCESS");
        ResearchSessionStore.atomicText(metadata,raw.toString());String before=RuntimeExecutionEvidence.sha256(metadata);
        vm=new ResearchSessionViewModel(app);drain(vm,"historyExecutor");
        File desired=new File(store.directory(),"final/"+store.sessionId()+".alprsession");assertFalse(desired.exists());
        File recovered=vm.recoverArchive(desired).get(30,TimeUnit.SECONDS);
        assertEquals(before,RuntimeExecutionEvidence.sha256(metadata));assertEquals("PARTIAL",F6ProducerContractVerifier.validate(recovered));
        ResearchSessionSummary summary=ResearchSessionSummary.load(Collections.singletonList(recovered)).get(0);
        assertEquals("PARTIAL",summary.session.getString("state"));assertEquals("RUNNING",new JSONObject(ResearchSessionStore.read(metadata)).getString("state"));
        vm.onCleared();vm=new ResearchSessionViewModel(app);drain(vm,"historyExecutor");
        assertEquals(before,RuntimeExecutionEvidence.sha256(metadata));assertEquals(1,ResearchSessionSummary.catalog(ResearchSessionStore.sessionsRoot(app)).size());
        assertEquals("PARTIAL",ResearchSessionSummary.catalog(ResearchSessionStore.sessionsRoot(app)).get(0).session.getString("state"));
    }
    @Test public void failureBeforeFinalizerEnqueueClearsOnlyItsOwnerAndRetainsSnapshot()throws Exception{
        Application app=RuntimeEnvironment.getApplication();File fake=temp.newFile();Files.write(fake.toPath(),new byte[]{1,2,3});app.getApplicationInfo().sourceDir=fake.toString();
        vm=new ResearchSessionViewModel(app);drain(vm,"historyExecutor");
        ExperimentSession.Prepared p=vm.experiment.prepare("runtime","SYNTHETIC",TimerConfig.disabled(),ThermalConfig.disabled(),new ExperimentIdentity("SYNTHETIC","STOP_BARRIER",1,"TEST_ONLY"),null);
        ResearchSessionStore store=vm.prepare(p);
        try{
            long now=Math.max(SystemClock.elapsedRealtimeNanos(),store.preparedElapsedNanos);store.startAt(1000,now);store.closeAdmission("manual",2000,now+1000);
            vm.beginFinalizing();assertTrue(vm.finalizing());
            vm.failFinalization(store,new CompletionException(new IOException("SYNTHETIC_CROP_BARRIER_FAILURE")));
            assertFalse(vm.finalizing());assertTrue(vm.ready());assertEquals(ResearchSessionStore.State.ERROR,store.state());
            assertTrue(new File(store.directory(),"session.json").isFile());
            assertTrue(vm.operationDescription().contains("SYNTHETIC_CROP_BARRIER_FAILURE"));
            String terminal=vm.operations.latest().id;vm.finish(store,()->{throw new AssertionError("A stale finalizer must not run");},null);
            assertEquals(terminal,vm.operations.latest().id);assertFalse(vm.finalizing());
        }finally{TvBboxProducerFixture.close(store);}
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void finalizerFatalErrorIsTerminalAndPreservesFrozenF6()throws Exception{
        Final180EvidenceTest producer=new Final180EvidenceTest();producer.rootOverride=temp.newFolder();
        ResearchSessionStore store=producer.start(256,false);TvBboxProducerFixture.drain(store);
        store.closeAdmission("manual",System.currentTimeMillis(),producer.clock.get());
        vm=new ResearchSessionViewModel(RuntimeEnvironment.getApplication());
        try{
            // The existing API may finalize an explicitly supplied, closed store without adopting a new measurement.
            vm.beginFinalizing();vm.finish(store,()->{throw new OutOfMemoryError("SYNTHETIC_FINALIZER_FATAL");},producer.config());
            drain(vm,"finalizer");assertFalse(vm.finalizing());assertTrue(vm.ready());
            assertEquals(ResearchSessionStore.State.ERROR,store.state());assertEquals("FAILED",vm.operations.latest().status);
            assertTrue(vm.operationHistoryDescription().contains("SYNTHETIC_FINALIZER_FATAL"));
            JSONObject saved=new JSONObject(ResearchSessionStore.read(new File(store.directory(),"session.json")));
            assertEquals(Final180PhotoRoi.PROFILE,saved.getJSONObject("execution").getString("capture_profile"));
        }finally{TvBboxProducerFixture.close(store);}
    }
    @Test public void receiptStorageFailureIsVisibleWithoutPretendingItWasPersisted()throws Exception{
        File notDirectory=temp.newFile();ResearchOperationState state=new ResearchOperationState(notDirectory,s->{});
        String id=state.begin("SYNTHETIC","PREPARING","");state.end(id,"FAILED",null,new IOException("SYNTHETIC_IO_FAILURE"));
        assertFalse(state.hasBlockingWork());assertTrue(state.historyDescription().contains("Nie zapisano trwale"));
    }
    @Test public void lateSafResultDuringMeasurementNeverCopiesOrStopsThatMeasurement()throws Exception{
        vm=new ResearchSessionViewModel(RuntimeEnvironment.getApplication());drain(vm,"historyExecutor");
        ExperimentSession.Prepared prepared=vm.experiment.prepare("runtime","SYNTHETIC",TimerConfig.disabled(),ThermalConfig.disabled(),new ExperimentIdentity("SYNTHETIC","LATE_SAF",1,"TEST_ONLY"),null);
        assertTrue(vm.experiment.startPrepared(prepared,1000,1000));
        File source=temp.newFile();Files.write(source.toPath(),new byte[]{1,2,3});
        Uri uri=Uri.parse("content://synthetic/late-result");ClosedOutput output=new ClosedOutput();
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerOutputStream(uri,output);
        vm.exportArchive(source,uri);assertEquals(0,output.size());assertFalse(vm.documentBusy());
        assertTrue(vm.experiment.isRunning());assertEquals("CANCELLED",vm.operations.latest().status);
        vm.experiment.finish(ExperimentSession.CompletionReason.MANUAL);
    }
    @Test public void realInterruptedExportsValidateWithoutChangingTheirPartialEvidence()throws Exception{
        String path=System.getenv("F6_REAL_EXPORTS");Assume.assumeNotNull(path);
        File[] archives=new File(path).listFiles((d,n)->n.endsWith(".alprsession"));assertNotNull(archives);assertEquals(2,archives.length);
        for(File archive:archives){String before=RuntimeExecutionEvidence.sha256(archive);
            assertEquals("PARTIAL",Final180ArchiveValidator.validate(archive));F6ProducerContractVerifier.validateScenes(archive);
            assertEquals(before,RuntimeExecutionEvidence.sha256(archive));
            JSONObject meta=Final180EvidenceTest.json(archive,"session.json");assertEquals("exp-1794570535251-7b6aa4a3",meta.getString("session_id"));
            assertFalse(meta.getBoolean("collection_complete"));assertFalse(meta.getBoolean("mt_detection_roles_complete"));
        }
    }
    @Test public void realClosedArchiveExportsThroughRetainedOwnerTwiceWithoutRefinalization()throws Exception{
        String path=System.getenv("F6_REAL_EXPORTS");Assume.assumeNotNull(path);
        File input=new File(path,"alpr_session_20261113_131600.alprsession");String sha=RuntimeExecutionEvidence.sha256(input);
        Application app=RuntimeEnvironment.getApplication();File dir=new File(ResearchSessionStore.sessionsRoot(app),"exp-1794570535251-7b6aa4a3");
        File source=new File(dir,"final/exp-1794570535251-7b6aa4a3.alprsession");source.getParentFile().mkdirs();Files.copy(input.toPath(),source.toPath());
        vm=new ResearchSessionViewModel(app);drain(vm,"historyExecutor");List<String> receipts=new ArrayList<>();
        for(int n=0;n<2;n++){
            Uri uri=Uri.parse("content://synthetic/real-copy/"+n);File document=temp.newFile();
            Shadows.shadowOf(app.getContentResolver()).registerOutputStreamSupplier(uri,()->{try{return new FileOutputStream(document);}catch(Exception e){throw new IllegalStateException(e);}});
            Shadows.shadowOf(app.getContentResolver()).registerInputStreamSupplier(uri,()->{try{return new FileInputStream(document);}catch(Exception e){throw new IllegalStateException(e);}});
            vm.exportArchive(source,uri);drain(vm,"historyExecutor");assertFalse(vm.documentBusy());
            assertEquals("SUCCEEDED",vm.operations.latest().status);assertTrue(vm.operations.latest().detail.contains(sha));
            assertEquals(sha,RuntimeExecutionEvidence.sha256(document));assertEquals(sha,RuntimeExecutionEvidence.sha256(source));receipts.add(vm.operations.latest().id);
        }
        assertNotEquals(receipts.get(0),receipts.get(1));assertFalse(new File(dir,"session.json").exists());
        // A real producer archive is read-only here: even a document failure cannot rewrite or refinalize it.
        Uri bad=Uri.parse("content://synthetic/real-copy/failure");
        Shadows.shadowOf(app.getContentResolver()).registerOutputStream(bad,new OutputStream(){@Override public void write(int b)throws IOException{throw new IOException("SYNTHETIC_DOCUMENT_NO_SPACE");}});
        vm.exportArchive(source,bad);drain(vm,"historyExecutor");assertFalse(vm.documentBusy());assertTrue(vm.ready());
        assertEquals("FAILED",vm.operations.latest().status);assertTrue(vm.operations.latest().error.contains("SYNTHETIC_DOCUMENT_NO_SPACE"));
        assertEquals(sha,RuntimeExecutionEvidence.sha256(source));
        assertTrue(vm.operations.historyDescription().contains(receipts.get(0)));
    }
}
