package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.capture.CapturedPlateItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class PlateCaptureAdapterInstrumentedTest {
    @Test
    public void newestCaptureOccupiesFirstAdapterSlot() {
        CapturedPlateItem oldest = crop("crop-1", 1L);
        CapturedPlateItem middle = crop("crop-2", 2L);
        CapturedPlateItem newest = crop("crop-3", 3L);
        PlateCaptureAdapter adapter = new PlateCaptureAdapter(new NoOpListener());

        adapter.setItems(Arrays.asList(oldest, middle, newest));

        assertEquals(3, adapter.getItemCount());
        assertEquals(newest.captureId.hashCode(), adapter.getItemId(0));
        assertEquals(middle.captureId.hashCode(), adapter.getItemId(1));
        assertEquals(oldest.captureId.hashCode(), adapter.getItemId(2));
        oldest.recycle();
        middle.recycle();
        newest.recycle();
    }

    private static CapturedPlateItem crop(String id, long trackId) {
        return new CapturedPlateItem(
                id,
                "adapter-session",
                trackId,
                Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888),
                id,
                0.9,
                0.8,
                true,
                Collections.emptyList(),
                System.currentTimeMillis(),
                android.os.SystemClock.elapsedRealtimeNanos(),
                0.7f,
                null
        );
    }

    private static final class NoOpListener implements PlateCaptureAdapter.SelectionListener {
        @Override
        public void onSelectionChanged(CapturedPlateItem item, boolean selected) {}

        @Override
        public void onVerificationChanged(
                CapturedPlateItem item,
                CapturedPlateItem.VerificationStatus status
        ) {}

        @Override
        public void onCorrectionRequested(CapturedPlateItem item) {}
    }
}
