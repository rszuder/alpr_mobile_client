package com.example.alpr_v1.setup;

import com.example.alpr_v1.setup.TvCornerEditGeometry.Move;
import com.example.alpr_v1.setup.TvCornerEditGeometry.Position;
import com.example.alpr_v1.vision.Point2;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvCornerEditGeometryTest {
    @Test public void closedTvEdgesDoNotChangeCameraPixelMembership(){
        CameraPreviewGeometry g=TvSetupGeometryTest.geometry(0,960,540,1,"synthetic",1);
        assertFalse(g.visible(1,1));assertTrue(TvCornerEditGeometry.contains(g,1,1));
        Move rounded=new TvCornerEditGeometry(g).constrain(960-1e-5,540-1e-5);
        assertEquals(1,rounded.point.x,0);assertEquals(1,rounded.point.y,0);assertFalse(rounded.limited);
        assertNull(TvSetupCalibration.validation(Arrays.asList(new Point2(0,0),new Point2(1,0),new Point2(1,1),new Point2(0,1)),g,1));
        assertFalse(TvCornerEditGeometry.contains(g,1.00001,.5));assertFalse(TvCornerEditGeometry.contains(g,Double.NaN,.5));
    }
    @Test public void everyEdgeKeepsTangentialPositionAndStopsAtTheActualImage(){
        CameraPreviewGeometry g=TvSetupGeometryTest.geometry(0,1000,800,1,"synthetic",1);
        TvCornerEditGeometry editor=new TvCornerEditGeometry(g);double[] u=g.usableViewRect();
        double x=(u[0]+u[2])*.5,y=(u[1]+u[3])*.5;
        double[][] outside={{u[0]-100,y},{u[2]+100,y},{x,u[1]-100},{x,u[3]+100},{u[0]-100,u[3]+100}};
        double[][] expected={{u[0],y},{u[2],y},{x,u[1]},{x,u[3]},{u[0],u[3]}};
        for(int i=0;i<outside.length;i++){
            Move p=editor.constrain(outside[i][0],outside[i][1]);assertTrue(p.limited);assertTrue(p.onBoundary);
            assertEquals(expected[i][0],p.viewX,1e-8);assertEquals(expected[i][1],p.viewY,1e-8);assertTrue(TvCornerEditGeometry.contains(g,p.point.toPoint()));
        }
        Move along=editor.constrain(u[2]+50,u[1]+40);assertEquals(u[1]+40,along.viewY,1e-8);
        Move back=editor.constrain(x,y);assertFalse(back.limited);assertFalse(back.onBoundary);
    }
    @Test public void cropAndUiOcclusionLimitTheEditorBeforeTheSourceBorder(){
        CameraPreviewGeometry g=new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{0,0,1920,1080},0,"synthetic","BACK",1,1,1,1000,800,
                new double[]{.5,0,40,0,.5,30,0,0,1},new double[]{120,80,900,600});
        TvCornerEditGeometry editor=new TvCornerEditGeometry(g);Move point=editor.constrain(-100,-100);
        assertEquals(120,point.viewX,1e-8);assertEquals(80,point.viewY,1e-8);
        assertEquals(160,point.point.x*g.sourceWidth,1e-8);assertEquals(100,point.point.y*g.sourceHeight,1e-8);
        assertFalse(editor.containsView(119,80));assertTrue(editor.containsView(120,80));
        assertTrue(TvCornerEditGeometry.contains(g,point.point.toPoint()));
    }
    @Test public void actualImagePolygonWinsOverItsAxisAlignedBoundingBox(){
        CameraPreviewGeometry g=new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{0,0,1920,1080},0,"synthetic","BACK",1,1,1,800,500,
                new double[]{.3,.07,40,.04,.25,20,0,0,1},new double[]{0,0,800,500});
        TvCornerEditGeometry editor=new TvCornerEditGeometry(g);assertFalse(editor.containsView(41,280));
        Move point=editor.constrain(41,280);assertTrue(point.limited);assertTrue(TvCornerEditGeometry.contains(g,point.point.toPoint()));
    }
    @Test public void nudgeIsOneSourcePixelInTheVisualDirectionForRotationAndMirror(){
        for(int rotation:new int[]{0,90,180,270})for(boolean mirror:new boolean[]{false,true}){
            CameraPreviewGeometry base=TvSetupGeometryTest.geometry(rotation,1000,1000,1,"synthetic",1);
            double[] turn=rotation==0?new double[]{1,0,0,0,1,0,0,0,1}:rotation==90?new double[]{0,-1,1000,1,0,0,0,0,1}:
                    rotation==180?new double[]{-1,0,1000,0,-1,1000,0,0,1}:new double[]{0,1,0,-1,0,1000,0,0,1};
            double[] matrix=CameraPreviewGeometry.multiply(turn,base.sourceToViewMatrix());
            if(mirror)matrix=CameraPreviewGeometry.multiply(new double[]{-1,0,1000,0,1,0,0,0,1},matrix);
            CameraPreviewGeometry g=new CameraPreviewGeometry(base.sourceWidth,base.sourceHeight,1920,1080,new double[]{0,0,1920,1080},rotation,"synthetic",mirror?"FRONT":"BACK",1,1,1,1000,1000,matrix,new double[]{0,0,1000,1000});
            TvCornerEditGeometry editor=new TvCornerEditGeometry(g);Position start=new Position(.5,.5);double[] v=g.normalizedToView(start.x,start.y);
            for(int[] direction:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){
                Move result=editor.nudge(start,direction[0],direction[1]);
                assertEquals(1,Math.hypot((result.point.x-start.x)*g.sourceWidth,(result.point.y-start.y)*g.sourceHeight),1e-8);
                assertTrue((result.viewX-v[0])*direction[0]+(result.viewY-v[1])*direction[1]>0);
                assertEquals(0,(result.viewX-v[0])*direction[1]-(result.viewY-v[1])*direction[0],1e-8);
            }
        }
    }
    @Test public void repeatedSubpixelEditingDoesNotAccumulateFloatStepRounding(){
        CameraPreviewGeometry g=TvSetupGeometryTest.geometry(0,360,800,1,"synthetic",1);
        TvCornerEditGeometry editor=new TvCornerEditGeometry(g);Position p=new Position(.35,.4);
        for(int i=0;i<300;i++)p=editor.nudge(p,1,0).point;
        assertEquals(.35*g.sourceWidth+300,p.x*g.sourceWidth,1e-7);assertEquals(.4,p.y,1e-12);
        Move edge=editor.constrain(1e6,400);assertTrue(editor.nudge(edge.point,1,0).limited);
        assertEquals(g.sourceWidth,edge.point.x*g.sourceWidth,1e-8);
    }
    @Test public void invalidCursorIsRejectedWithoutProducingNonFiniteCoordinates(){
        TvCornerEditGeometry editor=new TvCornerEditGeometry(TvSetupGeometryTest.geometry(0,360,800,1,"synthetic",1));
        assertThrows(IllegalArgumentException.class,()->editor.constrain(Double.NaN,20));
        assertThrows(IllegalArgumentException.class,()->editor.constrain(20,Double.POSITIVE_INFINITY));
    }
    @Test public void borderSnapshotUsesExistingSchemaAndRestoresAsStale() throws Exception {
        CameraPreviewGeometry g=TvSetupGeometryTest.geometry(0,960,540,1,"synthetic",1);
        TvSetupCalibration calibration=TvSetupCalibration.confirm(Arrays.asList(new Point2(0,0),new Point2(1,0),new Point2(1,1),new Point2(0,1)),g,1,"2026-11-06T10:00:00Z");
        org.json.JSONObject snapshot=calibration.snapshot(g,null,null,TvDistanceCalculator.unavailable("synthetic"),"2026-11-06T10:01:00Z");
        assertTrue(SetupJson.verified(snapshot));assertEquals(TvSetupCalibration.SCHEMA,snapshot.getString("schema"));
        TvSetupCalibration restored=TvSetupCalibration.restore(snapshot.toString());assertEquals("STALE",restored.status(g));
        assertEquals(1,restored.corners.get(2).x,0);assertEquals(1,restored.corners.get(2).y,0);
    }
}
