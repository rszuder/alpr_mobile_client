package com.example.alpr_v1.tracking;

import android.graphics.RectF;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.ui.OverlayItem;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class ComplementaryVehicleContinuityTest {
    @Test public void weakCurrentPixelsAreUnknownForIdentityAndTentativeForDisplay() {
        Fixture f=new Fixture();
        android.graphics.RectF bounds=new android.graphics.RectF(.1f,.1f,.9f,.9f);
        for(int i=0;i<=8;i++) {
            long time=2*S+i*200_000_000L;
            float dx=i*.002f;android.graphics.RectF b=new android.graphics.RectF(bounds);b.offset(dx,0);
            OverlayItem item=new OverlayItem(OverlayItem.Kind.VEHICLE,b,Arrays.asList(new android.graphics.PointF(b.left,b.top),
                    new android.graphics.PointF(b.right,b.top),new android.graphics.PointF(b.right,b.bottom),new android.graphics.PointF(b.left,b.bottom)),"",f.id,false);
            TrackedPlate pixels=new TrackedPlate(item,i==0?.9f:.6f,.8f,2,6,0,4,4,i==0?.95f:.55f,true,null,time,f.owner,S);
            ContinuityStamp source=new ContinuityStamp(0,0,0,20+i,time,SourceTimestampDomain.CAMERAX_SENSOR);
            f.c.observeLocalTargets(source,frame(pixels).withSourceStamp(source.sourceFrameStamp()),true,
                    frame().withSourceStamp(source.sourceFrameStamp()),true);
            assertTrue("weak flow must not mean absence",f.c.identity().accepts(f.owner));
            DynamicVehicleDisplaySnapshot shown=f.c.displaySnapshot(source);
            if(i>3) {
                assertFalse(shown.localGeometry.containsKey(f.id));assertTrue(shown.tentativeGeometry.containsKey(f.id));
                assertNotNull(shown.vehicles.get(f.id).plate);assertEquals(0,shown.vehicles.get(f.id).plateProofAtNanos);
                assertEquals(2,new com.example.alpr_v1.ui.DynamicPresentationController().geometry(Collections.emptyList(),shown,time).size());
            }
            assertEquals(S,f.c.repository().get(f.id).lastMpNanos());
            assertEquals(S,f.c.repository().get(f.id).lastMtSourceTimestampNanos());
        }
        DynamicVehicleDisplaySnapshot expired=f.c.displaySnapshot(new ContinuityStamp(0,0,0,90,4*S+200_000_000L,SourceTimestampDomain.CAMERAX_SENSOR));
        assertTrue(expired.tentativeGeometry.isEmpty());
        assertFalse(expired.localGeometry.containsKey(f.id));
    }
    @Test public void currentBodyPixelsKeepAttachedPlateGeometryBeyondTheMtRefreshInterval() {
        Fixture f=new Fixture();
        for(int i=2;i<=45;i++) {
            android.graphics.RectF box=new android.graphics.RectF(.12f,.1f,.92f,.9f);
            OverlayItem item=new OverlayItem(OverlayItem.Kind.VEHICLE,box,Arrays.asList(
                    new android.graphics.PointF(box.left,box.top),new android.graphics.PointF(box.right,box.top),
                    new android.graphics.PointF(box.right,box.bottom),new android.graphics.PointF(box.left,box.bottom)),"",f.id,false);
            TrackedPlate body=new TrackedPlate(item,.9f,1,0,9,0,4,4,.95f,true,null,i*S,f.owner,S);
            f.c.observeLocalTargets(stamp(i),frame(body).withSourceStamp(stamp(i).sourceFrameStamp()),true,null,false);
            DynamicVehicleDisplaySnapshot shown=f.c.displaySnapshot(stamp(i));
            assertNotNull("attached geometry at second "+i,shown.vehicles.get(f.id).plate);
            assertEquals(0,shown.vehicles.get(f.id).plateProofAtNanos);
            assertEquals(S,shown.vehicles.get(f.id).plateMeasuredAtNanos);
            assertEquals(S,f.c.repository().get(f.id).lastMpNanos());
        }
        assertFalse(f.c.displaySnapshot(stamp(46)).localGeometry.containsKey(f.id));
        assertNull(f.c.displaySnapshot(stamp(46)).vehicles.get(f.id).plate);
    }
    @Test public void plateProjectionUsesDisplayFreshnessWithoutExtendingFocusedOcrEvidence() {
        Fixture f=new Fixture();
        RectF box=new RectF(.12f,.1f,.92f,.9f);
        OverlayItem item=new OverlayItem(OverlayItem.Kind.VEHICLE,box,Arrays.asList(
                new android.graphics.PointF(box.left,box.top),new android.graphics.PointF(box.right,box.top),
                new android.graphics.PointF(box.right,box.bottom),new android.graphics.PointF(box.left,box.bottom)),"",f.id,false);
        TrackedPlate body=new TrackedPlate(item,.9f,1,0,9,0,4,4,.95f,true,null,8*S,f.owner,S);
        f.c.observeLocalTargets(stamp(8),frame(body).withSourceStamp(stamp(8).sourceFrameStamp()),true,null,false);
        long delayed=8*S+450_000_000L;
        ContinuityStamp camera=new ContinuityStamp(0,0,0,81,delayed,SourceTimestampDomain.CAMERAX_SENSOR);
        assertNull("450 ms pixels are too old for focused OCR",f.c.localTargetEvidence(f.id,delayed,0,0,0));
        DynamicVehicleDisplaySnapshot shown=f.c.displaySnapshot(camera);
        assertTrue(shown.localGeometry.containsKey(f.id));
        assertNotNull("the still-visible body must retain its attached plate",shown.vehicles.get(f.id).plate);
        assertEquals(0,shown.vehicles.get(f.id).plateProofAtNanos);
        assertEquals(S,shown.vehicles.get(f.id).plateMeasuredAtNanos);
        assertEquals(8*S,shown.vehicles.get(f.id).geometryAtNanos);
        assertEquals(2,new com.example.alpr_v1.ui.DynamicPresentationController().geometry(Collections.emptyList(),shown,delayed).size());
        assertEquals(S,f.c.repository().get(f.id).lastMtSourceTimestampNanos());
        ContinuityStamp expired=new ContinuityStamp(0,0,0,82,8*S+DynamicVehicleIdentity.COAST_NANOS+1,SourceTimestampDomain.CAMERAX_SENSOR);
        assertFalse(f.c.displaySnapshot(expired).localGeometry.containsKey(f.id));
        assertNull(f.c.displaySnapshot(expired).vehicles.get(f.id).plate);
        ContinuityStamp anotherContext=new ContinuityStamp(0,1,0,83,delayed,SourceTimestampDomain.CAMERAX_SENSOR);
        assertNull(f.c.displaySnapshot(anotherContext).vehicles.get(f.id).plate);
    }
    @Test public void mtPlateCanBeShownBeforeAnyOcrUsingCurrentValidatedBody() {
        VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();c.setNormalDynamicIdentity(true,S);
        VehicleTrackingFrame mp=c.updateFromMp(1,stamp(1).sourceFrameStamp(),S,Collections.singletonList(car(0)),Collections.emptySet(),S);
        long id=mp.candidates.get(0).entityId;DynamicVehicleIdentity.Stamp owner=c.identity().stamp(id,mp.candidates.get(0).vehicleTrackId);
        NormalizedQuad quad=new NormalizedQuad(new float[]{.4f,.6f,.6f,.6f,.6f,.65f,.4f,.65f});
        c.repository().attachPlate(id,10,quad,new AppearanceDescriptor(new float[]{1,0}),S);c.anchorIdentityPlate(owner,10,quad,stamp(1));
        android.graphics.RectF box=new android.graphics.RectF(.12f,.1f,.92f,.9f);
        OverlayItem item=new OverlayItem(OverlayItem.Kind.VEHICLE,box,Arrays.asList(
                new android.graphics.PointF(box.left,box.top),new android.graphics.PointF(box.right,box.top),
                new android.graphics.PointF(box.right,box.bottom),new android.graphics.PointF(box.left,box.bottom)),"",id,false);
        TrackedPlate body=new TrackedPlate(item,.9f,1,0,9,0,4,4,.95f,true,null,2*S,owner,S);
        c.observeLocalTargets(stamp(2),frame(body).withSourceStamp(stamp(2).sourceFrameStamp()),true,null,false);
        DynamicVehicleDisplaySnapshot snapshot=c.displaySnapshot(2*S);
        assertFalse(snapshot.vehicles.get(id).reading.available());
        assertNotNull(snapshot.vehicles.get(id).plate);assertEquals(.42f,snapshot.vehicles.get(id).plate.bounds().left,.001f);
        assertEquals(0,snapshot.vehicles.get(id).plateProofAtNanos);
        assertEquals(2,new com.example.alpr_v1.ui.DynamicPresentationController().geometry(Collections.emptyList(),snapshot,2*S).size());
        c.observeLocalTargets(stamp(3),frame(body).withSourceStamp(stamp(2).sourceFrameStamp()),true,null,false);
        assertFalse(c.displaySnapshot(3*S).localGeometry.containsKey(id));
    }
    static final long S=1_000_000_000L;
    static ContinuityStamp stamp(long frame) {return new ContinuityStamp(0,0,0,frame,frame*S,SourceTimestampDomain.CAMERAX_SENSOR);}
    static VehicleTrackManager.Observation car(int index) {
        return new VehicleTrackManager.Observation(new NormalizedBounds(.1f,.1f,.9f,.9f),.9f,new AppearanceDescriptor(new float[]{1,0}),index);
    }
    public static PreviewTrackingFrame frame(TrackedPlate... plates) {return new PreviewTrackingFrame(Arrays.asList(plates),0);}
    public static TrackedPlate tracked(long track,DynamicVehicleIdentity.Stamp owner,float x) {
        TrackedPlate item=new TrackedPlate(new OverlayItem(OverlayItem.Kind.PLATE,new RectF(x,.6f,x+.2f,.65f),
                Collections.emptyList(),"",track,false),.9f,1,0,6,0,3,3,.95f,true,new float[]{1,0},0,owner);
        return item;
    }
    static class Fixture {
        final VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();
        final long id;
        final DynamicVehicleIdentity.Stamp owner;
        Fixture() {
            c.setNormalDynamicIdentity(true,S);
            VehicleTrackingFrame initial=c.updateFromMp(1,stamp(1).sourceFrameStamp(),S,Collections.singletonList(car(0)),Collections.emptySet(),S);
            id=initial.candidates.get(0).entityId;
            c.repository().updateRegistration(id,new PlateTextConsensus("AB12345",.99f,2,true),S);
            owner=c.identity().stamp(id,initial.candidates.get(0).vehicleTrackId);
            NormalizedQuad quad=new NormalizedQuad(new float[]{.4f,.6f,.6f,.6f,.6f,.65f,.4f,.65f});
            c.repository().attachPlate(id,10,quad,new AppearanceDescriptor(new float[]{1,0}),S);
            c.anchorIdentityPlate(owner,10,quad,stamp(1));
        }
        void plate(long frame) {
            c.observeLocalTargets(stamp(frame),ComplementaryVehicleContinuityTest.frame(),true,
                    ComplementaryVehicleContinuityTest.frame(tracked(10,owner,.4f)),true);
        }
    }
    @Test public void plateKeepsOnePhysicalOwnerThroughRepeatedMpMissesAndTrackTtl() {
        Fixture f=new Fixture();
        for(int frame=2;frame<=70;frame++) {
            f.plate(frame);
            f.c.updateFromMp(frame,stamp(frame).sourceFrameStamp(),frame*S,Collections.emptyList(),Collections.emptySet(),frame*S);
            assertTrue(f.c.identity().accepts(f.owner));
            assertTrue(f.c.identity().plateDerivedBounds(frame*S).containsKey(f.id));
        }
        f.plate(71);
        VehicleTrackingFrame returned=f.c.updateFromMp(71,stamp(71).sourceFrameStamp(),71*S,Collections.singletonList(car(4)),Collections.emptySet(),71*S);
        assertEquals(f.id,returned.candidates.get(0).entityId);
        assertEquals(1,f.c.stats().entitiesCreated);
        assertEquals(1,f.c.identity().presentation().get(f.id).number);
        assertEquals("AB12345",f.c.repository().get(f.id).registration().text);
    }
    @Test public void actualLossOfBothChannelsInvalidatesTheOwnerAndOlderPreviewCannotUndoIt() {
        Fixture f=new Fixture();f.plate(2);
        f.c.observeLocalTargets(stamp(3),frame(),true,frame(),true);
        assertFalse(f.c.identity().accepts(f.owner));
        f.plate(2);assertFalse(f.c.identity().accepts(f.owner));
    }
    @Test public void missingFlowAfterCaptureGapIsUnavailableAndCannotInvalidateThePlateOwner() {
        Fixture f=new Fixture();f.plate(2);
        ContinuityStamp gap=stamp(3);
        PreviewTrackingFrame unavailable=frame().withSourceStamp(gap.sourceFrameStamp(),false);
        f.c.observeLocalTargets(gap,unavailable,true,unavailable,true);
        assertTrue(f.c.identity().accepts(f.owner));
        assertFalse(f.c.displaySnapshot(gap).localGeometry.containsKey(f.id)); // 600 ms geometry limit remains.
        f.plate(4);
        assertTrue(f.c.identity().accepts(f.owner));assertEquals(1,f.c.identity().ownerNumber(f.id));
        f.c.observeLocalTargets(stamp(5),frame(),true,frame(),true);
        assertFalse(f.c.identity().accepts(f.owner)); // A real evaluated miss still ends continuity.
    }
    @Test public void aPlateWithoutTheOriginalAnchorOwnerCannotProveContinuity() {
        Fixture f=new Fixture();f.plate(2);
        f.c.observeLocalTargets(stamp(3),frame(),true,frame(tracked(10,null,.4f)),true);
        assertFalse(f.c.identity().accepts(f.owner));
    }
    @Test public void unavailableTrackingDoesNotRefreshProofAndExpiresWithinTheExistingWindow() {
        Fixture f=new Fixture();f.plate(2);
        f.c.observeLocalTargets(stamp(3),null,false,null,false);
        assertTrue(f.c.identity().accepts(f.owner));
        f.c.observeLocalTargets(stamp(10),null,false,null,false);
        assertFalse(f.c.identity().accepts(f.owner));
    }
    @Test public void replayIsUnknownInsteadOfALostOwnerAndDoesNotExtendVisibleGeometry() {
        Fixture f=new Fixture();f.plate(2);
        PreviewTrackingFrame pending=PreviewTrackingFrame.pending(PreviewTrackingFrame.State.REPLAYING,stamp(2).sourceFrameStamp());
        f.c.observeLocalTargets(stamp(3),frame(),true,pending,true);
        assertTrue(f.c.identity().accepts(f.owner));
        assertFalse(f.c.displaySnapshot(stamp(3)).localGeometry.containsKey(f.id));
        f.plate(4);assertTrue(f.c.identity().accepts(f.owner));
        f.c.observeLocalTargets(stamp(5),frame(),true,frame(),true);
        assertFalse(f.c.identity().accepts(f.owner));
    }
    @Test public void differentOcrUpdatesEvidenceWithoutBreakingAContinuousOwner() {
        Fixture f=new Fixture();f.plate(2);
        assertTrue(f.c.preflightIdentity(f.owner,"AB1234S",2*S));
        assertTrue(f.c.identity().accepts(f.owner));
    }
    @Test public void exclusiveLockDoesNotAdmitOrTrackOtherVehicles() {
        Fixture f=new Fixture();f.plate(2);
        VehicleTrackManager.Observation neighbor=new VehicleTrackManager.Observation(new NormalizedBounds(.01f,.01f,.08f,.08f),
                .95f,new AppearanceDescriptor(new float[]{0,1}),1);
        f.c.updateFromMp(2,stamp(2).sourceFrameStamp(),2*S,Arrays.asList(car(0),neighbor),Collections.emptySet(),2*S);
        assertEquals(2,f.c.stats().entitiesCreated);
        f.c.setExclusiveTarget(f.id);f.plate(3);
        VehicleTrackingFrame locked=f.c.updateFromMp(3,stamp(3).sourceFrameStamp(),3*S,Arrays.asList(car(0),neighbor),Collections.emptySet(),3*S);
        assertEquals(1,locked.candidates.size());assertEquals(f.id,locked.candidates.get(0).entityId);
        assertEquals(2,f.c.stats().entitiesCreated);
        f.plate(4);
        f.c.updateFromMp(4,stamp(4).sourceFrameStamp(),4*S,Collections.singletonList(neighbor),Collections.emptySet(),4*S);
        assertEquals(2,f.c.stats().entitiesCreated);
        assertTrue(f.c.identity().accepts(f.owner));
    }
    @Test public void controlledZoomRequiresFreshUnambiguousObjectMeasurementAndKeepsItsOwner() {
        Fixture f=new Fixture();f.plate(2);f.c.setExclusiveTarget(f.id);
        f.c.identity().beginControlledTransform(f.id,2*S);
        f.c.identity().applyControlledZoom(1.2f);
        VehicleTrackManager.Observation zoomed=new VehicleTrackManager.Observation(new NormalizedBounds(.02f,.02f,.98f,.98f),
                .9f,new AppearanceDescriptor(new float[]{1,0}),0);
        VehicleTrackingFrame measured=f.c.updateFromMp(3,stamp(3).sourceFrameStamp(),3*S,Collections.singletonList(zoomed),Collections.emptySet(),3*S);
        assertEquals(f.id,measured.candidates.get(0).entityId);assertEquals(1,f.c.stats().entitiesCreated);
        assertTrue(f.c.identity().accepts(f.owner));
    }
    @Test public void localPlateEvidenceIsNotAFreshMpOrMtAndCanExplainASlightlyDelayedPreview() {
        Fixture f=new Fixture();f.plate(3);f.plate(4);
        TargetContinuityEvidence local=f.c.localTargetEvidence(f.id,3*S,0,0,0);
        assertNotNull(local);assertEquals(TargetContinuityLevel.PLATE_ONLY,local.level);
        assertFalse(local.freshVehicleMeasurement);assertFalse(local.freshPlateMeasurement);
        assertEquals(0,local.measurementAgeNanos);
        assertNull(f.c.localTargetEvidence(f.id,3*S,1,0,0));
    }
}
