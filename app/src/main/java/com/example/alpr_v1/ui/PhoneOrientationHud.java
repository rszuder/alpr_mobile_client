package com.example.alpr_v1.ui;

import android.view.View;
import com.example.alpr_v1.camera.PhoneOrientationEstimator;

/** Orientation feedback lives in the preview overlay, outside the control bars. */
public final class PhoneOrientationHud {
    private final PhoneLevelView level;

    public PhoneOrientationHud(View panel) {
        level = (PhoneLevelView) panel;
    }

    public void render(PhoneOrientationEstimator.Snapshot orientation) {
        level.setVisibility(View.VISIBLE);
        level.render(orientation);
    }

    public void hide() { level.setVisibility(View.GONE); }
}
