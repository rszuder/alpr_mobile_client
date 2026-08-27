package com.example.alpr_v1.acquisition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.domain.CropReference;

import org.junit.Test;

public class BestCropSelectorTest {
    @Test
    public void selectsOnlyBetterCropAndRecognizesCompletionQuality() {
        BestCropSelector selector = new BestCropSelector();
        BestCropSelector.Quality weak = quality(0.35f, 0.3f, 0.3f);
        BestCropSelector.Quality strong = quality(1f, 1f, 1f);
        CropReference first = selector.candidate(
                "weak", CropReference.Kind.WIDE_PLATE, weak, 10L
        );
        CropReference best = selector.choose(first, selector.candidate(
                "strong", CropReference.Kind.WIDE_PLATE, strong, 20L
        ));

        assertEquals("strong", best.referenceId);
        assertFalse(selector.veryGood(weak));
        assertTrue(selector.veryGood(strong));
        assertTrue(selector.sufficientMixed(quality(0.75f, 0.8f, 0.8f)));
    }

    private static BestCropSelector.Quality quality(
            float general,
            float mt,
            float mz
    ) {
        return new BestCropSelector.Quality(
                general, general, general, general, general, mt, mz, false
        );
    }
}
