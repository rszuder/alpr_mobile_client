package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StaticSceneWatcherTest {
    private static final NormalizedBounds REGION = new NormalizedBounds(0.2f, 0.2f, 0.6f, 0.6f);
    private final byte[] baseline = pattern();
    private StaticSceneWatcher watcher(boolean regions) {
        StaticSceneWatcher watcher = new StaticSceneWatcher();
        watcher.arm(new StaticSceneWatchRegions(regions ? Collections.singletonList(REGION) : null, null, 0f));
        watcher.observe(baseline, 100, 100, 1L, false);
        return watcher;
    }
    @Test public void s7ChangedVehicleRegionConfirmsBoundaryEvenWithSameBackground() {
        StaticSceneWatcher watcher = watcher(true);
        byte[] changed = change(20, 20, 60, 60);
        StaticSceneWatcher.Result result = watcher.observe(changed, 100, 100, 100_000_000L, false);
        assertTrue(result.changed); assertEquals("static_alpr_region_changed", result.reason);
        assertTrue(result.globalFraction < StaticSceneWatcher.DEFAULT.globalFraction);
    }
    @Test public void s8BackgroundOutsideWatchRegionDoesNotReset() {
        StaticSceneWatcher watcher = watcher(true);
        byte[] changed = change(75, 75, 95, 95);
        for (int i = 1; i <= 10; i++) assertFalse(watcher.observe(changed, 100, 100, i*100_000_000L, false).changed);
    }
    @Test public void s9GlobalFallbackWorksWithoutRegions() {
        StaticSceneWatcher watcher = watcher(false);
        byte[] changed = change(0, 0, 100, 100);
        assertTrue(watcher.observe(changed, 100, 100, 100_000_000L, false).changed);
    }
    @Test public void s4NoiseAndUniformExposureFlashDoNotCreateScene() {
        StaticSceneWatcher watcher = watcher(true);
        byte[] light = baseline.clone();
        for (int i = 0; i < light.length; i++) light[i] += 35;
        for (int i = 1; i <= 8; i++) assertFalse(watcher.observe(light, 100, 100, i*100_000_000L, false).changed);
        byte[] transientChange = change(20,20,36,60);
        assertFalse(watcher.observe(transientChange,100,100,900_000_000L,false).changed);
        assertFalse(watcher.observe(baseline,100,100,1_000_000_000L,false).changed);
    }
    @Test public void s5ControlledZoomDoesNotCreateScene() {
        StaticSceneWatcher watcher = watcher(true);
        assertFalse(watcher.observe(change(0,0,100,100),100,100,100_000_000L,true).changed);
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(SceneHandlingMode.STRICT_SCENE_BOUNDARY, SceneContinuityProfile.INITIAL);
        ContinuityStamp old = coordinator.stamp(1L);
        coordinator.advanceCameraTransformGeneration(2L);
        assertEquals(old.sceneGeneration, coordinator.snapshot().sceneGeneration);
        assertEquals(ContinuityResultDisposition.REJECT_STALE_CAMERA_TRANSFORM,
                new ContinuityGenerationGate().evaluate(coordinator.stamp(3L), old));
    }
    @Test public void zoomAndArmingRegionsCannotHideARealSampleChangeAtBaseZoom() {
        StaticSceneWatcher watcher = watcher(false);
        byte[] changed = change(0,0,100,100);
        assertFalse(watcher.observe(changed,100,100,100_000_000L,true).changed);
        watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(REGION),null,.12f));
        assertTrue(watcher.observe(changed,100,100,200_000_000L,false).changed);
    }

    @Test public void moderateRegionChangeNeedsOnlyTwoFramesAndFiftyMilliseconds() {
        StaticSceneWatcher watcher = watcher(true);
        byte[] changed = change(20,20,36,60);
        assertFalse(watcher.observe(changed,100,100,100_000_000L,false).changed);
        assertTrue(watcher.observe(changed,100,100,150_000_000L,false).changed);
    }

    @Test public void delayedInferenceCannotRearmWatcherOnTheNewPhoto() {
        StaticSceneWatcher watcher = watcher(false);
        byte[] changed = change(20,20,60,60);
        assertFalse(watcher.observe(changed,100,100,100_000_000L,false).changed);
        watcher.arm(new StaticSceneWatchRegions(Collections.singletonList(REGION),null,0f));
        watcher.prepareForZoom();
        assertTrue(watcher.observe(changed,100,100,110_000_000L,false).changed);
    }

    @Test public void s1s6s11SameOcrAfterBoundaryHasNewIdentityAndRejectsOldStamp() {
        VehicleEntityRepository repository = new VehicleEntityRepository();
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(SceneHandlingMode.STRICT_SCENE_BOUNDARY, SceneContinuityProfile.INITIAL);
        VehicleEntity first = repository.create(1L, REGION, null, 1L);
        repository.updateRegistration(first.entityId(), new PlateTextConsensus("WI1234A", .9f, 3, true), 1L);
        ContinuityStamp old = coordinator.stamp(1L);
        coordinator.requestStructuralReset("static_alpr_region_changed", 2L);
        repository.resetScene();
        assertNull(repository.get(first.entityId()));
        VehicleEntity second = repository.create(2L, REGION, null, 3L);
        assertTrue(second.registration().text.isEmpty());
        repository.updateRegistration(second.entityId(), new PlateTextConsensus("WI1234A", .9f, 3, true), 3L);
        assertNotEquals(first.entityId(), second.entityId());
        assertEquals(ContinuityResultDisposition.REJECT_ALL,
                new ContinuityGenerationGate().evaluate(coordinator.stamp(3L), old));
    }
    @Test public void regionsChooseVehiclesThenPlatesAndAreDetachedFromSourceList() {
        List<NormalizedBounds> vehicles = new ArrayList<>(Collections.singletonList(REGION));
        StaticSceneWatchRegions fixed = new StaticSceneWatchRegions(vehicles, null, .1f);
        vehicles.clear(); assertEquals(1, fixed.bounds.size()); assertEquals("vehicles", fixed.source);
        assertEquals(.16f, fixed.bounds.get(0).left, .001f);
        assertEquals("plates", new StaticSceneWatchRegions(vehicles, Collections.singletonList(REGION), .1f).source);
    }
    @Test public void rapidDistinctPhotosBypassResetCooldownButOldStampCannotResetAgain() {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.STRICT_SCENE_BOUNDARY,SceneContinuityProfile.INITIAL);
        ContinuityStamp first = coordinator.stamp(1L);
        assertEquals(SceneTransitionAction.HARD_RESET,
                coordinator.requestStaticSceneReset(first,"photo_one",1L).action);
        assertEquals(SceneTransitionAction.NONE,
                coordinator.requestStaticSceneReset(first,"old_callback",2L).action);
        ContinuityStamp next = coordinator.stamp(3L);
        assertEquals(SceneTransitionAction.HARD_RESET,
                coordinator.requestStaticSceneReset(next,"photo_two",3L).action);
        assertEquals(first.sceneGeneration+2,coordinator.snapshot().sceneGeneration);
    }

    private byte[] change(int left,int top,int right,int bottom) {
        byte[] changed=baseline.clone();
        for(int y=top;y<bottom;y++) for(int x=left;x<right;x++) changed[y*100+x]=(byte)(200-(baseline[y*100+x]&255));
        return changed;
    }
    private static byte[] pattern() {
        byte[] pixels=new byte[10000];
        for(int y=0;y<100;y++) for(int x=0;x<100;x++) pixels[y*100+x]=(byte)(((x/4+y/4)%2==0)?40:160);
        return pixels;
    }
}
