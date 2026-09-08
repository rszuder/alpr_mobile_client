package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.ZoomState;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.camera.AutoZoomController;
import com.example.alpr_v1.camera.CameraController;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class AutoZoomFramingInstrumentedTest {
    @Test public void staticAndDynamicRequestsKeepPeripheralPlateVisibleAndCenteredPlateAtFullZoom() {
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences preferences=context.getSharedPreferences("alpr_ui",Context.MODE_PRIVATE);
        String previousMode=preferences.getString(SettingsActivity.KEY_SCENE_HANDLING_MODE,null);
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    CameraController camera=(CameraController)field(activity,"cameraController").get(activity);
                    Field bound=field(camera,"camera"); Object previousCamera=bound.get(camera);
                    bound.set(camera,zoomCapableCamera());
                    AutoZoomController zoom=(AutoZoomController)field(activity,"autoZoomController").get(activity);
                    MetricsCollector metrics=(MetricsCollector)field(activity,"metricsCollector").get(activity);
                    DetectionOverlayView overlay=(DetectionOverlayView)field(activity,"overlayView").get(activity);
                    overlay.setPreviewSourceSize(720,1280);
                    field(activity,"latestOverlaySourceWidth").setInt(activity,720);
                    field(activity,"latestOverlaySourceHeight").setInt(activity,1280);
                    Method request=MainActivity.class.getDeclaredMethod("requestAutoZoom",AutoZoomController.Decision.class);
                    request.setAccessible(true);
                    Method cancel=MainActivity.class.getDeclaredMethod("cancelPendingAutoZoomStart"); cancel.setAccessible(true);
                    try {
                        for (SceneHandlingMode mode:SceneHandlingMode.values()) {
                            preferences.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,mode.wireName()).commit();
                            com.example.alpr_v1.pipeline.AlprPipeline pipeline=
                                    (com.example.alpr_v1.pipeline.AlprPipeline)field(activity,"pipeline").get(activity);
                            pipeline.setSceneHandlingMode(mode);
                            for (boolean peripheral:new boolean[]{true,false}) {
                                RectF plate=peripheral ? new RectF(115f/720f,.46f,172f/720f,.54f)
                                        : new RectF(.42f,.46f,.58f,.54f);
                                field(activity,"latestPipelinePlateItems").set(activity,Collections.singletonList(
                                        new OverlayItem(OverlayItem.Kind.PLATE,plate,Collections.emptyList(),"",41L,false)));
                                if (mode==SceneHandlingMode.STRICT_SCENE_BOUNDARY) {
                                    com.example.alpr_v1.acquisition.StaticSceneCycle cycle=
                                            (com.example.alpr_v1.acquisition.StaticSceneCycle)field(pipeline,"staticCycle").get(pipeline);
                                    cycle.reset(1); cycle.observe(baselineObservation(plate)); cycle.finishBaseline();
                                    assertNotNull(cycle.nextRefinement(true));
                                    field(activity,"latestPipelinePlateItems").set(activity,Collections.emptyList());
                                    overlay.setItems(Collections.emptyList(),720,1280);
                                }
                                zoom.setEnabled(true); zoom.resetSession(); metrics.startMeasurementSession();
                                AutoZoomController.Sample sample=new AutoZoomController.Sample(41L,plate.centerX(),plate.centerY(),
                                        plate.width(),.3,false,2,true,true,true,"COBRA",true);
                                AutoZoomController.Decision decision=mode==SceneHandlingMode.STRICT_SCENE_BOUNDARY
                                        ? zoom.requestStaticRefinement(sample) : zoom.requestRefinement(sample);
                                request.invoke(activity,decision);
                                assertEquals("AZ restores the measured plate before camera animation",1,
                                        overlay.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
                                cancel.invoke(activity);
                                JSONObject event=new JSONObject(metrics.createEventsJsonl().trim());
                                assertEquals("auto_zoom_started",event.getString("event_type"));
                                double ratio=event.getDouble("zoom_ratio");
                                assertEquals(mode.name(),peripheral ? 1.322449 : 1.8,ratio,.0001);
                                assertTrue(.5+ratio*(plate.left-.5)>=.05-.0001);
                                assertTrue(.5+ratio*(plate.right-.5)<=.95+.0001);
                                // Regression: a later, narrower MT layer must not become the permanent 1x outline.
                                float zoomRatio=(float)ratio;
                                RectF zoomed=new RectF(CameraController.scaledCoordinate(plate.left,zoomRatio),
                                        CameraController.scaledCoordinate(plate.top,zoomRatio),
                                        CameraController.scaledCoordinate(plate.right-plate.width()*.18f,zoomRatio),
                                        CameraController.scaledCoordinate(plate.bottom,zoomRatio));
                                overlay.setOpticalTransformItems(Collections.singletonList(new OverlayItem(
                                        OverlayItem.Kind.PLATE,zoomed,Collections.emptyList(),"COBRA 94%",41L,false)),720,1280);
                                field(activity,"currentCameraZoomRatio").setFloat(activity,zoomRatio);
                                Method freeze=MainActivity.class.getDeclaredMethod("freezeZoomResultForReturn");freeze.setAccessible(true);
                                Method prepare=MainActivity.class.getDeclaredMethod("prepareAutoZoomReturnGeometry");prepare.setAccessible(true);
                                Method transform=MainActivity.class.getDeclaredMethod("transformMemoryOverlay",float.class);transform.setAccessible(true);
                                freeze.invoke(activity);prepare.invoke(activity);transform.invoke(activity,1f);
                                java.util.List<?> returned=(java.util.List<?>)field(activity,"autoZoomBaseMemoryOverlayItems").get(activity);
                                OverlayItem restored=null;
                                for(Object value:returned)if(((OverlayItem)value).kind==OverlayItem.Kind.PLATE)restored=(OverlayItem)value;
                                assertNotNull(restored);
                                assertEquals(mode.name(),plate.left,restored.normalizedBounds.left,.00001f);
                                assertEquals(mode.name(),plate.right,restored.normalizedBounds.right,.00001f);
                                assertEquals("COBRA 94%",restored.label);
                                field(activity,"currentCameraZoomRatio").setFloat(activity,1f);
                                metrics.finishMeasurementSession();
                            }
                        }
                    } finally {
                        cancel.invoke(activity); bound.set(camera,previousCamera);
                    }
                } catch (Exception error) { throw new AssertionError(error); }
            });
        } finally {
            preferences.edit().putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,previousMode).commit();
        }
    }

    private static Camera zoomCapableCamera() {
        ZoomState state=new ZoomState() {
            public float getZoomRatio() { return 1f; }
            public float getMaxZoomRatio() { return 8f; }
            public float getMinZoomRatio() { return 1f; }
            public float getLinearZoom() { return 0f; }
        };
        MutableLiveData<ZoomState> zoom=new MutableLiveData<>(state);
        CameraInfo info=(CameraInfo)Proxy.newProxyInstance(CameraInfo.class.getClassLoader(),new Class<?>[]{CameraInfo.class},
                (proxy,method,args)-> { if (method.getName().equals("getZoomState")) return zoom;
                    throw new AssertionError("Unexpected camera info call: "+method.getName()); });
        return (Camera)Proxy.newProxyInstance(Camera.class.getClassLoader(),new Class<?>[]{Camera.class},
                (proxy,method,args)-> { if (method.getName().equals("getCameraInfo")) return info;
                    throw new AssertionError("Unexpected camera call: "+method.getName()); });
    }
    private static com.example.alpr_v1.pipeline.PlateObservation baselineObservation(RectF plate) {
        com.example.alpr_v1.pipeline.PlateGeometry geometry=com.example.alpr_v1.pipeline.PlateGeometry.from(720,1280,
                new com.example.alpr_v1.vision.Detection(0,.9f,plate.left*720,plate.top*1280,
                        plate.right*720,plate.bottom*1280,Collections.emptyList()),Collections.emptyList());
        return new com.example.alpr_v1.pipeline.PlateObservation(41L,
                com.example.alpr_v1.pipeline.PlateVehicleAssociation.direct(7L,7L,"test"),
                com.example.alpr_v1.pipeline.MtWorkKind.VEHICLE_ROI,
                com.example.alpr_v1.pipeline.MtReason.SCAN_NEXT_CANDIDATE,
                1L,null,"COBRA",.9,.3,false,2,Collections.emptyList(),1L,1L,.5f,null,null,geometry,
                true,true,"COBRA",false,1,"single_row",Collections.emptyList(),"","COBRA",
                new com.example.alpr_v1.continuity.ContinuityStamp(1,0,0,1));
    }
    private static Field field(Object object,String name) throws ReflectiveOperationException {
        Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
}
