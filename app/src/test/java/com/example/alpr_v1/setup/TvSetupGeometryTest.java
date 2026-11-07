package com.example.alpr_v1.setup;

import com.example.alpr_v1.vision.Point2;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class TvSetupGeometryTest {
    public static final double[] ID={1,0,0,0,1,0,0,0,1};
    public static final List<Point2> TRAPEZOID=Arrays.asList(new Point2(.12f,.18f),new Point2(.83f,.24f),new Point2(.76f,.70f),new Point2(.19f,.65f));
    public static CameraPreviewGeometry geometry(int rotation,int vw,int vh,double zoom,String camera,int binding) {
        int w=rotation==90||rotation==270?1080:1920,h=rotation==90||rotation==270?1920:1080;
        double scale=Math.min(vw/(double)w,vh/(double)h);
        return new CameraPreviewGeometry(w,h,1920,1080,new double[]{0,0,1920,1080},rotation,camera,"BACK",zoom,binding,1,vw,vh,
                new double[]{scale,0,(vw-w*scale)/2,0,scale,(vh-h*scale)/2,0,0,1},new double[]{0,0,vw,vh});
    }
    @Test public void editorPanelResizeDoesNotChangeSourcePixelsOrRelaxSavedGeometry(){
        CameraPreviewGeometry normal=geometry(0,360,700,1,"0",1),editor=geometry(0,360,420,1,"0",1);
        assertTrue(normal.sameSourceGeometry(editor));assertFalse(normal.sameGeometry(editor));
        assertTrue(normal.presentationDifferencePixels(editor)>100);
        TvSetupCalibration confirmed=TvSetupCalibration.confirm(TRAPEZOID,normal,1,"2026-11-05T12:00:00Z");
        assertEquals("STALE",confirmed.status(editor));
        assertFalse(normal.sameSourceGeometry(geometry(0,360,700,2,"0",1)));
        assertFalse(normal.sameSourceGeometry(geometry(90,360,700,1,"0",1)));
        assertFalse(normal.sameSourceGeometry(geometry(0,360,700,1,"1",1)));
        assertFalse(normal.sameSourceGeometry(geometry(0,360,700,1,"0",2)));
    }
    @Test public void physicalLensIdentityIsOptionalAndInvalidatesWithinSameLogicalCamera() throws Exception {
        CameraPreviewGeometry legacy=geometry(0,960,540,1,"0",1),wide=legacy.withPhysicalCameraId("2"),other=legacy.withPhysicalCameraId("3");
        assertFalse(legacy.toJson().has("physical_camera_id"));assertFalse(wide.sameSourceGeometry(other));assertFalse(wide.sameGeometry(other));
        assertTrue(wide.sameGeometry(CameraPreviewGeometry.fromJson(wide.toJson())));
        assertEquals("STALE",TvSetupCalibration.confirm(TRAPEZOID,wide,1,"2026-11-05T12:00:00Z").status(other));
        assertFalse(TvDistanceCalculator.fromCamera(TvDistanceCalculator.Inputs.diagonal(55,16,9,.05),new JSONObject(),wide).available());
    }
    @Test public void subpixelPublicationThresholdIsBoundedByMaximumScreenDisplacement() throws Exception {
        CameraPreviewGeometry normal=geometry(0,360,700,1,"0",1);
        JSONObject tiny=normal.toJson();tiny.getJSONArray("source_to_view_matrix").put(2,.24);
        CameraPreviewGeometry jitter=CameraPreviewGeometry.fromJson(tiny);
        assertTrue(normal.sameSourceGeometry(jitter));assertEquals(.24,normal.presentationDifferencePixels(jitter),1e-8);
        tiny.getJSONArray("source_to_view_matrix").put(2,.26);
        assertTrue(normal.presentationDifferencePixels(CameraPreviewGeometry.fromJson(tiny))>.25);
        assertEquals(Double.POSITIVE_INFINITY,normal.presentationDifferencePixels(geometry(0,360,700,2,"0",1)),0);
    }
    @Test public void K02_allRotationsAndAspectRatiosRoundTripBelowTwoPixels(){
        for(int rotation:new int[]{0,90,180,270})for(int[] view:new int[][]{{960,540},{540,960},{777,555},{1200,400}}){
            CameraPreviewGeometry g=geometry(rotation,view[0],view[1],1,"0",1);
            for(Point2 p:TRAPEZOID){double[] v=g.normalizedToView(p.x,p.y),r=g.viewToNormalized(v[0],v[1]);
                assertEquals((double)p.x*g.sourceWidth,r[0]*g.sourceWidth,1e-8);assertEquals((double)p.y*g.sourceHeight,r[1]*g.sourceHeight,1e-8);}
        }
    }
    @Test public void K02_nontrivialCropAndSensorTransformsAgreeWithKnownPixelPoints(){
        double[] crop={160,90,1760,990};
        double[][] expected={{0,0},{900,0},{1600,900},{0,1600}};
        int i=0;
        for(int rotation:new int[]{0,90,180,270}){
            double[] c=CameraPreviewGeometry.bufferToSource(crop,rotation);
            double[] atTopLeft=CameraPreviewGeometry.map(c,160,90);
            if(rotation==0)assertArrayEquals(new double[]{0,0},atTopLeft,1e-9);
            if(rotation==90)assertArrayEquals(new double[]{900,0},atTopLeft,1e-9);
            if(rotation==180)assertArrayEquals(new double[]{1600,900},atTopLeft,1e-9);
            if(rotation==270)assertArrayEquals(new double[]{0,1600},atTopLeft,1e-9);
            double[] sensorToBuffer={.48,0,-20,0,.48,-10,0,0,1};
            double[] sensorToSource=CameraPreviewGeometry.multiply(c,sensorToBuffer);
            double[] sourceToView={.5,0,23,0,.5,47,0,0,1};
            double[] sensorToView=CameraPreviewGeometry.multiply(sourceToView,sensorToSource);
            double[] derived=CameraPreviewGeometry.multiply(sensorToView,CameraPreviewGeometry.inverse(sensorToSource));
            assertArrayEquals(sourceToView,derived,1e-10);
            int w=rotation%180==0?1600:900,h=rotation%180==0?900:1600;
            CameraPreviewGeometry g=new CameraPreviewGeometry(w,h,1920,1080,crop,rotation,"0","BACK",1,1,1,1000,1000,derived,new double[]{0,0,1000,1000});
            for(Point2 p:TRAPEZOID){double[] v=g.normalizedToView(p.x,p.y),r=g.viewToNormalized(v[0],v[1]);assertEquals(p.x,r[0],1e-9);assertEquals(p.y,r[1],1e-9);}
            i++;
        }
        assertEquals(4,i);
    }
    @Test public void K02_mirrorRoundTripKeepsCornersWithoutClampingLetterbox(){
        CameraPreviewGeometry g=new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{0,0,1920,1080},0,"1","FRONT",1,1,1,1000,800,
                new double[]{-.5,0,980,0,.5,130,0,0,1},new double[]{0,0,1000,800});
        assertTrue(g.mirrored);
        assertEquals(-.24,g.viewToNormalized(500,0)[1],.001);
        assertFalse(g.visible(.5,-.24));
        for(Point2 p:TRAPEZOID){double[] v=g.normalizedToView(p.x,p.y),r=g.viewToNormalized(v[0],v[1]);assertEquals(p.x,r[0],1e-9);assertEquals(p.y,r[1],1e-9);}
    }
    @Test public void K01_quadValidationRejectsCrossingsDuplicatesNonFiniteAndWrongOrder(){
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);
        assertNull(TvSetupCalibration.validation(TRAPEZOID,g,1));
        assertNotNull(TvSetupCalibration.validation(Collections.emptyList(),g,1));
        assertNotNull(TvSetupCalibration.validation(Arrays.asList(TRAPEZOID.get(0),TRAPEZOID.get(2),TRAPEZOID.get(1),TRAPEZOID.get(3)),g,1));
        assertNotNull(TvSetupCalibration.validation(Arrays.asList(TRAPEZOID.get(0),TRAPEZOID.get(0),TRAPEZOID.get(2),TRAPEZOID.get(3)),g,1));
        assertNotNull(TvSetupCalibration.validation(Arrays.asList(new Point2(Float.NaN,.2f),TRAPEZOID.get(1),TRAPEZOID.get(2),TRAPEZOID.get(3)),g,1));
        assertNotNull(TvSetupCalibration.validation(Arrays.asList(new Point2(-.1f,.2f),TRAPEZOID.get(1),TRAPEZOID.get(2),TRAPEZOID.get(3)),g,1));
    }
    @Test public void K01_UIOcclusionAndManipulationSizeCannotBeReady(){
        CameraPreviewGeometry g=new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{0,0,1920,1080},0,"0","BACK",1,1,1,960,540,
                new double[]{.5,0,0,0,.5,0,0,0,1},new double[]{0,150,960,540});
        assertNotNull(TvSetupCalibration.validation(TRAPEZOID,g,1));
        List<Point2> tiny=Arrays.asList(new Point2(.5f,.5f),new Point2(.51f,.5f),new Point2(.51f,.51f),new Point2(.5f,.51f));
        assertNotNull(TvSetupCalibration.validation(tiny,g,1));
    }
    @Test public void K03_allGeometryChangesInvalidateConfirmedCalibration(){
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);
        TvSetupCalibration c=TvSetupCalibration.confirm(TRAPEZOID,g,1,"2026-11-05T12:00:00Z");
        assertEquals("READY",c.status(g));
        for(CameraPreviewGeometry change:Arrays.asList(geometry(90,960,540,1,"0",1),geometry(0,960,540,2,"0",1),
                geometry(0,960,540,1,"1",1),geometry(0,960,540,1,"0",2),geometry(0,540,960,1,"0",1),null))assertEquals("STALE",c.status(change));
        JSONObject j=g.toJson();try{j.put("crop_rect_px",SetupJson.array(new double[]{1,0,1920,1080}));j.put("source_image_width",1919);}catch(Exception e){throw new AssertionError(e);}
        assertEquals("STALE",c.status(CameraPreviewGeometry.fromJson(j)));
    }
    @Test public void K05_restoreIsStaleAndCorruptMetadataIsSafe() throws Exception {
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);TvSetupCalibration c=TvSetupCalibration.confirm(TRAPEZOID,g,1,"2026-11-05T12:00:00Z");
        JSONObject snapshot=c.snapshot(g,null,null,TvDistanceCalculator.unavailable("synthetic"),"2026-11-05T12:01:00Z");
        assertTrue(SetupJson.verified(snapshot));assertEquals("STALE",TvSetupCalibration.restore(snapshot.toString()).status(g));
        snapshot.getJSONArray("corners_norm").getJSONObject(0).put("x",.5);
        assertEquals("UNCALIBRATED",TvSetupCalibration.restore(snapshot.toString()).status(g));
        assertEquals("UNCALIBRATED",TvSetupCalibration.restore("broken").status(g));
        assertEquals("UNCALIBRATED",TvSetupCalibration.restore("").status(g));
    }
    @Test public void K03_display180InvalidatesEvenWhenCameraXAlreadyRotatedBufferToZero() throws Exception {
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);
        TvSetupCalibration calibration=TvSetupCalibration.confirm(TRAPEZOID,g,1,"2026-11-05T12:00:00Z");
        JSONObject changed=g.toJson();changed.put("display_rotation_degrees",180);
        CameraPreviewGeometry rotated=CameraPreviewGeometry.fromJson(changed);
        assertEquals(0,rotated.rotationDegrees);assertArrayEquals(g.sourceToViewMatrix(),rotated.sourceToViewMatrix(),0);
        assertEquals("STALE",calibration.status(rotated));
    }
    @Test public void K03_returningToOldZoomCannotUndoInvalidationWithoutConfirmation() {
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);
        TvSetupCalibration calibration=TvSetupCalibration.confirm(TRAPEZOID,g,1,"2026-11-05T12:00:00Z");
        calibration=calibration.invalidated("CAMERA_GEOMETRY_CHANGED");
        assertEquals("STALE",calibration.status(geometry(0,960,540,2,"0",1)));
        assertEquals("STALE",calibration.status(g));
        assertEquals("READY",TvSetupCalibration.confirm(calibration.corners,g,1,"2026-11-05T12:02:00Z").status(g));
    }
    @Test public void K05_freezeIsOwnedAndReconfirmationDoesNotMutateHistoricalSnapshot(){
        CameraPreviewGeometry g=geometry(0,960,540,1,"0",1);TvSetupCalibration c=TvSetupCalibration.confirm(TRAPEZOID,g,1,"2026-11-05T12:00:00Z");
        JSONObject frozen=c.snapshot(g,null,null,TvDistanceCalculator.unavailable("synthetic"),"2026-11-05T12:01:00Z");String before=SetupJson.canonical(frozen);
        TvSetupCalibration.confirm(TRAPEZOID,geometry(0,960,540,2,"0",1),1,"2026-11-05T12:02:00Z");
        double[] leaked=g.sourceToViewMatrix();leaked[0]=500;
        assertEquals(before,SetupJson.canonical(frozen));assertEquals("READY",c.status(g));
    }
    @Test public void K05_canonicalDigestIgnoresKeyOrderAndNumericFormatting() throws Exception {
        JSONObject a=new JSONObject("{\"b\":1.0,\"a\":[2,3]}");JSONObject b=new JSONObject("{\"a\":[2.00,3.0],\"b\":1}");
        assertEquals(SetupJson.sha256(a),SetupJson.sha256(b));assertEquals("{\"a\":[2,3],\"b\":1}",SetupJson.canonical(a));
    }
    @Test(expected=IllegalArgumentException.class) public void K02_singularTransformIsRejected(){CameraPreviewGeometry.inverse(new double[9]);}
    @Test(expected=IllegalArgumentException.class) public void K02_nonFiniteTransformIsRejected(){CameraPreviewGeometry.inverse(new double[]{Double.NaN,0,0,0,1,0,0,0,1});}
    @Test(expected=IllegalArgumentException.class) public void K02_sourceDimensionsMustAgreeWithAppliedCrop(){new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{20,0,1920,1080},0,"0","BACK",1,1,1,1920,1080,ID,new double[]{0,0,1920,1080});}
}
