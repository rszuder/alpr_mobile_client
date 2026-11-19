package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.ui.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class DynamicStabilityRegressionTest {
    @Test public void newMpDoesNotStartVisualCorrectionUntilItsPixelAnchorIsApplied() {
        ComplementaryVehicleContinuityTest.Fixture f=new ComplementaryVehicleContinuityTest.Fixture();
        long second=ComplementaryVehicleContinuityTest.S;
        OverlayItem body=new OverlayItem(OverlayItem.Kind.VEHICLE,new android.graphics.RectF(.1f,.1f,.9f,.9f),Collections.emptyList(),"",f.id,false);
        TrackedPlate old=new TrackedPlate(body,.9f,1,0,9,0,4,3,.95f,true,null,99*second,f.owner,second);
        f.c.observeLocalTargets(ComplementaryVehicleContinuityTest.stamp(2),ComplementaryVehicleContinuityTest.frame(old),true,null,false);
        f.c.updateFromMp(2,ComplementaryVehicleContinuityTest.stamp(2).sourceFrameStamp(),2*second,
                Collections.singletonList(ComplementaryVehicleContinuityTest.car(0)),Collections.emptySet(),2*second);
        assertEquals(2*second,f.c.repository().get(f.id).lastMpNanos());
        assertEquals(second,f.c.displaySnapshot(2*second).vehicles.get(f.id).measuredAtNanos);
        TrackedPlate applied=new TrackedPlate(body,.9f,1,0,9,0,4,3,.95f,true,null,100*second,f.owner,2*second);
        f.c.observeLocalTargets(ComplementaryVehicleContinuityTest.stamp(3),ComplementaryVehicleContinuityTest.frame(applied),true,null,false);
        assertEquals(2*second,f.c.displaySnapshot(3*second).vehicles.get(f.id).measuredAtNanos);
    }
    @Test public void lumaHistoryOwnsItsPixelsAndBoundsBytesFramesAndSceneContext() {
        PreviewLumaHistory history=new PreviewLumaHistory(256,3,1_000_000_000L,500_000_000L,16);
        long start=1_000_000_000L;SourceFrameStamp first=lumaStamp(1,start,0);
        for(int i=0;i<5;i++) {
            byte[] pixels=new byte[64];Arrays.fill(pixels,(byte)42);
            history.offer(lumaStamp(i+1,start+i*100_000_000L,0),pixels,8,8);pixels[0]=0;
        }
        assertEquals(3,history.retainedFrames());assertEquals(192,history.retainedBytes());
        List<PreviewLumaHistory.Frame> replay=history.replayAfter(first,lumaStamp(6,start+500_000_000L,0));
        assertNotNull(replay);assertEquals(42,replay.get(0).gray[0]);
        SourceFrameStamp replacement=lumaStamp(7,start+600_000_000L,1);
        history.offer(replacement,new byte[64],8,8);assertEquals(1,history.retainedFrames());
        history.offer(lumaStamp(8,start+700_000_000L,0),new byte[64],8,8);assertEquals(1,history.retainedFrames());
        assertNull(history.replayAfter(first,replacement));
        assertNull(history.replayAfter(replacement,lumaStamp(9,start+1_200_000_001L,1)));
    }
    @Test public void lumaReplayIsBoundedAndDoesNotSkipAnUnsupportedPixelGap() {
        PreviewLumaHistory history=new PreviewLumaHistory(8192,64,10_000_000_000L,500_000_000L,16);
        long start=1_000_000_000L;SourceFrameStamp first=lumaStamp(1,start,0);
        for(int i=1;i<=40;i++)history.offer(lumaStamp(i+1,start+i*100_000_000L,0),new byte[64],8,8);
        SourceFrameStamp current=lumaStamp(42,start+4_100_000_000L,0);
        List<PreviewLumaHistory.Frame> replay=history.replayAfter(first,current);assertNotNull(replay);
        assertEquals(16,replay.size());long previous=start;
        for(PreviewLumaHistory.Frame frame:replay) {assertTrue(frame.stamp.sourceTimestampNanos-previous<=500_000_000L);previous=frame.stamp.sourceTimestampNanos;}
        assertTrue(current.sourceTimestampNanos-previous<=500_000_000L);
        assertNull(history.replayAfter(first,lumaStamp(43,start+5_000_000_001L,0)));
    }
    private static SourceFrameStamp lumaStamp(long sequence,long time,long scene) {
        return new SourceFrameStamp(sequence,time,SourceTimestampDomain.CAMERAX_SENSOR,scene,scene,0);
    }
    @Test public void consistentPixelsBelowTheOcrLockQualityStillProvePresence() {
        ComplementaryVehicleContinuityTest.Fixture f=new ComplementaryVehicleContinuityTest.Fixture();
        TrackedPlate template=ComplementaryVehicleContinuityTest.tracked(10,f.owner,.4f);
        for(int frame=2;frame<=71;frame++) {
            long time=frame*ComplementaryVehicleContinuityTest.S;
            TrackedPlate presence=new TrackedPlate(template.overlayItem,.68f,.75f,8,5,0,frame,frame,.90f,true,
                    new float[]{1,0},time,f.owner);
            f.c.observeLocalTargets(ComplementaryVehicleContinuityTest.stamp(frame),
                    ComplementaryVehicleContinuityTest.frame(),true,ComplementaryVehicleContinuityTest.frame(presence),true);
            f.c.updateFromMp(frame,ComplementaryVehicleContinuityTest.stamp(frame).sourceFrameStamp(),time,
                    Collections.emptyList(),Collections.emptySet(),time);
            assertTrue(f.c.identity().accepts(f.owner));assertEquals(1,f.c.identity().ownerNumber(f.id));
            assertTrue(f.c.displaySnapshot(time).localGeometry.containsKey(f.id));
            assertEquals(ComplementaryVehicleContinuityTest.S,f.c.repository().get(f.id).lastMpNanos());
            assertTrue(presence.trackingQuality<com.example.alpr_v1.pipeline.TargetStateMachine.QUALITY_TRACKING);
        }
    }
    @Test public void weakSupportAppearanceOrResidualCannotRenewThePresenceDeadline() {
        for(float[] bad:new float[][]{{.49f,8,.9f},{.75f,17,.9f},{.75f,8,.64f}}) {
            ComplementaryVehicleContinuityTest.Fixture f=new ComplementaryVehicleContinuityTest.Fixture();f.plate(2);
            TrackedPlate template=ComplementaryVehicleContinuityTest.tracked(10,f.owner,.4f);
            for(long delta:new long[]{100_000_000L,600_000_001L}) {
                long time=2*ComplementaryVehicleContinuityTest.S+delta;
                TrackedPlate weak=new TrackedPlate(template.overlayItem,.68f,bad[0],bad[1],5,0,10,10,bad[2],true,
                        new float[]{1,0},time,f.owner);
                f.c.observeLocalTargets(new ContinuityStamp(0,0,0,100+delta,time,SourceTimestampDomain.CAMERAX_SENSOR),
                        ComplementaryVehicleContinuityTest.frame(),true,ComplementaryVehicleContinuityTest.frame(weak),true);
            }
            // Weak matching is N/A for absence, but cannot refresh the last
            // positive pixels or extend the existing source-presence deadline.
            assertTrue(f.c.identity().accepts(f.owner));
            assertFalse(f.c.displaySnapshot(2*ComplementaryVehicleContinuityTest.S+600_000_001L).localGeometry.containsKey(f.id));
            assertNull(f.c.localTargetEvidence(f.id,2*ComplementaryVehicleContinuityTest.S+600_000_001L,0,0,0));
            long expired=2*ComplementaryVehicleContinuityTest.S+f.c.currentTrackTtlNanos()+1;
            TrackedPlate stillWeak=new TrackedPlate(template.overlayItem,.68f,bad[0],bad[1],5,0,10,10,bad[2],true,
                    new float[]{1,0},expired,f.owner);
            f.c.observeLocalTargets(new ContinuityStamp(0,0,0,999,expired,SourceTimestampDomain.CAMERAX_SENSOR),
                    ComplementaryVehicleContinuityTest.frame(),true,ComplementaryVehicleContinuityTest.frame(stillWeak),true);
            assertFalse(f.c.identity().accepts(f.owner));
        }
    }
    @Test public void localVehiclePixelsGuideFreshMpWhenItsOldMotionPredictionNoLongerOverlaps() {
        com.example.alpr_v1.domain.VehicleEntityRepository repo=new com.example.alpr_v1.domain.VehicleEntityRepository();
        VehicleTrackManager manager=new VehicleTrackManager(repo);manager.setNormalDynamicIdentity(true,1_000_000_000L);
        long start=1_000_000_000L;VehicleTrackManager.Snapshot first=manager.update(Collections.singletonList(
                observation(.1f)),start).get(0);
        repo.updateRegistration(first.entityId,new com.example.alpr_v1.domain.PlateTextConsensus("AB12345",.9f,2,true),start);
        DynamicVehicleIdentity.Stamp owner=manager.identity().stamp(first.entityId,first.vehicleTrackId);
        for(int i=1;i<=6;i++) {
            long time=start+i*100_000_000L;
            manager.identity().localBounds(first.entityId,observation(.1f+i*.08f).bounds,time);
            manager.identity().preview(time,Collections.singleton(first.entityId),true);
            if(i==2)manager.update(Collections.emptyList(),time);
        }
        assertEquals(start,repo.get(first.entityId).lastMpNanos());
        List<VehicleTrackManager.Snapshot> returned=manager.update(Collections.singletonList(observation(.58f)),1_600_000_000L);
        assertEquals(1,returned.size());assertEquals(first.entityId,returned.get(0).entityId);
        assertTrue(manager.identity().accepts(owner));assertEquals(1,manager.identity().ownerNumber(first.entityId));
        assertEquals(1,repo.activeEntities().size());
    }
    @Test public void futureLocalPixelsCannotSupplyTheGeometryOfAnOlderMpFrame() {
        com.example.alpr_v1.domain.VehicleEntityRepository repo=new com.example.alpr_v1.domain.VehicleEntityRepository();
        VehicleTrackManager manager=new VehicleTrackManager(repo);manager.setNormalDynamicIdentity(true,1_000_000_000L);
        VehicleTrackManager.Snapshot first=manager.update(Collections.singletonList(observation(.1f)),1_000_000_000L).get(0);
        repo.updateRegistration(first.entityId,new com.example.alpr_v1.domain.PlateTextConsensus("AB12345",.9f,2,true),1_000_000_000L);
        manager.identity().localBounds(first.entityId,observation(.58f).bounds,2_000_000_000L);
        manager.identity().preview(2_000_000_000L,Collections.singleton(first.entityId),true);
        List<VehicleTrackManager.Snapshot> returned=manager.update(Collections.singletonList(observation(.58f)),1_500_000_000L);
        assertTrue(returned.stream().anyMatch(s->s.sourceIndex>=0&&s.entityId!=first.entityId));
    }
    private static VehicleTrackManager.Observation observation(float left) {
        return new VehicleTrackManager.Observation(new com.example.alpr_v1.domain.NormalizedBounds(left,.2f,left+.15f,.4f),
                .9f,new com.example.alpr_v1.domain.AppearanceDescriptor(new float[]{1,0}),0);
    }
    @Test public void aStrongAppearanceContradictionDoesNotReceiveTheCoastingGrace() {
        ComplementaryVehicleContinuityTest.Fixture f=new ComplementaryVehicleContinuityTest.Fixture();f.plate(2);
        TrackedPlate original=ComplementaryVehicleContinuityTest.tracked(10,f.owner,.4f);
        TrackedPlate contradiction=new TrackedPlate(original.overlayItem,.9f,1,0,6,0,3,3,.05f,true,new float[]{0,1},0,f.owner);
        f.c.observeLocalTargets(new ContinuityStamp(0,0,0,3,2_100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR),
                ComplementaryVehicleContinuityTest.frame(),true,ComplementaryVehicleContinuityTest.frame(contradiction),true);
        assertFalse(f.c.identity().accepts(f.owner));
    }
    @Test public void theFinalMpFilterMustNotEraseSeventyPlateSupportedVehicleFrames() {
        ComplementaryVehicleContinuityTest.Fixture f=new ComplementaryVehicleContinuityTest.Fixture();
        for(int i=2;i<=71;i++) {
            f.plate(i);long time=i*ComplementaryVehicleContinuityTest.S;
            VehicleTrackingFrame mp=f.c.updateFromMp(i,ComplementaryVehicleContinuityTest.stamp(i).sourceFrameStamp(),time,
                    Collections.emptyList(),Collections.emptySet(),time);
            DynamicVehicleDisplaySnapshot snapshot=f.c.displaySnapshot(time);
            List<OverlayItem> rendered=VehicleMeasurementOverlay.retainSupportedVehicles(Collections.emptyList(),mp,snapshot);
            assertEquals(1,rendered.size());assertEquals(f.id,rendered.get(0).trackId);
            assertTrue(rendered.get(0).carriedPrediction);assertEquals(1,snapshot.identities.get(f.id).number);
            assertEquals(1,f.c.repository().get(f.id).lastMpNanos()/ComplementaryVehicleContinuityTest.S);
        }
    }
    @Test public void briefLossCoastsWithoutRenewingPixelsAndThenExpiresAtTheDeadline() {
        ComplementaryVehicleContinuityTest.Fixture f=new ComplementaryVehicleContinuityTest.Fixture();f.plate(2);
        long proof=2*ComplementaryVehicleContinuityTest.S;
        for(long delta:new long[]{100_000_000L,600_000_000L,600_000_001L}) {
            long time=proof+delta;
            f.c.observeLocalTargets(new ContinuityStamp(0,0,0,10+delta,time,SourceTimestampDomain.CAMERAX_SENSOR),
                    ComplementaryVehicleContinuityTest.frame(),true,ComplementaryVehicleContinuityTest.frame(),true);
            assertEquals(delta<=DynamicVehicleIdentity.COAST_NANOS,f.c.identity().accepts(f.owner));
            assertEquals(delta<=DynamicVehicleIdentity.COAST_NANOS,f.c.displaySnapshot(time).localGeometry.containsKey(f.id));
        }
    }
}
