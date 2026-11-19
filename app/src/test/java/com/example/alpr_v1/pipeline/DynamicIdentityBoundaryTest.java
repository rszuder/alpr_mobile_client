package com.example.alpr_v1.pipeline;

import android.app.Application;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.acquisition.ScanAcquisitionController;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.tracking.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class)
public class DynamicIdentityBoundaryTest {
    AlprPipeline pipeline;VehicleTrackingCoordinator vehicles;
    @Before public void setup(){Application app=RuntimeEnvironment.getApplication();pipeline=new AlprPipeline(app,new ModelRegistry(app,true),new MetricsCollector(),new AutoTuneManager(app));
        pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);vehicles=ReflectionHelpers.getField(pipeline,"vehicleTrackingCoordinator");}
    @After public void close(){pipeline.close();}
    @Test public void actualPipelineTerminalLossKeepsManualSelectionUntilOperatorRelease(){
        VehicleEntity entity=vehicles.repository().create(1L,new NormalizedBounds(.1f,.2f,.4f,.5f),new AppearanceDescriptor(new float[]{1,0}),1_000_000_000L);
        ScanAcquisitionController scan=ReflectionHelpers.getField(pipeline,"scanAcquisitionController");scan.startRun(1L,1_000_000_000L);scan.pickVehicle(entity.entityId(),1_100_000_000L);
        assertEquals(entity.entityId(),scan.targetFocus().entityId);
        SceneTransitionCoordinator coordinator=ReflectionHelpers.getField(pipeline,"sceneTransitionCoordinator");coordinator.requestSoftReacquire("synthetic missing focus",1_200_000_000L,null);
        SoftReacquireReport report=SoftReacquireReport.terminal(SoftReacquireResult.ACTIVE_TARGET_LOST,VehicleContinuityEvidence.empty(),"synthetic B measured; A missing");
        ReflectionHelpers.callInstanceMethod(pipeline,"handleSoftReacquireReport",ReflectionHelpers.ClassParameter.from(SoftReacquireReport.class,report));
        assertEquals(entity.entityId(),scan.targetFocus().entityId);assertTrue(scan.targetFocus().waitingForTarget);
        assertSame(entity,vehicles.repository().get(entity.entityId()));
        pipeline.releaseSelectedTarget(false);
        assertEquals(0L,scan.targetFocus().entityId);assertSame(entity,vehicles.repository().get(entity.entityId()));assertFalse(entity.activeTarget());
    }
    @Test public void staticAndFrozenResearchNeverEnableTheNewIdentityPolicy()throws Exception {
        assertTrue(ReflectionHelpers.<Boolean>callInstanceMethod(pipeline,"normalDynamicIdentityEnabled"));
        pipeline.setSceneHandlingMode(SceneHandlingMode.STRICT_SCENE_BOUNDARY);assertFalse(ReflectionHelpers.<Boolean>callInstanceMethod(pipeline,"normalDynamicIdentityEnabled"));
        java.lang.reflect.Method factory=com.example.alpr_v1.ui.readiness.ReadinessEvaluatorTest.class.getDeclaredMethod("configuration",RoiBudgetPolicy.class,boolean.class,boolean.class,boolean.class,SceneHandlingMode.class);
        factory.setAccessible(true);
        com.example.alpr_v1.ui.readiness.ReadinessEvaluatorTest fixture=new com.example.alpr_v1.ui.readiness.ReadinessEvaluatorTest();fixture.setup();
        Object frozen=factory.invoke(fixture,RoiBudgetPolicy.FULL_FRAME,false,false,true,SceneHandlingMode.DYNAMIC_CONTINUITY);
        ReflectionHelpers.setField(pipeline,"frozenResearchExecutionConfig",frozen);pipeline.setSceneHandlingMode(SceneHandlingMode.DYNAMIC_CONTINUITY);
        assertFalse(ReflectionHelpers.<Boolean>callInstanceMethod(pipeline,"normalDynamicIdentityEnabled"));
    }
    @Test public void oldSceneMutationCannotUpdateNewIdentityOrCurrentGeometry(){
        SceneTransitionCoordinator coordinator=ReflectionHelpers.getField(pipeline,"sceneTransitionCoordinator");
        SourceFrameStamp source=new SourceFrameStamp(1L,1_000_000_000L,SourceTimestampDomain.CAMERAX_SENSOR,coordinator.snapshot().sceneGeneration,coordinator.snapshot().visualEpoch,coordinator.snapshot().cameraTransformGeneration);
        ContinuityStamp old=coordinator.stamp(source);coordinator.requestStructuralReset("next physical scene",1_100_000_000L);
        vehicles.repository().resetScene();
        NormalizedBounds currentBounds=new NormalizedBounds(.6f,.2f,.9f,.5f);
        VehicleEntity current=vehicles.repository().create(1,currentBounds,new AppearanceDescriptor(new float[]{0,1}),1_100_000_000L);
        assertThrows(MobileAlprEngine.ProcessingCancelledException.class,()->new SceneMutationGate(coordinator).run(old,()->{
            // A reused technical track id would otherwise overwrite the new physical owner's geometry.
            vehicles.repository().updateFromMp(1,new NormalizedBounds(.1f,.1f,.2f,.2f),MotionState.STATIONARY,new AppearanceDescriptor(new float[]{1,0}),1_200_000_000L);
            return null;
        }));
        assertSame(current,vehicles.repository().get(current.entityId()));assertSame(currentBounds,current.vehicleBounds());
        assertEquals(1_100_000_000L,current.lastMpNanos());
    }
    @Test public void deferredHardResetIsNotLostWhenLaterPreviewEvidenceIsCalm() {
        VehicleEntity entity=vehicles.repository().create(1,new NormalizedBounds(.1f,.2f,.4f,.5f),new AppearanceDescriptor(new float[]{1,0}),1_000_000_000L);
        ScanAcquisitionController scan=ReflectionHelpers.getField(pipeline,"scanAcquisitionController");
        scan.startRun(1,1_000_000_000L);scan.pickVehicle(entity.entityId(),1_100_000_000L);
        SceneTransitionCoordinator coordinator=ReflectionHelpers.getField(pipeline,"sceneTransitionCoordinator");
        SceneTransitionDecision hard=coordinator.requestStructuralReset("actual replacement scene",1_200_000_000L);
        ReflectionHelpers.callInstanceMethod(pipeline,"publishUiTransition",ReflectionHelpers.ClassParameter.from(SceneTransitionDecision.class,hard));
        pipeline.onWorkerPreviewSceneEvidence(pipeline.currentContinuityStamp(1_300_000_000L).sourceFrameStamp(),false,0,0,0,0,0,false,false,null);
        ReflectionHelpers.callInstanceMethod(pipeline,"applyPendingUiTransitions");
        assertTrue(scan.targetFocus().waitingForTarget);assertEquals(entity.entityId(),scan.targetFocus().entityId);
    }
    @Test public void survivingVehiclePixelsPreventAPlateOnlyLossFromResettingTheScene() {
        final long S=1_000_000_000L;
        vehicles.setNormalDynamicIdentity(true,S);
        ContinuityStamp first=pipeline.currentContinuityStamp(S);
        VehicleTrackingFrame measured=vehicles.updateFromMp(1,first.sourceFrameStamp(),S,Collections.singletonList(
                new VehicleTrackManager.Observation(new NormalizedBounds(.1f,.2f,.4f,.8f),.9f,new AppearanceDescriptor(new float[]{1,0}),0)),Collections.emptySet(),S);
        long id=measured.candidates.get(0).entityId;
        ScanAcquisitionController scan=ReflectionHelpers.getField(pipeline,"scanAcquisitionController");
        scan.setNormalDynamicPolicy(true);scan.startLiveRun(1,S);pipeline.pickVehicle(id);
        ContinuityStamp preview=pipeline.currentContinuityStamp(3*S);
        vehicles.observeLocalTargets(preview,ComplementaryVehicleContinuityTest.frame(ComplementaryVehicleContinuityTest.tracked(id,null,.1f)),true,
                ComplementaryVehicleContinuityTest.frame(),true);
        TargetContinuityEvidence evidence=ReflectionHelpers.callInstanceMethod(pipeline,"currentTargetEvidence",ReflectionHelpers.ClassParameter.from(long.class,3*S));
        assertEquals(id,evidence.entityId);assertEquals(TargetContinuityLevel.VEHICLE_ONLY,evidence.level);
        assertFalse(evidence.freshVehicleMeasurement);assertFalse(evidence.freshPlateMeasurement);
        SceneTransitionDecision decision=pipeline.onPreviewSceneEvidence(preview.sourceFrameStamp(),false,0,0,0,0,0,true,false);
        assertEquals(SceneTransitionAction.NONE,decision.action);assertEquals(id,scan.lockedEntityId());
    }
}
