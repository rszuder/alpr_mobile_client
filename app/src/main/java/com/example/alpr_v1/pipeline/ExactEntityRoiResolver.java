package com.example.alpr_v1.pipeline;

import java.util.List;

/** Resolves a Scan directive by durable entity identity, never by list position. */
public final class ExactEntityRoiResolver {
    private ExactEntityRoiResolver() {}

    public static VehicleRoi findByEntityId(
            List<VehicleRoi> rois,
            long entityId
    ) {
        if (rois == null || entityId <= 0L) return null;
        for (VehicleRoi roi : rois) {
            if (roi != null && roi.entityId == entityId) return roi;
        }
        return null;
    }

    public static VehicleRoi buildFromTrackedEntity(
            List<com.example.alpr_v1.tracking.VehicleCandidate> candidates,
            int imageWidth,
            int imageHeight,
            long entityId,
            float marginFraction
    ) {
        if (candidates == null || entityId <= 0L) return null;
        for (com.example.alpr_v1.tracking.VehicleCandidate candidate : candidates) {
            if (candidate == null || candidate.entityId != entityId) continue;
            List<VehicleRoi> exact = VehicleRoiSelector.selectTrackedCandidates(
                    java.util.Collections.singletonList(candidate),
                    imageWidth,
                    imageHeight,
                    1,
                    marginFraction
            );
            return exact.isEmpty() ? null : exact.get(0);
        }
        return null;
    }
}
