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
import java.lang.reflect.Field;
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
public class TvBboxEvidenceTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    final List<ResearchSessionStore> stores=new ArrayList<>();
    Bitmap camera;
    TvBboxCrop crop;
    ResearchExecutionConfig config;
    @Before public void setup()throws Exception{
        camera=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);crop=TvBboxCropTest.selection(640,400);
        ResearchStageExecutionConfig mt=new ResearchStageExecutionConfig(ModelRole.PLATE,true,"synthetic-mt","f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",512,512,3,"NHWC","RGB","FLOAT32");
        ResearchStageExecutionConfig mz=new ResearchStageExecutionConfig(ModelRole.CHARACTER,true,"synthetic-mz","f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",320,320,3,"NHWC","RGB","FLOAT32");
        config=new ResearchExecutionConfig("roi_budget","R0",RoiBudgetPolicy.FULL_FRAME,RecognitionProfile.BALANCED,"640x400",false,false,false,true,false,false,
                ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE),mt,mz).withCameraSetup(crop.cameraSetup).withTvBboxCrop(crop);
    }
    @After public void close()throws Exception{for(ResearchSessionStore store:stores)TvBboxProducerFixture.close(store);if(!camera.isRecycled())camera.recycle();}
    static Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    ResearchSessionStore prepared(String series,int capacity,ResearchSessionStore.ImageEncoder encoder)throws Exception{
        ExperimentSession.Prepared p=new ExperimentSession().prepare("roi_budget","R0",TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity(series,"SYNTHETIC",1,"synthetic=true; DEVICE_RUN=NOT_RUN"),config);
        ResearchSessionStore store=ResearchSessionStore.prepare(temp.newFolder(),p,capacity,32,0,encoder,System::nanoTime);stores.add(store);return store;
    }
    static void jpeg(Bitmap bitmap,File target)throws IOException{
        try(FileOutputStream out=new FileOutputStream(target)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("JPEG");}
    }
    ResearchSessionStore start()throws Exception{
        ResearchSessionStore store=prepared("SYNTHETIC-F5A20-TV-BBOX",256,TvBboxEvidenceTest::jpeg);
        store.startAt(System.currentTimeMillis(),System.nanoTime());return store;
    }
    static ContinuityStamp stamp(int sequence){return new ContinuityStamp(1,1,0,sequence,9007199254740993L+sequence,SourceTimestampDomain.CAMERAX_SENSOR);}
    void capture(ResearchSessionStore store,int sequence)throws Exception{
        String scene=store.f5aEvidence().ticket(stamp(sequence));if(!scene.isEmpty())store.captureSceneEvidence(scene,stamp(sequence),camera);
        try(TvBboxCrop.Frame frame=crop.open(camera,crop.geometry,crop.cameraSetup)){
            store.tvBboxEvidence().capture(frame,stamp(sequence)).complete("MT_NOT_RUN","");
        }
    }
    static JSONObject json(File archive,String entry)throws Exception{
        try(ZipFile zip=new ZipFile(archive)){return new JSONObject(new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8).trim());}
    }
    static void export(File archive,String name)throws Exception{
        String target=System.getenv("F5A20_FIXTURES");if(target!=null){Files.createDirectories(Paths.get(target));Files.copy(archive.toPath(),Paths.get(target,name+".alprsession"),StandardCopyOption.REPLACE_EXISTING);}
    }
    @Test public void publicStartsUseAcceptedContractWithoutSyntheticBypass()throws Exception{
        ResearchSessionStore store=prepared("UNOFFICIAL-TV-BBOX",8,TvBboxEvidenceTest::jpeg);
        store.startAt(System.currentTimeMillis(),System.nanoTime());assertTrue(store.accepting());
        assertThrows(IllegalStateException.class,()->store.startAtQueued(System.currentTimeMillis(),System.nanoTime(),e->fail()));
        ExperimentSession session=new ExperimentSession();ExperimentSession.Prepared p=session.prepare("roi_budget","R0",TimerConfig.disabled(),ThermalConfig.disabled(),ExperimentIdentity.defaults(),config);
        assertTrue(session.startPrepared(p,System.currentTimeMillis(),System.nanoTime()));assertTrue(session.isRunning());
    }
    @Test public void frozenSnapshotRoundTripIsImmutableAndBoundToActualStart()throws Exception{
        ResearchSessionStore store=start();capture(store,1);File archive=TvBboxProducerFixture.finish(store,config);
        assertEquals("PASS",TvBboxInputValidator.validate(archive));JSONObject session=json(archive,"session.json");
        assertEquals(session.getString("started_at"),session.getString("tv_bbox_crop_frozen_at_utc"));
        assertEquals(crop.digest,TvBboxCrop.fromJson(session.getJSONObject("tv_bbox_crop")).digest);
        JSONObject mutable=config.toJson();mutable.getJSONObject("tv_bbox_crop").put("input_width_px",1);
        assertEquals(crop.width(),config.tvBboxCrop().width());
        String before=json(archive,TvBboxEvidence.INDEX).getString("frame_key");
        File again=ResearchSessionStore.buildArchive(store.directory(),null);assertEquals("PASS",TvBboxInputValidator.validate(again));
        assertEquals(before,json(again,TvBboxEvidence.INDEX).getString("frame_key"));
        assertThrows(IllegalStateException.class,()->store.startAt(System.currentTimeMillis(),System.nanoTime()));export(again,"tv-reexport");
    }
    @Test public void failedWriterReportsMissingWithoutInventingCalls()throws Exception{
        ResearchSessionStore store=prepared("SYNTHETIC-F5A20-TV-BBOX",256,(b,f)->{throw new IOException("synthetic_disk_failure");});
        store.startAt(System.currentTimeMillis(),System.nanoTime());capture(store,1);
        File archive=TvBboxProducerFixture.finish(store,config);assertEquals("PARTIAL",TvBboxInputValidator.validate(archive));
        JSONObject frame=json(archive,TvBboxEvidence.INDEX);assertEquals("MISSING",frame.getJSONObject("source_image").getString("status"));
        assertEquals("image_write_failed",frame.getJSONObject("source_image").getString("missing_reason"));assertEquals(0,frame.getJSONArray("attempt_links").length());
        assertEquals(0,((AtomicLong)field(store,"sourceFrameBytes")).get());assertEquals(0,((AtomicLong)field(store,"imageBytes")).get());export(archive,"tv-write-missing");
    }
    @Test public void reservationsShareExistingFiniteImageBudgets()throws Exception{
        ResearchSessionStore store=start();AtomicLong sources=(AtomicLong)field(store,"sourceFrameBytes"),images=(AtomicLong)field(store,"imageBytes");
        sources.set(24L*1024*1024);images.set(64L*1024*1024);
        try(TvBboxCrop.Frame frame=crop.open(camera,crop.geometry,crop.cameraSetup)){store.tvBboxEvidence().capture(frame,stamp(1)).complete("MT_NOT_RUN","");}
        TvBboxProducerFixture.drain(store);assertEquals(24L*1024*1024,sources.get());assertEquals(64L*1024*1024,images.get());sources.set(0);images.set(0);
        File archive=TvBboxProducerFixture.finish(store,config);assertEquals("PARTIAL",TvBboxInputValidator.validate(archive));
        JSONObject row=json(archive,TvBboxEvidence.INDEX);assertEquals("source_frame_memory_or_copy_limit",row.getJSONObject("source_image").getString("missing_reason"));export(archive,"tv-budget-missing");
    }
    @Test public void fullQueueDoesNotBlockModelThreadAndReleasesCopies()throws Exception{
        ResearchSessionStore store=prepared("SYNTHETIC-F5A20-TV-BBOX",1,TvBboxEvidenceTest::jpeg);store.startAt(System.currentTimeMillis(),System.nanoTime());
        TvBboxProducerFixture.drain(store);
        ThreadPoolExecutor writer=(ThreadPoolExecutor)field(store,"writer");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        writer.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));writer.execute(()->{});
        try{try(TvBboxCrop.Frame frame=crop.open(camera,crop.geometry,crop.cameraSetup)){store.tvBboxEvidence().capture(frame,stamp(1)).complete("MT_NOT_RUN","");}
            assertEquals(1,writer.getQueue().size());assertEquals(0,((AtomicLong)field(store,"sourceFrameBytes")).get());assertEquals(0,((AtomicLong)field(store,"imageBytes")).get());
        }finally{release.countDown();}
        File archive=TvBboxProducerFixture.finish(store,config);assertEquals("PARTIAL",TvBboxInputValidator.validate(archive));
        assertEquals("writer_queue_full",json(archive,TvBboxEvidence.INDEX).getJSONObject("crop_image").getString("missing_reason"));export(archive,"tv-queue-missing");
    }
    @Test public void unavailableSourceAndStaleGeometryProduceNoFakeMt()throws Exception{
        ResearchSessionStore store=start();store.tvBboxEvidence().rejected(null,stamp(1),"source_frame_unavailable").complete("SOURCE_UNAVAILABLE","source_frame_unavailable");
        File archive=TvBboxProducerFixture.finish(store,config);assertEquals("PARTIAL",TvBboxInputValidator.validate(archive));
        JSONObject row=json(archive,TvBboxEvidence.INDEX);assertEquals("SOURCE_UNAVAILABLE",row.getString("pipeline_status"));assertEquals(0,row.getJSONArray("attempt_links").length());export(archive,"tv-source-unavailable");
        ResearchSessionStore stale=start();stale.tvBboxEvidence().rejected(camera,stamp(1),"TV_BBOX_GEOMETRY_CHANGED").complete("REJECTED_GEOMETRY","TV_BBOX_GEOMETRY_CHANGED");
        File stopped=TvBboxProducerFixture.finish(stale,config);assertEquals("PARTIAL",TvBboxInputValidator.validate(stopped));assertEquals("WRITTEN",json(stopped,TvBboxEvidence.INDEX).getJSONObject("source_image").getString("status"));export(stopped,"tv-stale-rejected");
    }
    @Test public void interruptedJournalCannotClaimCompleteAndReexportKeepsSource()throws Exception{
        ResearchSessionStore store=start();capture(store,1);TvBboxProducerFixture.drain(store);TvBboxProducerFixture.close(store);
        File meta=new File(store.directory(),"session.json");JSONObject interrupted=new JSONObject(new String(Files.readAllBytes(meta.toPath()),StandardCharsets.UTF_8));interrupted.put("state","PARTIAL").put("completion_reason","process_interrupted").put("collection_complete",false);
        Files.write(meta.toPath(),interrupted.toString().getBytes(StandardCharsets.UTF_8));File recovered=ResearchSessionStore.buildArchive(store.directory(),null);
        assertEquals("PARTIAL",TvBboxInputValidator.validate(recovered));JSONObject summary=json(recovered,"session.json").getJSONObject("tv_bbox_input_evidence");
        assertTrue(summary.getBoolean("pending_loss_unknown"));assertFalse(summary.getBoolean("complete"));
        assertEquals("WRITTEN",json(recovered,TvBboxEvidence.INDEX).getJSONObject("source_image").getString("status"));export(recovered,"tv-interrupted");
    }
    @Test public void metadataFrameLimitFailsClosed()throws Exception{
        ResearchSessionStore store=start();capture(store,1);TvBboxProducerFixture.drain(store);
        Map<String,Object> frames=(Map<String,Object>)field(store.tvBboxEvidence(),"frames");Object first=frames.values().iterator().next();
        for(int i=frames.size();i<TvBboxEvidence.MAX_FRAMES;i++)frames.put("synthetic-limit-"+i,first);
        assertThrows(IllegalStateException.class,()->store.tvBboxEvidence().rejected(null,stamp(2),"source_frame_unavailable"));
    }
}
