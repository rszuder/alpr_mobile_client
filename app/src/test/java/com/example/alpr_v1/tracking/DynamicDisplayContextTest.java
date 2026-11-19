package com.example.alpr_v1.tracking;

import android.app.Application;
import android.graphics.*;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.ui.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class DynamicDisplayContextTest {
    private static final long S=1_000_000_000L;
    private static final class Fixture implements AutoCloseable {
        final AlprPipeline pipeline;
        final VehicleTrackingCoordinator coordinator;
        final ContinuityStamp current,measured;
        final long id;
        final DynamicVehicleIdentity.Stamp owner;
        final NormalizedQuad quad=new NormalizedQuad(new float[]{.4f,.6f,.6f,.6f,.6f,.65f,.4f,.65f});
        Fixture() {
            Application app=RuntimeEnvironment.getApplication();
            pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
            pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            coordinator=ReflectionHelpers.getField(pipeline,"vehicleTrackingCoordinator");
            ContinuityStamp context=pipeline.currentContinuityStamp(2*S);assertTrue(context.visualEpoch>0);
            current=new ContinuityStamp(context.sceneGeneration,context.visualEpoch,context.cameraTransformGeneration,2,2*S,SourceTimestampDomain.CAMERAX_SENSOR);
            measured=new ContinuityStamp(current.sceneGeneration,current.visualEpoch-1,current.cameraTransformGeneration,1,S,current.sourceTimestampDomain);
            VehicleTrackingFrame mp=coordinator.updateFromMp(1,measured.sourceFrameStamp(),S,
                    Collections.singletonList(ComplementaryVehicleContinuityTest.car(0)),Collections.emptySet(),S);
            id=mp.candidates.get(0).entityId;owner=coordinator.identity().stamp(id,mp.candidates.get(0).vehicleTrackId);
            coordinator.repository().attachPlate(id,10,quad,new AppearanceDescriptor(new float[]{1,0}),S);
            coordinator.anchorIdentityPlate(owner,10,quad,measured);
        }
        PreviewTrackingFrame body() {return local(id,new RectF(.1f,.1f,.9f,.9f));}
        PreviewTrackingFrame plate(){return local(10,new RectF(.4f,.6f,.6f,.65f));}
        PreviewTrackingFrame local(long track,RectF b) {
            OverlayItem item=new OverlayItem(OverlayItem.Kind.PLATE,b,Arrays.asList(new PointF(b.left,b.top),new PointF(b.right,b.top),
                    new PointF(b.right,b.bottom),new PointF(b.left,b.bottom)),"",track,false);
            return new PreviewTrackingFrame(Collections.singletonList(new TrackedPlate(item,.9f,1,0,9,0,4,4,.95f,true,
                    null,2*S,owner,S)),2*S).withSourceStamp(current.sourceFrameStamp());
        }
        public void close(){pipeline.close();}
    }
    @Test public void freshBodyInCurrentEpochIsDisplayedWithoutWaitingForAnotherMp() {
        try(Fixture f=new Fixture()) {
            assertNull(f.pipeline.dynamicDisplaySnapshot(f.current));
            f.pipeline.observeVehicleIdentity(f.current,f.body(),true,null,false);
            DynamicVehicleDisplaySnapshot shown=f.pipeline.dynamicDisplaySnapshot(f.current);
            assertNotNull(shown);assertTrue(shown.localGeometry.containsKey(f.id));
            assertTrue(shown.identities.get(f.id).visible);assertEquals(1,shown.identities.get(f.id).number);
            assertEquals(S,f.coordinator.repository().get(f.id).lastMpNanos());
            assertEquals(f.measured.visualEpoch,f.coordinator.latestFrame().visualEpoch);
            assertNull(f.pipeline.dynamicDisplaySnapshot(f.current.withSourceTimestamp(4*S)));
        }
    }
    @Test public void freshPlateAloneCarriesOwnerAcrossSoftEpochWithoutANewMtOrOcr() {
        try(Fixture f=new Fixture()) {
            f.pipeline.observeVehicleIdentity(f.current,null,false,f.plate(),true);
            DynamicVehicleDisplaySnapshot shown=f.pipeline.dynamicDisplaySnapshot(f.current);
            assertNotNull(shown);assertNotNull(shown.vehicles.get(f.id).plate);
            assertEquals(S,shown.vehicles.get(f.id).plateMeasuredAtNanos);
            assertFalse(shown.vehicles.get(f.id).reading.available());
            assertEquals("PLATE",f.coordinator.identity().lastLocalEvidence(f.id));
            assertEquals(2,new DynamicPresentationController().geometry(Collections.emptyList(),shown,2*S).size());
        }
    }
    @Test public void actualPreviewTimelineInheritsCameraFreshnessForBothTrackingChannels() {
        try(Fixture f=new Fixture()) {
            f.pipeline.observeVehicleIdentity(f.current,f.body(),true,f.plate(),true);
            assertEquals("BOTH",f.coordinator.identity().lastLocalEvidence(f.id));
            ContinuityStamp preview=f.current.sourceFrameStamp().inheritedByPreview().continuityStamp();
            DynamicVehicleDisplaySnapshot shown=f.pipeline.dynamicDisplaySnapshot(preview);
            assertNotNull(shown);assertNotNull(shown.vehicles.get(f.id).plate);
            assertEquals(2*S,shown.vehicles.get(f.id).plateProofAtNanos);
            assertEquals(2,new DynamicPresentationController().geometry(Collections.emptyList(),shown,2*S).size());
            ContinuityStamp runtime=new ContinuityStamp(preview.sceneGeneration,preview.visualEpoch,preview.cameraTransformGeneration,
                    preview.sourceSequence,preview.sourceTimestampNanos,SourceTimestampDomain.RUNTIME_UPTIME);
            assertNull(f.coordinator.displaySnapshot(runtime).vehicles.get(f.id).plate);
        }
    }
    @Test public void oldOrUnstampedPlateCannotPromoteItsGeometryIntoAnotherEpoch() {
        try(Fixture f=new Fixture()) {
            f.pipeline.observeVehicleIdentity(f.current,null,false,f.plate().withSourceStamp(f.measured.sourceFrameStamp()),true);
            assertNull(f.pipeline.dynamicDisplaySnapshot(f.current));
            f.pipeline.observeVehicleIdentity(f.current,null,false,f.plate().withSourceStamp(null),true);
            assertNull(f.pipeline.dynamicDisplaySnapshot(f.current));
            assertEquals("NONE",f.coordinator.identity().lastLocalEvidence(f.id));
        }
    }
    @Test public void queueCompletionDoesNotResetTheDynamicPlateTracker() {
        try(Fixture f=new Fixture()) {
            com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
            android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("dyn-scope-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",f.pipeline);
            ReflectionHelpers.setField(main,"scanOverlayPresentationEntityId",f.id);
            PreviewPlateTracker tracker=ReflectionHelpers.getField(main,"previewPlateTracker");
            long revision=ReflectionHelpers.getField(tracker,"controlRevision");
            com.example.alpr_v1.pipeline.PipelineResult result=new com.example.alpr_v1.pipeline.PipelineResult("scan_queue_updated","","",0,
                    f.plate().overlayItems,240,240).withContinuityStamp(f.current);
            try {
                List<OverlayItem> scoped=ReflectionHelpers.callInstanceMethod(main,"scanScopedOverlayItems",
                        ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.pipeline.PipelineResult.class,result));
                assertTrue(scoped.isEmpty()); // Old MT geometry is not stamped as a new measurement.
                assertEquals(revision,(long)ReflectionHelpers.getField(tracker,"controlRevision"));
            }finally{result.close();}
        }
    }
    @Test public void ownedMtCallbackAnchorsOnceEvenWhenScanSessionHasAlreadyEnded() {
        try(Fixture f=new Fixture()) {
            f.pipeline.observeVehicleIdentity(f.current,f.body(),true,null,false);
            f.coordinator.anchorIdentityPlate(f.owner,10,f.quad,f.current);
            com.example.alpr_v1.MainActivity main=Robolectric.buildActivity(com.example.alpr_v1.MainActivity.class).get();
            android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("dyn-mt-callback-test",0);
            prefs.edit().putString(com.example.alpr_v1.SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity").commit();
            ReflectionHelpers.setField(main,"uiPreferences",prefs);ReflectionHelpers.setField(main,"pipeline",f.pipeline);
            ReflectionHelpers.setField(main,"cameraStarted",true);
            ReflectionHelpers.setField(main,"overlayView",new DetectionOverlayView(RuntimeEnvironment.getApplication(),null));
            com.example.alpr_v1.camera.LumaFrame luma=ReflectionHelpers.callConstructor(com.example.alpr_v1.camera.LumaFrame.class,
                    ReflectionHelpers.ClassParameter.from(byte[].class,new byte[240*240]),ReflectionHelpers.ClassParameter.from(int.class,240),
                    ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(long.class,f.current.sourceSequence),
                    ReflectionHelpers.ClassParameter.from(long.class,2*S),ReflectionHelpers.ClassParameter.from(SourceTimestampDomain.class,f.current.sourceTimestampDomain));
            PreviewPlateTracker tracker=ReflectionHelpers.getField(main,"previewPlateTracker");
            for(int repeat=0;repeat<2;repeat++)ReflectionHelpers.callInstanceMethod(main,"presentIntermediateGeometryStage",
                    ReflectionHelpers.ClassParameter.from(List.class,f.plate().overlayItems),ReflectionHelpers.ClassParameter.from(int.class,240),
                    ReflectionHelpers.ClassParameter.from(int.class,240),ReflectionHelpers.ClassParameter.from(ContinuityStamp.class,f.current),
                    ReflectionHelpers.ClassParameter.from(com.example.alpr_v1.camera.LumaFrame.class,luma),ReflectionHelpers.ClassParameter.from(boolean.class,false));
            assertEquals(1L,(long)ReflectionHelpers.getField(tracker,"controlRevision"));
            assertFalse(f.pipeline.scanAcquisitionSnapshot().runState.active());
        }
    }
}
