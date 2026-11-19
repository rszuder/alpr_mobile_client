package com.example.alpr_v1.ui.readiness;

import com.example.alpr_v1.R;
import com.example.alpr_v1.model.ModelRole;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReadinessPresentationTest {
    ReadinessFinding later(String id,ReadinessFinding.Domain domain){return new ReadinessFinding(id,domain,ReadinessFinding.Status.NOT_CHECKABLE_YET,id,"",false,null,"test","snapshot");}
    ReadinessFinding change(String id,ReadinessTarget target){return new ReadinessFinding(id,ReadinessFinding.Domain.CONFIG,ReadinessFinding.Status.NEEDS_CHANGE,id,"",true,target,"test","snapshot");}
    ReadinessPresentation view(boolean research,ReadinessFinding...findings){return new ReadinessPresentation(new ReadinessResult("snapshot","test",Arrays.asList(findings)),research);}
    @Test public void twoDeferredChecksMarkThreeRelevantSectionsAndRemainTwoFindings(){
        ReadinessPresentation p=view(true,later("camera_verification",ReadinessFinding.Domain.DEVICE),later("session_preparation",ReadinessFinding.Domain.SESSION_PREPARATION));
        for(int section:new int[]{1,2,3}){assertEquals(R.color.options_accent,p.section(section).color());assertEquals(1,p.section(section).later());}
        assertNull(p.section(0));assertNull(p.section(4));assertTrue(p.controls().isEmpty());assertEquals(2,p.result.visibleFindings().size());
    }
    @Test public void ordinaryModelPreparationDoesNotImplyResearchSessionSettings(){
        ReadinessPresentation p=view(false,later("session_preparation",ReadinessFinding.Domain.SESSION_PREPARATION));
        assertNotNull(p.section(2));assertNull(p.section(3));
    }
    @Test public void knownChangesTakePrecedenceWithoutLosingDeferredCounts(){
        ReadinessPresentation p=view(true,change("photo_area",ReadinessTarget.photo()),later("camera_verification",ReadinessFinding.Domain.DEVICE));
        assertEquals(R.color.alpr_warning,p.section(1).color());assertEquals(1,p.section(1).changes());assertEquals(1,p.section(1).later());
        assertEquals(1,p.controls().get(R.id.settings_f6_photo_roi).changes());
    }
    @Test public void modelRuntimePointsToOnlyItsExistingModelWithoutCreatingAnImportAction(){
        ReadinessFinding f=later("model_plate_runtime",ReadinessFinding.Domain.DEVICE);ReadinessPresentation p=view(true,f);
        assertNull(f.target);assertEquals(1,p.controls().size());assertTrue(p.controls().containsKey(ReadinessTarget.model(ModelRole.PLATE,true).controlId));
        assertFalse(p.controls().containsKey(ReadinessTarget.model(ModelRole.VEHICLE,true).controlId));
    }
    @Test public void correctedIssueRemovesItsFieldWhileCameraRemainsInformational(){
        ReadinessFinding camera=later("camera_verification",ReadinessFinding.Domain.DEVICE);
        ReadinessPresentation before=view(true,change("photo_area",ReadinessTarget.photo()),camera),after=view(true,camera);
        assertEquals(R.color.alpr_warning,before.section(1).color());assertEquals(R.color.options_accent,after.section(1).color());assertTrue(after.controls().isEmpty());
        ReadinessPresentation ready=view(true);assertNull(ready.section(1));assertEquals(ReadinessResult.State.READY,ready.result.state);
    }
    @Test public void deviceConditionsAndUnfinishedAssessmentDoNotInventAnOptionsField(){
        ReadinessPresentation p=view(true,later("thermal",ReadinessFinding.Domain.DEVICE),later("assessment_pending",ReadinessFinding.Domain.CONFIG));
        for(int i=0;i<5;i++)assertNull(p.section(i));assertTrue(p.controls().isEmpty());
    }
    @Test public void repeatedLocationsCountEachIssueOnceAndPendingClearsLocations(){
        ReadinessFinding f=change("variant",ReadinessTarget.model(ModelRole.PLATE,true));ReadinessPresentation p=view(true,f,f);
        assertEquals(1,p.section(2).changes());assertEquals(1,p.controls().get(f.target.controlId).changes());
        ReadinessPresentation pending=new ReadinessPresentation(null,true);assertTrue(pending.controls().isEmpty());assertNull(pending.section(2));
    }
}
