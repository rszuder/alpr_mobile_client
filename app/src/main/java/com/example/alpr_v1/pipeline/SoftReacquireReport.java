package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.VehicleContinuityEvidence;
import com.example.alpr_v1.continuity.VehicleContinuityEvaluator;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import java.util.Set;

/** One-shot engine report produced by the fresh MP/MT recovery path. */
final class SoftReacquireReport {
    final boolean attempted;
    final boolean targetRecovered;
    final boolean activeTargetLost;
    final VehicleContinuityEvidence vehicles;

    SoftReacquireReport(
            boolean attempted,
            boolean targetRecovered,
            boolean activeTargetLost,
            VehicleContinuityEvidence vehicles
    ) {
        this.attempted = attempted;
        this.targetRecovered = targetRecovered;
        this.activeTargetLost = activeTargetLost;
        this.vehicles = vehicles == null
                ? VehicleContinuityEvidence.empty() : vehicles;
    }

    static SoftReacquireReport none() {
        return new SoftReacquireReport(
                false, false, false, VehicleContinuityEvidence.empty()
        );
    }

    static SoftReacquireReport fromFreshMp(
            Set<Long> entitiesBefore,
            long activeEntityId,
            VehicleTrackingFrame frame,
            long recoveryStartedNanos,
            long nowNanos
    ) {
        int reassociated = 0;
        int predicted = 0;
        int newlyCreated = 0;
        float appearanceAgreement = 0f;
        float trajectoryAgreement = 0f;
        long newestAgeNanos = Long.MAX_VALUE;
        boolean activeRecovered = false;
        for (VehicleCandidate candidate : frame.candidates) {
            if (entitiesBefore.contains(candidate.entityId)) reassociated++;
            else newlyCreated++;
            if (candidate.predicted) predicted++;
            appearanceAgreement += candidate.effectiveConfidence;
            trajectoryAgreement += 1f - candidate.exitUrgency;
            newestAgeNanos = Math.min(newestAgeNanos, candidate.predictionAgeNanos);
            if (candidate.entityId == activeEntityId && !candidate.predicted) {
                activeRecovered = true;
            }
        }
        int before = entitiesBefore.size();
        int after = frame.candidates.size();
        float ratio = before == 0 ? 0f : reassociated / (float) before;
        VehicleContinuityEvidence vehicles = new VehicleContinuityEvidence(
                before,
                after,
                reassociated,
                predicted,
                newlyCreated,
                ratio,
                after == 0 ? 0f : appearanceAgreement / after,
                after == 0 ? 0f : trajectoryAgreement / after,
                newestAgeNanos == Long.MAX_VALUE
                        ? Math.max(0L, nowNanos - recoveryStartedNanos)
                        : newestAgeNanos
        );
        float vehicleScore = new VehicleContinuityEvaluator().evaluate(vehicles);
        boolean activeLost = activeEntityId > 0L
                && !activeRecovered
                && vehicleScore >= 0.50f;
        return new SoftReacquireReport(
                true,
                false,
                activeLost,
                vehicles
        );
    }
}
