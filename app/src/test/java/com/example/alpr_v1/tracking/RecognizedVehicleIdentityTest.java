package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Synthetic MP and fresh raw plate evidence; no model, camera, GT or research session. */
public class RecognizedVehicleIdentityTest {
    static final long SECOND=1_000_000_000L;
    static VehicleTrackManager.Observation box(float x) {
        return new VehicleTrackManager.Observation(new NormalizedBounds(x,.2f,x+.2f,.5f),.9f,new AppearanceDescriptor(new float[]{1,0}),0);
    }
    @Test public void freshUnambiguousGeometryKeepsTheOwnerWithoutRequiringOcr() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        assertEquals(a,r.mp(.21f,1_200_000_000L));assertEquals(1,r.number(a));
        assertEquals("WA12345",r.repo.get(a).registration().text);assertEquals(1,r.manager.stats().entitiesCreated);
    }

    static long current(List<VehicleTrackManager.Snapshot> values,int source) {
        for(VehicleTrackManager.Snapshot value:values)if(!value.predicted&&value.sourceIndex==source)return value.entityId;
        throw new AssertionError("No measured MP source "+source);
    }
    static VehicleTrackManager.Observation box(float x,int index) {
        VehicleTrackManager.Observation o=box(x);
        return new VehicleTrackManager.Observation(o.bounds,o.confidence,o.appearance,index);
    }
    static class Rig {
        final VehicleEntityRepository repo=new VehicleEntityRepository();
        final VehicleTrackManager manager=new VehicleTrackManager(repo,16,SECOND,5*SECOND);
        Rig(){manager.setNormalDynamicIdentity(true,SECOND);}
        long mp(float x,long at){return current(manager.update(Collections.singletonList(box(x)),at),0);}
        void identify(long id,String key){repo.updateRegistration(id,new PlateTextConsensus(key,.99f,3,true),SECOND,true,RegistrationConsensusSource.FRESH_MZ);}
        DynamicVehicleIdentity.Stamp stamp(long id){return manager.identity().stamp(id,repo.get(id).vehicleTrackId());}
        long read(long id,String key,long frame,long at){return manager.verifyFresh(stamp(id),key,frame,frame,at);}
        long number(long id){return manager.identity().presentation().get(id).number;}
    }
    @Test public void asynchronousPreviewCanBridgeItsExistingFreshnessWindowButNotAnUnboundedGap() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        r.manager.identity().preview(1_100_000_000L,Collections.singleton(a),true);
        assertEquals(a,r.mp(.2f,1_200_000_000L));
        r.manager.identity().preview(1_150_000_000L,Collections.singleton(a),true);
        assertEquals(a,r.mp(.2f,1_400_000_000L));
        r.manager.identity().preview(1_160_000_000L,Collections.emptySet(),true);
        assertTrue(r.manager.identity().accepts(r.stamp(a)));
        assertEquals(a,r.mp(.2f,1_600_000_000L));
        assertNotEquals(a,r.mp(.2f,6_600_000_001L));
    }
    @Test public void freshGeometryRecoversTechnicalTrackAndKeepsOriginalNumberAndCount() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");long oldTrack=r.repo.get(a).vehicleTrackId();
        DynamicVehicleIdentity.Stamp stale=r.stamp(a);r.manager.update(Collections.emptyList(),1_100_000_000L);
        r.manager.predict(2_100_000_001L);assertEquals(a,r.mp(.21f,2_200_000_000L));
        assertEquals(1,r.number(a));assertNotEquals(oldTrack,r.repo.get(a).vehicleTrackId());
        assertFalse(r.manager.identity().accepts(stale));assertEquals(1,r.manager.stats().entitiesCreated);
        assertEquals(1,r.manager.stats().entityReassociations);
    }
    @Test public void differentReadsUpdateCharacterHypothesisWithoutCreatingAnotherVehicle() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");long b=r.mp(.21f,1_200_000_000L);
        assertEquals(a,b);r.read(b,"WB98765",2,1_300_000_000L);r.read(b,"WB98765",3,1_400_000_000L);
        assertEquals(1,r.number(b));assertEquals(1,r.manager.stats().entitiesCreated);
        r.repo.updateDynamicRegistration(b,new PlateTextConsensus("WB98765",.65f,2,true),1_400_000_000L,true,RegistrationConsensusSource.FRESH_MZ);
        assertEquals("WB98765",r.repo.get(a).registration().text);
    }
    @Test public void duplicateEmptyAndPartialOcrCannotChangePhysicalIdentity() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");long c=r.mp(.21f,1_200_000_000L);
        for(String raw:Arrays.asList("WA12345","WA12345","","WA","WA12"))assertEquals(a,r.read(c,raw,2,1_300_000_000L));
        assertEquals(1,r.number(c));assertFalse(r.manager.identity().verifying(c));
        assertEquals(3,r.repo.get(a).registration().observations);
    }
    @Test public void contradictoryRawReadDoesNotRestartObjectIdentity() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");long c=r.mp(.21f,1_200_000_000L);
        r.read(c,"WA12345",2,1_300_000_000L);r.read(c,"WB98765",3,1_400_000_000L);
        assertEquals(a,c);assertFalse(r.manager.identity().verifying(c));assertEquals(1,r.number(c));
    }
    @Test public void localObjectProofKeepsOwnerAcrossChangedDetectionIndices() {
        Rig r=new Rig();List<VehicleTrackManager.Snapshot> first=r.manager.update(Arrays.asList(box(.1f,0),box(.65f,1)),SECOND);
        long a=current(first,0),b=current(first,1);r.identify(a,"WA12345");r.identify(b,"WB98765");
        r.manager.identity().preview(1_200_000_000L,new HashSet<>(Arrays.asList(a,b)),true);
        List<VehicleTrackManager.Snapshot> next=r.manager.update(Arrays.asList(box(.64f,0),box(.11f,1)),1_200_000_000L);
        assertEquals(a,current(next,1));assertEquals(b,current(next,0));assertEquals(2,r.manager.stats().entitiesCreated);
    }
    @Test public void observedLossOrExpiredUnavailableTrackingInvalidatesSameSceneOwnerIncludingDelayedMz() {
        for(boolean global:new boolean[]{false,true}){
            Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");DynamicVehicleIdentity.Stamp owner=r.stamp(a);
            r.manager.identity().preview(1_100_000_000L,Collections.singleton(a),true);
            r.manager.identity().preview(1_200_000_000L,global?Collections.singleton(a):Collections.emptySet(),!global);
            if(global) {
                assertTrue("Unavailable tracking is not an observed loss",r.manager.identity().accepts(owner));
                r.manager.identity().preview(2_200_000_000L,Collections.singleton(a),false);
            } else {
                assertTrue("A brief miss coasts without changing the owner",r.manager.identity().accepts(owner));
                r.manager.identity().preview(1_700_000_001L,Collections.emptySet(),true);
            }
            assertFalse(r.manager.identity().accepts(owner));assertFalse(r.manager.identity().sourceCurrent(1_100_000_000L));
            assertEquals(0,r.manager.verifyFresh(owner,"WA12345",4,4,1_300_000_000L));
        }
    }
    @Test public void missingMpAndReappearanceAllowNewTechnicalTrackButNotANewVehicle() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        r.manager.update(Collections.emptyList(),1_100_000_000L);r.manager.predict(2_100_000_001L);
        assertEquals(a,r.mp(.21f,2_200_000_000L));assertEquals(1,r.number(a));
    }
    @Test public void deadlineIsInclusiveAndSelectionPredictionOrTtlChangeDoNotExtendIt() {
        for(long offset:new long[]{0,1}) {
            Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
            r.manager.update(Collections.emptyList(),1_100_000_000L);
            r.manager.setPriorityTarget(a,4*SECOND);r.manager.setTrackTtlNanos(4*SECOND);r.manager.predict(5_900_000_000L);
            long resolved=r.mp(.21f,6*SECOND+offset);assertEquals(offset==0,a==resolved);
            assertEquals(offset==0?1:2,r.number(resolved));
        }
    }
    @Test public void expiredGeometryWindowCannotBeReopenedByMatchingText() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        r.manager.update(Collections.emptyList(),1_100_000_000L);long c=r.mp(.21f,6_100_000_000L);
        assertNotEquals(a,c);assertEquals(2,r.number(c));assertEquals(c,r.read(c,"WA12345",9,6_200_000_000L));
        assertEquals(2,r.manager.stats().entitiesCreated);
    }
    @Test public void activeNeighborAndIdenticalPlateTextCannotMergeObjects() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        r.manager.identity().preview(1_200_000_000L,Collections.singleton(a),true);
        List<VehicleTrackManager.Snapshot> next=r.manager.update(Arrays.asList(box(.21f,0),box(.7f,1)),1_200_000_000L);
        long b=current(next,1);assertEquals(a,current(next,0));assertNotEquals(a,b);assertFalse(r.manager.identity().verifying(b));
        assertEquals(b,r.read(b,"WA12345",4,1_300_000_000L));assertEquals(2,r.number(b));
    }
    @Test public void ambiguousParentsStayPendingAndReclamationIsOneToOne() {
        Rig r=new Rig();List<VehicleTrackManager.Snapshot> first=r.manager.update(Arrays.asList(box(.2f,0),box(.21f,1)),SECOND);
        long a=current(first,0),b=current(first,1);r.identify(a,"WA12345");r.identify(b,"WA12345");
        long c=r.mp(.21f,1_200_000_000L);r.read(c,"WA12345",2,1_300_000_000L);r.read(c,"WA12345",3,1_400_000_000L);
        assertTrue(r.manager.identity().verifying(c));assertEquals(0,r.number(c));
        Rig one=new Rig();long old=one.mp(.2f,SECOND);one.identify(old,"WA12345");
        List<VehicleTrackManager.Snapshot> pair=one.manager.update(Arrays.asList(box(.21f,0),box(.22f,1)),1_200_000_000L);
        long x=current(pair,0),y=current(pair,1);one.read(x,"WA12345",2,1_300_000_000L);assertEquals(x,one.read(x,"WA12345",3,1_400_000_000L));
        assertEquals(y,one.read(y,"WA12345",4,1_500_000_000L));assertTrue(one.manager.identity().verifying(y));
        assertEquals(0,one.number(x));assertEquals(0,one.number(y));assertEquals(1,one.manager.stats().entitiesCreated);
    }
    @Test public void missingOrDifferentAppearanceCannotBorrowARegistration() {
        for(AppearanceDescriptor changed:Arrays.asList(new AppearanceDescriptor(null),new AppearanceDescriptor(new float[]{0,1}))){
            Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
            VehicleTrackManager.Observation o=new VehicleTrackManager.Observation(box(.21f).bounds,.9f,changed,0);
            long b=current(r.manager.update(Collections.singletonList(o),1_200_000_000L),0);
            assertNotEquals(a,b);assertFalse(r.manager.identity().verifying(b));assertFalse(r.repo.get(b).registration().available());
        }
    }
    @Test public void explicitSceneAndIndependentRunInvalidateEpochsButPauseDoesNot() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");DynamicVehicleIdentity.Stamp old=r.stamp(a);
        r.manager.predict(1_100_000_000L);assertTrue(r.manager.identity().accepts(old));
        r.manager.resetScene();r.manager.identity().beginRunIfEmpty();long b=r.mp(.2f,2*SECOND);
        assertNotEquals(a,b);assertFalse(r.manager.identity().accepts(old));assertEquals(1,r.number(b));
    }
    @Test public void staticAndResearchTrackerPolicyKeepsLegacyMatchingWithoutIdentityState() {
        Rig r=new Rig();r.manager.setNormalDynamicIdentity(false,SECOND);long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        assertEquals(a,r.mp(.21f,1_200_000_000L));assertTrue(r.manager.identity().presentation().isEmpty());
    }
    @Test public void trackExpiryWithoutSampledMissUsesGeometryRatherThanPlateReads() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");
        assertEquals(a,r.mp(.21f,2_200_000_000L));assertFalse(r.manager.identity().verifying(a));
        assertEquals(1,r.number(a));assertEquals(1,r.manager.stats().entitiesCreated);
    }
    @Test public void outOfOrderPreviewCannotInvalidateMoreRecentLocalObjectEvidence() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");DynamicVehicleIdentity.Stamp owner=r.stamp(a);
        r.manager.identity().preview(1_300_000_000L,Collections.singleton(a),true);
        r.manager.identity().preview(1_200_000_000L,Collections.emptySet(),true);
        assertTrue(r.manager.identity().accepts(owner));assertEquals(a,r.mp(.21f,1_300_000_000L));
    }
    @Test public void priorityOwnerCanBeRefreshedByGeometryWithoutAnotherIdentity() {
        Rig r=new Rig();long a=r.mp(.2f,SECOND);r.identify(a,"WA12345");r.manager.setPriorityTarget(a,SECOND);
        assertEquals(a,r.mp(.21f,1_200_000_000L));assertEquals(a,r.mp(.21f,1_250_000_000L));
        assertEquals(1,r.manager.stats().entitiesCreated);assertEquals(1,r.number(a));
    }
}
