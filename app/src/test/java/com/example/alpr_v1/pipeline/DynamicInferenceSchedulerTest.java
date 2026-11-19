package com.example.alpr_v1.pipeline;

import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicInferenceSchedulerTest {
    private DynamicInferenceScheduler scheduler(long mp,long mt,long mz) {
        DynamicInferenceScheduler s=new DynamicInferenceScheduler();s.sourceSize(1080,1440);
        s.observe("MP",0,mp,0,0);s.observe("MT",0,mt,0,0);s.observe("MZ",0,mz,0,0);return s;
    }
    @Test public void twiceSlowerMzNeedsTwoActualGeometryCyclesAndFasterMzNeedsOne() {
        assertEquals(2,scheduler(100,100,200).baseCycles());
        assertEquals(1,scheduler(200,150,100).baseCycles());
    }
    @Test public void preparationAndPostprocessingArePartOfTheCost() {
        DynamicInferenceScheduler s=scheduler(100,100,200);
        s.observe("MP",500,100,200,0);
        assertEquals(1,s.baseCycles());
        s.observe("MZ",200,200,200,200);
        assertEquals(2,s.baseCycles());
    }
    @Test public void skippedStageDoesNotContributeZeroOrResetActualMeasurement() {
        DynamicInferenceScheduler s=scheduler(100,100,200);
        s.observe("MZ",0,0,0,0);assertEquals(200,s.stageNanos("MZ"),0);
        s.observe("MZ",0,400,0,0);assertEquals(250,s.stageNanos("MZ"),0);
    }
    @Test public void duplicateDecisionInOneMtCycleDoesNotAdvanceCadence() {
        DynamicInferenceScheduler s=scheduler(100,100,200);
        DynamicInferenceScheduler.Cadence c=new DynamicInferenceScheduler.Cadence();
        assertTrue(s.due(c,1,0,false,0,false));c.attempted();
        assertFalse(s.due(c,2,1,false,0,false));assertFalse(s.due(c,2,1,false,0,false));
        assertTrue(s.due(c,3,1,false,0,false));
    }
    @Test public void stableAndEmptyBackoffUseThreeTimesTheCurrentRatio() {
        for(boolean stable:new boolean[]{false,true}) {
            DynamicInferenceScheduler s=scheduler(100,100,200);
            DynamicInferenceScheduler.Cadence c=new DynamicInferenceScheduler.Cadence();
            for(int i=1;i<6;i++)assertFalse(s.due(c,i,2,stable,stable?0:2,false));
            assertTrue(s.due(c,6,2,stable,stable?0:2,false));
        }
    }
    @Test public void betterCropRemovesBackoffButStillHonorsBaseRatio() {
        DynamicInferenceScheduler s=scheduler(100,100,200);
        DynamicInferenceScheduler.Cadence c=new DynamicInferenceScheduler.Cadence();
        assertFalse(s.due(c,1,2,true,0,true));assertTrue(s.due(c,2,2,true,0,true));
    }
    @Test public void sourceSizeChangeResetsCostsAndWarmupDefaultsToTwo() {
        DynamicInferenceScheduler s=scheduler(200,150,100);assertEquals(1,s.baseCycles());
        s.sourceSize(1080,1440);assertEquals(1,s.baseCycles());
        s.sourceSize(720,960);assertEquals(2,s.baseCycles());
    }
}
