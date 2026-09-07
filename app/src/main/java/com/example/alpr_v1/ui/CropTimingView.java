package com.example.alpr_v1.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.alpr_v1.R;
import com.example.alpr_v1.pipeline.CropInferenceTiming;

import java.util.Locale;

/** Two rows, with all durations expressed in milliseconds. */
public final class CropTimingView extends LinearLayout {
    public CropTimingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.view_crop_timing, this);
        setTiming(null);
    }

    public void setTiming(@Nullable CropInferenceTiming timing) {
        setValue(R.id.crop_timing_mp, timing == null || timing.vehicleInferenceNanos < 0L
                ? null : timing.vehicleInferenceMilliseconds());
        setValue(R.id.crop_timing_mt, timing == null ? null : timing.plateInferenceMilliseconds());
        setValue(R.id.crop_timing_mz, timing == null ? null : timing.characterInferenceMilliseconds());
        setValue(R.id.crop_timing_pipeline, timing == null ? null : timing.totalMilliseconds());
    }

    private void setValue(int id, @Nullable Double milliseconds) {
        TextView value = findViewById(id);
        value.setText(milliseconds == null ? getContext().getString(R.string.result_placeholder)
                : String.format(Locale.getDefault(), "%.1f", milliseconds));
    }
}
