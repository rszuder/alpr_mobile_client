package com.example.alpr_v1.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RecognitionHistoryStoreInstrumentedTest {
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
}
