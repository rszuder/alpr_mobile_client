package com.example.alpr_v1.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class RecognitionHistoryStoreInstrumentedTest {
    @Test
    public void provisionalCropBecomesEntityEntryAndKeepsItsIdentityDuringAssociationGaps() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);
        upsert(store, 1L, 0L, 0L, 3L, 3L, "WI1234A", 0.9, 0.6f, 1L, source);
        Bitmap firstCopy = store.newestFirst().get(0).previewBitmap;
        // Association may arrive for the exact same observation, with no newer image.
        upsert(store, 1L, 4L, 8L, 3L, 3L, "WI1234A", 0.9, 0.6f, 1L, null);
        assertEquals(1, store.size());
        assertEquals(4L, store.newestFirst().get(0).entityId);
        assertFalse(firstCopy.isRecycled());
        upsert(store, 1L, 0L, 0L, 3L, 3L, "WI1234A", 0.8, 0.5f, 2L, source);
        assertEquals(1, store.size());
        assertEquals(4L, store.newestFirst().get(0).entityId);
        source.recycle();
        store.clear();
    }

    @Test
    public void trackNumberReusedAfterVisualResetDoesNotInheritOldOwner() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);
        assertTrue(store.upsert(1L, 4L, 8L, 3L, 3L, "WI1234A", 0.9, 0.9,
                1L, source, Collections.emptyList(), null, true, 2, 0.6f, "normal", 1L));
        assertTrue(store.upsert(1L, 0L, 0L, 3L, 3L, "WI1234A", 0.9, 0.9,
                2L, source, Collections.emptyList(), null, false, 1, 0.6f, "normal", 2L));
        assertEquals(2, store.size());
        assertEquals(0L, store.newestFirst().get(0).entityId);
        source.recycle();
        store.clear();
    }

    @Test
    public void identicalOcrOnDifferentProvenEntitiesDoesNotMergeTheirCrops() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);
        upsert(store, 1L, 4L, 8L, 3L, 3L, "WI1234A", 0.9, 0.6f, 1L, source);
        upsert(store, 1L, 5L, 9L, 4L, 4L, "WI1234A", 0.9, 0.6f, 2L, source);
        assertEquals(2, store.size());
        source.recycle();
        store.clear();
    }

    @Test
    public void emptyAndPartialAttemptsAreCapturedWithoutWaitingForGreenState() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);
        for (String text : new String[]{"", "W", "WI", "WI1"}) {
            assertTrue(store.upsert(1L, 1L, 1L, 1L, 1L, text, 0.2, 0.9,
                    text.length() + 1L, source, false, 1, 0.5f, "normal"));
            assertEquals(1, store.size());
            assertEquals(text, store.newestFirst().get(0).text);
            assertFalse(store.newestFirst().get(0).confirmed);
        }
        assertFalse(store.upsert(1L, 1L, 1L, 1L, 1L, "WI1", 0.2, 0.9,
                4L, source, false, 1, 0.5f, "normal"));
        RecognitionHistoryItem detail = store.newestFirst().get(0).snapshot();
        store.clear();
        assertFalse(detail.previewBitmap.isRecycled());
        detail.close();
        source.recycle();
    }

    @Test
    public void firstOcrIsVisibleBeforeConfirmationAndUpdatesTheSameEntry() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);
        assertTrue(store.upsert(
                5L, 10L, 20L, 30L, 30L, "WI1234A", 0.8, 0.9,
                1L, source, false, 1, 0.6f, "normal"
        ));
        RecognitionHistoryItem first = store.newestFirst().get(0);
        assertEquals(1, store.size());
        assertFalse(first.confirmed);
        assertFalse(first.previewBitmap.isRecycled());
        assertTrue(store.upsert(
                5L, 10L, 20L, 30L, 30L, "WI1234B", 0.9, 0.9,
                2L, source, true, 2, 0.7f, "normal"
        ));
        assertEquals(1, store.size());
        assertEquals(first.historyId, store.newestFirst().get(0).historyId);
        assertTrue(first.confirmed);
        assertEquals("WI1234B", first.text);
        source.recycle();
        store.clear();
    }

    @Test
    public void sameSceneAndEntityUpdateOneLogicalReading() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap first = bitmap(Color.RED);
        Bitmap second = bitmap(Color.BLUE);

        upsert(store, 5L, 10L, 20L, 30L, 30L, "WI1234A", 0.80, 0.6f, 1L, first);
        upsert(store, 5L, 10L, 20L, 30L, 30L, "WI1234B", 0.92, 0.7f, 2L, second);

        assertEquals(1, store.size());
        assertEquals("WI1234B", store.newestFirst().get(0).text);
        first.recycle();
        second.recycle();
        store.clear();
    }

    @Test
    public void sameEntityInNewSceneCreatesAnotherReading() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);

        upsert(store, 5L, 10L, 20L, 30L, 30L, "WI1234A", 0.8, 0.6f, 1L, source);
        upsert(store, 6L, 10L, 20L, 30L, 30L, "WI1234A", 0.8, 0.6f, 2L, source);

        assertEquals(2, store.size());
        source.recycle();
        store.clear();
    }

    @Test
    public void missingEntityFallsBackToPlateThenTrack() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap source = bitmap(Color.RED);

        upsert(store, 7L, 0L, 0L, 31L, 31L, "A", 0.8, 0.6f, 1L, source);
        upsert(store, 7L, 0L, 0L, 31L, 31L, "B", 0.9, 0.7f, 2L, source);
        upsert(store, 7L, 0L, 0L, 32L, 32L, "C", 0.8, 0.6f, 3L, source);
        upsert(store, 7L, 0L, 0L, 0L, 44L, "D", 0.8, 0.6f, 4L, source);

        assertEquals(3, store.size());
        source.recycle();
        store.clear();
    }

    @Test
    public void betterPreviewReplacesAndRecyclesPreviousCopy() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap first = bitmap(Color.RED);
        Bitmap weaker = bitmap(Color.GREEN);
        Bitmap sharper = bitmap(Color.BLUE);
        upsert(store, 1L, 1L, 1L, 1L, 1L, "A", 0.9, 0.6f, 1L, first);
        RecognitionHistoryItem item = store.newestFirst().get(0);
        Bitmap firstCopy = item.previewBitmap;
        assertNotSame(first, firstCopy);

        upsert(store, 1L, 1L, 1L, 1L, 1L, "A", 0.8, 1.0f, 2L, weaker);
        assertEquals(Color.RED, item.previewBitmap.getPixel(0, 0));
        assertFalse(firstCopy.isRecycled());

        upsert(store, 1L, 1L, 1L, 1L, 1L, "A", 0.9, 0.8f, 3L, sharper);
        assertTrue(firstCopy.isRecycled());
        assertEquals(Color.BLUE, item.previewBitmap.getPixel(0, 0));
        first.recycle();
        weaker.recycle();
        sharper.recycle();
        store.clear();
    }

    @Test
    public void capacityEvictsAndRecyclesOldestPreview() {
        RecognitionHistoryStore store = new RecognitionHistoryStore(2);
        Bitmap source = bitmap(Color.RED);
        upsert(store, 1L, 1L, 1L, 1L, 1L, "A", 0.8, 0.6f, 1L, source);
        Bitmap oldestPreview = store.newestFirst().get(0).previewBitmap;
        upsert(store, 1L, 2L, 2L, 2L, 2L, "B", 0.8, 0.6f, 2L, source);
        upsert(store, 1L, 3L, 3L, 3L, 3L, "C", 0.8, 0.6f, 3L, source);

        assertEquals(2, store.size());
        assertTrue(oldestPreview.isRecycled());
        source.recycle();
        store.clear();
    }

    @Test
    public void improvedPreviewCarriesMatchingCharacterBoxesAndTiming() {
        RecognitionHistoryStore store = new RecognitionHistoryStore();
        Bitmap first = bitmap(Color.RED);
        Bitmap better = bitmap(Color.BLUE);
        CropInferenceTiming firstTiming = timing(10_000_000L);
        CropInferenceTiming betterTiming = timing(20_000_000L);
        assertTrue(store.upsert(
                1L, 1L, 1L, 1L, 1L, "A", 0.8, 0.9,
                1L, first,
                Collections.singletonList(character("A", 0.8)),
                firstTiming,
                true, 2, 0.6f, "normal"
        ));
        assertTrue(store.upsert(
                1L, 1L, 2L, 2L, 2L, "B", 0.9, 0.9,
                2L, better,
                Collections.singletonList(character("B", 0.95)),
                betterTiming,
                true, 3, 0.7f, "normal"
        ));

        RecognitionHistoryItem item = store.newestFirst().get(0);
        assertEquals("B", item.characters.get(0).label);
        assertEquals(2L, item.vehicleTrackId);
        assertEquals(2L, item.plateTrackId);
        assertEquals(0.95, item.characters.get(0).confidence, 0.0001);
        assertEquals(20.0, item.timing.totalMilliseconds(), 0.0001);
        first.recycle();
        better.recycle();
        store.clear();
    }

    @Test public void weakerDifferentReadingCannotRelabelRetainedCropOrItsProvenance() {
        RecognitionHistoryStore store=new RecognitionHistoryStore();
        Bitmap a=bitmap(Color.RED), b=bitmap(Color.BLUE);
        try {
            store.upsert(1,7,8,1,1,"A",.95,.9,10,a,
                    Collections.singletonList(character("A",.95)),timing(10_000_000),false,1,.8f,"normal");
            RecognitionHistoryItem selected=store.newestFirst().get(0);
            store.upsert(1,7,9,2,2,"B",.4,.8,30,b,
                    Collections.singletonList(character("B",.4)),timing(20_000_000),true,2,.9f,"auto_zoom_retry");
            assertEquals("A",selected.text);assertEquals("A",selected.characters.get(0).label);
            assertEquals(.95,selected.confidence,.0001);assertEquals(.9,selected.plateConfidence,.0001);
            assertEquals(Color.RED,selected.previewBitmap.getPixel(0,0));
            assertEquals(10,selected.capturedAtMillis);assertEquals(30,selected.lastObservationAtMillis);
            assertEquals(1,selected.plateTrackId);assertEquals(8,selected.vehicleTrackId);
            assertFalse(selected.confirmed);assertEquals("normal",selected.captureSource);
            assertEquals(10,selected.timing.totalMilliseconds(),.0001);
            assertEquals(2,selected.observations);
            try(RecognitionHistoryItem snapshot=selected.snapshot()) {
                assertEquals("A",snapshot.text);assertEquals(10,snapshot.previewCapturedAtMillis);
                assertEquals(30,snapshot.lastObservationAtMillis);assertEquals(.95,snapshot.previewConfidence,.0001);
            }
            // A delayed callback is rejected using last observation time, not the retained image's time.
            assertFalse(store.upsert(1,7,9,2,2,"B",1,.8,20,b,
                    Collections.singletonList(character("B",1)),timing(20_000_000),true,2,.9f,"auto_zoom_retry"));
            assertEquals("A",selected.text);
            store.upsert(1,7,9,2,2,"B",.98,.8,40,b,
                    Collections.singletonList(character("B",.98)),timing(40_000_000),true,3,.9f,"auto_zoom_retry");
            assertEquals("B",selected.text);assertEquals("B",selected.characters.get(0).label);
            assertEquals(Color.BLUE,selected.previewBitmap.getPixel(0,0));assertEquals(40,selected.capturedAtMillis);
            assertEquals("auto_zoom_retry",selected.captureSource);assertEquals(2,selected.plateTrackId);
            assertEquals(.98,selected.confidence,.0001);assertEquals(40,selected.timing.totalMilliseconds(),.0001);
        } finally { a.recycle();b.recycle();store.clear(); }
    }

    @Test public void associationPromotionPreservesTheSelectedCropPackage() {
        RecognitionHistoryStore store=new RecognitionHistoryStore();Bitmap a=bitmap(Color.RED),b=bitmap(Color.BLUE);
        try {
            store.upsert(1,0,0,1,1,"A",.95,.9,10,a,
                    Collections.singletonList(character("A",.95)),timing(10_000_000),false,1,.8f,"normal");
            store.upsert(1,0,0,1,1,"B",.4,.8,30,b,
                    Collections.singletonList(character("B",.4)),timing(20_000_000),true,2,.9f,"auto_zoom_retry");
            store.upsert(1,7,8,1,1,"B",.4,.8,30,null,
                    Collections.emptyList(),null,true,2,.9f,"auto_zoom_retry");
            RecognitionHistoryItem selected=store.newestFirst().get(0);
            assertEquals(7,selected.entityId);assertEquals("A",selected.text);
            assertEquals("A",selected.characters.get(0).label);assertEquals(Color.RED,selected.previewBitmap.getPixel(0,0));
            assertEquals(10,selected.capturedAtMillis);assertEquals(30,selected.lastObservationAtMillis);
        } finally {a.recycle();b.recycle();store.clear();}
    }

    private static void upsert(
            RecognitionHistoryStore store,
            long scene,
            long entity,
            long vehicle,
            long plate,
            long track,
            String text,
            double confidence,
            float sharpness,
            long time,
            Bitmap bitmap
    ) {
        assertTrue(store.upsert(
                scene, entity, vehicle, plate, track, text, confidence, 0.9,
                time, bitmap, true, 3, sharpness, "normal"
        ));
    }

    private static Bitmap bitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static PlateCharacter character(String label, double confidence) {
        return new PlateCharacter(label, confidence, 0.1f, 0.2f, 0.3f, 0.8f);
    }

    private static CropInferenceTiming timing(long totalNanos) {
        return new CropInferenceTiming(
                1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, totalNanos
        );
    }
}
