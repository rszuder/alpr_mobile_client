package com.example.alpr_v1.experiment;

import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.vision.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Synthetic producer acceptance; no models, Android device, historical files or desktop writes. */
public class F5aEvidenceTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final List<ResearchSessionStore> stores=new ArrayList<>();
    private final AtomicLong clock=new AtomicLong(1000L);
    private final AtomicInteger created=new AtomicInteger(),released=new AtomicInteger();
    private static ContinuityStamp stamp(long generation,long sequence) {
        return new ContinuityStamp(generation,generation,0,sequence,sequence*1000000,SourceTimestampDomain.CAMERAX_SENSOR);
    }
    private ResearchSessionStore running() throws Exception { return running(256,TimerConfig.disabled()); }
    private ResearchSessionStore running(int capacity,TimerConfig timer) throws Exception {
        ExperimentSession.Prepared prepared=new ExperimentSession().prepare("roi_budget","R2",timer,
                ThermalConfig.disabled(),new ExperimentIdentity("F5A-SYNTHETIC-PILOT","SYNTHETIC",1,"synthetic=true"),null);
        ResearchSessionStore store=ResearchSessionStore.prepare(temporary.newFolder(),prepared,capacity,64,0,
                (bitmap,file)->{throw new AssertionError("Host acceptance must use owned JPEG payloads");},clock::get);
        stores.add(store);store.startAt(1000000,1000);return store;
    }
    private ThreadPoolExecutor writer(ResearchSessionStore store) throws Exception {
        java.lang.reflect.Field field=ResearchSessionStore.class.getDeclaredField("writer");field.setAccessible(true);
        return (ThreadPoolExecutor)field.get(store);
    }
    private void drain(ResearchSessionStore store) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        // Image tasks can enqueue one final journal write behind the first barrier.
        for(int pass=0;pass<2;pass++)while(true)try {writer(store).submit(()->{}).get(5,TimeUnit.SECONDS);break;}
        catch(RejectedExecutionException full) {if(System.nanoTime()>until)throw full;Thread.sleep(5);}
    }
    private static void encodeHostJpeg(File file,int width,int height,int color)throws IOException {
        // Android javac bootclasspath omits java.desktop; the host JVM supplies its real JPEG codec.
        try {
            Class<?> bitmap=Class.forName("java.awt.image.BufferedImage");
            Object image=bitmap.getConstructor(int.class,int.class,int.class).newInstance(width,height,1);
            int[] pixels=new int[width*height];Arrays.fill(pixels,color);
            if(color==0x112233)for(int y=60;y<Math.min(height,80);y++)for(int x=80;x<Math.min(width,145);x++)
                pixels[y*width+x]=(y>64 && y<76 && x%8<3) ? 0x101010 : 0xeeeeee;
            bitmap.getMethod("setRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class)
                    .invoke(image,0,0,width,height,pixels,0,width);
            Object result=Class.forName("javax.imageio.ImageIO").getMethod("write",Class.forName("java.awt.image.RenderedImage"),String.class,File.class)
                    .invoke(null,image,"jpg",file);
            if(!Boolean.TRUE.equals(result))throw new IOException("Host JPEG codec unavailable");
        }catch(ReflectiveOperationException error){throw new IOException(error);}
    }
    private F5aEvidence.Frame image(int w,int h,int color,boolean failure) {
        created.incrementAndGet();return new F5aEvidence.Frame() {
            final AtomicBoolean owned=new AtomicBoolean(true);
            public int width(){return w;}public int height(){return h;}
            public void write(File target)throws IOException {
                if(failure)throw new IOException("synthetic JPEG / no space failure");
                encodeHostJpeg(target,w,h,color);
            }
            public void release(){assertTrue("One owner releases each image once",owned.compareAndSet(true,false));released.incrementAndGet();}
        };
    }
    private String capture(ResearchSessionStore store,ContinuityStamp stamp) {
        String ticket=store.f5aEvidence().ticket(stamp);assertFalse(ticket.isEmpty());
        store.captureSceneEvidence(ticket,stamp,160,100,()->image(160,100,0x224466,false));return ticket;
    }
    private JSONObject mp(ContinuityStamp stamp,int raw,int[] classes,int[][] rois) throws Exception {
        JSONArray rejected=new JSONArray();for(int id:classes)rejected.put(id);
        JSONArray selected=new JSONArray();for(int[] roi:rois)selected.put(new JSONObject()
                .put("left",roi[0]).put("top",roi[1]).put("right",roi[2]).put("bottom",roi[3])
                .put("entity_id",1).put("vehicle_track_id",1));
        return new JSONObject().put("trace_frame_id",7).put("source_stamp",F5aEvidence.stampJson(stamp))
                .put("execution_status","EXECUTED").put("not_run_reason",JSONObject.NULL).put("raw_count",raw)
                .put("rejected_class_count",classes.length).put("rejected_class_ids",rejected).put("selected_rois",selected);
    }
    private AcquisitionAttemptRecord call(ResearchAttemptBatch batch,ContinuityStamp stamp,int l,int r) {
        AcquisitionAttemptRecord call=batch.beginMt(1,1,l,0,r,100,64,32);assertNotNull(call);
        call.inputPayloadFactory=()->image(64,32,0x224466,false);call.inputPayload=call.inputPayloadFactory.get();
        call.sourcePayload=image(160,100,0x224466,false);
        call.put("evidence_kind","mt_input_roi");call.put("source_frame_expected",true);
        call.put("source_frame_entry","samples/frames/"+call.attemptId+".jpg");call.put("source_frame_status","CAPTURED");
        call.put("source_frame_width_px",160);call.put("source_frame_height_px",100);
        call.put("source_frame_coordinate_space","mt_source_bitmap_xy");call.put("source_frame_origin","pipeline_bitmap_before_roi");
        call.mtStarted();return call;
    }
    private Detection detection(float confidence,int x) {
        return new Detection(0,confidence,x,30,x+40,50,Arrays.asList(
                new Point2(x,30,1),new Point2(x+40,30,1),new Point2(x+40,50,1),new Point2(x,50,1)));
    }
    private File finish(ResearchSessionStore store,String fixture) throws Exception {
        syntheticTraces(store);
        store.closeAdmission("manual",1001000,clock.get());
        File file=store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true,\"measured_at\":\"2026-11-04T00:00:00Z\"}","","","","",""),null);
        ResearchArchive.verifyEntryHashes(file);
        if(fixture!=null && System.getenv("F5A12A_FIXTURES")!=null) {
            Path dest=Paths.get(System.getenv("F5A12A_FIXTURES"));Files.createDirectories(dest);
            Files.copy(file.toPath(),dest.resolve(fixture+".alprsession"),StandardCopyOption.REPLACE_EXISTING);
        }
        assertEquals(created.get(),released.get());return file;
    }
    /** Serialize the simulated frame scopes as native telemetry too; never replay the deduplicator. */
    private void syntheticTraces(ResearchSessionStore store)throws Exception {
        if(store.f5aEvidence()==null)return;
        drain(store);
        Map<String,JSONObject> roleRows=new LinkedHashMap<>(),sceneRows=new LinkedHashMap<>();
        File journal=new File(store.directory(),F5aEvidence.JOURNAL);
        if(journal.isFile())for(String line:ResearchSessionStore.read(journal).split("\\R"))if(!line.isEmpty()) {
            JSONObject envelope=new JSONObject(line),row=envelope.getJSONObject("record");
            if("ROLE".equals(envelope.getString("kind")))roleRows.put(row.getString("mt_invocation_id"),row);
            if("SCENE".equals(envelope.getString("kind")))sceneRows.put(row.getString("scene_event_id"),row);
        }
        Map<String,List<JSONObject>> grouped=new LinkedHashMap<>();
        for(JSONObject row:roleRows.values())grouped.computeIfAbsent(row.getString("postprocess_scope_id"),k->new ArrayList<>()).add(row);
        Map<String,Integer> mzByInvocation=new HashMap<>();
        for(String line:ResearchSessionStore.readOr(new File(store.directory(),"samples/attempts.jsonl"),"").split("\\R"))if(!line.isEmpty()) {
            JSONObject attempt=new JSONObject(line);if(attempt.optBoolean("mz_executed"))mzByInvocation.merge(attempt.optString("mt_invocation_id"),1,Integer::sum);
        }
        for(List<JSONObject> calls:grouped.values()) {
            JSONObject first=calls.get(0),source=first.getJSONObject("source_stamp"),trace=new JSONObject(source.toString());
            int raw=0,kept=0,suppressed=0,mzRuns=0;boolean completed=true,cancelled=false;
            for(JSONObject call:calls) {
                completed &= !"NOT_REACHED".equals(call.getString("postprocess_status"));
                cancelled |= call.getBoolean("stale_or_cancelled");
                if(!call.isNull("raw_attempt_ids"))raw+=call.getJSONArray("raw_attempt_ids").length()-call.getJSONArray("rejected_before_nms").length();
                kept+=call.getJSONArray("final_attempt_ids").length();suppressed+=call.getJSONArray("suppressed_attempt_ids").length();
                mzRuns+=mzByInvocation.getOrDefault(call.getString("mt_invocation_id"),0);
            }
            JSONObject counters=new JSONObject().put("mt_runs_this_frame",calls.size()).put("mz_runs",mzRuns);
            if(completed)counters.put("plate_detections_raw",raw).put("plate_detections_after_dedup",kept).put("plate_detections_suppressed",suppressed);
            trace.put("frame_id",first.getLong("trace_frame_id")).put("source_timestamp_ns",source.getLong("source_timestamp_nanos"))
                    .put("status",cancelled ? "scene_superseded" : "synthetic_completed").put("counters",counters)
                    .put("attributes",new JSONObject().put("synthetic",true)).put("stage_ms",new JSONObject()).put("confidence",new JSONObject());
            store.appendTelemetry("traces.jsonl",trace.toString());
        }
        for(JSONObject scene:sceneRows.values())for(int i=0;i<scene.getJSONArray("mp_observations").length();i++) {
            JSONObject mp=scene.getJSONArray("mp_observations").getJSONObject(i),source=mp.getJSONObject("source_stamp");
            boolean represented=false;
            for(List<JSONObject> calls:grouped.values())if(calls.get(0).getLong("trace_frame_id")==mp.getLong("trace_frame_id")
                    && calls.get(0).getJSONObject("source_stamp").toString().equals(source.toString()))represented=true;
            if(represented)continue;
            JSONObject counters=new JSONObject().put("mt_runs_this_frame",0);
            if(!mp.isNull("raw_count"))counters.put("vehicle_runs",1).put("vehicle_detections_raw",mp.getInt("raw_count"))
                    .put("vehicle_detections_rejected_class",mp.getInt("rejected_class_count"));
            JSONObject trace=new JSONObject(source.toString()).put("frame_id",mp.getLong("trace_frame_id"))
                    .put("source_timestamp_ns",source.getLong("source_timestamp_nanos")).put("status","scan_queue_updated")
                    .put("counters",counters).put("attributes",new JSONObject().put("synthetic",true).put("scan_acquisition_gate","queue_before_mt"))
                    .put("stage_ms",new JSONObject()).put("confidence",new JSONObject());
            store.appendTelemetry("traces.jsonl",trace.toString());
        }
    }
    private static JSONObject read(File zip,String entry)throws Exception {
        try(ZipFile source=new ZipFile(zip)) {return new JSONObject(new String(source.getInputStream(source.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8));}
    }
    private static List<JSONObject> rows(File zip,String entry)throws Exception {
        try(ZipFile source=new ZipFile(zip)) {
            String text=new String(source.getInputStream(source.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8);
            List<JSONObject> out=new ArrayList<>();for(String line:text.split("\\R"))if(!line.isEmpty())out.add(new JSONObject(line));return out;
        }
    }
    @After public void cleanup()throws Exception {
        for(ResearchSessionStore store:stores) {ThreadPoolExecutor executor=writer(store);executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test public void T01_zeroRoiWritesSceneWithoutAnyMtInvocationOrAttempt()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,10);String id=capture(store,source);
        store.f5aEvidence().observation(id,mp(source,0,new int[0],new int[0][]),"zero_roi");
        File file=finish(store,"zero-roi");JSONObject scene=rows(file,F5aEvidence.SCENES).get(0);
        assertEquals("WRITTEN",scene.getString("write_status"));assertEquals("NOT_RUN",scene.getString("mt_execution_status"));
        assertEquals("zero_roi",scene.getString("mt_not_run_reason"));assertEquals(0,scene.getJSONArray("mt_invocation_ids").length());
        assertEquals(0,rows(file,"samples/attempts.jsonl").size());assertEquals(0,rows(file,F5aEvidence.ROLES).size());
        assertTrue(read(file,"session.json").getBoolean("scene_evidence_complete"));
        assertFalse(scene.has("ground_truth"));
    }
    @Test public void T02_sceneEvidenceDoesNotInferPhysicalPresenceOrGt()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,10);String ticket=store.f5aEvidence().ticket(source);
        store.captureSceneEvidence(ticket,source,160,100,()->image(160,100,0x112233,false));
        store.f5aEvidence().observation(ticket,mp(source,0,new int[0],new int[0][]),"zero_roi");
        File file=finish(store,"zero-roi-with-plate");
        JSONObject scene=rows(file,F5aEvidence.SCENES).get(0);
        assertFalse(scene.has("plate_count"));assertFalse(scene.has("gt_empty"));assertFalse(scene.has("ground_truth"));
        assertEquals("NOT_ASSESSED",scene.getString("settling_status"));
    }
    @Test public void T03_rejectedMpClassesAndReasonRetainOriginalValues()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,10);String id=capture(store,source);
        store.f5aEvidence().observation(id,mp(source,2,new int[]{7,9},new int[0][]),"zero_roi");
        JSONObject row=rows(finish(store,"mp-class-rejected"),F5aEvidence.SCENES).get(0).getJSONArray("mp_observations").getJSONObject(0);
        assertEquals(2,row.getInt("raw_count"));assertEquals("[7,9]",row.getJSONArray("rejected_class_ids").toString());
        assertEquals(0,row.getJSONArray("selected_rois").length());
    }
    @Test public void T04_captureDoesNotNeedHeavyInferenceOrMtDuringStaticIdle()throws Exception {
        ResearchSessionStore store=running();String id=capture(store,stamp(1,10));
        store.f5aEvidence().observation(id,null,"static_idle");File file=finish(store,"mt-not-run");
        JSONObject row=rows(file,F5aEvidence.SCENES).get(0);assertEquals("static_idle",row.getString("mt_not_run_reason"));
        assertEquals("WRITTEN",row.getString("write_status"));assertEquals(0,rows(file,F5aEvidence.ROLES).size());
    }
    @Test public void T05_startWithoutDetectorChangeHasExactlyOneEvent()throws Exception {
        ResearchSessionStore store=running();capture(store,stamp(1,1));
        List<JSONObject> scenes=rows(finish(store,null),F5aEvidence.SCENES);assertEquals(1,scenes.size());
        assertEquals("SESSION_START",scenes.get(0).getString("event_type"));
    }
    @Test public void T06_identicalFramesAndRepeatedRevisionDoNotWriteMoreImages()throws Exception {
        ResearchSessionStore store=running();capture(store,stamp(1,1));
        for(int i=2;i<102;i++)assertEquals("",store.f5aEvidence().ticket(stamp(1,i)));
        store.f5aEvidence().boundary(9,"static_global_changed",stamp(2,102),1001000,1100);
        store.f5aEvidence().boundary(9,"static_global_changed",stamp(2,102),1001000,1100);
        capture(store,stamp(2,103));assertEquals(2,rows(finish(store,null),F5aEvidence.SCENES).size());assertEquals(2,created.get());
    }
    @Test public void T07_supersededTicketCannotCaptureTheNextScene()throws Exception {
        ResearchSessionStore store=running();String old=store.f5aEvidence().ticket(stamp(1,10));
        store.f5aEvidence().boundary(9,"static_global_changed",stamp(2,20),1001000,1100);
        store.captureSceneEvidence(old,stamp(2,21),160,100,()->image(160,100,0,false));capture(store,stamp(2,22));
        List<JSONObject> scenes=rows(finish(store,null),F5aEvidence.SCENES);assertEquals(2,scenes.size());
        assertEquals("superseded_before_capture",scenes.get(0).getString("missing_reason"));
        assertEquals(22,scenes.get(1).getJSONObject("source_stamp").getLong("source_sequence"));
    }
    @Test public void T06_recreatedPipelineCanReuseARevisionWithoutLosingTheNextBoundary()throws Exception {
        ResearchSessionStore store=running();capture(store,stamp(1,1));
        store.f5aEvidence().boundary("first-pipeline",9,"static_global_changed",stamp(2,20),1001000,1100);
        store.f5aEvidence().boundary("first-pipeline",9,"static_global_changed",stamp(2,20),1001000,1100);
        capture(store,stamp(2,21));
        store.f5aEvidence().boundary("recreated-pipeline",9,"static_global_changed",stamp(3,30),1002000,1200);
        capture(store,stamp(3,31));
        List<JSONObject> scenes=rows(finish(store,null),F5aEvidence.SCENES);assertEquals(3,scenes.size());
        assertEquals(9,scenes.get(1).getLong("transition_revision"));assertEquals(9,scenes.get(2).getLong("transition_revision"));
        assertNotEquals(scenes.get(1).getString("scene_event_id"),scenes.get(2).getString("scene_event_id"));
    }
    @Test public void T08_stopAndTimerCloseCaptureWithoutChangingDeadline()throws Exception {
        ResearchSessionStore store=running(256,TimerConfig.of(true,5));String ticket=store.f5aEvidence().ticket(stamp(1,1));
        long deadline=store.deadlineElapsedNanos();clock.set(deadline);
        assertFalse(store.accepting());assertEquals("",store.f5aEvidence().ticket(stamp(1,2)));
        store.closeAdmission("manual",1009000,deadline+999);
        store.captureSceneEvidence(ticket,stamp(1,2),160,100,()->image(160,100,0,false));
        File file=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
        assertEquals(deadline,read(file,"session.json").getLong("finished_elapsed_nanos"));
        assertEquals("timer",read(file,"session.json").getString("completion_reason"));
        assertEquals("no_frame_before_session_end",rows(file,F5aEvidence.SCENES).get(0).getString("missing_reason"));assertEquals(created.get(),released.get());
    }
    @Test public void T09_memoryBudgetAndCopyFailureHaveExplicitReasons()throws Exception {
        ResearchSessionStore store=running();String ticket=store.f5aEvidence().ticket(stamp(1,1));
        store.captureSceneEvidence(ticket,stamp(1,1),4000,4000,()->{throw new AssertionError("Budget before copy");});
        assertEquals("source_frame_memory_budget",rows(finish(store,null),F5aEvidence.SCENES).get(0).getString("missing_reason"));
        store=running();ticket=store.f5aEvidence().ticket(stamp(1,1));
        store.captureSceneEvidence(ticket,stamp(1,1),160,100,()->{throw new OutOfMemoryError("synthetic");});
        assertEquals("source_frame_copy_failed",rows(finish(store,null),F5aEvidence.SCENES).get(0).getString("missing_reason"));
    }
    @Test public void T09_writerQueueFullRetainsMissingEventAndReleasesPixels()throws Exception {
        ResearchSessionStore store=running(1,TimerConfig.disabled());drain(store);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        writer(store).execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(2,TimeUnit.SECONDS));writer(store).execute(()->{});
        capture(store,stamp(1,1));release.countDown();
        JSONObject row=rows(finish(store,null),F5aEvidence.SCENES).get(0);assertEquals("writer_queue_full",row.getString("missing_reason"));
        assertEquals(created.get(),released.get());
    }
    @Test public void T09_jpegAndDiskFailureDoNotPretendTheImageWasWritten()throws Exception {
        ResearchSessionStore store=running();String ticket=store.f5aEvidence().ticket(stamp(1,1));
        store.captureSceneEvidence(ticket,stamp(1,1),160,100,()->image(160,100,0,true));
        File file=finish(store,"missing-frame");JSONObject row=rows(file,F5aEvidence.SCENES).get(0);
        assertEquals("source_frame_write_failed",row.getString("missing_reason"));assertTrue(row.isNull("source_frame_entry"));
        assertTrue(row.isNull("source_frame_sha256"));assertFalse(read(file,"session.json").getBoolean("scene_evidence_complete"));
        assertTrue("Legacy collection_complete retains its old meaning",read(file,"session.json").getBoolean("collection_complete"));
        assertFalse(store.evidenceExtensionsComplete());
    }
    @Test public void T10_realEmptyMtIsAnInvocationButNotADetectionOrHumanNegative()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);
        AcquisitionAttemptRecord call=call(batch,source,0,160);batch.decoded(call);
        batch.postprocessed(Collections.emptyList(),Collections.emptyList(),false);batch.finish("","");
        File file=finish(store,null);JSONObject role=rows(file,F5aEvidence.ROLES).get(0);
        assertEquals(0,role.getJSONArray("raw_attempt_ids").length());assertEquals("COMPLETE",role.getString("role_proof_status"));
        assertEquals("NO_DETECTION",rows(file,"samples/attempts.jsonl").get(0).getString("mt_status"));
    }
    @Test public void T11_statusesAndCompletedRolesSurviveLaterMzFailureOrCancellation()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);
        AcquisitionAttemptRecord call=call(batch,source,0,160);Detection detection=detection(.9f,20);
        batch.detected(call,detection,true,160,100);batch.decoded(call);call.put("mz_status","NO_CHARACTERS");call.put("mz_executed",true);
        batch.postprocessed(Collections.singletonList(detection),Collections.singletonList(detection),false);batch.finish("scene_superseded","synthetic MZ error");
        File file=finish(store,"cancelled");JSONObject role=rows(file,F5aEvidence.ROLES).get(0),attempt=rows(file,"samples/attempts.jsonl").get(0);
        assertTrue(role.getBoolean("stale_or_cancelled"));assertEquals("COMPLETE",role.getString("role_proof_status"));
        assertEquals(1,role.getJSONArray("final_attempt_ids").length());assertEquals("NO_CHARACTERS",attempt.getString("mz_status"));
        assertEquals("",attempt.getString("prediction"));assertEquals("synthetic MZ error",role.getString("processing_error"));
    }
    @Test public void T11_cancellationBeforePostprocessLeavesUnresolvedInsteadOfFinal()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);
        AcquisitionAttemptRecord call=call(batch,source,0,160);batch.detected(call,detection(.9f,20),true,160,100);batch.decoded(call);batch.finish("scene_superseded","");
        JSONObject row=rows(finish(store,null),F5aEvidence.ROLES).get(0);assertEquals("INCOMPLETE",row.getString("role_proof_status"));
        assertEquals(0,row.getJSONArray("final_attempt_ids").length());assertEquals(1,row.getJSONArray("unresolved_attempt_ids").length());
    }
    @Test public void T11_readNoReadAndNotRunRemainDistinctSerializedStates()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);List<Detection> detections=new ArrayList<>();
        String[] statuses={"READ","NO_CHARACTERS","NOT_RUN"};
        for(int i=0;i<3;i++) {
            AcquisitionAttemptRecord call=call(batch,source,0,160);Detection d=detection(.9f,10+i*40);detections.add(d);
            batch.detected(call,d,true,160,100);batch.decoded(call);call.put("mz_status",statuses[i]);call.put("mz_executed",i<2);
            if(i==0)call.put("prediction","SYN123");
        }
        batch.postprocessed(detections,detections,true);batch.finish("","");List<JSONObject> attempts=rows(finish(store,null),"samples/attempts.jsonl");
        for(int i=0;i<3;i++)assertEquals(statuses[i],attempts.get(i).getString("mz_status"));
        assertEquals("SYN123",attempts.get(0).getString("prediction"));assertEquals("",attempts.get(1).getString("prediction"));
        assertFalse(attempts.get(2).getBoolean("mz_executed"));
    }
    @Test public void T11_backendFailureHasUnknownRawInsteadOfAnEmptyDetectionPopulation()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);AcquisitionAttemptRecord call=call(batch,source,0,160);
        call.put("execution_error","synthetic MT backend failure");batch.finish("","synthetic MT backend failure");
        JSONObject role=rows(finish(store,null),F5aEvidence.ROLES).get(0);assertTrue(role.isNull("raw_attempt_ids"));
        assertEquals("INCOMPLETE",role.getString("role_proof_status"));assertEquals("NOT_REACHED",role.getString("postprocess_status"));
    }
    @Test public void T14_invalidQuadThatActuallyReachedDedupRetainsItsTrueRole()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);AcquisitionAttemptRecord call=call(batch,source,0,160);
        Detection invalid=detection(.9f,10);batch.detected(call,invalid,false,160,100);batch.decoded(call);
        batch.postprocessed(Collections.singletonList(invalid),Collections.singletonList(invalid),false);batch.finish("","");
        File file=finish(store,null);assertEquals("INVALID_QUAD",rows(file,"samples/attempts.jsonl").get(0).getString("mt_pose_status"));
        assertEquals(1,rows(file,F5aEvidence.ROLES).get(0).getJSONArray("final_attempt_ids").length());
    }
    @Test public void T12_oneAndTwoRoisKeepOriginalInvocationAndAttemptIds()throws Exception {
        for(int n=1;n<=2;n++) {
            ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
            ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);List<Detection> raw=new ArrayList<>();List<String> ids=new ArrayList<>();
            for(int i=0;i<n;i++) {AcquisitionAttemptRecord call=call(batch,source,i*70,80+i*80);ids.add(call.attemptId);
                Detection detection=detection(.9f,i*70+5);raw.add(detection);batch.detected(call,detection,true,160,100);batch.decoded(call);}
            List<Detection> retained=DetectionDeduplicator.suppress(raw,.45f,.82f,false);batch.postprocessed(raw,retained,false);batch.finish("","");
            File file=finish(store,n==1 ? "one-roi" : "two-roi");List<JSONObject> roles=rows(file,F5aEvidence.ROLES);assertEquals(n,roles.size());
            for(int i=0;i<n;i++){assertEquals(ids.get(i),roles.get(i).getString("mt_invocation_id"));assertEquals(ids.get(i),roles.get(i).getJSONArray("raw_attempt_ids").getString(0));}
            assertTrue(read(file,"session.json").getBoolean("mt_detection_roles_complete"));
        }
    }
    @Test public void T12_fallbackHasTwoEmptyRoiCallsAndOneActualFullFrameCall()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);
        for(int i=0;i<2;i++){AcquisitionAttemptRecord call=call(batch,source,i*80,80+i*80);batch.decoded(call);}
        AcquisitionAttemptRecord fallback=call(batch,source,0,160);Detection detection=detection(.9f,20);
        batch.detected(fallback,detection,true,160,100);batch.decoded(fallback);batch.postprocessed(Collections.singletonList(detection),Collections.singletonList(detection),false);batch.finish("","");
        File file=finish(store,"fallback");List<JSONObject> roles=rows(file,F5aEvidence.ROLES);assertEquals(3,roles.size());
        assertEquals(0,roles.get(0).getJSONArray("raw_attempt_ids").length());assertEquals("[0,0,160,100]",roles.get(2).getJSONArray("roi").toString());
        assertEquals(3,rows(file,F5aEvidence.SCENES).get(0).getJSONArray("mt_invocation_ids").length());
    }
    @Test public void T13_realCrossRoiDedupMembershipIsPersistedWithoutReplay()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);List<Detection> raw=new ArrayList<>();
        for(int i=0;i<2;i++){AcquisitionAttemptRecord call=call(batch,source,i*20,120+i*20);Detection detection=detection(.9f-i*.1f,30+i);raw.add(detection);batch.detected(call,detection,true,160,100);batch.decoded(call);}
        List<Detection> kept=DetectionDeduplicator.suppress(raw,.45f,.82f,false);assertEquals(1,kept.size());
        batch.postprocessed(raw,kept,false);batch.finish("","");List<JSONObject> roles=rows(finish(store,"dedup"),F5aEvidence.ROLES);
        assertEquals(1,roles.get(0).getJSONArray("final_attempt_ids").length());assertEquals(1,roles.get(1).getJSONArray("suppressed_attempt_ids").length());
    }
    @Test public void T14_insufficientKeypointsAreRejectedBeforeNmsWithoutInventingQuad()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);AcquisitionAttemptRecord call=call(batch,source,0,160);
        Detection invalid=new Detection(0,.9f,20,30,60,50,Collections.emptyList());batch.detected(call,invalid,false,160,100);batch.rejectedBeforeNms(invalid);batch.decoded(call);
        batch.postprocessed(Collections.emptyList(),Collections.emptyList(),false);batch.finish("","");File file=finish(store,null);
        JSONObject role=rows(file,F5aEvidence.ROLES).get(0);assertEquals(0,role.getJSONArray("final_attempt_ids").length());assertEquals(0,role.getJSONArray("suppressed_attempt_ids").length());
        assertEquals("INSUFFICIENT_KEYPOINTS",role.getJSONArray("rejected_before_nms").getJSONObject(0).getString("reason"));
        assertEquals("",rows(file,"samples/attempts.jsonl").get(0).getString("mt_pose_corners_norm"));
        assertEquals("MISSING_KEYPOINTS",rows(file,"samples/attempts.jsonl").get(0).getString("mt_pose_status"));
    }
    @Test public void T15_47Attempts40Calls40SourcePathsPreserveSharing()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);List<Detection> all=new ArrayList<>();
        for(int i=0;i<40;i++) {AcquisitionAttemptRecord call=call(batch,source,0,160);
            for(int j=0;j<(i<7 ? 2 : 1);j++){Detection d=detection(.9f,10+j*60);all.add(d);batch.detected(call,d,true,160,100);}batch.decoded(call);}
        batch.postprocessed(all,all,true);batch.finish("","");File file=finish(store,"shared-image");List<JSONObject> attempts=rows(file,"samples/attempts.jsonl");
        assertEquals(47,attempts.size());assertEquals(40,rows(file,F5aEvidence.ROLES).size());Set<String> paths=new HashSet<>();
        for(JSONObject attempt:attempts){assertEquals("WRITTEN",attempt.getString("source_frame_status"));paths.add(attempt.getString("source_frame_entry"));}
        assertEquals(40,paths.size());assertEquals(0,read(file,"session.json").getInt("source_frame_integrity_loss_count"));
        assertEquals(attempts.get(0).getString("source_frame_entry"),attempts.get(1).getString("source_frame_entry"));
    }
    @Test public void T16_deletedOrChangedSceneImageIsNotSilentlyReusedOnRebuild()throws Exception {
        ResearchSessionStore store=running();capture(store,stamp(1,1));File original=finish(store,null);
        String entry=rows(original,F5aEvidence.SCENES).get(0).getString("source_frame_entry");
        Files.write(new File(store.directory(),entry).toPath(),new byte[]{1,2,3});File rebuilt=ResearchSessionStore.buildArchive(store.directory(),null);
        JSONObject scene=rows(rebuilt,F5aEvidence.SCENES).get(0);assertEquals("source_frame_hash_mismatch",scene.getString("missing_reason"));
        assertFalse(read(rebuilt,"session.json").getBoolean("scene_evidence_complete"));ResearchArchive.verifyEntryHashes(rebuilt);
    }
    @Test public void T16_sceneShaDimensionsAndManifestRoundTripAgree()throws Exception {
        ResearchSessionStore store=running();capture(store,stamp(1,1));File archive=finish(store,null);
        JSONObject scene=rows(archive,F5aEvidence.SCENES).get(0),manifest=read(archive,"manifest.json");String entry=scene.getString("source_frame_entry");
        assertEquals(scene.getString("source_frame_sha256"),manifest.getJSONObject("entry_sha256").getString(entry));
        try(ZipFile zip=new ZipFile(archive)){
            Object decoded=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,zip.getInputStream(zip.getEntry(entry)));
            assertEquals(scene.getInt("source_frame_width_px"),decoded.getClass().getMethod("getWidth").invoke(decoded));
            assertEquals(scene.getInt("source_frame_height_px"),decoded.getClass().getMethod("getHeight").invoke(decoded));
        }
        assertTrue(read(archive,"samples/schema.json").getJSONObject("capabilities").getBoolean("scene_evidence_v1"));
    }
    @Test public void T16_wrongEncoderDimensionsAreReportedAsMissingWithoutLosingTheEvent()throws Exception {
        ResearchSessionStore store=running();String ticket=store.f5aEvidence().ticket(stamp(1,1));
        created.incrementAndGet();
        store.captureSceneEvidence(ticket,stamp(1,1),160,100,()->new F5aEvidence.Frame() {
            public int width(){return 160;}public int height(){return 100;}
            public void write(File file)throws IOException {encodeHostJpeg(file,80,50,0);}
            public void release(){released.incrementAndGet();}
        });
        JSONObject row=rows(finish(store,null),F5aEvidence.SCENES).get(0);
        assertEquals("MISSING",row.getString("write_status"));assertEquals("source_frame_write_failed",row.getString("missing_reason"));
    }
    @Test public void T12_delayedOldInvocationIsBoundToItsOriginalEventAfterSceneChange()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);String first=capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);AcquisitionAttemptRecord call=call(batch,source,0,160);
        Detection detection=detection(.9f,10);batch.detected(call,detection,true,160,100);batch.decoded(call);
        store.f5aEvidence().boundary(9,"static_global_changed",stamp(2,20),1001000,1100);String second=capture(store,stamp(2,21));
        batch.postprocessed(Collections.singletonList(detection),Collections.singletonList(detection),false);batch.finish("scene_superseded","");
        File file=finish(store,null);JSONObject role=rows(file,F5aEvidence.ROLES).get(0);
        assertEquals(first,role.getString("scene_event_id"));assertNotEquals(second,role.getString("scene_event_id"));
        List<JSONObject> scenes=rows(file,F5aEvidence.SCENES);assertEquals(1,scenes.get(0).getJSONArray("mt_invocation_ids").length());
        assertEquals(0,scenes.get(1).getJSONArray("mt_invocation_ids").length());
    }
    @Test public void T19_producerRebuildPreservesSceneIdsSourceShaAndInvocationMembership()throws Exception {
        ResearchSessionStore store=running();ContinuityStamp source=stamp(1,1);capture(store,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,source,"r2_two_roi",1,7);List<Detection> raw=new ArrayList<>();
        for(int i=0;i<2;i++) {
            AcquisitionAttemptRecord call=call(batch,source,i*80,(i+1)*80);Detection detection=detection(.9f,10+i*80);
            raw.add(detection);batch.detected(call,detection,true,160,100);batch.decoded(call);
        }
        batch.postprocessed(raw,DetectionDeduplicator.suppress(raw,.45f,.82f,false),false);batch.finish("","");
        File original=finish(store,null);String before=rows(original,F5aEvidence.SCENES).get(0).toString();
        List<JSONObject> roleBefore=rows(original,F5aEvidence.ROLES);assertEquals(2,roleBefore.size());
        File rebuilt=ResearchSessionStore.buildArchive(store.directory(),null);
        assertEquals(new JSONObject(before).toString(),rows(rebuilt,F5aEvidence.SCENES).get(0).toString());
        List<JSONObject> roleAfter=rows(rebuilt,F5aEvidence.ROLES);assertEquals(2,roleAfter.size());
        for(int i=0;i<2;i++)assertEquals(roleBefore.get(i).toString(),roleAfter.get(i).toString());
        ResearchArchive.verifyEntryHashes(rebuilt);
    }
    @Test public void interruptedSessionRetainsPendingStartAsMissingAndUnknownWork()throws Exception {
        ResearchSessionStore store=running();store.f5aEvidence().ticket(stamp(1,1));drain(store);writer(store).shutdown();assertTrue(writer(store).awaitTermination(5,TimeUnit.SECONDS));
        JSONObject session=new JSONObject(ResearchSessionStore.read(new File(store.directory(),"session.json")));
        session.put("state","PARTIAL").put("completion_reason","process_interrupted").put("collection_complete",false);
        ResearchSessionStore.atomicText(new File(store.directory(),"session.json"),session.toString());
        JSONObject scene=rows(ResearchSessionStore.buildArchive(store.directory(),null),F5aEvidence.SCENES).get(0);
        assertEquals("process_interrupted",scene.getString("missing_reason"));assertEquals("UNKNOWN",scene.getString("mt_execution_status"));assertEquals("INCOMPLETE",scene.getString("links_status"));
    }
    @Test public void nonF5aSessionsNeverDeclareOrBackfillExtension()throws Exception {
        ExperimentSession.Prepared prepared=new ExperimentSession().prepare("f4","fp32",TimerConfig.disabled(),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        ResearchSessionStore store=ResearchSessionStore.prepare(temporary.newFolder(),prepared,16,2,0,(b,f)->{},clock::get);stores.add(store);store.startAt(1000000,1000);
        assertNull(store.f5aEvidence());File file=finish(store,"legacy-f4");assertFalse(read(file,"samples/schema.json").getJSONObject("capabilities").has("scene_evidence_v1"));
        JSONObject old=read(file,"session.json");
        for(String variant:new String[]{"R0","R1","R2"}) {
            File directory=temporary.newFolder();new File(directory,"samples").mkdir();new File(directory,"telemetry").mkdir();
            JSONObject session=new JSONObject(old.toString()).put("experiment_type","roi_budget").put("variant",variant);
            // A synthetic pre-extension session deliberately has no producer capability flag/journal.
            ResearchSessionStore.atomicText(new File(directory,"session.json"),session.toString());
            ResearchSessionStore.atomicText(new File(directory,"telemetry/report.json"),"{\"synthetic\":true,\"measured_at\":\"2026-11-04T00:00:00Z\"}");
            File rebuilt=ResearchSessionStore.buildArchive(directory,null);ResearchArchive.verifyEntryHashes(rebuilt);
            assertFalse(read(rebuilt,"samples/schema.json").getJSONObject("capabilities").has("scene_evidence_v1"));
            if(System.getenv("F5A12A_FIXTURES")!=null)Files.copy(rebuilt.toPath(),Paths.get(System.getenv("F5A12A_FIXTURES"),"legacy-"+variant.toLowerCase(Locale.ROOT)+".alprsession"),StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
