package com.example.alpr_v1.experiment;

import com.example.alpr_v1.camera.CameraSelection;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

public class CameraSetupContractTest {
    @Test public void legacyInputModeCannotBeChangedToUnsupportedRectification() throws Exception {
        ResearchExecutionConfig config=config("roi_budget");String before=config.toJson().toString();
        assertEquals(com.example.alpr_v1.setup.ResearchInputMode.CAMERA_FULL_FRAME,config.inputMode());
        assertSame(config,config.withInputMode(com.example.alpr_v1.setup.ResearchInputMode.CAMERA_FULL_FRAME));
        assertEquals("BLOCKED_DESKTOP_CONTRACT",assertThrows(IllegalStateException.class,
                ()->config.withInputMode(com.example.alpr_v1.setup.ResearchInputMode.TV_RECTIFIED)).getMessage());
        assertEquals(before,config.toJson().toString());assertFalse(config.toJson().has("input_mode"));
    }
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static ResearchExecutionConfig config(String type){
        ResearchStageExecutionConfig mt=new ResearchStageExecutionConfig(ModelRole.PLATE,true,"mt","f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",512,512,3,"NHWC","RGB","FLOAT32");
        ResearchStageExecutionConfig mz=new ResearchStageExecutionConfig(ModelRole.CHARACTER,true,"mz","f","v",ModelRuntime.TFLITE,"fp32",2,false,"cpu",320,320,3,"NHWC","RGB","FLOAT32");
        return new ResearchExecutionConfig(type,"R0",RoiBudgetPolicy.FULL_FRAME,RecognitionProfile.BALANCED,"1280x960",false,false,false,true,true,true,ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE),mt,mz);
    }
    @Test public void cameraSnapshotIsOptionalImmutableAndF5aOnly() throws Exception {
        ResearchExecutionConfig original=config("roi_budget");JSONObject before=original.toJson();assertFalse(before.has("camera_setup"));
        CameraSetupSnapshot setup=new CameraSetupSnapshot(new CameraSelection("0","2"),1.75f);ResearchExecutionConfig frozen=original.withCameraSetup(setup);
        JSONObject serialized=frozen.toJson();assertTrue(setup.same(CameraSetupSnapshot.fromJson(serialized.getJSONObject("camera_setup"))));
        serialized.remove("camera_setup");assertEquals(before.toString(),serialized.toString());assertFalse(original.toJson().has("camera_setup"));
        assertFalse(config("pipeline_comparison").toJson().has("camera_setup"));assertThrows(IllegalStateException.class,()->config("pipeline_comparison").withCameraSetup(setup));
        assertThrows(IllegalStateException.class,()->ResearchExecutionConfig.requireUnchangedWhileRunning(frozen,original,true));
    }
    @Test public void missingOrInvalidSnapshotDoesNotInventACamera(){
        assertNull(CameraSetupSnapshot.restore(""));assertNull(CameraSetupSnapshot.restore("{}"));
        assertThrows(IllegalArgumentException.class,()->new CameraSetupSnapshot(CameraSelection.defaultBack(),1));
        assertThrows(IllegalArgumentException.class,()->new CameraSetupSnapshot(new CameraSelection("0",null),Float.NaN));
    }
    @Test public void frozenCameraAndTvGeometryRoundTripThroughProducerArchiveWithoutAttempts() throws Exception {
        CameraSetupSnapshot selected=new CameraSetupSnapshot(new CameraSelection("0","2"),1.75f);
        ResearchExecutionConfig frozen=config("roi_budget").withCameraSetup(selected);
        ExperimentSession.Prepared prepared=new ExperimentSession().prepare("roi_budget","R0",TimerConfig.disabled(),ThermalConfig.disabled(),
                new ExperimentIdentity("F5A14A-CAMERA-SYNTHETIC","SYNTHETIC",1,"synthetic=true"),frozen);
        ResearchSessionStore store=ResearchSessionStore.prepare(temp.newFolder(),prepared,128,32,0,
                (bitmap,file)->{throw new AssertionError("No image/model writes in setup test");},()->1000L);
        com.example.alpr_v1.setup.CameraPreviewGeometry geometry=com.example.alpr_v1.setup.TvSetupGeometryTest.geometry(0,960,540,1.75,"0",1).withPhysicalCameraId("2");
        JSONObject setup=com.example.alpr_v1.setup.TvSetupCalibration.confirm(com.example.alpr_v1.setup.TvSetupGeometryTest.TRAPEZOID,geometry,1,"2026-11-05T12:00:00Z")
                .snapshot(geometry,null,null,com.example.alpr_v1.setup.TvDistanceCalculator.unavailable("SYNTHETIC_NO_FOV"),"2026-11-05T12:01:00Z");
        store.startAtQueued(1000000,1000,setup,e->{throw new AssertionError(e);}).get(5,TimeUnit.SECONDS);
        setup.put("status","STALE");store.closeAdmission("manual",1000010,1010);
        File archive=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
        try(ZipFile zip=new ZipFile(archive)){
            JSONObject report=new JSONObject(new String(zip.getInputStream(zip.getEntry("report.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertTrue(selected.same(CameraSetupSnapshot.fromJson(report.getJSONObject("research_execution_config").getJSONObject("camera_setup"))));
            assertEquals("READY",report.getJSONObject("research_collection").getJSONObject("tv_setup_calibration").getString("status"));
            assertEquals(0,zip.getEntry("samples/attempts.jsonl").getSize());
            JSONObject manifest=new JSONObject(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),StandardCharsets.UTF_8));
            assertTrue(manifest.getJSONObject("entry_sha256").has("session.json"));assertTrue(manifest.getJSONObject("entry_sha256").has("report.json"));
        }
        String target=System.getenv("F5A14A_CAMERA_FIXTURES");if(target!=null){File dir=new File(target);dir.mkdirs();Files.copy(archive.toPath(),new File(dir,"camera-physical-zoom.alprsession").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
    }
}
