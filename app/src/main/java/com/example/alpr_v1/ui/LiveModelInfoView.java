package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import java.util.List;

/** Fixed information slot below the camera; metadata is supplied as an immutable snapshot. */
public final class LiveModelInfoView extends LinearLayout {
    public LiveModelInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setPadding(0,0,0,0);
        LayoutInflater.from(context).inflate(R.layout.view_live_model_info, this, true);
        setBackgroundResource(R.drawable.bg_cockpit_configuration);
    }

    private static String compact(ModelRuntimeSummary model) {
        // Actual opened backend and declared precision; full model/variant/kernel status remains accessible.
        return model.stage + " " + model.backend.replace("ONNX Runtime", "ONNX").replace("/", "·") + "·" + model.precision;
    }
    public void render(List<ModelRuntimeSummary> models, String resolution) {
        for (String stage : new String[]{"MP", "MT", "MZ"}) {
            int id = stage.equals("MP") ? R.id.hud_mp_variant
                    : stage.equals("MT") ? R.id.hud_mt_variant : R.id.hud_mz_variant;
            ModelRuntimeSummary found = null;
            for (ModelRuntimeSummary model : models) if (stage.equals(model.stage)) found = model;
            TextView view = findViewById(id);
            String label = found == null ? stage + " · —" : compact(found);
            instrumentText(view,label,stage.length());
            view.setContentDescription(found == null ? label : found.description());
            view.setTooltipText(found == null ? null : found.description());
        }
        TextView size = findViewById(R.id.hud_source_resolution);
        instrumentText(size,resolution,Math.max(0,resolution.indexOf(':')));
        size.setContentDescription(resolution);size.setTooltipText(resolution);
    }
    private void instrumentText(TextView field,String value,int captionLength){
        if(value.contentEquals(field.getText())&&field.getText() instanceof Spanned)return;
        SpannableString styled=new SpannableString(value);
        if(captionLength>0){
            styled.setSpan(new ForegroundColorSpan(getContext().getColor(R.color.cockpit_cyan)),0,captionLength,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new StyleSpan(Typeface.BOLD),0,captionLength,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        field.setText(styled);
    }
}
