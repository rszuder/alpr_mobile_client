package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticSceneFeatureWatcherTest {
    private static final int SIZE = 200;
    private static final NormalizedBounds BOX = new NormalizedBounds(.15f,.15f,.85f,.85f);
    public static byte[] sparseCorners(int dx, int dy) {
        byte[] image = new byte[SIZE * SIZE]; Arrays.fill(image,(byte)100);
        for (int row=0;row<4;row++) for(int col=0;col<4;col++) {
            int left=42+col*31+dx, top=42+row*31+dy;
            for(int y=top;y<top+9;y++) for(int x=left;x<left+9;x++)
                if(x>=0&&y>=0&&x<SIZE&&y<SIZE) image[y*SIZE+x]=(byte)(x<left+5 ? 50 : 170);
        }
        return image;
    }
    private StaticSceneWatcher watcher() {
        StaticSceneWatcher watcher=new StaticSceneWatcher();
        watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(BOX),null,0f));
        watcher.observe(sparseCorners(0,0),SIZE,SIZE,0,false);
        return watcher;
    }
    @Test public void lostCornersResetEvenWhenLumaFractionsRemainBelowThresholds() {
        StaticSceneWatcher w=watcher();
        byte[] blank=new byte[SIZE*SIZE];Arrays.fill(blank,(byte)100);
        assertFalse(w.observe(blank,SIZE,SIZE,100_000_000L,false).changed);
        assertFalse(w.observe(blank,SIZE,SIZE,200_000_000L,false).changed);
        StaticSceneWatcher.Result r=w.observe(blank,SIZE,SIZE,300_000_000L,false);
        assertTrue("points="+r.featurePoints+" lost="+r.featureLostFraction,r.changed);
        assertTrue(r.featurePoints>=4);
        assertEquals("static_alpr_features_changed",r.reason);
        assertTrue(r.localFraction<StaticSceneWatcher.DEFAULT.localFraction);
        assertTrue(r.globalFraction<StaticSceneWatcher.DEFAULT.globalFraction);
    }
    @Test public void persistentCornerMotionAlsoDetectsReplacementAtSameBackground() {
        StaticSceneWatcher w=watcher();
        byte[] shifted=sparseCorners(6,4);
        w.observe(shifted,SIZE,SIZE,100_000_000L,false);
        w.observe(shifted,SIZE,SIZE,200_000_000L,false);
        StaticSceneWatcher.Result r=w.observe(shifted,SIZE,SIZE,300_000_000L,false);
        assertTrue("points="+r.featurePoints+" moved="+r.featureMovedFraction+" lost="+r.featureLostFraction,r.changed);
        assertEquals("static_alpr_features_changed",r.reason);
    }
    @Test public void unchangedExposureNoiseAndOnePixelJitterDoNotReset() {
        for(int kind=0;kind<4;kind++) {
            StaticSceneWatcher w=watcher();byte[] current=sparseCorners(kind==3?1:0,0);
            for(int i=0;i<current.length;i++) {
                if(kind==1)current[i]+=35;
                if(kind==2)current[i]+=(i%3)-1;
            }
            for(int i=1;i<=8;i++)assertFalse("kind="+kind,w.observe(current,SIZE,SIZE,i*100_000_000L,false).changed);
        }
    }
    @Test public void transientFeatureLossAndRepeatedFastCallbacksCannotConfirmCut() {
        StaticSceneWatcher w=watcher(); byte[] blank=new byte[SIZE*SIZE];Arrays.fill(blank,(byte)100);
        for(int i=0;i<10;i++)assertFalse(w.observe(blank,SIZE,SIZE,100_000_000L+i*1_000_000L,false).changed);
        assertFalse(w.observe(sparseCorners(0,0),SIZE,SIZE,200_000_000L,false).changed);
        assertFalse(w.observe(blank,SIZE,SIZE,300_000_000L,false).changed);
    }
    @Test public void featurelessAndUnobservedRegionsCannotInventChange() {
        for(boolean empty:Arrays.asList(true,false)) {
            StaticSceneWatcher w=new StaticSceneWatcher();
            w.arm(new StaticSceneWatchRegions(empty?null:Collections.singletonList(new NormalizedBounds(0,0,.1f,.1f)),null,0f));
            w.observe(sparseCorners(0,0),SIZE,SIZE,0,false);
            byte[] blank=new byte[SIZE*SIZE];Arrays.fill(blank,(byte)100);
            for(int i=1;i<=5;i++) {
                StaticSceneWatcher.Result r=w.observe(blank,SIZE,SIZE,i*100_000_000L,false);
                assertFalse(r.changed);assertEquals(0,r.featurePoints);
            }
        }
    }
    @Test public void delayedBoxDiscoveryUsesOriginalLumaReference() {
        StaticSceneWatcher w=new StaticSceneWatcher();
        w.observe(sparseCorners(0,0),SIZE,SIZE,0,false);
        byte[] blank=new byte[SIZE*SIZE];Arrays.fill(blank,(byte)100);
        w.observe(blank,SIZE,SIZE,100_000_000L,false);
        w.arm(new StaticSceneWatchRegions(Collections.singletonList(BOX),null,0f));
        w.observe(blank,SIZE,SIZE,200_000_000L,false);
        w.observe(blank,SIZE,SIZE,300_000_000L,false);
        assertTrue(w.observe(blank,SIZE,SIZE,400_000_000L,false).changed);
    }
    @Test public void zoomHasSeparateAnchorAndReturnStillChecksOriginalScene() {
        StaticSceneWatcher w=watcher();byte[] zoom=sparseCorners(8,8);
        for(int i=1;i<=5;i++)assertFalse(w.observe(zoom,SIZE,SIZE,i*100_000_000L,true,1.8f).changed);
        assertFalse(w.observe(sparseCorners(0,0),SIZE,SIZE,600_000_000L,false).changed);
        byte[] blank=new byte[SIZE*SIZE];Arrays.fill(blank,(byte)100);
        w.observe(blank,SIZE,SIZE,700_000_000L,false);
        w.observe(blank,SIZE,SIZE,800_000_000L,false);
        assertTrue(w.observe(blank,SIZE,SIZE,900_000_000L,false).changed);
    }
    @Test public void realChangeDuringStableZoomIsStillDetected() {
        StaticSceneWatcher w=watcher();byte[] blank=new byte[SIZE*SIZE];Arrays.fill(blank,(byte)100);
        w.observe(sparseCorners(8,8),SIZE,SIZE,100_000_000L,true,1.8f);
        w.observe(blank,SIZE,SIZE,200_000_000L,true,1.8f);
        w.observe(blank,SIZE,SIZE,300_000_000L,true,1.8f);
        assertTrue(w.observe(blank,SIZE,SIZE,400_000_000L,true,1.8f).changed);
    }
    @Test public void platesAreFeatureRegionsEvenWhenVehicleBoxesExist() {
        NormalizedBounds plate=new NormalizedBounds(.4f,.5f,.6f,.55f);
        StaticSceneWatchRegions r=new StaticSceneWatchRegions(Collections.singletonList(BOX),Collections.singletonList(plate),.12f);
        assertEquals("vehicles",r.source);assertEquals(2,r.featureBounds.size());
        assertEquals(plate,r.featureBounds.get(0));
        assertEquals(.32f,r.atZoom(1.8f).featureBounds.get(0).left,.001f);
        assertEquals(plate,r.featureBounds.get(0));
    }
}
