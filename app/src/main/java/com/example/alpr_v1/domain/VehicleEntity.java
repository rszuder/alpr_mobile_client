package com.example.alpr_v1.domain;

/**
 * Durable domain identity joining a vehicle, its plate, recognition and crops.
 * Technical MP/MT track ids may change without changing {@code entityId}.
 */
public final class VehicleEntity {
    private final long entityId;
    private long vehicleTrackId;
    private Long plateTrackId;
    private NormalizedBounds vehicleBounds;
    private NormalizedQuad plateQuad;
    private MotionState motion = MotionState.STATIONARY;
    private AppearanceDescriptor vehicleAppearance = new AppearanceDescriptor(null);
    private AppearanceDescriptor plateAppearance = new AppearanceDescriptor(null);
    private PlateTextConsensus registration = PlateTextConsensus.EMPTY;
    private RegistrationConsensusSource registrationSource =
            RegistrationConsensusSource.NONE;
    private EntityAcquisitionState acquisitionState = EntityAcquisitionState.NEW;
    private SearchMatchState searchMatchState = SearchMatchState.NOT_EVALUATED;
    private PersistentLockIdentityState lockIdentityState =
            PersistentLockIdentityState.UNIDENTIFIED;
    private CropReference bestWidePlateCrop;
    private CropReference bestZoomPlateCrop;
    private CropReference bestVehicleContextCrop;
    private final long firstSeenNanos;
    private long lastSeenNanos;
    private long lastMpNanos;
    private long lastMtSourceTimestampNanos;
    private long lastFreshMzNanos;
    private long lastConsensusUpdateNanos;
    private int mtAttempts;
    private int mzAttempts;
    private boolean acquisitionCompleted;
    private boolean queued;
    private boolean activeTarget;

    VehicleEntity(
            long entityId,
            long vehicleTrackId,
            NormalizedBounds vehicleBounds,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        if (entityId <= 0L) throw new IllegalArgumentException("entityId must be positive");
        this.entityId = entityId;
        this.vehicleTrackId = Math.max(0L, vehicleTrackId);
        this.vehicleBounds = vehicleBounds;
        this.vehicleAppearance = appearance == null
                ? new AppearanceDescriptor(null) : appearance;
        this.firstSeenNanos = Math.max(0L, nowNanos);
        this.lastSeenNanos = this.firstSeenNanos;
        this.lastMpNanos = this.firstSeenNanos;
    }

    public synchronized long entityId() { return entityId; }
    public synchronized long vehicleTrackId() { return vehicleTrackId; }
    public synchronized Long plateTrackId() { return plateTrackId; }
    public synchronized NormalizedBounds vehicleBounds() { return vehicleBounds; }
    public synchronized NormalizedQuad plateQuad() { return plateQuad; }
    public synchronized MotionState motion() { return motion; }
    public synchronized AppearanceDescriptor vehicleAppearance() { return vehicleAppearance; }
    public synchronized AppearanceDescriptor plateAppearance() { return plateAppearance; }
    public synchronized PlateTextConsensus registration() { return registration; }
    public synchronized RegistrationConsensusSource registrationSource() {
        return registrationSource;
    }
    public synchronized EntityAcquisitionState acquisitionState() { return acquisitionState; }
    public synchronized SearchMatchState searchMatchState() { return searchMatchState; }
    public synchronized PersistentLockIdentityState lockIdentityState() { return lockIdentityState; }
    public synchronized CropReference bestWidePlateCrop() { return bestWidePlateCrop; }
    public synchronized CropReference bestZoomPlateCrop() { return bestZoomPlateCrop; }
    public synchronized CropReference bestVehicleContextCrop() { return bestVehicleContextCrop; }
    public synchronized long firstSeenNanos() { return firstSeenNanos; }
    public synchronized long lastSeenNanos() { return lastSeenNanos; }
    public synchronized long lastMpNanos() { return lastMpNanos; }
    public synchronized long lastMtSourceTimestampNanos() {
        return lastMtSourceTimestampNanos;
    }
    /** Historical accessor retained for callers that mean the last real MZ attempt. */
    public synchronized long lastMzNanos() { return lastFreshMzNanos; }
    public synchronized long lastFreshMzNanos() { return lastFreshMzNanos; }
    public synchronized long lastConsensusUpdateNanos() {
        return lastConsensusUpdateNanos;
    }
    public synchronized int mtAttempts() { return mtAttempts; }
    public synchronized int mzAttempts() { return mzAttempts; }
    public synchronized boolean acquisitionCompleted() { return acquisitionCompleted; }
    public synchronized boolean queued() { return queued; }
    public synchronized boolean activeTarget() { return activeTarget; }

    synchronized void updateFromMp(
            long trackId,
            NormalizedBounds bounds,
            MotionState motion,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        vehicleTrackId = Math.max(0L, trackId);
        if (bounds != null) vehicleBounds = bounds;
        if (motion != null) this.motion = motion;
        if (appearance != null && appearance.available()) vehicleAppearance = appearance;
        lastSeenNanos = Math.max(lastSeenNanos, nowNanos);
        lastMpNanos = Math.max(lastMpNanos, nowNanos);
    }

    synchronized void attachPlate(
            long trackId,
            NormalizedQuad quad,
            AppearanceDescriptor appearance,
            long nowNanos
    ) {
        plateTrackId = trackId > 0L ? trackId : null;
        if (quad != null) plateQuad = quad;
        if (appearance != null && appearance.available()) plateAppearance = appearance;
        mtAttempts++;
        lastMtSourceTimestampNanos = Math.max(
                lastMtSourceTimestampNanos, nowNanos
        );
        lastSeenNanos = Math.max(lastSeenNanos, nowNanos);
        acquisitionState = EntityAcquisitionState.advance(
                acquisitionState,
                EntityAcquisitionState.PLATE_LOCALIZED
        );
    }

    synchronized void detachPlateTrack(long expectedTrackId) {
        if (plateTrackId != null && plateTrackId == expectedTrackId) {
            plateTrackId = null;
        }
    }

    synchronized void recordMtAttempt(long nowNanos) {
        mtAttempts++;
        lastMtSourceTimestampNanos = Math.max(
                lastMtSourceTimestampNanos, nowNanos
        );
    }

    synchronized boolean updateRegistration(
            PlateTextConsensus consensus,
            long nowNanos,
            boolean freshMzAttempted,
            RegistrationConsensusSource source
    ) {
        PlateTextConsensus incoming = consensus == null
                ? PlateTextConsensus.EMPTY : consensus;
        if (freshMzAttempted) {
            mzAttempts++;
            lastFreshMzNanos = Math.max(lastFreshMzNanos, nowNanos);
        }
        if (!shouldAdoptConsensus(registration, incoming)) return false;
        registration = incoming;
        registrationSource = source == null
                ? RegistrationConsensusSource.NONE : source;
        lastConsensusUpdateNanos = Math.max(lastConsensusUpdateNanos, nowNanos);
        acquisitionState = EntityAcquisitionState.advance(
                acquisitionState,
                registration.stable
                        ? EntityAcquisitionState.READY_TO_FINALIZE
                        : EntityAcquisitionState.READING_REGISTRATION
        );
        lockIdentityState = registration.stable
                ? PersistentLockIdentityState.IDENTIFIED
                : registration.available()
                        ? PersistentLockIdentityState.PARTIALLY_IDENTIFIED
                        : PersistentLockIdentityState.UNIDENTIFIED;
        return true;
    }

    private static boolean shouldAdoptConsensus(
            PlateTextConsensus current,
            PlateTextConsensus incoming
    ) {
        if (incoming == null || !incoming.available()) return false;
        if (current == null || !current.available()) return true;
        if (current.stable && !incoming.stable) return false;
        if (incoming.stable && !current.stable) return true;
        if (current.text.equals(incoming.text)) {
            return incoming.observations >= current.observations
                    || incoming.confidence >= current.confidence;
        }
        return incoming.confidence > current.confidence
                && incoming.observations >= current.observations;
    }

    synchronized void considerCrop(CropReference crop) {
        if (crop == null) return;
        switch (crop.kind) {
            case ZOOM_PLATE:
                if (crop.betterThan(bestZoomPlateCrop)) bestZoomPlateCrop = crop;
                break;
            case VEHICLE_CONTEXT:
                if (crop.betterThan(bestVehicleContextCrop)) bestVehicleContextCrop = crop;
                break;
            case WIDE_PLATE:
            default:
                if (crop.betterThan(bestWidePlateCrop)) bestWidePlateCrop = crop;
                break;
        }
    }

    synchronized void setAcquisitionState(EntityAcquisitionState state) {
        acquisitionState = EntityAcquisitionState.advance(acquisitionState, state);
        queued = acquisitionState == EntityAcquisitionState.QUEUED;
    }

    synchronized void setSearchMatchState(SearchMatchState state) {
        searchMatchState = state == null ? SearchMatchState.NOT_EVALUATED : state;
    }

    synchronized void setActiveTarget(boolean activeTarget) {
        this.activeTarget = activeTarget;
        if (activeTarget) queued = false;
    }

    synchronized void markAcquired() {
        acquisitionCompleted = true;
        queued = false;
        activeTarget = false;
        acquisitionState = EntityAcquisitionState.ACQUIRED;
    }

    synchronized void expire() {
        queued = false;
        activeTarget = false;
        acquisitionState = EntityAcquisitionState.EXPIRED;
    }
}
