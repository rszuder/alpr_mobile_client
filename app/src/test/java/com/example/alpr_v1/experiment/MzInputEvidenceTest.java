package com.example.alpr_v1.experiment;

import android.graphics.*;
import com.example.alpr_v1.capture.GallerySessionScope;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.inference.*;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.vision.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,manifest=Config.NONE,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MzInputEvidenceTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    final List<ResearchSessionStore> stores=new ArrayList<>();
    final List<Bitmap> bitmaps=new ArrayList<>();
    Bitmap source;
    @Before public void scene(){
        source=Bitmap.createBitmap(320,200,Bitmap.Config.ARGB_8888);bitmaps.add(source);
        Canvas c=new Canvas(source);c.drawColor(Color.DKGRAY);Paint p=new Paint();p.setColor(Color.WHITE);
        c.drawRect(30,50,270,160,p);p.setColor(Color.BLACK);p.setTextSize(32);c.drawText("ABC123",45,120,p);
    }
    @After public void close()throws Exception{
        for(ResearchSessionStore s:stores){java.lang.reflect.Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);
            ThreadPoolExecutor e=(ThreadPoolExecutor)f.get(s);e.shutdownNow();assertTrue(e.awaitTermination(5,TimeUnit.SECONDS));}
        for(Bitmap b:bitmaps)if(!b.isRecycled())b.recycle();
    }
    ResearchSessionStore store(boolean on)throws Exception{
        ExperimentSession.Prepared p=new ExperimentSession().prepare("roi_budget","SYNTHETIC",TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity("SYNTHETIC-F5A16","SYNTHETIC",1,"synthetic=true"),null);
        ResearchSessionStore s=ResearchSessionStore.prepare(temp.newFolder(),p,256,64,0,(b,f)->{
            assertEquals("research-session-writer",Thread.currentThread().getName());
            try(FileOutputStream out=new FileOutputStream(f)){if(!b.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("JPEG");}
        },System::nanoTime);
        stores.add(s);if(on)s.enableMzInputEvidenceForSyntheticProducer();s.startAt(System.currentTimeMillis(),System.nanoTime());return s;
    }
    ContinuityStamp stamp(int seq){return new ContinuityStamp(2,3,4,seq,9007199254740993L+seq,SourceTimestampDomain.CAMERAX_SENSOR);}
    final Map<ResearchSessionStore,Map<Integer,int[]>> frames=new IdentityHashMap<>();
    List<Point2> quad(int x){return Arrays.asList(new Point2(x+12,60,1),new Point2(x+95,67,1),new Point2(x+87,112,1),new Point2(x+20,105,1));}
    AcquisitionAttemptRecord attempt(ResearchSessionStore s,int seq,int left,int right,boolean detected)throws Exception{
        return attempt(s,seq,left,right,detected,left==0&&right==320?"r0_full_frame":"r2_two_roi");
    }
    AcquisitionAttemptRecord attempt(ResearchSessionStore s,int seq,int left,int right,boolean detected,String policy)throws Exception{
        Map<Integer,int[]> totals=frames.computeIfAbsent(s,k->new LinkedHashMap<>());
        if(!totals.containsKey(seq)){
            totals.put(seq,new int[3]);String ticket=s.f5aEvidence().ticket(stamp(seq));
            if(!ticket.isEmpty())s.captureSceneEvidence(ticket,stamp(seq),source);
            boolean full="r0_full_frame".equals(policy);
            JSONArray selected=new JSONArray();
            int[][] rois=full?new int[][]{{0,320}}:"r1_one_roi".equals(policy)?new int[][]{{left,right}}:new int[][]{{0,160},{160,320}};
            for(int[] roi:rois)selected.put(new JSONObject().put("left",roi[0]).put("top",0).put("right",roi[1]).put("bottom",200).put("entity_id",1).put("vehicle_track_id",1));
            JSONObject mp=new JSONObject().put("trace_frame_id",seq).put("source_stamp",F5aEvidence.stampJson(stamp(seq)))
                    .put("execution_status",full?"NOT_RUN":"EXECUTED").put("not_run_reason",full?"r0_full_frame":JSONObject.NULL)
                    .put("raw_count",full?JSONObject.NULL:rois.length).put("rejected_class_count",full?JSONObject.NULL:0)
                    .put("rejected_class_ids",full?JSONObject.NULL:new JSONArray()).put("selected_rois",selected);
            s.f5aEvidence().observation(s.f5aEvidence().sceneFor(stamp(seq)),mp,"");
        }
        totals.get(seq)[0]++;if(detected)totals.get(seq)[1]++;
        ResearchAttemptBatch batch=new ResearchAttemptBatch(s,stamp(seq),policy,1,seq);
        AcquisitionAttemptRecord r=batch.beginMt(1,1,left,0,right,200,512,512);
        Bitmap roi=Bitmap.createBitmap(source,left,0,right-left,200);bitmaps.add(roi);
        com.example.alpr_v1.model.ModelInputSpec mt=new com.example.alpr_v1.model.ModelInputSpec(512,512,3,"NHWC","RGB","FLOAT32",1f/255,0);
        PreparedInput in=BitmapTensorPreprocessor.prepare(roi,mt,new TensorInfo(0,new int[]{1,512,512,3},"FLOAT32",512*512*12,0,0),r::copyEvidence);
        r.put("input_scale",in.scale);r.put("input_pad_x",in.padX);r.put("input_pad_y",in.padY);r.copySourceFrame(source);
        r.mtStarted();
        if(detected){
            List<Point2> q=quad(left+8);List<Point2> model=new ArrayList<>();
            for(Point2 p:q)model.add(new Point2((p.x-left)*in.scale+in.padX,p.y*in.scale+in.padY,1));
            Detection d=DetectionCoordinateMapper.toSource(new Detection(0,.9f,0,0,512,512,model),in,left,0);
            batch.detected(r,d,true,320,200);batch.postprocessed(Collections.singletonList(d),Collections.singletonList(d),false);
        }else batch.postprocessed(Collections.emptyList(),Collections.emptyList(),false);
        batch.decoded(r);
        // Batch submission is delayed until after the real MZ boundary below.
        batches.put(r,batch);return r;
    }
    final Map<AcquisitionAttemptRecord,ResearchAttemptBatch> batches=new IdentityHashMap<>();
    PreparedInput input(AcquisitionAttemptRecord r,MzBackendProbe probe)throws Exception{
        List<Point2> q=quad(r.data.getInt("roi_left")+8);
        Bitmap crop=PlateRectifier.rectify(source,q);bitmaps.add(crop);r.copyPlateCrop(crop);
        return BitmapTensorPreprocessor.prepare(crop,probe.spec,probe.backend.inputInfo());
    }
    MzInputEvidence.Capture capture(AcquisitionAttemptRecord r,PreparedInput input,MzBackendProbe probe){
        return r.prepareMzInput(input,probe.spec,probe.backend.inputInfo(),quad(r.data.optInt("roi_left")+8),
                probe.model.manifest().modelId(),probe.variant.id(),probe.model.fingerprint(),probe.backend);
    }
    void invoke(AcquisitionAttemptRecord r,PreparedInput input,MzBackendProbe probe,MzInputEvidence.Capture c){
        frames.get(r.store).get((int)r.data.optLong("source_sequence"))[2]++;
        r.put("mz_executed",true);if(c!=null)c.started();
        try{probe.run(input.buffer);r.put("mz_status","NO_CHARACTERS");if(c!=null)c.returned();}catch(RuntimeException error){if(c!=null)c.threw(error);throw error;}
    }
    void submit(AcquisitionAttemptRecord r){batches.remove(r).finish("","");}
    void drain(ResearchSessionStore s)throws Exception{
        java.lang.reflect.Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);
        ((ThreadPoolExecutor)f.get(s)).submit(()->{}).get(10,TimeUnit.SECONDS);
    }
    File finish(ResearchSessionStore s,String fixture)throws Exception{
        for(Map.Entry<Integer,int[]> frame:frames.getOrDefault(s,Collections.emptyMap()).entrySet()){
            int[] count=frame.getValue();JSONObject trace=F5aEvidence.stampJson(stamp(frame.getKey()));
            trace.put("frame_id",frame.getKey()).put("source_timestamp_ns",stamp(frame.getKey()).sourceTimestampNanos)
                    .put("status","synthetic_completed").put("counters",new JSONObject().put("mt_runs_this_frame",count[0])
                            .put("mz_runs",count[2]).put("plate_detections_raw",count[1]).put("plate_detections_after_dedup",count[1]).put("plate_detections_suppressed",0))
                    .put("attributes",new JSONObject().put("synthetic",true)).put("stage_ms",new JSONObject()).put("confidence",new JSONObject());
            s.appendTelemetry("traces.jsonl",trace.toString());
        }
        s.closeAdmission("manual",System.currentTimeMillis(),System.nanoTime());
        File archive=s.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true}","","","","",""),null);
        ResearchArchive.verifyEntryHashes(archive);
        if(fixture!=null){String path=System.getenv("F5A16_FIXTURES");if(path!=null){java.nio.file.Path dir=Paths.get(path);Files.createDirectories(dir);Files.copy(archive.toPath(),dir.resolve(fixture+".alprsession"),StandardCopyOption.REPLACE_EXISTING);}}
        return archive;
    }
    static List<JSONObject> rows(File archive)throws Exception{
        List<JSONObject> out=new ArrayList<>();for(String line:text(archive,MzInputEvidence.INDEX).split("\n"))if(!line.isEmpty())out.add(new JSONObject(line));return out;
    }
    static String text(File archive,String entry)throws Exception{
        try(ZipFile z=new ZipFile(archive)){return new String(z.getInputStream(z.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8);}
    }
    @Test public void M01_realPreparedTensorAndActualBackendDriverAreBitIdenticalOffOn()throws Exception{
        byte[] off,on;String modelSha;ResearchSessionStore s0=store(false),s1=store(true);
        try(MzBackendProbe probe=new MzBackendProbe(temp.newFolder(),320,320,"FLOAT32","NHWC")){
            modelSha=RuntimeExecutionEvidence.sha256(probe.model.resolve(probe.variant.primaryFile()));
            AcquisitionAttemptRecord a=attempt(s0,1,0,320,true);PreparedInput i=input(a,probe);
            assertNull(capture(a,i,probe));invoke(a,i,probe,null);off=probe.received.clone();submit(a);
            AcquisitionAttemptRecord b=attempt(s1,1,0,320,true);PreparedInput j=input(b,probe);
            j.buffer.position(13);j.buffer.mark();int limit=j.buffer.limit();ByteOrder order=j.buffer.order();
            MzInputEvidence.Capture c=capture(b,j,probe);
            assertEquals(13,j.buffer.position());assertEquals(limit,j.buffer.limit());assertEquals(order,j.buffer.order());
            j.buffer.reset();assertEquals(13,j.buffer.position());invoke(b,j,probe,c);on=probe.received.clone();
            assertArrayEquals(off,on);assertArrayEquals(on,c.bytes);assertEquals(1228800,on.length);submit(b);
        }
        File legacy=finish(s0,"mz-off"),archive=finish(s1,"mz-written-r0");
        assertEquals("ABSENT",MzInputEvidenceValidator.validate(legacy));assertEquals("PASS",MzInputEvidenceValidator.validate(archive));
        JSONObject tensor=rows(archive).get(0).getJSONObject("tensor");
        assertEquals(modelSha,rows(archive).get(0).getJSONObject("model").getString("artifact_sha256"));
        try(ZipFile zip=new ZipFile(archive)){assertArrayEquals(on,zip.getInputStream(zip.getEntry(tensor.getString("entry"))).readAllBytes());}
    }
    @Test public void M02_actualShapesDtypesLayoutsAndNormalizationAreNotHardcoded()throws Exception{
        for(String dtype:new String[]{"FLOAT32","INT8","UINT8"})for(String layout:new String[]{"NHWC","NCHW"}){
            ResearchSessionStore s=store(true);
            try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),32,16,dtype,layout)){
                AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);MzInputEvidence.Capture c=capture(r,i,p);
                invoke(r,i,p,c);assertArrayEquals(p.received,c.bytes);submit(r);
                File archive=finish(s,"mz-"+dtype.toLowerCase(Locale.ROOT)+"-"+layout.toLowerCase(Locale.ROOT));
                assertEquals("PASS",MzInputEvidenceValidator.validate(archive));JSONObject t=rows(archive).get(0).getJSONObject("tensor");
                assertEquals(dtype,t.getString("dtype"));assertEquals(layout,t.getString("layout"));assertEquals(p.backend.inputInfo().byteSize,t.getInt("byte_size"));
            }
        }
    }
    @Test public void M03_twoRoiKeepSeparateAttemptsAndExactSourceStamp()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),32,32,"FLOAT32","NHWC")){
            for(int left:new int[]{0,160}){AcquisitionAttemptRecord r=attempt(s,9,left,left+160,true);PreparedInput i=input(r,p);
                invoke(r,i,p,capture(r,i,p));submit(r);}
        }
        File archive=finish(s,"mz-written-r2-two-roi");assertEquals("PASS",MzInputEvidenceValidator.validate(archive));
        List<JSONObject> rows=rows(archive);assertEquals(2,rows.size());assertNotEquals(rows.get(0).getString("attempt_id"),rows.get(1).getString("attempt_id"));
        assertEquals(160,rows.get(1).getJSONObject("roi").getInt("left"));
        assertEquals(9007199254741002L,rows.get(1).getJSONObject("source_stamp").getLong("source_timestamp_nanos"));
    }
    @Test public void M04_noDetectionAndCancelledPrecallHaveNoFabricatedInvocation()throws Exception{
        ResearchSessionStore s=store(true);AcquisitionAttemptRecord no=attempt(s,1,0,320,false);submit(no);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),32,32,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,2,0,320,true);PreparedInput i=input(r,p);MzInputEvidence.Capture c=capture(r,i,p);
            c.discardIfNotStarted();r.cancel("synthetic_before_mz");submit(r);assertEquals(0,p.runs);
        }
        File archive=finish(s,"mz-not-run");assertEquals("PARTIAL",MzInputEvidenceValidator.validate(archive));
        for(JSONObject row:rows(archive)){assertTrue(row.isNull("call_index"));assertEquals("NOT_RUN",row.getString("invocation_status"));}
        assertEquals(0,s.mzInputEvidence().outstandingBytes());
    }
    @Test public void M03_R1_nonzeroOffsetHasOwnPoseAndInputReference()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),48,24,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,80,280,true,"r1_one_roi");PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));submit(r);
        }
        File archive=finish(s,"mz-written-r1");assertEquals("PASS",MzInputEvidenceValidator.validate(archive));
        JSONObject row=rows(archive).get(0);assertEquals("r1_one_roi",row.getString("roi_policy"));assertEquals(80,row.getJSONObject("roi").getInt("left"));
        assertEquals(100,row.getJSONArray("recognition_corners_source_px").getJSONArray(0).getDouble(0),.0001);
    }
    @Test public void M05_oversizeTensorIsMissingBeforeAllocatingDiagnosticCopy()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),1024,1024,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);MzInputEvidence.Capture c=capture(r,i,p);
            assertNull(c.bytes);assertEquals(0,s.mzInputEvidence().outstandingBytes());invoke(r,i,p,c);submit(r);assertEquals(1,p.runs);
        }
        File archive=finish(s,"mz-oversize-missing");assertEquals("PARTIAL",MzInputEvidenceValidator.validate(archive));
        assertEquals("TENSOR_SIZE_LIMIT",rows(archive).get(0).getJSONObject("tensor").getString("missing_reason"));
    }
    @Test public void M05_sessionByteBudgetIsIndependentOfPayloadCount()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),768,768,"FLOAT32","NHWC")){
            for(int n=0;n<10;n++){AcquisitionAttemptRecord r=attempt(s,n+1,0,320,true);PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));submit(r);drain(s);}
            assertEquals(10,p.runs);
        }
        File archive=finish(s,"mz-byte-budget");assertEquals("PARTIAL",MzInputEvidenceValidator.validate(archive));
        assertEquals("SESSION_PAYLOAD_LIMIT",rows(archive).get(9).getJSONObject("tensor").getString("missing_reason"));
    }
    @Test public void M05_blockedWriterCannotBlockBackendEntryOrMutateBuffer()throws Exception{
        ResearchSessionStore s=store(true);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        java.lang.reflect.Field f=ResearchSessionStore.class.getDeclaredField("writer");f.setAccessible(true);
        ((ThreadPoolExecutor)f.get(s)).execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),8,8,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);MzInputEvidence.Capture c=capture(r,i,p);
            invoke(r,i,p,c);submit(r);assertEquals(1,p.runs);assertEquals(1,release.getCount());assertEquals(1,s.mzInputEvidence().outstandingCaptures());
        }finally{release.countDown();}
        File archive=finish(s,"mz-slow-writer");assertEquals("PASS",MzInputEvidenceValidator.validate(archive));
        JSONObject row=rows(archive).get(0);assertTrue(row.getLong("capture_elapsed_nanos")>=0);assertTrue(row.getLong("write_elapsed_nanos")>=0);
    }
    @Test public void M04_backendFailureIsAnEnteredCallNotSuccessfulInference()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),16,16,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);p.failure=new IllegalStateException("synthetic");
            assertThrows(RuntimeException.class,()->invoke(r,i,p,capture(r,i,p)));submit(r);
        }
        File archive=finish(s,"mz-backend-threw");assertEquals("PASS",MzInputEvidenceValidator.validate(archive));
        assertEquals("THREW",rows(archive).get(0).getString("invocation_status"));
    }
    @Test public void M05_onlyTwoOwnedCopiesAndThirdInvocationStillRuns()throws Exception{
        ResearchSessionStore s=store(true);List<AcquisitionAttemptRecord> pending=new ArrayList<>();
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),32,32,"FLOAT32","NHWC")){
            for(int n=0;n<3;n++){AcquisitionAttemptRecord r=attempt(s,n+1,0,320,true);PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));pending.add(r);}
            assertEquals(3,p.runs);assertEquals(2,s.mzInputEvidence().outstandingCaptures());
            assertTrue(s.mzInputEvidence().outstandingBytes()<=MzInputEvidence.MAX_MEMORY_BYTES);
            for(AcquisitionAttemptRecord r:pending)submit(r);
        }
        File archive=finish(s,"mz-memory-missing");assertEquals("PARTIAL",MzInputEvidenceValidator.validate(archive));
        assertEquals("MEMORY_OR_QUEUE_LIMIT",rows(archive).get(2).getJSONObject("tensor").getString("missing_reason"));
        assertEquals(0,s.mzInputEvidence().outstandingBytes());
    }
    @Test public void M05_payloadCountLimitDoesNotStopLaterBackendCalls()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),8,8,"FLOAT32","NHWC")){
            for(int n=0;n<18;n++){AcquisitionAttemptRecord r=attempt(s,n+1,0,320,true);PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));submit(r);drain(s);}
            assertEquals(18,p.runs);
        }
        File archive=finish(s,"mz-payload-limit");assertEquals("PARTIAL",MzInputEvidenceValidator.validate(archive));
        List<JSONObject> list=rows(archive);assertEquals(18,list.size());
        assertEquals("SESSION_PAYLOAD_LIMIT",list.get(16).getJSONObject("tensor").getString("missing_reason"));
    }
    @Test public void M05_payloadWriteFailureIsMissingWithoutChangingPrimaryEvidence()throws Exception{
        ResearchSessionStore s=store(true);
        Files.write(new File(s.directory(),"samples/mz_inputs").toPath(),new byte[]{1});
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),8,8,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));submit(r);
        }
        File archive=finish(s,"mz-write-missing");assertEquals("PARTIAL",MzInputEvidenceValidator.validate(archive));
        assertEquals("PAYLOAD_WRITE_FAILED",rows(archive).get(0).getJSONObject("tensor").getString("missing_reason"));
        assertEquals(1,s.writtenCropCount());
    }
    @Test public void M07_rebuildMissingPayloadAndInterruptedIndexAreExplicitPartial()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),8,8,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));submit(r);
        }
        File first=finish(s,null);JSONObject row=rows(first).get(0);
        Files.delete(new File(s.directory(),row.getJSONObject("tensor").getString("entry")).toPath());
        File rebuilt=ResearchSessionStore.buildArchive(s.directory(),null);assertEquals("PARTIAL",MzInputEvidenceValidator.validate(rebuilt));
        assertEquals("PAYLOAD_INTEGRITY_FAILURE",rows(rebuilt).get(0).getJSONObject("tensor").getString("missing_reason"));
        Files.write(new File(s.directory(),MzInputEvidence.INDEX).toPath(),"{interrupted".getBytes(StandardCharsets.UTF_8));
        File interrupted=ResearchSessionStore.buildArchive(s.directory(),null);assertEquals("PARTIAL",MzInputEvidenceValidator.validate(interrupted));
        JSONObject session=new JSONObject(text(interrupted,"session.json"));assertTrue(session.getJSONObject("mz_input_evidence").getBoolean("pending_loss_unknown"));
        export(interrupted,"mz-interrupted");
    }
    static void export(File archive,String name)throws Exception{
        String path=System.getenv("F5A16_FIXTURES");if(path!=null)Files.copy(archive.toPath(),Paths.get(path,name+".alprsession"),StandardCopyOption.REPLACE_EXISTING);
    }
    @Test public void M08_diagnosticStartBlockedAndOffNeverAddsExtension()throws Exception{
        assertFalse(MzInputEvidence.productionEnabled());
        assertEquals(MzInputEvidence.DIAGNOSTIC_BLOCK_REASON,assertThrows(IllegalStateException.class,MzInputEvidence::requireDiagnosticStartAllowed).getMessage());
        ResearchSessionStore s=store(false);File archive=finish(s,"mz-legacy-no-calls");
        assertEquals("ABSENT",MzInputEvidenceValidator.validate(archive));
        Map<String,byte[]> legacy=new LinkedHashMap<>();
        try(ZipFile zip=new ZipFile(archive)){Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){
            ZipEntry entry=entries.nextElement();if(!"samples/schema.json".equals(entry.getName()))legacy.put(entry.getName(),zip.getInputStream(entry).readAllBytes());}}
        JSONObject manifest=new JSONObject(new String(legacy.remove("manifest.json"),StandardCharsets.UTF_8)),hashes=new JSONObject();
        for(Map.Entry<String,byte[]> entry:legacy.entrySet())hashes.put(entry.getKey(),sha(entry.getValue()));
        manifest.put("entry_sha256",hashes).put("entries_before_manifest",legacy.size());legacy.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        File old=temp.newFile("legacy-without-sample-descriptor.alprsession");
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(old))){for(Map.Entry<String,byte[]> entry:legacy.entrySet()){
            zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}
        assertEquals("ABSENT",MzInputEvidenceValidator.validate(old));
    }
    @Test public void M07_semanticCorruptionRejectedWithRehashedNativeProducerFixtures()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),8,8,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);invoke(r,i,p,capture(r,i,p));submit(r);
        }
        File good=finish(s,"mz-negative-source");assertEquals("PASS",MzInputEvidenceValidator.validate(good));
        for(String fault:new String[]{"tensor-sha","payload-missing","source-stamp","unknown-invocation","duplicate-index","shape","duplicate-zip"}){
            File bad=mutate(good,fault);assertThrows("Must reject "+fault,Exception.class,()->MzInputEvidenceValidator.validate(bad));export(bad,"negative-"+fault);
        }
    }

    File nativePurposeFixture()throws Exception{
        ResearchSessionStore s=store(true);
        try(MzBackendProbe p=new MzBackendProbe(temp.newFolder(),8,8,"FLOAT32","NHWC")){
            AcquisitionAttemptRecord r=attempt(s,1,0,320,true);PreparedInput i=input(r,p);
            invoke(r,i,p,capture(r,i,p));submit(r);
        }
        File archive=finish(s,"mz-purpose-synthetic");
        assertEquals("PASS",MzInputEvidenceValidator.validate(archive));return archive;
    }
    JSONObject metadata(File archive,String entry)throws Exception{
        try(ZipFile zip=new ZipFile(archive)){
            return new JSONObject(new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    interface PurposeEdit{void apply(JSONObject session,JSONObject research)throws Exception;}
    File purposeCopy(File original,String label,PurposeEdit edit)throws Exception{
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipFile zip=new ZipFile(original)){
            Enumeration<? extends ZipEntry> all=zip.entries();while(all.hasMoreElements()){
                ZipEntry entry=all.nextElement();entries.put(entry.getName(),zip.getInputStream(entry).readAllBytes());
            }
        }
        JSONObject session=new JSONObject(new String(entries.get("session.json"),StandardCharsets.UTF_8));
        JSONObject report=new JSONObject(new String(entries.get("report.json"),StandardCharsets.UTF_8));
        edit.apply(session,report.getJSONObject("research_collection"));
        entries.put("session.json",session.toString().getBytes(StandardCharsets.UTF_8));
        entries.put("report.json",report.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject manifest=new JSONObject(new String(entries.remove("manifest.json"),StandardCharsets.UTF_8));
        JSONObject hashes=new JSONObject();for(Map.Entry<String,byte[]> entry:entries.entrySet())hashes.put(entry.getKey(),sha(entry.getValue()));
        manifest.put("entry_sha256",hashes).put("entries_before_manifest",entries.size());
        entries.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        File changed=temp.newFile("purpose-"+label+".alprsession");
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(changed))){
            for(Map.Entry<String,byte[]> entry:entries.entrySet()){
                zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();
            }
        }
        ResearchArchive.verifyEntryHashes(changed);return changed;
    }
    void purposeRejected(File archive,String label)throws Exception{
        Exception error=assertThrows(label,Exception.class,()->MzInputEvidenceValidator.validate(archive));
        assertTrue(label+": "+error.getMessage(),error.getMessage().contains("Diagnostic purpose"));
        export(archive,"negative-purpose-"+label);
    }
    static JSONObject purposeLocation(JSONObject session,JSONObject research,int index)throws Exception{
        return index==0?session:index==1?research:index==2?session.getJSONObject("mz_input_evidence")
                :research.getJSONObject("mz_input_evidence");
    }
    @Test public void M09_nativeProducerDeclaresConsistentSyntheticPurpose()throws Exception{
        File archive=nativePurposeFixture();JSONObject session=metadata(archive,"session.json");
        JSONObject research=metadata(archive,"report.json").getJSONObject("research_collection");
        for(int index=0;index<4;index++)assertEquals("SYNTHETIC_DIAGNOSTIC",
                purposeLocation(session,research,index).getString(index<2?"measurement_purpose":"purpose"));
        assertTrue(session.getString("series_id").startsWith("SYNTHETIC-"));
    }
    @Test public void M10_inconsistentPurposeIsRejectedWithValidManifest()throws Exception{
        File archive=nativePurposeFixture();
        for(int index=0;index<3;index++){
            final int location=index;String label="conflict-"+index;
            purposeRejected(purposeCopy(archive,label,(s,r)->purposeLocation(s,r,location)
                    .put(location<2?"measurement_purpose":"purpose","DEVICE_DIAGNOSTIC")),label);
        }
    }
    @Test public void M11_missingAndWrongTypePurposeAreRejected()throws Exception{
        File archive=nativePurposeFixture();Object[] values={JSONObject.NULL,true,17,new JSONArray(),new JSONObject()};
        for(int index=0;index<3;index++){
            final int location=index;String key=index<2?"measurement_purpose":"purpose";
            String absent="missing-"+index;
            purposeRejected(purposeCopy(archive,absent,(s,r)->purposeLocation(s,r,location).remove(key)),absent);
            for(int value=0;value<values.length;value++){
                Object invalid=values[value];String label="type-"+index+"-"+value;
                purposeRejected(purposeCopy(archive,label,(s,r)->purposeLocation(s,r,location).put(key,invalid)),label);
            }
        }
    }
    @Test public void M12_unsupportedDeviceAndUnknownPurposesStayBlocked()throws Exception{
        File archive=nativePurposeFixture();
        for(String purpose:new String[]{"DEVICE_DIAGNOSTIC","UNKNOWN",""}){
            String label=purpose.isEmpty()?"empty":purpose.toLowerCase(Locale.ROOT);
            purposeRejected(purposeCopy(archive,label,(s,r)->{
                for(int index=0;index<4;index++)purposeLocation(s,r,index).put(index<2?"measurement_purpose":"purpose",purpose);
                if("DEVICE_DIAGNOSTIC".equals(purpose)){
                    s.put("series_id","DEVICE-DIAGNOSTIC-MZ-CONTRACT-TEST");r.put("series_id","DEVICE-DIAGNOSTIC-MZ-CONTRACT-TEST");
                }
            }),label);
        }
    }
    @Test public void M13_diagnosticSeriesMustRemainSynthetic()throws Exception{
        File archive=nativePurposeFixture();Object[] values={null,JSONObject.NULL,17,"","F5A-OFFICIAL","DEVICE-DIAGNOSTIC-MZ-TEST"};
        for(int index=0;index<values.length;index++){
            Object value=values[index];String label="series-"+index;
            File bad=purposeCopy(archive,label,(s,r)->{s.put("series_id",value);r.put("series_id",value);});
            Exception error=assertThrows(Exception.class,()->MzInputEvidenceValidator.validate(bad));
            assertTrue(error.getMessage().contains("Diagnostic series"));export(bad,"negative-purpose-"+label);
        }
    }
    @Test public void M14_frozenDiagnosticFlagsMustBeActualBooleans()throws Exception{
        File archive=nativePurposeFixture();Object[] values={null,JSONObject.NULL,false,"true",1};
        for(int index=0;index<2;index++)for(int value=0;value<values.length;value++){
            final int location=index;Object invalid=values[value];String label="enabled-"+index+"-"+value;
            File bad=purposeCopy(archive,label,(s,r)->(location==0?s:r).put("mz_input_evidence_enabled",invalid));
            Exception error=assertThrows(Exception.class,()->MzInputEvidenceValidator.validate(bad));
            assertTrue(error.getMessage().contains("frozen diagnostic setting"));export(bad,"negative-purpose-"+label);
        }
    }
    @Test public void M15_syntheticInjectionCannotArmOfficialOrRunningSessions()throws Exception{
        for(String variant:new String[]{"R0","R1","R2"}){
            ExperimentSession.Prepared prepared=new ExperimentSession().prepare("roi_budget",variant,TimerConfig.disabled(),ThermalConfig.disabled(),
                    new ExperimentIdentity("F5A-OFFICIAL-GUARD-TEST","SYNTHETIC-TEST",1,"synthetic=true; no device"),null);
            ResearchSessionStore s=ResearchSessionStore.prepare(temp.newFolder(),prepared,16,8,0,(b,f)->{throw new AssertionError("No image is expected");},System::nanoTime);
            stores.add(s);File metadata=new File(s.directory(),"session.json");byte[] before=Files.readAllBytes(metadata.toPath());
            assertThrows(IllegalStateException.class,s::enableMzInputEvidenceForSyntheticProducer);
            assertNull(s.mzInputEvidence());assertArrayEquals(before,Files.readAllBytes(metadata.toPath()));
            s.startAt(System.currentTimeMillis(),System.nanoTime());File archive=finish(s,"mz-f18-off-"+variant.toLowerCase(Locale.ROOT));
            assertEquals("ABSENT",MzInputEvidenceValidator.validate(archive));
        }
        ResearchSessionStore running=store(false);File metadata=new File(running.directory(),"session.json");byte[] before=Files.readAllBytes(metadata.toPath());
        assertThrows(IllegalStateException.class,running::enableMzInputEvidenceForSyntheticProducer);
        assertNull(running.mzInputEvidence());assertArrayEquals(before,Files.readAllBytes(metadata.toPath()));
    }
    static ResearchStageExecutionConfig purposeStage(com.example.alpr_v1.model.ModelRole role,int size){
        return new ResearchStageExecutionConfig(role,true,"synthetic-"+role.name(),"synthetic-fingerprint","fp32",
                role==com.example.alpr_v1.model.ModelRole.VEHICLE?com.example.alpr_v1.model.ModelRuntime.NCNN:com.example.alpr_v1.model.ModelRuntime.TFLITE,
                "fp32",2,false,"cpu",size,size,3,"NHWC","RGB","FLOAT32");
    }
    @Test public void M16_publicRoiConfigurationsCannotEnableDeviceDiagnostics()throws Exception{
        int index=0;
        for(com.example.alpr_v1.pipeline.RoiBudgetPolicy policy:com.example.alpr_v1.pipeline.RoiBudgetPolicy.values()){
            ResearchStageExecutionConfig vehicle=policy.usesVehicleCascade()
                    ?purposeStage(com.example.alpr_v1.model.ModelRole.VEHICLE,640)
                    :ResearchStageExecutionConfig.disabled(com.example.alpr_v1.model.ModelRole.VEHICLE);
            ResearchExecutionConfig config=new ResearchExecutionConfig("roi_budget","R"+index++,policy,
                    com.example.alpr_v1.pipeline.RecognitionProfile.BALANCED,"1280x960",false,false,policy.usesVehicleCascade(),true,true,true,vehicle,
                    purposeStage(com.example.alpr_v1.model.ModelRole.PLATE,512),
                    purposeStage(com.example.alpr_v1.model.ModelRole.CHARACTER,320));
            String before=config.toJson().toString();
            assertEquals(MzInputEvidence.DIAGNOSTIC_BLOCK_REASON,
                    assertThrows(IllegalStateException.class,()->config.withMzInputEvidence(true)).getMessage());
            assertFalse(config.mzInputEvidenceEnabled());assertSame(config,config.withMzInputEvidence(false));assertEquals(before,config.toJson().toString());
        }
    }

    File mutate(File original,String fault)throws Exception{
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipFile zip=new ZipFile(original)){Enumeration<? extends ZipEntry> all=zip.entries();while(all.hasMoreElements()){
            ZipEntry e=all.nextElement();entries.put(e.getName(),zip.getInputStream(e).readAllBytes());}}
        JSONObject row=new JSONObject(new String(entries.get(MzInputEvidence.INDEX),StandardCharsets.UTF_8).trim());
        switch(fault){
            case "tensor-sha":row.getJSONObject("tensor").put("sha256",String.join("",Collections.nCopies(64,"0")));break;
            case "payload-missing":entries.remove(row.getJSONObject("tensor").getString("entry"));break;
            case "source-stamp":row.getJSONObject("source_stamp").put("source_timestamp_nanos",17L);break;
            case "unknown-invocation":row.put("mt_invocation_id","nonexistent-real-invocation");break;
            case "shape":row.getJSONObject("tensor").put("shape",new JSONArray().put(1).put(8).put(99).put(3));break;
        }
        String index=row+"\n";if("duplicate-index".equals(fault))index+=index;
        entries.put(MzInputEvidence.INDEX,index.getBytes(StandardCharsets.UTF_8));
        JSONObject manifest=new JSONObject(new String(entries.remove("manifest.json"),StandardCharsets.UTF_8));JSONObject hashes=new JSONObject();
        for(Map.Entry<String,byte[]> entry:entries.entrySet())hashes.put(entry.getKey(),sha(entry.getValue()));
        manifest.put("entry_sha256",hashes).put("entries_before_manifest",entries.size());
        entries.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        String placeholder=MzInputEvidence.INDEX.replace("mz_input","mz_inpux");
        if("duplicate-zip".equals(fault))entries.put(placeholder,entries.get(MzInputEvidence.INDEX));
        File bad=temp.newFile(fault+".alprsession");
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(bad))){for(Map.Entry<String,byte[]> e:entries.entrySet()){
            zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}
        if("duplicate-zip".equals(fault)){
            byte[] bytes=Files.readAllBytes(bad.toPath()),from=placeholder.getBytes(StandardCharsets.UTF_8),to=MzInputEvidence.INDEX.getBytes(StandardCharsets.UTF_8);
            for(int i=0;i<=bytes.length-from.length;i++){boolean match=true;for(int j=0;j<from.length;j++)if(bytes[i+j]!=from[j]){match=false;break;}
                if(match){System.arraycopy(to,0,bytes,i,to.length);i+=from.length-1;}}
            Files.write(bad.toPath(),bytes);
        }
        return bad;
    }
    static String sha(byte[] bytes)throws Exception{
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
}
