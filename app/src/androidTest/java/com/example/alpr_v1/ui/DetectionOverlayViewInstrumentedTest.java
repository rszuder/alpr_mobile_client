package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class DetectionOverlayViewInstrumentedTest {
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
}
