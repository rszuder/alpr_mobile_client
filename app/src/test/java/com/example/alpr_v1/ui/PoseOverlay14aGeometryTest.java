package com.example.alpr_v1.ui;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.experiment.MtPoseGeometryEncoder;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.vision.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** True model points survive decoding/letterbox/ROI/display; no Android graphic stubs involved. */
public class PoseOverlay14aGeometryTest {
    private static final float[][] TRAP={{.12f,.18f},{.83f,.24f},{.76f,.70f},{.19f,.65f}};
    private static Detection decoded(int x,int y,int width,int height) {
        float scale=Math.min(512f/width,512f/height),px=(512-width*scale)/2,py=(512-height*scale)/2;
        float[] row={.05f,.05f,.95f,.95f,.9f,0,0,0,0,0,0,0,0,0};
        for(int i=0;i<4;i++){row[6+i*2]=(TRAP[i][0]*width*scale+px)/512;row[7+i*2]=(TRAP[i][1]*height*scale+py)/512;}
        YoloOutputSpec spec=new YoloOutputSpec(1,4,2,false,false,true,512,512,.25f,.45f,4,5);
        Detection model=YoloEndToEndDecoder.decode(row,1,14,spec).get(0);
        return DetectionCoordinateMapper.toSource(model,SyntheticLetterboxInput.create(scale,px,py,width,height),x,y);
    }
    private static PlateDisplayGeometry display(Detection source) {
        List<Point2> normalized=new ArrayList<>();for(Point2 p:source.keypoints)normalized.add(new Point2(p.x/1920,p.y/1080));
        return PlateDisplayGeometry.fromNormalized(normalized,new NormalizedBounds(source.left/1920,source.top/1080,source.right/1920,source.bottom/1080),1920,1080);
    }
    @Test public void G01_G02_R0R1R2AndFallbackKeepFourNonAxisAlignedSegmentsInPixels(){
        for(int[] roi:new int[][]{{0,0,1920,1080},{200,100,1500,900},{0,100,900,800},{950,50,900,850},{0,0,1920,1080}}){
            Detection source=decoded(roi[0],roi[1],roi[2],roi[3]);PlateDisplayGeometry display=display(source);
            assertTrue(display.hasQuad());assertEquals(4,display.cornersNorm.size());
            CameraPreviewGeometry screen=new CameraPreviewGeometry(1920,1080,1920,1080,new double[]{0,0,1920,1080},0,"0","BACK",1,1,1,1000,800,
                    new double[]{.5,0,20,0,.5,130,0,0,1},new double[]{0,0,1000,800});
            for(int i=0;i<4;i++){
                double expectedX=20+.5*(roi[0]+TRAP[i][0]*roi[2]),expectedY=130+.5*(roi[1]+TRAP[i][1]*roi[3]);
                Point2 p=display.cornersNorm.get(i);double[] actual=screen.normalizedToView(p.x,p.y);
                assertEquals(expectedX,actual[0],.001);assertEquals(expectedY,actual[1],.001);
                Point2 next=display.cornersNorm.get((i+1)%4);assertTrue(Math.abs(next.x-p.x)>.001);assertTrue(Math.abs(next.y-p.y)>.001);
            }
            assertEquals("VALID_QUAD",MtPoseGeometryEncoder.encode(source,true,1920,1080).status);
        }
    }
    @Test public void G01_G04_interpolationHasNoBoxEndpointAndInvalidPoseEndsImmediately(){
        PlateDisplayGeometry a=display(decoded(0,0,1920,1080)),b=display(decoded(10,10,1800,1000));
        for(float t:new float[]{0,.25f,.5f,.75f,1}){
            List<Point2> points=PlateDisplayGeometry.interpolate(a,b,t);assertEquals(4,points.size());
            for(int i=0;i<4;i++)assertEquals(a.cornersNorm.get(i).x+(b.cornersNorm.get(i).x-a.cornersNorm.get(i).x)*t,points.get(i).x,1e-6);
        }
        PlateDisplayGeometry missing=PlateDisplayGeometry.fromNormalized(Collections.emptyList(),new NormalizedBounds(.1f,.2f,.8f,.7f),1920,1080);
        for(float t:new float[]{0,.25f,.5f,1})assertTrue(PlateDisplayGeometry.interpolate(a,missing,t).isEmpty());
        assertEquals(b.cornersNorm,PlateDisplayGeometry.interpolate(missing,b,0));
    }
    @Test public void G02_twoRoiSourcesAndArchivePoseRemainIndependentOfPresentation(){
        Detection one=decoded(0,100,900,800),two=decoded(950,50,900,850);
        String oneEvidence=MtPoseGeometryEncoder.encode(one,true,1920,1080).cornersNorm;
        String twoEvidence=MtPoseGeometryEncoder.encode(two,true,1920,1080).cornersNorm;
        assertNotEquals(oneEvidence,twoEvidence);PlateDisplayGeometry a=display(one),b=display(two);
        PlateDisplayGeometry.interpolate(a,b,.5f);
        assertEquals(oneEvidence,MtPoseGeometryEncoder.encode(one,true,1920,1080).cornersNorm);
        assertEquals(twoEvidence,MtPoseGeometryEncoder.encode(two,true,1920,1080).cornersNorm);
        assertTrue(a.cornersNorm.get(0).x<b.cornersNorm.get(0).x);
    }
    @Test public void G04_duplicatesAndNonFinitePointsNeverProducePose(){
        for(List<Point2> points:java.util.Arrays.asList(
                java.util.Arrays.asList(new Point2(.1f,.1f),new Point2(.1f,.1f),new Point2(.5f,.5f),new Point2(.2f,.5f)),
                java.util.Arrays.asList(new Point2(Float.NaN,.1f),new Point2(.6f,.1f),new Point2(.5f,.5f),new Point2(.2f,.5f))))
            assertFalse(PlateDisplayGeometry.fromNormalized(points,new NormalizedBounds(.1f,.1f,.6f,.5f),1920,1080).hasQuad());
    }
}
