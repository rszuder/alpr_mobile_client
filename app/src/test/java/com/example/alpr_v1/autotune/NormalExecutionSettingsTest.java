package com.example.alpr_v1.autotune;

import android.app.Application;
import android.content.SharedPreferences;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.inference.*;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.ui.ReadinessTestModels;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.json.JSONObject;
import java.util.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class NormalExecutionSettingsTest {
    Application app; ModelRegistry registry; NormalExecutionSettings normal; AutoTuneManager tune;
    @Before public void setup()throws Exception {
        app=RuntimeEnvironment.getApplication();registry=ReadinessTestModels.install(app,true,true);
        app.getSharedPreferences(NormalExecutionSettings.PREFERENCES,0).edit().clear().commit();
        normal=new NormalExecutionSettings(app);tune=new AutoTuneManager(app);
    }
    InstalledModel model(ModelRole role){return registry.getActive(role);}
    @Test public void olderPreferencesRemainAutoWithoutAnyMigrationWrite(){
        SharedPreferences prefs=app.getSharedPreferences(NormalExecutionSettings.PREFERENCES,0);
        Map<String,?> before=new HashMap<>(prefs.getAll());
        for(ModelRole role:ModelRole.values()) {
            InstalledModel m=model(role);ExecutionProfile legacy=tune.chosenProfile(m);
            ExecutionProfile resolved=tune.normalProfile(m,tune.chosenVariant(m));
            assertEquals(legacy.cpuThreads,resolved.cpuThreads);assertEquals(legacy.gpu,resolved.gpu);
            assertEquals(0,normal.requestedThreads(m));assertFalse(normal.save(m,0));
        }
        assertEquals(before,prefs.getAll());
    }
    @Test public void rolesAndResearchProfilesRemainIndependentAcrossRecreation(){
        Map<String,?> research=new HashMap<>(app.getSharedPreferences(SettingsActivity.PREFERENCES,0).getAll());
        Map<String,?> automatic=new HashMap<>(app.getSharedPreferences("autotune",0).getAll());
        int mt=Math.min(3,NormalExecutionSettings.maximumCpuThreads());
        normal.save(model(ModelRole.PLATE),mt);normal.save(model(ModelRole.CHARACTER),1);
        NormalExecutionSettings reopened=new NormalExecutionSettings(app);
        assertEquals(mt,reopened.requestedThreads(model(ModelRole.PLATE)));
        assertEquals(1,reopened.requestedThreads(model(ModelRole.CHARACTER)));
        assertEquals(0,reopened.requestedThreads(model(ModelRole.VEHICLE)));
        app.getSharedPreferences(SettingsActivity.PREFERENCES,0).edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"cpu_4").commit();
        assertEquals(mt,reopened.requestedThreads(model(ModelRole.PLATE)));
        app.getSharedPreferences(SettingsActivity.PREFERENCES,0).edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,(String)research.get(SettingsActivity.KEY_RESEARCH_MT_EXECUTION)).commit();
        assertEquals(research,app.getSharedPreferences(SettingsActivity.PREFERENCES,0).getAll());
        assertEquals(automatic,app.getSharedPreferences("autotune",0).getAll());
    }
    @Test public void explicitThreadsSelectCpuAndReturningToAutoRestoresGpu(){
        InstalledModel m=model(ModelRole.PLATE);ModelVariant variant=tune.chosenVariant(m);
        normal.save(m,1);ExecutionProfile explicit=normal.resolve(m,variant,ExecutionProfile.tfliteGpu());
        assertFalse(explicit.gpu);assertEquals(1,explicit.cpuThreads);
        normal.save(m,0);assertTrue(normal.resolve(m,variant,ExecutionProfile.tfliteGpu()).gpu);
        assertEquals(2,normal.revision());assertFalse(normal.save(m,0));assertEquals(2,normal.revision());
    }
    @Test public void laterAutomaticWinnerCannotOverwriteManualCpu()throws Exception {
        InstalledModel m=model(ModelRole.PLATE);normal.save(m,1);
        String key=ReflectionHelpers.callInstanceMethod(tune,"key",ReflectionHelpers.ClassParameter.from(InstalledModel.class,m));
        JSONObject winner=new JSONObject().put("chosen_variant_id","selected").put("runtime","tflite").put("cpu_threads",2).put("gpu",true);
        app.getSharedPreferences("autotune",0).edit().putString(key,winner.toString()).commit();
        assertTrue(tune.chosenProfile(m).gpu);
        ExecutionProfile configured=new AutoTuneManager(app).normalProfile(m,tune.chosenVariant(m));
        assertFalse(configured.gpu);assertEquals(1,configured.cpuThreads);
    }
    @Test public void unsupportedSavedCountIsExplainedAndNeverSilentlyClamped(){
        InstalledModel m=model(ModelRole.PLATE);int invalid=NormalExecutionSettings.maximumCpuThreads()+1;
        assertThrows(IllegalArgumentException.class,()->normal.save(m,invalid));
        String key="cpu_threads."+m.manifest().role().wireName()+"."+m.fingerprint();
        app.getSharedPreferences(NormalExecutionSettings.PREFERENCES,0).edit().putInt(key,invalid).commit();
        assertThrows(IllegalArgumentException.class,()->tune.normalProfile(m,tune.chosenVariant(m)));
        assertEquals(invalid,normal.requestedThreads(m));
    }
    @Test public void readOnlyRegistryKeepsUnobservedNcnnWithoutLoadingIt()throws Exception {
        InstalledModel mt=model(ModelRole.PLATE);JSONObject manifest=new JSONObject(mt.manifest().rawJson());
        manifest.getJSONArray("variants").getJSONObject(0).put("runtime","ncnn");
        Files.write(mt.resolve("manifest.json").toPath(),manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReflectionHelpers.setStaticField(RuntimeBackendFactory.class,"observedNcnn",null);
        Map<String,?> before=new HashMap<>(app.getSharedPreferences("model_registry",0).getAll());
        ModelRegistry inspected=new ModelRegistry(app,true);
        assertNotNull(inspected.getActive(ModelRole.PLATE));assertEquals(ModelRuntime.NCNN,tune.inspectedVariant(inspected.getActive(ModelRole.PLATE)).runtime());
        assertEquals("Auto (liczba wątków do ustalenia)",tune.normalExecutionLabel(inspected.getActive(ModelRole.PLATE),tune.inspectedVariant(inspected.getActive(ModelRole.PLATE))));
        assertNull(RuntimeBackendFactory.observedAvailability(ModelRuntime.NCNN));
        assertEquals(before,app.getSharedPreferences("model_registry",0).getAll());
    }
    @Test public void cpuRequestsMatchEachSupportedBackendAndRejectAnImpossibleAutomaticProfile()throws Exception {
        InstalledModel mt=model(ModelRole.PLATE);
        for(ModelRuntime runtime:ModelRuntime.values()){
            JSONObject json=new JSONObject(mt.manifest().rawJson());json.getJSONArray("variants").getJSONObject(0).put("runtime",runtime.wireName());
            ModelVariant variant=ModelManifest.parse(json.toString()).variants().get(0);
            normal.save(mt,1);ExecutionProfile manual=normal.resolve(mt,variant,new ExecutionProfile(runtime,2,false));
            assertEquals(runtime,manual.runtime);assertEquals(1,manual.cpuThreads);assertFalse(manual.gpu);
            assertTrue(tune.normalExecutionLabel(mt,variant).contains("1 wątek"));
            normal.save(mt,0);
            if(runtime!=ModelRuntime.TFLITE)assertThrows(IllegalArgumentException.class,()->normal.resolve(mt,variant,ExecutionProfile.tfliteGpu()));
        }
    }
}
