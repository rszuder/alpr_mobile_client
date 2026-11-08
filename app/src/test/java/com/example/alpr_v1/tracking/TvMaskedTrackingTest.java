package com.example.alpr_v1.tracking;

import android.app.Application;
import android.graphics.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.ui.OverlayItem;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
public class TvMaskedTrackingTest {
    static final int S=240;
    static final ObservationMask MASK=ObservationMask.polygon(S,S,"tv/track",new double[]{20,20,220,30,210,220,25,210});
    static byte[] pattern(){byte[] a=new byte[S*S];Random r=new Random(23);r.nextBytes(a);return a;}
    static byte[] outside(byte[] a){byte[] b=a.clone();Random r=new Random(48);for(int y=0;y<S;y++)for(int x=0;x<S;x++)if(!MASK.contains(x,y))b[y*S+x]=(byte)r.nextInt(256);return b;}
    static OverlayItem plate(float l,float t,float r,float b){return new OverlayItem(OverlayItem.Kind.PLATE,new RectF(l,t,r,b),Arrays.asList(
            new PointF(l,t),new PointF(r,t+.01f),new PointF(r-.01f,b),new PointF(l+.01f,b-.01f)),"SYNTHETIC",9,false);}
    @Test public void pyramidRejectsBoundaryPatchesEvenWhenTheirCentreIsInside(){
        byte[] a=pattern();List<SparsePyramidalFlow.Point> p=Arrays.asList(new SparsePyramidalFlow.Point(25,25),new SparsePyramidalFlow.Point(120,120));
        assertTrue(MASK.contains(25,25));SparsePyramidalFlow.Result f=SparsePyramidalFlow.track(a,a,S,S,p,true,MASK);
        assertTrue(f.unsupported[0]);assertFalse(f.unsupported[1]);assertEquals(1,f.matches.size());assertEquals(1,f.matches.get(0).sourceIndex);
    }
    @Test public void supportedFlowIsBitEquivalentDespiteArbitraryExterior(){
        byte[] a=pattern(),b=outside(a);List<SparsePyramidalFlow.Point> p=Arrays.asList(new SparsePyramidalFlow.Point(80,80),new SparsePyramidalFlow.Point(160,80),new SparsePyramidalFlow.Point(120,160));
        SparsePyramidalFlow.Result first=SparsePyramidalFlow.track(a,a,S,S,p,true,MASK),second=SparsePyramidalFlow.track(a,b,S,S,p,true,MASK);
        assertEquals(3,first.matches.size());assertEquals(first.matches.size(),second.matches.size());
        for(int i=0;i<first.matches.size();i++){assertEquals(first.matches.get(i).target.x,second.matches.get(i).target.x,0);assertEquals(first.matches.get(i).target.y,second.matches.get(i).target.y,0);}
    }
    @Test public void localTrackWhoseSearchCrossesBoundaryIsUnobservedInsteadOfLost(){
        PreviewPlateTracker tracker=new PreviewPlateTracker();assertTrue(tracker.anchor(Collections.singletonList(plate(.10f,.12f,.3f,.25f)),S,S));
        byte[] a=pattern();for(int i=0;i<8;i++)assertNull(tracker.updateLuma(i%2==0?a:outside(a),S,S,MASK));
        assertEquals(S,tracker.sourceWidth());
    }
    @Test public void interiorTrackKeepsItsFourPoseCornersAndId(){
        PreviewPlateTracker tracker=new PreviewPlateTracker();tracker.anchor(Collections.singletonList(plate(.36f,.38f,.61f,.57f)),S,S);
        byte[] a=pattern();assertNull(tracker.updateLuma(a,S,S,MASK));PreviewTrackingFrame f=tracker.updateLuma(outside(a),S,S,MASK);
        assertNotNull(f);assertEquals(1,f.overlayItems.size());assertEquals(9,f.overlayItems.get(0).trackId);assertEquals(4,f.overlayItems.get(0).normalizedKeypoints.size());
        assertTrue(f.trackedPlates.get(0).localAppearanceValidated);assertEquals(1f,f.trackedPlates.get(0).localAppearanceSimilarity,.001f);
    }
    @Test public void changingMaskReanchorsWithoutFalseLoss(){
        PreviewPlateTracker tracker=new PreviewPlateTracker();tracker.anchor(Collections.singletonList(plate(.36f,.38f,.61f,.57f)),S,S);byte[] a=pattern();
        tracker.updateLuma(a,S,S,MASK);assertNotNull(tracker.updateLuma(a,S,S,MASK));
        ObservationMask next=ObservationMask.polygon(S,S,"next/calibration",new double[]{20,20,220,30,210,220,25,210});
        assertNull(tracker.updateLuma(outside(a),S,S,next));assertNotNull(tracker.updateLuma(a,S,S,next));
    }
}
