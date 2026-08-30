package com.example.alpr_v1.camera;

import static org.junit.Assert.assertEquals;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

import org.junit.Test;

public final class CameraSourceTimelineTest {
    @Test
    public void directLumaAndPreviewInheritOneCameraFrameIdentity() {
        CameraSourceTimeline timeline = new CameraSourceTimeline(
                CameraTimestampSource.REALTIME
        );
        SourceFrameStamp camera = timeline.observeCameraFrame(8_000_000_000L);
        SourceFrameStamp preview = timeline.current(2L, 7L, 3L);

        assertEquals(1L, camera.sourceSequence);
        assertEquals(1L, preview.sourceSequence);
        assertEquals(camera.sourceTimestampNanos, preview.sourceTimestampNanos);
        assertEquals(SourceTimestampDomain.CAMERAX_SENSOR, camera.domain);
        assertEquals(SourceTimestampDomain.PREVIEW_INHERITED_CAMERA, preview.domain);
        assertEquals(2L, preview.sceneGeneration);
        assertEquals(CameraTimestampSource.REALTIME, timeline.cameraTimestampSource());
    }

    @Test
    public void arbitraryUnknownTimestampStillHasMonotonicSourceSequence() {
        CameraSourceTimeline timeline = new CameraSourceTimeline(
                CameraTimestampSource.UNKNOWN
        );

        SourceFrameStamp first = timeline.observeCameraFrame(91L);
        SourceFrameStamp second = timeline.observeCameraFrame(7L);

        assertEquals(1L, first.sourceSequence);
        assertEquals(2L, second.sourceSequence);
        assertEquals(7L, second.sourceTimestampNanos);
        assertEquals(CameraTimestampSource.UNKNOWN, timeline.cameraTimestampSource());
    }

    @Test
    public void previewBeforeFirstCameraFrameIsExplicitlyUnknown() {
        SourceFrameStamp preview = new CameraSourceTimeline().current(1L, 2L, 3L);

        assertEquals(0L, preview.sourceSequence);
        assertEquals(0L, preview.sourceTimestampNanos);
        assertEquals(SourceTimestampDomain.UNKNOWN, preview.domain);
    }
}
