package com.example.alpr_v1.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import java.util.List;

/** Small footer over the camera; backend metadata is supplied as an immutable snapshot. */
public final class LiveModelInfoView extends LinearLayout {
    public LiveModelInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_hud_pill);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding / 2, padding, padding / 2);
        LayoutInflater.from(context).inflate(R.layout.view_live_model_info, this, true);
    }

    public void render(List<ModelRuntimeSummary> models, String resolution) {
        for (String stage : new String[]{"MP", "MT", "MZ"}) {
            int id = stage.equals("MP") ? R.id.hud_mp_variant
                    : stage.equals("MT") ? R.id.hud_mt_variant : R.id.hud_mz_variant;
            ModelRuntimeSummary found = null;
            for (ModelRuntimeSummary model : models) if (stage.equals(model.stage)) found = model;
            TextView view = findViewById(id);
            String label = found == null ? stage + " · —" : found.compactLabel();
            if (!label.contentEquals(view.getText())) view.setText(label);
            view.setContentDescription(found == null ? label : found.description());
            view.setTooltipText(found == null ? null : found.description());
        }
        TextView size = findViewById(R.id.hud_source_resolution);
        if (!resolution.contentEquals(size.getText())) size.setText(resolution);
    }
}
