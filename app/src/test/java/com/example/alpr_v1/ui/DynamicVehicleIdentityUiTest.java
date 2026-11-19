package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.*;
import android.view.View;
import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

/** Synthetic MP evidence, actual domain tracker and actual HUD renderer; no camera/NN claims. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class DynamicVehicleIdentityUiTest {
    static final long SECOND=1_000_000_000L;
    VehicleEntityRepository repo;VehicleTrackManager manager;DetectionOverlayView hud;boolean normalAfter;
    VehicleTrackingFrame latestFrame;List<OverlayItem> latestItems;
    final List<String> timeline=new ArrayList<>();
    void setup(boolean dynamic){
        normalAfter=dynamic;
        repo=new VehicleEntityRepository();manager=new VehicleTrackManager(repo,16,SECOND,5*SECOND);
        manager.setNormalDynamicIdentity(dynamic,SECOND);
        hud=new DetectionOverlayView(RuntimeEnvironment.getApplication(),null);
        hud.setDynamicVehicleMotion(false);hud.setStationaryScene(false);
        hud.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));hud.layout(0,0,720,900);
    }
    AppearanceDescriptor appearance(float red,float blue){return new AppearanceDescriptor(new float[]{red,blue});}
    VehicleTrackManager.Observation obs(float x,int index,boolean red){return obs(x,index,appearance(red?1:0,red?0:1));}
    VehicleTrackManager.Observation obs(float x,int index,AppearanceDescriptor a){return new VehicleTrackManager.Observation(new NormalizedBounds(x,.2f,x+.20f,.55f),.9f,a,index);}
    List<VehicleTrackManager.Snapshot> update(String scene,long at,List<VehicleTrackManager.Observation> observations,boolean ocr)throws Exception {
        List<VehicleTrackManager.Snapshot> snapshots=manager.update(observations,at);
        Map<Long,NormalizedBounds> raw=new HashMap<>();List<VehicleCandidate> candidates=new ArrayList<>();
        for(VehicleTrackManager.Snapshot v:snapshots){
            candidates.add(new VehicleCandidate(v.entityId,v.vehicleTrackId,v.bounds,v.confidence,v.confidence,0,v.predicted,v.missedUpdates,v.lastMeasurementTimestampNanos,at,v.sourceIndex));
            if(!v.predicted&&v.sourceIndex>=0)raw.put(v.entityId,observations.get(v.sourceIndex).bounds);
        }
        VehicleTrackingFrame frame=new VehicleTrackingFrame(at,at,at,0,candidates);
        latestFrame=frame;
        List<OverlayItem> items=VehicleMeasurementOverlay.from(frame,raw);
        if(!normalAfter){
            List<com.example.alpr_v1.vision.Detection> detections=new ArrayList<>();
            for(VehicleTrackManager.Observation observation:observations){NormalizedBounds box=observation.bounds;
                detections.add(new com.example.alpr_v1.vision.Detection(0,observation.confidence,box.left*1000,box.top*1000,box.right*1000,box.bottom*1000,Collections.emptyList()));}
            List<Long> ids=org.robolectric.util.ReflectionHelpers.callStaticMethod(Class.forName("com.example.alpr_v1.pipeline.MobileAlprEngine"),"vehicleOverlayIds",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(List.class,detections),org.robolectric.util.ReflectionHelpers.ClassParameter.from(List.class,candidates),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,1000),org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,1000));
            items=new ArrayList<>();for(int index=0;index<detections.size();index++){NormalizedBounds box=observations.get(index).bounds;
                items.add(new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(box.left,box.top,box.right,box.bottom),Collections.emptyList(),"MP before",ids.get(index),false));}
        }
        latestItems=items;hud.setItems(items,1000,1000);
        Map<Long,EntityRecognitionSnapshot> reads=new HashMap<>();
        if(ocr)for(VehicleTrackManager.Snapshot v:snapshots)if(v.sourceIndex>=0)
            reads.put(v.entityId,new EntityRecognitionSnapshot(v.entityId,100+v.entityId,"AB12345",.9,true,2));
        hud.setVehicleEntityStates(reads.keySet(),Collections.emptySet(),reads);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        Bitmap image=Bitmap.createBitmap(720,900,Bitmap.Config.ARGB_8888);hud.draw(new Canvas(image));
        for(VehicleTrackManager.Snapshot v:snapshots)if(v.sourceIndex>=0){
            String label=hud.vehicleLabelForTesting(v.entityId);assertTrue(label,label.startsWith("P"+v.entityId));
            timeline.add(scene+","+at+","+v.sourceIndex+","+v.vehicleTrackId+","+v.entityId+",\""+label+"\","+(ocr?"AB12345":""));
        }
        String folder=System.getenv("IDENTITY_UI_ARTIFACTS");
        if(folder!=null){File dir=new File(folder);dir.mkdirs();try(FileOutputStream stream=new FileOutputStream(new File(dir,scene+"-"+at+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}}
        image.recycle();return snapshots;
    }
    long entity(List<VehicleTrackManager.Snapshot> values,int source){for(VehicleTrackManager.Snapshot v:values)if(v.sourceIndex==source)return v.entityId;throw new AssertionError("Missing source "+source);}
    @After public void trace()throws Exception {
        String folder=System.getenv("IDENTITY_UI_ARTIFACTS");if(folder==null||timeline.isEmpty())return;
        File dir=new File(folder);dir.mkdirs();File file=new File(dir,"timeline-"+Integer.toHexString(timeline.get(0).hashCode())+".csv");
        try(java.io.PrintWriter out=new java.io.PrintWriter(file,"UTF-8")){out.println("scene,time_ns,source_index,track_id,entity_id,hud_label,ocr");for(String line:timeline)out.println(line);}
    }
    @Test public void occlusionReversedOrderAndTerminalReleaseHaveStableActualHudNumbers()throws Exception {
        for(boolean after:new boolean[]{false,true}){
            setup(after);String scene=after?"occlusion-after":"occlusion-before-replayed-retirement";
            List<VehicleTrackManager.Snapshot> first=update(scene,SECOND,Arrays.asList(obs(.1f,0,true),obs(.7f,1,false)),false);
            long a=entity(first,0),b=entity(first,1);
            update(scene,1_200_000_000L,Collections.singletonList(obs(.69f,0,false)),false);
            assertEquals("Missing A must not have a rendered MP box", "",hud.vehicleLabelForTesting(a));
            if(!after)repo.retireEntity(a); // Exact repository action identified in the pre-patch pipeline audit.
            manager.predict(2_300_000_001L);
            List<VehicleTrackManager.Snapshot> returned=update(scene,2_400_000_000L,Arrays.asList(obs(.68f,0,false),obs(.11f,1,true)),true);
            assertEquals(b,entity(returned,0));
            if(after){assertEquals(a,entity(returned,1));assertEquals(2,manager.stats().entitiesCreated);}
            else {assertNotEquals(a,entity(returned,1));assertEquals(3,manager.stats().entitiesCreated);}
            assertNotEquals(first.get(0).vehicleTrackId,returned.get(0).vehicleTrackId);
        }
    }
    @Test public void twoVehiclesCrossWithReversedIndicesAndTheSameOcrWithoutSharingHudIdentity()throws Exception {
        for(boolean after:new boolean[]{false,true}){
        setup(after);String scene=after?"crossing-after":"crossing-before";
        List<VehicleTrackManager.Snapshot> first=update(scene,SECOND,Arrays.asList(obs(.1f,0,true),obs(.7f,1,false)),true);
        long a=entity(first,0),b=entity(first,1);
        List<VehicleTrackManager.Snapshot> next=update(scene,1_200_000_000L,Arrays.asList(obs(.55f,0,false),obs(.25f,1,true)),false);
        assertEquals(a,entity(next,1));assertEquals(b,entity(next,0));
        next=update(scene,1_400_000_000L,Arrays.asList(obs(.4f,0,true),obs(.4f,1,false)),false);
        assertEquals(a,entity(next,0));assertEquals(b,entity(next,1));
        next=update(scene,1_600_000_000L,Arrays.asList(obs(.25f,0,false),obs(.55f,1,true)),true);
        assertEquals(a,entity(next,1));assertEquals(b,entity(next,0));assertEquals(2,manager.stats().entitiesCreated);
        assertTrue(hud.vehicleLabelForTesting(a).contains("AB12345"));assertTrue(hud.vehicleLabelForTesting(b).contains("AB12345"));
        }
    }
    @Test public void geometryAloneCannotRecoverAMissingVehicle()throws Exception {
        setup(true);long old=entity(update("no-appearance",SECOND,Collections.singletonList(obs(.2f,0,new AppearanceDescriptor(null))),false),0);
        update("no-appearance",1_200_000_000L,Collections.emptyList(),false);
        long next=entity(update("no-appearance",1_400_000_000L,Collections.singletonList(obs(.2f,0,new AppearanceDescriptor(null))),false),0);
        assertNotEquals(old,next);assertEquals("",hud.vehicleLabelForTesting(old));
    }
    @Test public void conflictingAppearanceDoesNotTransferTheOldIdentity()throws Exception {
        setup(true);long old=entity(update("replacement",SECOND,Collections.singletonList(obs(.2f,0,true)),false),0);
        update("replacement",1_200_000_000L,Collections.emptyList(),false);
        assertNotEquals(old,entity(update("replacement",1_400_000_000L,Collections.singletonList(obs(.2f,0,false)),false),0));
    }
    @Test public void ambiguousDormantMatchesNeverAssignOneIdentityTwice()throws Exception {
        setup(true);update("ambiguous",SECOND,Arrays.asList(obs(.2f,0,true),obs(.2f,1,true)),false);manager.predict(2_100_000_001L);
        List<VehicleTrackManager.Snapshot> next=update("ambiguous",2_200_000_000L,Arrays.asList(obs(.21f,0,true),obs(.21f,1,true)),false);
        assertTrue(entity(next,0)>2);assertTrue(entity(next,1)>2);assertNotEquals(entity(next,0),entity(next,1));
    }
    @Test public void recoveryDeadlineIsInclusiveAndPredictionDoesNotExtendIt()throws Exception {
        for(long offset:new long[]{0,1}){
            setup(true);long old=entity(update("deadline-"+offset,SECOND,Collections.singletonList(obs(.2f,0,true)),false),0);
            manager.predict(2_100_000_001L);manager.setTrackTtlNanos(4*SECOND);manager.predict(5_900_000_000L);
            long next=entity(update("deadline-"+offset,6*SECOND+offset,Collections.singletonList(obs(.2f,0,true)),false),0);
            assertEquals(offset==0,old==next);
        }
    }
    @Test public void existingAppearanceGateIsCheckedOnBothSidesOfItsBoundary()throws Exception {
        for(float cosine:new float[]{.54f,.56f}){
            setup(true);long old=entity(update("appearance-"+cosine,SECOND,Collections.singletonList(obs(.2f,0,true)),false),0);manager.predict(2_100_000_001L);
            AppearanceDescriptor changed=appearance(cosine,(float)Math.sqrt(1-cosine*cosine));
            long next=entity(update("appearance-"+cosine,2_200_000_000L,Collections.singletonList(obs(.2f,0,changed)),false),0);
            assertEquals(cosine>.55f,old==next);
        }
    }
    @Test public void missingVehicleCannotTakeAnObservationFromAnActiveNeighbor()throws Exception {
        setup(true);List<VehicleTrackManager.Snapshot> first=update("neighbor",SECOND,Arrays.asList(obs(.15f,0,true),obs(.42f,1,true)),false);long b=entity(first,1);
        update("neighbor",1_200_000_000L,Collections.singletonList(obs(.40f,0,true)),false);
        assertEquals(b,entity(update("neighbor",1_400_000_000L,Collections.singletonList(obs(.39f,0,true)),false),0));
    }
    @Test public void separatePhotosAndIndependentSessionsNeverReclaimOldNumbers()throws Exception {
        setup(true);long old=entity(update("boundary",SECOND,Collections.singletonList(obs(.2f,0,true)),true),0);manager.resetScene();
        long next=entity(update("boundary",2*SECOND,Collections.singletonList(obs(.2f,0,true)),true),0);assertNotEquals(old,next);
        assertEquals(1,repo.size());setup(true);assertEquals(0,repo.size());assertEquals(0,manager.trackedCount());
    }
    @Test public void cameraTransformRebasesDormantGeometryWithoutRenewingMpEvidence()throws Exception {
        setup(true);long old=entity(update("pan",SECOND,Collections.singletonList(obs(.05f,0,true)),false),0);
        manager.predict(2_100_000_001L);
        manager.applyCameraMotion(FrameMotionTransform.translation(.27f,0),2_100_000_001L);
        assertEquals(SECOND,repo.get(old).lastMpNanos());
        assertEquals(old,entity(update("pan",2_200_000_000L,Collections.singletonList(obs(.32f,0,true)),false),0));
    }
    @Test public void explicitFocusDoesNotSuppressANewVehicleWithoutAppearanceProof()throws Exception {
        setup(true);long old=entity(update("focus-no-proof",SECOND,Collections.singletonList(obs(.2f,0,new AppearanceDescriptor(null))),false),0);
        manager.setPriorityTarget(old,SECOND);update("focus-no-proof",1_200_000_000L,Collections.emptyList(),false);
        assertNotEquals(old,entity(update("focus-no-proof",1_400_000_000L,Collections.singletonList(obs(.2f,0,new AppearanceDescriptor(null))),false),0));
    }
    @Test public void previewGlobalAndLocalFallbackCannotRedrawAVehicleMissedByFreshMp()throws Exception {
        setup(true);long a=entity(update("preview-miss",SECOND,Collections.singletonList(obs(.2f,0,true)),false),0);
        List<OverlayItem> oldMeasurements=latestItems;
        update("preview-miss",1_200_000_000L,Collections.emptyList(),false);
        List<OverlayItem> projected=new EntityOverlayMotionProjector().project(oldMeasurements,Collections.emptyList(),oldMeasurements,
                a,0,latestFrame,SECOND,FrameMotionTransform.translation(.01f,0),FrameMotionTransform.translation(.01f,0));
        assertFalse("Fixture reproduces existing preview fallback",projected.isEmpty());
        List<OverlayItem> filtered=VehicleMeasurementOverlay.retainObservedVehicles(projected,latestFrame);
        assertTrue(filtered.isEmpty());hud.setItems(filtered,1000,1000);org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals("",hud.vehicleLabelForTesting(a));assertNotNull(repo.get(a));
        assertEquals(a,entity(update("preview-miss",1_400_000_000L,Collections.singletonList(obs(.21f,0,true)),false),0));
    }
    @Test public void actualMainPreviewFilterIsRestrictedToOrdinaryDynamicMode(){
        com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
        android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("identity-main-test",0);
        org.robolectric.util.ReflectionHelpers.setField(main,"uiPreferences",prefs);
        PreviewTrackingFrame frame=org.robolectric.util.ReflectionHelpers.callConstructor(PreviewTrackingFrame.class,
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(List.class,Collections.emptyList()),
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(long.class,SECOND));
        org.robolectric.util.ReflectionHelpers.setField(frame,"overlayItems",Collections.singletonList(
                new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(.1f,.2f,.3f,.5f),Collections.emptyList(),"synthetic prediction",1,true)));
        for(boolean research:new boolean[]{false,true})for(com.example.alpr_v1.continuity.SceneHandlingMode mode:com.example.alpr_v1.continuity.SceneHandlingMode.values()){
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,mode.wireName()).commit();
            org.robolectric.util.ReflectionHelpers.setField(main,"experimentModeEnabled",research);
            List<OverlayItem> actual=org.robolectric.util.ReflectionHelpers.callInstanceMethod(main,"trackedVehicleOverlayItems",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(PreviewTrackingFrame.class,frame));
            boolean changed=!research&&mode==com.example.alpr_v1.continuity.SceneHandlingMode.DYNAMIC_CONTINUITY;
            assertEquals(changed?0:1,actual.size());
            if(!changed)assertFalse("Retain the original static/research presentation contract",actual.get(0).carriedPrediction);
        }
    }
}
