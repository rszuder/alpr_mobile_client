package com.example.alpr_v1.capture;

import com.example.alpr_v1.pipeline.PlateCharacter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class CapturedPlateItemTest {
    @Test
    public void characterBoxesAreTheAuthorityForCropSummaryText() {
        assertEquals(
                "WA3G",
                CapturedPlateItem.textForCrop(
                        "WUA3G",
                        "WA3G",
                        Arrays.asList(
                                character("W"),
                                character("A"),
                                character("3"),
                                character("G")
                        )
                )
        );
    }

    @Test
    public void consensusRemainsFallbackForLegacyCropWithoutFreshCharacters() {
        assertEquals(
                "WUA3G",
                CapturedPlateItem.textForCrop(
                        "WUA3G",
                        "",
                        Collections.emptyList()
                )
        );
    }

    private static PlateCharacter character(String label) {
        return new PlateCharacter(label, 0.9, 0f, 0f, 1f, 1f);
    }
}
