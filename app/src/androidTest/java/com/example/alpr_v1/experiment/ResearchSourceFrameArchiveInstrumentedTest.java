package com.example.alpr_v1.experiment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.vision.Detection;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** New sessions only: one pre-ROI pipeline Bitmap per MT invocation, shared by its detections. */
@RunWith(AndroidJUnit4.class)
public final class ResearchSourceFrameArchiveInstrumentedTest {
    private File files;
    private final List<ResearchSessionStore> stores=new ArrayList<>();

    @Before public void setup() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        files=new File(target.getCacheDir(),"research-frames-test-"+UUID.randomUUID());
        Files.createDirectories(files.toPath());
    }
    @After public void cleanup() throws Exception {
        for (ResearchSessionStore store:stores) {
            java.lang.reflect.Field field=ResearchSessionStore.class.getDeclaredField("writer");
            field.setAccessible(true);
            ExecutorService writer=(ExecutorService)field.get(store);
            writer.shutdownNow(); writer.awaitTermination(5,TimeUnit.SECONDS);
        }
        if (files!=null && files.exists()) {
            try (java.util.stream.Stream<java.nio.file.Path> walk=Files.walk(files.toPath())) {
                for (java.nio.file.Path file:(Iterable<java.nio.file.Path>)walk.sorted(Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(file);
            }
        }
    }
    private ResearchSessionStore running(ResearchSessionStore.ImageEncoder encoder) throws Exception {
        ExperimentSession session=new ExperimentSession();
        ExperimentSession.Prepared prepared=session.prepare("quality","R0",TimerConfig.of(true,5),
                ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        ResearchSessionStore store=ResearchSessionStore.prepare(new File(files,"sessions"),prepared,
                16,8,0,encoder);
        stores.add(store);
        store.startAt(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        return store;
    }
    private static String entry(ZipFile zip,String name) throws Exception {
        ZipEntry element=zip.getEntry(name); assertNotNull("Archive entry: "+name,element);
        try (InputStream stream=zip.getInputStream(element)) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int n;
            while((n=stream.read(buffer))!=-1)out.write(buffer,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static List<JSONObject> jsonl(ZipFile zip) throws Exception {
        List<JSONObject> rows=new ArrayList<>();
        for (String line:entry(zip,"samples/attempts.jsonl").split("\\r?\\n"))
            if (!line.trim().isEmpty()) rows.add(new JSONObject(line));
        return rows;
    }
    private static Bitmap solid(int w,int h,int color) {
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
    private static String captureTwoDetections(ResearchSessionStore store) {
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,
                new ContinuityStamp(3,4,5,77L,555L,SourceTimestampDomain.UNKNOWN),"R1",1f);
        AcquisitionAttemptRecord call=batch.beginMt(42,42,18,9,118,58,64,32);
        assertNotNull(call);
        Bitmap source=solid(160,100,Color.RED);
        call.copySourceFrame(source);
        source.recycle();
        Bitmap input=solid(64,32,Color.GREEN);
        call.copyEvidence(input); input.recycle();
        call.mtStarted();
        for (int i=0;i<2;i++) {
            Detection detection=new Detection(0,.9f,30+i,30,70+i,45,Collections.emptyList());
            batch.detected(call,detection,false,160,100);
        }
        batch.finish("","");
        return call.attemptId;
    }
    private static File finish(ResearchSessionStore store) throws Exception {
        store.closeAdmission("manual",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        return store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
    }
    @Test public void twoDetectionsShareOneArchivedPreRoiFrameAndRoiIsInSourcePixels() throws Exception {
        ResearchSessionStore store=running(ResearchSessionStore::encodeJpeg);
        String id=captureTwoDetections(store); File zipFile=finish(store);
        ResearchArchive.verifyEntryHashes(zipFile);
        try (ZipFile zip=new ZipFile(zipFile)) {
            List<JSONObject> rows=jsonl(zip); assertEquals(2,rows.size());
            String frame="samples/frames/"+id+".jpg";
            assertNotNull(zip.getEntry(frame));
            JSONObject manifest=new JSONObject(entry(zip,"manifest.json"));
            assertTrue(manifest.getJSONObject("entry_sha256").has(frame));
            for (int i=0;i<rows.size();i++) {
                JSONObject row=rows.get(i);
                assertEquals(id,row.getString("mt_invocation_id"));
                assertEquals(i,row.getInt("mt_detection_index"));
                assertEquals(2,row.getInt("mt_detection_count"));
                assertTrue(row.getBoolean("source_frame_expected"));
                assertEquals(frame,row.getString("source_frame_entry"));
                assertEquals("WRITTEN",row.getString("source_frame_status"));
                assertEquals("",row.getString("source_frame_missing_evidence_reason"));
                assertEquals(160,row.getInt("source_frame_width_px"));
                assertEquals(100,row.getInt("source_frame_height_px"));
                assertEquals("mt_source_bitmap_xy",row.getString("source_frame_coordinate_space"));
                assertEquals(18,row.getInt("roi_left"));assertEquals(9,row.getInt("roi_top"));
                assertEquals(118,row.getInt("roi_right"));assertEquals(58,row.getInt("roi_bottom"));
                assertEquals(77,row.getLong("source_sequence"));assertEquals(555,row.getLong("source_timestamp_nanos"));
                assertNotEquals(frame,row.getString("mt_input_evidence_entry"));
            }
            try (InputStream stream=zip.getInputStream(zip.getEntry(frame))) {
                Bitmap decoded=BitmapFactory.decodeStream(stream);assertNotNull(decoded);
                assertEquals(160,decoded.getWidth());assertEquals(100,decoded.getHeight());
                assertTrue(Color.red(decoded.getPixel(20,20))>230);decoded.recycle();
            }
            JSONObject schema=new JSONObject(entry(zip,"samples/schema.json"));
            assertTrue(schema.getJSONObject("capabilities").getBoolean("mt_source_frame_pre_roi_evidence"));
            JSONObject session=new JSONObject(entry(zip,"session.json"));
            assertEquals("COMPLETED",session.getString("state"));
            assertEquals(0,session.getInt("source_frame_integrity_loss_count"));
        }
    }
    @Test public void deletedFrameMakesArchivePartialAndCountsMissingOnlyOncePerInvocation() throws Exception {
        ResearchSessionStore store=running(ResearchSessionStore::encodeJpeg);
        String id=captureTwoDetections(store);finish(store);
        Files.delete(new File(store.directory(),"samples/frames/"+id+".jpg").toPath());
        File rebuilt=ResearchSessionStore.buildArchive(store.directory(),null);
        ResearchArchive.verifyEntryHashes(rebuilt);
        try (ZipFile zip=new ZipFile(rebuilt)) {
            JSONObject session=new JSONObject(entry(zip,"session.json"));
            assertEquals("PARTIAL",session.getString("state"));
            assertEquals(1,session.getInt("source_frame_integrity_loss_count"));
            assertEquals(1,session.getInt("integrity_loss_count"));
            String csv=entry(zip,"samples/attempts.csv");
            assertTrue(csv.contains("missing_source_frame_image"));
            assertTrue(csv.contains("MISSING"));
            for (JSONObject row:jsonl(zip))assertTrue(row.getBoolean("mt_executed"));
        }
    }
    @Test public void failedFullFrameEncodingDoesNotEraseOriginalMtInputOrCall() throws Exception {
        ResearchSessionStore store=running((bitmap,target)->{
            if (bitmap.getWidth()==160)throw new IOException("simulated_source_frame_encoding_failure");
            ResearchSessionStore.encodeJpeg(bitmap,target);
        });
        String id=captureTwoDetections(store); File result=finish(store);
        ResearchArchive.verifyEntryHashes(result);
        try (ZipFile zip=new ZipFile(result)) {
            JSONObject session=new JSONObject(entry(zip,"session.json"));
            assertEquals("PARTIAL",session.getString("state"));
            assertEquals(1,session.getInt("source_frame_integrity_loss_count"));
            List<JSONObject> rows=jsonl(zip);assertEquals(2,rows.size());
            for(JSONObject row:rows) {
                assertEquals("WRITTEN",row.getString("write_state"));
                assertTrue(row.getBoolean("mt_executed"));
                assertNotNull(zip.getEntry(row.getString("mt_input_evidence_entry")));
                assertEquals("MISSING",row.getString("source_frame_status"));
            }
            assertNull(zip.getEntry("samples/frames/"+id+".jpg"));
        }
    }
}
