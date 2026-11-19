package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.*;
import android.view.View;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Real local KLT and native HUD on synthetic pixels; no NN/device acceptance claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class DynamicStopResumeHudTest {
    static final long S=1_000_000_000L;
    @Test public void twoActualHudNumbersAndPlatePairsSurviveAnEightSecondStopAndSubsetMp()throws Exception {
        VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();c.setNormalDynamicIdentity(true,S);
        List<VehicleTrackManager.Observation> observations=new ArrayList<>();
        for(int i=0;i<2;i++)observations.add(new VehicleTrackManager.Observation(box(i,0),.9f,
                new AppearanceDescriptor(i==0?new float[]{1,0}:new float[]{0,1}),i));
        VehicleTrackingFrame mp=c.updateFromMp(1,source(0).sourceFrameStamp(),S,observations,Collections.emptySet(),S);
        List<Long> ids=new ArrayList<>();Map<Long,DynamicVehicleIdentity.Stamp> owners=new HashMap<>();
        for(int i=0;i<2;i++) {
            VehicleCandidate v=mp.candidates.get(i);ids.add(v.entityId);
            DynamicVehicleIdentity.Stamp owner=c.identity().stamp(v.entityId,v.vehicleTrackId);owners.put(v.entityId,owner);
            NormalizedBounds b=box(i,0);NormalizedQuad plate=new NormalizedQuad(new float[]{
                    b.left+.05f,.53f,b.left+.15f,.53f,b.left+.15f,.56f,b.left+.05f,.56f});
            c.repository().attachPlate(v.entityId,100+i,plate,new AppearanceDescriptor(new float[]{1,0}),S);
            c.anchorIdentityPlate(owner,100+i,plate,source(0));
        }
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        assertTrue(tracker.anchorLuma(anchors(ids,0,2),320,240,owners,pixels(0),320,240,source(0).sourceFrameStamp()));
        DetectionOverlayView hud=new DetectionOverlayView(RuntimeEnvironment.getApplication(),null);
        hud.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));
        hud.layout(0,0,720,900);hud.setItems(Collections.emptyList(),320,240);
        Bitmap image=Bitmap.createBitmap(720,900,Bitmap.Config.ARGB_8888);
        try {
            for(int frame=1;frame<=80;frame++) {
                int dx=frame<=15?frame:frame<=55?15:15+frame-55;
                ContinuityStamp source=source(frame);byte[] gray=pixels(dx);
                history.offer(source.sourceFrameStamp(),gray,320,240);
                PreviewTrackingFrame tracked=tracker.updateLuma(gray,320,240,null,source.sourceFrameStamp(),history);
                c.observeLocalTargets(source,tracked,true,null,false);
                assertNotNull("local update "+frame,tracked);
                assertEquals("a subset MP must not remove its healthy neighbor at "+frame,2,tracked.trackedPlates.size());
                DynamicVehicleDisplaySnapshot snapshot=c.displaySnapshot(source);
                assertEquals(2,c.localPoolEvidence(source.sourceFrameStamp()).provenCount());
                hud.setVehicleIdentityPresentation(snapshot.identities,c.identityPlateOwners());
                hud.setDynamicVehicleSnapshot(snapshot,0);
                image.eraseColor(Color.BLACK);hud.draw(new Canvas(image));
                for(int i=0;i<2;i++) {
                    long id=ids.get(i);
                    assertTrue(c.identity().accepts(owners.get(id)));
                    assertEquals(i+1,c.identity().ownerNumber(id));
                    assertTrue("rendered number at "+frame+": "+hud.vehicleLabelForTesting(id),hud.vehicleLabelForTesting(id).startsWith("P"+(i+1)));
                    assertNotNull("plate pair at "+frame,snapshot.vehicles.get(id).plate);
                    assertEquals(box(i,dx).left,snapshot.localGeometry.get(id).left,.012f);
                    assertEquals(S,c.repository().get(id).lastMpNanos());
                    assertEquals(S,c.repository().get(id).lastMtSourceTimestampNanos());
                }
                if(frame==20||frame==60)assertTrue(tracker.anchorLuma(anchors(ids,dx,frame==20?1:2),320,240,owners,
                        gray,320,240,source.sourceFrameStamp()));
                if(frame==14||frame==54||frame==79) {
                    String out=System.getenv("DYNAMIC_STOP_RESUME_ARTIFACTS");
                    if(out!=null){java.io.File dir=new java.io.File(out);dir.mkdirs();
                        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(dir,"hud-"+frame+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}}
                }
                org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(200));
            }
        } finally {image.recycle();}
        assertEquals(2,c.repository().activeEntities().size());
    }
    static ContinuityStamp source(int frame){return new ContinuityStamp(0,0,0,frame+1,S+frame*200_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);}
    static NormalizedBounds box(int index,int dx){return new NormalizedBounds((30+150*index+dx)/320f,50/240f,(100+150*index+dx)/320f,150/240f);}
    static List<OverlayItem> anchors(List<Long> ids,int dx,int count) {
        List<OverlayItem> result=new ArrayList<>();
        for(int i=0;i<count;i++){NormalizedBounds b=box(i,dx);result.add(new OverlayItem(OverlayItem.Kind.PLATE,
                new RectF(b.left,b.top,b.right,b.bottom),Arrays.asList(new PointF(b.left,b.top),new PointF(b.right,b.top),
                new PointF(b.right,b.bottom),new PointF(b.left,b.bottom)),"",ids.get(i),false));}
        return result;
    }
    static byte[] pixels(int dx) {
        byte[] gray=new byte[320*240];Arrays.fill(gray,(byte)35);
        for(int i=0;i<2;i++)for(int y=50;y<150;y++)for(int x=30+150*i;x<100+150*i;x++)
            gray[y*320+x+dx]=(byte)Math.max(0,Math.min(255,Math.round((float)(128+42*Math.sin(x*.19+i)
                    +36*Math.cos(y*.17)+27*Math.sin((x+y)*.11)+18*Math.cos(x*.07-y*.13)))));
        return gray;
    }
}
