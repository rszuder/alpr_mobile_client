package com.example.alpr_v1.acquisition;

/** Immutable aggregate measures for one Phase 3B Scan run. */
public final class ScanAcquisitionStats {
    public final int vehiclesSeen;
    public final int vehiclesQueued;
    public final int vehiclesSelected;
    public final int vehiclesDeferred;
    public final int vehiclesLost;
    public final int entitiesReadyToFinalize;
    public final int acquisitionsFinalized;
    public final int uniquePlatesSaved;
    public final int duplicateAcquisitionsSuppressed;
    public final double duplicateCaptureRate;
    public final double meanAcquisitionMillis;
    public final double p95AcquisitionMillis;
    public final double uniquePlatesPerWallMinute;
    public final double meanQueueWaitMillis;
    public final double p95QueueWaitMillis;
    public final double meanActiveSessionMillis;
    public final double p95ActiveSessionMillis;
    public final double mtAttemptsPerEntity;
    public final double freshMzAttemptsPerEntity;

    public ScanAcquisitionStats(
            int vehiclesSeen,
            int vehiclesQueued,
            int vehiclesSelected,
            int vehiclesDeferred,
            int vehiclesLost,
            int entitiesReadyToFinalize,
            int acquisitionsFinalized,
            int uniquePlatesSaved,
            int duplicateAcquisitionsSuppressed,
            double duplicateCaptureRate,
            double meanAcquisitionMillis,
            double p95AcquisitionMillis,
            double uniquePlatesPerWallMinute,
            double meanQueueWaitMillis,
            double p95QueueWaitMillis,
            double meanActiveSessionMillis,
            double p95ActiveSessionMillis,
            double mtAttemptsPerEntity,
            double freshMzAttemptsPerEntity
    ) {
        this.vehiclesSeen = Math.max(0, vehiclesSeen);
        this.vehiclesQueued = Math.max(0, vehiclesQueued);
        this.vehiclesSelected = Math.max(0, vehiclesSelected);
        this.vehiclesDeferred = Math.max(0, vehiclesDeferred);
        this.vehiclesLost = Math.max(0, vehiclesLost);
        this.entitiesReadyToFinalize = Math.max(0, entitiesReadyToFinalize);
        this.acquisitionsFinalized = Math.max(0, acquisitionsFinalized);
        this.uniquePlatesSaved = Math.max(0, uniquePlatesSaved);
        this.duplicateAcquisitionsSuppressed = Math.max(
                0, duplicateAcquisitionsSuppressed
        );
        this.duplicateCaptureRate = finite(duplicateCaptureRate);
        this.meanAcquisitionMillis = finite(meanAcquisitionMillis);
        this.p95AcquisitionMillis = finite(p95AcquisitionMillis);
        this.uniquePlatesPerWallMinute = finite(uniquePlatesPerWallMinute);
        this.meanQueueWaitMillis = finite(meanQueueWaitMillis);
        this.p95QueueWaitMillis = finite(p95QueueWaitMillis);
        this.meanActiveSessionMillis = finite(meanActiveSessionMillis);
        this.p95ActiveSessionMillis = finite(p95ActiveSessionMillis);
        this.mtAttemptsPerEntity = finite(mtAttemptsPerEntity);
        this.freshMzAttemptsPerEntity = finite(freshMzAttemptsPerEntity);
    }

    public static ScanAcquisitionStats empty() {
        return new ScanAcquisitionStats(
                0, 0, 0, 0, 0, 0,
                0, 0, 0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        );
    }

    private static double finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, value);
    }
}
