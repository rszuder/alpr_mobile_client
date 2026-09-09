package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.acquisition.DynamicMtConfig;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveMtRoiPolicyTest {
    private final NormalizedBounds car = new NormalizedBounds(.2f,.1f,.8f,.9f);
    private final NormalizedBounds plate = new NormalizedBounds(.4f,.65f,.6f,.72f);
    private final VehicleRoiSelector.Region expanded = new VehicleRoiSelector.Region(100,0,900,1000,null);
    private ContinuityStamp stamp(long time) { return new ContinuityStamp(1,1,0,time); }
    private VehicleRoi owner() { return new VehicleRoi(new VehicleCandidate(1,1,car,.9f,.9f,0,false,0,1,1),100,0,900,1000); }
    private AdaptiveMtRoiPolicy.Plan plan(AdaptiveMtRoiPolicy policy, ContinuityStamp stamp) {
        return policy.select(owner(),car,expanded,stamp,1000,1000,false,DynamicMtConfig.INITIAL);
    }
    @Test public void unknownPlateUsesConfigurableLowerVehicleRegionWithMargins() {
        AdaptiveMtRoiPolicy policy = new AdaptiveMtRoiPolicy();
        AdaptiveMtRoiPolicy.Plan first = plan(policy,stamp(1));
        assertEquals(AdaptiveMtRoiPolicy.Kind.PRIMARY_LOWER_VEHICLE,first.kind);
        assertEquals(380,first.region.top,1);
        assertTrue(first.region.left < 200);
        assertTrue(first.region.right > 800);
        assertTrue(first.region.bottom > 900);
        AdaptiveMtRoiPolicy.Plan changed = policy.select(owner(),car,expanded,stamp(2),1000,1000,
                false,new DynamicMtConfig(DynamicMtConfig.INITIAL.size,.4f));
        assertEquals(420,changed.region.top,1);
    }
    @Test public void freshKnownPlateUsesLocalMarginThenFallsBackThroughPrimaryAndExpanded() {
        AdaptiveMtRoiPolicy policy = new AdaptiveMtRoiPolicy();
        policy.remember(1,car,plate,stamp(1_000_000_000L));
        AdaptiveMtRoiPolicy.Plan local = plan(policy,stamp(2_000_000_000L));
        assertEquals(AdaptiveMtRoiPolicy.Kind.LOCAL_PLATE,local.kind);
        assertTrue(local.region.left < 400 && local.region.right > 600);
        assertTrue(local.region.top < 650 && local.region.bottom > 720);
        assertTrue(local.region.area() < expanded.area());
        policy.result(1,local.kind,false);
        assertEquals(AdaptiveMtRoiPolicy.Kind.PRIMARY_LOWER_VEHICLE,plan(policy,stamp(2_000_000_001L)).kind);
        policy.result(1,AdaptiveMtRoiPolicy.Kind.PRIMARY_LOWER_VEHICLE,false);
        AdaptiveMtRoiPolicy.Plan retry = plan(policy,stamp(2_000_000_002L));
        assertEquals(AdaptiveMtRoiPolicy.Kind.EXPANDED_VEHICLE,retry.kind);
        assertSame(expanded,retry.region);
    }
    @Test public void staleZoomSceneEpochAndResetCannotReuseLocalAnchor() {
        for (ContinuityStamp next : new ContinuityStamp[]{
                new ContinuityStamp(1,1,1,2), new ContinuityStamp(2,1,0,2),
                new ContinuityStamp(1,2,0,2), stamp(AdaptiveMtRoiPolicy.LOCAL_MAX_AGE_NANOS+2)}) {
            AdaptiveMtRoiPolicy policy = new AdaptiveMtRoiPolicy();
            policy.remember(1,car,plate,stamp(1));
            assertEquals(AdaptiveMtRoiPolicy.Kind.PRIMARY_LOWER_VEHICLE,plan(policy,next).kind);
        }
        AdaptiveMtRoiPolicy policy = new AdaptiveMtRoiPolicy();
        policy.remember(1,car,plate,stamp(1)); policy.reset();
        assertEquals(AdaptiveMtRoiPolicy.Kind.PRIMARY_LOWER_VEHICLE,plan(policy,stamp(2)).kind);
    }
    @Test public void explicitExpandedRetryWinsOverAvailableLocalAnchor() {
        AdaptiveMtRoiPolicy policy = new AdaptiveMtRoiPolicy();
        policy.remember(1,car,plate,stamp(1));
        assertEquals(AdaptiveMtRoiPolicy.Kind.EXPANDED_VEHICLE,
                policy.select(owner(),car,expanded,stamp(2),1000,1000,true,DynamicMtConfig.INITIAL).kind);
    }
    @Test public void associationUsesRawMpGeometryInsteadOfLaterDisplayPrediction() {
        VehicleCandidate projected = new VehicleCandidate(1,2,new NormalizedBounds(.6f,.2f,.9f,.7f),
                .9f,.2f,0,true,0,1,100);
        VehicleCandidate source = MobileAlprEngine.sourceMeasuredCandidates(Collections.singletonList(projected),
                Collections.singletonMap(1L,car),1).get(0);
        assertEquals(car,source.bounds);
        assertFalse(source.predicted);
        assertEquals(0,source.predictionAgeNanos);
        assertTrue(MobileAlprEngine.sourceMeasuredCandidates(Collections.singletonList(projected),
                Collections.emptyMap(),1).isEmpty());
    }
}
