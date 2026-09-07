package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.continuity.StaticSceneWatchRegions;
import com.example.alpr_v1.continuity.StaticSceneWatcher;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import java.util.*;

/** Per-scene phases, independent of technical zoom state and track numbering. */
public final class StaticSceneCycle {
    public enum Phase { BASELINE, AZ_REFINEMENT, STATIC_IDLE }
    private long scene = -1L;
    private Phase phase = Phase.BASELINE;
    private final Map<Long, AutoZoomController.Sample> candidates = new LinkedHashMap<>();
    private final Set<Long> attempted = new HashSet<>();
    private final List<NormalizedBounds> vehicleBounds = new ArrayList<>(), plateBounds = new ArrayList<>();
    private long zoomEntity;
    private List<VehicleCandidate> baselineVehicles = Collections.emptyList();
    public synchronized List<VehicleCandidate> baselineVehicles() { return baselineVehicles; }
    public synchronized Phase phase() { return phase; }
    public synchronized long zoomEntity() { return zoomEntity; }
    public synchronized void reset(long scene) {
        this.scene = scene; phase = Phase.BASELINE; zoomEntity = 0L;
        candidates.clear(); attempted.clear(); vehicleBounds.clear(); plateBounds.clear();
        baselineVehicles = Collections.emptyList();
    }
    public synchronized void observeVehicles(VehicleTrackingFrame frame) {
        if (phase != Phase.BASELINE || frame == null || frame.sceneGeneration != scene) return;
        if (frame.candidates.isEmpty()) return;
        List<NormalizedBounds> measured = new ArrayList<>();
        for (VehicleCandidate candidate : frame.candidates) if (!candidate.predicted) measured.add(candidate.bounds);
        if (!measured.isEmpty()) {
            vehicleBounds.clear(); vehicleBounds.addAll(measured);
            baselineVehicles = Collections.unmodifiableList(new ArrayList<>(frame.candidates));
        }
    }
    public synchronized void observe(PlateObservation observation) {
        if (phase != Phase.BASELINE || observation.sceneGeneration != scene || !observation.freshMzAttempted) return;
        PlateGeometry g = observation.geometry;
        if (g == null || g.sourceWidthPx <= 0 || g.sourceHeightPx <= 0) return;
        NormalizedBounds b = new NormalizedBounds(g.bboxLeftPx/g.sourceWidthPx, g.bboxTopPx/g.sourceHeightPx,
                g.bboxRightPx/g.sourceWidthPx, g.bboxBottomPx/g.sourceHeightPx);
        if (!b.valid()) return;
        plateBounds.add(b);
        if (observation.entityId <= 0L || g.cornersNorm.size() != 4 || g.quadAreaRatio <= 0) return;
        AutoZoomController.Sample sample = new AutoZoomController.Sample(observation.plateTrackId,
                b.centerX(), b.centerY(), b.width(), observation.recognitionConfidence, observation.confirmed,
                observation.observations, true, true, observation.freshMzSuccessful,
                observation.freshPrediction, true);
        AutoZoomController.Sample old = candidates.get(observation.entityId);
        if (old == null || sample.recognitionConfidence > old.recognitionConfidence) candidates.put(observation.entityId, sample);
    }
    public synchronized void finishBaseline() { if (phase == Phase.BASELINE) phase = Phase.AZ_REFINEMENT; }
    public synchronized AutoZoomController.Sample nextRefinement(boolean enabled) {
        if (phase != Phase.AZ_REFINEMENT || zoomEntity > 0L) return null;
        if (enabled) for (Map.Entry<Long, AutoZoomController.Sample> entry : candidates.entrySet()) {
            AutoZoomController.Sample s = entry.getValue();
            if (attempted.contains(entry.getKey()) || !needsRefinement(s)) continue;
            attempted.add(entry.getKey()); zoomEntity = entry.getKey(); return s;
        }
        phase = Phase.STATIC_IDLE;
        return null;
    }
    public synchronized void finishZoom() { zoomEntity = 0L; }
    public synchronized void enableRefinement() { if (phase == Phase.STATIC_IDLE) phase = Phase.AZ_REFINEMENT; }
    public synchronized StaticSceneWatchRegions watchRegions() {
        return new StaticSceneWatchRegions(vehicleBounds, plateBounds, StaticSceneWatcher.DEFAULT.margin);
    }
    public static boolean needsRefinement(AutoZoomController.Sample s) {
        return s.validQuad && s.recognitionExecuted && (!s.recognitionSuccessful || !s.confirmed
                || s.recognitionConfidence < 0.85 || s.normalizedWidth < 0.12f);
    }
}
