package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.tracking.MotionBoxTracker;
import com.example.alpr_v1.vision.Detection;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic character detections and source clocks; no neural inference or GT. */
public class DynamicCharacterEvidenceTest {
    static final List<String> LABELS=Arrays.asList("A","B","1","2","3","4","5","S","X","Y");
    static ContinuityStamp source(long frame,long millis) {
        return new ContinuityStamp(0,0,0,frame,millis*1_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
    }
    static List<Detection> characters(String text,float confidence) {
        List<Detection> result=new ArrayList<>();
        for(int i=0;i<text.length();i++)result.add(new Detection(LABELS.indexOf(text.substring(i,i+1)),confidence,
                10+i*20,10,20+i*20,30,Collections.emptyList()));
        return result;
    }
    @Test public void matchingEvidenceIncreasesScoreAndDisagreementLowersItWithoutResetting() {
        DynamicCharacterEvidence e=new DynamicCharacterEvidence();
        double first=e.accept(source(1,1000),1,.9f,characters("AB12345",.9f),LABELS).confidence;
        double second=e.accept(source(2,2000),2,.9f,characters("AB12345",.9f),LABELS).confidence;
        assertTrue(second>first);
        TemporalCharacterAggregator.Result conflict=e.accept(source(3,3000),3,.9f,characters("AB1234S",.9f),LABELS);
        assertEquals("AB12345",conflict.text);assertTrue(conflict.confidence<second);assertFalse(conflict.stable);
        assertEquals(3,conflict.observations);
    }
    @Test public void differentFullStringsCanProduceAStableCharacterConsensus() {
        DynamicCharacterEvidence e=new DynamicCharacterEvidence();
        String[] reads={"XB12345","AY12345","ABX2345","AB1X345"};
        for(int i=0;i<reads.length;i++)e.accept(source(i+1,(i+1)*1000),i+1,.9f,characters(reads[i],.9f),LABELS);
        assertEquals("AB12345",e.current().text);assertTrue(e.current().stable);
    }
    @Test public void sourceDuplicatesAndEmptyReadsCannotInventMoreEvidence() {
        DynamicCharacterEvidence e=new DynamicCharacterEvidence();
        e.accept(source(1,1000),1,.9f,characters("AB12345",.9f),LABELS);
        e.accept(source(1,2000),2,.9f,characters("AB12345",.9f),LABELS);
        e.accept(source(2,1000),3,.9f,characters("AB12345",.9f),LABELS);
        assertEquals(1,e.current().observations);
        double before=e.current().confidence;
        e.accept(source(3,3000),4,.9f,Collections.emptyList(),LABELS);
        assertEquals(before,e.current().confidence,1e-9);
    }
    @Test public void newerEvidenceCanCorrectAnInitiallyStrongerWrongReading() {
        DynamicCharacterEvidence e=new DynamicCharacterEvidence();
        e.accept(source(1,1000),1,.3f,characters("AB1234S",.99f),LABELS);
        for(int i=2;i<=4;i++)e.accept(source(i,i*1000),i,.95f,characters("AB12345",.8f),LABELS);
        assertEquals("AB12345",e.current().text);assertTrue(e.current().stable);
        for(int i=5;i<=40;i++)e.accept(source(i,i*1000),i,.95f,characters("AB12345",.8f),LABELS);
        assertEquals(DynamicCharacterEvidence.WINDOW,e.current().observations);
    }
    @Test public void differentStructuresCompeteWithoutShiftingCharacterPositions() {
        DynamicCharacterEvidence e=new DynamicCharacterEvidence();
        e.accept(source(1,1000),1,.9f,characters("AB12345",.9f),LABELS);
        e.accept(source(2,2000),2,.9f,characters("AB12345",.9f),LABELS);
        double before=e.current().confidence;
        e.accept(source(3,3000),3,.9f,characters("AB1345",.9f),LABELS);
        assertEquals("AB12345",e.current().text);assertFalse(e.current().stable);
        assertTrue(e.current().confidence<before);
    }
    private PlateTrackCoordinator.Observation plate() {
        return new PlateTrackCoordinator.Observation(0,new MotionBoxTracker.Box(.2f,.4f,.5f,.5f),.9f,true,7);
    }
    private PlateTrackCoordinator.Decision decision(PlateTrackCoordinator c,long frame,long time) {
        c.beginSource(source(frame,time),true);
        return c.update(Collections.singletonList(plate()),frame,time*1_000_000L).get(0);
    }
    @Test public void realGeometryCyclesLimitRetriesIndependentlyOfWallClock() {
        PlateTrackCoordinator c=new PlateTrackCoordinator();
        PlateTrackCoordinator.Decision first=decision(c,1,1000);assertTrue(first.recognize);
        c.recordRecognition(first.trackId,.9f,1,characters("AB12345",.9f),LABELS);
        assertFalse(decision(c,2,1100).recognize);
        PlateTrackCoordinator.Decision next=decision(c,3,1750);assertTrue(next.recognize);
        c.recordRecognition(next.trackId,.9f,3,characters("AB12345",.9f),LABELS);
        assertFalse(decision(c,4,2500).recognize);
        assertFalse(decision(c,5,4750).recognize);
        for(int frame=6;frame<9;frame++)assertFalse(decision(c,frame,4750+frame).recognize);
        assertTrue(decision(c,9,4800).recognize);
    }
    @Test public void emptyExecutionsBackOffAndZoomStillHonorsTheBaseCadence() {
        PlateTrackCoordinator c=new PlateTrackCoordinator();
        PlateTrackCoordinator.Decision first=decision(c,1,1000);
        c.recordRecognition(first.trackId,.9f,1,Collections.emptyList(),LABELS);
        assertFalse(decision(c,2,1700).recognize);
        PlateTrackCoordinator.Decision second=decision(c,3,1750);assertTrue(second.recognize);
        c.recordRecognition(second.trackId,.9f,3,Collections.emptyList(),LABELS);
        assertFalse(decision(c,4,2500).recognize);
        c.requestFreshRecognitionAfterZoom();
        PlateTrackCoordinator.Decision zoom=decision(c,5,2600);assertTrue(zoom.recognize);
        c.recordRecognition(zoom.trackId,.9f,5,characters("AB12345",.9f),LABELS);
        assertFalse(decision(c,5,2600).recognize);
    }
    @Test public void softTechnicalRecoveryPreservesTheSameOwnersEvidenceAndRetryClock() {
        PlateTrackCoordinator c=new PlateTrackCoordinator();
        PlateTrackCoordinator.Decision first=decision(c,1,1000);c.bindEntityState(first.trackId,7,1);
        c.recordRecognition(first.trackId,.9f,1,characters("AB12345",.9f),LABELS);
        c.resetTechnicalTracks();
        PlateTrackCoordinator.Decision next=decision(c,2,1100);c.bindEntityState(next.trackId,7,1);
        next=c.identityDecision(next,plate(),2);
        assertNotEquals(first.trackId,next.trackId);assertNotNull(next.currentResult);
        assertEquals("AB12345",next.currentResult.text);assertFalse(next.recognize);
    }
    @Test public void aBetterImageCanRefineStableEvidenceBeforeThePeriodicCheck() {
        PlateTrackCoordinator c=new PlateTrackCoordinator();
        PlateTrackCoordinator.Observation low=new PlateTrackCoordinator.Observation(0,new MotionBoxTracker.Box(.2f,.4f,.5f,.5f),.5f,true,7);
        c.beginSource(source(1,1000),true);
        PlateTrackCoordinator.Decision d=c.update(Collections.singletonList(low),1,1_000_000_000L).get(0);
        c.recordRecognition(d.trackId,.5f,1,characters("AB12345",.9f),LABELS);
        c.beginSource(source(3,1750),true);d=c.update(Collections.singletonList(low),3,1_750_000_000L).get(0);
        c.recordRecognition(d.trackId,.5f,3,characters("AB12345",.9f),LABELS);
        assertFalse(decision(c,4,2500).recognize);
        PlateTrackCoordinator.Decision improved=decision(c,5,2600);
        assertTrue(improved.recognize);assertEquals("BETTER_CROP",improved.schedulingReason);
    }
    @Test public void stableTenSecondSyntheticSequenceUsesFewerMzCallsWithoutChangingItsText() {
        int[] calls=new int[2];
        for(int mode=0;mode<2;mode++) {
            PlateTrackCoordinator c=new PlateTrackCoordinator();
            for(int frame=1;frame<=200;frame++) {
                c.beginSource(source(frame,1000+frame*50L),mode==1);
                PlateTrackCoordinator.Decision d=c.update(Collections.singletonList(plate()),frame,(1000+frame*50L)*1_000_000L).get(0);
                if(d.recognize){calls[mode]++;c.recordRecognition(d.trackId,.9f,frame,characters("AB12345",.9f),LABELS);}
                assertEquals("AB12345",c.identityDecision(d,plate(),frame).currentResult.text);
            }
        }
        assertEquals(36,calls[0]);assertEquals(34,calls[1]);
        System.out.println("SYNTHETIC_SCHEDULER_200_FRAMES_10_SECONDS legacy_mz_calls="+calls[0]+" dynamic_mz_calls="+calls[1]);
    }
}
