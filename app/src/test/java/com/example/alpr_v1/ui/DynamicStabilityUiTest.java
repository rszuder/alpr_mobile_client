package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.capture.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.tracking.*;
import java.util.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DynamicStabilityUiTest {
    @Test public void ordinaryGlobalRecoveryUsesSmallPixelsWhileSceneAndObjectTrackingKeepTheCameraResolution() {
        Application app=RuntimeEnvironment.getApplication();MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            android.content.SharedPreferences prefs=app.getSharedPreferences("global-motion-budget-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            for(boolean research:new boolean[]{false,true}) {
                ReflectionHelpers.setField(main,"experimentModeEnabled",research);
                com.example.alpr_v1.camera.LumaFrame frame=ReflectionHelpers.callConstructor(com.example.alpr_v1.camera.LumaFrame.class,
                        ReflectionHelpers.ClassParameter.from(byte[].class,movingLuma(0)),ReflectionHelpers.ClassParameter.from(int.class,240),
                        ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(long.class,1L),
                        ReflectionHelpers.ClassParameter.from(long.class,1_000_000_000L),
                        ReflectionHelpers.ClassParameter.from(SourceTimestampDomain.class,SourceTimestampDomain.CAMERAX_SENSOR));
                ReflectionHelpers.callInstanceMethod(main,"updatePreviewFrameMotion",
                        ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.camera.LumaFrame.class,frame),
                        ReflectionHelpers.ClassParameter.from(ContinuityStamp.class,pipeline.currentContinuityStamp(1_000_000_000L)),
                        ReflectionHelpers.ClassParameter.from(boolean.class,false));
                Object global=ReflectionHelpers.getField(main,"globalLumaMotionTracker"),scene=ReflectionHelpers.getField(main,"lumaSceneChangeDetector");
                assertEquals(research?240:96,(int)ReflectionHelpers.getField(global,"previousWidth"));
                assertEquals(240,(int)ReflectionHelpers.getField(scene,"previousWidth"));
                assertEquals(240,frame.width);
            }
        } finally {pipeline.close();}
    }
    @Test public void sourceHistoryIsCapturedBeforeTheBusyLumaWorkerDropsIntermediateFrames() {
        Application app=RuntimeEnvironment.getApplication();MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            android.content.SharedPreferences prefs=app.getSharedPreferences("camera-intake-history-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            ReflectionHelpers.setField(main,"cameraStarted",true);
            ((java.util.concurrent.atomic.AtomicBoolean)ReflectionHelpers.getField(main,"directLumaWorkerRunning")).set(true);
            for(int i=0;i<20;i++) {
                com.example.alpr_v1.camera.LumaFrame frame=ReflectionHelpers.callConstructor(com.example.alpr_v1.camera.LumaFrame.class,
                        ReflectionHelpers.ClassParameter.from(byte[].class,movingLuma(0)),ReflectionHelpers.ClassParameter.from(int.class,240),
                        ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(long.class,i+1L),
                        ReflectionHelpers.ClassParameter.from(long.class,1_000_000_000L+i*100_000_000L),
                        ReflectionHelpers.ClassParameter.from(SourceTimestampDomain.class,SourceTimestampDomain.CAMERAX_SENSOR));
                ReflectionHelpers.callInstanceMethod(main,"submitDirectLumaFrame",ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.camera.LumaFrame.class,frame));
            }
            PreviewLumaHistory history=ReflectionHelpers.getField(main,"ordinaryLumaHistory");
            assertTrue(history.retainedFrames()>=9);assertTrue(history.retainedBytes()<=4L*1024*1024);
            assertEquals(0L,(long)ReflectionHelpers.getField(main,"lastDirectLumaFrameNanos"));
        } finally {pipeline.close();}
    }
    @Test public void ordinaryDynamicBitmapCannotConsumeTheCameraTrackersPendingAnchor() {
        Application app=RuntimeEnvironment.getApplication();MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            android.content.SharedPreferences prefs=app.getSharedPreferences("single-pixel-owner-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            ReflectionHelpers.setField(main,"cameraStarted",true);ReflectionHelpers.setField(main,"previewSceneAnchorPending",true);
            assertFalse(ReflectionHelpers.<Boolean>callInstanceMethod(main,"needsPreviewSceneSampling"));
            PreviewPlateTracker tracker=ReflectionHelpers.getField(main,"previewPlateTracker");
            RectF box=new RectF(48f/240,64f/240,160f/240,152f/240);
            OverlayItem item=new OverlayItem(OverlayItem.Kind.PLATE,box,Arrays.asList(new PointF(box.left,box.top),
                    new PointF(box.right,box.top),new PointF(box.right,box.bottom),new PointF(box.left,box.bottom)),"",1,false);
            ContinuityStamp context=pipeline.currentContinuityStamp(1_000_000_000L);
            SourceFrameStamp source=new SourceFrameStamp(1,1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,
                    context.sceneGeneration,context.visualEpoch,context.cameraTransformGeneration);
            assertTrue(tracker.anchorLuma(Collections.singletonList(item),240,240,Collections.emptyMap(),movingLuma(0),240,240,source));
            Object pending=ReflectionHelpers.getField(tracker,"pendingControl");
            java.util.concurrent.atomic.AtomicBoolean inFlight=ReflectionHelpers.getField(main,"previewFrameInFlight");inFlight.set(true);
            Bitmap obsolete=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);
            PreviewPresentationBarrier barrier=ReflectionHelpers.getField(main,"previewPresentationBarrier");
            ReflectionHelpers.callInstanceMethod(main,"processPreviewTrackingFrame",ReflectionHelpers.ClassParameter.from(Bitmap.class,obsolete),
                    ReflectionHelpers.ClassParameter.from(long.class,((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(main,"uiSceneGeneration")).get()),
                    ReflectionHelpers.ClassParameter.from(long.class,((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(main,"uiCameraTransformGeneration")).get()),
                    ReflectionHelpers.ClassParameter.from(long.class,barrier.capture()),
                    ReflectionHelpers.ClassParameter.from(ContinuityStamp.class,source.continuityStamp()),
                    ReflectionHelpers.ClassParameter.from(SceneObservationDomain.class,pipeline.sceneObservationDomain()),
                    ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.setup.CameraPreviewGeometry.class,null));
            assertSame(pending,ReflectionHelpers.getField(tracker,"pendingControl"));
            assertTrue(obsolete.isRecycled());assertFalse(inFlight.get());
            SourceFrameStamp live=new SourceFrameStamp(2,1_100_000_000L,source.domain,source.sceneGeneration,source.visualEpoch,source.cameraTransformGeneration);
            PreviewTrackingFrame tracked=tracker.updateLuma(movingLuma(0),240,240,null,live,new PreviewLumaHistory());
            assertEquals(PreviewTrackingFrame.State.LIVE,tracked.state);assertEquals(1,tracked.trackedPlates.size());
        } finally {pipeline.close();}
    }
    @Test public void ordinaryDynamicRecoveryRebasesOnCameraLumaWithoutWaitingForABitmap() {
        Application app=RuntimeEnvironment.getApplication();MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            android.content.SharedPreferences prefs=app.getSharedPreferences("camera-rebase-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            ReflectionHelpers.setField(main,"cameraStarted",true);
            PreviewPresentationBarrier barrier=ReflectionHelpers.getField(main,"previewPresentationBarrier");barrier.activate();
            ((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(main,"previewSceneRecoveryRebaseRevision")).set(7);
            com.example.alpr_v1.camera.LumaFrame frame=ReflectionHelpers.callConstructor(com.example.alpr_v1.camera.LumaFrame.class,
                    ReflectionHelpers.ClassParameter.from(byte[].class,movingLuma(0)),ReflectionHelpers.ClassParameter.from(int.class,240),
                    ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(long.class,2L),
                    ReflectionHelpers.ClassParameter.from(long.class,1_100_000_000L),ReflectionHelpers.ClassParameter.from(SourceTimestampDomain.class,SourceTimestampDomain.CAMERAX_SENSOR));
            ReflectionHelpers.callInstanceMethod(main,"processDirectLumaFrame",ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.camera.LumaFrame.class,frame));
            assertFalse("camera reference must release the presentation barrier",barrier.active());
            PreviewLumaHistory retained=ReflectionHelpers.getField(main,"ordinaryLumaHistory");
            assertEquals("the rebase frame must remain available for a delayed neural anchor",1,retained.retainedFrames());
            assertEquals(7L,(long)ReflectionHelpers.getField(main,"previewSceneRecoveryRebaseAppliedRevision"));
            assertFalse(ReflectionHelpers.<Boolean>callInstanceMethod(main,"needsPreviewSceneSampling"));
        } finally {pipeline.close();}
    }
    @Test public void intermediateMpStartsLocalTrackingBeforeTheSlowMzResultReturns() {
        Application app=RuntimeEnvironment.getApplication();MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            android.content.SharedPreferences prefs=app.getSharedPreferences("early-mp-stage-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            VehicleTrackingCoordinator coordinator=ReflectionHelpers.getField(pipeline,"vehicleTrackingCoordinator");
            long start=1_000_000_000L;ContinuityStamp context=pipeline.currentContinuityStamp(start);
            ContinuityStamp source=new ContinuityStamp(context.sceneGeneration,context.visualEpoch,context.cameraTransformGeneration,
                    1,start,SourceTimestampDomain.CAMERAX_SENSOR);
            android.graphics.RectF box=new RectF(48f/240,64f/240,160f/240,152f/240);
            VehicleTrackingFrame mp=coordinator.updateFromMp(1,source.sourceFrameStamp(),start,
                    Collections.singletonList(new VehicleTrackManager.Observation(new NormalizedBounds(box.left,box.top,box.right,box.bottom),
                            .9f,new AppearanceDescriptor(new float[]{1,0}),0)),Collections.emptySet(),start);
            long id=mp.candidates.get(0).entityId;DynamicVehicleIdentity.Stamp owner=coordinator.identity().stamp(id,mp.candidates.get(0).vehicleTrackId);
            com.example.alpr_v1.camera.LumaFrame luma=ReflectionHelpers.callConstructor(com.example.alpr_v1.camera.LumaFrame.class,
                    ReflectionHelpers.ClassParameter.from(byte[].class,movingLuma(0)),ReflectionHelpers.ClassParameter.from(int.class,240),
                    ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(long.class,source.sourceSequence),
                    ReflectionHelpers.ClassParameter.from(long.class,start),ReflectionHelpers.ClassParameter.from(SourceTimestampDomain.class,source.sourceTimestampDomain));
            List<OverlayItem> stage=Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,box,Collections.emptyList(),"",id,false));
            PreviewLumaHistory history=ReflectionHelpers.getField(main,"ordinaryLumaHistory");
            for(int frame=1;frame<15;frame++) {
                long time=start+frame*100_000_000L;
                history.offer(new SourceFrameStamp(frame+1,time,source.sourceTimestampDomain,source.sceneGeneration,
                        source.visualEpoch,source.cameraTransformGeneration),movingLuma(0),240,240);
            }
            ReflectionHelpers.callInstanceMethod(main,"presentIntermediateGeometryStage",ReflectionHelpers.ClassParameter.from(List.class,stage),
                    ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(int.class,240),
                    ReflectionHelpers.ClassParameter.from(ContinuityStamp.class,source),ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.camera.LumaFrame.class,luma),
                    ReflectionHelpers.ClassParameter.from(boolean.class,false));
            assertNull(ReflectionHelpers.getField(main,"presentingSourceLuma"));
            PreviewPlateTracker tracker=ReflectionHelpers.getField(main,"previewVehicleTracker");
            for(int frame=15;frame<=55;frame++) {
                long time=start+frame*100_000_000L;
                ContinuityStamp current=new ContinuityStamp(source.sceneGeneration,source.visualEpoch,source.cameraTransformGeneration,frame+1,time,source.sourceTimestampDomain);
                history.offer(current.sourceFrameStamp(),movingLuma(0),240,240);
                PreviewTrackingFrame tracked=tracker.updateLuma(movingLuma(0),240,240,null,current.sourceFrameStamp(),history);
                coordinator.observeLocalTargets(current,tracked,true,null,false);
            }
            assertTrue("owner must survive while MZ is still pending",coordinator.identity().accepts(owner));
            assertTrue(coordinator.preflightIdentity(owner,"AB12345",start));
            assertEquals(1,coordinator.identity().ownerNumber(id));assertEquals(start,coordinator.repository().get(id).lastMpNanos());
        } finally {pipeline.close();}
    }
    @Test public void delayedNeuralAnchorReplaysActualIntermediatePixelsBeforeLiveTracking() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();PreviewLumaHistory history=new PreviewLumaHistory();
        RectF box=new RectF(48f/240,64f/240,160f/240,152f/240);
        OverlayItem item=new OverlayItem(OverlayItem.Kind.PLATE,box,Arrays.asList(new PointF(box.left,box.top),
                new PointF(box.right,box.top),new PointF(box.right,box.bottom),new PointF(box.left,box.bottom)),"",1,false);
        long start=1_000_000_000L;
        SourceFrameStamp measured=new SourceFrameStamp(1,start,SourceTimestampDomain.CAMERAX_SENSOR,0,0,0);
        assertTrue(tracker.anchorLuma(Collections.singletonList(item),240,240,Collections.emptyMap(),movingLuma(0),240,240,measured));
        SourceFrameStamp current=measured;
        for(int i=1;i<=16;i++) {
            current=new SourceFrameStamp(i+1,start+i*100_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,0,0,0);
            history.offer(current,movingLuma(i),240,240);
        }
        PreviewTrackingFrame result=tracker.updateLuma(movingLuma(16),240,240,null,current,history);
        int updates=0;
        while(result.state==PreviewTrackingFrame.State.REPLAYING&&updates++<16) {
            assertTrue(tracker.lastReplaySteps()<=1);
            current=new SourceFrameStamp(current.sourceSequence+1,current.sourceTimestampNanos+100_000_000L,current.domain,
                    current.sceneGeneration,current.visualEpoch,current.cameraTransformGeneration);
            history.offer(current,movingLuma(16),240,240);
            result=tracker.updateLuma(movingLuma(16),240,240,null,current,history);
        }
        assertEquals(PreviewTrackingFrame.State.LIVE,result.state);
        assertNotNull(result);assertEquals(1,result.trackedPlates.size());TrackedPlate tracked=result.trackedPlates.get(0);
        assertTrue(tracked.localAppearanceValidated);assertEquals(0,tracked.consecutiveFailures);
        assertTrue(tracked.framesSinceMtAnchor>1);assertTrue(tracked.framesSinceMtAnchor<=16);
        assertEquals(start,tracked.anchorSourceTimestampNanos);
        assertEquals(120f/240,tracked.normalizedBounds.centerX(),.012f);
        assertEquals(108f/240,tracked.normalizedBounds.centerY(),.012f);
    }
    private static byte[] movingLuma(int dx) {
        byte[] gray=new byte[240*240];Arrays.fill(gray,(byte)40);
        for(int y=64;y<152;y++)for(int x=48;x<160;x++)
            gray[y*240+x+dx]=(byte)Math.max(0,Math.min(255,Math.round((float)(128
                    +42*Math.sin(x*.19)+36*Math.cos(y*.17)+27*Math.sin((x+y)*.11)+18*Math.cos(x*.07-y*.13)))));
        return gray;
    }
    @Test public void delayedAnchorUsesTheMeasuredPixelsBeforeMatchingTheLiveObject() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();
        byte[] measured=new byte[240*240],live=new byte[240*240];Arrays.fill(measured,(byte)40);Arrays.fill(live,(byte)40);
        for(int y=64;y<152;y++)for(int x=48;x<160;x++) {
            byte value=(byte)(70+Math.floorMod(x*7919+y*104729+x*y*31,170));
            measured[y*240+x]=value;live[(y+2)*240+x+4]=value;
        }
        RectF box=new RectF(48f/240,64f/240,160f/240,152f/240);
        List<PointF> corners=Arrays.asList(new PointF(box.left,box.top),new PointF(box.right,box.top),
                new PointF(box.right,box.bottom),new PointF(box.left,box.bottom));
        OverlayItem item=new OverlayItem(OverlayItem.Kind.PLATE,box,corners,"",1,false);
        assertTrue(tracker.anchorLuma(Collections.singletonList(item),240,240,Collections.emptyMap(),measured,240,240));
        Arrays.fill(measured,(byte)0); // The queued reference owns its bytes.
        PreviewTrackingFrame result=tracker.updateLuma(live,240,240);
        assertNotNull(result);assertEquals(1,result.trackedPlates.size());
        TrackedPlate tracked=result.trackedPlates.get(0);
        assertTrue(tracked.localAppearanceValidated);assertEquals(0,tracked.consecutiveFailures);
        assertEquals((104f+4)/240,tracked.normalizedBounds.centerX(),.012f);
        assertEquals((108f+2)/240,tracked.normalizedBounds.centerY(),.012f);
    }
    @Test public void delayedAnchorCannotInitializeOnPixelsFromAnotherCameraGeometry() {
        PreviewPlateTracker tracker=new PreviewPlateTracker();RectF box=new RectF(.2f,.2f,.8f,.8f);
        OverlayItem item=new OverlayItem(OverlayItem.Kind.PLATE,box,Arrays.asList(new PointF(.2f,.2f),new PointF(.8f,.2f),
                new PointF(.8f,.8f),new PointF(.2f,.8f)),"",1,false);
        assertTrue(tracker.anchorLuma(Collections.singletonList(item),240,240,Collections.emptyMap(),new byte[240*240],240,240));
        assertNull(tracker.updateLuma(new byte[120*120],120,120));
        assertNull(tracker.updateLuma(new byte[120*120],120,120));
    }
    @Test public void longObservationHistoriesRenderOneBestResultAndItsPhotoKeepsTheWinningSource()throws Exception {
        Application app=RuntimeEnvironment.getApplication();Context context=new ContextThemeWrapper(app,R.style.Theme_ALPR_v1);
        RecognitionHistoryStore history=new RecognitionHistoryStore();Bitmap image=Bitmap.createBitmap(16,4,Bitmap.Config.ARGB_8888);
        try {
            for(int frame=1;frame<=66;frame++) {
                PlateObservation observation=new PlateObservation(10,PlateVehicleAssociation.directValidated(1,1,1,"synthetic"),
                        MtWorkKind.VEHICLE_ROI,MtReason.UNKNOWN,frame,image,"AB12345",.9,.5,false,frame,
                        Collections.emptyList(),1000+frame,1000+frame,.8f,null,null,null,true,true,"AB12345",true,frame,"single",
                        Collections.emptyList(),"","AB12345",new ContinuityStamp(0,0,0,frame,frame*1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR));
                history.upsertObservation(observation,null,true,"synthetic","session_test");
            }
            RecognitionHistoryItem item=history.newestFirst().get(0);assertEquals(66,item.observations);
            RecognitionEntityPager pager=new RecognitionEntityPager(context,null);java.util.concurrent.atomic.AtomicLong photoFrame=new java.util.concurrent.atomic.AtomicLong();
            pager.setPhotoListener(observation->photoFrame.set(observation.frameId));pager.setItem(item);
            List<?> pages=ReflectionHelpers.getField(pager,"pages");Object page=pages.get(0);
            java.lang.reflect.Method render=RecognitionEntityPager.class.getDeclaredMethod("entityContent",page.getClass());render.setAccessible(true);
            android.widget.LinearLayout content=(android.widget.LinearLayout)render.invoke(pager,page);
            android.widget.ScrollView scroll=new android.widget.ScrollView(context);scroll.addView(content);
            assertEquals(1,content.getChildCount());assertEquals(1,pages.size());
            assertNull(findText(content,"Starsze"));
            View photo=findText(content,"Zdjęcie najlepszego wyniku");assertNotNull(photo);photo.performClick();assertEquals(66,photoFrame.get());
            assertEquals(66,item.observationRecords().size());
        }finally{history.clear();image.recycle();}
    }
    private View findText(View view,String value) {
        if(view instanceof android.widget.TextView&&value.contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=findText(((ViewGroup)view).getChildAt(i),value);if(result!=null)return result;}
        return null;
    }
    @Test public void oneBestConsensusCardShowsPnAndRetainsItsRawPhotoAtBothFontScales()throws Exception {
        for(float font:new float[]{1,1.5f}) {
            Application app=RuntimeEnvironment.getApplication();android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());config.fontScale=font;
            Context themed=new ContextThemeWrapper(app.createConfigurationContext(config),R.style.Theme_ALPR_v1);
            RecognitionHistoryStore history=new RecognitionHistoryStore();Bitmap photo=Bitmap.createBitmap(256,64,Bitmap.Config.ARGB_8888);
            VehicleTrackManager manager=new VehicleTrackManager(new VehicleEntityRepository());manager.setNormalDynamicIdentity(true,1_000_000_000L);
            VehicleTrackManager.Snapshot car=manager.update(Collections.singletonList(new VehicleTrackManager.Observation(
                    new NormalizedBounds(.1f,.1f,.9f,.9f),.9f,new AppearanceDescriptor(new float[]{1,0}),0)),1_000_000_000L).get(0);
            DynamicVehicleIdentity.Stamp owner=manager.identity().stamp(car.entityId,car.vehicleTrackId);
            try {
                Canvas canvas=new Canvas(photo);canvas.drawColor(Color.DKGRAY);Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.WHITE);ink.setTextSize(40);canvas.drawText("AB1234S",8,48,ink);
                for(int frame=1;frame<=2;frame++) {
                    String raw=frame==1?"AB1234S":"AB12345";List<PlateCharacter> characters=new ArrayList<>();
                    for(int i=0;i<raw.length();i++)characters.add(new PlateCharacter(raw.substring(i,i+1),frame==1?.9:.7,i/7f,0,(i+1)/7f,1));
                    PlateObservation observation=new PlateObservation(10,PlateVehicleAssociation.directValidated(car.entityId,car.vehicleTrackId,1,"synthetic"),
                            MtWorkKind.VEHICLE_ROI,MtReason.UNKNOWN,frame,photo,"AB12345",.9,frame==1?.8:.5,false,frame,
                            characters,1000+frame,1000+frame,.8f,null,null,null,true,true,raw,false,frame,"single",
                            Collections.emptyList(),"","AB12345",new ContinuityStamp(0,0,0,frame,frame*1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR));
                    ReflectionHelpers.callInstanceMethod(observation,"stampIdentityOwner",ReflectionHelpers.ClassParameter.from(DynamicVehicleIdentity.Stamp.class,owner));
                    history.upsertObservation(observation,null,true,"SYNTHETIC");
                }
                assertEquals(1,history.size());RecognitionHistoryItem item=history.newestFirst().get(0);
                View row=LayoutInflater.from(themed).inflate(R.layout.item_recognition_history,null);
                new RecognitionHistoryAdapter.Holder(row).bind(item,ignored->{});
                row.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));row.layout(0,0,720,row.getMeasuredHeight());
                android.widget.TextView number=row.findViewById(R.id.history_number),confidence=row.findViewById(R.id.history_confidence);
                assertEquals("P1 · AB12345",number.getText().toString());assertTrue(confidence.getText().toString().contains("80%"));
                assertEquals("AB1234S",item.text);assertEquals(2,item.observations);
                for(int line=0;line<number.getLayout().getLineCount();line++)assertEquals("title at font "+font,0,number.getLayout().getEllipsisCount(line));
                View observations=row.findViewById(R.id.history_observations);
                assertTrue("observation count must fit at font "+font,observations.getRight()<=((View)observations.getParent()).getWidth());
                String directory=System.getenv("DYNAMIC_STABILITY_UI_ARTIFACTS");
                if(directory!=null) {
                    File out=new File(directory);out.mkdirs();Bitmap capture=Bitmap.createBitmap(720,row.getHeight(),Bitmap.Config.ARGB_8888);row.draw(new Canvas(capture));
                    try(FileOutputStream stream=new FileOutputStream(new File(out,"one-vehicle-gallery-font"+font+".png"))){assertTrue(capture.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{capture.recycle();}
                }
            }finally{history.clear();photo.recycle();}
        }
    }
    @Test public void ordinaryMpCanAnchorDuringCameraMotionWhileResearchRetainsItsOldRule() {
        Application app=RuntimeEnvironment.getApplication();MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        android.content.SharedPreferences prefs=app.getSharedPreferences("motion-anchor-test",0);
        prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
        ReflectionHelpers.setField(main,"uiPreferences",prefs);
        ReflectionHelpers.setField(main,"presentingSourceLuma",ReflectionHelpers.callConstructor(
                com.example.alpr_v1.camera.LumaFrame.class,
                ReflectionHelpers.ClassParameter.from(byte[].class,new byte[240*240]),
                ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(int.class,240),
                ReflectionHelpers.ClassParameter.from(long.class,1L),ReflectionHelpers.ClassParameter.from(long.class,1L),
                ReflectionHelpers.ClassParameter.from(SourceTimestampDomain.class,SourceTimestampDomain.CAMERAX_SENSOR)));
        VehiclePreviewMotionPolicy motion=ReflectionHelpers.getField(main,"vehiclePreviewMotionPolicy");motion.update(true,1);
        List<OverlayItem> measured=Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(.1f,.2f,.8f,.9f),Collections.emptyList(),"",1,false));
        for(boolean research:new boolean[]{false,true}) {
            ReflectionHelpers.setField(main,"experimentModeEnabled",research);
            ReflectionHelpers.setField(main,"previewVehicleTrackerAwaitingFreshMpAnchor",true);
            ReflectionHelpers.callInstanceMethod(main,"anchorPreviewVehicleTracker",ReflectionHelpers.ClassParameter.from(List.class,measured),
                    ReflectionHelpers.ClassParameter.from(int.class,1000),ReflectionHelpers.ClassParameter.from(int.class,1000),
                    ReflectionHelpers.ClassParameter.from(String.class,"MP"));
            assertEquals(research,ReflectionHelpers.<Boolean>getField(main,"previewVehicleTrackerAwaitingFreshMpAnchor"));
        }
    }
    @Test public void actualMainProjectionAndNativeHudKeepP1AcrossSeventyMpMisses()throws Exception {
        Application app=RuntimeEnvironment.getApplication();Context themed=new ContextThemeWrapper(app,R.style.Theme_ALPR_v1);
        AlprPipeline pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        MainActivity main=Robolectric.buildActivity(MainActivity.class).get();
        DetectionOverlayView hud=new DetectionOverlayView(themed,null);
        hud.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));hud.layout(0,0,720,960);
        try {
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            android.content.SharedPreferences prefs=app.getSharedPreferences("stability-ui-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",pipeline);
            ReflectionHelpers.setField(main,"overlayView",hud);
            VehicleTrackingCoordinator c=ReflectionHelpers.getField(pipeline,"vehicleTrackingCoordinator");
            final long S=1_000_000_000L;ContinuityStamp context=pipeline.currentContinuityStamp(S);
            ContinuityStamp first=new ContinuityStamp(context.sceneGeneration,context.visualEpoch,context.cameraTransformGeneration,1,S,SourceTimestampDomain.CAMERAX_SENSOR);
            VehicleTrackingFrame mp=c.updateFromMp(1,first.sourceFrameStamp(),S,Collections.singletonList(new VehicleTrackManager.Observation(
                    new NormalizedBounds(.1f,.1f,.9f,.9f),.9f,new AppearanceDescriptor(new float[]{1,0}),0)),Collections.emptySet(),S);
            long id=mp.candidates.get(0).entityId;DynamicVehicleIdentity.Stamp owner=c.identity().stamp(id,mp.candidates.get(0).vehicleTrackId);
            NormalizedQuad plate=new NormalizedQuad(new float[]{.4f,.6f,.6f,.6f,.6f,.65f,.4f,.65f});
            c.repository().attachPlate(id,10,plate,new AppearanceDescriptor(new float[]{1,0}),S);c.anchorIdentityPlate(owner,10,plate,first);
            for(int frame=2;frame<=71;frame++) {
                long time=frame*S;
                ContinuityStamp stamp=new ContinuityStamp(first.sceneGeneration,first.visualEpoch,first.cameraTransformGeneration,frame,time,SourceTimestampDomain.CAMERAX_SENSOR);
                c.observeLocalTargets(stamp,com.example.alpr_v1.tracking.ComplementaryVehicleContinuityTest.frame(),true,
                        com.example.alpr_v1.tracking.ComplementaryVehicleContinuityTest.frame(com.example.alpr_v1.tracking.ComplementaryVehicleContinuityTest.tracked(10,owner,.4f)),true);
                c.updateFromMp(frame,stamp.sourceFrameStamp(),time,Collections.emptyList(),Collections.emptySet(),time);
                DynamicVehicleDisplaySnapshot snapshot=pipeline.dynamicDisplaySnapshot(stamp);
                assertNotNull("actual pipeline snapshot at frame "+frame,snapshot);
                assertTrue("local proof at frame "+frame,snapshot.localGeometry.containsKey(id));
                ReflectionHelpers.setField(main,"dynamicDisplaySnapshot",snapshot);
                hud.setVehicleIdentityPresentation(snapshot.identities,snapshot.plateOwners);
                List<OverlayItem> actual=ReflectionHelpers.callInstanceMethod(main,"dynamicCameraMotionOverlayItems",
                        ReflectionHelpers.ClassParameter.from(List.class,Collections.emptyList()),
                        ReflectionHelpers.ClassParameter.from(List.class,Collections.emptyList()),
                        ReflectionHelpers.ClassParameter.from(FrameMotionTransform.class,FrameMotionTransform.invalid()));
                assertEquals("actual Main projection at frame "+frame,1,actual.stream().filter(item->item.kind==OverlayItem.Kind.VEHICLE).count());
                hud.setItems(actual,1000,1000);
                assertEquals(1,hud.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
                assertTrue(hud.vehicleLabelForTesting(id),hud.vehicleLabelForTesting(id).startsWith("P1"));
                assertTrue(ReflectionHelpers.<Boolean>callStaticMethod(MainActivity.class,"hasLocalVehicleGeometry",
                        ReflectionHelpers.ClassParameter.from(List.class,actual),
                        ReflectionHelpers.ClassParameter.from(DynamicVehicleDisplaySnapshot.class,snapshot)));
                List<OverlayItem> unrelated=Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                        new RectF(.1f,.1f,.9f,.9f),Collections.emptyList(),"",id+99,false));
                assertFalse(ReflectionHelpers.<Boolean>callStaticMethod(MainActivity.class,"hasLocalVehicleGeometry",
                        ReflectionHelpers.ClassParameter.from(List.class,unrelated),
                        ReflectionHelpers.ClassParameter.from(DynamicVehicleDisplaySnapshot.class,snapshot)));
            }
            String folder=System.getenv("DYNAMIC_STABILITY_UI_ARTIFACTS");
            DynamicVehicleDisplaySnapshot expired=c.displaySnapshot(71*S+DynamicVehicleIdentity.COAST_NANOS+1);
            assertFalse(ReflectionHelpers.<Boolean>callStaticMethod(MainActivity.class,"hasLocalVehicleGeometry",
                    ReflectionHelpers.ClassParameter.from(List.class,Collections.singletonList(new OverlayItem(OverlayItem.Kind.VEHICLE,
                            new RectF(.1f,.1f,.9f,.9f),Collections.emptyList(),"",id,false))),
                    ReflectionHelpers.ClassParameter.from(DynamicVehicleDisplaySnapshot.class,expired)));
            if(folder!=null) {
                File output=new File(folder);output.mkdirs();Bitmap image=Bitmap.createBitmap(720,960,Bitmap.Config.ARGB_8888);hud.draw(new Canvas(image));
                try(FileOutputStream stream=new FileOutputStream(new File(output,"p1-after-seventy-mp-misses.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
            }
        } finally {pipeline.close();}
    }
}
