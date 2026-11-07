package com.example.alpr_v1.experiment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.vision.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Native Bitmap adapter acceptance. Compiled offline; device execution requires separate authorization. */
@RunWith(AndroidJUnit4.class)
public class F5aSceneEvidenceInstrumentedTest {
    private File root;
    private ResearchSessionStore store;
    @Before public void setup()throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        root=new File(context.getCacheDir(),"f5a-evidence-"+UUID.randomUUID());
        ExperimentSession.Prepared prepared=new ExperimentSession().prepare("roi_budget","R2",TimerConfig.disabled(),
                ThermalConfig.disabled(),new ExperimentIdentity("F5A-SYNTHETIC-PILOT","SYNTHETIC",1,"native bitmap fixture"),null);
        store=ResearchSessionStore.prepare(root,prepared,256,64,0,ResearchSessionStore::encodeJpeg);
        store.startAt(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
    }
    @After public void cleanup()throws Exception {
        if(store!=null) {
            java.lang.reflect.Field field=ResearchSessionStore.class.getDeclaredField("writer");field.setAccessible(true);
            ExecutorService writer=(ExecutorService)field.get(store);writer.shutdownNow();writer.awaitTermination(5,TimeUnit.SECONDS);
        }
        if(root!=null && root.exists())try(java.util.stream.Stream<Path> walk=Files.walk(root.toPath())) {
            for(Path path:(Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);
        }
    }
    private File finish()throws Exception {
        store.closeAdmission("manual",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        File archive=store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true}","","","","",""),null);
        ResearchArchive.verifyEntryHashes(archive);return archive;
    }
    private static JSONObject first(ZipFile zip,String name)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=zip.getInputStream(zip.getEntry(name))) {
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
        }
        String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        return new JSONObject(text.split("\\R")[0]);
    }
    @Test public void zeroRoiCapturesImmutableNativeFullFrameWithoutMt()throws Exception {
        ContinuityStamp stamp=new ContinuityStamp(1,1,0,10,1000000,SourceTimestampDomain.CAMERAX_SENSOR);
        Bitmap source=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
        store.captureSceneEvidence(store.f5aEvidence().ticket(stamp),stamp,source);
        source.eraseColor(Color.BLUE);source.recycle();
        try(ZipFile zip=new ZipFile(finish())) {
            JSONObject scene=first(zip,F5aEvidence.SCENES);assertEquals("WRITTEN",scene.getString("write_status"));
            assertEquals(0,scene.getJSONArray("mt_invocation_ids").length());
            Bitmap decoded=BitmapFactory.decodeStream(zip.getInputStream(zip.getEntry(scene.getString("source_frame_entry"))));
            assertNotNull(decoded);assertEquals(160,decoded.getWidth());assertEquals(100,decoded.getHeight());
            assertTrue(Color.red(decoded.getPixel(40,40))>230);decoded.recycle();
        }
    }
    @Test public void actualBitmapAttemptAndRetainedIdentityRoundTripTogether()throws Exception {
        ContinuityStamp stamp=new ContinuityStamp(1,1,0,10,1000000,SourceTimestampDomain.CAMERAX_SENSOR);
        Bitmap source=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);source.eraseColor(Color.GREEN);
        store.captureSceneEvidence(store.f5aEvidence().ticket(stamp),stamp,source);
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,stamp,"r2_two_roi",1f,7);
        AcquisitionAttemptRecord call=batch.beginMt(1,1,0,0,160,100,64,32);
        call.copySourceFrame(source);call.copyEvidence(source);call.mtStarted();
        Detection d=new Detection(0,.9f,20,30,60,50,Arrays.asList(new Point2(20,30,1),new Point2(60,30,1),new Point2(60,50,1),new Point2(20,50,1)));
        batch.detected(call,d,true,160,100);batch.decoded(call);
        List<Detection> raw=Collections.singletonList(d);batch.postprocessed(raw,DetectionDeduplicator.suppress(raw,.45f,.82f,false),false);
        batch.finish("","");source.recycle();
        try(ZipFile zip=new ZipFile(finish())) {
            JSONObject roles=first(zip,F5aEvidence.ROLES);assertEquals(call.attemptId,roles.getString("mt_invocation_id"));
            assertEquals(call.attemptId,roles.getJSONArray("final_attempt_ids").getString(0));
            assertNotNull(zip.getEntry(roles.getString("source_frame_entry")));
        }
    }
}
