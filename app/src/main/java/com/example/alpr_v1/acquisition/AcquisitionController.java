package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.domain.ApplicationMode;
import com.example.alpr_v1.domain.AppearanceDescriptor;
import com.example.alpr_v1.domain.CropReference;
import com.example.alpr_v1.domain.EntityAcquisitionState;
import com.example.alpr_v1.domain.ModeController;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import com.example.alpr_v1.domain.TargetPurpose;
import com.example.alpr_v1.domain.TargetSession;
import com.example.alpr_v1.domain.TargetSessionState;
import com.example.alpr_v1.domain.VehicleEntity;
import com.example.alpr_v1.domain.VehicleEntityRepository;
import com.example.alpr_v1.tracking.VehicleTrackManager;

import java.util.List;

/** Coordinates one short, non-persistent Scan acquisition at a time. */
public final class AcquisitionController {
    public enum Outcome { ACQUIRED, REQUEUED, FAILED, IGNORED }

    private static final int MAX_MT_ATTEMPTS = 3;
    private final VehicleEntityRepository repository;
    private final AcquisitionQueue queue;
    private final AcquisitionDeduplicator deduplicator;
    private final BestCropSelector cropSelector;
    private final ModeController modeController =
            new ModeController(ApplicationMode.SCAN_ACQUIRE);
    private AcquisitionCandidate activeCandidate;
    private TargetSession activeSession;

    public AcquisitionController(VehicleEntityRepository repository) {
        this(repository, new AcquisitionQueue(), new AcquisitionDeduplicator(),
                new BestCropSelector());
    }

    public AcquisitionController(
            VehicleEntityRepository repository,
            AcquisitionQueue queue,
            AcquisitionDeduplicator deduplicator,
            BestCropSelector cropSelector
    ) {
        if (repository == null || queue == null || deduplicator == null
                || cropSelector == null) throw new IllegalArgumentException("dependencies");
        this.repository = repository;
        this.queue = queue;
        this.deduplicator = deduplicator;
        this.cropSelector = cropSelector;
    }

    public synchronized void refresh(
            List<VehicleTrackManager.Snapshot> snapshots,
            long nowNanos
    ) {
        if (snapshots == null) return;
        List<VehicleEntity> scene = repository.activeEntities();
        for (VehicleTrackManager.Snapshot snapshot : snapshots) {
            VehicleEntity entity = repository.get(snapshot.entityId);
            if (entity == null || entity.acquisitionCompleted() || entity.activeTarget()
                    || entity.acquisitionState() == EntityAcquisitionState.EXPIRED) continue;
            AcquisitionDeduplicator.Result duplicate = deduplicator.evaluate(entity, scene);
            if (duplicate.duplicate) continue;
            float areaReadability = Math.min(1f, snapshot.bounds.area() / 0.18f);
            float readability = snapshot.confidence
                    * (float) Math.sqrt(Math.max(0f, areaReadability));
            float recentPenalty = Math.min(0.75f, entity.mtAttempts() * 0.20f);
            queue.offer(new AcquisitionQueue.Offer(
                    entity.entityId(),
                    entity.mtAttempts() == 0 ? 1f : 0.35f,
                    readability,
                    snapshot.exitUrgency,
                    0f,
                    recentPenalty,
                    duplicate.penalty,
                    0f // Phase 3 Scan does not request autozoom.
            ), nowNanos);
            repository.markQueued(entity.entityId());
        }
    }

    public synchronized TargetSession startNext(long nowNanos) {
        if (activeSession != null) return activeSession;
        activeCandidate = queue.poll(nowNanos);
        if (activeCandidate == null) return null;
        activeSession = modeController.startSession(
                activeCandidate.entityId, TargetPurpose.SCAN_ACQUISITION, nowNanos
        );
        activeSession.transitionTo(TargetSessionState.ACQUIRING_PLATE, nowNanos);
        repository.markActiveTarget(activeCandidate.entityId, true);
        return activeSession;
    }

    public synchronized Outcome completeAttempt(
            long plateTrackId,
            NormalizedQuad quad,
            AppearanceDescriptor plateAppearance,
            PlateTextConsensus consensus,
            CropReference crop,
            BestCropSelector.Quality quality,
            long nowNanos
    ) {
        if (activeCandidate == null || activeSession == null) return Outcome.IGNORED;
        VehicleEntity entity = repository.get(activeCandidate.entityId);
        if (entity == null) return release(false, true, nowNanos);
        activeCandidate.mtAttempts++;
        if (quad != null) {
            repository.attachPlate(
                    entity.entityId(), plateTrackId, quad, plateAppearance, nowNanos
            );
            activeCandidate.plateLocalized = true;
            activeSession.transitionTo(TargetSessionState.READING_REGISTRATION, nowNanos);
        } else {
            repository.recordMtAttempt(entity.entityId(), nowNanos);
        }
        if (consensus != null) {
            repository.updateRegistration(entity.entityId(), consensus, nowNanos);
            activeCandidate.mzAttempts++;
            activeCandidate.registrationStable = consensus.stable;
        }
        if (crop != null) repository.considerCrop(entity.entityId(), crop);

        boolean success = consensus != null && consensus.stable
                || cropSelector.veryGood(quality)
                || cropSelector.sufficientMixed(quality);
        if (success) return release(true, false, nowNanos);
        return release(false, activeCandidate.mtAttempts >= MAX_MT_ATTEMPTS, nowNanos);
    }

    public synchronized VehicleEntity activeEntity() {
        return activeCandidate == null ? null : repository.get(activeCandidate.entityId);
    }

    public synchronized TargetSession activeSession() { return activeSession; }
    public AcquisitionQueue queue() { return queue; }

    public synchronized void resetScene() {
        if (activeSession != null && !activeSession.state().terminal()) {
            activeSession.transitionTo(TargetSessionState.CANCELLED, 0L);
        }
        activeCandidate = null;
        activeSession = null;
        queue.clear();
    }

    private Outcome release(boolean acquired, boolean failed, long nowNanos) {
        long entityId = activeCandidate.entityId;
        repository.markActiveTarget(entityId, false);
        if (acquired) {
            repository.markAcquired(entityId);
            modeController.finishSession(
                    activeSession.sessionId(), TargetSessionState.COMPLETED, nowNanos
            );
        } else if (failed) {
            repository.markAcquisitionState(entityId, EntityAcquisitionState.FAILED);
            modeController.finishSession(
                    activeSession.sessionId(), TargetSessionState.LOST, nowNanos
            );
        } else {
            repository.markQueued(entityId);
            modeController.finishSession(
                    activeSession.sessionId(), TargetSessionState.CANCELLED, nowNanos
            );
            queue.restore(activeCandidate, nowNanos);
        }
        activeCandidate = null;
        activeSession = null;
        return acquired ? Outcome.ACQUIRED : failed ? Outcome.FAILED : Outcome.REQUEUED;
    }
}
