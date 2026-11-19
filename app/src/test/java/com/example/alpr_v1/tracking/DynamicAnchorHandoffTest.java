package com.example.alpr_v1.tracking;

import android.graphics.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.ui.OverlayItem;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class DynamicAnchorHandoffTest {
    @Test public void cameraMotionRequiresTheExactPairOfActualSourceFrames() {
        SourceFrameStamp from=stamp(4),to=stamp(5);
        FrameMotionTransform motion=FrameMotionTransform.translation(.04f,0);
        assertTrue(new PreviewPlateTracker.CameraMotionSample(from,to,motion).matches(from,to));
        assertFalse(new PreviewPlateTracker.CameraMotionSample(stamp(3),to,motion).matches(from,to));
        SourceFrameStamp anotherScene=new SourceFrameStamp(to.sourceSequence,to.sourceTimestampNanos,to.domain,2,1,0);
        assertFalse(new PreviewPlateTracker.CameraMotionSample(from,anotherScene,motion).matches(from,to));
        SourceFrameStamp anotherSequence=new SourceFrameStamp(to.sourceSequence+1,to.sourceTimestampNanos,to.domain,1,1,0);
        assertFalse(new PreviewPlateTracker.CameraMotionSample(from,anotherSequence,motion).matches(from,to));
        assertFalse(new PreviewPlateTracker.CameraMotionSample(from,to,FrameMotionTransform.invalid()).matches(from,to));
        FrameMotionTransform weak=new FrameMotionTransform(true,1,0,.04f,0,1,0,3,1,FrameMotionQuality.unavailable(3));
        assertFalse(new PreviewPlateTracker.CameraMotionSample(from,to,weak).matches(from,to));
    }
    @Test public void cameraMotionCannotSubstituteTheMissingLocalPixels() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        for(int i=1;i<=5;i++)tracker.updateLuma(pixels(0),240,240,null,stamp(i),null);
        for(int i=6;i<=9;i++) {
            PreviewTrackingFrame frame=tracker.updateLuma(new byte[240*240],240,240,null,stamp(i),null,
                    new PreviewPlateTracker.CameraMotionSample(stamp(i-1),stamp(i),FrameMotionTransform.translation(.04f,0)));
            for(TrackedPlate item:frame.trackedPlates)assertFalse("camera motion is not object presence",VehicleTrackingCoordinator.validLocal(item));
        }
    }
    @Test public void localBodyAndNarrowPlateKeepGeometryDuringCameraTranslation() {
        for(boolean plate:new boolean[]{false,true}) {
            PreviewPlateTracker tracker=new PreviewPlateTracker();
            RectF bounds=plate?new RectF(68f/240,224f/320,108f/240,232f/320)
                    :new RectF(48f/240,160f/320,128f/240,240f/320);
            OverlayItem target=new OverlayItem(OverlayItem.Kind.PLATE,bounds,Arrays.asList(
                    new PointF(bounds.left,bounds.top),new PointF(bounds.right,bounds.top),
                    new PointF(bounds.right,bounds.bottom),new PointF(bounds.left,bounds.bottom)),"",1,false);
            tracker.anchorLuma(Collections.singletonList(target),240,320,Collections.emptyMap(),
                    cameraTranslationPixels(0),240,320,stamp(0));
            for(int i=1;i<=8;i++) {
                int dx=i<=4?0:(i-4)*10;
                int previousDx=i<=5?0:(i-5)*10;
                PreviewTrackingFrame frame=tracker.updateLuma(cameraTranslationPixels(dx),240,320,null,stamp(i),null,
                        new PreviewPlateTracker.CameraMotionSample(stamp(i-1),stamp(i),
                                FrameMotionTransform.translation((dx-previousDx)/240f,0)));
                assertNotNull(frame);assertEquals("plate="+plate+" step="+i,1,frame.trackedPlates.size());
                TrackedPlate live=frame.trackedPlates.get(0);
                assertTrue("local pixels must remain validated, plate="+plate+" step="+i,VehicleTrackingCoordinator.validLocal(live));
                assertEquals("plate="+plate+" step="+i+" app="+live.localAppearanceSimilarity
                        +" quality="+live.trackingQuality,bounds.centerX()+dx/240f,live.normalizedBounds.centerX(),.012f);
            }
        }
    }
    private static byte[] cameraTranslationPixels(int dx) {
        byte[] image=new byte[240*320];
        for(int y=0;y<320;y++)for(int x=0;x<240;x++) {
            int sourceX=x-dx;
            double value=110+23*Math.sin(sourceX*.13)+18*Math.cos(y*.17);
            if(sourceX>=48&&sourceX<128&&y>=160&&y<240)
                value=115+42*Math.sin(sourceX*.63)+35*Math.cos(y*.71)+30*Math.sin((sourceX+y)*.43);
            if(sourceX>=68&&sourceX<108&&y>=224&&y<232)
                value=135+85*Math.sin(sourceX*1.1)*Math.cos((y-224)*.5);
            image[y*240+x]=(byte)Math.max(0,Math.min(255,Math.round(value)));
        }
        return image;
    }
    @Test public void capturedIntermediateAppearanceCannotPoisonValidatedCurrentPixels() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        SourceFrameStamp initial=appearanceStamp(0);
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,initial);
        PreviewTrackingFrame before=null;
        for(int i=1;i<=32;i++) {
            history.offer(appearanceStamp(i),pixels(0),240,240);
            before=tracker.updateLuma(pixels(0),240,240,null,appearanceStamp(i),history);
        }
        assertTrue(VehicleTrackingCoordinator.validLocal(before.trackedPlates.get(0)));
        int age=before.trackedPlates.get(0).ageFrames;
        // A real captured sample between processed KLT frames can have a transient
        // appearance inconsistent with the current image (e.g. monitor moire).
        // Its delayed detector callback must not replace a still-valid prototype.
        SourceFrameStamp captured=new SourceFrameStamp(18,1_850_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,1,1,0);
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),invertedPixels(),240,240,captured);
        PreviewTrackingFrame next=tracker.updateLuma(pixels(0),240,240,null,appearanceStamp(33),history);
        assertEquals(1,next.trackedPlates.size());TrackedPlate body=next.trackedPlates.get(0);
        assertEquals(age+1,body.ageFrames);assertEquals(0,body.consecutiveFailures);
        assertTrue("a delayed template must not destroy current pixel proof",VehicleTrackingCoordinator.validLocal(body));
        assertEquals(captured.sourceTimestampNanos,body.anchorSourceTimestampNanos);
    }
    @Test public void retainedAppearanceStillRejectsARealChangeInCurrentPixels() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,appearanceStamp(0));
        for(int i=1;i<=20;i++)tracker.updateLuma(pixels(0),240,240,null,appearanceStamp(i),history);
        for(int i=21;i<=24;i++) {
            PreviewTrackingFrame changed=tracker.updateLuma(invertedPixels(),240,240,null,appearanceStamp(i),history);
            for(TrackedPlate item:changed.trackedPlates)assertFalse(VehicleTrackingCoordinator.validLocal(item));
        }
    }
    private static SourceFrameStamp appearanceStamp(int i) {
        return new SourceFrameStamp(2*i+1,1_000_000_000L+i*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,1,1,0);
    }
    private static byte[] invertedPixels() {
        byte[] image=pixels(0);
        for(int y=64;y<152;y++)for(int x=48;x<160;x++)image[y*240+x]=(byte)(255-(image[y*240+x]&255));
        return image;
    }
    @Test public void neuralCorrectionKeepsThePixelProofAlreadyAcceptedByTheIdentityCoordinator() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        PreviewTrackingFrame last=null;
        for(int i=1;i<=30;i++) {
            history.offer(stamp(i),pixels(0),240,240);last=tracker.updateLuma(pixels(0),240,240,null,stamp(i),history);
        }
        TrackedPlate actual=last.trackedPlates.get(0);
        TrackedPlate moderate=new TrackedPlate(actual.overlayItem,.9f,1,0,9,0,actual.ageFrames,actual.framesSinceMtAnchor,.5f,true,
                null,0,actual.identityOwner,actual.anchorSourceTimestampNanos);
        assertTrue(VehicleTrackingCoordinator.validLocal(moderate));
        Object state=((java.util.List<?>)org.robolectric.util.ReflectionHelpers.getField(tracker,"tracks")).get(0);
        org.robolectric.util.ReflectionHelpers.setField(state,"localAppearanceSimilarity",.5f);
        org.robolectric.util.ReflectionHelpers.setField(state,"localPixelValidated",VehicleTrackingCoordinator.validLocal(moderate));
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(8));
        PreviewTrackingFrame next=tracker.updateLuma(pixels(0),240,240,null,stamp(31),history);
        assertEquals(PreviewTrackingFrame.State.LIVE,next.state);assertEquals(actual.ageFrames+1,next.trackedPlates.get(0).ageFrames);
        assertEquals(stamp(8).sourceTimestampNanos,next.trackedPlates.get(0).anchorSourceTimestampNanos);
    }
    @Test public void coldCameraSampleGapsCanReplayOnlyWhenTheirActualPixelsMatch() {
        for(boolean matching:new boolean[]{true,false}) {
            PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
            SourceFrameStamp measured=stamp(0),current=measured;
            tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,measured);
            for(int i=1;i<=6;i++) {
                current=new SourceFrameStamp(i+1,measured.sourceTimestampNanos+i*675_000_000L,measured.domain,1,1,0);
                history.offer(current,matching?pixels(0):new byte[240*240],240,240);
            }
            PreviewTrackingFrame result=null;
            for(int i=0;i<12;i++) {
                current=new SourceFrameStamp(current.sourceSequence+1,current.sourceTimestampNanos+100_000_000L,current.domain,1,1,0);
                byte[] image=matching?pixels(0):new byte[240*240];history.offer(current,image,240,240);
                result=tracker.updateLuma(image,240,240,null,current,history);
                assertTrue(tracker.lastReplaySteps()<=1);
                if(result.state!=PreviewTrackingFrame.State.REPLAYING)break;
            }
            assertNotNull(result);
            if(matching) {
                assertEquals(PreviewTrackingFrame.State.LIVE,result.state);assertEquals(1,result.trackedPlates.size());
                assertTrue(result.trackedPlates.get(0).localAppearanceValidated);assertFalse(result.absenceEvaluated);
            } else assertTrue(result.trackedPlates.isEmpty());
        }
    }
    private static SourceFrameStamp stamp(int i) {
        return new SourceFrameStamp(i+1,1_000_000_000L+i*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,1,1,0);
    }
    private static OverlayItem box(float dx) {
        RectF b=new RectF((48+dx)/240,64f/240,(160+dx)/240,152f/240);
        return new OverlayItem(OverlayItem.Kind.PLATE,b,Arrays.asList(new PointF(b.left,b.top),new PointF(b.right,b.top),
                new PointF(b.right,b.bottom),new PointF(b.left,b.bottom)),"",1,false);
    }
    private static byte[] pixels(int dx) {
        byte[] gray=new byte[240*240];Arrays.fill(gray,(byte)40);
        for(int y=64;y<152;y++)for(int x=48;x<160;x++)
            gray[y*240+x+dx]=(byte)Math.max(0,Math.min(255,Math.round((float)(128+42*Math.sin(x*.19)
                    +36*Math.cos(y*.17)+27*Math.sin((x+y)*.11)+18*Math.cos(x*.07-y*.13)))));
        return gray;
    }
    @Test public void duplicateCallbacksAndDelayedCorrectionsDoNotRestartValidatedTracking() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0)));
        assertFalse(tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0)));
        PreviewTrackingFrame result=null;
        for(int i=1;i<=32;i++) {
            history.offer(stamp(i),pixels(i/2),240,240);
            result=tracker.updateLuma(pixels(i/2),240,240,null,stamp(i),history);
            assertNotNull(result);assertEquals(1,result.trackedPlates.size());
            assertEquals(stamp(i).sourceTimestampNanos,result.sourceStamp.sourceTimestampNanos);
        }
        int age=result.trackedPlates.get(0).ageFrames;
        // This MP arrived 2.4 seconds after capture. Its correction is two pixels right.
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(6)),240,240,Collections.emptyMap(),pixels(4),240,240,stamp(8)));
        history.offer(stamp(33),pixels(16),240,240);
        result=tracker.updateLuma(pixels(16),240,240,null,stamp(33),history);
        assertNotNull(result);TrackedPlate tracked=result.trackedPlates.get(0);
        assertEquals(age+1,tracked.ageFrames);
        assertEquals(stamp(8).sourceTimestampNanos,tracked.anchorSourceTimestampNanos);
        assertEquals(1,tracked.framesSinceMtAnchor);
        assertTrue(tracked.localAppearanceValidated);assertEquals(0,tracked.consecutiveFailures);
        assertEquals(122f/240,tracked.normalizedBounds.centerX(),.015f);
    }
    @Test public void missingPixelPathDoesNotDestroyTheCurrentTrack() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        for(int i=1;i<=20;i++) {
            history.offer(stamp(i),pixels(0),240,240);
            assertNotNull(tracker.updateLuma(pixels(0),240,240,null,stamp(i),history));
        }
        // Too old for the new local-motion segment, but the live vehicle remains visible.
        history.offer(stamp(35),pixels(0),240,240);
        tracker.updateLuma(pixels(0),240,240,null,stamp(35),history);
        tracker.anchorLuma(Collections.singletonList(box(2)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(10));
        history.offer(stamp(36),pixels(0),240,240);
        PreviewTrackingFrame result=tracker.updateLuma(pixels(0),240,240,null,stamp(36),history);
        assertNotNull(result);assertEquals(1,result.trackedPlates.size());
        assertEquals(stamp(0).sourceTimestampNanos,result.trackedPlates.get(0).anchorSourceTimestampNanos);
        assertTrue(result.trackedPlates.get(0).ageFrames>20);
    }
    @Test public void detectorTrackReplacementKeepsTheSameOwnersValidatedPixelPath() {
        ComplementaryVehicleContinuityTest.Fixture owner=new ComplementaryVehicleContinuityTest.Fixture();
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.singletonMap(1L,owner.owner),pixels(0),240,240,stamp(0));
        PreviewTrackingFrame result=null;
        for(int i=1;i<=32;i++) {
            history.offer(stamp(i),pixels(i/2),240,240);
            result=tracker.updateLuma(pixels(i/2),240,240,null,stamp(i),history);
        }
        int age=result.trackedPlates.get(0).ageFrames;
        OverlayItem measured=box(4);
        OverlayItem replacement=new OverlayItem(measured.kind,measured.normalizedBounds,measured.normalizedKeypoints,"",9,false);
        tracker.anchorLuma(Collections.singletonList(replacement),240,240,Collections.singletonMap(9L,owner.owner),pixels(4),240,240,stamp(8));
        result=tracker.updateLuma(pixels(16),240,240,null,stamp(33),history);
        assertNotNull(result);assertEquals(1,result.trackedPlates.size());
        TrackedPlate live=result.trackedPlates.get(0);
        assertEquals(9,live.trackId);assertEquals(age+1,live.ageFrames);
        assertEquals(stamp(8).sourceTimestampNanos,live.anchorSourceTimestampNanos);
        assertSame(owner.owner,live.identityOwner);assertEquals(0,live.consecutiveFailures);
        assertEquals(120f/240,live.normalizedBounds.centerX(),.015f);
    }
    @Test public void partialNeuralBoxCannotCollapseAnAlreadyValidatedFullPlate() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        PreviewTrackingFrame result=null;
        for(int i=1;i<=20;i++) {
            history.offer(stamp(i),pixels(0),240,240);
            result=tracker.updateLuma(pixels(0),240,240,null,stamp(i),history);
        }
        int age=result.trackedPlates.get(0).ageFrames;
        RectF part=new RectF(130f/240,90f/240,155f/240,120f/240);
        OverlayItem wrong=new OverlayItem(OverlayItem.Kind.PLATE,part,Arrays.asList(new PointF(part.left,part.top),new PointF(part.right,part.top),
                new PointF(part.right,part.bottom),new PointF(part.left,part.bottom)),"",1,false);
        tracker.anchorLuma(Collections.singletonList(wrong),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(10));
        result=tracker.updateLuma(pixels(0),240,240,null,stamp(21),history);
        assertNotNull(result);assertEquals(age+1,result.trackedPlates.get(0).ageFrames);
        assertEquals(112f/240,result.trackedPlates.get(0).normalizedBounds.width(),.015f);
        assertEquals(stamp(0).sourceTimestampNanos,result.trackedPlates.get(0).anchorSourceTimestampNanos);
    }
    @Test public void motionInterpolationNeverBridgesMissingPixelsOrAnotherScene() {
        LocalMotionHistory history=new LocalMotionHistory();
        history.record(stamp(0),RobustAffineTransform.Result.identity());
        history.record(stamp(2),new RobustAffineTransform.Result(true,1,0,8,0,1,4,9,0));
        RobustAffineTransform.Result middle=history.at(stamp(1));assertTrue(middle.valid);
        assertEquals(4,middle.tx,.001f);assertEquals(2,middle.ty,.001f);
        SparsePyramidalFlow.Point p=new SparsePyramidalFlow.Point(11,9);
        SparsePyramidalFlow.Point restored=middle.inverse().apply(middle.apply(p));assertEquals(p.x,restored.x,.001f);
        history.record(stamp(20),RobustAffineTransform.Result.identity());assertFalse(history.at(stamp(1)).valid);
        assertFalse(history.at(new SourceFrameStamp(21,stamp(20).sourceTimestampNanos,SourceTimestampDomain.CAMERAX_SENSOR,2,1,0)).valid);
    }
    @Test public void firstFlowAfterCameraSampleGapCannotReportAnEvaluatedAbsence() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        for(int i=1;i<=8;i++) {
            history.offer(stamp(i),pixels(0),240,240);
            assertNotNull(tracker.updateLuma(pixels(0),240,240,null,stamp(i),history));
        }
        PreviewTrackingFrame afterGap=tracker.updateLuma(pixels(0),240,240,null,stamp(18),history);
        assertNotNull(afterGap);assertFalse(afterGap.absenceEvaluated);
        PreviewTrackingFrame next=tracker.updateLuma(pixels(0),240,240,null,stamp(19),history);
        assertNotNull(next);assertTrue(next.absenceEvaluated);
    }
    @Test public void thirtyFpsDuringColdMpKeepsEnoughRealHistoryToBootstrapTheFirstOwner() {
        ComplementaryVehicleContinuityTest.Fixture owner=new ComplementaryVehicleContinuityTest.Fixture();
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        SourceFrameStamp measured=new SourceFrameStamp(1,1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,0,0,0);
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.singletonMap(1L,owner.owner),pixels(0),240,240,measured));
        SourceFrameStamp current=measured;
        for(int i=1;i<=150;i++) {
            current=new SourceFrameStamp(i+1,measured.sourceTimestampNanos+i*33_333_333L,measured.domain,0,0,0);
            history.offer(current,pixels(i/8),240,240);
        }
        assertTrue(history.retainedBytes()<=4L*1024*1024);assertTrue(history.retainedFrames()<=64);
        PreviewTrackingFrame frame=tracker.updateLuma(pixels(18),240,240,null,current,history);
        int updates=0;
        while(frame.state==PreviewTrackingFrame.State.REPLAYING&&updates++<16) {
            assertTrue(tracker.lastReplaySteps()<=1);assertFalse(frame.absenceEvaluated);
            current=new SourceFrameStamp(current.sourceSequence+1,current.sourceTimestampNanos+100_000_000L,current.domain,0,0,0);
            history.offer(current,pixels(18),240,240);
            frame=tracker.updateLuma(pixels(18),240,240,null,current,history);
        }
        assertTrue(updates>1);assertEquals(PreviewTrackingFrame.State.LIVE,frame.state);
        assertNotNull(frame);assertEquals(1,frame.trackedPlates.size());
        owner.c.observeLocalTargets(current.continuityStamp(),frame,true,null,false);
        assertTrue(owner.c.identity().accepts(owner.owner));assertEquals(1,owner.c.identity().ownerNumber(owner.id));
        assertEquals(1,owner.c.stats().entitiesCreated);assertTrue(owner.c.displaySnapshot(current.continuityStamp()).localGeometry.containsKey(owner.id));
    }
    @Test public void newSameOwnerMeasurementsDoNotStarveReplayOrBlockTheLiveVehicle() {
        PreviewPlateTracker body=new PreviewPlateTracker(),plate=new PreviewPlateTracker();
        PreviewLumaHistory history=new PreviewLumaHistory();
        body.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        PreviewTrackingFrame live=null;
        for(int i=1;i<=30;i++) {
            history.offer(stamp(i),pixels(0),240,240);
            live=body.updateLuma(pixels(0),240,240,null,stamp(i),history);
        }
        int bodyAge=live.trackedPlates.get(0).ageFrames;
        plate.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        int waiting=0;boolean caughtUp=false;
        for(int i=31;i<=46;i++) {
            history.offer(stamp(i),pixels(0),240,240);
            PreviewTrackingFrame currentBody=body.updateLuma(pixels(0),240,240,null,stamp(i),history);
            PreviewTrackingFrame currentPlate=plate.updateLuma(pixels(0),240,240,null,stamp(i),history);
            assertEquals(PreviewTrackingFrame.State.LIVE,currentBody.state);
            assertEquals(++bodyAge,currentBody.trackedPlates.get(0).ageFrames);
            assertTrue(plate.lastReplaySteps()<=1);
            if(currentPlate.state==PreviewTrackingFrame.State.LIVE){caughtUp=true;break;}
            assertEquals(PreviewTrackingFrame.State.REPLAYING,currentPlate.state);
            assertTrue(currentPlate.sourceStamp.sourceTimestampNanos<stamp(i).sourceTimestampNanos);
            waiting++;
            plate.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(i-2));
        }
        assertTrue(waiting>1);assertTrue(caughtUp);
    }
    @Test public void stopAndSourceContextChangeCancelPendingReplay() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        for(int i=1;i<=30;i++)history.offer(stamp(i),pixels(0),240,240);
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        assertEquals(PreviewTrackingFrame.State.REPLAYING,tracker.updateLuma(pixels(0),240,240,null,stamp(30),history).state);
        tracker.reset();
        assertEquals(PreviewTrackingFrame.State.UNAVAILABLE,tracker.updateLuma(pixels(0),240,240,null,stamp(31),history).state);
        tracker.anchorLuma(Collections.singletonList(box(0)),240,240,Collections.emptyMap(),pixels(0),240,240,stamp(0));
        assertEquals(PreviewTrackingFrame.State.REPLAYING,tracker.updateLuma(pixels(0),240,240,null,stamp(30),history).state);
        SourceFrameStamp changed=new SourceFrameStamp(32,stamp(31).sourceTimestampNanos,SourceTimestampDomain.CAMERAX_SENSOR,2,1,0);
        assertEquals(PreviewTrackingFrame.State.UNAVAILABLE,tracker.updateLuma(pixels(0),240,240,null,changed,history).state);
    }
}
