package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
public class DynamicBestReadingUiTest {
    private static final long S=1_000_000_000L;
    private static ContinuityStamp source(int frame) {
        return new ContinuityStamp(0,0,0,frame+1,S+frame*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);
    }
    private static VehicleTrackingCoordinator coordinator() {
        VehicleTrackingCoordinator c=new VehicleTrackingCoordinator();c.setNormalDynamicIdentity(true,S);
        c.updateFromMp(1,source(0).sourceFrameStamp(),S,Collections.singletonList(new VehicleTrackManager.Observation(
                new NormalizedBounds(.1f,.1f,.9f,.9f),.9f,new AppearanceDescriptor(new float[]{1,0}),0)),Collections.emptySet(),S);
        return c;
    }
    private static LivePresentationController hud(View root,Context context) {
        return new LivePresentationController(root.findViewById(R.id.live_status_strip),root.findViewById(R.id.live_status_dot),
                root.findViewById(R.id.live_status),root.findViewById(R.id.live_status_secondary),root.findViewById(R.id.live_diagnostics_toggle),
                root.findViewById(R.id.live_event),root.findViewById(R.id.live_hud_row),new TextView(context),root.findViewById(R.id.recognition_hint),
                root.findViewById(R.id.confirmed_result_tray),root.findViewById(R.id.confirmed_result_text),
                root.findViewById(R.id.confirmed_result_confidence),root.findViewById(R.id.confirmed_result_meta));
    }
    private static void reading(VehicleTrackingCoordinator c,int frame,String text,float confidence,boolean stable) {
        c.repository().updateDynamicRegistration(1,new PlateTextConsensus(text,confidence,frame+1,stable),
                source(frame).sourceTimestampNanos,true,RegistrationConsensusSource.FRESH_MZ);
    }
    @Test public void aTenPercentReadingIsShownAndTheNextThreePercentReadingCannotReplaceIt() {
        VehicleTrackingCoordinator c=coordinator();
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        overlay.setItems(Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                new android.graphics.RectF(.1f,.1f,.9f,.9f),Collections.emptyList(),"",1,false)),720,1000);
        View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);LivePresentationController hud=hud(root,context);
        try {
            for(int frame=1;frame<=2;frame++) {
                reading(c,frame,"AB12345",frame==1?.1f:.03f,true);
                DynamicVehicleDisplaySnapshot s=c.displaySnapshot(source(frame));
                overlay.setVehicleIdentityPresentation(s.identities,s.plateOwners);overlay.setDynamicVehicleSnapshot(s,0);
                String label=overlay.vehicleLabelForTesting(1);
                assertTrue(label,label.contains("P1"));assertTrue(label,label.contains("AB12345"));
                assertFalse(label,label.contains("Weryfikacja"));assertTrue(label,label.contains("10%"));
                hud.renderDynamicResult(s,1);
                assertEquals(View.VISIBLE,root.findViewById(R.id.confirmed_result_tray).getVisibility());
                assertEquals("AB12345",((TextView)root.findViewById(R.id.confirmed_result_text)).getText().toString());
                assertEquals("Numer odczytany",((TextView)root.findViewById(R.id.live_status)).getText().toString());
                assertEquals("AB12345",c.repository().get(1).registration().text);
            }
        } finally {hud.dispose();}
    }
    @Test public void verificationOnlyAppearsDuringAnActualMzJobWhileNoReadingExists() {
        VehicleTrackingCoordinator c=coordinator();
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        overlay.setItems(Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                new android.graphics.RectF(.1f,.1f,.9f,.9f),Collections.emptyList(),"",1,false)),720,1000);
        java.util.function.Supplier<String> label=()->{
            DynamicVehicleDisplaySnapshot s=c.displaySnapshot(source(1));
            overlay.setVehicleIdentityPresentation(s.identities,s.plateOwners);overlay.setDynamicVehicleSnapshot(s,0);
            return overlay.vehicleLabelForTesting(1);
        };
        assertEquals("P1",label.get());
        long first=c.beginDynamicMzAttempt(c.identity().stamp(1,1));
        assertTrue(label.get(),label.get().contains("Weryfikacja"));
        long second=c.beginDynamicMzAttempt(c.identity().stamp(1,1));
        c.endDynamicMzAttempt(first);
        assertTrue("A late completion must not clear the new job",label.get().contains("Weryfikacja"));
        c.endDynamicMzAttempt(second);
        c.repository().updateDynamicRegistration(1,PlateTextConsensus.EMPTY,source(1).sourceTimestampNanos,
                true,RegistrationConsensusSource.FRESH_MZ);
        assertEquals(1,c.repository().get(1).mzAttempts());assertEquals("P1",label.get());
        long third=c.beginDynamicMzAttempt(c.identity().stamp(1,1));
        reading(c,2,"AB12345",.03f,false);
        assertTrue(label.get(),label.get().contains("AB12345"));
        assertTrue(label.get(),label.get().contains("3%"));assertFalse(label.get().contains("Weryfikacja"));
        c.endDynamicMzAttempt(third);
        assertTrue(label.get().contains("AB12345"));
    }
    @Test public void aWeakerCurrentAggregateCannotReplaceTheBestConfirmedReadingInEitherView() {
        VehicleTrackingCoordinator c=coordinator();
        Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_ALPR_v1);
        DetectionOverlayView overlay=new DetectionOverlayView(context,null);overlay.layout(0,0,720,1000);
        overlay.setItems(Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                new android.graphics.RectF(.1f,.1f,.9f,.9f),Collections.emptyList(),"",1,false)),720,1000);
        View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);LivePresentationController hud=hud(root,context);
        String[] incoming={"AB12345","AB1234S","AB12346"};float[] scores={.8f,.03f,.92f};
        try {
            for(int i=0;i<3;i++) {
                reading(c,i+1,incoming[i],scores[i],i!=1);
                DynamicVehicleDisplaySnapshot s=c.displaySnapshot(source(i+1));
                overlay.setVehicleIdentityPresentation(s.identities,s.plateOwners);overlay.setDynamicVehicleSnapshot(s,0);
                String expected=i<2?"AB12345":"AB12346";
                assertTrue(overlay.vehicleLabelForTesting(1),overlay.vehicleLabelForTesting(1).contains(expected));
                hud.renderDynamicResult(s,1);
                assertEquals(expected,((TextView)root.findViewById(R.id.confirmed_result_text)).getText().toString());
                assertEquals(incoming[i],c.repository().get(1).registration().text);
                assertEquals(i+1,c.repository().get(1).mzAttempts());
                assertEquals(1,c.identity().ownerNumber(1));
            }
        } finally {hud.dispose();}
    }
}
