package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.domain.TargetPurpose;

public final class DynamicZoomPolicy {
    private DynamicZoomPolicy() { }
    public static boolean allows(TargetPurpose purpose, AutoZoomController.Sample sample,
            boolean moving, SceneContinuityState continuity) {
        return purpose != null && purpose != TargetPurpose.SCAN_ACQUISITION
                && sample != null && sample.stableTargetGeometry
                && !moving && continuity == SceneContinuityState.STABLE
                && sample.centerX >= .15f && sample.centerX <= .85f
                && sample.centerY >= .15f && sample.centerY <= .85f
                && StaticSceneCycle.needsRefinement(sample);
    }
    public static boolean shouldAbort(boolean rapidMotion, boolean targetLost, SceneContinuityState continuity) {
        return rapidMotion || targetLost || continuity == SceneContinuityState.REACQUIRING
                || continuity == SceneContinuityState.HARD_RESETTING;
    }
}
