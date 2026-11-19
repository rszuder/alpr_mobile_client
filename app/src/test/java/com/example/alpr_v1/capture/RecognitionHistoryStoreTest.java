package com.example.alpr_v1.capture;

import android.app.Application;
import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.pipeline.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class RecognitionHistoryStoreTest {
    PlateObservation reading(long scene,long entity,long frame,String text,Bitmap bitmap,boolean fresh){
        return new PlateObservation(100+entity,PlateVehicleAssociation.direct(entity,entity,"synthetic physical owner"),
                MtWorkKind.VEHICLE_ROI,MtReason.SCAN_NEXT_CANDIDATE,frame,bitmap,text,.9,.9,true,1,
                Collections.emptyList(),1000+frame,1000+frame,.8f,null,null,null,fresh,fresh,text,true,1,"single",
                Collections.emptyList(),"",text,new ContinuityStamp(scene,0,0,1000+frame));
    }
    @Test public void dynamicGallerySeparatesPhysicalOwnersEvenWithTheSameText(){
        RecognitionHistoryStore history=new RecognitionHistoryStore();VehicleEntityRepository entities=new VehicleEntityRepository();
        VehicleEntity a=entities.create(1,new NormalizedBounds(.1f,.2f,.3f,.5f),new AppearanceDescriptor(new float[]{1,0}),1);
        VehicleEntity b=entities.create(2,new NormalizedBounds(.6f,.2f,.8f,.5f),new AppearanceDescriptor(new float[]{0,1}),1);
        Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        try{
            assertTrue(history.upsertObservation(reading(0,a.entityId(),1,"AB12345",bitmap,true),null,true,"SYNTHETIC"));
            assertTrue(history.upsertObservation(reading(0,b.entityId(),2,"AB12345",bitmap,true),null,true,"SYNTHETIC"));
            assertEquals(2,history.size());assertEquals(2,entities.size());
            assertNotEquals(history.newestFirst().get(0).entityId,history.newestFirst().get(1).entityId);
            assertSame(a,entities.get(a.entityId()));assertSame(b,entities.get(b.entityId()));
        }finally{bitmap.recycle();}
    }
    @Test public void equalScoreReadingsOfTheSameVehicleKeepTheBestCardAndCountDistinctAttempts(){
        RecognitionHistoryStore history=new RecognitionHistoryStore();Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        try {
            assertTrue(history.upsertObservation(reading(3,1,6,"HV3MP",bitmap,true),null,true,"SYNTHETIC"));
            PlateObservation next=reading(3,1,12,"HVI3MP",bitmap,true);
            assertTrue(history.upsertObservation(next,null,true,"SYNTHETIC"));
            assertFalse(history.upsertObservation(next,null,true,"SYNTHETIC"));
            assertEquals(1,history.size());RecognitionHistoryItem item=history.newestFirst().get(0);
            assertEquals(2,item.observations);assertEquals("HV3MP",item.currentText);
            assertEquals("HV3MP",item.observationRecords().get(0).rawPrediction);
            assertEquals("HVI3MP",item.observationRecords().get(1).rawPrediction);
        } finally {history.clear();bitmap.recycle();}
    }
    @Test public void differentTextAndCarriedConsensusDoNotCreateFalseFreshObservations(){
        RecognitionHistoryStore history=new RecognitionHistoryStore();Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        try{
            assertTrue(history.upsertObservation(reading(0,1,1,"AB12345",bitmap,true),null,true,"SYNTHETIC"));
            assertFalse(history.upsertObservation(reading(0,1,2,"AB12345",bitmap,false),null,true,"SYNTHETIC"));
            assertTrue(history.upsertObservation(reading(0,2,3,"AB12346",bitmap,true),null,true,"SYNTHETIC"));assertEquals(2,history.size());
        }finally{bitmap.recycle();}
    }
    @Test public void separateStaticPhotosRetainSeparateSceneAndEntityEvidence(){
        RecognitionHistoryStore history=new RecognitionHistoryStore();Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        try{
            history.upsertObservation(reading(10,1,1,"AB12345",bitmap,true),null,false,"SYNTHETIC PHOTO A");
            history.upsertObservation(reading(11,2,2,"AB12345",bitmap,true),null,false,"SYNTHETIC PHOTO B");
            List<RecognitionHistoryObservation> records=history.newestFirst().get(0).observationRecords();assertEquals(2,records.size());
            assertNotEquals(records.get(0).sceneGeneration,records.get(1).sceneGeneration);assertNotEquals(records.get(0).entityId,records.get(1).entityId);
        }finally{bitmap.recycle();}
    }
}
