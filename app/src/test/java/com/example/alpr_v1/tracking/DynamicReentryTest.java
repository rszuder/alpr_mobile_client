package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DynamicReentryTest {
    private static final long S=1_000_000_000L;
    private static VehicleTrackManager.Observation box(float x,int index,float... appearance) {
        return new VehicleTrackManager.Observation(new NormalizedBounds(x,.2f,x+.2f,.5f),.9f,
                new AppearanceDescriptor(appearance),index);
    }
    private static final class Rig {
        final VehicleEntityRepository repo=new VehicleEntityRepository();
        final VehicleTrackManager manager=new VehicleTrackManager(repo,16,S,60*S);
        Rig(){manager.setNormalDynamicIdentity(true,S);}
        long mp(float x,long time){return measured(manager.update(Collections.singletonList(box(x,0,1,0)),time),0);}
        long number(long id){return manager.identity().ownerNumber(id);}
    }
    private static long measured(List<VehicleTrackManager.Snapshot> snapshots,int index) {
        for(VehicleTrackManager.Snapshot s:snapshots)if(!s.predicted&&s.sourceIndex==index)return s.entityId;
        throw new AssertionError("No current MP observation "+index);
    }
    @Test public void repeatedRealMpDoesNotNeedOcrToKeepOneVehicleAndNumber() {
        Rig r=new Rig();long id=r.mp(.2f,S);
        for(int i=1;i<=90;i++)assertEquals(id,r.mp(.2f+(i%3)*.003f,S+i*2*S));
        assertEquals(1,r.number(id));assertEquals(1,r.manager.stats().entitiesCreated);
    }
    @Test public void reentryAtFourPointNineSecondsKeepsPnAndConsensusWithoutMz() {
        Rig r=new Rig();long id=r.mp(.2f,S);
        r.repo.updateDynamicRegistration(id,new PlateTextConsensus("WA12345",.9f,4,true),S,true,RegistrationConsensusSource.FRESH_MZ);
        DynamicVehicleIdentity.Stamp stale=r.manager.identity().stamp(id,r.repo.get(id).vehicleTrackId());
        r.manager.update(Collections.emptyList(),S+100_000_000L);
        assertEquals(id,r.mp(.21f,S+4_900_000_000L));
        assertEquals(1,r.number(id));assertEquals("WA12345",r.repo.get(id).registration().text);
        assertFalse(r.manager.identity().accepts(stale));
    }
    @Test public void fiveSecondDeadlineIsInclusiveAndPredictionDoesNotRenewIt() {
        for(long extra:new long[]{0,1}) {
            Rig r=new Rig();long id=r.mp(.2f,S);
            r.manager.update(Collections.emptyList(),S+100_000_000L);
            r.manager.setTrackTtlNanos(20*S);r.manager.setPriorityTarget(id,20*S);
            r.manager.predict(5*S);
            long returned=r.mp(.21f,6*S+extra);
            assertEquals(extra==0,id==returned);
            assertEquals(extra==0?1:2,r.number(returned));
        }
    }
    @Test public void differentAppearanceDoesNotBorrowIdentityEvenWithIdenticalPlateText() {
        Rig r=new Rig();long id=r.mp(.2f,S);
        r.manager.update(Collections.emptyList(),S+100_000_000L);
        long other=measured(r.manager.update(Collections.singletonList(box(.21f,0,0,1)),2*S),0);
        assertNotEquals(id,other);
        DynamicVehicleIdentity.Stamp owner=r.manager.identity().stamp(other,r.repo.get(other).vehicleTrackId());
        assertEquals(other,r.manager.verifyFresh(owner,"WA12345",3,3,2*S+100_000_000L));
        assertEquals(2,r.number(other));
    }
    @Test public void ambiguousGeometryStaysUnnumberedAndOcrCannotResolveIt() {
        Rig r=new Rig();
        r.manager.update(Arrays.asList(box(.2f,0,1,0),box(.21f,1,1,0)),S);
        r.manager.update(Collections.emptyList(),S+100_000_000L);
        long pending=r.mp(.205f,2*S);
        assertTrue(r.manager.identity().verifying(pending));assertEquals(0,r.number(pending));
        DynamicVehicleIdentity.Stamp stamp=r.manager.identity().stamp(pending,r.repo.get(pending).vehicleTrackId());
        r.manager.verifyFresh(stamp,"WA12345",3,3,2*S+100_000_000L);
        r.manager.verifyFresh(stamp,"WA12345",4,4,2*S+200_000_000L);
        assertTrue(r.manager.identity().verifying(pending));assertEquals(0,r.number(pending));
    }
    @Test public void oneParentCannotBeAssignedToTwoCurrentObservations() {
        Rig r=new Rig();long id=r.mp(.2f,S);
        r.manager.update(Collections.emptyList(),S+100_000_000L);
        List<VehicleTrackManager.Snapshot> next=r.manager.update(Arrays.asList(box(.205f,0,1,0),box(.21f,1,1,0)),2*S);
        for(int i=0;i<2;i++) {
            long candidate=measured(next,i);
            assertNotEquals(id,candidate);assertEquals(0,r.number(candidate));
        }
    }
    @Test public void expiringAmbiguousParentsCannotCreateAnUnobservedNumberOrPzero() {
        Rig r=new Rig();r.manager.update(Arrays.asList(box(.2f,0,1,0),box(.21f,1,1,0)),S);
        r.manager.update(Collections.emptyList(),S+100_000_000L);long pending=r.mp(.205f,2*S);
        r.manager.setTrackTtlNanos(20*S);
        r.manager.predict(6*S+1);assertTrue(r.manager.identity().verifying(pending));assertEquals(0,r.number(pending));
        assertEquals(2,r.manager.stats().entitiesCreated);
        long observed=r.mp(.205f,6*S+100_000_000L);assertEquals(3,r.number(observed));
        assertEquals(pending,observed);
        assertEquals(3,r.manager.stats().entitiesCreated);
    }
}
