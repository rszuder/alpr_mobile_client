package com.example.alpr_v1.pipeline;

/**
 * Centralny, testowalny budżet MT. Jedno wywołanie {@link #plan(Input)} zwraca
 * co najwyżej jeden crop do wykonania dla danej klatki pipeline'u.
 */
public final class MtInferenceScheduler {
    public static final int DEFAULT_REFRESH_FRAMES = 3;
    public static final float QUALITY_OK = 0.75f;
    public static final float QUALITY_INVALID = 0.45f;

    public enum Kind { SKIP, TARGET_ROI, VEHICLE_ROI, FULL_FRAME }

    public static final class Input {
        public final long frameId;
        public final boolean targetGeometryAvailable;
        public final TargetSnapshot.State targetState;
        public final float trackingQuality;
        public final int consecutiveFailures;
        public final boolean sceneChanged;
        public final boolean rapidCameraMotion;
        public final boolean cameraTransformInProgress;
        public final int vehicleRegionCount;

        public Input(
                long frameId,
                boolean targetGeometryAvailable,
                TargetSnapshot.State targetState,
                float trackingQuality,
                int consecutiveFailures,
                boolean sceneChanged,
                boolean rapidCameraMotion,
                boolean cameraTransformInProgress,
                int vehicleRegionCount
        ) {
            this.frameId = frameId;
            this.targetGeometryAvailable = targetGeometryAvailable;
            this.targetState = targetState == null
                    ? TargetSnapshot.State.SEARCHING : targetState;
            this.trackingQuality = clamp01(trackingQuality);
            this.consecutiveFailures = Math.max(0, consecutiveFailures);
            this.sceneChanged = sceneChanged;
            this.rapidCameraMotion = rapidCameraMotion;
            this.cameraTransformInProgress = cameraTransformInProgress;
            this.vehicleRegionCount = Math.max(0, vehicleRegionCount);
        }
    }

    public static final class Decision {
        public final Kind kind;
        public final String reason;
        public final int vehicleRegionIndex;
        public final int recoveryLevel;
        public final float targetMargin;
        public final long vehicleEntityId;
        public final long acquisitionDirectiveRevision;
        public final MtReason mtReason;

        private Decision(
                Kind kind,
                String reason,
                int vehicleRegionIndex,
                int recoveryLevel,
                float targetMargin,
                long vehicleEntityId,
                long acquisitionDirectiveRevision,
                MtReason mtReason
        ) {
            this.kind = kind;
            this.reason = reason;
            this.vehicleRegionIndex = vehicleRegionIndex;
            this.recoveryLevel = recoveryLevel;
            this.targetMargin = Math.max(0f, targetMargin);
            this.vehicleEntityId = Math.max(0L, vehicleEntityId);
            this.acquisitionDirectiveRevision = Math.max(
                    0L, acquisitionDirectiveRevision
            );
            this.mtReason = mtReason == null ? MtReason.UNKNOWN : mtReason;
        }

        public boolean runsMt() {
            return kind != Kind.SKIP;
        }
    }

    private final int refreshFrames;
    private long lastMtFrame = Long.MIN_VALUE;
    private int nextVehicleRegion;
    private int pendingRecoveryLevel;
    private String forcedReason;
    private long requestedVehicleEntityId;
    private long requestedDirectiveRevision;
    private MtReason requestedMtReason = MtReason.UNKNOWN;
    private long lastAcquisitionDirectiveRevision;

    public MtInferenceScheduler() {
        this(DEFAULT_REFRESH_FRAMES);
    }

    MtInferenceScheduler(int refreshFrames) {
        this.refreshFrames = Math.max(1, refreshFrames);
    }

    public synchronized Decision plan(Input input) {
        if (input == null) {
            return fullFrame("missing_scheduler_input", 3);
        }

        if (requestedVehicleEntityId > 0L) {
            long entityId = requestedVehicleEntityId;
            long directiveRevision = requestedDirectiveRevision;
            MtReason mtReason = requestedMtReason;
            requestedVehicleEntityId = 0L;
            requestedDirectiveRevision = 0L;
            requestedMtReason = MtReason.UNKNOWN;
            return exactVehicle(
                    entityId,
                    mtReason,
                    directiveRevision
            );
        }

        if (input.sceneChanged) {
            pendingRecoveryLevel = 0;
            return search(input, "scene_change");
        }

        if (forcedReason != null) {
            String reason = forcedReason;
            forcedReason = null;
            return input.targetGeometryAvailable
                    ? target(reason, 1, margin(input.trackingQuality))
                    : search(input, reason);
        }

        if (input.targetGeometryAvailable) {
            if (pendingRecoveryLevel == 2) {
                return target("recovery_expanded_target", 2, 0.45f);
            }
            if (pendingRecoveryLevel == 3) {
                return fullFrame("recovery_full_frame", 3);
            }
            if (pendingRecoveryLevel == 4 && input.vehicleRegionCount > 0) {
                return vehicle(input.vehicleRegionCount, "recovery_mp_roi", 4);
            }
            if (pendingRecoveryLevel == 4) {
                return fullFrame("recovery_mp_no_roi_full_frame", 3);
            }
            if (input.cameraTransformInProgress) {
                return target("camera_transform", 1, margin(input.trackingQuality));
            }
            if (input.rapidCameraMotion) {
                return target("rapid_camera_motion", 1, margin(input.trackingQuality));
            }
            if (input.consecutiveFailures > 0
                    || input.trackingQuality < QUALITY_INVALID
                    || input.targetState == TargetSnapshot.State.LOST) {
                return target("tracking_invalid", 1, 0.45f);
            }
            if (input.trackingQuality < QUALITY_OK
                    || input.targetState == TargetSnapshot.State.DEGRADED) {
                return target("tracking_degraded", 1, margin(input.trackingQuality));
            }
            if (lastMtFrame == Long.MIN_VALUE
                    || input.frameId - lastMtFrame >= refreshFrames) {
                return target("periodic_refresh", 1, margin(input.trackingQuality));
            }
            return new Decision(
                    Kind.SKIP, "healthy_tracker", -1, 0, 0f,
                    0L, 0L, MtReason.UNKNOWN
            );
        }

        if (pendingRecoveryLevel == 3) {
            return fullFrame("deferred_full_frame_fallback", 3);
        }
        return search(input, "searching");
    }

    public synchronized void onMtResult(
            Decision decision,
            long frameId,
            boolean plateFound
    ) {
        if (decision == null || !decision.runsMt()) return;
        lastMtFrame = frameId;
        if (decision.vehicleEntityId > 0L) {
            pendingRecoveryLevel = 0;
            return;
        }
        if (plateFound) {
            pendingRecoveryLevel = 0;
            return;
        }
        switch (decision.kind) {
            case TARGET_ROI:
                pendingRecoveryLevel = decision.recoveryLevel <= 1 ? 2 : 3;
                break;
            case VEHICLE_ROI:
                // Pełna klatka jest odroczona, nigdy wykonywana w tym samym przebiegu.
                pendingRecoveryLevel = 3;
                break;
            case FULL_FRAME:
                pendingRecoveryLevel = 4;
                break;
            case SKIP:
            default:
                break;
        }
    }

    public synchronized void reset() {
        lastMtFrame = Long.MIN_VALUE;
        nextVehicleRegion = 0;
        pendingRecoveryLevel = 0;
        forcedReason = null;
        requestedVehicleEntityId = 0L;
        requestedDirectiveRevision = 0L;
        requestedMtReason = MtReason.UNKNOWN;
        lastAcquisitionDirectiveRevision = 0L;
    }

    public synchronized void forceRefresh(String reason) {
        forcedReason = reason == null || reason.trim().isEmpty()
                ? "forced_refresh" : reason.trim();
    }

    public synchronized void requestVehicleEntity(
            long entityId,
            MtReason reason,
            long directiveRevision
    ) {
        if (entityId <= 0L) throw new IllegalArgumentException("entityId");
        if (directiveRevision <= 0L) {
            throw new IllegalArgumentException("directiveRevision");
        }
        if (directiveRevision <= lastAcquisitionDirectiveRevision) return;
        lastAcquisitionDirectiveRevision = directiveRevision;
        requestedVehicleEntityId = entityId;
        requestedDirectiveRevision = directiveRevision;
        requestedMtReason = reason == null ? MtReason.SCAN_NEXT_CANDIDATE : reason;
    }

    public synchronized void clearVehicleEntityRequest() {
        requestedVehicleEntityId = 0L;
        requestedDirectiveRevision = 0L;
        requestedMtReason = MtReason.UNKNOWN;
    }

    public synchronized boolean requiresVehicleRecovery() {
        return pendingRecoveryLevel == 4;
    }

    private Decision search(Input input, String reason) {
        if (input.vehicleRegionCount > 0) {
            return vehicle(input.vehicleRegionCount, reason + "_vehicle_roi", 4);
        }
        return fullFrame(reason + "_full_frame", 3);
    }

    private Decision vehicle(int regionCount, String reason, int recoveryLevel) {
        int index = Math.floorMod(nextVehicleRegion, Math.max(1, regionCount));
        nextVehicleRegion = (index + 1) % Math.max(1, regionCount);
        return new Decision(
                Kind.VEHICLE_ROI, reason, index, recoveryLevel, 0f,
                0L, 0L, MtReason.UNKNOWN
        );
    }

    private static Decision exactVehicle(
            long entityId,
            MtReason reason,
            long directiveRevision
    ) {
        MtReason safeReason = reason == null
                ? MtReason.SCAN_NEXT_CANDIDATE : reason;
        float margin = safeReason == MtReason.SCAN_EXPANDED_ENTITY_ROI
                ? 0.18f : 0f;
        return new Decision(
                Kind.VEHICLE_ROI,
                safeReason.name().toLowerCase(java.util.Locale.ROOT),
                -1,
                0,
                margin,
                entityId,
                directiveRevision,
                safeReason
        );
    }

    private static Decision target(String reason, int recoveryLevel, float margin) {
        return new Decision(
                Kind.TARGET_ROI, reason, -1, recoveryLevel, margin,
                0L, 0L, MtReason.UNKNOWN
        );
    }

    private static Decision fullFrame(String reason, int recoveryLevel) {
        return new Decision(
                Kind.FULL_FRAME, reason, -1, recoveryLevel, 0f,
                0L, 0L, MtReason.UNKNOWN
        );
    }

    private static float margin(float quality) {
        if (quality > 0.80f) return 0.20f;
        if (quality >= 0.60f) return 0.30f;
        return 0.45f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
