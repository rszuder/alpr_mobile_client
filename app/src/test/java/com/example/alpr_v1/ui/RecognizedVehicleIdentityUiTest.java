package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.View;
import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual native HUD render and actual MP/entity registry. OCR inputs are explicitly synthetic. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class RecognizedVehicleIdentityUiTest {
    static final long S=1_000_000_000L;
    VehicleEntityRepository repo;VehicleTrackManager manager;DetectionOverlayView hud;
    List<VehicleTrackManager.Observation> lastMp;final List<String> timeline=new ArrayList<>();
    String scene;boolean guarded;
    void setup(boolean after,float scale,String name){
        scene=name+(after?"-after":"-before-v1-policy")+"-font"+scale;guarded=after;
        Application app=RuntimeEnvironment.getApplication();Configuration config=new Configuration(app.getResources().getConfiguration());config.fontScale=scale;
        repo=new VehicleEntityRepository();manager=new VehicleTrackManager(repo,16,S,5*S);manager.setNormalDynamicIdentity(after,S);
        hud=new DetectionOverlayView(app.createConfigurationContext(config),null);
        hud.setStationaryScene(false);hud.setDynamicVehicleMotion(false);
        hud.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));hud.layout(0,0,720,1000);
    }
    VehicleTrackManager.Observation box(float x,int index,boolean red){return new VehicleTrackManager.Observation(new NormalizedBounds(x,.2f,x+.22f,.55f),.9f,new AppearanceDescriptor(red?new float[]{1,0}:new float[]{0,1}),index);}
    long entity(List<VehicleTrackManager.Snapshot> values,int source){for(VehicleTrackManager.Snapshot v:values)if(!v.predicted&&v.sourceIndex==source)return v.entityId;throw new AssertionError("No MP source "+source);}
    List<VehicleTrackManager.Snapshot> mp(long at,VehicleTrackManager.Observation... boxes){lastMp=Arrays.asList(boxes);return manager.update(lastMp,at);}
    void identify(long id,String text,double confidence,int count,long at){
        if(guarded)repo.updateDynamicRegistration(id,new PlateTextConsensus(text,(float)confidence,count,true),at,true,RegistrationConsensusSource.FRESH_MZ);
        else repo.updateRegistration(id,new PlateTextConsensus(text,(float)confidence,count,true),at,true,RegistrationConsensusSource.FRESH_MZ);
    }
    long read(long id,String raw,long frame,long at){return manager.verifyFresh(manager.identity().stamp(id,repo.get(id).vehicleTrackId()),raw,frame,frame,at);}
    void render(String step,long at,String raw)throws Exception {
        List<VehicleCandidate> values=new ArrayList<>();Map<Long,NormalizedBounds> measured=new HashMap<>();
        for(VehicleTrackManager.Snapshot v:manager.projectAfterMeasurement(at)){
            values.add(new VehicleCandidate(v.entityId,v.vehicleTrackId,v.bounds,v.confidence,v.confidence,0,v.predicted,v.missedUpdates,v.lastMeasurementTimestampNanos,at,v.sourceIndex));
            if(!v.predicted&&v.sourceIndex>=0)measured.put(v.entityId,lastMp.get(v.sourceIndex).bounds);
        }
        hud.setVehicleIdentityPresentation(guarded?manager.identity().presentation():null,Collections.emptyMap());
        hud.setItems(VehicleMeasurementOverlay.from(new VehicleTrackingFrame(at,at,at,0,values),measured),1000,1000);
        Map<Long,EntityRecognitionSnapshot> recognized=new HashMap<>();
        for(VehicleEntity e:repo.activeEntities())if(e.registration().available())recognized.put(e.entityId(),new EntityRecognitionSnapshot(e.entityId(),100+e.entityId(),e.registration().text,e.registration().confidence,e.registration().stable,e.registration().observations,at,guarded?manager.identity().presentation().get(e.entityId()).epoch:0L));
        hud.setVehicleEntityStates(recognized.keySet(),Collections.emptySet(),recognized);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        Bitmap bitmap=Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888);hud.draw(new Canvas(bitmap));
        for(VehicleCandidate v:values)if(v.sourceIndex>=0){
            String ownRaw=step.equals("01-identified")?repo.get(v.entityId).registration().text
                    :v.sourceIndex==lastMp.size()-1?raw:"";
            timeline.add(scene+","+step+","+at+","+v.vehicleTrackId+","+v.entityId+",\""+hud.vehicleLabelForTesting(v.entityId)+"\","+ownRaw+","+manager.stats().entitiesCreated);
            RectF badge=hud.vehicleBadgeForTesting(v.entityId);
            if(badge!=null){assertTrue(badge.toString(),badge.left>=0&&badge.top>=0&&badge.right<=720&&badge.bottom<=1000);}
        }
        String folder=System.getenv("IDENTITY_UI_ARTIFACTS");if(folder!=null){File dir=new File(folder);dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,scene+"-"+step+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}}
        bitmap.recycle();
    }
    @After public void exportTimeline()throws Exception {
        String folder=System.getenv("IDENTITY_UI_ARTIFACTS");if(folder==null||timeline.isEmpty())return;
        try(PrintWriter out=new PrintWriter(new File(folder,"recognized-"+scene+".csv"),"UTF-8")){out.println("scene,step,time_ns,track,entity,hud_label,raw_ocr,vehicle_count");for(String line:timeline)out.println(line);}
    }
    void scenario(boolean sameObject,float font)throws Exception {
        setup(true,font,sameObject?"visual-reentry-with-changed-mz":"different-appearance-with-neighbor");
        List<VehicleTrackManager.Snapshot> first=mp(S,box(.1f,0,true),box(.65f,1,false));long a=entity(first,0),neighbor=entity(first,1);
        identify(a,"AB12345",.99,4,S);identify(neighbor,"WX00000",.9,3,S);render("01-identified",S,"AB12345");
        assertTrue(hud.vehicleLabelForTesting(a).startsWith("P1: AB12345"));
        manager.identity().preview(1_100_000_000L,new HashSet<>(Arrays.asList(a,neighbor)),true);
        manager.identity().preview(1_800_000_001L,Collections.singleton(neighbor),true);
        long at=1_900_000_000L;
        long current=entity(mp(at,box(.64f,0,false),box(.11f,1,sameObject)),1);
        render("02-geometry-before-mz",at,"");
        if(sameObject) {
            assertEquals(a,current);assertTrue(hud.vehicleLabelForTesting(current).startsWith("P1: AB12345"));
        } else {
            assertNotEquals(a,current);assertFalse(hud.vehicleLabelForTesting(current).contains("AB12345"));
            assertEquals("",hud.vehicleLabelForTesting(a));
        }
        String fresh=sameObject?"AB1234S":"CD9876";
        assertEquals(current,read(current,fresh,3,2*S));
        identify(current,fresh,.65,2,2*S);render("03-current-evidence",2*S,fresh);
        assertTrue(hud.vehicleLabelForTesting(current),hud.vehicleLabelForTesting(current).startsWith((sameObject?"P1: ":"P3: ")+fresh));
        assertEquals(sameObject?2:3,manager.stats().entitiesCreated);
        assertTrue(hud.vehicleLabelForTesting(neighbor).startsWith("P2: WX00000"));
    }
    @Test public void differentVehicleNeverInheritsBadgeAt100Percent()throws Exception {scenario(false,1f);}
    @Test public void differentVehicleNeverInheritsBadgeAt150Percent()throws Exception {scenario(false,1.5f);}
    @Test public void sameVehicleKeepsNumberWithChangedMzAt100Percent()throws Exception {scenario(true,1f);}
    @Test public void sameVehicleKeepsNumberWithChangedMzAt150Percent()throws Exception {scenario(true,1.5f);}
    @Test public void sameSceneEpochInvalidationRemovesCachedBadgeAndQueuedTransfer()throws Exception {
        setup(true,1f,"stale-badge");long a=entity(mp(S,box(.1f,0,true)),0);identify(a,"AB12345",.99,3,S);render("01",S,"AB12345");
        assertTrue(hud.recognizedVehicleForTesting(a));manager.identity().preview(1_100_000_000L,Collections.singleton(a),true);
        manager.identity().preview(1_700_000_001L,Collections.emptySet(),true);
        hud.setVehicleIdentityPresentation(manager.identity().presentation(),Collections.emptyMap());org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertFalse(hud.recognizedVehicleForTesting(a));assertEquals("",hud.vehicleLabelForTesting(a));
        hud.setVehicleEntityStates(Collections.singleton(a),Collections.emptySet(),Collections.singletonMap(a,new EntityRecognitionSnapshot(a,101,"AB12345",.99,true,4)));
        assertFalse(hud.recognizedVehicleForTesting(a));assertEquals("",hud.vehicleLabelForTesting(a));
    }
    @Test public void lossCancelsActualInFlightPlateAbsorptionAndHidesItsOldText()throws Exception {
        setup(true,1f,"cancel-transfer");long a=entity(mp(S,box(.1f,0,true)),0);identify(a,"AB12345",.99,3,S);
        List<PointF> corners=Arrays.asList(new PointF(.13f,.4f),new PointF(.28f,.4f),new PointF(.28f,.45f),new PointF(.13f,.45f));
        Map<Long,Long> owners=Collections.singletonMap(101L,a);
        hud.setVehicleIdentityPresentation(manager.identity().presentation(),owners);
        hud.setItems(Arrays.asList(new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(.1f,.2f,.32f,.55f),Collections.emptyList(),"MP",a,false),
                new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.13f,.4f,.28f,.45f),corners,"Tablica: AB12345",101,false)),1000,1000);
        hud.setVehicleEntityStates(Collections.singleton(a),Collections.emptySet(),Collections.singletonMap(a,new EntityRecognitionSnapshot(a,101,"AB12345",.99,true,3)));
        assertEquals(a,hud.plateAbsorptionEntityForTesting());
        manager.identity().preview(1_100_000_000L,Collections.singleton(a),true);manager.identity().preview(1_700_000_001L,Collections.emptySet(),true);
        hud.setVehicleIdentityPresentation(manager.identity().presentation(),owners);hud.finishPlateAbsorptionForTesting();
        assertEquals(0,hud.plateAbsorptionEntityForTesting());assertFalse(hud.recognizedVehicleForTesting(a));
        List<Object> rendered=org.robolectric.util.ReflectionHelpers.getField(hud,"renderItems");
        for(Object item:rendered){Object parts=org.robolectric.util.ReflectionHelpers.getField(item,"parts");String text=org.robolectric.util.ReflectionHelpers.getField(parts,"detection");assertFalse(text,text.contains("AB12345"));}
    }
}
