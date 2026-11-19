package com.example.alpr_v1.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicReadingSelectionTest {
    @Test public void badgeShowsAnyActualNonemptyReadingWithoutClaimingConfirmation() {
        assertFalse(DynamicReadingSelection.badgeEligible(PlateTextConsensus.EMPTY));
        assertFalse(DynamicReadingSelection.badgeEligible(null));
        assertTrue(DynamicReadingSelection.badgeEligible(new PlateTextConsensus("AB12345",.03f,1,false)));
        assertTrue(DynamicReadingSelection.badgeEligible(new PlateTextConsensus("AB12345",.1f,1,false)));
        assertTrue(DynamicReadingSelection.badgeEligible(new PlateTextConsensus("AB12345",.6f,2,true)));
        assertFalse(DynamicReadingSelection.confirmedForDisplay(false,.1,1));
    }
    @Test public void aHistoricalBestDoesNotFreezeCurrentEvidenceOrLeakToAnotherEntity() {
        VehicleEntityRepository r=new VehicleEntityRepository();
        VehicleEntity a=r.create(1,new NormalizedBounds(.1f,.1f,.4f,.8f),null,1);
        PlateTextConsensus strongest=new PlateTextConsensus("AB12345",.9f,3,true);
        r.updateDynamicRegistration(a.entityId(),strongest,10,true,RegistrationConsensusSource.FRESH_MZ);
        r.updateDynamicRegistration(a.entityId(),new PlateTextConsensus("AB1234S",.03f,4,false),20,true,RegistrationConsensusSource.FRESH_MZ);
        assertEquals("AB1234S",a.registration().text);assertFalse(a.registration().stable);
        assertSame(strongest,a.dynamicBadgeReading());assertSame(strongest,a.bestDynamicRegistration());
        assertEquals(10,a.dynamicBadgeSourceNanos());assertEquals(2,a.mzAttempts());
        assertFalse(r.updateDynamicRegistration(a.entityId(),new PlateTextConsensus("AB12346",.99f,8,true),15,true,RegistrationConsensusSource.FRESH_MZ));
        assertSame(strongest,a.dynamicBadgeReading());
        VehicleEntity b=r.create(2,new NormalizedBounds(.6f,.1f,.9f,.8f),null,30);
        assertFalse(b.dynamicBadgeReading().available());assertFalse(b.bestDynamicRegistration().available());
    }
}
