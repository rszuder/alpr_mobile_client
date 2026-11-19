package com.example.alpr_v1.ui.readiness;

import android.content.SharedPreferences;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.experiment.Final180PhotoRoi;
import com.example.alpr_v1.experiment.ResearchVariantSelection;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import java.io.File;
import java.util.*;

/** Immutable effective values, including the normalized, unsaved form. No persisted UI state. */
public final class ReadinessSnapshot {
    public static final class IdentityDraft {
        public final String series,scenario,notes,variant;
        public final int replicate;
        public IdentityDraft(String series,String scenario,String replicate,String notes,String variant,String defaultSeries) {
            this.series=normalize(series,defaultSeries);this.scenario=normalize(scenario,"live_camera");
            this.notes=normalize(notes,"");this.variant=normalize(variant,"");int value=1;
            try { value=Math.max(1,Integer.parseInt(normalize(replicate,"1"))); } catch(NumberFormatException ignored) { }
            this.replicate=value;
        }
        private static String normalize(String s,String fallback) { return s==null||s.trim().isEmpty()?fallback:s.trim(); }
        public Map<String,Object> values() {
            Map<String,Object> out=new HashMap<>();
            out.put(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,series);out.put(SettingsActivity.KEY_EXPERIMENT_SCENARIO_ID,scenario);
            out.put(SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX,replicate);out.put(SettingsActivity.KEY_EXPERIMENT_NOTES,notes);
            out.put(SettingsActivity.KEY_EXPERIMENT_VARIANT,variant);return out;
        }
    }
    public static final class Device {
        public final boolean permission,cameraVerified,preparationVerified,thermalVerified,focusRequested,focusHeld,writeBusy,running;
        public final String cameraProblem;
        public final long cameraGeneration,sessionGeneration;
        public Device(boolean permission,boolean camera,boolean prepared,boolean thermal,boolean requested,boolean held,
                      boolean busy,boolean running,String problem,long cameraGeneration,long sessionGeneration) {
            this.permission=permission;cameraVerified=camera;preparationVerified=prepared;thermalVerified=thermal;
            focusRequested=requested;focusHeld=held;writeBusy=busy;this.running=running;cameraProblem=problem==null?"":problem;
            this.cameraGeneration=cameraGeneration;this.sessionGeneration=sessionGeneration;
        }
        public static Device options(boolean permission,boolean busy) {
            return options(permission,busy,false);
        }
        public static Device options(boolean permission,boolean busy,boolean thermalRequired) {
            return new Device(permission,false,false,!thermalRequired,false,false,busy,false,"",0,0);
        }
    }
    public static final class Stage {
        public final InstalledModel model;
        public final ModelVariant variant;
        public final String selectionError;
        public final boolean availabilityPending;
        public final AutoTuneManager.VariantSelection selection;
        Stage(InstalledModel model,ModelVariant variant,String error,boolean pending,AutoTuneManager.VariantSelection selection) {
            this.model=model;this.variant=variant;selectionError=error;availabilityPending=pending;this.selection=selection;
        }
    }
    private static final class UnobservedRuntime extends RuntimeException { }
    public final Map<String,Object> values;
    public final Map<ModelRole,Stage> stages;
    public final InstalledAlprPackage basePackage;
    public final File sessionsRoot;
    public final Device device;
    public final String identity;
    public final long runtimeGeneration;

    public ReadinessSnapshot(Map<String,?> values,Map<ModelRole,Stage> stages,InstalledAlprPackage base,
                             File sessionsRoot,Device device,String identity) {
        this.values=Collections.unmodifiableMap(new HashMap<>(values));
        Map<ModelRole,Stage> copy=new EnumMap<>(ModelRole.class);copy.putAll(stages);
        this.stages=Collections.unmodifiableMap(copy);basePackage=base;this.sessionsRoot=sessionsRoot;
        this.device=device;this.identity=identity;runtimeGeneration=RuntimeBackendFactory.availabilityGeneration();
    }
    public static ReadinessSnapshot capture(SharedPreferences prefs,ModelRegistry registry,AutoTuneManager tune,
                                            IdentityDraft draft,File sessions,Device device,long ticket) {
        Map<String,Object> values=new HashMap<>(prefs.getAll());if(draft!=null)values.putAll(draft.values());
        if (!values.containsKey(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY) && values.containsKey("roi_budget_policy"))
            values.put(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY, values.get("roi_budget_policy"));
        boolean research=Boolean.TRUE.equals(values.get(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED));
        Map<ModelRole,Stage> stages=new EnumMap<>(ModelRole.class);
        for(ModelRole role:ModelRole.values()) {
            InstalledModel model=registry.getActive(role);ModelVariant variant=null;String error="";boolean pending=false;
            AutoTuneManager.VariantSelection selection=model==null?null:tune.selectionPreferences(model);
            if(model!=null)try {
                if(research)variant=ResearchVariantSelection.requirePinned(model,selection.pinnedId);
                else variant=selection.choose(model,runtime->{
                    Boolean available=RuntimeBackendFactory.observedAvailability(runtime);
                    if(available==null)throw new UnobservedRuntime();return available;
                });
            } catch(UnobservedRuntime unknown) { pending=true; }
            catch(RuntimeException invalid) { error=invalid.getMessage()==null?invalid.toString():invalid.getMessage(); }
            stages.put(role,new Stage(model,variant,error,pending,selection));
        }
        return new ReadinessSnapshot(values,stages,registry.getBasePackage(),sessions,device,
                ticket+":"+device.cameraGeneration+":"+device.sessionGeneration+":"+RuntimeBackendFactory.availabilityGeneration());
    }
    public String text(String key,String fallback) { Object v=values.get(key);return v instanceof String?(String)v:fallback; }
    public ReadinessSnapshot withDevice(Device device) { return new ReadinessSnapshot(values,stages,basePackage,sessionsRoot,device,identity+":"+device.cameraGeneration+":"+device.sessionGeneration); }
    public boolean flag(String key,boolean fallback) { Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback; }
    public boolean research() { return flag(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false); }
    public String category() { String v=text(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget");return v.trim().isEmpty()?"roi_budget":v; }
    public RoiBudgetPolicy policy() { return RoiBudgetPolicy.fromWireName(text(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,RoiBudgetPolicy.TWO_ROI.wireName())); }
    public boolean tv() { return research()&&"roi_budget".equals(category())&&"TV_BBOX_CROP".equals(text(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME")); }
    public boolean photo() { return research()&&"roi_budget".equals(category())&&flag(Final180PhotoRoi.ENABLED,false); }
    public String goal() {
        if(!research())return "Skanowanie tablic";
        if(photo())return tv()&&policy()==RoiBudgetPolicy.FULL_FRAME?"Końcowe badanie na fotografiach":"Konfiguracja badawcza";
        return tv()?"Analiza obrazu z ekranu TV":"Skanowanie z zapisem wyników";
    }
    public Collection<File> watchedDirectories() {
        Set<File> out=new HashSet<>();
        for(Stage stage:stages.values())if(stage.model!=null) {
            out.add(stage.model.directory());
            for(ModelVariant v:stage.model.manifest().variants())for(String file:v.files())out.add(stage.model.resolve(file).getParentFile());
        }
        if(basePackage!=null&&basePackage.sourceArchive()!=null)out.add(basePackage.sourceArchive().getParentFile());
        return out;
    }
}
