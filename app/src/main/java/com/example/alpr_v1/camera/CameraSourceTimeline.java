package com.example.alpr_v1.camera;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

/** Single owner of the monotonically increasing CameraX source sequence. */
public final class CameraSourceTimeline {
    private final CameraTimestampSource cameraTimestampSource;
    private long latestCameraTimestampNanos;
    private long latestSourceSequence;

    public CameraSourceTimeline() {
        this(CameraTimestampSource.UNKNOWN);
    }

    public CameraSourceTimeline(CameraTimestampSource cameraTimestampSource) {
        this.cameraTimestampSource = cameraTimestampSource == null
                ? CameraTimestampSource.UNAVAILABLE : cameraTimestampSource;
    }

    public synchronized SourceFrameStamp observeCameraFrame(long timestampNanos) {
        latestSourceSequence++;
        latestCameraTimestampNanos = Math.max(0L, timestampNanos);
        return new SourceFrameStamp(
                latestSourceSequence,
                latestCameraTimestampNanos,
                SourceTimestampDomain.CAMERAX_SENSOR,
                0L, 0L, 0L
        );
    }

    public synchronized SourceFrameStamp current(
            long sceneGeneration,
            long visualEpoch,
            long cameraTransformGeneration
    ) {
        if (latestSourceSequence <= 0L) {
            return SourceFrameStamp.unknown(
                    sceneGeneration,
                    visualEpoch,
                    cameraTransformGeneration
            );
        }
        return new SourceFrameStamp(
                latestSourceSequence,
                latestCameraTimestampNanos,
                SourceTimestampDomain.PREVIEW_INHERITED_CAMERA,
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration
        );
    }

    public synchronized long latestCameraTimestampNanos() {
        return latestCameraTimestampNanos;
    }

    public synchronized long latestSourceSequence() {
        return latestSourceSequence;
    }

    public CameraTimestampSource cameraTimestampSource() {
        return cameraTimestampSource;
    }

    public synchronized void reset() {
        latestCameraTimestampNanos = 0L;
        latestSourceSequence = 0L;
    }
}
