package com.example.alpr_v1.experiment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.capture.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.vision.Detection;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Produces distributable artifacts using Android's real stores and serializers. */
@RunWith(AndroidJUnit4.class)
public class InteropArtifactInstrumentedTest {
    private File output() {
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"interop");
        file.mkdirs(); return file;
    }
    private ResearchSessionStore running() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExperimentSession domain = new ExperimentSession();
        ExperimentSession.Prepared prepared=domain.prepare("quality","R0",TimerConfig.of(true,5),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        File root=new File(context.getCacheDir(),"interop-research-"+UUID.randomUUID());
        ResearchSessionStore store=ResearchSessionStore.prepare(root,prepared,32,16,0,ResearchSessionStore::encodeJpeg);
        store.startAt(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos()); return store;
    }
    private File finish(ResearchSessionStore store,String name) throws Exception {
        store.closeAdmission("fixture",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtimeNanos());
        JSONObject report=new JSONObject().put("schema","alpr.mobile_benchmark_report.v1")
                .put("report_id","interop-"+name).put("package_id","contract-probe").put("variant_id","controlled-test")
                .put("fixture",true).put("app_build",com.example.alpr_v1.metrics.BuildProvenance.snapshot());
        File archive=store.finish(new ResearchSessionStore.Telemetry(report.toString(),"frame_id\n","timestamp\n","timestamp\n","","fixture"),null);
        ResearchArchive.verifyEntryHashes(archive);
        File dest=new File(output(),name+".alprsession");Files.copy(archive.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }
    private AcquisitionAttemptRecord call(ResearchAttemptBatch batch,Bitmap evidence) {
        AcquisitionAttemptRecord record=batch.beginMt(4,4,0,0,64,32,64,32);
        record.put("input_scale",1);record.put("input_pad_x",0);record.put("input_pad_y",0);
        if(evidence!=null) record.copyEvidence(evidence);
        record.mtStarted();record.put("mt_backend","fixture/no-inference"); return record;
    }
    private static JSONObject json(ZipFile zip,String name) throws Exception {
        try(InputStream in=zip.getInputStream(zip.getEntry(name))) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)bytes.write(b,0,n);
            return new JSONObject(bytes.toString("UTF-8"));
        }
    }
    @Test public void researchSerializersProduceCompletePartialAndBackendFailureFixtures() throws Exception {
        Bitmap evidence=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888);evidence.eraseColor(Color.GREEN);
        Bitmap crop=Bitmap.createBitmap(32,12,Bitmap.Config.ARGB_8888);crop.eraseColor(Color.WHITE);
        ContinuityStamp stamp=new ContinuityStamp(1,1,0,12,123456,SourceTimestampDomain.UNKNOWN);
        ResearchSessionStore store=running();
        try {
            for(int size:new int[]{0,1,3}) {
                ResearchAttemptBatch batch=new ResearchAttemptBatch(store,stamp,"R0",1);
                AcquisitionAttemptRecord call=call(batch,evidence);
                for(int i=0;i<size;i++) {
                    Detection d=new Detection(0,.9f,4+i*12,8,14+i*12,20,Collections.emptyList());batch.detected(call,d,true);
                    AcquisitionAttemptRecord child=batch.forDetection(d);
                    if(i==0) {
                        child.copyPlateCrop(crop);child.put("mz_executed",true);child.put("mz_backend","fixture/no-inference");
                        child.put("mz_status",size==1 ? "READ" : "NO_CHARACTERS");
                        String raw=size==1 ? "aaa123" : "";
                        PlateObservation observation=DynamicRecognitionHistoryInstrumentedTest.observation(crop,1,4,7,12,raw,true,raw).withContinuityStamp(stamp);
                        batch.observe(d,observation);
                    }
                }
                batch.finish("","");
            }
            ResearchAttemptBatch failed=new ResearchAttemptBatch(store,stamp,"R0",1);
            AcquisitionAttemptRecord failure=call(failed,evidence);failure.put("execution_error","fixture_backend_error");
            failed.finish("","fixture_processing_error");
            ResearchAttemptBatch cancelled=new ResearchAttemptBatch(store,stamp,"R0",1);call(cancelled,evidence);cancelled.finish("scene_superseded","");
            ResearchAttemptBatch notRun=new ResearchAttemptBatch(store,stamp,"R0",1);
            notRun.beginMt(4,4,0,0,64,32,64,32).copyEvidence(evidence);notRun.finish("","");
            File full=finish(store,"research-complete");assertEquals(ResearchSessionStore.State.COMPLETED,store.state());
            try(ZipFile zip=new ZipFile(full)) {
                assertTrue(json(zip,"session.json").getBoolean("collection_complete"));
                assertEquals("uppercase_alphanumeric.v1",json(zip,"samples/schema.json").getString("normalization_policy"));
                assertNotNull(json(zip,"environment/software.json").getJSONObject("app_build"));
            }
            ResearchSessionStore partial=running();ResearchAttemptBatch missing=new ResearchAttemptBatch(partial,stamp,"R0",1);
            AcquisitionAttemptRecord noInput=call(missing,null);noInput.copyPlateCrop(crop);missing.finish("","");
            finish(partial,"research-partial");assertEquals(ResearchSessionStore.State.PARTIAL,partial.state());
        } finally { evidence.recycle();crop.recycle(); }
    }
    @Test public void canonicalCropSessionPreservesRawEntitiesAndSingleImageAcrossResume() throws Exception {
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"interop-crops-"+UUID.randomUUID());
        Bitmap bitmap=Bitmap.createBitmap(40,12,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED);
        PlateObservation first=DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,1,4,7,10,"aaa123");
        try(CropSessionStore store=new CropSessionStore(root)) {
            store.record("interop",first,DynamicRecognitionHistoryInstrumentedTest.telemetry(),"normal").get();
        }
        try(CropSessionStore store=new CropSessionStore(root)) {
            store.record("interop",first,null,"normal");
            store.record("interop",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,11,"AAA123"),null,"normal");
            store.record("interop",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,12,"AA A-123"),null,"normal");
            store.record("interop",DynamicRecognitionHistoryInstrumentedTest.observation(bitmap,2,9,8,13,"AAA123",false,"AAA123"),null,"normal");
            File destination=new File(output(),"crop-session.zip");
            assertEquals(1,(int)store.export("interop",()->new FileOutputStream(destination)).get());
            try(ZipFile zip=new ZipFile(destination)) {
                assertEquals(2,zip.size());JSONObject manifest=json(zip,"session.json");
                JSONObject group=manifest.getJSONArray("crops").getJSONObject(0);
                assertEquals("aaa123",group.getString("text"));assertEquals("AAA123",group.getString("registration_key"));
                JSONArray observations=group.getJSONArray("observations");assertEquals(3,observations.length());
                assertEquals("aaa123",observations.getJSONObject(0).getString("raw_prediction"));
                assertEquals("AAA123",observations.getJSONObject(1).getString("raw_prediction"));
                assertEquals("AA A-123",observations.getJSONObject(2).getString("raw_prediction"));
                assertEquals(4,observations.getJSONObject(0).getLong("entity_id"));assertEquals(9,observations.getJSONObject(1).getLong("entity_id"));
                assertNotNull(manifest.getJSONObject("app_build"));
            }
        } finally { bitmap.recycle(); }
    }
    @Test public void laterProcessingFailureDoesNotRelabelEarlierMtAsBackendError() throws Exception {
        Bitmap image=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888);
        ResearchSessionStore store=running();
        ResearchAttemptBatch batch=new ResearchAttemptBatch(store,new ContinuityStamp(1,0,0,1),"R0",1);
        call(batch,image);batch.finish("","mz_failed");image.recycle();
        File archive=finish(store,"processing-error");
        try(ZipFile zip=new ZipFile(archive);InputStream in=zip.getInputStream(zip.getEntry("samples/attempts.jsonl"))) {
            BufferedReader reader=new BufferedReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
            JSONObject row=new JSONObject(reader.readLine());assertEquals("",row.optString("execution_error"));
            assertEquals("mz_failed",row.getString("processing_error"));
        }
    }
}
