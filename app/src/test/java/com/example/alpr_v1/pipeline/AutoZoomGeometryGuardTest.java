package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AutoZoomGeometryGuardTest {
    static ContinuityStamp stamp(long scene, long visual, long transform) {
        return new ContinuityStamp(scene, visual, transform, 12, 1000, SourceTimestampDomain.UNKNOWN);
    }
    static Detection box(float l, float t, float r, float b) {
        return new Detection(0,.99f,l,t,r,b,Arrays.asList(
                new Point2(l,t),new Point2(r,t),new Point2(r,b),new Point2(l,b)));
    }
    static Detection renaultBaseline() {
        return new Detection(0,.9731926f,412.01065f,685.7186f,641.8999f,722.8786f,Arrays.asList(
                new Point2(413.016f,683.772f),new Point2(643.78f,685.22003f),
                new Point2(642.0453f,722.97034f),new Point2(413.56412f,721.734f)));
    }
    static Detection renaultZoom() {
        return new Detection(0,.92711794f,350.21292f,718.56195f,716.7258f,808.3356f,Arrays.asList(
                new Point2(348.75363f,712.1186f),new Point2(734.51f,717.2377f),
                new Point2(733.8212f,809.3462f),new Point2(343.34094f,801.70526f)));
    }
    private AutoZoomGeometryGuard ready() {
        AutoZoomGeometryGuard g = new AutoZoomGeometryGuard();
        g.remember(1,7,box(400,500,600,550),1000,1000,stamp(1,1,0),0);
        g.cameraTransform(1.8f);
        return g;
    }
    private Detection choose(AutoZoomGeometryGuard g, Detection d) {
        return g.select(d,2,7,1000,1000,stamp(1,1,1),100).detection;
    }
    @Test public void recordedRenaultZoomUsesWholeBaselineQuadWithNewTrack() {
        AutoZoomGeometryGuard g = new AutoZoomGeometryGuard();
        Detection baseline=renaultBaseline(), raw=renaultZoom();
        g.remember(1,7,baseline,960,1280,stamp(1,1,0),0);
        g.cameraTransform(1.8f);
        AutoZoomGeometryGuard.Result result=g.select(raw,9,7,960,1280,stamp(1,1,1),100);
        assertNotSame(raw,result.detection);
        assertEquals(774.804f,result.detection.keypoints.get(1).x,.001f);
        assertEquals(12,result.referenceSourceSequence);
        assertEquals(716.7258f,raw.right,.001f);
        assertEquals(raw.confidence,result.detection.confidence,0f);
        assertEquals(643.78f,baseline.keypoints.get(1).x,.001f);
    }
    @Test public void keepsReferenceAcrossRepeatedBadHighConfidenceDetections() {
        AutoZoomGeometryGuard g=ready();
        assertEquals(680,choose(g,box(320,500,645,590)).right,.001f);
        // A late observation cannot overwrite the frozen reference during AZ.
        g.remember(2,7,box(320,500,645,590),1000,1000,stamp(1,1,1),101);
        assertEquals(680,choose(g,box(320,500,620,590)).right,.001f);
    }
    @Test public void leftSuffixAndRightSuffixUseSamePolicy() {
        AutoZoomGeometryGuard g=ready();
        assertEquals(320,choose(g,box(360,500,680,590)).left,.001f);
        assertEquals(680,choose(g,box(320,500,640,590)).right,.001f);
    }
    @Test public void acceptsHealthyNewGeometryAndSmallJitter() {
        AutoZoomGeometryGuard g=ready();
        for (Detection raw:Arrays.asList(box(318,498,683,592),box(325,501,675,588)))
            assertSame(raw,choose(g,raw));
    }
    @Test public void uniformShrinkingAndTargetMotionAreNotCorrected() {
        AutoZoomGeometryGuard g=ready();
        for (Detection raw:Arrays.asList(box(350,508,650,582),box(380,500,700,590),box(320,560,640,650)))
            assertSame(raw,choose(g,raw));
    }
    @Test public void requiresSameSceneVisualEpochAndNewCameraTransform() {
        Detection raw=box(320,500,640,590);
        for (ContinuityStamp s:Arrays.asList(stamp(2,1,1),stamp(1,2,1),stamp(1,1,0)))
            assertSame(raw,ready().select(raw,2,7,1000,1000,s,100).detection);
    }
    @Test public void rejectsOtherOwnerAndExpiredReference() {
        Detection raw=box(320,500,640,590);
        assertSame(raw,ready().select(raw,2,8,1000,1000,stamp(1,1,1),100).detection);
        assertSame(raw,ready().select(raw,2,7,1000,1000,stamp(1,1,1),31_000_000_000L).detection);
    }
    @Test public void unknownOwnerRequiresStableTrack() {
        AutoZoomGeometryGuard g=new AutoZoomGeometryGuard();
        g.remember(1,0,box(400,500,600,550),1000,1000,stamp(1,1,0),0);
        g.cameraTransform(1.8f);
        Detection raw=box(320,500,640,590);
        assertSame(raw,g.select(raw,2,0,1000,1000,stamp(1,1,1),100).detection);
        assertNotSame(raw,g.select(raw,1,0,1000,1000,stamp(1,1,1),100).detection);
    }
    @Test public void ambiguousReferenceDoesNotGuessWhichPlate() {
        AutoZoomGeometryGuard g=new AutoZoomGeometryGuard();
        g.remember(1,7,box(400,500,600,550),1000,1000,stamp(1,1,0),0);
        g.remember(2,7,box(405,500,605,550),1000,1000,stamp(1,1,0),0);
        g.cameraTransform(1.8f);
        Detection raw=box(320,500,640,590);
        assertSame(raw,choose(g,raw));
    }
    @Test public void returningAndResetDisableReference() {
        Detection raw=box(320,500,640,590);
        AutoZoomGeometryGuard g=ready();g.cameraTransform(1/1.8f);
        assertSame(raw,choose(g,raw));
        g=ready();g.clear();assertSame(raw,choose(g,raw));
        g=new AutoZoomGeometryGuard();
        g.remember(1,7,box(400,500,600,550),1000,1000,stamp(1,1,0),0);
        assertSame(raw,choose(g,raw));
    }
    @Test public void projectedQuadOutsideSensorCannotBeUsed() {
        AutoZoomGeometryGuard g=new AutoZoomGeometryGuard();
        g.remember(1,7,box(700,500,820,550),1000,1000,stamp(1,1,0),0);
        g.cameraTransform(1.8f);
        Detection raw=box(860,500,980,590);
        assertSame(raw,choose(g,raw));
    }
    @Test public void scanCompletionHandsBaselineToZoomButRealReleaseInvalidatesIt() {
        for (String reason:Arrays.asList("scan_read_captured","scan_ready_to_finalize")) {
            AutoZoomGeometryGuard g=new AutoZoomGeometryGuard();
            g.remember(1,7,box(400,500,600,550),1000,1000,stamp(1,1,0),0);
            g.releaseTarget(reason);
            g.cameraTransform(1.8f);
            Detection raw=box(320,500,640,590);
            assertNotSame(reason,raw,choose(g,raw));
            g.releaseTarget("foreground_released");
            assertSame(raw,choose(g,raw));
        }
    }
}
