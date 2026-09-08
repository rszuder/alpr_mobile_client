package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.PlateRectifier;
import com.example.alpr_v1.vision.Point2;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AutoZoomGeometryGuardInstrumentedTest {
    @Test public void preservedQuadFeedsActualRectifierAndKeepsRawResearchGeometry() throws Exception {
        Detection baseline=quad(400,500,600,550),raw=quad(320,500,640,590);
        AutoZoomGeometryGuard guard=new AutoZoomGeometryGuard();
        guard.remember(1,7,baseline,1000,1000,new ContinuityStamp(1,1,0,1),0);
        guard.cameraTransform(1.8f);
        Detection effective=guard.select(raw,2,7,1000,1000,new ContinuityStamp(1,1,1,2),100).detection;
        Bitmap frame=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);
        Bitmap truncated=null, complete=null;
        try {
            Canvas canvas=new Canvas(frame); canvas.drawColor(Color.BLACK);
            Paint paint=new Paint(); paint.setColor(Color.GREEN);
            canvas.drawRect(652,515,670,575,paint); // The suffix is beyond the raw MT quad.
            truncated=PlateRectifier.rectify(frame,raw.keypoints);
            complete=PlateRectifier.rectify(frame,effective.keypoints);
            assertEquals(0,greenPixels(truncated));
            assertTrue(greenPixels(complete)>100);
            PlateGeometry geometry=PlateGeometry.from(1000,1000,raw,raw.keypoints)
                    .withAutoZoomCropGeometry(PlateGeometry.from(1000,1000,effective,effective.keypoints),12);
            assertEquals(640,geometry.bboxRightPx,.001f);
            assertEquals(680,geometry.forRecognition().bboxRightPx,.001f);
            org.json.JSONObject report=geometry.toJson();
            assertEquals(640,report.getDouble("plate_bbox_right_px"),.001);
            assertEquals("pre_zoom_reference",report.getString("crop_geometry_source"));
            assertEquals(12,report.getLong("crop_reference_source_sequence"));
            assertEquals(680,report.getJSONObject("effective_crop_geometry").getDouble("plate_bbox_right_px"),.001);
        } finally {
            frame.recycle(); if(truncated!=null)truncated.recycle(); if(complete!=null)complete.recycle();
        }
    }
    private static Detection quad(float l,float t,float r,float b) {
        return new Detection(0,.95f,l,t,r,b,Arrays.asList(new Point2(l,t),new Point2(r,t),new Point2(r,b),new Point2(l,b)));
    }
    private static int greenPixels(Bitmap bitmap) {
        int count=0;
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)
            if(Color.green(bitmap.getPixel(x,y))>200)count++;
        return count;
    }
}
