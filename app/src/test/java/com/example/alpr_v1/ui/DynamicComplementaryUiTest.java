package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.acquisition.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.tracking.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Native Android layout, real touch dispatch, controller and HUD; synthetic vehicle geometry only. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class DynamicComplementaryUiTest {
    static class Fixture {
        final View root;
        final DetectionOverlayView hud;
        final ScanAcquisitionController scan=new ScanAcquisitionController();
        final VehicleTrackManager manager=new VehicleTrackManager(new VehicleEntityRepository());
        final List<OverlayItem> items=new ArrayList<>();
        final long id;
        Fixture(float font) {
            Application app=RuntimeEnvironment.getApplication();
            Configuration configuration=new Configuration(app.getResources().getConfiguration());configuration.fontScale=font;
            Context context=new ContextThemeWrapper(app.createConfigurationContext(configuration),R.style.Theme_ALPR_v1);
            root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);
            LivePresentationController.configureLayout(root);
            hud=root.findViewById(R.id.detection_overlay);hud.setDynamicVehicleMotion(false);
            manager.setNormalDynamicIdentity(true,1_000_000_000L);
            List<VehicleTrackManager.Snapshot> vehicles=manager.update(Arrays.asList(
                    new VehicleTrackManager.Observation(new NormalizedBounds(.08f,.12f,.45f,.82f),.9f,new AppearanceDescriptor(new float[]{1,0}),0),
                    new VehicleTrackManager.Observation(new NormalizedBounds(.55f,.22f,.93f,.88f),.9f,new AppearanceDescriptor(new float[]{0,1}),1)),1_000_000_000L);
            id=vehicles.get(0).entityId;
            for(VehicleTrackManager.Snapshot car:vehicles)items.add(new OverlayItem(OverlayItem.Kind.VEHICLE,
                    new RectF(car.bounds.left,car.bounds.top,car.bounds.right,car.bounds.bottom),Collections.emptyList(),"",car.entityId,false));
            List<PointF> quad=Arrays.asList(new PointF(.18f,.63f),new PointF(.36f,.63f),new PointF(.36f,.69f),new PointF(.18f,.69f));
            items.add(new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.18f,.63f,.36f,.69f),quad,"T",101,false));
            hud.setPlateEntityResolver(track->track==101?id:0);
            hud.setVehicleIdentityPresentation(manager.identity().presentation(),Collections.singletonMap(101L,id));
            hud.setItems(items,1000,1000);
            scan.setNormalDynamicPolicy(true);scan.startLiveRun(1,0);
            hud.setVehicleTapListener(entity->{scan.pickVehicle(entity,manager.identity().presentation().get(entity).number,2_000_000_000L);
                hud.setTargetFocus(scan.targetFocus().entityId,false);
                ((TextView)root.findViewById(R.id.live_status)).setText("LOCK · P1 · śledzę wybrany pojazd");});
            root.findViewById(R.id.release_target_button).setEnabled(true);
            root.findViewById(R.id.release_target_button).setOnClickListener(v->{scan.releaseForeground(false,3_000_000_000L);
                hud.setTargetFocus(0,true);});
            layout();
        }
        void layout(){root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);}
        void tap(float x,float y) {
            MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(1,20,MotionEvent.ACTION_UP,x,y,0);
            assertTrue(hud.dispatchTouchEvent(down));assertTrue(hud.dispatchTouchEvent(up));down.recycle();up.recycle();
        }
        void save(String name)throws Exception {
            String folder=System.getenv("COMPLEMENTARY_UI_ARTIFACTS");if(folder==null)return;
            layout();File directory=new File(folder);directory.mkdirs();
            Bitmap bitmap=Bitmap.createBitmap(720,1600,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
            try(FileOutputStream stream=new FileOutputStream(new File(directory,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
            bitmap.recycle();
        }
    }
    void lockAndRelease(float font,boolean tapBadge)throws Exception {
        Fixture f=new Fixture(font);f.save("synthetic-before-font"+font);
        RectF target=tapBadge?f.hud.vehicleBadgeForTesting(f.id):f.hud.vehicleBoundsForTesting(f.id);
        assertNotNull(target);f.tap(target.centerX(),target.centerY());
        assertEquals(f.id,f.scan.lockedEntityId());assertEquals(1,f.hud.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
        assertTrue(f.hud.vehicleLabelForTesting(f.id).startsWith("LOCK · P1"));
        f.save("synthetic-locked-font"+font);
        assertTrue(f.root.findViewById(R.id.release_target_button).performClick());
        assertEquals(0,f.scan.lockedEntityId());assertTrue(f.scan.targetFocus().awaitingFreshScanAnchor);
        assertEquals(0,f.hud.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
        f.hud.setTargetFocus(0,false);f.hud.setItems(f.items,1000,1000);
        assertEquals(2,f.hud.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
        f.save("synthetic-released-font"+font);
    }
    @Test public void oneBodyTapLocksAt100PercentAndReleaseButtonRestoresThePool()throws Exception {lockAndRelease(1,false);}
    @Test public void oneBadgeTapLocksAt150PercentAndReleaseButtonRestoresThePool()throws Exception {lockAndRelease(1.5f,true);}
    @Test public void plateTapCanSelectItsKnownVehicleBeforeAnyOcr() {
        Fixture f=new Fixture(1);PointF point=f.hud.normalizedToViewPoint(.27f,.66f);f.tap(point.x,point.y);
        assertEquals(f.id,f.scan.lockedEntityId());
    }
    @Test public void plateOnlyTapIsAvailableOnlyWithOrdinaryDynamicOwnership() {
        Fixture f=new Fixture(1);
        f.hud.setVehicleIdentityPresentation(null,null);
        f.hud.setItems(Collections.singletonList(f.items.get(2)),1000,1000);f.layout();
        PointF point=f.hud.normalizedToViewPoint(.27f,.66f);
        MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,point.x,point.y,0);
        assertFalse(f.hud.dispatchTouchEvent(down));down.recycle();assertEquals(0,f.scan.lockedEntityId());
        f.hud.setVehicleIdentityPresentation(f.manager.identity().presentation(),Collections.singletonMap(101L,f.id));
        f.tap(point.x,point.y);assertEquals(f.id,f.scan.lockedEntityId());
    }
    @Test public void dynamicBadgeAcceptsReducedConfidenceAndACorrectedConsensus() {
        Fixture f=new Fixture(1);
        f.hud.setVehicleEntityStates(Collections.singleton(f.id),Collections.emptySet(),Collections.singletonMap(f.id,
                new EntityRecognitionSnapshot(f.id,101,"AB12345",.95,true,4,1000,1)));
        f.hud.finishPlateAbsorptionForTesting();
        f.hud.setVehicleEntityStates(Collections.singleton(f.id),Collections.emptySet(),Collections.singletonMap(f.id,
                new EntityRecognitionSnapshot(f.id,101,"AB1234S",.55,false,5,2000,1)));
        assertTrue(f.hud.vehicleLabelForTesting(f.id).contains("AB1234S"));
        assertFalse(f.hud.confirmedVehicleForTesting(f.id));
    }
    @Test public void currentLiveResultCanLoseConfirmationAndConfidenceAndCannotBeRolledBack() {
        Fixture f=new Fixture(1);
        LivePresentationController live=new LivePresentationController(f.root.findViewById(R.id.live_status_strip),f.root.findViewById(R.id.live_status_dot),
                f.root.findViewById(R.id.live_status),f.root.findViewById(R.id.live_status_secondary),f.root.findViewById(R.id.live_diagnostics_toggle),
                f.root.findViewById(R.id.live_event),f.root.findViewById(R.id.live_hud_row),f.root.findViewById(R.id.live_hud),
                f.root.findViewById(R.id.recognition_hint),f.root.findViewById(R.id.confirmed_result_tray),f.root.findViewById(R.id.confirmed_result_text),
                f.root.findViewById(R.id.confirmed_result_confidence),f.root.findViewById(R.id.confirmed_result_meta));
        try {
            live.showCurrentResult(101,"AB12345",.95,true,4,1000,1);
            live.showCurrentResult(101,"AB12345",.55,false,5,2000,1);
            String confidence=((TextView)f.root.findViewById(R.id.confirmed_result_confidence)).getText().toString();
            assertTrue(confidence,confidence.contains("55%"));assertTrue(confidence,confidence.contains("odczyt"));
            live.showCurrentResult(101,"AB12345",.95,true,4,1000,1);
            assertEquals(confidence,((TextView)f.root.findViewById(R.id.confirmed_result_confidence)).getText().toString());
        }finally{live.dispose();}
    }
    @Test public void lostLockAndTheReleaseInstructionFitAt150Percent()throws Exception {
        Fixture f=new Fixture(1.5f);
        LivePresentationController live=new LivePresentationController(f.root.findViewById(R.id.live_status_strip),f.root.findViewById(R.id.live_status_dot),
                f.root.findViewById(R.id.live_status),f.root.findViewById(R.id.live_status_secondary),f.root.findViewById(R.id.live_diagnostics_toggle),
                f.root.findViewById(R.id.live_event),f.root.findViewById(R.id.live_hud_row),f.root.findViewById(R.id.live_hud),
                f.root.findViewById(R.id.recognition_hint),f.root.findViewById(R.id.confirmed_result_tray),f.root.findViewById(R.id.confirmed_result_text),
                f.root.findViewById(R.id.confirmed_result_confidence),f.root.findViewById(R.id.confirmed_result_meta));
        try {
            f.scan.pickVehicle(f.id,1,2_000_000_000L);
            f.scan.onManualIdentityStatus(3_000_000_000L,3_000_000_001L,3_000_000_001L);
            f.manager.identity().endRecovery(f.id,3_000_000_001L);
            f.hud.setVehicleIdentityPresentation(f.manager.identity().presentation(),Collections.singletonMap(101L,f.id));
            f.hud.setTargetFocus(f.id,f.scan.targetFocus().waitingForTarget);
            live.showUserStatus(LivePresentationController.State.RECOVERING,f.root.getResources().getString(R.string.dynamic_lock_target_unavailable),
                    f.root.getResources().getString(R.string.dynamic_lock_release_hint));
            f.save("synthetic-lost-lock-font1.5");
            assertTrue(f.scan.targetFocus().waitingForTarget);assertEquals(0,f.hud.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
            assertEquals(0,f.hud.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
            for(int id:new int[]{R.id.live_status,R.id.live_status_secondary}) {
                TextView text=f.root.findViewById(id);assertNotNull(text.getLayout());
                for(int line=0;line<text.getLayout().getLineCount();line++)assertEquals(text.getText().toString(),0,text.getLayout().getEllipsisCount(line));
            }
            assertTrue(f.root.findViewById(R.id.release_target_button).performClick());assertEquals(0,f.scan.lockedEntityId());
        }finally{live.dispose();}
    }
    @Test public void actualMainWaitingPresentationDropsCachedGeometryWhileSelectionStaysLocked() {
        Fixture f=new Fixture(1);
        f.scan.pickVehicle(f.id,1,2_000_000_000L);
        f.scan.onManualIdentityStatus(3_000_000_000L,3_000_000_001L,3_000_000_001L);
        com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
        List<OverlayItem> filtered=org.robolectric.util.ReflectionHelpers.callInstanceMethod(main,"focusPresentationItems",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(List.class,f.items),
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(TargetFocusSnapshot.class,f.scan.targetFocus()));
        assertTrue(filtered.isEmpty());assertEquals(f.id,f.scan.lockedEntityId());assertTrue(f.scan.targetFocus().waitingForTarget);
    }
}
