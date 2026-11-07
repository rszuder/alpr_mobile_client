package com.example.alpr_v1.ui;

/** TV tools are also ordinary preparation tools; other research protocols keep their gates. */
public final class TvSetupAvailability {
    private TvSetupAvailability() { }
    public static boolean available(boolean experimentEnabled,String experimentType){
        return !experimentEnabled||"roi_budget".equals(experimentType);
    }
}
