package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CropSessionStoreInstrumentedTest {
    private File root() {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"crop-session-test-"+UUID.randomUUID());
    }
    @Test public void exportFlushesPendingWritesAndKeepsOneExactReadWithDistinctEntities() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(120,30,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.WHITE);
        try(CropSessionStore store = new CropSessionStore(root())) {
            com.example.alpr_v1.pipeline.PlateObservation first = DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,10,"AAA");
            store.record("session-a",first,DynamicRecognitionHistoryInstrumentedTest.telemetry(),"normal");
            store.record("session-a",first,DynamicRecognitionHistoryInstrumentedTest.telemetry(),"normal");
            store.record("session-a",DynamicRecognitionHistoryInstrumentedTest.observation(null,2,9,8,11,"AAA"),null,"normal");
            store.record("session-a",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,12,"AAB"),null,"normal");
            store.record("session-b",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,3,10,9,13,"BBB"),null,"normal");
            bitmap.recycle(); // Recorder must already own any image needed by its worker.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertEquals(2,(int)store.export("session-a",()->bytes).get(20,TimeUnit.SECONDS));
            Map<String,byte[]> entries = readZip(bytes.toByteArray());
            assertEquals(3,entries.size());
            JSONObject manifest = new JSONObject(new String(entries.get("session.json"),StandardCharsets.UTF_8));
            assertEquals("session-a",manifest.getString("session_id"));
            org.json.JSONArray crops = manifest.getJSONArray("crops");
            JSONObject aaa = crops.getJSONObject(0);
            assertEquals("AAA",aaa.getString("text"));
            assertEquals(2,aaa.getJSONArray("observations").length());
            assertEquals(4,aaa.getJSONArray("observations").getJSONObject(0).getLong("entity_id"));
            assertEquals(9,aaa.getJSONArray("observations").getJSONObject(1).getLong("entity_id"));
            assertEquals(29.5,aaa.getJSONArray("observations").getJSONObject(0).getJSONObject("hud").getDouble("fps"),.001);
            assertEquals("W",aaa.getJSONArray("characters").getJSONObject(0).getString("label"));
            assertEquals("AAB",crops.getJSONObject(1).getString("text"));
            for(int i=0;i<crops.length();i++) {
                byte[] image = entries.get(crops.getJSONObject(i).getString("image"));
                assertNotNull(image); assertEquals(0xff,image[0]&0xff); assertEquals(0xd8,image[1]&0xff);
            }
            assertEquals(2,store.sessions().get(20,TimeUnit.SECONDS).size());
        } finally { if(!bitmap.isRecycled()) bitmap.recycle(); }
    }
    @Test public void diskSessionsSurviveStoreRecreationAndGalleryCapacityEviction() throws Exception {
        File root = root(); Bitmap bitmap = Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);
        RecognitionHistoryStore gallery = new RecognitionHistoryStore(1);
        try {
            try(CropSessionStore store = new CropSessionStore(root)) {
                for(int index=0;index<3;index++) {
                    com.example.alpr_v1.pipeline.PlateObservation observation = DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,index+1,index+1,index+1,"AAA"+index);
                    gallery.upsertObservation(observation,null,true,"normal");
                    store.record("durable",observation,null,"normal").get(20,TimeUnit.SECONDS);
                }
                assertEquals(1,gallery.size()); gallery.clear();
            }
            try(CropSessionStore restored = new CropSessionStore(root)) {
                assertEquals(3,restored.sessions().get(20,TimeUnit.SECONDS).get(0).crops);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                assertEquals(3,(int)restored.export("durable",()->bytes).get(20,TimeUnit.SECONDS));
                assertEquals(4,readZip(bytes.toByteArray()).size());
            }
        } finally { gallery.clear(); bitmap.recycle(); }
    }
    @Test public void emptyMzAndCarriedConsensusDoNotCreateDiskSessions() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);
        try(CropSessionStore store = new CropSessionStore(root())) {
            store.record("empty",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,1,1,1,"AAA",true,""),null,"normal").get();
            store.record("empty",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,1,1,2,"AAA",false,"AAA"),null,"normal").get();
            assertTrue(store.sessions().get().isEmpty());
        } finally { bitmap.recycle(); }
    }
    @Test public void failedDestinationIsReportedAndSourceSessionCanBeExportedAgain() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);
        try(CropSessionStore store = new CropSessionStore(root())) {
            store.record("retry",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,1,1,1,"AAA"),null,"normal").get();
            try {
                store.export("retry",()->new OutputStream() { @Override public void write(int value) throws IOException { throw new IOException("disk full"); }}).get();
                fail("Expected write failure");
            } catch(java.util.concurrent.ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); }
            assertEquals(1,(int)store.export("retry",ByteArrayOutputStream::new).get());
        } finally { bitmap.recycle(); }
    }
    private static Map<String,byte[]> readZip(byte[] bytes) throws IOException {
        Map<String,byte[]> entries = new LinkedHashMap<>();
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                ByteArrayOutputStream value = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
                while((count=zip.read(buffer))!=-1) value.write(buffer,0,count);
                assertNull(entries.put(entry.getName(),value.toByteArray()));
            }
        }
        return entries;
    }
}
