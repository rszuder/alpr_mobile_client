package com.example.alpr_v1.camera;

/** Wybór celu AUTO z minimalną jakością źródła wymaganą przez Scan ALPR. */
public final class AnalysisResolutionPolicy {
    private AnalysisResolutionPolicy() {}

    public static int autoWidth(boolean constrainedDevice, boolean scanActive) {
        if (scanActive) return 1280;
        return constrainedDevice ? 640 : 1280;
    }

    public static int autoHeight(boolean constrainedDevice, boolean scanActive) {
        if (scanActive) return 960;
        return constrainedDevice ? 480 : 720;
    }
}
