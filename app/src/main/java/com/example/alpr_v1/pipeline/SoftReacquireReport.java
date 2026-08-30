package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.SoftReacquireResult;
import com.example.alpr_v1.continuity.VehicleContinuityEvidence;
import com.example.alpr_v1.continuity.VehicleContinuityEvaluator;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import java.util.Set;

/** One-shot immutable terminal report produced by the fresh MP/MT recovery path. */
final class SoftReacquireReport {
    final boolean attempted;
    final SoftReacquireResult result;
    final VehicleContinuityEvidence vehicles;
    final String reason;

    private SoftReacquireReport(
            boolean attempted,
            SoftReacquireResult result,
            VehicleContinuityEvidence vehicles,
            String reason
    ) {
        if (attempted != (result != null)) {
            throw new IllegalArgumentException(
                    "attempted must describe exactly one terminal result"
            );
        }
        this.attempted = attempted;
        this.result = result;
        this.vehicles = vehicles == null
                ? VehicleContinuityEvidence.empty() : vehicles;
        this.reason = reason == null ? "" : reason;
    }

    static SoftReacquireReport none() {
        return new SoftReacquireReport(
                false, null, VehicleContinuityEvidence.empty(), ""
        );
    }

    static SoftReacquireReport pending(
            VehicleContinuityEvidence vehicles,
            String reason
    ) {
        return new SoftReacquireReport(false, null, vehicles, reason);
    }

    static SoftReacquireReport terminal(
            SoftReacquireResult result,
            VehicleContinuityEvidence vehicles,
            String reason
    ) {
        if (result == null) throw new IllegalArgumentException("result");
        return new SoftReacquireReport(true, result, vehicles, reason);
    }

    static SoftReacquireReport fromFreshMp(
            Set<Long> entitiesBefore,
            long activeEntityId,
            VehicleTrackingFrame frame,
            long triggerSourceTimestampNanos
    ) {
        return fromFreshMp(
                entitiesBefore,
                activeEntityId,
                frame,
                0L,
                triggerSourceTimestampNanos
        );
    }

    static SoftReacquireReport fromFreshMp(
            Set<Long> entitiesBefore,
            long activeEntityId,
            VehicleTrackingFrame frame,
            long triggerSourceSequence,
            long triggerSourceTimestampNanos
    ) {
        if (triggerSourceSequence > 0L
                && frame.sourceSequence <= triggerSourceSequence) {
            return pending(
                    VehicleContinuityEvidence.empty(),
                    "mp_source_sequence_not_after_recovery"
            );
        }
        if (triggerSourceTimestampNanos > 0L
                && triggerSourceSequence <= 0L
                && frame.sourceTimestampNanos <= triggerSourceTimestampNanos) {
            return pending(
                    VehicleContinuityEvidence.empty(),
                    "mp_source_frame_predates_recovery"
            );
        }
        int reassociated = 0;
        int predicted = 0;
        int newlyCreated = 0;
        int freshMeasuredEntities = 0;
        long newestAgeNanos = Long.MAX_VALUE;
        boolean activeRecovered = false;
        for (VehicleCandidate candidate : frame.candidates) {
            boolean existedBefore = entitiesBefore.contains(candidate.entityId);
            boolean freshMeasured = existedBefore
                    && !candidate.predicted
                    && (triggerSourceSequence > 0L
                    || candidate.lastMeasurementTimestampNanos
                    > triggerSourceTimestampNanos);
            boolean anyFreshMeasurement = !candidate.predicted
                    && (triggerSourceSequence > 0L
                    || candidate.lastMeasurementTimestampNanos
                    > triggerSourceTimestampNanos);
            if (anyFreshMeasurement) freshMeasuredEntities++;
            if (freshMeasured) {
                reassociated++;
                newestAgeNanos = Math.min(
                        newestAgeNanos,
                        Math.max(
                                0L,
                                frame.snapshotTimestampNanos
                                        - candidate.lastMeasurementTimestampNanos
                        )
                );
            } else if (existedBefore && candidate.predicted) {
                predicted++;
            } else if (!existedBefore) {
                newlyCreated++;
            }
            if (candidate.entityId == activeEntityId && freshMeasured) {
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
                0f,
                0f,
                newestAgeNanos == Long.MAX_VALUE
                        ? (triggerSourceTimestampNanos <= 0L
                        ? 0L
                        : Math.max(
                                0L,
                                frame.snapshotTimestampNanos
                                        - triggerSourceTimestampNanos
                        ))
                        : newestAgeNanos,
                false,
                false,
                freshMeasuredEntities
        );
        float vehicleScore = new VehicleContinuityEvaluator().evaluate(vehicles);
        boolean poolRecovered = before > 0 && reassociated > 0 && vehicleScore >= 0.50f;

        if (activeEntityId <= 0L) {
            return terminal(
                    poolRecovered
                            ? SoftReacquireResult.VEHICLE_POOL_RECOVERED
                            : SoftReacquireResult.FAILED,
                    vehicles,
                    poolRecovered
                            ? "fresh_mp_recovered_vehicle_pool"
                            : "fresh_mp_did_not_recover_vehicle_pool"
            );
        }
        if (activeRecovered) {
            return pending(vehicles, "fresh_mp_recovered_active_vehicle_waiting_for_mt");
        }
        if (poolRecovered) {
            return terminal(
                    SoftReacquireResult.ACTIVE_TARGET_LOST,
                    vehicles,
                    "active_target_lost_vehicle_pool_recovered"
            );
        }
        return terminal(
                SoftReacquireResult.FAILED,
                vehicles,
                "fresh_mp_recovered_neither_target_nor_pool"
        );
    }
}
