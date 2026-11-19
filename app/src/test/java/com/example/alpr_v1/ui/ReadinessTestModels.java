package com.example.alpr_v1.ui;

import android.content.Context;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.inference.RuntimeEvidenceFixture;
import com.example.alpr_v1.model.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Non-executable model bytes in Robolectric's private temporary application storage only. */
public final class ReadinessTestModels {
    private ReadinessTestModels() { }
    public static ModelRegistry install(Context context,boolean vehicle,boolean pin)throws Exception {
        File root=new File(context.getExternalFilesDir(null),"models");
        for(ModelRole role:ModelRole.values()) {
            if(role==ModelRole.VEHICLE&&!vehicle)continue;
            File directory=new File(root,role.wireName()+"/readiness-synthetic-"+role.wireName());
            InstalledModel original=RuntimeEvidenceFixture.model(directory,"fp32","FLOAT32",null);
            JSONObject json=new JSONObject(original.manifest().rawJson()).put("role",role.wireName())
                    .put("model_id","readiness-synthetic-"+role.wireName()).put("task",role==ModelRole.PLATE?"pose":"detect");
            json.put("output",new JSONObject().put("decoder","ultralytics_yolo_raw_v1").put("class_count",1)
                    .put("keypoint_count",role==ModelRole.PLATE?4:0).put("keypoint_dimensions",role==ModelRole.PLATE?3:0)
                    .put("tensor_layout","channels_first").put("confidence_threshold",.25).put("iou_threshold",.45));
            Files.write(new File(directory,"manifest.json").toPath(),json.toString().getBytes(StandardCharsets.UTF_8));
            context.getSharedPreferences("model_registry",0).edit().putString("active."+role.wireName(),role.wireName()+"/"+directory.getName()).commit();
        }
        ModelRegistry registry=new ModelRegistry(context);AutoTuneManager tune=new AutoTuneManager(context);
        for(ModelRole role:ModelRole.values())if(registry.getActive(role)!=null&&pin)tune.pinVariant(registry.getActive(role),"selected");
        context.getSharedPreferences(SettingsActivity.PREFERENCES,0).edit()
                .putString(SettingsActivity.KEY_RESEARCH_MP_EXECUTION,"cpu_2")
                .putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"cpu_2")
                .putString(SettingsActivity.KEY_RESEARCH_MZ_EXECUTION,"cpu_2").commit();
        return registry;
    }
}
