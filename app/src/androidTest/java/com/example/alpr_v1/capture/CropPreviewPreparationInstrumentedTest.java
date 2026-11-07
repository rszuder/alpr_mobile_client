package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CropPreviewPreparationInstrumentedTest {
    @Test public void slowPreparationRetainsOrderAndOwnedBitmapsAcrossOwnerRecreation() throws Exception {
        ViewModelStore owner = new ViewModelStore();
        CaptureGalleryViewModel first = model(owner);
        Bitmap one = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        Bitmap two = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            assertTrue(first.prepareCrop(() -> { held.countDown(); await(release); return crop("one", one); },
                    () -> recycle(one), 4));
            assertTrue(held.await(2, TimeUnit.SECONDS));
            assertTrue(first.prepareCrop(() -> crop("two", two), () -> recycle(two), 4));
            CaptureGalleryViewModel recreated = model(owner);
            assertSame(first, recreated);
            assertTrue(recreated.takePreparedCrops().isEmpty());
            release.countDown();
            recreated.cropPreparationBarrier().get(2, TimeUnit.SECONDS);
            List<CapturedPlateItem> ready = recreated.takePreparedCrops();
            assertEquals(2, ready.size());
            assertEquals("one", ready.get(0).captureId); assertEquals("two", ready.get(1).captureId);
            assertFalse(one.isRecycled()); assertFalse(two.isRecycled());
            recreated.capturedCrops().addAll(ready);
        } finally { release.countDown(); owner.clear(); }
        assertTrue(one.isRecycled()); assertTrue(two.isRecycled());
    }

    @Test public void clearDiscardsLatePreviewAndReleasesItsBitmap() throws Exception {
        ViewModelStore owner = new ViewModelStore();
        CaptureGalleryViewModel gallery = model(owner);
        Bitmap bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            gallery.prepareCrop(() -> { held.countDown(); await(release); return crop("late", bitmap); },
                    () -> recycle(bitmap), 4);
            assertTrue(held.await(2, TimeUnit.SECONDS));
            gallery.invalidatePendingCropPreviews();
            release.countDown(); gallery.cropPreparationBarrier().get(2, TimeUnit.SECONDS);
            assertTrue(gallery.takePreparedCrops().isEmpty());
            assertTrue(bitmap.isRecycled());
        } finally { release.countDown(); owner.clear(); }
    }

    @Test public void capacityIncludesUndeliveredResultsAndFailuresReleaseOwnership() throws Exception {
        ViewModelStore owner = new ViewModelStore();
        CaptureGalleryViewModel gallery = model(owner);
        Bitmap first = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        Bitmap rejected = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        Bitmap failed = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        try {
            assertTrue(gallery.prepareCrop(() -> crop("first", first), () -> recycle(first), 1, "WI1234A"));
            gallery.cropPreparationBarrier().get(2, TimeUnit.SECONDS);
            assertTrue(gallery.previewNumberPending("WI1234A"));
            assertFalse(gallery.prepareCrop(() -> { throw new AssertionError("capacity"); }, () -> recycle(rejected), 1));
            assertTrue(rejected.isRecycled());
            gallery.capturedCrops().addAll(gallery.takePreparedCrops());
            assertFalse(gallery.previewNumberPending("WI1234A"));
            assertTrue(gallery.prepareCrop(() -> { throw new IllegalStateException("synthetic failure"); },
                    () -> recycle(failed), 1, "WI1234A"));
            gallery.cropPreparationBarrier().get(2, TimeUnit.SECONDS);
            assertTrue(failed.isRecycled()); assertFalse(gallery.previewNumberPending("WI1234A"));
        } finally { owner.clear(); }
    }

    private static CaptureGalleryViewModel model(ViewModelStore owner) {
        return new ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory.getInstance()).get(CaptureGalleryViewModel.class);
    }
    private static CapturedPlateItem crop(String id, Bitmap bitmap) {
        return new CapturedPlateItem(id, "synthetic", 1L, bitmap, "WI1234A", 0.9, 0.8, true,
                Collections.emptyList(), 0L, 0L, 0f, null);
    }
    private static void recycle(Bitmap bitmap) { if (!bitmap.isRecycled()) bitmap.recycle(); }
    private static void await(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(3, TimeUnit.SECONDS)); }
}
