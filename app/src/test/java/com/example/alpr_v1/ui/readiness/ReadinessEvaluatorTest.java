package com.example.alpr_v1.ui.readiness;

import android.app.Application;
import android.content.SharedPreferences;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.ui.ReadinessTestModels;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class ReadinessEvaluatorTest {
    SharedPreferences prefs;ModelRegistry registry;AutoTuneManager tune;ReadinessEvaluator evaluator;
    @Before public void setup()throws Exception {
        Application app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences(SettingsActivity.PREFERENCES,0);
        prefs.edit().clear().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true)
                .putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget").putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r0_full_frame")
                .putString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME").putBoolean(SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,true).commit();
        registry=ReadinessTestModels.install(app,false,true);tune=new AutoTuneManager(app);evaluator=new ReadinessEvaluator();
    }
    ReadinessSnapshot snapshot(ReadinessSnapshot.Device device) { return ReadinessSnapshot.capture(prefs,registry,tune,null,
            ResearchSessionStore.sessionsRoot(RuntimeEnvironment.getApplication()),device,1); }
    ReadinessResult result() { return evaluator.evaluate(snapshot(ReadinessSnapshot.Device.options(true,false))); }
    @Test public void installedModelsAndUnpreparedSessionAreDeferredWithoutImportSuggestion(){
        Map<String,?> before=new HashMap<>(prefs.getAll());ReadinessResult result=result();
        assertEquals(ReadinessResult.State.DEFERRED,result.state);assertNull(result.finding("model_plate"));assertNull(result.finding("model_character"));
        ReadinessFinding preparation=result.finding("session_preparation");assertNotNull(preparation);assertNull(preparation.target);
        assertFalse(preparation.blocksByExistingRule);assertEquals(before,prefs.getAll());
        assertEquals(0,Optional.ofNullable(ResearchSessionStore.sessionsRoot(RuntimeEnvironment.getApplication()).listFiles(File::isDirectory)).orElse(new File[0]).length);
    }
    @Test public void r0AllowsMissingVehicleAndR1R2ExposeTheSameConstructorRequirement(){
        assertNull(result().finding("model_vehicle"));
        for(RoiBudgetPolicy policy:new RoiBudgetPolicy[]{RoiBudgetPolicy.ONE_ROI,RoiBudgetPolicy.TWO_ROI}){
            prefs.edit().putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,policy.wireName()).commit();
            assertEquals(ReadinessResult.State.NEEDS_CHANGE,result().state);assertNotNull(result().finding("model_vehicle"));
            assertEquals("R1/R2 wymagają aktywnego etapu MP",assertThrows(IllegalArgumentException.class,()->configuration(policy,false,false,true,SceneHandlingMode.DYNAMIC_CONTINUITY)).getMessage());
        }
        configuration(RoiBudgetPolicy.FULL_FRAME,false,false,true,SceneHandlingMode.DYNAMIC_CONTINUITY);
    }
    @Test public void autoExecutionAndStalePinGoToTheirOwnControls(){
        prefs.edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"auto").commit();
        assertEquals(ReadinessTarget.Action.EXECUTION,result().finding("model_plate_execution").target.action);
        InstalledModel mt=registry.getActive(ModelRole.PLATE);
        RuntimeEnvironment.getApplication().getSharedPreferences("autotune",0).edit()
                .putString("variant_pin.plate."+mt.fingerprint(),"removed-variant").commit();
        assertEquals(ReadinessTarget.Action.VARIANT,result().finding("model_plate_variant").target.action);
        assertThrows(IllegalStateException.class,()->tune.requireResearchVariant(mt));
    }
    @Test public void ordinaryAutoAndDormantResearchFlagsDoNotCreateNewRequirements(){
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).putBoolean(Final180PhotoRoi.ENABLED,true)
                .putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r2_two_roi").putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"auto").commit();
        tune.clearPinnedVariant(registry.getActive(ModelRole.PLATE));
        ReadinessResult result=result();assertEquals("Skanowanie tablic",result.goal);assertEquals(ReadinessResult.State.DEFERRED,result.state);
        assertNull(result.finding("model_vehicle"));assertNull(result.finding("model_plate_variant"));assertNull(result.finding("photo_area"));
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"runtime").commit();
        assertNull(result().finding("photo_area"));assertNull(result().finding("photo_input"));
    }
    @Test public void bothKnownPhotoConflictsAppearBeforeDeviceConfirmation(){
        prefs.edit().putBoolean(Final180PhotoRoi.ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r1_one_roi").commit();
        ReadinessResult result=result();assertEquals(ReadinessResult.State.NEEDS_CHANGE,result.state);
        assertNotNull(result.finding("photo_input"));assertNotNull(result.finding("photo_strategy"));assertNotNull(result.finding("photo_area"));
        assertEquals(ReadinessTarget.Action.TV_CORNERS,result.finding("photo_area").target.action);
        assertNotNull(result.finding("camera_verification"));
        assertThrows(IllegalStateException.class,()->configuration(RoiBudgetPolicy.FULL_FRAME,false,false,true,SceneHandlingMode.DYNAMIC_CONTINUITY)
                .withFinal180(null));
    }
    @Test public void fixedZoomAndDynamicLockKeepTheirOriginalConditionalContracts(){
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true)
                .putBoolean(SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,false).commit();
        assertNotNull(result().finding("tv_fixed_zoom"));assertNotNull(result().finding("autozoom_lock"));
        assertEquals("Autozoom wymaga włączonego locka",assertThrows(IllegalArgumentException.class,
                ()->configuration(RoiBudgetPolicy.FULL_FRAME,false,true,false,SceneHandlingMode.DYNAMIC_CONTINUITY)).getMessage());
        prefs.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName()).commit();
        assertNull(result().finding("autozoom_lock"));configuration(RoiBudgetPolicy.FULL_FRAME,false,true,false,SceneHandlingMode.STRICT_SCENE_BOUNDARY);
    }
    @Test public void realApprovalReviewRemovesOnlyResolvedPhotoIssueAndRetainsDeviceCheck()throws Exception {
        prefs.edit().putBoolean(Final180PhotoRoi.ENABLED,true).putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").commit();
        TvBboxCrop crop=TvBboxCropTest.selection(640,400);
        // Reuse the actual sealed operator-approval fixture, not a guessed rectangle or a successful result flag.
        org.json.JSONObject approval=crop.calibrationJson();
        prefs.edit().putString("tv_setup_calibration_v1",approval.toString()).commit();
        assertEquals(ReadinessTarget.Action.PHOTO_AREA,result().finding("photo_area").target.action);
        Final180PhotoRoi photo=Final180PhotoRoi.prepare(new org.json.JSONArray("[{x:.25,y:.3},{x:.7,y:.3},{x:.7,y:.7},{x:.25,y:.7}]"),crop);
        Final180PhotoRoiApproval proof=Final180PhotoRoiApproval.capture(photo,crop,approval,"TEST_ONLY_READINESS");
        prefs.edit().putString(Final180PhotoRoi.PREFERENCE,photo.toJson().toString()).putString(Final180PhotoRoiApproval.PREFERENCE,proof.toJson().toString()).commit();
        assertNull(result().finding("photo_area"));assertEquals(ReadinessResult.State.DEFERRED,result().state);
        prefs.edit().putString(Final180PhotoRoiApproval.PREFERENCE,"invalid").commit();assertNotNull(result().finding("photo_area"));
    }
    @Test public void changedArtifactInvalidatesAFormerlyPassingModelCheck()throws Exception {
        assertNull(result().finding("model_plate_files"));
        InstalledModel mt=registry.getActive(ModelRole.PLATE);Files.write(mt.resolve("synthetic.tflite").toPath(),new byte[]{1,2,3});
        assertNotNull(result().finding("model_plate_files"));
        ModelVariant variant=tune.requireResearchVariant(mt);
        assertThrows(IllegalStateException.class,()->ModelConfigurationRequirements.requireResearchArtifacts(mt,variant));
    }
    @Test public void savedCameraMismatchAndFutureApprovalAreKnownBeforePreview()throws Exception{
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").commit();
        TvBboxCrop crop=TvBboxCropTest.selection(640,400);
        prefs.edit().putString("tv_setup_calibration_v1",crop.calibrationJson().toString())
                .putString(com.example.alpr_v1.camera.CameraSetupSnapshot.PREFERENCE,TvBboxCropTest.setup(2).toJson().toString()).commit();
        assertNotNull(result().finding("tv_camera_configuration"));
        assertThrows(IllegalArgumentException.class,()->TvBboxCrop.prepare(crop.calibrationJson(),crop.geometry,TvBboxCropTest.setup(2)));
        TvSetupCalibration future=TvSetupCalibration.confirm(TvSetupCalibration.restore(crop.calibrationJson().toString()).corners,
                crop.geometry,1,Instant.now().plusSeconds(86400).toString());
        prefs.edit().putString("tv_setup_calibration_v1",future.approval().toString()).commit();
        assertNotNull(result().finding("tv_confirmation_time"));assertFalse(future.confirmationTimeValid(Instant.now().toString()));
    }
    @Test public void unknownNativeRuntimeIsDeferredWithoutLoadingItAndStaticInputErrorsRemainVisible()throws Exception{
        InstalledModel old=registry.getActive(ModelRole.PLATE);
        org.json.JSONObject json=new org.json.JSONObject(old.manifest().rawJson());json.getJSONObject("input").put("layout","NCHW");
        org.json.JSONObject variant=json.getJSONArray("variants").getJSONObject(0);variant.put("runtime","ncnn").remove("file");
        org.json.JSONArray files=new org.json.JSONArray();org.json.JSONObject sums=new org.json.JSONObject();
        for(String name:new String[]{"synthetic.param","synthetic.bin"}){
            File file=old.resolve(name);Files.write(file.toPath(),new byte[]{1,2,3});files.put(name);
            sums.put(name,com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(file));
        }
        variant.put("files",files).put("sha256",sums);
        Map<ModelRole,InstalledModel> active=ReflectionHelpers.getField(registry,"active");
        active.put(ModelRole.PLATE,new InstalledModel(ModelManifest.parse(json.toString()),old.directory(),old.fingerprint()));
        ReflectionHelpers.setStaticField(RuntimeBackendFactory.class,"observedNcnn",null);
        ReadinessResult result=result();assertEquals(ReadinessResult.State.DEFERRED,result.state);
        assertEquals(ReadinessFinding.Status.NOT_CHECKABLE_YET,result.finding("model_plate_runtime").status);
        assertNull(RuntimeBackendFactory.observedAvailability(ModelRuntime.NCNN));
        json.getJSONObject("input").put("layout","NHWC");
        active.put(ModelRole.PLATE,new InstalledModel(ModelManifest.parse(json.toString()),old.directory(),old.fingerprint()));
        assertNotNull(result().finding("model_plate_input"));assertNull(RuntimeBackendFactory.observedAvailability(ModelRuntime.NCNN));
    }
    @Test public void readingReadinessPreservesTheRealFrozenProtocolSnapshot()throws Exception{
        TvBboxCrop crop=TvBboxCropTest.selection(640,400);
        Final180PhotoRoi photo=Final180PhotoRoi.prepare(new org.json.JSONArray("[{x:.25,y:.3},{x:.7,y:.3},{x:.7,y:.7},{x:.25,y:.7}]"),crop);
        ResearchExecutionConfig frozen=configuration(RoiBudgetPolicy.FULL_FRAME,false,false,true,SceneHandlingMode.DYNAMIC_CONTINUITY)
                .withCameraSetup(crop.cameraSetup).withTvBboxCrop(crop).withFinal180(photo);
        String before=SetupJson.canonical(frozen.toJson());Map<String,?> values=new HashMap<>(prefs.getAll());
        result();result();String after=SetupJson.canonical(frozen.toJson());assertEquals(before,after);assertEquals(values,prefs.getAll());
        String directory=System.getenv("READINESS_AUDIT_ARTIFACTS");
        if(directory!=null){java.nio.file.Path path=java.nio.file.Paths.get(directory);Files.createDirectories(path);
            Files.write(path.resolve("protocol-before-readiness.json"),before.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(path.resolve("protocol-after-readiness.json"),after.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    @Test public void unsavedDraftUsesExactlyTheNormalizationThatWillBePersisted(){
        ReadinessSnapshot.IdentityDraft draft=new ReadinessSnapshot.IdentityDraft("  NEW SERIES  ","  ","bad"," note "," NEW ","DEFAULT");
        ReadinessSnapshot snap=ReadinessSnapshot.capture(prefs,registry,tune,draft,null,ReadinessSnapshot.Device.options(true,false),7);
        assertEquals("NEW SERIES",snap.text(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,""));assertEquals("live_camera",draft.scenario);
        assertEquals(1,draft.replicate);assertEquals("NEW",snap.text(SettingsActivity.KEY_EXPERIMENT_VARIANT,""));
        assertFalse(prefs.contains(SettingsActivity.KEY_EXPERIMENT_SERIES_ID));
    }
    @Test public void readyRequiresPositiveCurrentProofAndFocusIsNotAnUnconditionalGate(){
        ReadinessSnapshot.Device verified=new ReadinessSnapshot.Device(true,true,true,true,false,false,false,false,"",10,20);
        assertEquals(ReadinessResult.State.READY,evaluator.evaluate(snapshot(verified)).state);
        prefs.edit().putBoolean(Final180PhotoRoi.ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"runtime").commit();
        assertNull(evaluator.evaluate(snapshot(verified)).finding("focus"));
        ReadinessSnapshot.Device failedFocus=new ReadinessSnapshot.Device(true,true,true,true,true,false,false,false,"",11,20);
        assertNotNull(evaluator.evaluate(snapshot(failedFocus)).finding("focus"));
        assertEquals(ReadinessResult.State.DEFERRED,result().state);
    }
    ResearchExecutionConfig configuration(RoiBudgetPolicy policy,boolean vehicle,boolean az,boolean lock,SceneHandlingMode scene){
        ResearchStageExecutionConfig mp=vehicle?stage(ModelRole.VEHICLE):ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE);
        return new ResearchExecutionConfig("roi_budget","TEST_ONLY",policy,RecognitionProfile.BALANCED,"auto",lock,az,vehicle,true,true,true,
                mp,stage(ModelRole.PLATE),stage(ModelRole.CHARACTER),null,false,0L,scene);
    }
    ResearchStageExecutionConfig stage(ModelRole role){
        InstalledModel model=registry.getActive(role);return ResearchStageExecutionConfig.enabled(role,model,tune.requireResearchVariant(model),
                ResearchExecutionChoice.CPU_2.resolveApproved(tune.requireResearchVariant(model)),registry);
    }
}
