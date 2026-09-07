package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.continuity.SceneContinuityState;
import com.example.alpr_v1.domain.TargetPurpose;
import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicZoomPolicyTest {
    private AutoZoomController.Sample sample(boolean quad,boolean mz,boolean stable,float x) {
        return new AutoZoomController.Sample(1L,x,.5f,.1f,.4,false,2,quad,mz,mz,"WI1234A",stable);
    }
    @Test public void az6ScanNeverOwnsAutomaticZoom() {
        assertFalse(DynamicZoomPolicy.allows(TargetPurpose.SCAN_ACQUISITION,sample(true,true,true,.5f),false,SceneContinuityState.STABLE));
    }
    @Test public void az7OnlyStableSafeForegroundTargetsWithNormalMzCanZoom() {
        for(TargetPurpose purpose:new TargetPurpose[]{TargetPurpose.USER_PICK,TargetPurpose.SEARCH_PURSUIT,TargetPurpose.SEARCH_VERIFICATION}) {
            assertTrue(DynamicZoomPolicy.allows(purpose,sample(true,true,true,.5f),false,SceneContinuityState.STABLE));
            assertFalse(DynamicZoomPolicy.allows(purpose,sample(false,true,true,.5f),false,SceneContinuityState.STABLE));
            assertFalse(DynamicZoomPolicy.allows(purpose,sample(true,false,true,.5f),false,SceneContinuityState.STABLE));
            assertFalse(DynamicZoomPolicy.allows(purpose,sample(true,true,false,.5f),false,SceneContinuityState.STABLE));
            assertFalse(DynamicZoomPolicy.allows(purpose,sample(true,true,true,.95f),false,SceneContinuityState.STABLE));
            assertFalse(DynamicZoomPolicy.allows(purpose,sample(true,true,true,.5f),true,SceneContinuityState.STABLE));
            assertFalse(DynamicZoomPolicy.allows(purpose,sample(true,true,true,.5f),false,SceneContinuityState.REACQUIRING));
        }
    }
    @Test public void az8MotionLossOrBoundaryAbortsOpticalCycle() {
        assertTrue(DynamicZoomPolicy.shouldAbort(true,false,SceneContinuityState.STABLE));
        assertTrue(DynamicZoomPolicy.shouldAbort(false,true,SceneContinuityState.STABLE));
        assertTrue(DynamicZoomPolicy.shouldAbort(false,false,SceneContinuityState.REACQUIRING));
        assertTrue(DynamicZoomPolicy.shouldAbort(false,false,SceneContinuityState.HARD_RESETTING));
        assertFalse(DynamicZoomPolicy.shouldAbort(false,false,SceneContinuityState.STABLE));
    }
}
