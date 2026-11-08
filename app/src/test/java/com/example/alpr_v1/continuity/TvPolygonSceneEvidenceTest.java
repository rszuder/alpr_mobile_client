package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.*;
import com.example.alpr_v1.vision.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TvPolygonSceneEvidenceTest {
    static final int W=TvObservationMaskTest.W,H=TvObservationMaskTest.H;
    static final List<NormalizedBounds> REGIONS=Collections.singletonList(new NormalizedBounds(.3f,.35f,.65f,.65f));
    @Test public void aExteriorCannotWakeStaticOrAlterFeatureEvidence(){outsideStatic(false);}
    @Test public void bBboxCornersCannotWakeStaticOrAlterFeatureEvidence(){outsideStatic(true);}
    private void outsideStatic(boolean corners){
        byte[] a=TvObservationMaskTest.pattern();ObservationMask m=TvObservationMaskTest.mask();
        for(boolean local:new boolean[]{false,true}){
            StaticSceneWatcher baseline=new StaticSceneWatcher(),test=new StaticSceneWatcher();
            StaticSceneWatchRegions regions=new StaticSceneWatchRegions(local?REGIONS:null,null,0f);baseline.arm(regions);test.arm(regions);
            for(int i=0;i<16;i++){
                StaticSceneWatcher.Result expected=baseline.observe(a,W,H,i*150_000_000L,false,1,m);
                StaticSceneWatcher.Result r=test.observe(TvObservationMaskTest.outside(a,i,corners),W,H,i*150_000_000L,false,1,m);
                assertFalse(r.changed);assertEquals(expected.localFraction,r.localFraction,0f);assertEquals(expected.globalFraction,r.globalFraction,0f);
                assertEquals(expected.featurePoints,r.featurePoints);assertEquals(expected.featureLostFraction,r.featureLostFraction,0f);
                assertEquals(expected.sceneFeatureLostFraction,r.sceneFeatureLostFraction,0f);assertEquals(expected.addedFeaturePoints,r.addedFeaturePoints);
                assertEquals(expected.addedFeatureUnmatchedFraction,r.addedFeatureUnmatchedFraction,0f);
            }
        }
    }
    @Test public void cInteriorReplacementProducesOneGenerationAndOldEvidenceCannotResetTwice(){
        ObservationMask m=TvObservationMaskTest.mask();byte[] a=TvObservationMaskTest.pattern(),b=a.clone();
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(m.contains(x,y))b[y*W+x]=(byte)(230-(a[y*W+x]&255));
        StaticSceneWatcher w=new StaticSceneWatcher();w.observe(a,W,H,0,false,1,m);
        SceneTransitionCoordinator c=new SceneTransitionCoordinator(SceneHandlingMode.STRICT_SCENE_BOUNDARY,SceneContinuityProfile.INITIAL);
        ContinuityStamp stamp=c.stamp(1);int resets=0;
        for(int i=1;i<=8;i++)if(w.observe(b,W,H,i*150_000_000L,false,1,m).changed){assertEquals(SceneTransitionAction.HARD_RESET,c.requestStaticSceneReset(stamp,"tv_interior",i*150_000_000L).action);resets++;}
        assertEquals(1,resets);assertEquals(stamp.sceneGeneration+1,c.snapshot().sceneGeneration);
        assertEquals(SceneTransitionAction.NONE,c.requestStaticSceneReset(stamp,"delayed",2_000_000_000L).action);
    }
    @Test public void dPersistentNewInteriorFeaturesWakeWithOldPlateUnchanged()throws Exception{
        // Reuse the established low-contrast fixture; only its observation domain changes.
        java.lang.reflect.Method original=StaticSceneFeatureAdditionTest.class.getDeclaredMethod("originalScene");original.setAccessible(true);
        java.lang.reflect.Method add=StaticSceneFeatureAdditionTest.class.getDeclaredMethod("addBackground",byte[].class,int.class,int.class);add.setAccessible(true);
        byte[] a=(byte[])original.invoke(null),b=(byte[])add.invoke(null,a,90,110);
        ObservationMask m=ObservationMask.polygon(200,200,"tv/new_features",new double[]{2,2,198,4,196,198,4,196});
        StaticSceneWatcher w=new StaticSceneWatcher();w.arm(new StaticSceneWatchRegions(null,Collections.singletonList(new NormalizedBounds(.3f,.3f,.7f,.7f)),0));
        w.observe(a,200,200,0,false,1,m);
        assertFalse(w.observe(b,200,200,100_000_000L,false,1,m).changed);
        assertFalse(w.observe(b,200,200,200_000_000L,false,1,m).changed);
        StaticSceneWatcher.Result r=w.observe(b,200,200,300_000_000L,false,1,m);
        assertTrue(r.reason,r.changed);assertEquals("static_scene_features_added",r.reason);assertEquals(0,r.localFraction,0);assertEquals(3,r.addedFeatureConfirmations);
    }
    @Test public void eDynamicGlobalAndLocalLumaIgnoreExterior(){
        ObservationMask m=TvObservationMaskTest.mask();byte[] a=TvObservationMaskTest.pattern();LumaSceneChangeDetector d=new LumaSceneChangeDetector();
        d.update(a,W,H,REGIONS,m);
        for(int i=1;i<25;i++){
            LumaSceneChangeDetector.Result r=d.update(TvObservationMaskTest.outside(a,i,i%2==0),W,H,REGIONS,m);
            assertFalse(r.changed);assertFalse(r.globalChanged);assertEquals(0,r.globalMeanDelta,0);assertEquals(0,r.changedFraction,0);
        }
    }
    @Test public void dynamicSourceAndPreviewScalarChannelsIgnoreExteriorAndDetectInterior(){
        ObservationMask m=TvObservationMaskTest.mask();byte[] a=TvObservationMaskTest.pattern();SceneChangeDetector d=new SceneChangeDetector();SceneAnchorGuard guard=new SceneAnchorGuard();
        guard.anchorLuma(a,W,H,m);d.updateLuma(a,W,H,m);d.updateLuma(a,W,H,m);
        for(int i=1;i<10;i++){
            byte[] b=TvObservationMaskTest.outside(a,i,false);SceneChangeDetector.Result r=d.updateLuma(b,W,H,m);
            assertFalse(r.rawCandidate);assertFalse(r.sceneChanged);assertEquals(0,r.brightnessDelta,0);assertFalse(guard.evaluateLuma(b,W,H,m).changed);
        }
        byte[] b=a.clone();for(int i=0;i<b.length;i++)b[i]=(byte)(230-(b[i]&255));
        assertTrue(d.updateLuma(b,W,H,m).sceneChanged);assertTrue(guard.evaluateLuma(b,W,H,m).changed);
        assertFalse(d.updateLuma(b,W,H,m).sceneChanged);
    }
    @Test public void exposureCompensationUsesOnlyInterior(){
        ObservationMask m=TvObservationMaskTest.mask();byte[] a=TvObservationMaskTest.pattern(),b=TvObservationMaskTest.outside(a,9,false);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(m.contains(x,y))b[y*W+x]=(byte)((a[y*W+x]&255)+20);
        StaticSceneWatcher w=new StaticSceneWatcher();w.observe(a,W,H,0,false,1,m);
        for(int i=1;i<=5;i++){StaticSceneWatcher.Result r=w.observe(b,W,H,i*150_000_000L,false,1,m);assertFalse(r.changed);assertEquals(0,r.globalFraction,0);}
    }
    @Test public void movingExteriorDoesNotSupplyGlobalMotionOrLostFeatures(){
        ObservationMask m=TvObservationMaskTest.mask();byte[] a=TvObservationMaskTest.pattern();
        GlobalLumaMotionTracker g=new GlobalLumaMotionTracker();RegionFeatureChangeDetector f=new RegionFeatureChangeDetector();
        g.update(a,W,H,Collections.emptyList(),false,m);f.anchor(a,W,H,Collections.singletonList(new NormalizedBounds(0,0,1,1)),m);
        for(int i=1;i<=12;i++){
            byte[] b=TvObservationMaskTest.outside(a,i,false);FrameMotionTransform t=g.update(b,W,H,Collections.emptyList(),false,m);
            assertFalse(t.valid&&Math.abs(t.tx)+Math.abs(t.ty)>.00001);RegionFeatureChangeDetector.Result r=f.observe(b,0);
            assertFalse(r.significant);assertEquals(0,r.lostFraction,0);assertEquals(0,r.movedFraction,0);
            assertTrue(f.compareAppearance(b).unsupportedFraction<.01f);
        }
    }
    @Test public void maskChangeRearmsReferencesWithoutComparingDifferentDomains(){
        ObservationMask a=TvObservationMaskTest.mask(),b=ObservationMask.polygon(W,H,"different",TvObservationMaskTest.Q);
        byte[] p=TvObservationMaskTest.pattern(),q=p.clone();for(int i=0;i<q.length;i++)q[i]=(byte)~q[i];
        StaticSceneWatcher w=new StaticSceneWatcher();w.observe(p,W,H,0,false,1,a);assertFalse(w.observe(q,W,H,100_000_000,false,1,b).changed);
        LumaSceneChangeDetector d=new LumaSceneChangeDetector();d.update(p,W,H,REGIONS,a);assertFalse(d.update(q,W,H,REGIONS,b).globalChanged);
        SceneChangeDetector s=new SceneChangeDetector();s.updateLuma(p,W,H,a);s.updateLuma(p,W,H,a);assertFalse(s.updateLuma(q,W,H,b).sceneChanged);
    }
    @Test public void hFullFrameStillSeesExteriorChangesWithLegacyCalls(){
        byte[] a=new byte[W*H],b=new byte[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++)b[y*W+x]=(byte)(x<W/2?240:20);
        LumaSceneChangeDetector d=new LumaSceneChangeDetector();d.update(a,W,H);assertTrue(d.update(b,W,H).globalChanged);
        SceneChangeDetector s=new SceneChangeDetector();float[] first=new float[400],second=new float[400];for(int i=0;i<400;i++)second[i]=i%2==0?255:0;
        s.updateSamples(first,W,H);s.updateSamples(first,W,H);assertTrue(s.updateSamples(second,W,H).sceneChanged);
    }
}
