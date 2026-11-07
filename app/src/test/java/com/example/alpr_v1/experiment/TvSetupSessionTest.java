package com.example.alpr_v1.experiment;

import com.example.alpr_v1.setup.*;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;
import static com.example.alpr_v1.setup.TvSetupGeometryTest.*;

public class TvSetupSessionTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private ResearchSessionStore prepare(String type)throws Exception{
        ExperimentSession.Prepared prepared=new ExperimentSession().prepare(type,"R0",TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity("F5A14A-SYNTHETIC","SYNTHETIC",1,"synthetic=true"),null);
        return ResearchSessionStore.prepare(temp.newFolder(),prepared,128,32,0,(bitmap,file)->{throw new AssertionError("No model or image write allowed");},()->1000L);
    }
    private static JSONObject json(File file)throws Exception{return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
    private static JSONObject snapshot(){
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);
        TvDistanceCalculator.Inputs input=TvDistanceCalculator.Inputs.diagonal(55,16,9,.05);
        try {
            // Explicit synthetic characteristics, never claimed as Galaxy A12 metadata.
            JSONObject metadata=new JSONObject().put("camera_id","0").put("focal_length_mm",4)
                    .put("sensor_width_mm",6.4).put("sensor_height_mm",4.8).put("pixel_array_width",4000).put("pixel_array_height",3000)
                    .put("active_array_rect_px",SetupJson.array(new double[]{0,0,4000,3000}))
                    .put("capture_crop_rect_px",SetupJson.array(new double[]{0,0,4000,3000})).put("control_zoom_ratio",1)
                    .put("sensor_to_buffer_matrix",SetupJson.array(new double[]{.48,0,0,0,.48,-180,0,0,1}))
                    .put("minimum_focus_distance_diopters",JSONObject.NULL).put("capture_timestamp_nanos",1000)
                    .put("source","SYNTHETIC_TEST_FIXTURE_NOT_DEVICE");
            TvDistanceCalculator.Estimate estimate=TvDistanceCalculator.fromCamera(input,metadata,g);assertTrue(estimate.available());
            return TvSetupCalibration.confirm(TRAPEZOID,g,1,"2026-11-05T12:00:00Z").snapshot(g,input,null,estimate,"2026-11-05T12:01:00Z");
        }catch(Exception e){throw new AssertionError(e);}
    }
    @Test public void K04_K05_frozenAtActualStartAndRecoveredInArchiveWithoutFakeAttempts()throws Exception{
        ResearchSessionStore store=prepare("roi_budget");JSONObject setup=snapshot();
        assertFalse(json(new File(store.directory(),"session.json")).has("tv_setup_calibration"));
        store.startAtQueued(1000000,1000,setup,e->{throw new AssertionError(e);}).get(5,TimeUnit.SECONDS);
        String digest=setup.getString("digest_sha256");setup.put("status","STALE");
        JSONObject persisted=json(new File(store.directory(),"session.json")).getJSONObject("tv_setup_calibration");
        assertEquals("READY",persisted.getString("status"));assertEquals(digest,persisted.getString("digest_sha256"));assertTrue(SetupJson.verified(persisted));
        assertEquals(0,Files.size(new File(store.directory(),"samples/attempts.jsonl").toPath()));
        store.closeAdmission("manual",1000010,1010);
        File archive=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
        artifact("tv-ready.alprsession",archive);
        String artifactRoot=System.getenv("F5A14A_FIXTURES");
        if(artifactRoot!=null)Files.write(new File(artifactRoot,"tv-ready-snapshot.json").toPath(),persisted.toString(2).getBytes(StandardCharsets.UTF_8));
        try(ZipFile zip=new ZipFile(archive)){
            JSONObject report=new JSONObject(new String(zip.getInputStream(zip.getEntry("report.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(digest,report.getJSONObject("research_collection").getJSONObject("tv_setup_calibration").getString("digest_sha256"));
            JSONObject manifest=new JSONObject(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertTrue(manifest.getJSONObject("entry_sha256").has("session.json"));assertTrue(manifest.getJSONObject("entry_sha256").has("report.json"));
        }
    }
    @Test public void K05_legacyF4NeverAcquiresOptionalTvField()throws Exception{
        ResearchSessionStore store=prepare("pipeline_comparison");store.startAtQueued(1000000,1000,snapshot(),e->{throw new AssertionError(e);}).get(5,TimeUnit.SECONDS);
        assertFalse(json(new File(store.directory(),"session.json")).has("tv_setup_calibration"));
        store.closeAdmission("manual",1000010,1010);store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
    }
    @Test public void K05_invalidOptionalMetadataDoesNotChange12aAdmissionOrErrorSemantics()throws Exception{
        ResearchSessionStore store=prepare("roi_budget");JSONObject invalid=snapshot();invalid.put("digest_sha256","bad");
        store.startAtQueued(1000000,1000,invalid,e->{throw new AssertionError(e);}).get(5,TimeUnit.SECONDS);
        assertTrue(store.accepting());assertFalse(json(new File(store.directory(),"session.json")).has("tv_setup_calibration"));
        store.closeAdmission("manual",1000010,1010);store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
    }
    @Test public void K05_uncalibratedAndStaleRemainExplicitAfterStart()throws Exception {
        CameraPreviewGeometry current=geometry(0,960,540,2,"0",1),old=geometry(0,960,540,1,"0",1);
        for(TvSetupCalibration calibration:new TvSetupCalibration[]{TvSetupCalibration.empty(),TvSetupCalibration.confirm(TRAPEZOID,old,1,"2026-11-05T12:00:00Z")}) {
            String expected=calibration.corners.isEmpty()?"UNCALIBRATED":"STALE";
            ResearchSessionStore store=prepare("roi_budget");JSONObject setup=calibration.snapshot(current,null,null,TvDistanceCalculator.unavailable("synthetic"),"2026-11-05T12:01:00Z");
            store.startAtQueued(1000000,1000,setup,e->{throw new AssertionError(e);}).get(5,TimeUnit.SECONDS);
            assertEquals(expected,store.tvSetupSnapshot().getString("status"));
            store.closeAdmission("manual",1000010,1010);
            artifact("tv-"+expected.toLowerCase(java.util.Locale.ROOT)+".alprsession",store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null));
        }
    }
    private static void artifact(String name,File source)throws Exception {
        String root=System.getenv("F5A14A_FIXTURES");if(root==null)return;
        File directory=new File(root);Files.createDirectories(directory.toPath());
        Files.copy(source.toPath(),new File(directory,name).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
