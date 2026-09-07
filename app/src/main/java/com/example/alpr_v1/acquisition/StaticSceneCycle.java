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
    private final Map<Long, Long> owners = new HashMap<>();
    private final Set<Long> attempted = new HashSet<>();
    private final List<NormalizedBounds> vehicleBounds = new ArrayList<>(), plateBounds = new ArrayList<>();
    private long zoomEntity;
    private long zoomTrackId;
    private List<VehicleCandidate> baselineVehicles = Collections.emptyList();
    public synchronized List<VehicleCandidate> baselineVehicles() { return baselineVehicles; }
    public synchronized Phase phase() { return phase; }
    public synchronized long zoomEntity() { return zoomEntity; }
    public synchronized boolean refining() { return zoomTrackId > 0L; }
    public synchronized void reset(long scene) {
        this.scene = scene; phase = Phase.BASELINE; zoomEntity = 0L; zoomTrackId = 0L;
        candidates.clear(); owners.clear(); attempted.clear(); vehicleBounds.clear(); plateBounds.clear();
        baselineVehicles = Collections.emptyList();
    }
    public synchronized void observeVehicles(VehicleTrackingFrame frame) {
        if (phase != Phase.BASELINE || frame == null || frame.sceneGeneration != scene) return;
        if (frame.sourceFrameId <= 0L) return;
        baselineVehicles = frame.measuredOnly().candidates;
        vehicleBounds.clear();
        Set<Long> present = new HashSet<>();
        for (VehicleCandidate candidate : baselineVehicles) {
            vehicleBounds.add(candidate.bounds); present.add(candidate.entityId);
        }
        candidates.keySet().removeIf(track -> owners.getOrDefault(track, 0L) > 0L
                && !present.contains(owners.get(track)));
    }
    public synchronized void observe(PlateObservation observation) {
        if (phase != Phase.BASELINE || observation.sceneGeneration != scene) return;
        PlateGeometry g = observation.geometry;
        if (g == null || g.sourceWidthPx <= 0 || g.sourceHeightPx <= 0) return;
        NormalizedBounds b = new NormalizedBounds(g.bboxLeftPx/g.sourceWidthPx, g.bboxTopPx/g.sourceHeightPx,
                g.bboxRightPx/g.sourceWidthPx, g.bboxBottomPx/g.sourceHeightPx);
        if (!b.valid()) return;
        plateBounds.add(b);
        if (observation.plateTrackId <= 0L) return;
        AutoZoomController.Sample sample = new AutoZoomController.Sample(observation.plateTrackId,
                b.centerX(), b.centerY(), b.width(), observation.recognitionConfidence, observation.confirmed,
                observation.observations, g.cornersNorm.size() == 4 && g.quadAreaRatio > 0,
                observation.freshMzAttempted, observation.freshMzSuccessful,
                observation.freshPrediction, true);
        owners.put(observation.plateTrackId, observation.entityId);
        AutoZoomController.Sample old = candidates.get(observation.plateTrackId);
        if (old == null || sample.recognitionConfidence > old.recognitionConfidence)
            candidates.put(observation.plateTrackId, sample);
    }
    public synchronized void finishBaseline() { if (phase == Phase.BASELINE) phase = Phase.AZ_REFINEMENT; }
    public synchronized AutoZoomController.Sample nextRefinement(boolean enabled) {
        if (phase != Phase.AZ_REFINEMENT || refining()) return null;
        if (enabled) for (Map.Entry<Long, AutoZoomController.Sample> entry : candidates.entrySet()) {
            AutoZoomController.Sample s = entry.getValue();
            if (attempted.contains(entry.getKey())) continue;
            attempted.add(entry.getKey()); zoomTrackId = entry.getKey();
            zoomEntity = owners.getOrDefault(zoomTrackId, 0L); return s;
        }
        phase = Phase.STATIC_IDLE;
        return null;
    }
    public synchronized void finishZoom() { zoomEntity = 0L; zoomTrackId = 0L; }
    public synchronized void enableRefinement() { if (phase == Phase.STATIC_IDLE) phase = Phase.AZ_REFINEMENT; }
    public synchronized StaticSceneWatchRegions watchRegions() {
        return new StaticSceneWatchRegions(vehicleBounds, plateBounds, StaticSceneWatcher.DEFAULT.margin);
    }
    /** Dynamic lock keeps its quality policy; STATIC queues every detected plate. */
    public static boolean needsRefinement(AutoZoomController.Sample s) {
        return s.validQuad && s.recognitionExecuted && (!s.recognitionSuccessful || !s.confirmed
                || s.recognitionConfidence < 0.85 || s.normalizedWidth < 0.12f);
    }
}
