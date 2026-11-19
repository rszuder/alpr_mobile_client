package com.example.alpr_v1.experiment;

import android.app.Application;
import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.setup.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Final180EvidenceTest {
    public Final180EvidenceTest()throws Exception{}
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    final List<ResearchSessionStore> stores=new ArrayList<>();
    final AtomicLong clock=new AtomicLong(1_000_000_000L);
    final TvBboxCrop tv=TvBboxCropTest.selection(64,64);
    String variantLabel="R0";
    File rootOverride;
    ResearchExecutionConfig config(){
        ResearchStageExecutionConfig mt=new ResearchStageExecutionConfig(ModelRole.PLATE,true,"SYNTHETIC-MT","f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",64,64,3,"NHWC","RGB","FLOAT32");
        ResearchStageExecutionConfig mz=new ResearchStageExecutionConfig(ModelRole.CHARACTER,true,"SYNTHETIC-MZ","f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",64,64,3,"NHWC","RGB","FLOAT32");
        return new ResearchExecutionConfig("roi_budget",variantLabel,RoiBudgetPolicy.FULL_FRAME,RecognitionProfile.BALANCED,"64x64",false,false,false,true,false,false,
                ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE),mt,mz).withCameraSetup(tv.cameraSetup).withTvBboxCrop(tv)
                .withFinal180(Final180PhotoRoi.prepare(array("[{x:.25,y:.25},{x:.75,y:.25},{x:.75,y:.75},{x:.25,y:.75}]"),tv));
    }
    static JSONArray array(String value){try{return new JSONArray(value);}catch(Exception e){throw new IllegalArgumentException(e);}}
    ResearchSessionStore start(int capacity,boolean failedEncoder)throws Exception{
        ExperimentSession.Prepared p=new ExperimentSession().prepare("roi_budget","R0",TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity("SYNTHETIC-F6-FINAL180","TECHNICAL-NO-GT",1,"synthetic=true; DEVICE_RUN=NOT_RUN"),config());
        ResearchSessionStore s=ResearchSessionStore.prepare(rootOverride==null?temp.newFolder():rootOverride,p,capacity,64,0,(bitmap,target)->{
            if(failedEncoder)throw new IOException("SYNTHETIC_DISK_FAILURE");
            try(FileOutputStream out=new FileOutputStream(target)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("JPEG");out.getFD().sync();}
        },clock::get);stores.add(s);s.startAt(System.currentTimeMillis(),clock.get());return s;
    }
    static ContinuityStamp stamp(int n){return new ContinuityStamp(1,1,0,n,9_007_199_254_740_993L+n,SourceTimestampDomain.CAMERAX_SENSOR);}
    void capture(ResearchSessionStore s,Bitmap image,int n)throws Exception{
        String scene=s.f5aEvidence().ticket(stamp(n));if(!scene.isEmpty())s.captureSceneEvidence(scene,stamp(n),image);
        try(TvBboxCrop.Frame frame=tv.open(image,tv.geometry,tv.cameraSetup)){s.tvBboxEvidence().capture(frame,stamp(n)).complete("SCENE_EVIDENCE_ONLY","",0,"",n);}
    }
    File finish(ResearchSessionStore s,String name,String status)throws Exception{
        TvBboxProducerFixture.drain(s);s.closeAdmission("synthetic_completed",System.currentTimeMillis(),clock.get());
        File archive=s.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true,\"device_run\":\"NOT_RUN\"}","","","","",""),config());
        assertEquals(status,F6ProducerContractVerifier.validate(archive));export(archive,name);return archive;
    }
    static JSONObject json(File archive,String entry)throws Exception{try(ZipFile z=new ZipFile(archive)){return new JSONObject(new String(z.getInputStream(z.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8));}}
    static void export(File archive,String name)throws Exception{String dest=System.getenv("F6_FIXTURES");if(dest!=null){Files.createDirectories(Paths.get(dest));Files.copy(archive.toPath(),Paths.get(dest,name+".alprsession"),StandardCopyOption.REPLACE_EXISTING);}}
    static ThreadPoolExecutor writer(ResearchSessionStore s)throws Exception{java.lang.reflect.Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);return (ThreadPoolExecutor)f.get(s);}
    @After public void close()throws Exception{for(ResearchSessionStore s:stores)TvBboxProducerFixture.close(s);}

    @Test public void optionalProfileIsFrozenAndDoesNotAlterTvCalibration()throws Exception{
        ResearchExecutionConfig c=config();assertEquals(tv.digest,c.tvBboxCrop().digest);assertEquals(Final180PhotoRoi.PROFILE,c.toJson().getString("capture_profile"));
        assertEquals(16,c.final180PhotoRoi().left);assertEquals(48,c.final180PhotoRoi().right);
        JSONObject bad=c.final180PhotoRoi().toJson();bad.getJSONObject("photo_roi_camera_xy").put("left",0);SetupJson.seal(bad);
        assertThrows(IllegalArgumentException.class,()->Final180PhotoRoi.fromJson(bad,tv));
        assertThrows(IllegalArgumentException.class,()->Final180PhotoRoi.prepare(array("[{x:0,y:0},{x:1,y:0},{x:1,y:1},{x:0,y:1}]"),tv));
        assertEquals(5400,TimerConfig.fromMinutes("90").durationSeconds());
    }
    @Test public void operatorVariantLabelDoesNotReplaceActualR0Policy()throws Exception{
        variantLabel="R0_FINAL";
        ResearchSessionStore s=start(256,false);
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();
        File archive=finish(s,"lifecycle-r0-final-not-run","PASS");
        assertEquals("R0_FINAL",json(archive,"session.json").getJSONObject("execution").getString("variant"));
    }
    @Test public void stopBeforeFirstFrameRetainsFullSnapshotWithoutInventedMt()throws Exception{
        variantLabel="R0_FINAL";ResearchSessionStore s=start(256,false);
        String before=SetupJson.canonical(new JSONObject(new String(Files.readAllBytes(new File(s.directory(),"session.json").toPath()),StandardCharsets.UTF_8)).getJSONObject("execution"));
        File archive=finish(s,"lifecycle-stop-before-frame-EMPTY","PARTIAL");JSONObject session=json(archive,"session.json");
        assertEquals("PARTIAL",session.getString("state"));assertFalse(session.getBoolean("collection_complete"));
        assertEquals(0,session.getJSONObject("tv_bbox_input_evidence").getLong("frames"));
        assertEquals(before,SetupJson.canonical(session.getJSONObject("execution")));
        assertEquals(0,session.getJSONObject("f6_evidence").getLong("mt_started_count"));
        try(ZipFile zip=new ZipFile(archive)){assertEquals(0,zip.getEntry("samples/attempts.jsonl").getSize());}
    }
    @Test public void telemetryFinalizationFailurePreservesSessionAndFullCause()throws Exception{
        ResearchSessionStore s=start(256,false);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();TvBboxProducerFixture.drain(s);
        s.closeAdmission("manual",System.currentTimeMillis(),clock.get());
        String before=SetupJson.canonical(new JSONObject(new String(Files.readAllBytes(new File(s.directory(),"session.json").toPath()),StandardCharsets.UTF_8)).getJSONObject("execution"));
        ResearchSessionViewModel vm=new ResearchSessionViewModel(org.robolectric.RuntimeEnvironment.getApplication());vm.beginFinalizing();
        vm.finish(s,()->{throw new IOException("SYNTHETIC_TELEMETRY_FAILURE",new IllegalStateException("AUTHENTIC_TEST_CAUSE"));},config());
        java.lang.reflect.Field field=ResearchSessionViewModel.class.getDeclaredField("finalizer");field.setAccessible(true);
        ((ExecutorService)field.get(vm)).submit(()->{}).get(10,TimeUnit.SECONDS);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        JSONObject after=new JSONObject(new String(Files.readAllBytes(new File(s.directory(),"session.json").toPath()),StandardCharsets.UTF_8));
        assertEquals("ERROR",after.getString("state"));assertEquals("manual",after.getString("completion_reason"));
        assertEquals(before,SetupJson.canonical(after.getJSONObject("execution")));assertTrue(new File(s.directory(),Final180Evidence.FILE).isFile());
        String error=com.example.alpr_v1.logging.AppLog.lastFinalizationFailure(org.robolectric.RuntimeEnvironment.getApplication());
        assertTrue(error,error.contains("SYNTHETIC_TELEMETRY_FAILURE"));assertTrue(error,error.contains("AUTHENTIC_TEST_CAUSE"));
        assertTrue(vm.messages.getValue(),vm.messages.getValue().contains("Pliki zachowano"));vm.onCleared();
    }
    @Test public void interruptedProcessRecoversOnlyOnCopyWithOriginalRunningRecordPreserved()throws Exception{
        File files=temp.newFolder();rootOverride=new File(files,"research/sessions");ResearchSessionStore s=start(256,false);
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();TvBboxProducerFixture.drain(s);TvBboxProducerFixture.close(s);
        File metadata=new File(s.directory(),"session.json");JSONObject raw=new JSONObject(new String(Files.readAllBytes(metadata.toPath()),StandardCharsets.UTF_8)).put("process_owner","SYNTHETIC_DEAD_PROCESS");
        Files.write(metadata.toPath(),raw.toString().getBytes(StandardCharsets.UTF_8));byte[] before=Files.readAllBytes(metadata.toPath());
        android.content.Context context=new android.content.ContextWrapper(org.robolectric.RuntimeEnvironment.getApplication()){@Override public File getFilesDir(){return files;}};
        List<File> copies=ResearchSessionStore.recoverInterrupted(context);assertEquals(1,copies.size());assertArrayEquals(before,Files.readAllBytes(metadata.toPath()));
        JSONObject recovered=new JSONObject(new String(Files.readAllBytes(new File(copies.get(0),"session.json").toPath()),StandardCharsets.UTF_8));
        assertEquals("PARTIAL",recovered.getString("state"));assertEquals("process_interrupted",recovered.getString("completion_reason"));
        assertEquals(SetupJson.canonical(raw.getJSONObject("execution")),SetupJson.canonical(recovered.getJSONObject("execution")));
        File archive=new File(copies.get(0),"final/"+s.sessionId()+".alprsession");assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));export(archive,"lifecycle-process-interrupted-copy-PARTIAL");
        assertTrue(ResearchSessionStore.recoverInterrupted(context).isEmpty());
    }
    @Test public void explicitNotRunAndMissingImagesNeverBecomeEmptyMtExecutions()throws Exception{
        for(boolean failed:new boolean[]{false,true}){
            ResearchSessionStore s=start(256,failed);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();
            File archive=finish(s,failed?"not-run-image-write-error":"not-run",""+(failed?"PARTIAL":"PASS"));
            assertEquals(0,json(archive,"session.json").getJSONObject("f6_evidence").getLong("mt_started_count"));
            try(ZipFile z=new ZipFile(archive)){assertEquals(0,z.getEntry("samples/attempts.jsonl").getSize());}
        }
        ResearchSessionStore absent=start(256,false);absent.tvBboxEvidence().rejected(null,stamp(1),"source_frame_unavailable").complete("SOURCE_UNAVAILABLE","source_frame_unavailable");
        finish(absent,"not-run-source-unavailable","PARTIAL");
    }
    @Test public void fullQueueRecordsLossAndKeepsEarlierDurableFrames()throws Exception{
        ResearchSessionStore s=start(256,false);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);TvBboxProducerFixture.drain(s);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ThreadPoolExecutor w=writer(s);
        w.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
        while(w.getQueue().offer(()->{})){}
        try{capture(s,b,2);}finally{release.countDown();b.recycle();}
        File archive=finish(s,"queue-full","PARTIAL");assertTrue(json(archive,"session.json").getLong("dropped_telemetry_count")>0);
        try(ZipFile z=new ZipFile(archive)){assertTrue(z.stream().anyMatch(e->e.getName().startsWith("samples/frames/tv-camera-")));}
    }
    @Test public void interruptedStartWithoutAttemptIsUnknownAndReexportPreservesEvidence()throws Exception{
        ResearchSessionStore s=start(256,false);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();TvBboxProducerFixture.drain(s);
        JSONObject frame;try(java.io.BufferedReader in=Files.newBufferedReader(new File(s.directory(),TvBboxEvidence.JOURNAL).toPath())){frame=new JSONObject(in.readLine());}
        s.final180().event("MT_STARTED",stamp(1),frame.getString("frame_key"),frame.getString("scene_event_id"),1,"SYNTHETIC-INTERRUPTED-CALL","SYNTHETIC-INTERRUPTED-CALL",null,null);
        TvBboxProducerFixture.drain(s);TvBboxProducerFixture.close(s);
        File meta=new File(s.directory(),"session.json");JSONObject session=new JSONObject(new String(Files.readAllBytes(meta.toPath()),StandardCharsets.UTF_8));
        session.put("state","PARTIAL").put("collection_complete",false).put("completion_reason","process_interrupted").put("pending_sample_loss_unknown",true);
        Files.write(meta.toPath(),session.toString().getBytes(StandardCharsets.UTF_8));
        File archive=ResearchSessionStore.buildArchive(s.directory(),null);assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));export(archive,"interrupted-start");
        byte[] before=Files.readAllBytes(new File(s.directory(),Final180Evidence.FILE).toPath());File rebuilt=ResearchSessionStore.buildArchive(s.directory(),null);
        assertArrayEquals(before,Files.readAllBytes(new File(s.directory(),Final180Evidence.FILE).toPath()));
        assertEquals("PARTIAL",F6ProducerContractVerifier.validate(rebuilt));
    }
    @Test public void brokenJournalTailExportsPartialAndRetainsCommittedEvidence()throws Exception{
        ResearchSessionStore s=start(256,false);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();TvBboxProducerFixture.drain(s);TvBboxProducerFixture.close(s);
        Files.write(new File(s.directory(),Final180Evidence.FILE).toPath(),"{\"event_sequence\":".getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
        File meta=new File(s.directory(),"session.json");JSONObject session=new JSONObject(new String(Files.readAllBytes(meta.toPath()),StandardCharsets.UTF_8));
        session.put("state","PARTIAL").put("collection_complete",false).put("completion_reason","process_interrupted");Files.write(meta.toPath(),session.toString().getBytes(StandardCharsets.UTF_8));
        File archive=ResearchSessionStore.buildArchive(s.directory(),null);assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));export(archive,"interrupted-broken-tail");
        assertTrue(json(archive,"session.json").getJSONObject("f6_evidence").getLong("projection_error_count")>0);
    }
    @Test public void longSessionStreamsBeyond4096FramesAnd32MiBWithoutRetainingHistory()throws Exception{
        ResearchSessionStore s=start(256,false);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        for(int n=1;n<=4100;n++){
            clock.set(1_000_000_000L+5_400_000_000_000L*n/4100);capture(s,b,n);
            s.f5aEvidence().observation(s.f5aEvidence().sceneFor(stamp(n)),notRunMp(n),"synthetic_mp_disabled");
            if(n%4==0)TvBboxProducerFixture.drain(s);
        }
        addSceneLoadMetadata(s);
        b.recycle();File archive=finish(s,"long-session-4100-frames","PASS");JSONObject session=json(archive,"session.json");
        assertEquals(4100,session.getJSONObject("tv_bbox_input_evidence").getLong("frames"));
        assertEquals(12300,session.getJSONObject("f6_evidence").getLong("events_persisted"));
        assertEquals(0,session.getJSONObject("f6_evidence").getLong("mt_started_count"));
        assertTrue(new File(s.directory(),F5aEvidence.SCENES).length()>=34_608_299L);
        int observations=0;try(android.util.JsonReader in=new android.util.JsonReader(Files.newBufferedReader(new File(s.directory(),F5aEvidence.SCENES).toPath()))){
            in.beginObject();while(in.hasNext()){if("mp_observations".equals(in.nextName())){in.beginArray();while(in.hasNext()){in.skipValue();observations++;}in.endArray();}else in.skipValue();}in.endObject();}
        assertEquals(4100,observations);
        java.lang.reflect.Field f=TvBboxEvidence.class.getDeclaredField("frames");f.setAccessible(true);assertTrue(((Map<?,?>)f.get(s.tvBboxEvidence())).size()<=64);
        try(ZipFile zip=new ZipFile(archive)){assertEquals(null,zip.stream().filter(e->e.getName().contains("f6-sort-")).findFirst().orElse(null));}
        System.out.println("F6_SCALE frames=4100 mp_not_run=4100 events=12300 mt_started=0 scene_bytes="
                +new File(s.directory(),F5aEvidence.SCENES).length()+" max_heap_bytes="+Runtime.getRuntime().maxMemory());
    }

    static JSONObject notRunMp(int n)throws Exception{
        return new JSONObject().put("trace_frame_id",n).put("source_stamp",F5aEvidence.stampJson(stamp(n)))
                .put("execution_status","NOT_RUN").put("not_run_reason","mp_disabled_in_synthetic_r0")
                .put("raw_count",JSONObject.NULL).put("rejected_class_count",JSONObject.NULL)
                .put("rejected_class_ids",JSONObject.NULL).put("selected_rois",new JSONArray());
    }

    /** Test-only extension, before finalization: use the existing scene journal serializer and writer.
     * It is serialization load, not MP output. Never edit an already exported ZIP. */
    static void addSceneLoadMetadata(ResearchSessionStore s)throws Exception{
        TvBboxProducerFixture.drain(s);F5aEvidence evidence=s.f5aEvidence();
        java.lang.reflect.Field field=F5aEvidence.class.getDeclaredField("scenes");field.setAccessible(true);
        Map<?,?> scenes=(Map<?,?>)field.get(evidence);assertEquals(1,scenes.size());
        JSONObject scene=(JSONObject)scenes.values().iterator().next();assertEquals("WRITTEN",scene.getString("write_status"));
        char[] chars=new char[33_100_000];Arrays.fill(chars,'x');
        scene.put("_synthetic_test",new JSONObject().put("purpose","serialization_load_only").put("not_model_output",true).put("padding",new String(chars)));
        java.lang.reflect.Method journal=F5aEvidence.class.getDeclaredMethod("journal",String.class,JSONObject.class);journal.setAccessible(true);
        journal.invoke(evidence,"SCENE",scene);TvBboxProducerFixture.drain(s);scene.remove("_synthetic_test");
    }

    @Test public void deliveryGateRejectsTheExactPreviousLongArchive()throws Exception{
        String path=System.getenv("F6_PREVIOUS_BAD_ARCHIVE");Assume.assumeTrue("Prior delivery supplied explicitly for the regression",path!=null);
        File bad=new File(path);assertEquals("0323d3e5d1f79fe32e99c193a6ce71ee558b31ac0d0ed8aca57e0b7c48212f5f",
                com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(bad));
        assertEquals("PASS",Final180ArchiveValidator.validate(bad)); // Historical gate missed nested MP fields.
        Exception rejected=assertThrows(Exception.class,()->F6ProducerContractVerifier.validate(bad));
        assertTrue(rejected.toString(),rejected.toString().contains("MP_OBSERVATION_FIELDS_MISSING"));
        System.out.println("F6_PREVIOUS_DELIVERY_CONTRACT=FAIL "+rejected.getMessage());
    }

    @Test public void deliveryGateRejectsMpReboundToAnotherSourceOrTrace()throws Exception{
        for(boolean trace:new boolean[]{false,true}){
            ResearchSessionStore s=start(256,false);Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);capture(s,b,1);b.recycle();
            JSONObject mp=notRunMp(trace?1:2);if(trace)mp.put("trace_frame_id",7);
            s.f5aEvidence().observation(s.f5aEvidence().sceneFor(stamp(1)),mp,"synthetic_mp_disabled");
            TvBboxProducerFixture.drain(s);s.closeAdmission("synthetic_completed",System.currentTimeMillis(),clock.get());
            File archive=s.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true}","","","","",""),config());
            assertEquals("PASS",Final180ArchiveValidator.validate(archive));
            Exception rejected=assertThrows(Exception.class,()->F6ProducerContractVerifier.validate(archive));
            assertTrue(rejected.toString(),rejected.toString().contains(trace?"trace/frame identity conflict":"source frame missing"));
        }
    }
}
