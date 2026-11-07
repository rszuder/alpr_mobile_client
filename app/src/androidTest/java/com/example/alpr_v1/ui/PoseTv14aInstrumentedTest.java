package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic native Canvas/touch tests. Compilation is not execution or device acceptance. */
public class PoseTv14aInstrumentedTest {
    private static List<PointF> points(float offset){return Arrays.asList(new PointF(.12f+offset,.18f),new PointF(.43f+offset,.24f),new PointF(.36f+offset,.70f),new PointF(.19f+offset,.65f));}
    private static OverlayItem plate(long id,float offset){return new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.12f+offset,.18f,.43f+offset,.7f),points(offset),"same",id,false);}
    private static Bitmap draw(DetectionOverlayView view){Bitmap b=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;}
    private static void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Test public void G01_G04_realTrapezoidUsesSlopedEdgesAndMissingPoseIsHidden(){main(()->{
        DetectionOverlayView view=new DetectionOverlayView(ApplicationProvider.getApplicationContext(),null);view.layout(0,0,1000,1000);
        OverlayItem plate=new OverlayItem(new RectF(.12f,.18f,.83f,.7f),Arrays.asList(new PointF(.12f,.18f),new PointF(.83f,.24f),new PointF(.76f,.7f),new PointF(.19f,.65f)),"",77,false);
        view.setItems(Collections.singletonList(plate),1000,1000);
        Bitmap b=draw(view);try{assertTrue(Color.alpha(b.getPixel(475,210))>0);assertEquals(0,Color.alpha(b.getPixel(475,180)));}finally{b.recycle();}
        view.setItems(Collections.singletonList(new OverlayItem(new RectF(.12f,.18f,.83f,.7f),Collections.emptyList(),"",77,false)),1000,1000);
        assertEquals(0,view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
    });}
    @Test public void G02_G05_identicalLabelsRetainIndependentQuadsAcrossShortPredictionsAndReset(){main(()->{
        CameraMotionOverlayTracker tracker=new CameraMotionOverlayTracker();OverlayItem a=plate(11,0),b=plate(22,.5f);
        tracker.update(Arrays.asList(a,b),1000000000L,1000000000L);
        List<OverlayItem> carried=tracker.update(Collections.emptyList(),1016000000L,1016000000L);assertEquals(2,carried.size());
        assertEquals(11,carried.get(0).trackId);assertEquals(22,carried.get(1).trackId);
        assertEquals(.5f,carried.get(1).normalizedKeypoints.get(0).x-carried.get(0).normalizedKeypoints.get(0).x,.001f);
        tracker.reset();assertTrue(tracker.update(Collections.emptyList(),1032000000L,1032000000L).isEmpty());
    });}
    @Test public void G01_plateStaysAfterMzAndOnlyLabelTransfers(){main(()->{
        DetectionOverlayView view=new DetectionOverlayView(ApplicationProvider.getApplicationContext(),null);view.layout(0,0,1000,1000);
        OverlayItem p=plate(77,0);view.setItems(Arrays.asList(new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(.05f,.1f,.8f,.9f),Collections.emptyList(),"P7",7,false),p),1000,1000);
        List<Point2> corners=Arrays.asList(new Point2(120,180),new Point2(430,240),new Point2(360,700),new Point2(190,650));
        PlateGeometry geometry=PlateGeometry.from(1000,1000,new Detection(0,.9f,120,180,430,700,corners),corners);
        view.animatePlateObservation(new PlateObservation(77L,PlateVehicleAssociation.direct(7,7,"synthetic"),MtWorkKind.VEHICLE_ROI,MtReason.EXPERIMENT_POLICY,1L,null,"TEST123",.9,.9,true,2,Collections.emptyList(),1,1,.8f,null,null,geometry,true,true,"TEST123",true,1,"single",Collections.singletonList(7),"","TEST123"));
        assertEquals(1,view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));view.finishPlateAbsorptionForTesting();
        assertEquals(1,view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));assertTrue(view.vehicleLabelForTesting(7).contains("TEST123"));
    });}
    @Test public void K01_K06_touchHandlesAreIndependentAndEditingCanBeCancelled(){main(()->{
        Context context=ApplicationProvider.getApplicationContext();TvCalibrationOverlayView tv=new TvCalibrationOverlayView(context,null);tv.layout(0,0,1000,1000);
        CameraPreviewGeometry g=new CameraPreviewGeometry(1000,1000,1000,1000,new double[]{0,0,1000,1000},0,"0","BACK",1,1,1,1000,1000,new double[]{1,0,0,0,1,0,0,0,1},new double[]{0,0,1000,1000});
        tv.setGeometry(g);tv.setEditing(true);
        for(float[] point:new float[][]{{120,180},{830,240},{760,700},{190,650}}){touch(tv,MotionEvent.ACTION_DOWN,point[0],point[1]);touch(tv,MotionEvent.ACTION_UP,point[0],point[1]);}
        assertNull(TvSetupCalibration.validation(tv.corners(),g,context.getResources().getDisplayMetrics().density));
        List<Point2> before=tv.corners();tv.setEditing(false);assertFalse(touch(tv,MotionEvent.ACTION_DOWN,120,180));assertEquals(before,tv.corners());
    });}
    private static boolean touch(TvCalibrationOverlayView view,int action,float x,float y){MotionEvent e=MotionEvent.obtain(0,0,action,x,y,0);try{return view.onTouchEvent(e);}finally{e.recycle();}}
}
