package com.example.alpr_v1.tracking;

import android.app.Application;
import android.graphics.*;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.ui.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Owned, delayed detector measurements and real local pixels; no NN or device acceptance claim. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class OwnerIsolatedAnchorTest {
    static final long S=1_000_000_000L;
    static final DynamicVehicleIdentity.Stamp A=new DynamicVehicleIdentity.Stamp(1,101,1,1,1);
    static final DynamicVehicleIdentity.Stamp B=new DynamicVehicleIdentity.Stamp(2,102,2,1,2);
    static SourceFrameStamp source(int frame) {
        return new SourceFrameStamp(frame+1,S+frame*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,1,1,0);
    }
    static OverlayItem box(long id,int dx) {
        float l=((id==1?25:140)+dx)/240f,r=l+70/240f,t=50/240f,b=155/240f;
        return new OverlayItem(OverlayItem.Kind.PLATE,new RectF(l,t,r,b),Arrays.asList(new PointF(l,t),new PointF(r,t),
                new PointF(r,b),new PointF(l,b)),"",id,false);
    }
    static byte[] pixels(int dx,boolean second) {
        byte[] gray=new byte[240*240];Arrays.fill(gray,(byte)35);
        for(int owner=0;owner<(second?2:1);owner++)for(int y=50;y<155;y++)for(int x=25+115*owner;x<95+115*owner;x++)
            gray[y*240+x+dx]=(byte)Math.max(0,Math.min(255,Math.round((float)(128+42*Math.sin(x*.19+owner)
                    +36*Math.cos(y*.17)+27*Math.sin((x+y)*.11)+18*Math.cos(x*.07-y*.13)))));
        return gray;
    }
    static TrackedPlate target(PreviewTrackingFrame frame,long id) {
        return frame.trackedPlates.stream().filter(p->p.trackId==id).findFirst().orElse(null);
    }
    @Test public void aDelayedNewOwnerCannotDiscardTheOtherOwnersCurrentPixelPath() {
        checkNewOwner(true);
    }
    @Test public void anUnmatchedNewMeasurementCannotReplaceAHealthyNeighborOrInventPixels() {
        checkNewOwner(false);
    }
    @Test public void aFasterNeuralWorkerCannotUseFuturePixelsOrRestartTheCurrentOwner() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(1,0)),240,240,Collections.singletonMap(1L,A),
                pixels(0,true),240,240,source(0)));
        for(int i=1;i<=5;i++){byte[] gray=pixels(i,true);history.offer(source(i),gray,240,240);
            tracker.updateLuma(gray,240,240,null,source(i),history);}
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(2,7)),240,240,Collections.singletonMap(2L,B),
                pixels(7,true),240,240,source(7)));
        for(int i=6;i<=8;i++) {
            byte[] gray=pixels(i,true);history.offer(source(i),gray,240,240);
            PreviewTrackingFrame frame=tracker.updateLuma(gray,240,240,null,source(i),history);
            assertTrue(VehicleTrackingCoordinator.validLocal(target(frame,1)));
            if(i<7)assertNull("future pixels are not current proof",target(frame,2));
            if(i==8)assertTrue(VehicleTrackingCoordinator.validLocal(target(frame,2)));
        }
    }
    private void checkNewOwner(boolean present) {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(1,0)),240,240,Collections.singletonMap(1L,A),
                pixels(0,true),240,240,source(0)));
        PreviewTrackingFrame frame=null;
        for(int i=1;i<=10;i++) {
            byte[] gray=pixels(i,present||i<8);history.offer(source(i),gray,240,240);
            frame=tracker.updateLuma(gray,240,240,null,source(i),history);
            assertTrue(VehicleTrackingCoordinator.validLocal(target(frame,1)));
        }
        int age=target(frame,1).ageFrames;
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(2,5)),240,240,Collections.singletonMap(2L,B),
                pixels(5,true),240,240,source(5)));
        byte[] current=pixels(11,present);history.offer(source(11),current,240,240);
        frame=tracker.updateLuma(current,240,240,null,source(11),history);
        assertEquals(PreviewTrackingFrame.State.LIVE,frame.state);
        TrackedPlate a=target(frame,1);assertNotNull("new owner must not discard A",a);
        assertTrue(VehicleTrackingCoordinator.validLocal(a));assertEquals(age+1,a.ageFrames);
        assertEquals(source(0).sourceTimestampNanos,a.anchorSourceTimestampNanos);
        if(present) {
            TrackedPlate b=target(frame,2);assertNotNull(b);assertTrue(VehicleTrackingCoordinator.validLocal(b));
            assertEquals(source(5).sourceTimestampNanos,b.anchorSourceTimestampNanos);
            assertEquals(box(2,11).normalizedBounds.left,b.normalizedBounds.left,.014f);
        } else assertFalse(VehicleTrackingCoordinator.validLocal(target(frame,2)));
    }
    @Test public void anEpochReplacementCannotResetItsNeighborOrRetainTheRevokedOwnerStamp() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        Map<Long,DynamicVehicleIdentity.Stamp> owners=new HashMap<>();owners.put(1L,A);owners.put(2L,B);
        assertTrue(tracker.anchorLuma(Arrays.asList(box(1,0),box(2,0)),240,240,owners,pixels(0,true),240,240,source(0)));
        PreviewTrackingFrame frame=null;
        for(int i=1;i<=10;i++) {byte[] gray=pixels(i,true);history.offer(source(i),gray,240,240);
            frame=tracker.updateLuma(gray,240,240,null,source(i),history);}
        int age=target(frame,2).ageFrames;
        DynamicVehicleIdentity.Stamp renewed=new DynamicVehicleIdentity.Stamp(1,103,3,1,1);
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(1,8)),240,240,Collections.singletonMap(1L,renewed),
                pixels(8,true),240,240,source(8)));
        byte[] gray=pixels(11,true);history.offer(source(11),gray,240,240);
        frame=tracker.updateLuma(gray,240,240,null,source(11),history);
        assertNotNull("neighbor must remain",target(frame,2));assertEquals(age+1,target(frame,2).ageFrames);
        assertSame(B,target(frame,2).identityOwner);
        assertNotNull(target(frame,1));assertSame(renewed,target(frame,1).identityOwner);
        assertEquals(2,frame.trackedPlates.size());
        // An old worker can finish later, but it cannot restore its revoked owner epoch.
        assertTrue(tracker.anchorLuma(Collections.singletonList(box(1,10)),240,240,Collections.singletonMap(1L,A),
                pixels(10,true),240,240,source(10)));
        gray=pixels(12,true);history.offer(source(12),gray,240,240);
        frame=tracker.updateLuma(gray,240,240,null,source(12),history);
        assertSame(renewed,target(frame,1).identityOwner);assertSame(B,target(frame,2).identityOwner);
    }
    @Test public void scanPresentationChangesPreserveDynamicPlatePixelsWhileStaticResetStillClearsThem() {
        for(boolean dynamic:new boolean[]{true,false}) {
            MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
            android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("scan-reset-"+dynamic,0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,
                    dynamic?"dynamic_continuity":"strict_scene_boundary").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);
            ReflectionHelpers.setField(main,"overlayView",new DetectionOverlayView(RuntimeEnvironment.getApplication(),null));
            PreviewPlateTracker tracker=ReflectionHelpers.getField(main,"previewPlateTracker");
            Map<Long,DynamicVehicleIdentity.Stamp> owners=new HashMap<>();owners.put(1L,A);owners.put(2L,B);
            assertTrue(tracker.anchorLuma(Arrays.asList(box(1,0),box(2,0)),240,240,owners,pixels(0,true),240,240,source(0)));
            tracker.updateLuma(pixels(1,true),240,240,null,source(1),null);
            ReflectionHelpers.callInstanceMethod(main,"resetScanPlateVisualState",ReflectionHelpers.ClassParameter.from(boolean.class,false));
            PreviewTrackingFrame frame=tracker.updateLuma(pixels(2,true),240,240,null,source(2),null);
            assertEquals("dynamic="+dynamic,dynamic?2:0,frame.trackedPlates.size());
        }
    }
}
