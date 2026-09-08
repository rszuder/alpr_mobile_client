package com.example.alpr_v1.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.capture.RecognitionHistoryItem;
import com.example.alpr_v1.capture.RecognitionHistoryStore;
import com.example.alpr_v1.pipeline.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CropReadingConsistencyInstrumentedTest {
    @Test public void activitySelectsCaptionAndConfidenceFromSameMzCropNotTemporalConsensus() {
        Bitmap first=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888);
        Bitmap second=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888);
        first.eraseColor(Color.RED);second.eraseColor(Color.BLUE);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                try {
                    Field mode=MainActivity.class.getDeclaredField("experimentModeEnabled");mode.setAccessible(true);mode.setBoolean(activity,false);
                    Field field=MainActivity.class.getDeclaredField("recognitionHistory");field.setAccessible(true);
                    RecognitionHistoryStore history=(RecognitionHistoryStore)field.get(activity);history.clear();
                    Method collect=MainActivity.class.getDeclaredMethod("collectRecognitionHistory",java.util.List.class);collect.setAccessible(true);
                    PlateObservation a=observation(first,"A",.95,.20,10);
                    PlateObservation b=observation(second,"B",.40,.99,20);
                    collect.invoke(activity,Collections.singletonList(a));
                    collect.invoke(activity,Collections.singletonList(b));
                    RecognitionHistoryItem retained=history.newestFirst().get(0);
                    assertEquals("A",retained.text);assertEquals("A",retained.characters.get(0).label);
                    assertEquals(.95,retained.confidence,.0001);
                    assertEquals(Color.RED,retained.previewBitmap.getPixel(0,0));
                    assertEquals(10,retained.capturedAtMillis);
                    assertEquals(.99,b.recognitionConfidence,.0001); // Consensus remains unchanged in the pipeline.
                    assertEquals("CONSENSUS",b.text);assertEquals("B",b.freshPrediction);
                    assertEquals(0,observation(second,"",0,.99,30).freshRecognitionConfidence(),0);
                } catch(Exception e){throw new AssertionError(e);}
            });
        } finally {first.recycle();second.recycle();}
    }
    private static PlateObservation observation(Bitmap bitmap,String fresh,double confidence,double consensus,long time) {
        return new PlateObservation(1,PlateVehicleAssociation.direct(7,8,"test"),MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,time,bitmap,"CONSENSUS",.9,consensus,true,2,
                fresh.isEmpty()?Collections.emptyList():Collections.singletonList(new PlateCharacter(fresh,confidence,.1f,.1f,.9f,.9f)),
                time,time,.8f,null,null,PlateGeometry.unavailable(),true,!fresh.isEmpty(),fresh,false,1,
                "single_row",Collections.emptyList(),"CONSENSUS","CONSENSUS");
    }
}
