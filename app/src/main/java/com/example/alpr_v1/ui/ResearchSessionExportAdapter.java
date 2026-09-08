package com.example.alpr_v1.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.ResearchSessionSummary;
import org.json.JSONObject;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ResearchSessionExportAdapter extends BaseAdapter {
    private final List<ResearchSessionSummary> items;
    public ResearchSessionExportAdapter(List<ResearchSessionSummary> items) { this.items = new ArrayList<>(items); }
    @Override public int getCount() { return items.size(); }
    @Override public ResearchSessionSummary getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        View row = recycled == null ? LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_export, parent, false) : recycled;
        ResearchSessionSummary item = getItem(position);
        text(row,R.id.session_export_date,item.dateText(ZoneId.systemDefault()));
        text(row,R.id.session_export_duration,item.durationText());
        text(row,R.id.session_export_title,type(item.value("experiment_type")));
        text(row,R.id.session_export_configuration,configuration(item));
        text(row,R.id.session_export_execution,execution(item.execution));
        String series = item.session.optString("series_id", "");
        String scenario = item.session.optString("scenario_id", "");
        List<String> identity = new ArrayList<>();
        if (!series.isEmpty()) identity.add("Seria: " + series);
        if (!scenario.isEmpty()) identity.add("Scenariusz: " + scenario);
        if (item.session.has("replicate_index")) identity.add("Powtórzenie " + item.session.optInt("replicate_index"));
        if (identity.isEmpty()) {
            String name = item.archive.getName().replace(".alprsession", "");
            identity.add("Sesja #" + name.substring(Math.max(0,name.length()-8)));
        }
        text(row,R.id.session_export_series,String.join(" · ",identity));
        TextView status = row.findViewById(R.id.session_export_status);
        status.setText(!item.metadataAvailable ? R.string.session_export_no_metadata
                : item.complete ? R.string.session_export_complete : R.string.session_export_partial);
        status.setTextColor(ContextCompat.getColor(row.getContext(),item.complete ? R.color.alpr_success : R.color.alpr_warning));
        return row;
    }

    private static void text(View row,int id,String value) { ((TextView)row.findViewById(id)).setText(value); }

    private static String type(String value) {
        switch(value) {
            case "roi_budget": return "Budżet ROI";
            case "runtime": return "Runtime i kwantyzacja";
            case "consensus": return "Konsensus odczytu";
            case "continuity": return "Ciągłość sceny i odzyskiwanie";
            case "end_to_end": return "Pełny przebieg ALPR";
            default: return value.isEmpty() ? "Konfiguracja nieznana" : readable(value);
        }
    }

    private static String roi(String value) {
        switch(value) {
            case "r0_full_frame": return "R0 · pełna klatka";
            case "r1_one_roi": return "R1 · 1 obszar ROI";
            case "r2_two_roi": return "R2 · 2 obszary ROI";
            default: return readable(value);
        }
    }

    private static String configuration(ResearchSessionSummary item) {
        List<String> first = new ArrayList<>();
        String variant = item.value("variant"), policy = item.value("roi_budget_policy");
        if (!variant.isEmpty()) first.add(roi(variant));
        if (!policy.isEmpty() && !policy.equals(variant)) first.add(roi(policy));
        String mode = item.value("analysis_mode");
        if (mode.isEmpty()) mode = item.value("scene_handling_mode");
        if (!mode.isEmpty()) first.add("static".equals(mode) || "strict_scene_boundary".equals(mode) ? "Statyczny"
                : "dynamic".equals(mode) || "dynamic_continuity".equals(mode) ? "Dynamiczny" : readable(mode));
        String camera = item.value("camera_requested_resolution");
        String profile = item.value("recognition_profile");
        if ("balanced".equals(profile)) profile = "zrównoważony";
        else if ("fast".equals(profile)) profile = "szybki";
        else if ("accurate".equals(profile)) profile = "dokładny";
        List<String> second = new ArrayList<>();
        if (!camera.isEmpty()) second.add("Kamera " + ("auto".equals(camera) ? "AUTO" : camera.replace("x","×")));
        if (!profile.isEmpty()) second.add("Profil " + readable(profile));
        return String.join(" · ",first) + (second.isEmpty() ? "" : "\n" + String.join(" · ",second));
    }

    private static String execution(JSONObject execution) {
        List<String> lines = new ArrayList<>();
        JSONObject stages = execution.optJSONObject("stages");
        if (stages != null) for (String key : new String[]{"mp","mt","mz"}) {
            JSONObject stage = stages.optJSONObject(key);
            if (stage == null) continue;
            String label = key.toUpperCase(Locale.ROOT);
            if (!stage.optBoolean("enabled",false)) { lines.add(label + " · wyłączony"); continue; }
            String runtime = stage.optString("runtime","");
            runtime = "tflite".equals(runtime) ? "LiteRT" : "onnx".equals(runtime) ? "ONNX" : readable(runtime);
            List<String> values = new ArrayList<>(); values.add(label);
            values.add((runtime + " " + stage.optString("precision","").toUpperCase(Locale.ROOT)).trim());
            if (stage.optBoolean("gpu",false)) values.add("GPU");
            else if (stage.has("cpu_threads")) values.add("CPU ×" + stage.optInt("cpu_threads"));
            JSONObject input = stage.optJSONObject("input");
            if (input != null && input.optInt("width") > 0 && input.optInt("height") > 0)
                values.add(input.optInt("width") + "×" + input.optInt("height"));
            lines.add(String.join(" · ",values));
        }
        JSONObject flags = execution.optJSONObject("feature_flags");
        if (flags != null) {
            lines.add("AZ " + flag(flags,"autozoom") + " · Lock " + flag(flags,"lock"));
            lines.add("Tracking MP " + flag(flags,"vehicle_tracking") + " / MT " + flag(flags,"plate_tracking")
                    + " · Konsensus " + flag(flags,"temporal_mz") + " · Bramka " + flag(flags,"adaptive_frame_gate"));
        }
        return lines.isEmpty() ? "Brak zapisanej konfiguracji wykonania" : String.join("\n",lines);
    }
    private static String flag(JSONObject flags,String key) { return !flags.has(key) ? "?" : flags.optBoolean(key) ? "tak" : "nie"; }
    private static String readable(String value) { return value.replace('_',' ').trim(); }
}
