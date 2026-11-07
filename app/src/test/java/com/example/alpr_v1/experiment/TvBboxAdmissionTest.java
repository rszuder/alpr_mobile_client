package com.example.alpr_v1.experiment;

import android.app.Application;
import android.graphics.Bitmap;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Public admission and the native writer, with no model execution and no synthetic START bypass. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvBboxAdmissionTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    final List<ResearchSessionStore> stores=new ArrayList<>();
    public static ResearchExecutionConfig base(RoiBudgetPolicy policy,boolean az){
        ResearchStageExecutionConfig mt=stage(ModelRole.PLATE,512),mz=stage(ModelRole.CHARACTER,320);
        return new ResearchExecutionConfig("roi_budget","R"+policy.ordinal(),policy,RecognitionProfile.BALANCED,"640x400",az,az,
                policy.usesVehicleCascade(),true,false,false,policy.usesVehicleCascade()?stage(ModelRole.VEHICLE,640):ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE),mt,mz);
    }
    static ResearchStageExecutionConfig stage(ModelRole role,int side){return new ResearchStageExecutionConfig(role,true,"synthetic-"+role.wireName(),"f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",side,side,3,"NHWC","RGB","FLOAT32");}
    public static ResearchExecutionConfig config(RoiBudgetPolicy policy,TvBboxCrop crop){return base(policy,false).withCameraSetup(crop.cameraSetup).withTvBboxCrop(crop);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    ResearchSessionStore prepared(ResearchExecutionConfig c)throws Exception{
        ResearchSessionStore store=TvBboxProducerFixture.prepareOnly(temp.newFolder(),c);stores.add(store);return store;
    }
    @After public void close()throws Exception{for(ResearchSessionStore s:stores)TvBboxProducerFixture.close(s);}
    @Test public void R0R1R2PublicSynchronousAndQueuedStartFreezeAndWriteTheSameContract()throws Exception{
        for(RoiBudgetPolicy policy:RoiBudgetPolicy.values())for(boolean queued:new boolean[]{false,true}){
            TvBboxCrop crop=TvBboxCropTest.selection(640,400);ResearchExecutionConfig c=config(policy,crop);
            ExperimentSession session=new ExperimentSession();ExperimentSession.Prepared p=session.prepare("roi_budget",c.variant,TimerConfig.disabled(),ThermalConfig.disabled(),new ExperimentIdentity("SYNTHETIC-F5A22-TV-BBOX","SYNTHETIC",1,"synthetic=true; DEVICE_RUN=NOT_RUN"),c);
            ResearchSessionStore store=TvBboxProducerFixture.prepareFor(temp.newFolder(),p);stores.add(store);
            long wall=System.currentTimeMillis(),elapsed=System.nanoTime();
            if(queued)store.startAtQueued(wall,elapsed,e->{throw new AssertionError(e);}).get(10,TimeUnit.SECONDS);else store.startAt(wall,elapsed);
            assertTrue(session.startPrepared(p,wall,elapsed));assertTrue(store.accepting());assertSame(c,session.frozenExecutionConfig());assertEquals(store.sessionId(),session.sessionId());
            Bitmap camera=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
            try{
                ContinuityStamp stamp=new ContinuityStamp(1,1,0,1,9007199254740995L,SourceTimestampDomain.CAMERAX_SENSOR);
                String scene=store.f5aEvidence().ticket(stamp);store.captureSceneEvidence(scene,stamp,camera);
                try(TvBboxCrop.Frame frame=crop.open(camera,crop.geometry,crop.cameraSetup)){store.tvBboxEvidence().capture(frame,stamp).complete("MT_NOT_RUN","");}
            }finally{camera.recycle();}
            session.finish(ExperimentSession.CompletionReason.MANUAL);
            File archive=TvBboxProducerFixture.finish(store,c);assertEquals("PASS",TvBboxInputValidator.validate(archive));
            try(ZipFile zip=new ZipFile(archive)){
                JSONObject data=new JSONObject(new String(zip.getInputStream(zip.getEntry("session.json")).readAllBytes(),StandardCharsets.UTF_8));
                assertEquals(data.getString("started_at"),data.getString("tv_bbox_crop_frozen_at_utc"));assertEquals(crop.digest,data.getJSONObject("tv_bbox_crop").getString("digest_sha256"));
                assertEquals("ACCEPTED_F5A21",data.getJSONObject("tv_bbox_input_evidence").getString("desktop_contract_status"));
                assertEquals(0,zip.getEntry("samples/attempts.jsonl").getSize());
            }
            export(archive,"start-"+c.variant.toLowerCase(Locale.ROOT)+(queued?"-queued":"-sync"));
        }
    }
    @Test public void modeAloneCannotSelectAnUncroppedCameraAndFullFrameStaysIdentical()throws Exception{
        ResearchExecutionConfig full=base(RoiBudgetPolicy.FULL_FRAME,false);String original=full.toJson().toString();
        assertThrows(IllegalStateException.class,()->full.withInputMode(ResearchInputMode.TV_BBOX_CROP));assertEquals(original,full.toJson().toString());
        full.requireSessionInputReady();assertSame(full,full.withInputMode(ResearchInputMode.CAMERA_FULL_FRAME));
        assertThrows(IllegalStateException.class,()->full.withInputMode(ResearchInputMode.TV_RECTIFIED));assertThrows(IllegalStateException.class,()->full.withMzInputEvidence(true));
        TvBboxCrop crop=TvBboxCropTest.selection(640,400);ResearchExecutionConfig selected=config(RoiBudgetPolicy.FULL_FRAME,crop);
        assertSame(selected,selected.withInputMode(ResearchInputMode.TV_BBOX_CROP));
        assertEquals(ResearchInputMode.CAMERA_FULL_FRAME,selected.withInputMode(ResearchInputMode.CAMERA_FULL_FRAME).inputMode());assertNull(selected.withInputMode(ResearchInputMode.CAMERA_FULL_FRAME).tvBboxCrop());
    }
    @Test public void autoZoomCannotBeSilentlyDisabledBySelectingCrop()throws Exception{
        TvBboxCrop crop=TvBboxCropTest.selection(640,400);ResearchExecutionConfig az=base(RoiBudgetPolicy.FULL_FRAME,true).withCameraSetup(crop.cameraSetup);
        assertThrows(IllegalStateException.class,()->az.withTvBboxCrop(crop));assertTrue(az.autoZoomEnabled);
    }
    @Test public void badFrozenBboxDigestCameraAndZoomCannotReachRunning()throws Exception{
        for(String fault:new String[]{"bbox","digest","camera","autozoom"}){
            ResearchSessionStore store=prepared(config(RoiBudgetPolicy.FULL_FRAME,TvBboxCropTest.selection(640,400)));JSONObject metadata=(JSONObject)field(store,"metadata");
            switch(fault){
                case "bbox":JSONObject b=metadata.getJSONObject("tv_bbox_crop");b.getJSONObject("tv_bbox_px").put("right",b.getJSONObject("tv_bbox_px").getInt("left"));SetupJson.seal(b);break;
                case "digest":metadata.getJSONObject("tv_bbox_crop").put("digest_sha256","bad");break;
                case "camera":metadata.getJSONObject("execution").getJSONObject("camera_setup").put("logical_camera_id","different");break;
                case "autozoom":metadata.getJSONObject("execution").getJSONObject("feature_flags").put("autozoom",true);break;
            }
            assertThrows("Reject "+fault,Exception.class,()->store.startAt(System.currentTimeMillis(),System.nanoTime()));
            assertFalse(store.accepting());assertEquals(ResearchSessionStore.State.PREPARED,store.state());assertTrue(metadata.isNull("started_at"));assertFalse(metadata.has("tv_bbox_crop_frozen_at_utc"));
        }
    }
    @Test public void duplicateStartCannotChangeTheFrozenStartOrAdmitAnotherSession()throws Exception{
        ResearchSessionStore store=prepared(config(RoiBudgetPolicy.FULL_FRAME,TvBboxCropTest.selection(640,400)));long wall=System.currentTimeMillis();store.startAt(wall,System.nanoTime());
        JSONObject before=new JSONObject(((JSONObject)field(store,"metadata")).toString());
        assertThrows(IllegalStateException.class,()->store.startAt(wall+5000,System.nanoTime()));assertThrows(IllegalStateException.class,()->store.startAtQueued(wall+5000,System.nanoTime(),e->fail()));
        assertEquals(before.toString(),((JSONObject)field(store,"metadata")).toString());assertTrue(store.accepting());
    }
    @Test public void confirmationAfterStartAndStoppedPreparationAreRejectedBeforeAdmission()throws Exception{
        ResearchSessionStore future=prepared(config(RoiBudgetPolicy.FULL_FRAME,TvBboxCropTest.selection(640,400)));
        assertThrows(IllegalStateException.class,()->future.startAt(0,System.nanoTime()));assertFalse(future.accepting());
        ResearchSessionStore cancelled=prepared(config(RoiBudgetPolicy.FULL_FRAME,TvBboxCropTest.selection(640,400)));cancelled.abortPreparation("synthetic_cancelled_before_start");
        assertThrows(IllegalStateException.class,()->cancelled.startAt(System.currentTimeMillis(),System.nanoTime()));assertFalse(cancelled.accepting());
    }
    @Test public void startWriteFailureCannotAdmitModels()throws Exception{
        ResearchSessionStore store=prepared(config(RoiBudgetPolicy.FULL_FRAME,TvBboxCropTest.selection(640,400)));File obstruction=new File(store.directory(),"session.json.tmp");assertTrue(obstruction.mkdir());
        AtomicInteger failed=new AtomicInteger();CompletableFuture<Void> result=store.startAtQueued(System.currentTimeMillis(),System.nanoTime(),e->failed.incrementAndGet());
        assertThrows(ExecutionException.class,()->result.get(10,TimeUnit.SECONDS));TvBboxProducerFixture.drain(store);assertEquals(1,failed.get());assertFalse(store.accepting());assertEquals("session_start_write_failed",field(store,"lastFailure"));
    }
    static void export(File source,String name)throws Exception{
        String folder=System.getenv("F5A22_FIXTURES");if(folder==null)return;java.nio.file.Path dest=Paths.get(folder,name+".alprsession");Files.createDirectories(dest.getParent());Files.copy(source.toPath(),dest,StandardCopyOption.REPLACE_EXISTING);
    }
}
