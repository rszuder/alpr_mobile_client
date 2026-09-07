package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.vision.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StaticSceneCycleTest {
    @Test public void noZoomBeforeBaselineButDetectedBoxCanRefineMissingCorners() {
        StaticSceneCycle cycle=new StaticSceneCycle(); cycle.reset(1L);
        cycle.observe(observation(1L,1L,true,false));
        assertNull(cycle.nextRefinement(true));
        assertEquals(StaticSceneCycle.Phase.BASELINE,cycle.phase());
        cycle.finishBaseline();
        assertNotNull(cycle.nextRefinement(true));
        StaticSceneCycle noQuad=new StaticSceneCycle(); noQuad.reset(1L);
        noQuad.observe(observation(1L,1L,false,false)); noQuad.finishBaseline();
        assertNotNull(noQuad.nextRefinement(true));
        assertTrue(noQuad.refining());
    }
    @Test public void az3az4az5EachEntityGetsOneCycleThenIdleAndNewSceneGetsNewBudget() {
        StaticSceneCycle cycle=new StaticSceneCycle(); cycle.reset(1L);
        cycle.observe(observation(1L,1L,true,false)); cycle.observe(observation(1L,2L,true,false));
        cycle.finishBaseline();
        assertNotNull(cycle.nextRefinement(true)); assertEquals(1L,cycle.zoomEntity());
        assertNull(cycle.nextRefinement(true)); cycle.finishZoom();
        assertNotNull(cycle.nextRefinement(true)); assertEquals(2L,cycle.zoomEntity()); cycle.finishZoom();
        assertNull(cycle.nextRefinement(true)); assertEquals(StaticSceneCycle.Phase.STATIC_IDLE,cycle.phase());
        cycle.reset(2L); cycle.observe(observation(1L,1L,true,false)); cycle.finishBaseline();
        assertNull(cycle.nextRefinement(true));
        cycle.reset(2L); cycle.observe(observation(2L,3L,true,false)); cycle.finishBaseline();
        assertNotNull(cycle.nextRefinement(true)); assertEquals(3L,cycle.zoomEntity());
    }
    @Test public void strongConfirmedPlateAlsoGetsZoomAndDisabledAzGoesIdle() {
        StaticSceneCycle cycle=new StaticSceneCycle(); cycle.reset(1L);
        cycle.observe(observation(1L,1L,true,true)); cycle.finishBaseline();
        assertNotNull(cycle.nextRefinement(true));
        cycle.reset(2L); cycle.observe(observation(2L,2L,true,false)); cycle.finishBaseline();
        assertNull(cycle.nextRefinement(false)); assertEquals(StaticSceneCycle.Phase.STATIC_IDLE,cycle.phase());
    }
    @Test public void technicalZoomCannotStartFromVehicleOnly() {
        AutoZoomController controller=new AutoZoomController(); controller.setEnabled(true);
        AutoZoomController.Sample invalid=new AutoZoomController.Sample(1L,.5f,.5f,.1f,.2,false,1,false,true,false,"",true);
        assertEquals(AutoZoomController.Action.NONE,controller.requestRefinement(invalid).action);
        AutoZoomController.Sample valid=new AutoZoomController.Sample(1L,.5f,.5f,.1f,.2,false,1,true,true,false,"",true);
        assertEquals(AutoZoomController.Action.REQUEST_ZOOM,controller.requestRefinement(valid).action);
    }
    @Test public void eachPlateOnTheSameVehicleAndUnassignedPlateGetsItsOwnZoom() {
        StaticSceneCycle cycle = new StaticSceneCycle(); cycle.reset(1L);
        cycle.observe(observation(1L,1L,11L,true,true));
        cycle.observe(observation(1L,1L,12L,true,true));
        cycle.observe(observation(1L,0L,13L,false,false));
        cycle.finishBaseline();
        for (long track : new long[]{11L,12L,13L}) {
            assertEquals(track, cycle.nextRefinement(true).trackId);
            assertTrue(cycle.refining());
            assertNull(cycle.nextRefinement(true));
            cycle.finishZoom();
        }
        assertNull(cycle.nextRefinement(true));
        assertEquals(StaticSceneCycle.Phase.STATIC_IDLE, cycle.phase());
    }

    @Test public void emptyFreshMpRemovesBaselineBoxesAndTheirPendingZooms() {
        StaticSceneCycle cycle = new StaticSceneCycle(); cycle.reset(1L);
        com.example.alpr_v1.tracking.VehicleCandidate car = new com.example.alpr_v1.tracking.VehicleCandidate(
                1L,1L,new com.example.alpr_v1.domain.NormalizedBounds(.1f,.1f,.8f,.8f),
                .9f,.9f,0f,false,0,1L,1L);
        cycle.observeVehicles(new com.example.alpr_v1.tracking.VehicleTrackingFrame(
                1L,1L,1L,1L,Collections.singletonList(car)));
        cycle.observe(observation(1L,1L,true,true));
        assertEquals(1, cycle.baselineVehicles().size());
        cycle.observeVehicles(new com.example.alpr_v1.tracking.VehicleTrackingFrame(
                2L,2L,2L,1L,Collections.emptyList()));
        assertTrue(cycle.baselineVehicles().isEmpty());
        cycle.finishBaseline(); assertNull(cycle.nextRefinement(true));
    }

    @Test public void staticDetectedBoxDoesNotRequirePreviousCharacterInference() {
        AutoZoomController controller = new AutoZoomController(); controller.setEnabled(true);
        AutoZoomController.Sample detected = new AutoZoomController.Sample(
                1L,.5f,.5f,.1f,0,false,0,false,false,false,"",false);
        assertEquals(AutoZoomController.Action.NONE, controller.requestRefinement(detected).action);
        assertEquals(AutoZoomController.Action.REQUEST_ZOOM, controller.requestStaticRefinement(detected).action);
    }

    private static PlateObservation observation(long scene,long entity,boolean quad,boolean strong) {
        return observation(scene,entity,entity+10L,quad,strong);
    }
    private static PlateObservation observation(long scene,long entity,long track,boolean quad,boolean strong) {
        List<Point2> corners=quad?Arrays.asList(new Point2(30,45),new Point2(70,45),new Point2(70,55),new Point2(30,55)):Collections.emptyList();
        PlateGeometry geometry=PlateGeometry.from(100,100,new Detection(0,.9f,30,45,70,55,Collections.emptyList()),corners);
        return new PlateObservation(track,entity > 0L ? PlateVehicleAssociation.direct(entity,entity,"test")
                : PlateVehicleAssociation.unassigned("test"),MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,1L,null,"WI1234A",.9,strong?.95:.4,strong,strong?3:1,
                Collections.emptyList(),0L,1L,.8f,null,null,geometry,true,true,"WI1234A",strong,1,
                "single_row",Collections.emptyList(),"","WI1234A",new ContinuityStamp(scene,0L,0L,1L));
    }
}
