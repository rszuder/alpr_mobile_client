package com.example.alpr_v1.ui;

import android.content.Context;
import com.example.alpr_v1.camera.CameraSetupReadiness.Reason;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32)
public class CameraOperatorPresentationTest {
    final Context context=RuntimeEnvironment.getApplication();
    @Test public void cameraOffGivesAnActionInsteadOfClaimingAStartup(){
        CameraOperatorPresentation p=CameraOperatorPresentation.from(context,Reason.CAMERA_NOT_BOUND,false,false,false);
        assertEquals("Kamera jest wyłączona",p.primary);assertTrue(p.hint.contains("Podgląd"));
        assertEquals(p.primary,CameraOperatorPresentation.from(context,Reason.READY,true,true,false).primary);
    }
    @Test public void pendingStartAndPreviewHaveAWaitingMessage(){
        for(boolean preview:new boolean[]{false,true}){
            CameraOperatorPresentation p=CameraOperatorPresentation.from(context,Reason.CAMERA_NOT_BOUND,preview,false,true);
            assertEquals("Uruchamianie kamery…",p.primary);assertFalse(p.message().contains("CAMERA_NOT_BOUND"));
        }
    }
    @Test public void everyStructuredReasonHasHumanCopyAndKeepsItsTechnicalIdentity(){
        for(Reason r:Reason.values()){
            CameraOperatorPresentation p=CameraOperatorPresentation.from(context,r,true,false,true);
            assertFalse(p.primary.isEmpty());assertFalse(p.hint.isEmpty());
            for(Reason code:Reason.values())assertFalse(p.message(),p.message().contains(code.name()));
            assertEquals(r,CameraOperatorPresentation.reason(r.name()));
            if(r!=Reason.READY){assertEquals(r,CameraOperatorPresentation.reason(r.description()));assertTrue(r.description().startsWith(r.name()+" · "));}
        }
        assertEquals("",Reason.READY.description());
    }
    @Test public void frameAndChangedSetupAreDistinctFromAnOffCamera(){
        assertEquals("Czekam na nowy obraz z kamery…",CameraOperatorPresentation.from(context,Reason.NO_FRESH_CAMERA_FRAME,true,false,false).primary);
        assertEquals("Zmieniono ustawienia kamery",CameraOperatorPresentation.from(context,Reason.CAMERA_SETUP_SNAPSHOT_STALE,true,false,false).primary);
        assertTrue(CameraOperatorPresentation.from(context,Reason.CAMERA_SETUP_GEOMETRY_MISMATCH,true,false,false).hint.contains("TV"));
    }
    @Test public void calibrationDisplayMapsReasonsWithoutAddingAnUnrelatedPhotoError(){
        for(Reason r:Reason.values())if(r!=Reason.READY){
            assertFalse(CalibrationOperatorText.preparationMessage(context,r.description()).contains(r.name()));
            assertEquals("",CalibrationOperatorText.preparationDetail(context,r.description()));
        }
        assertEquals("Sprawdź obraz",CameraOperatorPresentation.readableDetail(context,"Sprawdź obraz"));
        assertFalse(CameraOperatorPresentation.readableDetail(context,"CAMERA_BINDING_FAILED: synthetic").contains("CAMERA_BINDING_FAILED"));
    }
}
