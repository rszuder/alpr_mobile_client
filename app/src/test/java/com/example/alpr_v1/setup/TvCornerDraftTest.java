package com.example.alpr_v1.setup;

import com.example.alpr_v1.vision.Point2;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TvCornerDraftTest {
    @Test public void draftUsesVisiblePixelsAcrossRotationsLetterboxAndMirror(){
        for(int rotation:new int[]{0,90,180,270})for(int[] size:new int[][]{{360,640},{800,360}}){
            CameraPreviewGeometry normal=TvSetupGeometryTest.geometry(rotation,size[0],size[1],1,"synthetic",1);
            for(boolean mirror:new boolean[]{false,true}){
                double[] matrix=normal.sourceToViewMatrix();if(mirror)matrix=CameraPreviewGeometry.multiply(new double[]{-1,0,size[0],0,1,0,0,0,1},matrix);
                CameraPreviewGeometry g=new CameraPreviewGeometry(normal.sourceWidth,normal.sourceHeight,1920,1080,new double[]{0,0,1920,1080},rotation,"synthetic",mirror?"FRONT":"BACK",1,1,1,size[0],size[1],matrix,new double[]{0,40,size[0],size[1]});
                List<Point2> points=TvCornerDraft.create(g,1);assertEquals(4,points.size());assertNull(TvSetupCalibration.validation(points,g,1));
                for(Point2 p:points)assertTrue(g.visible(p.x,p.y));
                assertEquals("UNCALIBRATED",TvSetupCalibration.empty().status(g));
            }
        }
    }
    @Test public void insufficientManipulationAreaCannotCreateAnApparentlyReadyDraft(){
        CameraPreviewGeometry tiny=TvSetupGeometryTest.geometry(0,20,20,1,"synthetic",1);
        assertThrows(IllegalArgumentException.class,()->TvCornerDraft.create(tiny,1));
        assertThrows(IllegalArgumentException.class,()->TvCornerDraft.create(null,1));
    }
}
