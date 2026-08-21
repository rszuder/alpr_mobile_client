package com.example.alpr_v1.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class CaptureGalleryViewModelInstrumentedTest {
    @Test
    public void galleryAndSessionSurviveOwnerRecreation() {
        ViewModelStore retainedStore = new ViewModelStore();
        ViewModelProvider firstOwner = provider(retainedStore);
        CaptureGalleryViewModel first = firstOwner.get(CaptureGalleryViewModel.class);
        Bitmap bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888);
        first.capturedCrops().add(crop(bitmap));
        first.retainSession(true, "rotation-session", 123L, 7);
        first.setGalleryExpanded(false);
        first.setGalleryMaximized(true);

        ViewModelProvider recreatedOwner = provider(retainedStore);
        CaptureGalleryViewModel restored = recreatedOwner.get(CaptureGalleryViewModel.class);

        assertSame(first, restored);
        assertEquals(1, restored.capturedCrops().size());
        assertTrue(restored.collectionActive());
        assertEquals("rotation-session", restored.collectionSessionId());
        assertEquals(123L, restored.collectionSessionStartedElapsedNanos());
        assertEquals(7, restored.collectionSequence());
        assertTrue(restored.galleryExpanded());
        assertTrue(restored.galleryMaximized());
        assertFalse(bitmap.isRecycled());

        retainedStore.clear();
        assertTrue(bitmap.isRecycled());
    }

    @Test
    public void collapsingGalleryLeavesFullscreenMode() {
        ViewModelStore store = new ViewModelStore();
        CaptureGalleryViewModel viewModel = provider(store).get(CaptureGalleryViewModel.class);

        viewModel.setGalleryMaximized(true);
        viewModel.setGalleryExpanded(false);

        assertFalse(viewModel.galleryExpanded());
        assertFalse(viewModel.galleryMaximized());
        store.clear();
    }

    private static ViewModelProvider provider(ViewModelStore store) {
        return new ViewModelProvider(store, ViewModelProvider.NewInstanceFactory.getInstance());
    }

    private static CapturedPlateItem crop(Bitmap bitmap) {
        return new CapturedPlateItem(
                "rotation-crop",
                "rotation-session",
                1L,
                bitmap,
                "KR12345",
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
}
