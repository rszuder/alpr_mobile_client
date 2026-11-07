package com.example.alpr_v1.setup;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.example.alpr_v1.setup.TvSetupGeometryTest.*;

public class TvDistanceCalculatorTest {
    @Test public void fieldOfViewDoesNotRequireAnyTvDimensionsAndMatchesDistanceEstimator() throws Exception {
        JSONObject meta=camera(1,new double[]{0,0,4000,3000});CameraPreviewGeometry g=sensorGeometry(1,1280,960);
        TvDistanceCalculator.FieldOfView fov=TvDistanceCalculator.effectiveFov(meta,g);assertTrue(fov.available());
        TvDistanceCalculator.Estimate estimate=TvDistanceCalculator.fromCamera(tv(55,.05),meta,g);
        assertEquals(estimate.fovH,fov.horizontalDegrees);assertEquals(estimate.fovV,fov.verticalDegrees);
        assertFalse(TvDistanceCalculator.effectiveFov(null,g).available());
        assertFalse(TvDistanceCalculator.effectiveFov(meta,g.withPhysicalCameraId("unverified-physical")).available());
    }
    private static TvDistanceCalculator.Inputs tv(double inches,double m){return TvDistanceCalculator.Inputs.diagonal(inches,16,9,m);}
    private static CameraPreviewGeometry sensorGeometry(double zoom,int w,int h){return new CameraPreviewGeometry(w,h,w,h,new double[]{0,0,w,h},0,"0","BACK",zoom,1,1,w,h,ID,new double[]{0,0,w,h});}
    private static JSONObject camera(double zoom,double[] crop)throws Exception{
        return new JSONObject().put("camera_id","0").put("focal_length_mm",4).put("sensor_width_mm",6.4).put("sensor_height_mm",4.8)
                .put("pixel_array_width",4000).put("pixel_array_height",3000).put("active_array_rect_px",SetupJson.array(new double[]{0,0,4000,3000}))
                .put("capture_crop_rect_px",SetupJson.array(crop)).put("control_zoom_ratio",zoom)
                .put("sensor_to_buffer_matrix",SetupJson.array(new double[]{.32,0,0,0,.32,0,0,0,1}));
    }
    @Test public void D01_inchesCentimetersMetersAndCustomRatios(){
        TvDistanceCalculator.Inputs wide=tv(55,.05);
        assertEquals(55*.0254,Math.hypot(wide.widthM,wide.heightM),1e-12);assertEquals(16.0/9,wide.widthM/wide.heightM,1e-12);
        TvDistanceCalculator.Inputs cm=TvDistanceCalculator.Inputs.sizeCm(wide.widthM*100,wide.heightM*100,.05);
        assertEquals(wide.diagonalIn,cm.diagonalIn,1e-10);
        assertEquals(4.0/3,TvDistanceCalculator.Inputs.diagonal(40,4,3,0).widthM/TvDistanceCalculator.Inputs.diagonal(40,4,3,0).heightM,1e-12);
        assertEquals(.5,TvDistanceCalculator.Inputs.diagonal(40,1,2,0).widthM/TvDistanceCalculator.Inputs.diagonal(40,1,2,0).heightM,1e-12);
    }
    @Test public void D02_knownFovMatchesFormula(){
        TvDistanceCalculator.Inputs in=TvDistanceCalculator.Inputs.sizeCm(120,70,.05);
        double expected=Math.max(1.2/(2*Math.tan(Math.PI/6)*.9),.7/(2*Math.tan(Math.PI/8)*.9));
        assertEquals(expected,TvDistanceCalculator.fromFov(in,60,45).distanceM,1e-12);
    }
    @Test public void D02_monotonicTvMarginAndFov(){
        double base=TvDistanceCalculator.fromFov(tv(55,.05),60,45).distanceM;
        assertTrue(TvDistanceCalculator.fromFov(tv(65,.05),60,45).distanceM>base);
        assertTrue(TvDistanceCalculator.fromFov(tv(55,.1),60,45).distanceM>base);
        assertTrue(TvDistanceCalculator.fromFov(tv(55,.05),70,55).distanceM<base);
    }
    @Test public void D02_degenerateAnglesAndDimensionsNeverReturnFakeMeters(){
        for(double bad:new double[]{0,-1,180,Double.NaN,Double.POSITIVE_INFINITY})assertFalse(TvDistanceCalculator.fromFov(tv(55,.05),bad,45).available());
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})try{tv(bad,.05);fail();}catch(IllegalArgumentException expected){}
        for(double bad:new double[]{-.01,.5,Double.NaN,Double.POSITIVE_INFINITY})try{tv(55,bad);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void D03_camera2ZoomRatioAndLegacyCropAgreeWithoutDoubleCounting() throws Exception {
        JSONObject full=camera(1,new double[]{0,0,4000,3000});
        double base=TvDistanceCalculator.fromCamera(tv(55,.05),full,sensorGeometry(1,1280,960)).distanceM;
        double ratio=TvDistanceCalculator.fromCamera(tv(55,.05),camera(2,new double[]{0,0,4000,3000}),sensorGeometry(2,1280,960)).distanceM;
        double legacy=TvDistanceCalculator.fromCamera(tv(55,.05),camera(1,new double[]{1000,750,3000,2250}),sensorGeometry(2,1280,960)).distanceM;
        assertEquals(base*2,ratio,1e-10);assertEquals(ratio,legacy,1e-10);
        assertEquals(base*4,TvDistanceCalculator.fromCamera(tv(55,.05),camera(2,new double[]{1000,750,3000,2250}),sensorGeometry(4,1280,960)).distanceM,1e-10);
    }
    @Test public void D03_sourceViewportAndUiReduceEffectiveFov() throws Exception {
        JSONObject meta=camera(1,new double[]{0,0,4000,3000});
        CameraPreviewGeometry base=sensorGeometry(1,1280,960);
        double d=TvDistanceCalculator.fromCamera(tv(55,.05),meta,base).distanceM;
        CameraPreviewGeometry clipped=new CameraPreviewGeometry(1280,960,1280,960,new double[]{0,0,1280,960},0,"0","BACK",1,1,1,1280,960,ID,new double[]{0,200,1280,960});
        assertTrue(TvDistanceCalculator.fromCamera(tv(55,.05),meta,clipped).distanceM>=d);
        assertTrue(TvDistanceCalculator.fromCamera(tv(55,.05),meta,sensorGeometry(1,1280,720)).fovV<TvDistanceCalculator.fromCamera(tv(55,.05),meta,base).fovV);
    }
    @Test public void D03_alreadyRotatedImageAnalysisSwapsFovAxes() throws Exception {
        JSONObject meta=camera(1,new double[]{0,0,4000,3000});
        TvDistanceCalculator.Estimate landscape=TvDistanceCalculator.fromCamera(tv(55,.05),meta,sensorGeometry(1,1280,960));
        meta.put("sensor_to_buffer_matrix",SetupJson.array(new double[]{0,-.32,960,.32,0,0,0,0,1}));
        TvDistanceCalculator.Estimate portrait=TvDistanceCalculator.fromCamera(tv(55,.05),meta,sensorGeometry(1,960,1280));
        assertEquals(landscape.fovH,portrait.fovV,1e-10);assertEquals(landscape.fovV,portrait.fovH,1e-10);
    }
    @Test public void D04_missingInconsistentNoncentralMetadataIsUnavailable() throws Exception {
        CameraPreviewGeometry g=sensorGeometry(1,1280,960);
        assertFalse(TvDistanceCalculator.fromCamera(tv(55,.05),null,g).available());
        JSONObject m=camera(1,new double[]{0,0,4000,3000});m.remove("focal_length_mm");assertFalse(TvDistanceCalculator.fromCamera(tv(55,.05),m,g).available());
        assertFalse(TvDistanceCalculator.fromCamera(tv(55,.05),camera(2,new double[]{0,0,4000,3000}),g).available());
        assertFalse(TvDistanceCalculator.fromCamera(tv(55,.05),camera(1,new double[]{0,0,3000,2250}),g).available());
        m=camera(1,new double[]{0,0,4000,3000});m.put("camera_id","other");assertFalse(TvDistanceCalculator.fromCamera(tv(55,.05),m,g).available());
    }
    @Test public void D04_minimumFocusIsNotFitDistance() throws Exception {
        JSONObject meta=camera(1,new double[]{0,0,4000,3000});CameraPreviewGeometry g=sensorGeometry(1,1280,960);
        double d=TvDistanceCalculator.fromCamera(tv(55,.05),meta,g).distanceM;meta.put("minimum_focus_distance_diopters",100);
        assertEquals(d,TvDistanceCalculator.fromCamera(tv(55,.05),meta,g).distanceM,0);
    }
    @Test public void D05_measuredApproximationIsExplicitAndRequiresValidCorners(){
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);
        TvDistanceCalculator.Estimate e=TvDistanceCalculator.fromMeasured(tv(55,.05),2,TRAPEZOID,g);
        assertEquals("MEASURED_RATIO",e.method);assertEquals(2*(.83-.12)/.9,e.distanceM,1e-6);
        assertFalse(TvDistanceCalculator.fromMeasured(tv(55,.05),0,TRAPEZOID,g).available());
        assertNull(e.fovH);assertTrue(e.toJson().optJSONArray("assumptions").length()>0);
    }
}
