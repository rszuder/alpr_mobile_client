package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals("Pojazd 7 · czeka na odczyt", vehicleLabel.get());
        assertEquals("Pojazd 7: WX1234 · 82%", transferVehicleLabel.get());
        assertEquals(
                "Pojazd 7: WX1234 · 82%",
                recognitionVehicleLabel.get()
        );
        assertEquals(7L, (long) absorptionEntity.get());
        assertTrue(absorbedPlate.get());
        assertEquals(0, (int) remainingPlateCount.get());
        assertEquals("Pojazd 7: WX1234 · 82%", completedVehicleLabel.get());
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
                "Pojazd 7: WX1234 \u00b7 60%",
                provisionalLabel.get()
        );
        assertFalse(provisionalConfirmed.get());
        assertTrue(provisionalRecognized.get());
        assertEquals(0L, (long) secondAbsorptionEntity.get());
        assertEquals("Pojazd 7: WX1234 \u00b7 82%", confirmedLabel.get());
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
