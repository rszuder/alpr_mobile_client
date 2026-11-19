package com.example.alpr_v1.tracking;

import android.app.Application;
import android.graphics.PointF;
import android.graphics.RectF;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.ui.OverlayItem;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Real local pixels cross the image edge and return; no neural inference or device claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
public class PartialVehicleTrackingTest {
    private static final long S=1_000_000_000L;
    private static SourceFrameStamp source(int frame) {
        return new ContinuityStamp(0,0,0,frame+1,S+frame*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR).sourceFrameStamp();
    }
    private static PreviewPlateTracker bodyTracker() {
        return ReflectionHelpers.getField(Robolectric.buildActivity(MainActivity.class).get(),"previewVehicleTracker");
    }
    private static OverlayItem anchor() {
        float l=60/320f,t=45/240f,r=200/320f,b=180/240f;
        return new OverlayItem(OverlayItem.Kind.PLATE,new RectF(l,t,r,b),Arrays.asList(
                new PointF(l,t),new PointF(r,t),new PointF(r,b),new PointF(l,b)),"",7,false);
    }
    private static byte[] pixels(int dx) {
        byte[] gray=new byte[320*240];Arrays.fill(gray,(byte)35);
        for(int y=45;y<180;y++)for(int x=60;x<200;x++)if(x+dx>=0&&x+dx<320)
            gray[y*320+x+dx]=(byte)Math.max(0,Math.min(255,Math.round((float)(128+42*Math.sin(x*.19)
                    +36*Math.cos(y*.17)+27*Math.sin((x+y)*.11)+18*Math.cos(x*.07-y*.13)))));
        return gray;
    }
    @Test public void visibleVehiclePixelsKeepTheirCoordinatesWhenOneFifthLeavesTheFrameAndReturns() {
        PreviewPlateTracker tracker=bodyTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(anchor()),320,240,Collections.emptyMap(),
                pixels(0),320,240,source(0)));
        for(int frame=1;frame<=90;frame++) {
            int dx=frame<=45?-2*frame:-90+2*(frame-45);
            byte[] gray=pixels(dx);history.offer(source(frame),gray,320,240);
            PreviewTrackingFrame result=tracker.updateLuma(gray,320,240,null,source(frame),history);
            assertNotNull("frame "+frame,result);assertEquals("frame "+frame,1,result.trackedPlates.size());
            TrackedPlate body=result.trackedPlates.get(0);
            assertTrue("frame "+frame+" similarity="+body.localAppearanceSimilarity+" failures="+body.consecutiveFailures,
                    VehicleTrackingCoordinator.validLocal(body));
            assertEquals(Math.max(0,60+dx)/320f,body.normalizedBounds.left,.018f);
            assertEquals((200+dx)/320f,body.normalizedBounds.right,.018f);
        }
    }
    @Test public void aBlankImageCannotRenewThePartiallyVisibleVehicle() {
        PreviewPlateTracker tracker=bodyTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(anchor()),320,240,Collections.emptyMap(),
                pixels(0),320,240,source(0)));
        for(int frame=1;frame<=35;frame++) {
            byte[] gray=pixels(-2*frame);history.offer(source(frame),gray,320,240);
            tracker.updateLuma(gray,320,240,null,source(frame),history);
        }
        byte[] blank=new byte[320*240];Arrays.fill(blank,(byte)35);
        for(int frame=36;frame<=40;frame++) {
            history.offer(source(frame),blank,320,240);
            PreviewTrackingFrame result=tracker.updateLuma(blank,320,240,null,source(frame),history);
            if(result!=null)for(TrackedPlate body:result.trackedPlates)assertFalse(VehicleTrackingCoordinator.validLocal(body));
        }
    }
    @Test public void aNeuralDisplayBoxCorrectionCannotChangeTheAppearanceReferenceOfTheSamePixelPath() {
        PreviewPlateTracker tracker=bodyTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(anchor()),320,240,Collections.emptyMap(),
                pixels(0),320,240,source(0)));
        for(int frame=1;frame<=30;frame++) {
            int dx=frame<=15?0:frame-15;byte[] gray=pixels(dx);
            history.offer(source(frame),gray,320,240);
            PreviewTrackingFrame result=tracker.updateLuma(gray,320,240,null,source(frame),history);
            assertNotNull(result);assertEquals(1,result.trackedPlates.size());
            TrackedPlate body=result.trackedPlates.get(0);
            assertTrue("frame "+frame+" app="+body.localAppearanceSimilarity+" q="+body.trackingQuality,
                    VehicleTrackingCoordinator.validLocal(body));
            if(frame==10) {
                // Same actual pixels; the independent detector slightly changes the displayed crop.
                float l=54/320f,t=42/240f,r=210/320f,b=184/240f;
                OverlayItem corrected=new OverlayItem(OverlayItem.Kind.PLATE,new RectF(l,t,r,b),Arrays.asList(
                        new PointF(l,t),new PointF(r,t),new PointF(r,b),new PointF(l,b)),"",7,false);
                assertTrue(tracker.anchorLuma(Collections.singletonList(corrected),320,240,Collections.emptyMap(),
                        gray,320,240,source(frame)));
            }
        }
    }
    @Test public void theAttachedPlateUsesTheFullBodyTransformAcrossTheImageEdge() {
        VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();c.setNormalDynamicIdentity(true,S);
        NormalizedBounds box=new NormalizedBounds(60/320f,45/240f,200/320f,180/240f);
        VehicleCandidate candidate=c.updateFromMp(1,source(0),S,Collections.singletonList(new VehicleTrackManager.Observation(
                box,.9f,new AppearanceDescriptor(new float[]{1,0}),0)),Collections.emptySet(),S).candidates.get(0);
        long id=candidate.entityId;
        DynamicVehicleIdentity.Stamp owner=c.identity().stamp(id,candidate.vehicleTrackId);
        NormalizedQuad plate=new NormalizedQuad(new float[]{105/320f,125/240f,155/320f,125/240f,
                155/320f,145/240f,105/320f,145/240f});
        c.repository().attachPlate(id,11,plate,null,S);
        c.anchorIdentityPlate(owner,11,plate,new ContinuityStamp(0,0,0,1,S,SourceTimestampDomain.CAMERAX_SENSOR));
        OverlayItem original=anchor();
        OverlayItem body=new OverlayItem(original.kind,original.normalizedBounds,original.normalizedKeypoints,"",id,false);
        PreviewPlateTracker tracker=bodyTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(body),320,240,Collections.singletonMap(id,owner),
                pixels(0),320,240,source(0)));
        for(int frame=1;frame<=90;frame++) {
            int dx=frame<=45?-2*frame:-90+2*(frame-45);byte[] gray=pixels(dx);
            history.offer(source(frame),gray,320,240);
            PreviewTrackingFrame tracked=tracker.updateLuma(gray,320,240,null,source(frame),history);
            ContinuityStamp stamp=new ContinuityStamp(0,0,0,frame+1,S+frame*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
            c.observeLocalTargets(stamp,tracked,true,null,false);
            DynamicVehicleDisplaySnapshot snapshot=c.displaySnapshot(stamp);
            assertTrue("visible owner "+frame,snapshot.identities.get(id).visible);
            assertEquals(1,snapshot.identities.get(id).number);
            assertNotNull("attached plate "+frame,snapshot.vehicles.get(id).plate);
            assertEquals((105+dx)/320f,snapshot.vehicles.get(id).plate.bounds().left,.018f);
            assertEquals(S,c.repository().get(id).lastMtSourceTimestampNanos());
            if(frame==45) {
                // MP also sees a clipped body. It must not redefine the full body/plate coordinate system.
                float l=0,r=(200+dx)/320f,t=45/240f,b=180/240f;
                OverlayItem clipped=new OverlayItem(OverlayItem.Kind.PLATE,new RectF(l,t,r,b),Arrays.asList(
                        new PointF(l,t),new PointF(r,t),new PointF(r,b),new PointF(l,b)),"",id,false);
                assertTrue(tracker.anchorLuma(Collections.singletonList(clipped),320,240,Collections.singletonMap(id,owner),
                        gray,320,240,source(frame)));
            }
        }
    }
}
