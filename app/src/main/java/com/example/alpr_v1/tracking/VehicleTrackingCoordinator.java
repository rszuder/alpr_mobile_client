package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.continuity.LocalVehiclePoolEvidence;

import com.example.alpr_v1.domain.VehicleEntityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime owner of vehicle-domain state. Its lifecycle is intentionally longer
 * than the model backends owned by MobileAlprEngine.
 */
public final class VehicleTrackingCoordinator {
    public static final long CONFIDENCE_DECAY_HORIZON_NANOS = 2_500_000_000L;
    public static final long MIN_ADAPTIVE_TRACK_TTL_NANOS = 3_000_000_000L;
    public static final long MAX_ADAPTIVE_TRACK_TTL_NANOS =
            VehicleTrackManager.DEFAULT_ENTITY_TTL_NANOS - 100_000_000L;
    public static final double TRACK_TTL_GAP_MULTIPLIER = 1.75;
    private static final float[] MISSED_UPDATE_PENALTIES = {1f, 0.75f, 0.50f, 0.25f};
    private final VehicleEntityRepository repository;
    private final com.example.alpr_v1.acquisition.DynamicVehicleSizeGate vehicleSizeGate =
            new com.example.alpr_v1.acquisition.DynamicVehicleSizeGate();
    public com.example.alpr_v1.acquisition.DynamicVehicleSizeGate vehicleSizeGate() { return vehicleSizeGate; }
    private final VehicleTrackManager tracker;
    private DynamicVehicleIdentity.Stamp dynamicMzOwner;
    private long dynamicMzSequence, dynamicMzTicket;

    /** Presentation only: never renews identity, geometry or recognition evidence. */
    public synchronized long beginDynamicMzAttempt(DynamicVehicleIdentity.Stamp owner) {
        if(!identity().enabled()||!identity().accepts(owner))return 0L;
        dynamicMzOwner=owner;
        dynamicMzTicket=++dynamicMzSequence;
        return dynamicMzTicket;
    }
    public synchronized void endDynamicMzAttempt(long ticket) {
        if(ticket>0L&&ticket==dynamicMzTicket){dynamicMzOwner=null;dynamicMzTicket=0L;}
    }
    private void clearDynamicMzActivity() {dynamicMzOwner=null;dynamicMzTicket=0L;}
    private boolean dynamicMzInProgress(long entityId) {
        return dynamicMzOwner!=null&&dynamicMzOwner.entityId==entityId&&identity().accepts(dynamicMzOwner);
    }
    private long sceneGeneration;
    private SourceFrameStamp observedContext;
    private long lastMpSourceTimestampNanos;
    private long rawMpFrameId, rawMpTimestampNanos;
    private Map<Long, com.example.alpr_v1.domain.NormalizedBounds> rawMpBounds = Collections.emptyMap();

    /** Positive MP boxes before Kalman projection, keyed by durable entity. */
    public synchronized Map<Long, com.example.alpr_v1.domain.NormalizedBounds> rawMpBounds(
            long sourceFrameId, long sourceTimestampNanos) {
        return rawMpFrameId == sourceFrameId && rawMpTimestampNanos == sourceTimestampNanos
                ? rawMpBounds : Collections.emptyMap();
    }
    private long lastMpObservationGapNanos;
    private VehicleTrackingFrame latestFrame = VehicleTrackingFrame.empty(0L);
    private VehicleTrackingStats lastReportedStats = VehicleTrackingStats.zero();
    private final ArrayDeque<VehicleTrackingEvent> events = new ArrayDeque<>();
    private static final int MAX_PENDING_EVENTS = 128;

    public VehicleTrackingCoordinator() {
        this(new VehicleEntityRepository());
    }

    VehicleTrackingCoordinator(VehicleEntityRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository is required");
        this.repository = repository;
        this.tracker = new VehicleTrackManager(repository);
        this.tracker.setTrackTtlNanos(MIN_ADAPTIVE_TRACK_TTL_NANOS);
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations
    ) {
        return updateFromMp(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                observations,
                Collections.emptySet(),
                snapshotTimestampNanos
        );
    }

    public synchronized void applyCameraMotion(
            FrameMotionTransform transform,
            long sourceTimestampNanos
    ) {
        // This runs before updateFromMp. Its intermediate prediction must already
        // use the incoming MP gap, otherwise it expires tracks with the previous,
        // shorter cadence just before the new measurements can associate.
        adaptTrackLifetime(sourceTimestampNanos, sourceTimestampNanos);
        tracker.applyCameraMotion(transform, sourceTimestampNanos);
        if (latestFrame.candidates.isEmpty()) return;
        latestFrame = frame(
                latestFrame.sourceFrameId,
                latestFrame.sourceSequence,
                Math.max(latestFrame.sourceTimestampNanos, sourceTimestampNanos),
                latestFrame.sourceTimestampDomain,
                Math.max(latestFrame.snapshotTimestampNanos, sourceTimestampNanos),
                tracker.predict(sourceTimestampNanos)
        );
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds
    ) {
        return updateFromMp(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                observations,
                protectedReassociationEntityIds,
                snapshotTimestampNanos
        );
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds,
            long eventRuntimeNanos
    ) {
        return updateFromMp(
                sourceFrameId,
                0L,
                sourceTimestampNanos,
                SourceTimestampDomain.UNKNOWN,
                snapshotTimestampNanos,
                observations,
                protectedReassociationEntityIds,
                eventRuntimeNanos
        );
    }

    public synchronized VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            SourceFrameStamp sourceFrameStamp,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds,
            long eventRuntimeNanos
    ) {
        SourceFrameStamp safe = sourceFrameStamp == null
                ? SourceFrameStamp.unknown(sceneGeneration, 0L, 0L)
                : sourceFrameStamp;
        observedContext=identity().enabled()?safe:null;
        return updateFromMp(
                sourceFrameId,
                safe.sourceSequence,
                safe.sourceTimestampNanos,
                safe.domain,
                snapshotTimestampNanos,
                observations,
                protectedReassociationEntityIds,
                eventRuntimeNanos
        );
    }

    private VehicleTrackingFrame updateFromMp(
            long sourceFrameId,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Observation> observations,
            Set<Long> protectedReassociationEntityIds,
            long eventRuntimeNanos
    ) {
        Map<Long, VehicleCandidate> previousByEntity = candidatesByEntity(
                latestFrame.candidates
        );
        Set<Long> activeBefore = activeEntityIds();
        adaptTrackLifetime(sourceTimestampNanos, snapshotTimestampNanos);
        if (lastMpSourceTimestampNanos > 0L
                && sourceTimestampNanos >= lastMpSourceTimestampNanos) {
            lastMpObservationGapNanos = sourceTimestampNanos - lastMpSourceTimestampNanos;
        }
        lastMpSourceTimestampNanos = Math.max(
                lastMpSourceTimestampNanos, sourceTimestampNanos
        );
        List<VehicleTrackManager.Snapshot> measured = tracker.update(
                observations,
                sourceTimestampNanos,
                protectedReassociationEntityIds
        );
        Map<Long, com.example.alpr_v1.domain.NormalizedBounds> raw = new HashMap<>();
        Map<Integer, com.example.alpr_v1.domain.NormalizedBounds> boundsBySource = new HashMap<>();
        if (observations != null) for (VehicleTrackManager.Observation observation : observations) {
            if (observation != null && observation.sourceIndex >= 0 && observation.bounds != null
                    && observation.bounds.valid()) boundsBySource.put(observation.sourceIndex, observation.bounds);
        }
        for (VehicleTrackManager.Snapshot snapshot : measured) {
            if (!snapshot.predicted && snapshot.missedUpdates == 0 && snapshot.sourceIndex >= 0
                    && boundsBySource.containsKey(snapshot.sourceIndex)) {
                raw.put(snapshot.entityId, boundsBySource.get(snapshot.sourceIndex));
            }
        }
        rawMpFrameId = sourceFrameId;
        rawMpTimestampNanos = sourceTimestampNanos;
        rawMpBounds = Collections.unmodifiableMap(raw);
        List<VehicleTrackManager.Snapshot> snapshots = snapshotTimestampNanos
                > sourceTimestampNanos
                ? tracker.projectAfterMeasurement(snapshotTimestampNanos) : measured;
        latestFrame = frame(
                sourceFrameId,
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain,
                snapshotTimestampNanos,
                snapshots
        );
        emitLifecycleEvents(
                previousByEntity,
                activeBefore,
                latestFrame,
                eventRuntimeNanos
        );
        return latestFrame;
    }

    public synchronized VehicleTrackingFrame predict(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos
    ) {
        return predict(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                snapshotTimestampNanos
        );
    }

    public synchronized VehicleTrackingFrame predict(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            long eventRuntimeNanos
    ) {
        Map<Long, VehicleCandidate> previousByEntity = candidatesByEntity(
                latestFrame.candidates
        );
        Set<Long> activeBefore = activeEntityIds();
        List<VehicleTrackManager.Snapshot> snapshots = tracker.predict(
                snapshotTimestampNanos
        );
        latestFrame = frame(
                sourceFrameId,
                sourceTimestampNanos,
                snapshotTimestampNanos,
                snapshots
        );
        emitLifecycleEvents(
                previousByEntity,
                activeBefore,
                latestFrame,
                eventRuntimeNanos
        );
        return latestFrame;
    }

    public synchronized VehicleTrackingFrame latestFrame() {
        latestFrame = withCurrentAcquisitionStates(latestFrame);
        return latestFrame;
    }
    public synchronized long lastMpObservationGapNanos() {
        return lastMpObservationGapNanos;
    }
    public synchronized long lastMpSourceTimestampNanos() {
        return lastMpSourceTimestampNanos;
    }
    public synchronized long currentTrackTtlNanos() { return tracker.trackTtlNanos(); }
    public VehicleEntityRepository repository() { return repository; }
    public DynamicVehicleIdentity identity() {return tracker.identity();}
    private static final class PlateAnchor {
        final long plateTrackId;
        final DynamicVehicleIdentity.Stamp owner;
        final com.example.alpr_v1.continuity.ContinuityStamp source;
        final com.example.alpr_v1.domain.NormalizedBounds vehicle, plate;
        final com.example.alpr_v1.domain.NormalizedQuad quad;
        PlateAnchor(long track,DynamicVehicleIdentity.Stamp owner,
                com.example.alpr_v1.continuity.ContinuityStamp source,
                com.example.alpr_v1.domain.NormalizedBounds vehicle,com.example.alpr_v1.domain.NormalizedQuad quad) {
            this.plateTrackId=track;this.owner=owner;this.source=source;this.vehicle=vehicle;this.plate=quad.bounds();this.quad=quad;
        }
    }
    private final Map<Long,PlateAnchor> plateAnchors=new HashMap<>();
    private final java.util.NavigableMap<Long,LocalVehiclePoolEvidence> localPoolHistory=new java.util.TreeMap<>();

    /** Exact source only: neither newer pixels nor an expired owner can explain an old frame. */
    public synchronized LocalVehiclePoolEvidence localPoolEvidence(SourceFrameStamp source) {
        if(!identity().enabled()||source==null)return LocalVehiclePoolEvidence.unavailable();
        LocalVehiclePoolEvidence saved=localPoolHistory.get(source.sourceTimestampNanos);
        if(saved==null||!saved.matches(source))return LocalVehiclePoolEvidence.unavailable();
        Map<Long,LocalVehiclePoolEvidence.Entry> entries=new java.util.LinkedHashMap<>();
        for(LocalVehiclePoolEvidence.Entry entry:saved.entries.values()) {
            DynamicVehicleIdentity.Stamp owner=identity().stamp(entry.entityId,entry.vehicleTrackId);
            boolean current=owner!=null&&owner.epoch==entry.ownerEpoch&&identity().accepts(owner);
            entries.put(entry.entityId,current||entry.status!=LocalVehiclePoolEvidence.Status.PROVEN?entry:
                    new LocalVehiclePoolEvidence.Entry(entry.entityId,entry.vehicleTrackId,entry.ownerEpoch,
                            LocalVehiclePoolEvidence.Status.UNAVAILABLE,false,false));
        }
        return new LocalVehiclePoolEvidence(source,entries);
    }
    private final Set<Long> observedPlateOwners=new java.util.HashSet<>();
    private final DynamicPlateGeometryGate plateGeometryGate=new DynamicPlateGeometryGate();
    private final Map<Long,DynamicPlateGeometryGate.Decision> plateGeometryDecisions=new java.util.LinkedHashMap<>();

    public synchronized DynamicPlateGeometryGate.Decision evaluatePlateGeometry(DynamicVehicleIdentity.Stamp owner,
            com.example.alpr_v1.continuity.ContinuityStamp source,
            com.example.alpr_v1.domain.NormalizedBounds detectorBox,com.example.alpr_v1.domain.NormalizedQuad quad) {
        com.example.alpr_v1.domain.NormalizedBounds measuredVehicle=owner!=null&&source!=null&&rawMpTimestampNanos==source.sourceTimestampNanos
                ?rawMpBounds.get(owner.entityId):null;
        DynamicPlateGeometryGate.Decision decision=plateGeometryGate.evaluate(owner,source,detectorBox,quad,measuredVehicle,
                owner!=null&&source!=null&&identity().accepts(owner,source.sourceTimestampNanos));
        if(owner!=null) {
            plateGeometryDecisions.put(owner.entityId,decision);
            while(plateGeometryDecisions.size()>64)plateGeometryDecisions.remove(plateGeometryDecisions.keySet().iterator().next());
        }
        return decision;
    }

    public synchronized boolean commitPlateGeometry(DynamicPlateGeometryGate.Decision decision,long plateTrackId) {
        if(decision==null||!decision.accepted()||!identity().accepts(decision.owner,decision.source.sourceTimestampNanos))return false;
        if(!plateGeometryGate.commit(decision))return false;
        anchorIdentityPlate(decision.owner,plateTrackId,decision.quad,decision.source);
        return true;
    }
    private static final class LocalEvidence {
        final com.example.alpr_v1.continuity.ContinuityStamp source;
        final DynamicVehicleIdentity.Stamp owner;
        final LocalStats vehicle,plate;
        final boolean cameraStamped;
        LocalEvidence(com.example.alpr_v1.continuity.ContinuityStamp source,DynamicVehicleIdentity.Stamp owner,
                TrackedPlate vehicle,TrackedPlate plate,boolean cameraStamped){this.source=source;this.owner=owner;this.cameraStamped=cameraStamped;
            this.vehicle=vehicle==null?null:new LocalStats(vehicle);this.plate=plate==null?null:new LocalStats(plate);}
    }
    private static final class LocalStats {
        final float trackingQuality,supportRatio,localAppearanceSimilarity;
        final int trackerInliers,consecutiveFailures;
        LocalStats(TrackedPlate item){trackingQuality=item.trackingQuality;supportRatio=item.supportRatio;
            localAppearanceSimilarity=item.localAppearanceSimilarity;trackerInliers=item.trackerInliers;consecutiveFailures=item.consecutiveFailures;}
    }
    private final Map<Long,java.util.NavigableMap<Long,LocalEvidence>> localEvidence=new HashMap<>();
    private final Map<Long,TrackedPlate> localPlateGeometry=new HashMap<>();
    private final Map<Long,Long> localPlateSourceTimes=new HashMap<>();
    private final Map<Long,com.example.alpr_v1.continuity.ContinuityStamp> localPlateContexts=new HashMap<>();
    private final Map<Long,TrackedPlate> localVehicleGeometry=new HashMap<>();
    private static final class TentativeBody {
        final com.example.alpr_v1.continuity.ContinuityStamp source;final TrackedPlate pixels;
        TentativeBody(com.example.alpr_v1.continuity.ContinuityStamp source,TrackedPlate pixels){this.source=source;this.pixels=pixels;}
    }
    private final Map<Long,TentativeBody> tentativeBodies=new HashMap<>();
    private LocalEvidence localAt(long id,long now) {
        java.util.NavigableMap<Long,LocalEvidence> history=localEvidence.get(id);
        Map.Entry<Long,LocalEvidence> entry=history==null?null:history.floorEntry(now);
        return entry==null?null:entry.getValue();
    }
    public synchronized com.example.alpr_v1.continuity.TargetContinuityEvidence localTargetEvidence(long id,long now,
            long scene,long visual,long transform) {
        LocalEvidence local=localAt(id,now);
        if(id==0)for(long owner:localEvidence.keySet()) {
            LocalEvidence candidate=localAt(owner,now);
            if(!currentLocal(candidate,now,scene,visual,transform))continue;
            if(local!=null)return null;
            local=candidate;
        }
        if(!currentLocal(local,now,scene,visual,transform))return null;
        com.example.alpr_v1.domain.VehicleEntity entity=repository.get(local.owner.entityId);
        if(entity==null)return null;
        LocalStats strongest=local.plate==null?local.vehicle:local.vehicle==null?local.plate:
                local.vehicle.trackingQuality>=local.plate.trackingQuality?local.vehicle:local.plate;
        com.example.alpr_v1.continuity.TargetContinuityLevel level=local.vehicle!=null&&local.plate!=null
                ?com.example.alpr_v1.continuity.TargetContinuityLevel.VEHICLE_AND_PLATE:local.vehicle!=null
                ?com.example.alpr_v1.continuity.TargetContinuityLevel.VEHICLE_ONLY:com.example.alpr_v1.continuity.TargetContinuityLevel.PLATE_ONLY;
        boolean freshMp=entity.lastMpNanos()>0&&now>=entity.lastMpNanos()
                &&now-entity.lastMpNanos()<=com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.reacquireTimeoutNanos;
        boolean freshMt=entity.lastMtSourceTimestampNanos()>0&&now>=entity.lastMtSourceTimestampNanos()
                &&now-entity.lastMtSourceTimestampNanos()<=350_000_000L;
        return new com.example.alpr_v1.continuity.TargetContinuityEvidence(entity.entityId(),entity.vehicleTrackId(),
                entity.plateTrackId()==null?0L:entity.plateTrackId(),level,strongest.trackingQuality,strongest.trackerInliers,
                strongest.supportRatio,strongest.consecutiveFailures,strongest.trackingQuality,strongest.trackingQuality,strongest.trackingQuality,
                local.vehicle==null?0f:local.vehicle.localAppearanceSimilarity,local.plate==null?0f:local.plate.localAppearanceSimilarity,
                0f,freshMp,freshMt,true,now-local.source.sourceTimestampNanos,local.plate!=null);
    }
    private boolean currentLocal(LocalEvidence local,long now,long scene,long visual,long transform) {
        return local!=null&&identity().enabled()&&identity().accepts(local.owner)
                &&local.source.sceneGeneration==scene&&local.source.visualEpoch==visual&&local.source.cameraTransformGeneration==transform
                &&now>=local.source.sourceTimestampNanos
                &&now-local.source.sourceTimestampNanos<=com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.maximumFocusedEvidenceAgeNanos;
    }
    public synchronized boolean hasCurrentLocalEvidence(com.example.alpr_v1.continuity.ContinuityStamp source) {
        for(Long id:localEvidence.keySet())if(displayLocal(id,source))return true;
        return false;
    }
    private boolean displayLocal(long id,com.example.alpr_v1.continuity.ContinuityStamp source) {
        LocalEvidence local=localAt(id,source.sourceTimestampNanos);
        return currentLocal(local,source.sourceTimestampNanos,source.sceneGeneration,source.visualEpoch,source.cameraTransformGeneration)&&local.cameraStamped
                &&source.sourceTimestampNanos-local.source.sourceTimestampNanos<=DynamicVehicleIdentity.COAST_NANOS;
    }
    private static boolean supportsPlateContext(PlateAnchor anchor,com.example.alpr_v1.continuity.ContinuityStamp source,
            PreviewTrackingFrame frame) {
        return sameContext(anchor.source,source)||frame!=null&&frame.sourceStamp!=null&&frame.sourceStamp.domain.cameraDerived()
                &&anchor.source.sceneGeneration==source.sceneGeneration&&anchor.source.visualEpoch<=source.visualEpoch
                &&anchor.source.cameraTransformGeneration<=source.cameraTransformGeneration;
    }

    /** Only a validated MT attachment and a vehicle geometry in the same source coordinates create a pair. */
    public synchronized void anchorIdentityPlate(DynamicVehicleIdentity.Stamp owner,long plateTrackId,
            com.example.alpr_v1.domain.NormalizedQuad quad,com.example.alpr_v1.continuity.ContinuityStamp source) {
        if(!identity().enabled()||owner==null||quad==null||source==null||!identity().accepts(owner))return;
        com.example.alpr_v1.domain.NormalizedBounds vehicle=rawMpTimestampNanos==source.sourceTimestampNanos
                ?rawMpBounds.get(owner.entityId):identity().boundsAt(owner.entityId,source.sourceTimestampNanos);
        if(vehicle==null||!vehicle.valid()||!quad.bounds().valid())return;
        plateAnchors.put(plateTrackId,new PlateAnchor(plateTrackId,owner,source,vehicle,quad));
        while(plateAnchors.size()>64)plateAnchors.remove(plateAnchors.keySet().iterator().next());
    }
    public synchronized Map<Long,DynamicVehicleIdentity.Stamp> plateAnchorOwners(
            com.example.alpr_v1.continuity.ContinuityStamp source) {
        Map<Long,DynamicVehicleIdentity.Stamp> result=new HashMap<>();
        if(source!=null)for(PlateAnchor anchor:plateAnchors.values())if(sameContext(anchor.source,source)
                &&anchor.source.sourceTimestampNanos==source.sourceTimestampNanos&&identity().accepts(anchor.owner))
            result.put(anchor.plateTrackId,anchor.owner);
        return result;
    }
    private static boolean sameContext(com.example.alpr_v1.continuity.ContinuityStamp a,
            com.example.alpr_v1.continuity.ContinuityStamp b) {
        return a.sceneGeneration==b.sceneGeneration&&a.visualEpoch==b.visualEpoch
                &&a.cameraTransformGeneration==b.cameraTransformGeneration
                &&(a.sourceTimestampDomain==b.sourceTimestampDomain
                    ||a.sourceTimestampDomain.freshnessComparableWith(b.sourceTimestampDomain));
    }
    static boolean validLocal(TrackedPlate item) {
        return item!=null&&item.overlayItem!=null&&!item.overlayItem.carriedPrediction
                &&item.consecutiveFailures==0&&item.localAppearanceValidated
                &&item.trackerInliers>=com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.minimumTrackerInliers
                &&(item.trackingQuality>=com.example.alpr_v1.pipeline.TargetStateMachine.QUALITY_TRACKING
                    &&item.localAppearanceSimilarity>=com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.localAppearanceContradictionThreshold
                    // Presence can be well supported before the stricter OCR lock is ready.
                    ||item.trackingQuality>=.60f&&item.supportRatio>=.50f&&item.meanMatchError<=16f
                    &&item.localAppearanceSimilarity>=.65f);
    }
    static boolean usablePosition(TrackedPlate item) {
        return item!=null&&item.overlayItem!=null&&!item.overlayItem.carriedPrediction&&item.consecutiveFailures==0
                &&item.localAppearanceValidated&&item.localAppearanceSimilarity>=com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.localAppearanceContradictionThreshold
                &&item.trackerInliers>=3&&item.trackingQuality>=.5f&&item.supportRatio>=.4f;
    }
    private TentativeBody tentativeBody(long id,long now,com.example.alpr_v1.continuity.ContinuityStamp context) {
        TentativeBody value=tentativeBodies.get(id);LocalEvidence strong=localAt(id,now);
        return value!=null&&context!=null&&sameContext(value.source,context)&&identity().accepts(value.pixels.identityOwner,now)
                &&now>=value.source.sourceTimestampNanos&&now-value.source.sourceTimestampNanos<=DynamicVehicleIdentity.COAST_NANOS
                &&strong!=null&&sameContext(strong.source,context)&&now>=strong.source.sourceTimestampNanos
                &&now-strong.source.sourceTimestampNanos<=2_000_000_000L?value:null;
    }
    private static boolean contradictsLocalAppearance(TrackedPlate item) {
        return item!=null&&item.overlayItem!=null&&!item.overlayItem.carriedPrediction
                &&item.localAppearanceValidated&&item.trackingQuality>=com.example.alpr_v1.pipeline.TargetStateMachine.QUALITY_TRACKING
                &&item.trackerInliers>=com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.minimumTrackerInliers
                &&item.localAppearanceSimilarity<com.example.alpr_v1.continuity.SceneContinuityProfile.INITIAL.localAppearanceContradictionThreshold;
    }
    public synchronized void observeLocalTargets(com.example.alpr_v1.continuity.ContinuityStamp source,
            PreviewTrackingFrame vehicles,boolean vehiclesAvailable,PreviewTrackingFrame plates,boolean platesAvailable) {
        if(!identity().enabled())return;
        boolean pairReplaying=replayingInContext(vehicles,source)||replayingInContext(plates,source);
        if(!matchesLocalSource(vehicles,source)){vehicles=null;vehiclesAvailable=false;}
        if(!matchesLocalSource(plates,source)){plates=null;platesAvailable=false;}
        long time=source.sourceTimestampNanos;
        Set<Long> vehicleProof=new java.util.HashSet<>(),plateProof=new java.util.HashSet<>();
        Set<Long> vehicleEvaluated=new java.util.HashSet<>(),plateEvaluated=new java.util.HashSet<>();
        Map<Long,TrackedPlate> validVehicles=new HashMap<>(),validPlates=new HashMap<>();
        Set<Long> contradictions=new java.util.HashSet<>();
        Set<Long> uncertainOwners=new java.util.HashSet<>();
        if(vehiclesAvailable&&vehicles!=null) {
            if(vehicles.absenceEvaluated) {
                for(TrackedPlate item:vehicles.trackedPlates)if(allowedLocalOwner(item.trackId)&&contradictsLocalAppearance(item))contradictions.add(item.trackId);
                if(!pairReplaying&&!vehicles.ownerScopedEvaluation)for(com.example.alpr_v1.domain.VehicleEntity entity:repository.activeEntities())if(allowedLocalOwner(entity.entityId()))vehicleEvaluated.add(entity.entityId());
            }
            for(TrackedPlate item:vehicles.trackedPlates)if(allowedLocalOwner(item.trackId)
                    &&item.identityOwner!=null&&identity().accepts(item.identityOwner,time)) {
                // A measured but temporarily weak flow is UNKNOWN, not an evaluated absence.
                if(!validLocal(item)&&!contradictsLocalAppearance(item))uncertainOwners.add(item.trackId);
                if(!validLocal(item)&&usablePosition(item))tentativeBodies.put(item.trackId,new TentativeBody(source,item));
                else if(validLocal(item))tentativeBodies.remove(item.trackId);
            }
            for(TrackedPlate item:vehicles.trackedPlates)if(validLocal(item)&&allowedLocalOwner(item.trackId)
                    &&(vehicles.sourceStamp==null||item.identityOwner!=null&&identity().accepts(item.identityOwner,time))) {
                vehicleProof.add(item.trackId);
                validVehicles.put(item.trackId,item);
                localVehicleGeometry.put(item.trackId,item);
                identity().localBounds(item.trackId,new com.example.alpr_v1.domain.NormalizedBounds(item.normalizedBounds.left,
                        item.normalizedBounds.top,item.normalizedBounds.right,item.normalizedBounds.bottom),time,false);
            }
        }
        plateAnchors.entrySet().removeIf(e->!identity().accepts(e.getValue().owner));
        if(platesAvailable&&plates!=null) {
            for(PlateAnchor anchor:plateAnchors.values())if(!pairReplaying&&!plates.ownerScopedEvaluation&&plates.absenceEvaluated&&supportsPlateContext(anchor,source,plates)
                    &&allowedLocalOwner(anchor.owner.entityId)&&observedPlateOwners.contains(anchor.owner.entityId))plateEvaluated.add(anchor.owner.entityId);
            for(TrackedPlate item:plates.trackedPlates) {
                PlateAnchor anchor=plateAnchors.get(item.trackId);
                if(anchor==null||!supportsPlateContext(anchor,source,plates)||item.identityOwner==null||!allowedLocalOwner(anchor.owner.entityId))continue;
                if(time<anchor.source.sourceTimestampNanos){plateEvaluated.remove(anchor.owner.entityId);continue;}
                DynamicVehicleIdentity.Stamp owner=item.identityOwner;
                if(owner.entityId!=anchor.owner.entityId||owner.epoch!=anchor.owner.epoch
                        ||owner.vehicleTrackId!=anchor.owner.vehicleTrackId) {
                    plateEvaluated.remove(anchor.owner.entityId);continue;
                }
                if(item.ageFrames<=1&&!item.localAppearanceValidated) {
                    plateEvaluated.remove(owner.entityId);continue;
                }
                if(!pairReplaying&&plates.absenceEvaluated)plateEvaluated.add(owner.entityId);
                observedPlateOwners.add(owner.entityId);
                if(plates.absenceEvaluated&&contradictsLocalAppearance(item))contradictions.add(owner.entityId);
                if(!validLocal(item)&&!contradictsLocalAppearance(item))uncertainOwners.add(owner.entityId);
                if(!validLocal(item))continue;
                plateProof.add(owner.entityId);
                validPlates.put(owner.entityId,item);
                localPlateGeometry.put(owner.entityId,item);
                localPlateSourceTimes.put(owner.entityId,time);
                localPlateContexts.put(owner.entityId,source);
                if(vehicleProof.contains(owner.entityId))continue;
                com.example.alpr_v1.domain.NormalizedBounds plate=new com.example.alpr_v1.domain.NormalizedBounds(
                        item.normalizedBounds.left,item.normalizedBounds.top,item.normalizedBounds.right,item.normalizedBounds.bottom);
                float sx=plate.width()/anchor.plate.width(),sy=plate.height()/anchor.plate.height();
                // Preserve the measured pair's proportions; do not invent an MP observation.
                com.example.alpr_v1.domain.NormalizedBounds projected=new com.example.alpr_v1.domain.NormalizedBounds(
                        plate.centerX()+(anchor.vehicle.left-anchor.plate.centerX())*sx,
                        plate.centerY()+(anchor.vehicle.top-anchor.plate.centerY())*sy,
                        plate.centerX()+(anchor.vehicle.right-anchor.plate.centerX())*sx,
                        plate.centerY()+(anchor.vehicle.bottom-anchor.plate.centerY())*sy);
                identity().localBounds(owner.entityId,projected,time,true);
            }
        }
        for(long id:contradictions)if(!vehicleProof.contains(id)&&!plateProof.contains(id))identity().rejectLocalAppearance(id,time);
        vehicleEvaluated.removeAll(uncertainOwners);plateEvaluated.removeAll(uncertainOwners);
        identity().preview(time,vehicleProof,plateProof,vehicleEvaluated,plateEvaluated);
        Map<Long,LocalVehiclePoolEvidence.Entry> pool=new java.util.LinkedHashMap<>();
        for(com.example.alpr_v1.domain.VehicleEntity entity:repository.activeEntities()) {
            long id=entity.entityId();
            if(!allowedLocalOwner(id))continue;
            DynamicVehicleIdentity.Stamp owner=identity().stamp(id,entity.vehicleTrackId());
            boolean vehicle=vehicleProof.contains(id),plate=plateProof.contains(id);
            boolean proved=(vehicle||plate)&&owner!=null&&identity().accepts(owner);
            boolean evaluated=vehicleEvaluated.contains(id)||plateEvaluated.contains(id);
            pool.put(id,new LocalVehiclePoolEvidence.Entry(id,entity.vehicleTrackId(),owner==null?0L:owner.epoch,
                    proved?LocalVehiclePoolEvidence.Status.PROVEN:contradictions.contains(id)||evaluated
                            ?LocalVehiclePoolEvidence.Status.CONTRADICTED:LocalVehiclePoolEvidence.Status.UNAVAILABLE,
                    proved&&vehicle,proved&&plate));
        }
        localPoolHistory.put(time,new LocalVehiclePoolEvidence(source.sourceFrameStamp(),pool));
        while(localPoolHistory.size()>32)localPoolHistory.pollFirstEntry();
        tentativeBodies.entrySet().removeIf(e->!identity().accepts(e.getValue().pixels.identityOwner)
                ||time-e.getValue().source.sourceTimestampNanos>2_000_000_000L);
        Set<Long> proved=new HashSet<>(vehicleProof);proved.addAll(plateProof);
        for(long id:proved) {
            tentativeBodies.remove(id);
            com.example.alpr_v1.domain.VehicleEntity entity=repository.get(id);
            if(entity==null)continue;
            DynamicVehicleIdentity.Stamp owner=identity().stamp(id,entity.vehicleTrackId());
            java.util.NavigableMap<Long,LocalEvidence> history=localEvidence.computeIfAbsent(id,ignored->new java.util.TreeMap<>());
            if(identity().accepts(owner)&&(history.isEmpty()||history.lastKey()<=time)) {
                boolean cameraStamped=validVehicles.containsKey(id)&&vehicles!=null&&vehicles.sourceStamp!=null&&vehicles.sourceStamp.domain.cameraDerived()
                        ||validPlates.containsKey(id)&&plates!=null&&plates.sourceStamp!=null&&plates.sourceStamp.domain.cameraDerived();
                history.put(time,new LocalEvidence(source,owner,validVehicles.get(id),validPlates.get(id),cameraStamped));
                while(history.size()>128)history.pollFirstEntry();
                if(cameraStamped) {
                    TrackedPlate plate=validPlates.get(id);
                    plateGeometryGate.observe(owner,source,identity().boundsAt(id,time),plate==null?null:plate.fullReferenceQuad);
                }
            }
        }
        localEvidence.entrySet().removeIf(e->e.getValue().isEmpty()||!identity().accepts(e.getValue().lastEntry().getValue().owner));
    }
    private static boolean matchesLocalSource(PreviewTrackingFrame frame,
            com.example.alpr_v1.continuity.ContinuityStamp source) {
        if(frame!=null&&frame.state!=PreviewTrackingFrame.State.LIVE)return false;
        if(frame==null||frame.sourceStamp==null)return true; // Existing bitmap/test callers have no camera stamp.
        com.example.alpr_v1.continuity.SourceFrameStamp stamp=frame.sourceStamp;
        return stamp.sceneGeneration==source.sceneGeneration&&stamp.visualEpoch==source.visualEpoch
                &&stamp.cameraTransformGeneration==source.cameraTransformGeneration&&stamp.domain==source.sourceTimestampDomain
                &&stamp.sourceSequence==source.sourceSequence&&stamp.sourceTimestampNanos==source.sourceTimestampNanos;
    }
    private static boolean replayingInContext(PreviewTrackingFrame frame,
            com.example.alpr_v1.continuity.ContinuityStamp source) {
        if(frame==null||frame.state!=PreviewTrackingFrame.State.REPLAYING||frame.sourceStamp==null)return false;
        com.example.alpr_v1.continuity.SourceFrameStamp stamp=frame.sourceStamp;
        return sameContext(stamp.continuityStamp(),source)&&stamp.domain.cameraDerived()
                &&stamp.sourceTimestampNanos<=source.sourceTimestampNanos
                &&source.sourceTimestampNanos-stamp.sourceTimestampNanos<=10_000_000_000L;
    }
    public synchronized void observeLocalVehicles(long sourceTime,PreviewTrackingFrame frame,boolean available) {
        Set<Long> validated=new java.util.HashSet<>();
        if(available&&frame!=null)for(TrackedPlate item:frame.trackedPlates) {
            if(validLocal(item)) {
                validated.add(item.trackId);
                tracker.identity().localBounds(item.trackId,new com.example.alpr_v1.domain.NormalizedBounds(
                        item.normalizedBounds.left,item.normalizedBounds.top,item.normalizedBounds.right,item.normalizedBounds.bottom),sourceTime);
            }
        }
        tracker.identity().preview(sourceTime,validated,available&&frame!=null);
    }
    public synchronized boolean acceptsObservation(com.example.alpr_v1.pipeline.PlateObservation observation) {
        if(observation==null)return false;
        if(!identity().enabled())return true;
        if(observation.identityOwner()==null)return !identity().guarded(observation.entityId);
        return !identity().verifying(observation.entityId)&&identity().accepts(observation.identityOwner());
    }
    public synchronized Map<Long,Long> identityPlateOwners() {
        Map<Long,Long> result=new HashMap<>();
        if(identity().enabled())for(com.example.alpr_v1.domain.VehicleEntity entity:repository.activeEntities())
            if(entity.plateTrackId()!=null)result.put(entity.plateTrackId(),entity.entityId());
        return Collections.unmodifiableMap(result);
    }
    public synchronized DynamicVehicleDisplaySnapshot displaySnapshot(long sourceTime) {
        return displaySnapshot(sourceTime,observedContext==null?null:observedContext.continuityStamp());
    }
    public synchronized DynamicVehicleDisplaySnapshot displaySnapshot(com.example.alpr_v1.continuity.ContinuityStamp source) {
        return displaySnapshot(source.sourceTimestampNanos,source);
    }
    private DynamicVehicleDisplaySnapshot displaySnapshot(long sourceTime,com.example.alpr_v1.continuity.ContinuityStamp context) {
        Map<Long,DynamicVehicleDisplaySnapshot.Vehicle> vehicles=new HashMap<>();
        Map<Long,com.example.alpr_v1.domain.NormalizedBounds> tentative=new HashMap<>();
        Set<Long> plateDerived=identity().plateDerivedBounds(sourceTime).keySet();
        for(com.example.alpr_v1.domain.VehicleEntity entity:repository.activeEntities()) {
            long id=entity.entityId();TrackedPlate local=localPlateGeometry.get(id);
            PlateAnchor anchor=entity.plateTrackId()==null?null:plateAnchors.get(entity.plateTrackId());
            com.example.alpr_v1.domain.NormalizedQuad quad=null;
            long proof=0L,measured=anchor==null?0L:anchor.source.sourceTimestampNanos;
            long bodyMeasured=identity().measuredAt(id);
            TrackedPlate body=localVehicleGeometry.get(id);
            TentativeBody weak=tentativeBody(id,sourceTime,context);
            if(weak!=null) {
                android.graphics.RectF b=weak.pixels.normalizedBounds;
                tentative.put(id,new com.example.alpr_v1.domain.NormalizedBounds(b.left,b.top,b.right,b.bottom));
            }
            LocalEvidence latest=localAt(id,sourceTime);
            long geometryAt=weak!=null?weak.source.sourceTimestampNanos:latest==null?0L:latest.source.sourceTimestampNanos;
            if(body!=null&&body.identityOwner!=null&&identity().accepts(body.identityOwner)&&body.anchorSourceTimestampNanos>0)
                bodyMeasured=body.anchorSourceTimestampNanos;
            long plateTime=localPlateSourceTimes.getOrDefault(id,0L);
            boolean sameContext=anchor!=null&&context!=null&&sameContext(anchor.source,context);
            boolean plateContext=context!=null&&localPlateContexts.get(id)!=null&&sameContext(localPlateContexts.get(id),context);
            if(plateContext&&local!=null&&local.identityOwner!=null&&identity().accepts(local.identityOwner)
                    &&plateTime>0&&sourceTime>=plateTime&&sourceTime-plateTime<=DynamicVehicleIdentity.COAST_NANOS) {
                List<android.graphics.PointF> points=local.overlayItem.normalizedKeypoints;
                if(points.size()==4) {
                    float[] values=new float[8];for(int i=0;i<4;i++){values[2*i]=points.get(i).x;values[2*i+1]=points.get(i).y;}
                    quad=new com.example.alpr_v1.domain.NormalizedQuad(values);proof=plateTime;
                    if(local.anchorSourceTimestampNanos>0)measured=local.anchorSourceTimestampNanos;
                    if(plateDerived.contains(id))bodyMeasured=measured;
                }
            }
            // MT geometry is independent of OCR and can follow a continuously validated vehicle.
            // This supplies display geometry only; it is never independent plate-presence evidence.
            // Display attachment follows the same 600 ms pixel window as its body.
            // The stricter focused-OCR freshness must not flash away just the plate.
            boolean bodyContext=context!=null&&context.sourceTimestampDomain.cameraDerived()
                    &&latest!=null&&latest.vehicle!=null&&latest.cameraStamped&&identity().accepts(latest.owner)
                    &&sameContext(latest.source,context)&&sourceTime>=latest.source.sourceTimestampNanos
                    &&sourceTime-latest.source.sourceTimestampNanos<=DynamicVehicleIdentity.COAST_NANOS;
            if(quad==null&&anchor!=null&&context!=null&&anchor.source.sceneGeneration==context.sceneGeneration
                    &&(sameContext||bodyContext)&&body!=null&&body.identityOwner!=null&&identity().accepts(body.identityOwner)
                    &&(bodyContext||"VEHICLE".equals(identity().lastLocalEvidence(id)))
                    &&identity().displayBounds(sourceTime).containsKey(id)&&body.overlayItem.normalizedKeypoints.size()==4
                    &&sourceTime>=anchor.source.sourceTimestampNanos
                    &&(bodyContext||sourceTime-anchor.source.sourceTimestampNanos
                    <=com.example.alpr_v1.acquisition.DynamicMtConfig.LOCAL_MAX_AGE_NANOS)) {
                quad=projectAttachedPlate(anchor,body.overlayItem.normalizedKeypoints,anchor.quad);
            }
            if(weak!=null&&anchor!=null&&sameContext(anchor.source,context)
                    &&identity().accepts(anchor.owner)&&weak.pixels.overlayItem.normalizedKeypoints.size()==4) {
                quad=projectAttachedPlate(anchor,weak.pixels.overlayItem.normalizedKeypoints,anchor.quad);
                proof=0L;
            }
            vehicles.put(id,new DynamicVehicleDisplaySnapshot.Vehicle(id,entity.plateTrackId()==null?0L:entity.plateTrackId(),
                    bodyMeasured,measured,proof,entity.registration(),quad,geometryAt,entity.dynamicBadgeReading(),
                    entity.dynamicBadgeSourceNanos(),entity.mzAttempts(),dynamicMzInProgress(id)));
        }
        Map<Long,DynamicVehicleIdentity.Presentation> shown=identity().presentation();
        Map<Long,com.example.alpr_v1.domain.NormalizedBounds> geometry=new HashMap<>(identity().displayBounds(sourceTime));
        if(context!=null&&(latestFrame.visualEpoch!=context.visualEpoch||latestFrame.cameraTransformGeneration!=context.cameraTransformGeneration)) {
            shown=new HashMap<>(shown);
            for(Map.Entry<Long,DynamicVehicleIdentity.Presentation> entry:new ArrayList<>(shown.entrySet())) {
                if(displayLocal(entry.getKey(),context))continue;
                geometry.remove(entry.getKey());DynamicVehicleIdentity.Presentation value=entry.getValue();
                shown.put(entry.getKey(),new DynamicVehicleIdentity.Presentation(value.number,value.epoch,false,value.verifying,value.recoveryUntilNanos));
            }
        }
        Map<Long,DynamicPlateGeometryGate.Decision> geometryStatus=new HashMap<>();
        for(Map.Entry<Long,DynamicPlateGeometryGate.Decision> entry:plateGeometryDecisions.entrySet()) {
            DynamicPlateGeometryGate.Decision decision=entry.getValue();
            if(identity().accepts(decision.owner)&&decision.source!=null&&decision.source.sourceTimestampNanos<=sourceTime
                    &&(context==null||sameContext(decision.source,context)))geometryStatus.put(entry.getKey(),decision);
        }
        return new DynamicVehicleDisplaySnapshot(sourceTime,identity().analysisRunId(),latestFrame.sceneGeneration,
                identity().canonicalOwners(),shown,geometry,identityPlateOwners(),vehicles,vehicleSizeGate.decisions(),geometryStatus,tentative);
    }

    private static com.example.alpr_v1.domain.NormalizedQuad projectAttachedPlate(PlateAnchor anchor,
            List<android.graphics.PointF> body,com.example.alpr_v1.domain.NormalizedQuad measured) {
        if(measured==null)return null;
        float[] points=measured.points();
        for(int i=0;i<4;i++) {
            float u=(points[2*i]-anchor.vehicle.left)/anchor.vehicle.width();
            float v=(points[2*i+1]-anchor.vehicle.top)/anchor.vehicle.height();
            if(u<0||u>1||v<0||v>1)return null;
            points[2*i]=(1-u)*(1-v)*body.get(0).x+u*(1-v)*body.get(1).x+u*v*body.get(2).x+(1-u)*v*body.get(3).x;
            points[2*i+1]=(1-u)*(1-v)*body.get(0).y+u*(1-v)*body.get(1).y+u*v*body.get(2).y+(1-u)*v*body.get(3).y;
        }
        return new com.example.alpr_v1.domain.NormalizedQuad(points);
    }
    public synchronized boolean preflightIdentity(DynamicVehicleIdentity.Stamp owner,String raw,long sourceTime) {
        if(!identity().enabled()||owner==null)return true;
        if(!identity().accepts(owner,sourceTime))return false;
        if(identity().conflicts(owner,raw)) {
            recordEvent("vehicle_registration_disagreement",owner.entityId,owner.vehicleTrackId,0,0,sourceTime,
                    "fresh_ocr_updates_character_evidence_without_breaking_object_continuity");
        }
        return true;
    }
    /** Preview invalidation and this short mutation share the same runtime owner lock. */
    public synchronized <T> T withIdentityOwner(DynamicVehicleIdentity.Stamp owner,long sourceTime,
            java.util.function.Supplier<T> operation,T rejected) {
        if(identity().enabled()&&(owner==null?!identity().sourceCurrent(sourceTime):!identity().accepts(owner,sourceTime)))return rejected;
        return operation.get();
    }
    public synchronized long verifyIdentity(DynamicVehicleIdentity.Stamp owner,String raw,long frameId,long sequence,long sourceTime) {
        if(owner==null)return 0;
        long resolved=tracker.verifyFresh(owner,raw,frameId,sequence,sourceTime);
        if(resolved>0&&resolved!=owner.entityId) {
            for(Map.Entry<Long,PlateAnchor> e:new java.util.ArrayList<>(plateAnchors.entrySet())) {
                PlateAnchor anchor=e.getValue();
                if(anchor.owner.entityId==owner.entityId)plateAnchors.put(e.getKey(),new PlateAnchor(anchor.plateTrackId,
                        identity().stamp(resolved,owner.vehicleTrackId),anchor.source,anchor.vehicle,anchor.quad));
            }
            Map<Long,com.example.alpr_v1.domain.NormalizedBounds> moved=new HashMap<>(rawMpBounds);
            com.example.alpr_v1.domain.NormalizedBounds measured=moved.remove(owner.entityId);
            if(measured!=null)moved.put(resolved,measured);
            rawMpBounds=Collections.unmodifiableMap(moved);
            latestFrame=replaceIdentity(latestFrame,owner.entityId,resolved);
            recordEvent("vehicle_identity_plate_verified",resolved,owner.vehicleTrackId,0,frameId,sourceTime,"two_independent_fresh_plate_reads");
        }
        return resolved;
    }
    public synchronized VehicleTrackingFrame replaceIdentity(VehicleTrackingFrame frame,long from,long to) {
        List<VehicleCandidate> values=new ArrayList<>();
        for(VehicleCandidate c:frame.candidates)values.add(c.entityId!=from?c:new VehicleCandidate(to,c.vehicleTrackId,c.bounds,
                c.detectionConfidence,c.effectiveConfidence,c.exitUrgency,c.predicted,c.missedUpdates,
                c.lastMeasurementTimestampNanos,c.snapshotTimestampNanos,c.sourceIndex,c.acquisitionState));
        return new VehicleTrackingFrame(frame.sourceFrameId,frame.sourceSequence,frame.sourceTimestampNanos,
                frame.sourceTimestampDomain,frame.snapshotTimestampNanos,frame.sceneGeneration,frame.visualEpoch,
                frame.cameraTransformGeneration,values);
    }
    /** A reused technical plate track gets a new owner only when its previous object is lost. */
    public synchronized boolean prepareCandidatePlate(long entityId,long plateTrackId,
            com.example.alpr_v1.domain.NormalizedQuad quad,com.example.alpr_v1.domain.AppearanceDescriptor appearance,long now) {
        if(!identity().enabled())return false;
        com.example.alpr_v1.domain.VehicleEntity previous=repository.findByPlateTrackId(plateTrackId);
        if(previous==null||previous.entityId()==entityId)return false;
        DynamicVehicleIdentity.Presentation view=identity().presentation().get(previous.entityId());
        if(view==null||view.visible)return false;
        return repository.reassignPlateTrack(plateTrackId,previous.entityId(),entityId,quad,appearance,now)
                ==com.example.alpr_v1.domain.PlateTrackAttachmentStatus.REASSIGNED;
    }
    public synchronized void setNormalDynamicIdentity(boolean enabled,long recoveryGraceNanos) {
        if(!enabled||enabled!=identity().enabled())clearDynamicMzActivity();
        if(!enabled||enabled!=identity().enabled()){plateGeometryGate.clear();plateGeometryDecisions.clear();plateAnchors.clear();observedPlateOwners.clear();localEvidence.clear();localPoolHistory.clear();localPlateGeometry.clear();localPlateSourceTimes.clear();localPlateContexts.clear();localVehicleGeometry.clear();tentativeBodies.clear();}
        if(!enabled)exclusiveLocalOwner=0;
        tracker.setNormalDynamicIdentity(enabled,recoveryGraceNanos);
    }
    public synchronized void setPriorityTarget(long entityId, long recoveryGraceNanos) {
        tracker.setPriorityTarget(entityId, recoveryGraceNanos);
    }
    private long exclusiveLocalOwner;
    private boolean allowedLocalOwner(long id) {
        return exclusiveLocalOwner==0||id==exclusiveLocalOwner||identity().verificationFor(id,exclusiveLocalOwner);
    }
    public synchronized void setExclusiveTarget(long entityId) {
        exclusiveLocalOwner=identity().enabled()?Math.max(0L,entityId):0L;tracker.setExclusiveTarget(entityId);
    }

    private void adaptTrackLifetime(long sourceTimestampNanos, long snapshotTimestampNanos) {
        long incomingGap = lastMpSourceTimestampNanos > 0L
                ? Math.max(0L, sourceTimestampNanos - lastMpSourceTimestampNanos) : 0L;
        long processingLatency = Math.max(0L, snapshotTimestampNanos - sourceTimestampNanos);
        tracker.setTrackTtlNanos(adaptiveTrackTtl(Math.max(incomingGap, processingLatency)));
    }
    public synchronized VehicleTrackingStats stats() { return tracker.stats(); }

    /** Event counters for one trace; gauges and durations remain current values. */
    public synchronized VehicleTrackingStats statsDelta() {
        VehicleTrackingStats current = tracker.stats();
        VehicleTrackingStats delta = current.deltaSince(lastReportedStats);
        lastReportedStats = current;
        return delta;
    }

    public synchronized void recordEvent(
            String eventType,
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            long frameId,
            long elapsedNanos,
            String reason
    ) {
        addEvent(new VehicleTrackingEvent(
                eventType,
                entityId,
                vehicleTrackId,
                plateTrackId,
                frameId,
                elapsedNanos,
                sceneGeneration,
                reason
        ));
    }

    public synchronized List<VehicleTrackingEvent> drainEvents() {
        List<VehicleTrackingEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    /** Explicit scene boundary; model-engine recreation does not call this method. */
    public synchronized long resetScene() {
        clearDynamicMzActivity();
        localPoolHistory.clear();
        plateGeometryGate.clear();
        plateGeometryDecisions.clear();
        localPlateGeometry.clear();localPlateSourceTimes.clear();localPlateContexts.clear();localVehicleGeometry.clear();tentativeBodies.clear();
        observedContext=null;
        vehicleSizeGate.reset();
        rawMpBounds = Collections.emptyMap();
        rawMpFrameId = rawMpTimestampNanos = 0L;
        for (com.example.alpr_v1.domain.VehicleEntity entity : repository.activeEntities()) {
            addEvent(new VehicleTrackingEvent(
                    "vehicle_entity_expired",
                    entity.entityId(),
                    entity.vehicleTrackId(),
                    entity.plateTrackId() == null ? 0L : entity.plateTrackId(),
                    latestFrame.sourceFrameId,
                    latestFrame.snapshotTimestampNanos,
                    sceneGeneration,
                    "scene_reset"
            ));
        }
        tracker.resetScene();
        sceneGeneration++;
        lastMpSourceTimestampNanos = 0L;
        lastMpObservationGapNanos = 0L;
        latestFrame = VehicleTrackingFrame.empty(sceneGeneration);
        return sceneGeneration;
    }

    public synchronized long sceneGeneration() { return sceneGeneration; }

    private void emitLifecycleEvents(
            Map<Long, VehicleCandidate> previousByEntity,
            Set<Long> activeBefore,
            VehicleTrackingFrame current,
            long elapsedNanos
    ) {
        Set<Long> currentTrackIds = new HashSet<>();
        for (VehicleCandidate candidate : current.candidates) {
            currentTrackIds.add(candidate.vehicleTrackId);
            VehicleCandidate previous = previousByEntity.get(candidate.entityId);
            if (previous == null || previous.vehicleTrackId != candidate.vehicleTrackId) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_track_created",
                        candidate.entityId,
                        candidate.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        previous == null ? "new_or_returned_track" : "technical_track_changed"
                ));
            }
            if (!activeBefore.contains(candidate.entityId)) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_entity_created",
                        candidate.entityId,
                        candidate.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "new_mp_entity"
                ));
            } else if (previous == null
                    || previous.vehicleTrackId != candidate.vehicleTrackId) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_entity_reassociated",
                        candidate.entityId,
                        candidate.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "track_recovery"
                ));
            }
        }
        for (VehicleCandidate previous : previousByEntity.values()) {
            if (!currentTrackIds.contains(previous.vehicleTrackId)) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_track_expired",
                        previous.entityId,
                        previous.vehicleTrackId,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "track_missing_from_frame"
                ));
            }
        }
        Set<Long> activeAfter = activeEntityIds();
        for (Long entityId : activeBefore) {
            if (!activeAfter.contains(entityId)) {
                addEvent(new VehicleTrackingEvent(
                        "vehicle_entity_expired",
                        entityId,
                        0L,
                        0L,
                        current.sourceFrameId,
                        elapsedNanos,
                        sceneGeneration,
                        "entity_removed_from_active_repository"
                ));
            }
        }
    }

    private Map<Long, VehicleCandidate> candidatesByEntity(
            List<VehicleCandidate> candidates
    ) {
        Map<Long, VehicleCandidate> result = new HashMap<>();
        for (VehicleCandidate candidate : candidates) {
            result.put(candidate.entityId, candidate);
        }
        return result;
    }

    private Set<Long> activeEntityIds() {
        Set<Long> result = new HashSet<>();
        for (com.example.alpr_v1.domain.VehicleEntity entity : repository.activeEntities()) {
            result.add(entity.entityId());
        }
        return result;
    }

    private void addEvent(VehicleTrackingEvent event) {
        events.addLast(event);
        while (events.size() > MAX_PENDING_EVENTS) events.removeFirst();
    }

    private VehicleTrackingFrame frame(
            long sourceFrameId,
            long sourceTimestampNanos,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Snapshot> snapshots
    ) {
        return frame(
                sourceFrameId,
                latestFrame.sourceSequence,
                sourceTimestampNanos,
                latestFrame.sourceTimestampDomain,
                snapshotTimestampNanos,
                snapshots
        );
    }

    private VehicleTrackingFrame frame(
            long sourceFrameId,
            long sourceSequence,
            long sourceTimestampNanos,
            SourceTimestampDomain sourceTimestampDomain,
            long snapshotTimestampNanos,
            List<VehicleTrackManager.Snapshot> snapshots
    ) {
        List<VehicleCandidate> candidates = new ArrayList<>(snapshots.size());
        for (VehicleTrackManager.Snapshot snapshot : snapshots) {
            // Prediction outside the frame is not a visible candidate. Keep the
            // technical track for bounded recovery without aborting the MP frame.
            if (snapshot.bounds == null || !snapshot.bounds.valid()) continue;
            float effectiveConfidence = effectiveConfidence(snapshot, snapshotTimestampNanos);
            com.example.alpr_v1.domain.VehicleEntity entity = repository.get(
                    snapshot.entityId
            );
            candidates.add(new VehicleCandidate(
                    snapshot.entityId,
                    snapshot.vehicleTrackId,
                    snapshot.bounds,
                    snapshot.confidence,
                    effectiveConfidence,
                    snapshot.exitUrgency,
                    snapshot.predicted,
                    snapshot.missedUpdates,
                    snapshot.lastMeasurementTimestampNanos,
                    snapshotTimestampNanos,
                    snapshot.sourceIndex,
                    entity == null ? null : entity.acquisitionState()
            ));
        }
        return new VehicleTrackingFrame(
                sourceFrameId,
                sourceSequence,
                sourceTimestampNanos,
                sourceTimestampDomain,
                snapshotTimestampNanos,
                observedContext==null?sceneGeneration:observedContext.sceneGeneration,
                observedContext==null?0L:observedContext.visualEpoch,
                observedContext==null?0L:observedContext.cameraTransformGeneration,
                candidates
        );
    }

    private VehicleTrackingFrame withCurrentAcquisitionStates(
            VehicleTrackingFrame source
    ) {
        List<VehicleCandidate> refreshed = new ArrayList<>(source.candidates.size());
        boolean changed = false;
        for (VehicleCandidate candidate : source.candidates) {
            com.example.alpr_v1.domain.VehicleEntity entity = repository.get(
                    candidate.entityId
            );
            if (entity == null) {
                changed = true;
                continue;
            }
            com.example.alpr_v1.domain.EntityAcquisitionState currentState =
                    entity.acquisitionState();
            if (currentState != candidate.acquisitionState) changed = true;
            refreshed.add(new VehicleCandidate(
                    candidate.entityId,
                    candidate.vehicleTrackId,
                    candidate.bounds,
                    candidate.detectionConfidence,
                    candidate.effectiveConfidence,
                    candidate.exitUrgency,
                    candidate.predicted,
                    candidate.missedUpdates,
                    candidate.lastMeasurementTimestampNanos,
                    candidate.snapshotTimestampNanos,
                    candidate.sourceIndex,
                    currentState
            ));
        }
        if (!changed) return source;
        return new VehicleTrackingFrame(
                source.sourceFrameId,
                source.sourceSequence,
                source.sourceTimestampNanos,
                source.sourceTimestampDomain,
                source.snapshotTimestampNanos,
                source.sceneGeneration,
                source.visualEpoch,
                source.cameraTransformGeneration,
                refreshed
        );
    }

    static float effectiveConfidence(
            VehicleTrackManager.Snapshot snapshot,
            long snapshotTimestampNanos
    ) {
        long ageNanos = Math.max(
                0L, snapshotTimestampNanos - snapshot.lastMeasurementTimestampNanos
        );
        float agePenalty = Math.max(
                0.10f,
                1f - ageNanos / (float) CONFIDENCE_DECAY_HORIZON_NANOS
        );
        int missIndex = Math.min(
                snapshot.missedUpdates, MISSED_UPDATE_PENALTIES.length - 1
        );
        float missedPenalty = MISSED_UPDATE_PENALTIES[missIndex];
        float seconds = ageNanos / 1_000_000_000f;
        float speed = (float) Math.hypot(
                snapshot.motion.velocityX, snapshot.motion.velocityY
        );
        float motionPenalty = 1f / (1f + 1.5f * speed * seconds);
        return clamp01(
                snapshot.confidence * agePenalty * missedPenalty * motionPenalty
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static long adaptiveTrackTtl(long observationGapNanos) {
        long proposed = (long) Math.ceil(
                Math.max(0L, observationGapNanos) * TRACK_TTL_GAP_MULTIPLIER
        );
        return Math.max(
                MIN_ADAPTIVE_TRACK_TTL_NANOS,
                Math.min(MAX_ADAPTIVE_TRACK_TTL_NANOS, proposed)
        );
    }
}
