package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.ui.OverlayItem;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real KLT on two synthetic objects; delayed MP observations are synthetic, not device evidence. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class OwnerAvailabilityTest {
    private static final long S=1_000_000_000L;
    private static ContinuityStamp stamp(int frame) {
        return new ContinuityStamp(0,0,0,frame+1,S+frame*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
    }
    private static List<VehicleTrackManager.Observation> observations() {
        List<VehicleTrackManager.Observation> result=new ArrayList<>();
        for(int i=1;i<=2;i++) {
            android.graphics.RectF b=OwnerIsolatedAnchorTest.box(i,0).normalizedBounds;
            result.add(new VehicleTrackManager.Observation(new NormalizedBounds(b.left,b.top,b.right,b.bottom),.9f,
                    new AppearanceDescriptor(i==1?new float[]{1,0}:new float[]{0,1}),i-1));
        }
        return result;
    }
    @Test public void aCurrentNeighborCannotTurnAnUnobservedOwnerIntoAConfirmedAbsence() {
        VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();c.setNormalDynamicIdentity(true,S);
        VehicleTrackingFrame mp=c.updateFromMp(1,stamp(0).sourceFrameStamp(),S,observations(),Collections.emptySet(),S);
        long a=mp.candidates.get(0).entityId,b=mp.candidates.get(1).entityId;
        Map<Long,DynamicVehicleIdentity.Stamp> owners=new HashMap<>();
        for(VehicleCandidate car:mp.candidates)owners.put(car.entityId,c.identity().stamp(car.entityId,car.vehicleTrackId));
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        byte[] gray=OwnerIsolatedAnchorTest.pixels(0,true);
        List<OverlayItem> anchors=Arrays.asList(OwnerIsolatedAnchorTest.box(a,0),OwnerIsolatedAnchorTest.box(b,0));
        assertTrue(tracker.anchorLuma(anchors,240,240,owners,gray,240,240,stamp(0).sourceFrameStamp()));
        history.offer(stamp(1).sourceFrameStamp(),gray,240,240);
        PreviewTrackingFrame frame=tracker.updateLuma(gray,240,240,null,stamp(1).sourceFrameStamp(),history);
        c.observeLocalTargets(stamp(1),frame,true,null,false);
        assertTrue(c.identity().accepts(owners.get(a)));
        tracker.retainOnly(Collections.singleton(b));
        for(int i=2;i<=10;i++) {
            history.offer(stamp(i).sourceFrameStamp(),gray,240,240);
            frame=tracker.updateLuma(gray,240,240,null,stamp(i).sourceFrameStamp(),history);
            assertEquals(1,frame.trackedPlates.size());
            c.observeLocalTargets(stamp(i),frame,true,null,false);
        }
        assertTrue("B's LIVE frame must not revoke A's owner",c.identity().accepts(owners.get(a)));
        assertEquals(1,c.identity().ownerNumber(a));assertEquals(2,c.identity().ownerNumber(b));
        assertFalse("no unobserved geometry may be drawn",c.displaySnapshot(stamp(10)).localGeometry.containsKey(a));
        assertEquals(S,c.repository().get(a).lastMpNanos());
        // A late detector result describes its original source, not the current camera instant.
        VehicleTrackingFrame delayed=c.updateFromMp(2,stamp(2).sourceFrameStamp(),stamp(10).sourceTimestampNanos,
                observations(),Collections.emptySet(),stamp(10).sourceTimestampNanos);
        assertEquals(new HashSet<>(Arrays.asList(a,b)),new HashSet<>(Arrays.asList(
                delayed.candidates.get(0).entityId,delayed.candidates.get(1).entityId)));
        assertEquals(stamp(2).sourceTimestampNanos,c.repository().get(a).lastMpNanos());
        assertTrue(c.identity().accepts(owners.get(a)));
        tracker.retainOnly(null);
        assertTrue(tracker.anchorLuma(Collections.singletonList(OwnerIsolatedAnchorTest.box(a,0)),240,240,
                Collections.singletonMap(a,owners.get(a)),gray,240,240,stamp(2).sourceFrameStamp()));
        history.offer(stamp(11).sourceFrameStamp(),gray,240,240);
        frame=tracker.updateLuma(gray,240,240,null,stamp(11).sourceFrameStamp(),history);
        c.observeLocalTargets(stamp(11),frame,true,null,false);
        assertTrue(c.displaySnapshot(stamp(11)).localGeometry.containsKey(a));
        assertEquals(1,c.identity().ownerNumber(a));assertEquals(0,c.repository().get(a).mzAttempts());
        // Unavailability is bounded; it cannot keep an old identity alive indefinitely.
        c.observeLocalTargets(stamp(200),null,false,null,false);
        assertFalse(c.identity().accepts(owners.get(a)));
        assertFalse(c.displaySnapshot(stamp(200)).localGeometry.containsKey(a));
    }
}
