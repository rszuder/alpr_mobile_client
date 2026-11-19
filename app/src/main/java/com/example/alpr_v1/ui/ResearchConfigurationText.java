package com.example.alpr_v1.ui;

import android.content.SharedPreferences;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.model.*;
import org.json.JSONObject;
import java.util.*;

/** Presentation only. Never selects a model, writes preferences or changes a research type. */
public final class ResearchConfigurationText {
    private ResearchConfigurationText() { }
    public static boolean isF6(JSONObject execution) {
        return execution!=null&&Final180PhotoRoi.PROFILE.equals(execution.optString("capture_profile"));
    }
    public static String title(JSONObject execution) {
        return isF6(execution)?"F6 · test końcowy":type(execution==null?"":execution.optString("experiment_type"));
    }
    public static String title(ResearchSessionSummary summary){return isF6(summary.execution)?"F6 · test końcowy":type(summary.value("experiment_type"));}
    public static String type(String value) {
        switch(value){case "roi_budget":return "Budżet ROI";case "runtime":return "Runtime i kwantyzacja";
            case "consensus":return "Konsensus odczytu";case "continuity":return "Ciągłość sceny i odzyskiwanie";
            case "end_to_end":return "Pełny przebieg ALPR";default:return value.isEmpty()?"Konfiguracja nieznana":value;}
    }
    public static String details(JSONObject e) {
        if(e==null)return "Konfiguracja nieznana";
        List<String> parts=new ArrayList<>();String p=e.optString("roi_budget_policy");
        parts.add(p.equals("r0_full_frame")?"Strategia R0":p.equals("r1_one_roi")?"Strategia R1":p.equals("r2_two_roi")?"Strategia R2":"Strategia: "+(p.isEmpty()?"nieustalona":p));
        JSONObject stages=e.optJSONObject("stages"),mp=stages==null?null:stages.optJSONObject("mp");
        parts.add(mp==null?"MP: nieustalone":mp.optBoolean("enabled")?"MP ON":"bez MP");
        parts.add(isF6(e)?"pole fotografii":"TV_BBOX_CROP".equals(e.optString("input_mode"))?"wycinek TV":"pełna klatka");
        for(String role:new String[]{"mt","mz"}){
            JSONObject stage=stages==null?null:stages.optJSONObject(role);
            if(stage==null||stage.has("unavailable_reason")){parts.add(role.toUpperCase(Locale.ROOT)+": ustawienia niepotwierdzone");continue;}
            String precision=stage.optString("precision"),runtime=stage.optString("runtime");
            parts.add(role.toUpperCase(Locale.ROOT)+" "+runtime+" "+precision.toUpperCase(Locale.ROOT)+" "+
                    (stage.optBoolean("gpu")?"GPU":"CPU ×"+stage.optInt("cpu_threads")));
        }
        return String.join(" · ",parts);
    }
    public static String compactDetails(JSONObject e){
        JSONObject stages=e.optJSONObject("stages"),mt=stages==null?null:stages.optJSONObject("mt"),mz=stages==null?null:stages.optJSONObject("mz");
        if(mt==null||mz==null||mt.has("unavailable_reason")||mz.has("unavailable_reason"))return details(e);
        for(String key:new String[]{"runtime","precision","cpu_threads","gpu"})if(!mt.optString(key).equals(mz.optString(key)))return details(e);
        String full=details(e);int split=full.indexOf(" · MT ");
        if(split<0)return full;
        return full.substring(0,split)+" · MT/MZ "+mt.optString("runtime")+" "+mt.optString("precision").toUpperCase(Locale.ROOT)+" · "+
                (mt.optBoolean("gpu")?"GPU":"CPU ×"+mt.optInt("cpu_threads"));
    }
    public static JSONObject planned(SharedPreferences prefs,ModelRegistry registry,AutoTuneManager tune) {
        JSONObject e=new JSONObject(),stages=new JSONObject();
        try{
            String policy=prefs.getString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"");
            e.put("experiment_type",prefs.getString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget"))
                    .put("roi_budget_policy",policy).put("input_mode",prefs.getString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME"));
            if(prefs.getBoolean(Final180PhotoRoi.ENABLED,false)&&"roi_budget".equals(e.optString("experiment_type"))&&"TV_BBOX_CROP".equals(e.optString("input_mode")))e.put("capture_profile",Final180PhotoRoi.PROFILE);
            if(policy.equals("r0_full_frame")||policy.equals("r1_one_roi")||policy.equals("r2_two_roi"))stages.put("mp",new JSONObject().put("enabled",!"r0_full_frame".equals(policy)));
            e.put("stages",stages);
            for(ModelRole role:new ModelRole[]{ModelRole.PLATE,ModelRole.CHARACTER}){
                String wire=role==ModelRole.PLATE?"mt":"mz",key=role==ModelRole.PLATE?SettingsActivity.KEY_RESEARCH_MT_EXECUTION:SettingsActivity.KEY_RESEARCH_MZ_EXECUTION;
                try{
                    InstalledModel model=registry.getActive(role);if(model==null)continue;
                    ModelVariant variant=ResearchVariantSelection.requirePinned(model,tune.storedVariantId(model));
                    if(Boolean.FALSE.equals(com.example.alpr_v1.inference.RuntimeBackendFactory.observedAvailability(variant.runtime())))
                        throw new IllegalStateException("RESEARCH_RUNTIME_UNAVAILABLE");
                    ResearchExecutionChoice choice=ResearchExecutionChoice.fromWireName(prefs.getString(key,"auto"));
                    com.example.alpr_v1.inference.ExecutionProfile profile=choice.resolveApproved(variant);
                    // UI reads the approved primitives; it does not resolve/hash model files or build a second producer.
                    stages.put(wire,new JSONObject().put("enabled",true).put("runtime",variant.runtime().wireName())
                            .put("precision",variant.precision()).put("cpu_threads",profile.cpuThreads).put("gpu",profile.gpu));
                }catch(Exception unavailable){stages.put(wire,new JSONObject().put("unavailable_reason",unavailable.toString()));}
            }
        }catch(Exception invalid){throw new IllegalStateException(invalid);}
        return e;
    }
}

