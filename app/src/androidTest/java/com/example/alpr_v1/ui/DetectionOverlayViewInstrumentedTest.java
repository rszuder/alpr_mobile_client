package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class DetectionOverlayViewInstrumentedTest {
    @Test public void l8l11PersistentFocusImmediatelyRemovesNeighborsAndTheirLateTransfers() {
        AtomicReference<DetectionOverlayView> reference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(InstrumentationRegistry.getInstrumentation().getTargetContext(),null);
            reference.set(view); view.layout(0,0,720,1280); view.setDiagnosticMode(true);
            view.setPlateEntityResolver(track -> track == 11L ? 1L : track == 22L ? 2L : 0L);
            List<OverlayItem> pool = Arrays.asList(
                    item(OverlayItem.Kind.VEHICLE,new RectF(.1f,.2f,.45f,.8f),1L),
                    item(OverlayItem.Kind.VEHICLE,new RectF(.55f,.2f,.9f,.8f),2L),
                    item(OverlayItem.Kind.VEHICLE_ROI,new RectF(.1f,.2f,.45f,.8f),1L),
                    item(OverlayItem.Kind.VEHICLE_ROI,new RectF(.55f,.2f,.9f,.8f),2L),
                    item(OverlayItem.Kind.PLATE,new RectF(.2f,.6f,.35f,.7f),11L),
                    item(OverlayItem.Kind.PLATE,new RectF(.65f,.6f,.8f,.7f),22L));
            view.setItems(pool,720,1280);
            view.animatePlateObservation(animationObservation(2L,22L));
            view.setTargetFocus(1L,false);
            assertEquals(1,view.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
            assertEquals(0L,view.plateAbsorptionEntityForTesting());
            assertFalse(view.recognizedVehicleForTesting(2L));
            view.setItems(pool,720,1280);
            view.setPreviewItems(pool);
            view.setTrackedPlateItems(pool);
            view.animatePlateObservation(animationObservation(2L,22L));
            assertTrue(view.snapshotItemsForTesting().stream().allMatch(i -> i.trackId == 1L || i.trackId == 11L));
            assertEquals(0,view.fadingPlateCountForTesting());
        });
        android.os.SystemClock.sleep(1100L);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertFalse(reference.get().recognizedVehicleForTesting(2L));
            assertEquals(0L,reference.get().plateAbsorptionEntityForTesting());
        });
    }

    @Test public void l13ReleaseDoesNotRestoreOldGeometryBeforeFreshMeasurement() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(InstrumentationRegistry.getInstrumentation().getTargetContext(),null);
            view.layout(0,0,720,1280);
            List<OverlayItem> old = Collections.singletonList(item(OverlayItem.Kind.VEHICLE,new RectF(.1f,.2f,.4f,.8f),1L));
            view.setItems(old,720,1280); view.setTargetFocus(1L,false);
            view.setTargetFocus(0L,true);
            view.setItems(old,720,1280); view.setPreviewItems(old);
            assertTrue(view.snapshotItemsForTesting().isEmpty());
            assertTrue(view.snapshotRenderBoundsForTesting().isEmpty());
            view.setTargetFocus(0L,false);
            assertTrue(view.snapshotItemsForTesting().isEmpty());
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,new RectF(.6f,.2f,.9f,.8f),3L)),720,1280);
            assertEquals(3L,view.snapshotItemsForTesting().get(0).trackId);
        });
    }
    @Test public void opticalZoomScalesVehiclesWithoutPlateAndReturnsToExactBaseBounds() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
            view.layout(0, 0, 720, 1280);
            RectF baseBounds = new RectF(.1f, .25f, .8f, .65f);
            List<OverlayItem> base = ZoomVehicleOverlay.snapshot(Collections.singletonList(item(OverlayItem.Kind.VEHICLE, baseBounds, 7L)));
            view.setStationaryScene(true);
            view.setItems(base,720,1280);
            // Small optical steps must bypass the stationary jitter threshold.
            view.setOpticalTransformItems(ZoomVehicleOverlay.atZoom(Collections.emptyList(),base,1.01f),720,1280);
            assertEquals(.096f,view.snapshotItemsForTesting().get(0).normalizedBounds.left,.0001f);
            List<OverlayItem> zoomed = ZoomVehicleOverlay.atZoom(base,base,1.8f);
            view.setOpticalTransformItems(zoomed,720,1280);
            assertEquals(.72f,view.snapshotItemsForTesting().get(0).normalizedBounds.height(),.0001f);
            assertEquals(0f,view.snapshotItemsForTesting().get(0).normalizedBounds.left,.0001f);
            // Fresh MT may carry a stale vehicle box; the retained base owns optical geometry.
            assertEquals(zoomed.get(0).normalizedBounds,ZoomVehicleOverlay.atZoom(base,base,1.8f).get(0).normalizedBounds);
            view.setOpticalTransformItems(ZoomVehicleOverlay.atZoom(zoomed,base,1f),720,1280);
            RectF returned = view.snapshotItemsForTesting().get(0).normalizedBounds;
            assertEquals(baseBounds.left, returned.left, .0001f);
            assertEquals(baseBounds.top, returned.top, .0001f);
            assertEquals(baseBounds.right, returned.right, .0001f);
            assertEquals(baseBounds.bottom, returned.bottom, .0001f);
            assertEquals(7L,view.snapshotItemsForTesting().get(0).trackId);
        });
    }
    @Test public void s10s12StaticBoundaryClearsGeometryBadgesAndInFlightAnimations() {
        AtomicReference<DetectionOverlayView> reference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
            reference.set(view); view.layout(0,0,720,1280);
            view.setPresentationStamp(new com.example.alpr_v1.continuity.ContinuityStamp(1L,1L,0L,1L));
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,new RectF(.2f,.3f,.7f,.6f),7L)),720,1280);
            view.setActiveVehicleEntityId(7L);
            view.animatePlateObservation(animationObservation(7L,77L));
            view.hardResetForNewScene(new com.example.alpr_v1.continuity.ContinuityStamp(2L,2L,0L,2L));
            assertTrue(view.snapshotItemsForTesting().isEmpty());
            assertTrue(view.snapshotRenderBoundsForTesting().isEmpty());
            assertFalse(view.recognizedVehicleForTesting(7L));
            assertEquals(0L,view.plateAbsorptionEntityForTesting());
            assertEquals(0,view.pendingPlateReadingCountForTesting());
            assertEquals(0,view.fadingPlateCountForTesting());
            view.setStationaryScene(true);
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,new RectF(.21f,.3f,.71f,.6f),8L)),720,1280);
            assertEquals(1,view.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
            assertFalse(view.recognizedVehicleForTesting(8L));
        });
        android.os.SystemClock.sleep(1100L);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertFalse(reference.get().recognizedVehicleForTesting(7L));
            assertFalse(reference.get().recognizedVehicleForTesting(8L));
            assertEquals(0L,reference.get().plateAbsorptionEntityForTesting());
        });
    }

    @Test public void vehicleTapResolvesDomainEntityFromVisibleBox() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view=new DetectionOverlayView(InstrumentationRegistry.getInstrumentation().getTargetContext(),null);
            view.layout(0,0,720,1280);
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,new RectF(.2f,.3f,.7f,.6f),47L)),720,1280);
            java.util.concurrent.atomic.AtomicLong selected=new java.util.concurrent.atomic.AtomicLong();
            view.setVehicleTapListener(selected::set);
            android.view.MotionEvent down=android.view.MotionEvent.obtain(1L,1L,0,300f,500f,0);
            android.view.MotionEvent up=android.view.MotionEvent.obtain(1L,2L,1,300f,500f,0);
            assertTrue(view.onTouchEvent(down)); assertTrue(view.onTouchEvent(up));
            assertEquals(47L,selected.get()); down.recycle(); up.recycle();
        });
    }

    @Test
    public void badgeRejectsWeakerReadingsFromBothMzAndPipelineUpdates() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,
                    new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L)), 720, 1280);
            view.animatePlateObservation(animationObservation(7L, 77L, "WX1234", 0.95, false));
            String bestLabel = view.vehicleLabelForTesting(7L);
            assertTrue(bestLabel.contains("WX1234"));
            assertTrue(bestLabel.contains("95%"));
            view.animatePlateObservation(animationObservation(7L, 88L, "WX1284", 0.6, false));
            assertEquals(bestLabel, view.vehicleLabelForTesting(7L));
            view.setVehicleEntityStates(Collections.singleton(7L), Collections.singleton(7L),
                    Collections.singletonMap(7L, new EntityRecognitionSnapshot(7L, 88L, "WX1284", 0.65, true, 8)));
            assertEquals(bestLabel, view.vehicleLabelForTesting(7L));
            view.finishPlateAbsorptionForTesting();
            assertEquals(bestLabel, view.vehicleLabelForTesting(7L));
            view.animatePlateObservation(animationObservation(7L, 99L, "WX1254", 0.97, false));
            assertTrue(view.vehicleLabelForTesting(7L).contains("WX1254"));
            assertTrue(view.vehicleLabelForTesting(7L).contains("97%"));
            assertEquals(0L, view.plateAbsorptionEntityForTesting());
            view.resetVehicleEntityStates();
            view.animatePlateObservation(animationObservation(7L, 100L, "AB1234", 0.4, false));
            assertTrue(view.vehicleLabelForTesting(7L).contains("AB1234"));
            view.resetVehicleEntityStates();
        });
    }

    @Test
    public void reusedPlateNumberInNewEpochIsNotHiddenByOldVehicleReading() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            view.setPresentationStamp(com.example.alpr_v1.continuity.ContinuityStamp.initial(1L));
            OverlayItem car = item(OverlayItem.Kind.VEHICLE, new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L);
            view.setItems(Collections.singletonList(car), 720, 1280);
            view.animatePlateObservation(animationObservation(7L, 77L));
            view.finishPlateAbsorptionForTesting();
            assertTrue(view.absorbedPlateTrackForTesting(77L));
            view.setPresentationStamp(new com.example.alpr_v1.continuity.ContinuityStamp(
                    0L, 1L, 0L, 0L, 2L,
                    com.example.alpr_v1.continuity.SourceTimestampDomain.RUNTIME_UPTIME));
            view.setVehicleEntityStates(Collections.singleton(7L), Collections.emptySet(),
                    Collections.singletonMap(7L, new EntityRecognitionSnapshot(7L, 77L, "WX1234", 0.8, false, 1)));
            assertFalse(view.absorbedPlateTrackForTesting(77L));
            view.setItems(Arrays.asList(car, item(OverlayItem.Kind.PLATE,
                    new RectF(0.6f, 0.6f, 0.7f, 0.65f), 77L)), 720, 1280);
            assertEquals(1, view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
            assertTrue(view.vehicleLabelForTesting(7L).contains("WX1234"));
            view.resetVehicleEntityStates();
        });
    }

    @Test
    public void sparseSnapshotAndFadeCannotEraseProvisionalReadOrCancelItsFlight() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            OverlayItem car = item(OverlayItem.Kind.VEHICLE, new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L);
            view.setItems(Collections.singletonList(car), 720, 1280);
            int unconfirmedColor = view.vehicleColorForTesting(7L);
            view.animatePlateObservation(animationObservation(7L, 77L));
            view.setVehicleEntityStates(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
            view.fadeOutPlateItems();
            assertEquals(7L, view.plateAbsorptionEntityForTesting());
            view.setItems(Collections.emptyList(), 720, 1280);
            view.finishPlateAbsorptionForTesting();
            view.setItems(Collections.singletonList(car), 720, 1280);
            view.setVehicleEntityStates(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
            assertTrue(view.vehicleLabelForTesting(7L).contains("WX1234"));
            assertFalse(view.confirmedVehicleForTesting(7L));
            assertEquals(android.graphics.Color.argb(245, 52, 211, 153), view.vehicleColorForTesting(7L));
            view.setActiveVehicleEntityId(7L);
            assertEquals(android.graphics.Color.argb(245, 52, 211, 153), view.vehicleColorForTesting(7L));
            assertEquals(view.vehicleColorForTesting(7L), view.activeVehicleMarkerColorForTesting());
            view.setVehicleEntityStates(Collections.singleton(7L), Collections.singleton(7L),
                    Collections.singletonMap(7L, new EntityRecognitionSnapshot(7L, 88L, "WX1234", 0.95, true, 3)));
            assertEquals(0L, view.plateAbsorptionEntityForTesting());
            assertTrue(view.confirmedVehicleForTesting(7L));
            assertTrue(view.vehicleColorForTesting(7L) != unconfirmedColor);
            view.resetVehicleEntityStates();
            assertEquals("P7", view.vehicleLabelForTesting(7L));
        });
    }

    @Test
    public void unresolvedReadWaitsAtPlateThenFliesWhenOwnerArrives() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            view.setStationaryScene(true);
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,
                    new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L)), 720, 1280);
            view.animatePlateObservation(animationObservation(0L, 77L));
            view.fadeOutPlateItems();
            view.setVehicleEntityStates(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
            assertEquals(1, view.pendingPlateReadingCountForTesting());
            assertEquals("P7", view.vehicleLabelForTesting(7L));
            view.animatePlateObservation(animationObservation(7L, 77L));
            assertEquals(0, view.pendingPlateReadingCountForTesting());
            assertEquals(7L, view.plateAbsorptionEntityForTesting());
            view.finishPlateAbsorptionForTesting();
            assertTrue(view.vehicleLabelForTesting(7L).contains("WX1234"));
            view.resetVehicleEntityStates();
        });
    }

    @Test
    public void sourceRemainsVisibleUntilDestinationBadgeCanBePlaced() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            OverlayItem plate = item(OverlayItem.Kind.PLATE, new RectF(0.2f, 0.6f, 0.3f, 0.65f), 77L);
            view.setItems(Arrays.asList(item(OverlayItem.Kind.VEHICLE, new RectF(0, 0, 1, 1), 7L), plate), 720, 1280);
            view.setVehicleEntityStates(Collections.singleton(7L), Collections.emptySet(),
                    Collections.singletonMap(7L, new EntityRecognitionSnapshot(7L, 77L, "WX1234", 0.8, false, 1)));
            assertEquals(0L, view.plateAbsorptionEntityForTesting());
            assertEquals(1, view.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
            view.setItems(Arrays.asList(item(OverlayItem.Kind.VEHICLE,
                    new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L), plate), 720, 1280);
            assertEquals(7L, view.plateAbsorptionEntityForTesting());
            view.finishPlateAbsorptionForTesting();
            view.resetVehicleEntityStates();
        });
    }

    @Test
    public void stationaryPreviewSuppressesJitterAndBriefMissingVehicleButExplicitClearWins() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            OverlayItem car = item(OverlayItem.Kind.VEHICLE, new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L);
            view.setItems(Collections.singletonList(car), 720, 1280);
            RectF before = view.vehicleBoundsForTesting(7L);
            view.setStationaryScene(true);
            view.setPreviewItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,
                    new RectF(0.103f, 0.302f, 0.504f, 0.702f), 7L)));
            assertEquals(before, view.vehicleBoundsForTesting(7L));
            view.setPreviewItems(Collections.singletonList(item(OverlayItem.Kind.PLATE,
                    new RectF(0.2f, 0.6f, 0.3f, 0.65f), 77L)));
            assertEquals(before, view.vehicleBoundsForTesting(7L));
            view.setStationaryScene(false);
            view.setPreviewItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,
                    new RectF(0.103f, 0.302f, 0.504f, 0.702f), 7L)));
            assertFalse(before.equals(view.vehicleBoundsForTesting(7L)));
            view.setItems(Collections.emptyList(), 720, 1280);
            assertTrue(view.snapshotItemsForTesting().isEmpty());
        });
    }

    @Test
    public void firstMzAnimatesFromCropGeometryEvenWhenPlateOverlayHasExpired() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            view.setItems(Collections.singletonList(item(OverlayItem.Kind.VEHICLE,
                    new RectF(0.1f, 0.3f, 0.5f, 0.7f), 7L)), 720, 1280);
            com.example.alpr_v1.pipeline.PlateObservation observation = animationObservation(7L, 77L);
            view.animatePlateObservation(observation);
            assertEquals(7L, view.plateAbsorptionEntityForTesting());
            assertFalse(view.confirmedVehicleForTesting(7L));
            assertTrue(view.absorbedPlateTrackForTesting(77L));
            view.finishPlateAbsorptionForTesting();
            assertTrue(view.vehicleLabelForTesting(7L).contains("WX1234"));
            view.animatePlateObservation(observation);
            assertEquals(0L, view.plateAbsorptionEntityForTesting());
            view.resetVehicleEntityStates();
            assertFalse(view.absorbedPlateTrackForTesting(77L));
        });
    }

    @Test
    public void simultaneousFirstReadingsFlyOneAfterAnother() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            view.setItems(Arrays.asList(
                    item(OverlayItem.Kind.VEHICLE, new RectF(0.1f, 0.3f, 0.45f, 0.7f), 7L),
                    item(OverlayItem.Kind.VEHICLE, new RectF(0.55f, 0.3f, 0.95f, 0.7f), 8L)
            ), 720, 1280);
            view.animatePlateObservation(animationObservation(7L, 77L));
            view.animatePlateObservation(animationObservation(8L, 88L));
            assertEquals(7L, view.plateAbsorptionEntityForTesting());
            view.finishPlateAbsorptionForTesting();
            assertEquals(8L, view.plateAbsorptionEntityForTesting());
            view.finishPlateAbsorptionForTesting();
            assertEquals(0L, view.plateAbsorptionEntityForTesting());
        });
    }

    private static com.example.alpr_v1.pipeline.PlateObservation animationObservation(long entity, long track) {
        return animationObservation(entity, track, "WX1234", 0.8, false);
    }

    private static com.example.alpr_v1.pipeline.PlateObservation animationObservation(
            long entity, long track, String text, double confidence, boolean confirmed) {
        return new com.example.alpr_v1.pipeline.PlateObservation(track,
                entity > 0L ? com.example.alpr_v1.pipeline.PlateVehicleAssociation.direct(entity, entity, "test")
                        : com.example.alpr_v1.pipeline.PlateVehicleAssociation.unassigned("test"),
                com.example.alpr_v1.pipeline.MtWorkKind.VEHICLE_ROI,
                com.example.alpr_v1.pipeline.MtReason.SCAN_NEXT_CANDIDATE,
                1L, null, text, 0.9, confidence, confirmed, 1, Collections.emptyList(),
                1L, 1L, 0.5f, null, null,
                com.example.alpr_v1.pipeline.PlateGeometry.from(720, 1280,
                        new com.example.alpr_v1.vision.Detection(0, 0.9f, 144, 768, 216, 832,
                                Collections.emptyList()), Collections.emptyList()),
                true, true, text, false, 1, "single_row", Collections.emptyList(), "", text);
    }

    @Test
    public void vehicleCalloutsStayOutsideCarsAndActiveCarHasDistinctColor() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 720, 1280);
            view.setItems(Arrays.asList(
                    item(OverlayItem.Kind.VEHICLE, new RectF(0.12f, 0.4f, 0.5f, 0.75f), 7L),
                    item(OverlayItem.Kind.VEHICLE, new RectF(0.53f, 0.38f, 0.95f, 0.73f), 8L)
            ), 720, 1280);
            view.setActiveVehicleEntityId(7L);
            assertEquals(android.graphics.Color.rgb(255, 82, 82), view.vehicleColorForTesting(7L));
            assertEquals(view.vehicleColorForTesting(7L), view.activeVehicleMarkerColorForTesting());
            RectF first = view.vehicleBoundsForTesting(7L);
            RectF second = view.vehicleBoundsForTesting(8L);
            RectF firstBadge = view.vehicleBadgeForTesting(7L);
            RectF secondBadge = view.vehicleBadgeForTesting(8L);
            assertTrue(firstBadge != null && secondBadge != null);
            assertFalse(RectF.intersects(firstBadge, first));
            assertFalse(RectF.intersects(firstBadge, second));
            assertFalse(RectF.intersects(secondBadge, first));
            assertFalse(RectF.intersects(secondBadge, second));
            assertFalse(RectF.intersects(firstBadge, secondBadge));
            assertEquals("P7", view.vehicleLabelForTesting(7L));
            assertTrue(view.vehicleColorForTesting(7L) != view.vehicleColorForTesting(8L));
            android.graphics.Bitmap rendered = android.graphics.Bitmap.createBitmap(
                    720, 1280, android.graphics.Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(rendered));
            // The straight leader starts at the exact upper-left corner (outside the rounded stroke).
            assertTrue(android.graphics.Color.alpha(rendered.getPixel(
                    Math.round(first.left), Math.round(first.top))) > 0);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new java.io.File(context.getExternalFilesDir(null), "overlay-callouts-qa.png"))) {
                rendered.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } catch (java.io.IOException error) {
                throw new AssertionError(error);
            } finally {
                rendered.recycle();
            }
        });
    }

    @Test
    public void vehicleFrameAndEntityNumberAreVisibleWithoutDiagnosticHud() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<Integer> vehicleCount = new AtomicReference<>();
        AtomicReference<Integer> roiCount = new AtomicReference<>();
        AtomicReference<String> vehicleLabel = new AtomicReference<>();
        AtomicReference<String> transferVehicleLabel = new AtomicReference<>();
        AtomicReference<String> recognitionVehicleLabel = new AtomicReference<>();
        AtomicReference<String> completedVehicleLabel = new AtomicReference<>();
        AtomicReference<Boolean> recognized = new AtomicReference<>();
        AtomicReference<Boolean> recognizedAfterRead = new AtomicReference<>();
        AtomicReference<RectF> vehicleBadge = new AtomicReference<>();
        AtomicReference<RectF> vehicleBounds = new AtomicReference<>();
        AtomicReference<Long> absorptionEntity = new AtomicReference<>();
        AtomicReference<Boolean> absorbedPlate = new AtomicReference<>();
        AtomicReference<Integer> remainingPlateCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setItems(Arrays.asList(
                    item(
                            OverlayItem.Kind.VEHICLE,
                            new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                            7L
                    ),
                    item(
                            OverlayItem.Kind.VEHICLE_ROI,
                            new RectF(0.15f, 0.25f, 0.65f, 0.60f),
                            7L
                    ),
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.34f, 0.47f, 0.46f, 0.51f),
                            77L
                    )
            ), 1920, 1080);
            view.setVehicleEntityProgress(
                    Collections.singleton(7L),
                    Collections.emptySet()
            );

            vehicleCount.set(view.renderedKindCountForTesting(
                    OverlayItem.Kind.VEHICLE
            ));
            roiCount.set(view.renderedKindCountForTesting(
                    OverlayItem.Kind.VEHICLE_ROI
            ));
            vehicleLabel.set(view.vehicleLabelForTesting(7L));
            recognized.set(view.recognizedVehicleForTesting(7L));
            EntityRecognitionSnapshot recognition = new EntityRecognitionSnapshot(
                    7L, 77L, "WX1234", 0.82, true, 2
            );
            view.setVehicleEntityStates(
                    Collections.singleton(7L),
                    Collections.emptySet(),
                    Collections.singletonMap(7L, recognition)
            );
            // Terminalny fade nie może przejąć ramki konsumowanej przez transfer.
            view.fadeOutPlateItems();
            transferVehicleLabel.set(view.vehicleLabelForTesting(7L));
            recognizedAfterRead.set(view.recognizedVehicleForTesting(7L));
            vehicleBadge.set(view.vehicleBadgeForTesting(7L));
            vehicleBounds.set(view.vehicleBoundsForTesting(7L));
            absorptionEntity.set(view.plateAbsorptionEntityForTesting());
            absorbedPlate.set(view.absorbedPlateTrackForTesting(77L));
            remainingPlateCount.set(view.renderedKindCountForTesting(
                    OverlayItem.Kind.PLATE
            ));
            view.finishPlateAbsorptionForTesting();
            recognitionVehicleLabel.set(view.vehicleLabelForTesting(7L));
            recognizedAfterRead.set(view.recognizedVehicleForTesting(7L));
            view.setVehicleEntityStates(
                    Collections.singleton(7L),
                    Collections.singleton(7L),
                    Collections.singletonMap(7L, recognition)
            );
            completedVehicleLabel.set(view.vehicleLabelForTesting(7L));
        });

        assertEquals(1, (int) vehicleCount.get());
        assertEquals(0, (int) roiCount.get());
        assertEquals("P7", vehicleLabel.get());
        assertEquals("P7: WX1234 · 82%", transferVehicleLabel.get());
        assertEquals(
                "P7: WX1234 · 82%",
                recognitionVehicleLabel.get()
        );
        assertEquals(7L, (long) absorptionEntity.get());
        assertTrue(absorbedPlate.get());
        assertEquals(0, (int) remainingPlateCount.get());
        assertEquals("P7: WX1234 · 82%", completedVehicleLabel.get());
        assertFalse(recognized.get());
        assertTrue(recognizedAfterRead.get());
        assertTrue(vehicleBadge.get().centerY() < vehicleBounds.get().centerY());
    }

    @Test
    public void provisionalTransferIsNotRepeatedWhenRecognitionBecomesConfirmed() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<String> provisionalLabel = new AtomicReference<>();
        AtomicReference<String> confirmedLabel = new AtomicReference<>();
        AtomicReference<Long> secondAbsorptionEntity = new AtomicReference<>();
        AtomicReference<Boolean> provisionalConfirmed = new AtomicReference<>();
        AtomicReference<Boolean> provisionalRecognized = new AtomicReference<>();
        AtomicReference<Boolean> finalConfirmed = new AtomicReference<>();
        AtomicReference<Integer> remainingPlateCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            OverlayItem vehicle = item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                    7L
            );
            view.setItems(Arrays.asList(
                    vehicle,
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.34f, 0.47f, 0.46f, 0.51f),
                            77L
                    )
            ), 1920, 1080);
            EntityRecognitionSnapshot provisional = new EntityRecognitionSnapshot(
                    7L, 77L, "WX1234", 0.60, false, 1
            );
            view.setVehicleEntityStates(
                    Collections.singleton(7L),
                    Collections.emptySet(),
                    Collections.singletonMap(7L, provisional)
            );
            view.fadeOutPlateItems();
            view.finishPlateAbsorptionForTesting();
            provisionalLabel.set(view.vehicleLabelForTesting(7L));
            provisionalConfirmed.set(view.confirmedVehicleForTesting(7L));
            provisionalRecognized.set(view.recognizedVehicleForTesting(7L));

            view.setItems(Arrays.asList(
                    vehicle,
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.35f, 0.47f, 0.47f, 0.51f),
                            88L
                    )
            ), 1920, 1080);
            EntityRecognitionSnapshot confirmed = new EntityRecognitionSnapshot(
                    7L, 88L, "WX1234", 0.82, true, 2
            );
            view.setVehicleEntityStates(
                    Collections.singleton(7L),
                    Collections.singleton(7L),
                    Collections.singletonMap(7L, confirmed)
            );
            secondAbsorptionEntity.set(view.plateAbsorptionEntityForTesting());
            confirmedLabel.set(view.vehicleLabelForTesting(7L));
            finalConfirmed.set(view.confirmedVehicleForTesting(7L));
            remainingPlateCount.set(view.renderedKindCountForTesting(
                    OverlayItem.Kind.PLATE
            ));
        });

        assertEquals(
                "P7: WX1234 \u00b7 60%",
                provisionalLabel.get()
        );
        assertFalse(provisionalConfirmed.get());
        assertTrue(provisionalRecognized.get());
        assertEquals(0L, (long) secondAbsorptionEntity.get());
        assertEquals("P7: WX1234 \u00b7 82%", confirmedLabel.get());
        assertTrue(finalConfirmed.get());
        assertEquals(0, (int) remainingPlateCount.get());
    }

    @Test
    public void previewUpdateReplacesOnlyPlateAndPreservesMpGeometry() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            OverlayItem vehicle = item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                    7L
            );
            OverlayItem roi = item(
                    OverlayItem.Kind.VEHICLE_ROI,
                    new RectF(0.15f, 0.25f, 0.65f, 0.60f),
                    7L
            );
            view.setItems(
                    Arrays.asList(
                            vehicle,
                            roi,
                            item(
                                    OverlayItem.Kind.PLATE,
                                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                                    70L
                            )
                    ),
                    1088,
                    1088
            );

            view.setTrackedPlateItems(Collections.singletonList(item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.31f, 0.46f, 0.43f, 0.50f),
                    70L
            )));
            rendered.set(view.snapshotItemsForTesting());
        });

        List<OverlayItem> items = rendered.get();
        assertEquals(3, items.size());
        assertEquals(OverlayItem.Kind.VEHICLE, items.get(0).kind);
        assertEquals(new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                items.get(0).normalizedBounds);
        assertEquals(OverlayItem.Kind.VEHICLE_ROI, items.get(1).kind);
        assertEquals(new RectF(0.15f, 0.25f, 0.65f, 0.60f),
                items.get(1).normalizedBounds);
        assertEquals(OverlayItem.Kind.PLATE, items.get(2).kind);
        assertEquals(new RectF(0.31f, 0.46f, 0.43f, 0.50f),
                items.get(2).normalizedBounds);
    }

    @Test
    public void emptyPreviewUpdateDoesNotEraseFreshPipelinePlate() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            OverlayItem vehicle = item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                    7L
            );
            OverlayItem freshPlate = item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                    70L
            );
            view.setItems(Arrays.asList(vehicle, freshPlate), 1088, 1088);

            view.setTrackedPlateItems(Collections.emptyList());
            rendered.set(view.snapshotItemsForTesting());
        });

        assertEquals(2, rendered.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, rendered.get().get(0).kind);
        assertEquals(OverlayItem.Kind.PLATE, rendered.get().get(1).kind);
        assertEquals(new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                rendered.get().get(1).normalizedBounds);
    }

    @Test
    public void explicitExpiryRemovesOnlyPlateLayer() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            OverlayItem vehicle = item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                    7L
            );
            OverlayItem plate = item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                    70L
            );
            view.setItems(Arrays.asList(vehicle, plate), 1088, 1088);

            view.clearPlateItems();
            rendered.set(view.snapshotItemsForTesting());
        });

        assertEquals(1, rendered.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, rendered.get().get(0).kind);
        assertEquals(new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                rendered.get().get(0).normalizedBounds);
    }

    @Test
    public void stalePlateFadesAsSnapshotWhilePreviewGeometryKeepsUpdating() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();
        AtomicReference<Integer> fadingCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setItems(Arrays.asList(
                    item(
                            OverlayItem.Kind.VEHICLE,
                            new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                            7L
                    ),
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                            70L
                    )
            ), 1920, 1080);

            view.fadeOutPlateItems();
            view.setPreviewItems(Collections.singletonList(item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.21f, 0.30f, 0.61f, 0.55f),
                    7L
            )));
            rendered.set(view.snapshotItemsForTesting());
            fadingCount.set(view.fadingPlateCountForTesting());
        });

        assertEquals(1, rendered.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, rendered.get().get(0).kind);
        assertEquals(1, (int) fadingCount.get());
    }

    @Test
    public void emptyPipelineFrameKeepsFadeButStoppedAnalysisCancelsIt() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<Integer> activeFadeCount = new AtomicReference<>();
        AtomicReference<Integer> stoppedFadeCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setAnalysisViewportEnabled(true);
            view.setItems(Collections.singletonList(item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                    70L
            )), 1920, 1080);

            view.fadeOutPlateItems();
            view.setItems(Collections.emptyList(), 1920, 1080);
            activeFadeCount.set(view.fadingPlateCountForTesting());

            view.setAnalysisViewportEnabled(false);
            view.setItems(Collections.emptyList(), 1920, 1080);
            stoppedFadeCount.set(view.fadingPlateCountForTesting());
        });

        assertEquals(1, (int) activeFadeCount.get());
        assertEquals(0, (int) stoppedFadeCount.get());
    }

    @Test
    public void hardReleaseCancelsCurrentAndFadingPlateImmediately() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<Integer> fadingBeforeRelease = new AtomicReference<>();
        AtomicReference<Integer> fadingAfterRelease = new AtomicReference<>();
        AtomicReference<List<OverlayItem>> itemsAfterRelease = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setAnalysisViewportEnabled(true);
            view.setItems(Arrays.asList(
                    item(
                            OverlayItem.Kind.VEHICLE,
                            new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                            7L
                    ),
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                            70L
                    )
            ), 1920, 1080);

            view.fadeOutPlateItems();
            fadingBeforeRelease.set(view.fadingPlateCountForTesting());
            view.clearPlateItems();
            fadingAfterRelease.set(view.fadingPlateCountForTesting());
            itemsAfterRelease.set(view.snapshotItemsForTesting());
        });

        assertEquals(1, (int) fadingBeforeRelease.get());
        assertEquals(0, (int) fadingAfterRelease.get());
        assertEquals(1, itemsAfterRelease.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, itemsAfterRelease.get().get(0).kind);
    }

    @Test
    public void nextPlateDoesNotCancelPreviousFadeButSameTrackDoes() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<Integer> differentTrackFadeCount = new AtomicReference<>();
        AtomicReference<Integer> sameTrackFadeCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setAnalysisViewportEnabled(true);
            OverlayItem firstPlate = item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                    70L
            );
            view.setItems(Collections.singletonList(firstPlate), 1920, 1080);
            view.fadeOutPlateItems();

            view.setTrackedPlateItems(Collections.singletonList(item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.65f, 0.45f, 0.77f, 0.49f),
                    71L
            )));
            differentTrackFadeCount.set(view.fadingPlateCountForTesting());

            view.setTrackedPlateItems(Collections.singletonList(firstPlate));
            sameTrackFadeCount.set(view.fadingPlateCountForTesting());
        });

        assertEquals(1, (int) differentTrackFadeCount.get());
        assertEquals(0, (int) sameTrackFadeCount.get());
    }

    @Test
    public void carriedPredictionCannotCancelTerminalPlateFade() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<Integer> afterPrediction = new AtomicReference<>();
        AtomicReference<Integer> afterNextMp = new AtomicReference<>();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setAnalysisViewportEnabled(true);
            OverlayItem freshPlate = item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                    70L
            );
            OverlayItem vehicle = item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.80f, 0.60f),
                    7L
            );
            view.setItems(Arrays.asList(vehicle, freshPlate), 1920, 1080);
            view.fadeOutPlateItems();

            view.setItems(Arrays.asList(
                    vehicle,
                    carriedPlate(freshPlate)
            ), 1920, 1080);
            afterPrediction.set(view.fadingPlateCountForTesting());
            rendered.set(view.snapshotItemsForTesting());

            view.setItems(Collections.singletonList(vehicle), 1920, 1080);
            afterNextMp.set(view.fadingPlateCountForTesting());
        });

        assertEquals(1, (int) afterPrediction.get());
        assertEquals(1, rendered.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, rendered.get().get(0).kind);
        assertEquals(1, (int) afterNextMp.get());
    }

    @Test
    public void plateFadeRemainsVisibleMidwayAndFinishesAfterAboutTwoPointFourSeconds() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<DetectionOverlayView> viewReference = new AtomicReference<>();
        AtomicReference<Float> midwayAlpha = new AtomicReference<>();
        AtomicReference<Integer> finalFadeCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setAnalysisViewportEnabled(true);
            view.setItems(Collections.singletonList(item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                    70L
            )), 1920, 1080);
            view.fadeOutPlateItems();
            viewReference.set(view);
        });

        android.os.SystemClock.sleep(1_100L);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                midwayAlpha.set(viewReference.get().fadingPlateAlphaForTesting())
        );
        android.os.SystemClock.sleep(1_500L);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                finalFadeCount.set(viewReference.get().fadingPlateCountForTesting())
        );

        assertTrue(midwayAlpha.get() > 0.30f);
        assertTrue(midwayAlpha.get() < 0.75f);
        assertEquals(0, (int) finalFadeCount.get());
    }

    @Test
    public void partialExpiryKeepsFreshPlateAndFadesOnlyStalePlate() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();
        AtomicReference<Integer> fadingCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            OverlayItem freshPlate = item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.65f, 0.45f, 0.77f, 0.49f),
                    71L
            );
            view.setItems(Arrays.asList(
                    item(
                            OverlayItem.Kind.VEHICLE,
                            new RectF(0.20f, 0.30f, 0.80f, 0.60f),
                            7L
                    ),
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                            70L
                    ),
                    freshPlate
            ), 1920, 1080);

            view.fadeOutPlateItems(Collections.singletonList(freshPlate));
            rendered.set(view.snapshotItemsForTesting());
            fadingCount.set(view.fadingPlateCountForTesting());
        });

        assertEquals(2, rendered.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, rendered.get().get(0).kind);
        assertEquals(71L, rendered.get().get(1).trackId);
        assertEquals(1, (int) fadingCount.get());
    }

    @Test
    public void consecutiveExpiryMergesFadeSnapshotsWithoutLeavingAStalePlate() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<List<OverlayItem>> rendered = new AtomicReference<>();
        AtomicReference<Integer> fadingCount = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            OverlayItem secondPlate = item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.65f, 0.45f, 0.77f, 0.49f),
                    71L
            );
            view.setItems(Arrays.asList(
                    item(
                            OverlayItem.Kind.VEHICLE,
                            new RectF(0.20f, 0.30f, 0.80f, 0.60f),
                            7L
                    ),
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.30f, 0.45f, 0.42f, 0.49f),
                            70L
                    ),
                    secondPlate
            ), 1920, 1080);

            view.fadeOutPlateItems(Collections.singletonList(secondPlate));
            view.fadeOutPlateItems(Collections.emptyList());
            rendered.set(view.snapshotItemsForTesting());
            fadingCount.set(view.fadingPlateCountForTesting());
        });

        assertEquals(1, rendered.get().size());
        assertEquals(OverlayItem.Kind.VEHICLE, rendered.get().get(0).kind);
        assertEquals(2, (int) fadingCount.get());
    }

    @Test
    public void actualRenderBoundsUseFitCenterLetterbox() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<RectF> renderedBounds = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setItems(Collections.singletonList(item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0f, 0f, 1f, 1f),
                    70L
            )), 1920, 1080);
            renderedBounds.set(view.snapshotRenderBoundsForTesting().get(0));
        });

        RectF bounds = renderedBounds.get();
        assertEquals(0f, bounds.left, 0.01f);
        assertEquals(896.25f, bounds.top, 0.01f);
        assertEquals(1080f, bounds.right, 0.01f);
        assertEquals(1503.75f, bounds.bottom, 0.01f);
    }

    @Test
    public void analysisViewportUsesSameFitCenterMappingAsDetectionFrames() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<RectF> viewportBounds = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setItems(Collections.singletonList(item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                    7L
            )), 1920, 1080);
            view.setAnalysisViewportEnabled(true);
            viewportBounds.set(view.analysisViewportBoundsForTesting());
        });

        RectF bounds = viewportBounds.get();
        assertEquals(54f, bounds.left, 0.01f);
        assertEquals(993.45f, bounds.top, 0.01f);
        assertEquals(1026f, bounds.right, 0.01f);
        assertEquals(1406.55f, bounds.bottom, 0.01f);
    }

    @Test
    public void cameraGeometryDrawsViewportBeforeDetectionAndSurvivesLayerReset() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setPreviewSourceSize(1080, 1920);
            view.setAnalysisViewportEnabled(true);
            RectF before = view.analysisViewportBoundsForTesting();
            assertNotNull(before);
            android.graphics.Bitmap initial = android.graphics.Bitmap.createBitmap(
                    1080, 2400, android.graphics.Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(initial));
            assertTrue(android.graphics.Color.alpha(initial.getPixel(1, 1200)) > 0);
            assertEquals(0, android.graphics.Color.alpha(initial.getPixel(540, 1200)));

            view.setItems(Collections.emptyList());
            view.setPreviewItems(Collections.emptyList());
            view.setAnalysisViewportEnabled(false);
            view.setAnalysisViewportEnabled(true);
            assertEquals(before, view.analysisViewportBoundsForTesting());
            android.graphics.Bitmap restored = android.graphics.Bitmap.createBitmap(
                    1080, 2400, android.graphics.Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(restored));
            assertTrue(initial.sameAs(restored));
            view.setPreviewSourceSize(1920, 1080);
            assertEquals(993.45f, view.analysisViewportBoundsForTesting().top, 0.01f);
            initial.recycle();
            restored.recycle();
        });
    }

    @Test
    public void activeVehicleMarkerUsesLastMpGeometryEvenWhenVehicleFrameIsHidden() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<RectF> cachedBounds = new AtomicReference<>();
        AtomicReference<PointF> awayTip = new AtomicReference<>();
        AtomicReference<PointF> touchingTip = new AtomicReference<>();
        AtomicReference<RectF> mappedBounds = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            RectF vehicleBounds = new RectF(0.20f, 0.30f, 0.60f, 0.55f);
            view.setItems(Arrays.asList(
                    item(OverlayItem.Kind.VEHICLE, vehicleBounds, 7L),
                    item(
                            OverlayItem.Kind.PLATE,
                            new RectF(0.31f, 0.46f, 0.43f, 0.50f),
                            70L
                    )
            ), 1920, 1080);
            view.setActiveVehicleEntityId(7L);

            // Domyślna ramka VEHICLE i tablica są widoczne, a geometria kotwiczy marker.
            assertEquals(2, view.snapshotRenderBoundsForTesting().size());
            cachedBounds.set(view.snapshotActiveVehicleBoundsForTesting());
            awayTip.set(view.activeVehicleMarkerTipForTesting(0f));
            touchingTip.set(view.activeVehicleMarkerTipForTesting(1f));
            mappedBounds.set(OverlayViewportTransform.mapNormalizedToView(
                    vehicleBounds,
                    1920,
                    1080,
                    1080,
                    2400
            ));

            // Krótkotrwały brak warstwy MP nie może zgubić pozycji aktywnego celu.
            view.setPreviewItems(Collections.singletonList(item(
                    OverlayItem.Kind.PLATE,
                    new RectF(0.32f, 0.46f, 0.44f, 0.50f),
                    70L
            )));
            assertEquals(vehicleBounds, view.snapshotActiveVehicleBoundsForTesting());
        });

        assertEquals(new RectF(0.20f, 0.30f, 0.60f, 0.55f), cachedBounds.get());
        assertEquals(mappedBounds.get().centerX(), touchingTip.get().x, 0.01f);
        assertEquals(mappedBounds.get().top, touchingTip.get().y, 0.01f);
        assertEquals(10f * context.getResources().getDisplayMetrics().density,
                touchingTip.get().y - awayTip.get().y,
                0.01f);
    }

    @Test
    public void activeVehicleMarkerExpiresWithGeometryDeadline() {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        AtomicReference<PointF> freshTip = new AtomicReference<>();
        AtomicReference<PointF> expiredTip = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DetectionOverlayView view = new DetectionOverlayView(context, null);
            view.layout(0, 0, 1080, 2400);
            view.setActiveVehicleGeometryMaximumAgeNanos(1_000_000L);
            view.setItems(Collections.singletonList(item(
                    OverlayItem.Kind.VEHICLE,
                    new RectF(0.20f, 0.30f, 0.60f, 0.55f),
                    7L
            )), 1920, 1080);
            view.setActiveVehicleEntityId(7L);
            freshTip.set(view.activeVehicleMarkerTipForTesting(1f));

            android.os.SystemClock.sleep(5L);
            expiredTip.set(view.activeVehicleMarkerTipForTesting(1f));
        });

        org.junit.Assert.assertNotNull(freshTip.get());
        org.junit.Assert.assertNull(expiredTip.get());
    }

    private static OverlayItem item(
            OverlayItem.Kind kind,
            RectF bounds,
            long trackId
    ) {
        return new OverlayItem(
                kind,
                bounds,
                Collections.emptyList(),
                "test",
                trackId,
                false
        );
    }

    private static OverlayItem carriedPlate(OverlayItem source) {
        return new OverlayItem(
                source.kind,
                source.normalizedBounds,
                source.normalizedKeypoints,
                source.label,
                source.trackId,
                true
        );
    }
}
