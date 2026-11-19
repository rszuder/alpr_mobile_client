package com.example.alpr_v1.ui.readiness;

import com.example.alpr_v1.R;
import com.example.alpr_v1.model.ModelRole;

/** UI destinations only. Resource IDs and actions are never written to research preferences. */
public final class ReadinessTarget {
    public enum Action { FIELD, MODEL, VARIANT, EXECUTION, TV_CORNERS, PHOTO_AREA, PREVIEW, FOCUS, PACKAGES }
    public final int sectionId, controlId;
    public final Action action;
    public final ModelRole role;
    public final String label;

    public ReadinessTarget(int section, int control, Action action, ModelRole role, String label) {
        sectionId=section;controlId=control;this.action=action;this.role=role;this.label=label;
    }
    public static ReadinessTarget field(int section,int control,String label) {
        return new ReadinessTarget(section,control,Action.FIELD,null,label);
    }
    public static ReadinessTarget model(ModelRole role,boolean variant) {
        int id=role==ModelRole.VEHICLE?R.id.settings_node_vehicle:role==ModelRole.PLATE?R.id.settings_node_plate:R.id.settings_node_character;
        return new ReadinessTarget(2,id,variant?Action.VARIANT:Action.MODEL,role,variant?"Wybierz wariant":"Wybierz model");
    }
    public static ReadinessTarget execution(ModelRole role) {
        int id=role==ModelRole.VEHICLE?R.id.settings_research_mp_execution:role==ModelRole.PLATE?R.id.settings_research_mt_execution:R.id.settings_research_mz_execution;
        return new ReadinessTarget(2,id,Action.EXECUTION,role,"Wybierz wykonanie");
    }
    public static ReadinessTarget tv() { return new ReadinessTarget(1,R.id.settings_tv_calibrate,Action.TV_CORNERS,null,"Ustaw narożniki TV"); }
    public static ReadinessTarget photo() { return new ReadinessTarget(1,R.id.settings_f6_photo_roi,Action.PHOTO_AREA,null,"Ustaw obszar fotografii"); }
    public static ReadinessTarget preview() { return new ReadinessTarget(1,0,Action.PREVIEW,null,"Przejdź do podglądu"); }
}
