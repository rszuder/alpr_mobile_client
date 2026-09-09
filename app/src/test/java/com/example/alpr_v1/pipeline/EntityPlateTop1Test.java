package com.example.alpr_v1.pipeline;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class EntityPlateTop1Test {
    private EntityPlateTop1.Candidate candidate(int index,long entity,boolean valid,float quality,float confidence,float previous) {
        return new EntityPlateTop1.Candidate(index,entity,valid,quality,confidence,.8f,previous,.1f,.9f);
    }
    @Test public void oneEntityWithThreeDetectionsSendsOnlyBestValidGeometryForward() {
        assertEquals(Collections.singletonList(2),EntityPlateTop1.select(Arrays.asList(
                candidate(0,1,false,1,1,1),candidate(1,1,true,.5f,.5f,0),
                candidate(2,1,true,.9f,.9f,.8f)),1));
    }
    @Test public void stableTieBreakerIsIndependentOfIterationOrder() {
        EntityPlateTop1.Candidate first = candidate(7,1,true,.8f,.8f,.8f);
        EntityPlateTop1.Candidate second = candidate(3,1,true,.8f,.8f,.8f);
        assertEquals(Collections.singletonList(3),EntityPlateTop1.select(Arrays.asList(first,second),0));
        assertEquals(Collections.singletonList(3),EntityPlateTop1.select(Arrays.asList(second,first),0));
    }
    @Test public void neighboringPlateKeepsOwnerAndCannotEnterRequestedTarget() {
        assertEquals(Collections.singletonList(0),EntityPlateTop1.select(Arrays.asList(
                candidate(0,1,true,.7f,.7f,0),candidate(1,2,true,1,1,1)),1));
        assertEquals(Arrays.asList(0,1),EntityPlateTop1.select(Arrays.asList(
                candidate(0,1,true,.7f,.7f,0),candidate(1,2,true,1,1,1)),0));
    }
    @Test public void ambiguousAndInvalidCandidatesAreDeferred() {
        assertTrue(EntityPlateTop1.select(Arrays.asList(candidate(0,0,true,1,1,1),
                candidate(1,1,false,1,1,1)),1).isEmpty());
    }
    @Test public void previousGeometryCanOutrankUnrelatedHigherConfidenceDetection() {
        assertEquals(Collections.singletonList(1),EntityPlateTop1.select(Arrays.asList(
                candidate(0,1,true,.9f,.95f,0),candidate(1,1,true,.85f,.8f,1)),1));
    }
}
