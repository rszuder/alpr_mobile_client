package com.example.alpr_v1.experiment;

import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;

/** Existing admission predicates, shared with the read-only UI. No state or preparation. */
public final class ConfigurationRequirements {
    private ConfigurationRequirements() { }

    public static void requireVehicleStage(RoiBudgetPolicy policy, boolean enabled) {
        if (policy.usesVehicleCascade() && !enabled)
            throw new IllegalArgumentException("R1/R2 wymagają aktywnego etapu MP");
    }
    public static void requireAlprStages(boolean plate, boolean character) {
        if (!plate || !character)
            throw new IllegalArgumentException("Research Mode wymaga etapów MT i MZ");
    }
    public static void requireAutoZoomLock(boolean autoZoom, boolean lock, SceneHandlingMode scene) {
        if (autoZoom && !lock && scene == SceneHandlingMode.DYNAMIC_CONTINUITY)
            throw new IllegalArgumentException("Autozoom wymaga włączonego locka");
    }
    public static void requirePhotoInput(boolean tvCrop, RoiBudgetPolicy policy, boolean vehicle) {
        if (!tvCrop || policy != RoiBudgetPolicy.FULL_FRAME || vehicle)
            throw new IllegalStateException("F6_REQUIRES_R0_TV_BBOX_WITHOUT_MP");
    }
    public static void requireTvCategory(String type) {
        if (!"roi_budget".equals(type)) throw new IllegalStateException("TV_BBOX_F5A_ONLY");
    }
    public static void requireTvFixedZoom(boolean autoZoom) {
        if (autoZoom) throw new IllegalStateException("TV_BBOX_REQUIRES_FIXED_ZOOM");
    }
    public static boolean focusBlocksStart(boolean profile, boolean requested, boolean held) {
        return profile && requested && !held;
    }
    public static void requirePackageHash(String observed, String expected) {
        if (!observed.equals(expected)) throw new IllegalArgumentException("RESEARCH_PACKAGE_SHA_CHANGED");
    }
}
