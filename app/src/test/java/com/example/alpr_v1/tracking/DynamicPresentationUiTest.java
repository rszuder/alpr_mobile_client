package com.example.alpr_v1.tracking;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import com.example.alpr_v1.R;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.ui.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,qualifiers="pl-rPL-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DynamicPresentationUiTest {
    private static final long S=1_000_000_000L;
    private DynamicVehicleDisplaySnapshot snapshot(long now,long measured,float x,float plateX,boolean visible,PlateTextConsensus read) {
        return new DynamicVehicleDisplaySnapshot(now,1,1,Collections.emptyMap(),
                Collections.singletonMap(1L,new DynamicVehicleIdentity.Presentation(1,1,visible,false,visible?0:6*S)),
                visible?Collections.singletonMap(1L,new NormalizedBounds(x,.2f,x+.6f,.7f)):Collections.emptyMap(),
                Collections.singletonMap(11L,1L),Collections.singletonMap(1L,new DynamicVehicleDisplaySnapshot.Vehicle(1,11,measured,measured,now,read,
                        new NormalizedQuad(new float[]{plateX,.52f,plateX+.2f,.52f,plateX+.2f,.6f,plateX,.6f}))));
    }
    private PlateTextConsensus read(){return new PlateTextConsensus("WA12345",.9f,4,true);}
    @Test public void cameraMotionIsPresentedBetweenPixelUpdatesAndNeverOutlivesItsEvidence() {
        DynamicPresentationController p=new DynamicPresentationController();
        p.geometry(Collections.emptyList(),snapshot(S,S,.1f,.3f,true,read()),S);
        DynamicVehicleDisplaySnapshot next=snapshot(S+200_000_000L,S,.12f,.32f,true,read());
        List<OverlayItem> current=p.geometry(Collections.emptyList(),next,next.sourceTimeNanos);
        List<OverlayItem> a=p.geometry(Collections.emptyList(),next,next.sourceTimeNanos+50_000_000L);
        List<OverlayItem> b=p.geometry(Collections.emptyList(),next,next.sourceTimeNanos+83_000_000L);
        assertTrue(a.get(0).normalizedBounds.left>current.get(0).normalizedBounds.left);
        assertTrue(b.get(0).normalizedBounds.left>a.get(0).normalizedBounds.left);
        assertTrue(b.get(0).normalizedBounds.left-a.get(0).normalizedBounds.left<.01f);
        assertEquals(a.get(0).normalizedBounds.left-current.get(0).normalizedBounds.left,
                a.get(1).normalizedBounds.left-current.get(1).normalizedBounds.left,.0001f);
        assertTrue(p.geometry(Collections.emptyList(),next,next.sourceTimeNanos+DynamicVehicleIdentity.COAST_NANOS+1).isEmpty());
        assertFalse(p.canAnimate(next,next.sourceTimeNanos+DynamicVehicleIdentity.COAST_NANOS+1));
    }
    @Test public void nativeOverlayMovesOnDrawWithoutAnotherModelOrSnapshotUpdate() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        DynamicVehicleDisplaySnapshot first=snapshot(S,S,.1f,.3f,true,read());
        overlay.setVehicleIdentityPresentation(first.identities,first.plateOwners);overlay.setDynamicVehicleSnapshot(first,0);
        overlay.setItems(Collections.emptyList(),720,1000);
        DynamicVehicleDisplaySnapshot next=snapshot(S+200_000_000L,S,.12f,.32f,true,read());
        overlay.setDynamicVehicleSnapshot(next,0);
        Bitmap image=Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888);
        try {
            overlay.draw(new Canvas(image));float x=overlay.snapshotVisibleItems().get(0).normalizedBounds.left;
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50));
            overlay.draw(new Canvas(image));float moved=overlay.snapshotVisibleItems().get(0).normalizedBounds.left;
            assertTrue("rendering must progress independently of inference",moved>x);
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
            overlay.draw(new Canvas(image));assertTrue(overlay.snapshotVisibleItems().isEmpty());
        } finally {image.recycle();}
    }
    @Test public void lateCameraDeliveryCannotMoveThePresentationClockBackwards() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        overlay.setDynamicVehicleSnapshot(snapshot(S,S,.1f,.3f,true,read()),0);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(100));
        long before=ReflectionHelpers.callInstanceMethod(overlay,"dynamicPresentationTime");
        overlay.setDynamicVehicleSnapshot(snapshot(S+50_000_000L,S,.11f,.31f,true,read()),0);
        long after=ReflectionHelpers.callInstanceMethod(overlay,"dynamicPresentationTime");
        assertTrue(after>=before);
    }
    @Test public void activeCameraPublicationLagCannotFlashTheLastOwnedPixelsAway() {
        DynamicPresentationController p=new DynamicPresentationController();
        DynamicVehicleDisplaySnapshot measured=snapshot(S,S,.1f,.3f,true,read());
        assertEquals(2,p.geometry(Collections.emptyList(),measured,S).size());
        DynamicVehicleDisplaySnapshot delayed=new DynamicVehicleDisplaySnapshot(S+700_000_000L,measured.analysisRunId,
                measured.sceneGeneration,measured.canonicalOwners,measured.identities,Collections.emptyMap(),measured.plateOwners,measured.vehicles);
        assertEquals(2,p.geometry(Collections.emptyList(),delayed,delayed.sourceTimeNanos).size());
        assertEquals(S,delayed.vehicles.get(1L).geometryAtNanos);
        assertTrue(p.geometry(Collections.emptyList(),delayed,S+1_000_000_001L).isEmpty());
        assertFalse(p.canAnimate(delayed,S+1_000_000_001L));
    }
    private DynamicVehicleDisplaySnapshot withoutPlate(long now,float x) {
        DynamicVehicleDisplaySnapshot frame=snapshot(now,S,x,x+.2f,true,read());
        return new DynamicVehicleDisplaySnapshot(now,frame.analysisRunId,frame.sceneGeneration,frame.canonicalOwners,
                frame.identities,frame.localGeometry,frame.plateOwners,Collections.singletonMap(1L,
                new DynamicVehicleDisplaySnapshot.Vehicle(1,11,S,S,0,read(),null,now)));
    }
    @Test public void unavailablePlatePublicationFollowsItsBodyWithoutRenewingTheAttachment() {
        DynamicPresentationController p=new DynamicPresentationController();
        assertEquals(2,p.geometry(Collections.emptyList(),snapshot(S,S,.1f,.3f,true,read()),S).size());
        DynamicVehicleDisplaySnapshot moving=withoutPlate(S+450_000_000L,.14f);
        List<OverlayItem> shown=p.geometry(Collections.emptyList(),moving,moving.sourceTimeNanos);
        assertEquals(2,shown.size());
        assertEquals(.2f,shown.get(1).normalizedBounds.left-shown.get(0).normalizedBounds.left,.0001f);
        assertEquals(0,moving.vehicles.get(1L).plateProofAtNanos);
        assertEquals(S,moving.vehicles.get(1L).plateMeasuredAtNanos);
        assertEquals(2,p.geometry(Collections.emptyList(),withoutPlate(S+900_000_000L,.16f),S+900_000_000L).size());
        assertEquals("displaying the inferred attachment must not renew its lifetime",1,
                p.geometry(Collections.emptyList(),withoutPlate(S+1_000_000_001L,.17f),S+1_000_000_001L).size());
    }
    @Test public void cachedAttachmentCannotCrossRunsOrHideAnExplicitlyInvalidNewQuad() {
        DynamicPresentationController p=new DynamicPresentationController();
        p.geometry(Collections.emptyList(),snapshot(S,S,.1f,.3f,true,read()),S);
        assertEquals(1,p.geometry(Collections.emptyList(),snapshot(S+200_000_000L,S,.1f,.9f,true,read()),S+200_000_000L).size());
        assertEquals(Collections.singleton(1L),p.consumePlateRefresh());
        DynamicVehicleDisplaySnapshot missing=withoutPlate(S+300_000_000L,.12f);
        DynamicVehicleDisplaySnapshot nextRun=new DynamicVehicleDisplaySnapshot(missing.sourceTimeNanos,2,missing.sceneGeneration,
                missing.canonicalOwners,missing.identities,missing.localGeometry,missing.plateOwners,missing.vehicles);
        assertEquals(1,p.geometry(Collections.emptyList(),nextRun,nextRun.sourceTimeNanos).size());
    }
    @Implements(value=com.example.alpr_v1.pipeline.AlprPipeline.class,isInAndroidSdk=false)
    public static class SnapshotPipeline {
        static DynamicVehicleDisplaySnapshot current;
        @Implementation public DynamicVehicleDisplaySnapshot dynamicDisplaySnapshot(com.example.alpr_v1.continuity.ContinuityStamp source){return current;}
    }
    @Test @Config(shadows=SnapshotPipeline.class)
    public void stablePreviewDecisionCannotReplaceTheDynamicReadingWithLegacySearchStatus() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
        LivePresentationController hud=new LivePresentationController(root.findViewById(R.id.live_status_strip),root.findViewById(R.id.live_status_dot),
                root.findViewById(R.id.live_status),root.findViewById(R.id.live_status_secondary),root.findViewById(R.id.live_diagnostics_toggle),
                root.findViewById(R.id.live_event),root.findViewById(R.id.live_hud_row),new TextView(context),root.findViewById(R.id.recognition_hint),
                root.findViewById(R.id.confirmed_result_tray),root.findViewById(R.id.confirmed_result_text),root.findViewById(R.id.confirmed_result_confidence),root.findViewById(R.id.confirmed_result_meta));
        DynamicVehicleDisplaySnapshot current=snapshot(S,S,.1f,.3f,true,read());
        com.example.alpr_v1.pipeline.AlprPipeline pipeline=new com.example.alpr_v1.pipeline.AlprPipeline(context,
                new com.example.alpr_v1.model.ModelRegistry(context,true),new com.example.alpr_v1.metrics.MetricsCollector(),
                new com.example.alpr_v1.autotune.AutoTuneManager(context));
        SnapshotPipeline.current=current;
        try {
            pipeline.setSceneHandlingMode(com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY);
            com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
            android.content.SharedPreferences prefs=context.getSharedPreferences("dynamic-preview-status-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            ReflectionHelpers.setField(main,"cameraStarted",true);ReflectionHelpers.setField(main,"livePresentation",hud);
            ReflectionHelpers.setField(main,"overlayView",root.findViewById(R.id.detection_overlay));
            hud.renderDynamicResult(current,1);
            String expected=((TextView)root.findViewById(R.id.live_status)).getText().toString();
            com.example.alpr_v1.continuity.SceneTransitionDecision stable=new com.example.alpr_v1.continuity.SceneTransitionDecision(1,
                    com.example.alpr_v1.continuity.SceneTransitionAction.NONE,com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY,
                    com.example.alpr_v1.continuity.SceneContinuityState.STABLE,com.example.alpr_v1.continuity.ContinuityAssessment.none(),
                    true,true,true,false,false,false,false,false,false,false,false,false,false,"synthetic_stable_preview");
            ReflectionHelpers.callInstanceMethod(main,"renderPreviewContinuityDecision",
                    ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.continuity.SceneTransitionDecision.class,stable),
                    ReflectionHelpers.ClassParameter.from(float.class,0f),ReflectionHelpers.ClassParameter.from(float.class,0f),
                    ReflectionHelpers.ClassParameter.from(List.class,Collections.emptyList()));
            assertEquals(expected,((TextView)root.findViewById(R.id.live_status)).getText().toString());
            assertEquals("WA12345",((TextView)root.findViewById(R.id.confirmed_result_text)).getText().toString());
        } finally {pipeline.close();hud.dispose();SnapshotPipeline.current=null;}
    }
    @Test public void oldMpCannotFlashABoxBeforeItsPixelsHaveCaughtUpToPreview() {
        DynamicPresentationController presenter=new DynamicPresentationController();
        OverlayItem raw=new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(.1f,.2f,.7f,.7f),Collections.emptyList(),"",1,false);
        DynamicVehicleDisplaySnapshot old=snapshot(3*S,S,.1f,.3f,true,read());
        DynamicVehicleDisplaySnapshot waiting=new DynamicVehicleDisplaySnapshot(old.sourceTimeNanos,old.analysisRunId,old.sceneGeneration,
                old.canonicalOwners,old.identities,Collections.emptyMap(),old.plateOwners,old.vehicles);
        assertTrue(presenter.geometry(Collections.singletonList(raw),waiting,3*S).isEmpty());
        assertEquals(2,presenter.geometry(Collections.emptyList(),old,3*S).size());
    }
    @Test public void temporarilyUnavailableSnapshotDoesNotEnableStaticPresentation() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        DynamicVehicleDisplaySnapshot current=snapshot(S,S,.1f,.3f,true,read());
        overlay.setVehicleIdentityPresentation(current.identities,current.plateOwners);
        overlay.setDynamicVehicleSnapshot(true,current,0);overlay.setItems(Collections.emptyList(),720,1000);
        assertEquals(2,overlay.snapshotVisibleItems().size());
        overlay.setDynamicVehicleSnapshot(true,null,0);
        overlay.setItems(Collections.singletonList(new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.8f,.8f,.9f,.9f),Collections.emptyList(),"",99,false)),720,1000);
        assertTrue(ReflectionHelpers.getField(overlay,"ordinaryDynamicPresentation"));assertTrue(overlay.snapshotVisibleItems().isEmpty());
        overlay.setDynamicVehicleSnapshot(true,current,0);overlay.setItems(Collections.emptyList(),720,1000);
        assertEquals(2,overlay.snapshotVisibleItems().size());
    }
    @Test public void blockedMtIsExplainedWithoutInventingAnOcrResult() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
        LivePresentationController hud=new LivePresentationController(root.findViewById(R.id.live_status_strip),root.findViewById(R.id.live_status_dot),
                root.findViewById(R.id.live_status),root.findViewById(R.id.live_status_secondary),root.findViewById(R.id.live_diagnostics_toggle),
                root.findViewById(R.id.live_event),root.findViewById(R.id.live_hud_row),new TextView(context),root.findViewById(R.id.recognition_hint),
                root.findViewById(R.id.confirmed_result_tray),root.findViewById(R.id.confirmed_result_text),root.findViewById(R.id.confirmed_result_confidence),root.findViewById(R.id.confirmed_result_meta));
        com.example.alpr_v1.acquisition.DynamicVehicleSizeGate gate=new com.example.alpr_v1.acquisition.DynamicVehicleSizeGate(
                new com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Config(256,112,100,64));gate.setEnabled(true);
        gate.observe(new com.example.alpr_v1.continuity.ContinuityStamp(1,0,0,S),1080,1440,
                Collections.singletonMap(1L,new NormalizedBounds(0,0,185f/1080,143f/1440)));
        DynamicVehicleDisplaySnapshot old=snapshot(S,S,.1f,.3f,true,new PlateTextConsensus("",0,0,false));
        DynamicVehicleDisplaySnapshot blocked=new DynamicVehicleDisplaySnapshot(S,1,1,old.canonicalOwners,old.identities,
                old.localGeometry,old.plateOwners,old.vehicles,gate.decisions());
        hud.renderDynamicResult(blocked,1);
        assertEquals(context.getString(R.string.dynamic_vehicle_too_small),((TextView)root.findViewById(R.id.live_status)).getText().toString());
        assertTrue(((TextView)root.findViewById(R.id.live_status_secondary)).getText().toString().contains("185 × 143"));
        hud.showState(LivePresentationController.State.TRACKING,"");
        assertTrue(((TextView)root.findViewById(R.id.live_status_secondary)).getText().toString().contains("256 × 112"));hud.dispose();
    }
    @Test public void rejectedPartialGeometryExplainsWaitingAndKeepsTheLastReading() {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
        LivePresentationController hud=new LivePresentationController(root.findViewById(R.id.live_status_strip),root.findViewById(R.id.live_status_dot),
                root.findViewById(R.id.live_status),root.findViewById(R.id.live_status_secondary),root.findViewById(R.id.live_diagnostics_toggle),
                root.findViewById(R.id.live_event),root.findViewById(R.id.live_hud_row),new TextView(context),root.findViewById(R.id.recognition_hint),
                root.findViewById(R.id.confirmed_result_tray),root.findViewById(R.id.confirmed_result_text),root.findViewById(R.id.confirmed_result_confidence),root.findViewById(R.id.confirmed_result_meta));
        DynamicVehicleDisplaySnapshot current=snapshot(2*S,S,.1f,.3f,true,read());
        DynamicPlateGeometryGate gate=new DynamicPlateGeometryGate();
        DynamicVehicleIdentity.Stamp owner=new DynamicVehicleIdentity.Stamp(1,1,1,1,1);
        com.example.alpr_v1.continuity.ContinuityStamp source=new com.example.alpr_v1.continuity.ContinuityStamp(1,1,0,1,S,
                com.example.alpr_v1.continuity.SourceTimestampDomain.CAMERAX_SENSOR);
        NormalizedQuad partial=new NormalizedQuad(new float[]{.3f,.52f,.4f,.52f,.4f,.6f,.3f,.6f});
        DynamicPlateGeometryGate.Decision rejected=gate.evaluate(owner,source,current.vehicles.get(1L).plate.bounds(),
                partial,current.localGeometry.get(1L),true);
        assertFalse(rejected.accepted());
        DynamicVehicleDisplaySnapshot waiting=new DynamicVehicleDisplaySnapshot(2*S,1,1,current.canonicalOwners,current.identities,
                current.localGeometry,current.plateOwners,current.vehicles,Collections.emptyMap(),Collections.singletonMap(1L,rejected));
        hud.renderDynamicResult(waiting,1);
        assertEquals(context.getString(R.string.dynamic_plate_geometry_waiting),((TextView)root.findViewById(R.id.live_status)).getText().toString());
        assertEquals("WA12345",((TextView)root.findViewById(R.id.confirmed_result_text)).getText().toString());
        assertEquals(context.getString(R.string.dynamic_plate_geometry_last),((TextView)root.findViewById(R.id.live_status_secondary)).getText().toString());
        hud.renderDynamicResult(current,1);
        assertEquals("WA12345",((TextView)root.findViewById(R.id.confirmed_result_text)).getText().toString());
        assertNotEquals(context.getString(R.string.dynamic_plate_geometry_waiting),((TextView)root.findViewById(R.id.live_status)).getText().toString());
    }
    @Test public void geometryComesFromOneSnapshotEvenWhenLegacyLayerIsEmptyOrWrong() {
        DynamicPresentationController p=new DynamicPresentationController();
        DynamicVehicleDisplaySnapshot s=snapshot(S,S,.1f,.3f,true,read());
        List<OverlayItem> wrong=Collections.singletonList(new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.9f,.9f,1,1),Collections.emptyList(),"",11,false));
        List<OverlayItem> shown=p.geometry(wrong,s,S);assertEquals(2,shown.size());
        assertEquals(.3f,shown.get(1).normalizedBounds.left,.001f);
        assertEquals(2,p.geometry(Collections.emptyList(),s,S+100_000_000L).size());
    }
    @Test public void plateOutsideVehicleIsDroppedAndRefreshIsBoundedPerMeasurement() {
        DynamicPresentationController p=new DynamicPresentationController();
        DynamicVehicleDisplaySnapshot s=snapshot(S,S,.1f,.8f,true,read());
        assertEquals(1,p.geometry(Collections.emptyList(),s,S).size());assertEquals(Collections.singleton(1L),p.consumePlateRefresh());
        p.geometry(Collections.emptyList(),s,S+100_000_000L);assertTrue(p.consumePlateRefresh().isEmpty());
        p.geometry(Collections.emptyList(),snapshot(2*S,2*S,.1f,.8f,true,read()),2*S);
        assertEquals(Collections.singleton(1L),p.consumePlateRefresh());
    }
    @Test public void localPixelUpdatesDoNotJumpOnTheFirstDisplayFrameBetweenMpMeasurements() {
        DynamicPresentationController p=new DynamicPresentationController();
        DynamicVehicleDisplaySnapshot first=snapshot(S,S,.1f,.3f,true,read());
        p.geometry(Collections.emptyList(),first,S);
        float before=p.geometry(Collections.emptyList(),first,S+240_000_000L).get(0).normalizedBounds.left;
        DynamicVehicleDisplaySnapshot next=snapshot(S+250_000_000L,S,.12f,.32f,true,read());
        List<OverlayItem> shown=p.geometry(Collections.emptyList(),next,next.sourceTimeNanos);
        float current=shown.get(0).normalizedBounds.left;
        assertTrue("a local pixel publication must not jump the whole 0.02 between display frames",current-before<.006f);
        assertTrue(current>=before);
        for(int i=1;i<=10;i++) {
            shown=p.geometry(Collections.emptyList(),next,next.sourceTimeNanos+i*16_000_000L);
            float moved=shown.get(0).normalizedBounds.left;
            assertTrue("bounded progress on each display frame",moved-current<.006f);
            assertTrue(moved>=current);
            assertEquals("the plate must follow the same displayed body movement",.2f,
                    shown.get(1).normalizedBounds.left-moved,.001f);
            current=moved;
        }
    }
    @Test public void neuralAndLocalCorrectionsAreSmoothedWithoutChangingTheirEvidenceTimes() {
        DynamicPresentationController p=new DynamicPresentationController();
        p.geometry(Collections.emptyList(),snapshot(S,S,.1f,.3f,true,read()),S);
        List<OverlayItem> moved=p.geometry(Collections.emptyList(),snapshot(S+100_000_000L,S,.11f,.31f,true,read()),S+100_000_000L);
        assertTrue(moved.get(0).normalizedBounds.left>.1f);assertTrue(moved.get(0).normalizedBounds.left<.11f);
        List<OverlayItem> correction=p.geometry(Collections.emptyList(),snapshot(S+200_000_000L,S+200_000_000L,.2f,.4f,true,read()),S+200_000_000L);
        assertTrue(correction.get(0).normalizedBounds.left>.11f);assertTrue(correction.get(0).normalizedBounds.left<.2f);
        for(OverlayItem item:correction)if(item.kind==OverlayItem.Kind.PLATE)
            for(PointF point:item.normalizedKeypoints)assertTrue(correction.get(0).normalizedBounds.contains(point.x,point.y));
        List<OverlayItem> settled=p.geometry(Collections.emptyList(),snapshot(3*S,S+200_000_000L,.2f,.4f,true,read()),3*S);
        assertEquals(.2f,settled.get(0).normalizedBounds.left,.001f);
    }
    @Test public void recoveryKeepsSelectionButNeverDrawsTheLostBody() {
        DynamicPresentationController p=new DynamicPresentationController();
        assertEquals(1,p.selected(snapshot(S,S,.1f,.3f,true,read()),0));
        DynamicVehicleDisplaySnapshot lost=snapshot(5*S,S,.1f,.3f,false,read());
        assertEquals(1,p.selected(lost,0));assertTrue(p.geometry(Collections.emptyList(),lost,5*S).isEmpty());
        assertEquals(0,p.selected(snapshot(6*S+1,S,.1f,.3f,false,read()),0));
    }
    @Test public void explicitLostTargetNeverSilentlySelectsAnotherVehiclesReading() {
        DynamicPresentationController p=new DynamicPresentationController();
        Map<Long,DynamicVehicleIdentity.Presentation> owners=new LinkedHashMap<>();
        owners.put(1L,new DynamicVehicleIdentity.Presentation(1,1,false,false,6*S));
        owners.put(2L,new DynamicVehicleIdentity.Presentation(2,2,true,false));
        DynamicVehicleDisplaySnapshot s=new DynamicVehicleDisplaySnapshot(7*S,1,1,Collections.emptyMap(),owners,Collections.emptyMap(),Collections.emptyMap());
        assertEquals(0,p.selected(s,1));assertEquals(0,p.selected(s,999));
        assertEquals(2,p.selected(s,0));
    }
    @Test public void renderedNumberAndColorStayStableAcrossAcquisitionAndMzChanges() throws Exception {
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        int color=-1;
        for(int i=0;i<20;i++) {
            DynamicVehicleDisplaySnapshot s=snapshot(S+i*100_000_000L,S,.1f,.3f,true,
                    new PlateTextConsensus(i%2==0?"WA12345":"WA1234S",i%2==0?.9f:.6f,i+1,i%2==0));
            overlay.setVehicleIdentityPresentation(s.identities,s.plateOwners);overlay.setDynamicVehicleSnapshot(s,0);
            overlay.setActiveVehicleEntityId(i%2);overlay.setVehicleEntityStates(Collections.emptySet(),Collections.emptySet(),Collections.emptyMap());
            String label=ReflectionHelpers.callInstanceMethod(overlay,"vehicleLabelForTesting",ReflectionHelpers.ClassParameter.from(long.class,1L));
            assertTrue(label,label.contains("P1"));
            assertTrue(label,label.contains("MZ"));
            assertTrue(label,label.contains(i%2==0?"WA12345":"WA1234S"));
            assertFalse(label,label.contains("Weryfikacja"));
            assertFalse(label,label.contains("zap."));
            int current=ReflectionHelpers.callInstanceMethod(overlay,"vehicleColorForTesting",ReflectionHelpers.ClassParameter.from(long.class,1L));
            if(color<0)color=current;else assertEquals(color,current);
            assertEquals(context.getColor(R.color.cockpit_cyan),current);
            assertNull(ReflectionHelpers.getField(overlay,"overlayAnimator"));
            assertNull(ReflectionHelpers.getField(overlay,"plateAbsorbAnimator"));
        }
        String dir=System.getenv("DYNAMIC_STABILITY_UI_ARTIFACTS");
        if(dir!=null){new File(dir).mkdirs();Bitmap bitmap=Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888);overlay.draw(new Canvas(bitmap));
            try(FileOutputStream out=new FileOutputStream(new File(dir,"dyn-fixed-color-p1.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    }
    @Test public void hudKeepsReadingAcrossSkippedMzAndTargetEventsAtBothFontScales() {
        for(float scale:new float[]{1f,1.5f}) {
            Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
            android.content.res.Configuration config=new android.content.res.Configuration(context.getResources().getConfiguration());config.fontScale=scale;
            context=new ContextThemeWrapper(context.createConfigurationContext(config),R.style.Theme_ALPR_v1);
            View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
            LivePresentationController hud=new LivePresentationController(root.findViewById(R.id.live_status_strip),root.findViewById(R.id.live_status_dot),
                    root.findViewById(R.id.live_status),root.findViewById(R.id.live_status_secondary),root.findViewById(R.id.live_diagnostics_toggle),
                    root.findViewById(R.id.live_event),root.findViewById(R.id.live_hud_row),new TextView(context),root.findViewById(R.id.recognition_hint),
                    root.findViewById(R.id.confirmed_result_tray),root.findViewById(R.id.confirmed_result_text),root.findViewById(R.id.confirmed_result_confidence),root.findViewById(R.id.confirmed_result_meta));
            PlateTextConsensus reading=read();hud.renderDynamicResult(snapshot(S,S,.1f,.3f,true,reading),1);
            for(int i=1;i<=20;i++) {hud.onTargetLost();hud.showState(LivePresentationController.State.RECOGNIZING,"");
                hud.renderDynamicResult(snapshot(S+i*100_000_000L,S,.1f,.3f,true,reading),1);}
            assertEquals(View.VISIBLE,root.findViewById(R.id.confirmed_result_tray).getVisibility());
            assertEquals("WA12345",((TextView)root.findViewById(R.id.confirmed_result_text)).getText().toString());
            hud.renderDynamicResult(snapshot(5*S,S,.1f,.3f,false,reading),1);
            assertTrue(((TextView)root.findViewById(R.id.confirmed_result_confidence)).getText().toString().contains("Ostatni"));
            hud.dispose();
        }
    }
}
