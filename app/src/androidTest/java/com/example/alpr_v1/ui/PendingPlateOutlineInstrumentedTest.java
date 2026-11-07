package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.acquisition.StaticSceneCycle;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.pipeline.MtReason;
import com.example.alpr_v1.pipeline.MtWorkKind;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.PlateVehicleAssociation;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Canvas regression; no model inference or experiment START is used. */
@RunWith(AndroidJUnit4.class)
public class PendingPlateOutlineInstrumentedTest {
    @Test public void unassignedReadingPreservesQuadBeforeAndAfterOcrAtDifferentViewports() {
        onMain(() -> {
            for (int[] viewport : new int[][]{{1000,1000},{720,1280},{1280,720}}) {
                DetectionOverlayView view = view(viewport[0], viewport[1]);
                PlateGeometry geometry = geometry(1000,1000,false);
                view.setItems(Collections.singletonList(plate(geometry)),1000,1000);
                assertStroke(view,100,225,1000,1000);
                view.animatePlateObservation(observation(geometry,0,"TEST123"));
                assertEquals(1,view.pendingPlateReadingCountForTesting());
                assertEquals(0,view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
                assertStroke(view,100,225,1000,1000);
                // IDLE republishes the fixed scene geometry without a new OCR callback.
                view.setStationaryScene(true);
                for (int i=0;i<5;i++) view.setItems(Collections.singletonList(plate(geometry)),1000,1000);
                assertStroke(view,100,225,1000,1000);
            }
        });
    }

    @Test public void pendingReadingDrawsSlopedEdgesInsteadOfItsEnvelopeRectangle() {
        onMain(() -> {
            DetectionOverlayView view = view(1000,1000);
            view.animatePlateObservation(observation(geometry(1000,1000,true),0,"TEST123"));
            Bitmap bitmap = render(view);
            try {
                assertTrue("Measured left slope must be visible",Color.alpha(bitmap.getPixel(127,220))>0);
                assertEquals("Envelope edge must not replace the quad",0,Color.alpha(bitmap.getPixel(100,220)));
            } finally { bitmap.recycle(); }
        });
    }

    @Test public void freshMissingPoseEndsThePreviouslyMeasuredOutline() {
        onMain(() -> {
            DetectionOverlayView view = view(1000,1000);
            PlateGeometry geometry = geometry(1000,1000,false);
            view.animatePlateObservation(observation(geometry,0,"TEST123"));
            Object pending = pending(view);
            long seen = android.os.SystemClock.elapsedRealtimeNanos()-5_000_000_000L;
            set(pending,"seenAtNanos",seen);
            OverlayItem boxOnly = new OverlayItem(new RectF(.1f,.204f,.3f,.206f),
                    Collections.emptyList(),"",77L,false);
            view.setItems(Collections.singletonList(boxOnly),640,480);
            assertEquals(0,view.pendingPlateReadingCountForTesting());
            assertEquals(0,view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
        });
    }

    @Test public void quadRefreshUpdatesItsSourceSizeAndOwnsMutableAndroidPoints() {
        onMain(() -> {
            DetectionOverlayView view = view(1000,1000);
            view.animatePlateObservation(observation(geometry(1000,1000,false),0,"TEST123"));
            List<PointF> points = new ArrayList<>(Arrays.asList(new PointF(.2f,.3f),new PointF(.4f,.3f),
                    new PointF(.4f,.4f),new PointF(.2f,.4f)));
            view.setItems(Collections.singletonList(new OverlayItem(
                    new RectF(.2f,.345f,.4f,.35f),points,"",77L,false)),640,480);
            PlateDisplayGeometry updated = (PlateDisplayGeometry)get(pending(view),"geometry");
            assertEquals(640,updated.sourceWidth);
            assertEquals(480,updated.sourceHeight);
            assertEquals(.1f,updated.bounds.height(),.0001f);
            points.get(0).y=.9f;
            assertEquals(.3f,updated.cornersNorm.get(0).y,.0001f);
            assertStroke(view,128,180,640,480);
        });
    }

    @Test public void effectiveCropOutlineAndMissingCornerFallbackAreKeptSeparate() {
        onMain(() -> {
            DetectionOverlayView view = view(1000,1000);
            PlateGeometry raw = geometry(2000,1500,false);
            PlateGeometry effective = geometry(1000,1000,false);
            view.animatePlateObservation(observation(raw.withAutoZoomCropGeometry(effective,23L),0,"TEST123"));
            assertStroke(view,100,225,1000,1000);
            view.hardResetForNewScene(new ContinuityStamp(2,2,0,1));
            assertEquals(0,view.pendingPlateReadingCountForTesting());
            Bitmap empty = render(view);
            try { assertEquals(0,Color.alpha(empty.getPixel(100,225))); }
            finally { empty.recycle(); }
            PlateGeometry legacy = PlateGeometry.from(1000,1000,
                    new Detection(0,.9f,100,204,300,206,Collections.emptyList()),Collections.emptyList());
            view.animatePlateObservation(observation(legacy,0,""));
            assertEquals(1,view.pendingPlateReadingCountForTesting());
            PlateDisplayGeometry fallback = (PlateDisplayGeometry)get(pending(view),"geometry");
            assertFalse(fallback.hasQuad());
            assertEquals(.002f,fallback.bounds.height(),.0001f);
        });
    }

    @Test public void assigningOwnerTransfersTheLabelAndRetainsOneMeasuredPlate() {
        onMain(() -> {
            DetectionOverlayView view = view(1000,1000);
            view.setItems(Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                    new RectF(.05f,.1f,.8f,.8f),Collections.emptyList(),"P7",7L,false)),1000,1000);
            PlateGeometry geometry = geometry(1000,1000,false);
            view.animatePlateObservation(observation(geometry,0,"TEST123"));
            view.animatePlateObservation(observation(geometry,7,"TEST123"));
            assertEquals(0,view.pendingPlateReadingCountForTesting());
            assertEquals(7L,view.plateAbsorptionEntityForTesting());
            Object flight = get(view,"absorbingPlate");
            assertEquals(50f,((RectF)get(flight,"sourceBounds")).height(),.001f);
            view.finishPlateAbsorptionForTesting();
            assertTrue(view.vehicleLabelForTesting(7L).contains("TEST123"));
            assertEquals(1,view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
        });
    }

    @Test public void activityStaticIdleKeepsUnassignedOutlineAndHardBoundaryClearsIt() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("alpr_ui",Context.MODE_PRIVATE);
        boolean experiment = prefs.getBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false);
        String mode = prefs.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity");
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true)
                .putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName()).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Stop camera lifecycle callbacks; the test drives only synthetic presentation state.
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(activity -> {
                boolean started = (Boolean)get(activity,"cameraStarted");
                try {
                    AlprPipeline pipeline = (AlprPipeline)get(activity,"pipeline");
                    pipeline.setSceneHandlingMode(SceneHandlingMode.STRICT_SCENE_BOUNDARY);
                    DetectionOverlayView view = (DetectionOverlayView)get(activity,"overlayView");
                    view.layout(0,0,1000,1000);
                    view.setAnalysisViewportEnabled(false);
                    view.setTargetFocus(0L,false);
                    view.setPresentationStamp(pipeline.currentContinuityStamp(1L));
                    PlateGeometry geometry = geometry(1000,1000,false);
                    invoke(activity,"applyVisibleOverlay",new Class<?>[]{List.class,int.class,int.class},
                            Collections.singletonList(plate(geometry)),1000,1000);
                    view.animatePlateObservation(observation(geometry,0,"TEST123"));
                    assertStroke(view,100,225,1000,1000);
                    StaticSceneCycle cycle = (StaticSceneCycle)get(pipeline,"staticCycle");
                    cycle.finishBaseline();cycle.nextRefinement(false);
                    assertTrue(pipeline.isStaticIdle());
                    set(activity,"cameraStarted",true);
                    assertTrue((Boolean)invoke(activity,"renderStaticIdle",new Class<?>[0]));
                    assertStroke(view,100,225,1000,1000);
                    view.hardResetForNewScene(new ContinuityStamp(
                            pipeline.sceneContinuitySnapshot().sceneGeneration+1,2,0,2));
                    assertEquals(0,view.pendingPlateReadingCountForTesting());
                } finally { set(activity,"cameraStarted",started); }
            });
        } finally {
            prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,experiment)
                    .putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,mode).commit();
        }
    }

    private static DetectionOverlayView view(int width,int height) {
        DetectionOverlayView view = new DetectionOverlayView(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),null);
        view.layout(0,0,width,height);
        view.setStationaryScene(true);
        view.setPresentationStamp(new ContinuityStamp(1,1,0,1));
        return view;
    }

    private static void onMain(Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    private static Bitmap render(DetectionOverlayView view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void assertStroke(DetectionOverlayView view,float sourceX,float sourceY,int width,int height) {
        PointF point = OverlayViewportTransform.mapNormalizedToView(sourceX/width,sourceY/height,
                width,height,view.getWidth(),view.getHeight());
        Bitmap bitmap = render(view);
        try {
            assertTrue("Measured outline missing at "+point,
                    Color.alpha(bitmap.getPixel(Math.round(point.x),Math.round(point.y)))>0);
        } finally { bitmap.recycle(); }
    }

    private static Object pending(DetectionOverlayView view) {
        return ((Map<?,?>)get(view,"pendingPlateReadings")).get(77L);
    }

    private static PlateGeometry geometry(int width,int height,boolean sloped) {
        List<Point2> corners = Arrays.asList(new Point2(100,180),new Point2(300,180),
                new Point2(sloped?260:300,sloped?240:230),new Point2(sloped?140:100,sloped?240:230));
        return PlateGeometry.from(width,height,new Detection(0,.9f,100,204,300,206,corners),corners);
    }

    private static OverlayItem plate(PlateGeometry geometry) {
        List<PointF> points = new ArrayList<>();
        for (Point2 point : geometry.cornersNorm) points.add(new PointF(point.x,point.y));
        return new OverlayItem(new RectF(geometry.bboxLeftPx/geometry.sourceWidthPx,
                geometry.bboxTopPx/geometry.sourceHeightPx,geometry.bboxRightPx/geometry.sourceWidthPx,
                geometry.bboxBottomPx/geometry.sourceHeightPx),points,"",77L,false);
    }

    private static PlateObservation observation(PlateGeometry geometry,long entity,String text) {
        return new PlateObservation(77L,entity>0?PlateVehicleAssociation.direct(entity,entity,"test")
                :PlateVehicleAssociation.unassigned("test"),MtWorkKind.VEHICLE_ROI,MtReason.SCAN_NEXT_CANDIDATE,
                1L,null,text,.9,.8,false,1,Collections.emptyList(),1L,1L,.5f,null,null,geometry,
                true,!text.isEmpty(),text,false,1,"single_row",Collections.emptyList(),"",text);
    }

    private static Object get(Object instance,String name) {
        try { Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void set(Object instance,String name,Object value) {
        try { Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);field.set(instance,value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object invoke(Object instance,String name,Class<?>[] types,Object... args) {
        try { Method method=instance.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(instance,args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
