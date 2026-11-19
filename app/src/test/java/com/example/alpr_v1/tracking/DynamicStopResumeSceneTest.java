package com.example.alpr_v1.tracking;

import android.app.Application;
import android.graphics.RectF;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.AlprPipeline;
import com.example.alpr_v1.tracking.*;
import com.example.alpr_v1.ui.OverlayItem;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Synthetic source-bound local observations, real scene policy and identity owners. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
public class DynamicStopResumeSceneTest {
    static final long S=1_000_000_000L;

    @Test public void resumedLumaChangeCannotReacquireTwoStillLocallyProvenVehicles() {
        try(Fixture f=new Fixture()) {
            f.missMpWithLivePixels();
            ContinuityStamp source=f.source(4,1_300_000_000L);
            f.p.observeVehicleIdentity(source,f.bodies(source,0,1),true,null,false);
            SceneTransitionDecision decision=f.change(source,true,false);
            assertEquals("current object pixels must be evaluated before scene recovery",SceneTransitionAction.NONE,decision.action);
            assertEquals(SceneContinuityState.STABLE,decision.nextState);
            assertEquals(f.initial.visualEpoch,f.p.sceneContinuitySnapshot().visualEpoch);
            assertEquals(f.initial.sceneGeneration,f.p.sceneContinuitySnapshot().sceneGeneration);
            for(int i=0;i<2;i++) {
                assertTrue(f.c.identity().accepts(f.owners.get(i)));
                assertEquals(i+1,f.c.identity().ownerNumber(f.ids.get(i)));
                assertEquals(S,f.c.repository().get(f.ids.get(i)).lastMpNanos());
            }
        }
    }

    @Test public void locallyProvenNeighborSurvivesAChangedFrameAndLostFocus() {
        try(Fixture f=new Fixture()) {
            f.missMpWithLivePixels();
            ContinuityStamp source=f.source(4,1_300_000_000L);
            f.p.observeVehicleIdentity(source,f.bodies(source,1),true,null,false);
            SceneTransitionDecision decision=f.change(source,true,true);
            assertEquals("one missing object must not clear a current neighbor",SceneTransitionAction.NONE,decision.action);
            assertTrue(f.c.identity().accepts(f.owners.get(1)));
        }
    }

    @Test public void directLumaBoundaryCannotResetCurrentLocalOwners() {
        try(Fixture f=new Fixture()) {
            ContinuityStamp source=f.source(2,1_100_000_000L);
            f.p.observeVehicleIdentity(source,f.bodies(source,0,1),true,null,false);
            SceneTransitionDecision decision=f.p.requestAbruptSceneReset(source.sourceFrameStamp(),.34f,27f);
            assertEquals("the direct-luma shortcut must not bypass current object evidence",SceneTransitionAction.NONE,decision.action);
            assertEquals(f.initial.sceneGeneration,f.p.sceneContinuitySnapshot().sceneGeneration);
        }
    }

    @Test public void localEvidenceIsExactSourceBoundAndCannotSurviveAnOwnerBreak() {
        try(Fixture f=new Fixture()) {
            ContinuityStamp source=f.source(2,1_100_000_000L);
            f.p.observeVehicleIdentity(source,f.bodies(source,0,1),true,null,false);
            assertEquals(2,f.c.localPoolEvidence(source.sourceFrameStamp()).provenCount());
            assertEquals(0,f.c.localPoolEvidence(f.source(3,1_200_000_000L).sourceFrameStamp()).provenCount());
            SourceFrameStamp other=new SourceFrameStamp(source.sourceSequence,source.sourceTimestampNanos,source.sourceTimestampDomain,
                    source.sceneGeneration,source.visualEpoch+1,source.cameraTransformGeneration);
            assertEquals(0,f.c.localPoolEvidence(other).provenCount());
            ContinuityStamp missing=f.source(4,2*S);
            f.p.observeVehicleIdentity(missing,new PreviewTrackingFrame(Collections.emptyList(),2*S).withSourceStamp(missing.sourceFrameStamp(),true),true,null,false);
            assertFalse(f.c.identity().accepts(f.owners.get(0)));
            assertEquals(0,f.c.localPoolEvidence(source.sourceFrameStamp()).provenCount());
        }
    }

    @Test public void replayingAndUnavailableChannelsAreNotNegativeObjectEvidence() {
        try(Fixture f=new Fixture()) {
            ContinuityStamp source=f.source(2,1_100_000_000L);
            f.p.observeVehicleIdentity(source,PreviewTrackingFrame.pending(PreviewTrackingFrame.State.REPLAYING,source.sourceFrameStamp()),true,null,false);
            LocalVehiclePoolEvidence pool=f.c.localPoolEvidence(source.sourceFrameStamp());
            assertEquals(0,pool.provenCount());assertEquals(2,pool.entries.size());
            for(LocalVehiclePoolEvidence.Entry item:pool.entries.values())assertEquals(LocalVehiclePoolEvidence.Status.UNAVAILABLE,item.status);
        }
    }

    @Test public void anActualCutStillResetsAndRejectsOldOwners() {
        try(Fixture f=new Fixture()) {
            ContinuityStamp source=f.source(2,1_100_000_000L);
            SceneTransitionDecision decision=f.p.requestAbruptSceneReset(source.sourceFrameStamp(),.9f,100f);
            assertEquals(SceneTransitionAction.HARD_RESET,decision.action);
            assertTrue(f.p.sceneContinuitySnapshot().sceneGeneration>f.initial.sceneGeneration);
            assertNull("old callbacks cannot reset the new generation",f.p.requestAbruptSceneReset(source.sourceFrameStamp(),.9f,100f));
        }
    }

    static final class Fixture implements AutoCloseable {
        final AlprPipeline p;
        final VehicleTrackingCoordinator c;
        final ContinuityStamp initial;
        final List<Long> ids=new ArrayList<>();
        final List<DynamicVehicleIdentity.Stamp> owners=new ArrayList<>();
        Fixture() {
            Application app=RuntimeEnvironment.getApplication();
            p=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
            p.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
            c=ReflectionHelpers.getField(p,"vehicleTrackingCoordinator");
            c.setNormalDynamicIdentity(true,S);
            ContinuityStamp context=p.currentContinuityStamp(S);
            initial=new ContinuityStamp(context.sceneGeneration,context.visualEpoch,context.cameraTransformGeneration,1,S,SourceTimestampDomain.CAMERAX_SENSOR);
            List<VehicleTrackManager.Observation> observations=new ArrayList<>();
            for(int i=0;i<2;i++)observations.add(new VehicleTrackManager.Observation(bounds(i),.9f,
                    new AppearanceDescriptor(i==0?new float[]{1,0}:new float[]{0,1}),i));
            VehicleTrackingFrame frame=c.updateFromMp(1,initial.sourceFrameStamp(),S,observations,Collections.emptySet(),S);
            for(VehicleCandidate item:frame.candidates){ids.add(item.entityId);owners.add(c.identity().stamp(item.entityId,item.vehicleTrackId));}
        }
        ContinuityStamp source(long sequence,long time) {
            return new ContinuityStamp(initial.sceneGeneration,initial.visualEpoch,initial.cameraTransformGeneration,sequence,time,initial.sourceTimestampDomain);
        }
        void missMpWithLivePixels() {
            ContinuityStamp proof=source(2,1_100_000_000L);
            p.observeVehicleIdentity(proof,bodies(proof,0,1),true,null,false);
            ContinuityStamp miss=source(3,1_200_000_000L);
            c.updateFromMp(2,miss.sourceFrameStamp(),miss.sourceTimestampNanos,Collections.emptyList(),Collections.emptySet(),miss.sourceTimestampNanos);
            for(DynamicVehicleIdentity.Stamp owner:owners)assertTrue(c.identity().accepts(owner));
        }
        PreviewTrackingFrame bodies(ContinuityStamp source,int... indices) {
            List<TrackedPlate> values=new ArrayList<>();
            for(int i:indices) {
                NormalizedBounds b=bounds(i);
                OverlayItem overlay=new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(b.left,b.top,b.right,b.bottom),Collections.emptyList(),"",ids.get(i),false);
                values.add(new TrackedPlate(overlay,.95f,1,0,9,0,4,4,.95f,true,null,source.sourceTimestampNanos,owners.get(i),S));
            }
            return new PreviewTrackingFrame(values,source.sourceTimestampNanos).withSourceStamp(source.sourceFrameStamp(),true);
        }
        SceneTransitionDecision change(ContinuityStamp source,boolean changed,boolean lost) {
            return p.onWorkerPreviewSceneEvidence(source.sourceFrameStamp(),changed,.34f,.34f,27f/255f,0f,0f,lost,false,
                    new MotionExplanationEvidence(true,false,false,0,false,false,0,1,0,0,false));
        }
        @Override public void close(){p.close();}
    }
    static NormalizedBounds bounds(int i){return new NormalizedBounds(.08f+i*.5f,.2f,.38f+i*.5f,.65f);}
}
