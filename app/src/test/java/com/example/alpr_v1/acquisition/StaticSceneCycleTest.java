package com.example.alpr_v1.acquisition;

import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.vision.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StaticSceneCycleTest {
    @Test public void az1az2NoZoomBeforeBaselineOrWithoutValidQuad() {
        StaticSceneCycle cycle=new StaticSceneCycle(); cycle.reset(1L);
        cycle.observe(observation(1L,1L,true,false));
        assertNull(cycle.nextRefinement(true));
        assertEquals(StaticSceneCycle.Phase.BASELINE,cycle.phase());
        cycle.finishBaseline();
        assertNotNull(cycle.nextRefinement(true));
        StaticSceneCycle noQuad=new StaticSceneCycle(); noQuad.reset(1L);
        noQuad.observe(observation(1L,1L,false,false)); noQuad.finishBaseline();
        assertNull(noQuad.nextRefinement(true));
        assertEquals(StaticSceneCycle.Phase.STATIC_IDLE,noQuad.phase());
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
    @Test public void strongConfirmedPlateDoesNotNeedRefinementAndDisabledAzGoesIdle() {
        StaticSceneCycle cycle=new StaticSceneCycle(); cycle.reset(1L);
        cycle.observe(observation(1L,1L,true,true)); cycle.finishBaseline();
        assertNull(cycle.nextRefinement(true));
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
    private static PlateObservation observation(long scene,long entity,boolean quad,boolean strong) {
        List<Point2> corners=quad?Arrays.asList(new Point2(30,45),new Point2(70,45),new Point2(70,55),new Point2(30,55)):Collections.emptyList();
        PlateGeometry geometry=PlateGeometry.from(100,100,new Detection(0,.9f,30,45,70,55,Collections.emptyList()),corners);
        return new PlateObservation(entity+10L,PlateVehicleAssociation.direct(entity,entity,"test"),MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,1L,null,"WI1234A",.9,strong?.95:.4,strong,strong?3:1,
                Collections.emptyList(),0L,1L,.8f,null,null,geometry,true,true,"WI1234A",strong,1,
                "single_row",Collections.emptyList(),"","WI1234A",new ContinuityStamp(scene,0L,0L,1L));
    }
}
