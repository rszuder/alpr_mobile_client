package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;
import com.example.alpr_v1.capture.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class DynamicGalleryIdentityTest {
    final RecognitionHistoryStore history=new RecognitionHistoryStore();
    final Bitmap image=Bitmap.createBitmap(16,4,Bitmap.Config.ARGB_8888);
    final VehicleTrackManager manager=new VehicleTrackManager(new VehicleEntityRepository());
    @Before public void setup(){manager.setNormalDynamicIdentity(true,1_000_000_000L);}
    @After public void close(){history.clear();image.recycle();}
    DynamicVehicleIdentity.Stamp owner(VehicleTrackManager tracker) {
        tracker.setNormalDynamicIdentity(true,1_000_000_000L);
        VehicleTrackManager.Snapshot snapshot=tracker.update(Collections.singletonList(new VehicleTrackManager.Observation(
                new NormalizedBounds(.1f,.1f,.9f,.9f),.9f,new AppearanceDescriptor(new float[]{1,0}),0)),1_000_000_000L).get(0);
        return tracker.identity().stamp(snapshot.entityId,snapshot.vehicleTrackId);
    }
    PlateObservation observation(DynamicVehicleIdentity.Stamp owner,long frame,String consensus,String raw,double confidence) {
        List<PlateCharacter> characters=new ArrayList<>();
        for(int i=0;i<raw.length();i++)characters.add(new PlateCharacter(raw.substring(i,i+1),frame==1?.9:.7,i/(float)raw.length(),0,(i+1f)/raw.length(),1));
        return new PlateObservation(10,PlateVehicleAssociation.directValidated(owner.entityId,owner.vehicleTrackId,1,"fixture"),
                MtWorkKind.VEHICLE_ROI,MtReason.UNKNOWN,frame,image,consensus,.9,confidence,false,(int)frame,
                characters,1000+frame,1000+frame,.8f,null,null,null,true,true,raw,raw.equals(consensus),1,"single",
                Collections.emptyList(),"",consensus,new ContinuityStamp(0,0,0,frame,frame*1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR))
                .stampIdentityOwner(owner);
    }
    @Test public void oneVehicleHasOnePnCardWithBestConsensusAndSeparateRawEvidence() {
        DynamicVehicleIdentity.Stamp owner=owner(manager);
        history.upsertObservation(observation(owner,1,"AB12345","AB1234S",.8),null,true,"test");
        PlateObservation corrected=observation(owner,2,"AB12345","AB12345",.5);
        history.upsertObservation(corrected,null,true,"test");history.upsertObservation(corrected,null,true,"duplicate");
        assertEquals(1,history.size());RecognitionHistoryItem item=history.newestFirst().get(0);
        assertEquals("P1 · AB12345",item.displayText());assertEquals(.8,item.displayConfidence(),0);
        assertEquals("AB1234S",item.text);assertEquals(.9,item.confidence,1e-8);
        assertEquals(2,item.observations);assertEquals("AB1234S",item.observationRecords().get(0).rawPrediction);
        try(RecognitionHistoryItem copy=item.snapshot()){assertEquals(item.displayText(),copy.displayText());assertEquals(item.displayConfidence(),copy.displayConfidence(),0);}
    }
    @Test public void independentRunsCannotReuseAFormerVehiclesGalleryCard() {
        DynamicVehicleIdentity.Stamp first=owner(manager);
        DynamicVehicleIdentity.Stamp second=owner(new VehicleTrackManager(new VehicleEntityRepository()));
        assertNotEquals(first.analysisRunId,second.analysisRunId);
        history.upsertObservation(observation(first,1,"AB12345","AB12345",.8),null,true,"test");
        history.upsertObservation(observation(second,1,"AB12345","AB12345",.8),null,true,"test");
        assertEquals(2,history.size());
    }
    @Test public void oneOwnerKeepsTheBestAggregateWhileEveryFreshAttemptStillContributesEvidence() {
        DynamicVehicleIdentity.Stamp owner=owner(manager);
        String[] texts={"AB12345","AB1234S","AB12346","AB1234S","AB12347"};
        double[] scores={.1,.03,.7,.6,.85};
        for(int i=0;i<texts.length;i++) {
            image.eraseColor(android.graphics.Color.rgb(10+i*30,20,30));
            history.upsertObservation(observation(owner,i+1,texts[i],texts[i],scores[i]),null,true,"test");
            RecognitionHistoryItem item=history.newestFirst().get(0);
            assertEquals(1,history.size());assertEquals(i+1,item.observations);
            assertEquals(i<2?.1:i<4?.7:.85,item.displayConfidence(),1e-8);
            assertEquals("P1 · "+(i<2?texts[0]:i<4?texts[2]:texts[4]),item.displayText());
            int best=i<2?0:i<4?2:4;
            assertEquals(android.graphics.Color.rgb(10+best*30,20,30),item.previewBitmap.getPixel(0,0));
            assertEquals(best+1,item.bestConsensusObservation().frameId);
        }
    }
    @Test public void dynamicGalleryDetailsShowOneBestReadingInsteadOfAListOfWeakAttempts() {
        DynamicVehicleIdentity.Stamp owner=owner(manager);
        history.upsertObservation(observation(owner,1,"AB12345","AB12345",.8),null,true,"test");
        history.upsertObservation(observation(owner,2,"AB1234S","AB1234S",.03),null,true,"test");
        android.content.Context context=new android.view.ContextThemeWrapper(org.robolectric.RuntimeEnvironment.getApplication(),
                com.example.alpr_v1.R.style.Theme_ALPR_v1);
        com.example.alpr_v1.ui.RecognitionEntityPager pager=new com.example.alpr_v1.ui.RecognitionEntityPager(context,null);
        pager.setItem(history.newestFirst().get(0));
        pager.measure(android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1000,android.view.View.MeasureSpec.EXACTLY));
        pager.layout(0,0,720,1000);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        String rendered=texts(pager);
        assertTrue(rendered,rendered.contains("AB12345"));assertFalse(rendered,rendered.contains("AB1234S"));
        assertFalse(rendered,rendered.contains("OBSERWACJA 2"));assertTrue(rendered,rendered.contains("P1"));
        assertEquals(2,history.newestFirst().get(0).observationRecords().size());
    }
    private static String texts(android.view.View view) {
        StringBuilder result=new StringBuilder();
        if(view.getVisibility()!=android.view.View.VISIBLE)return "";
        if(view instanceof android.widget.TextView)result.append(((android.widget.TextView)view).getText()).append('\n');
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)
            result.append(texts(((android.view.ViewGroup)view).getChildAt(i)));
        return result.toString();
    }
    @Test public void anExplicitVerifiedPromotionMergesCardsWithoutRewritingRawOwnerIds() {
        DynamicVehicleIdentity.Stamp first=owner(manager);
        // A second independently measured vehicle starts a separate card before a verified alias arrives.
        java.util.List<VehicleTrackManager.Snapshot> tracks=manager.update(Arrays.asList(
                new VehicleTrackManager.Observation(new NormalizedBounds(.1f,.1f,.5f,.9f),.9f,new AppearanceDescriptor(new float[]{1,0}),0),
                new VehicleTrackManager.Observation(new NormalizedBounds(.6f,.1f,.95f,.9f),.9f,new AppearanceDescriptor(new float[]{0,1}),1)),1_100_000_000L);
        VehicleTrackManager.Snapshot other=tracks.stream().filter(car->car.entityId!=first.entityId).findFirst().get();
        DynamicVehicleIdentity.Stamp second=manager.identity().stamp(other.entityId,other.vehicleTrackId);
        history.upsertObservation(observation(first,1,"AB12345","AB12345",.8),null,true,"test");
        history.upsertObservation(observation(second,2,"AB12345","AB12345",.5),null,true,"test");assertEquals(2,history.size());
        history.promoteDynamicOwners(first.analysisRunId,0,Collections.singletonMap(second.entityId,first.entityId),manager.identity().presentation());
        assertEquals(1,history.size());RecognitionHistoryItem item=history.newestFirst().get(0);assertEquals(2,item.observations);
        assertEquals(first.entityId,item.observationRecords().get(0).entityId);assertEquals(second.entityId,item.observationRecords().get(1).entityId);
    }
}
