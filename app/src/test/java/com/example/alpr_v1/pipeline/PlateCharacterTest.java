package com.example.alpr_v1.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlateCharacterTest {
    @Test
    public void clampsCoordinatesToPreviewBounds() {
        PlateCharacter character = new PlateCharacter(
                "A", 0.9, -0.2f, 0.1f, 1.3f, 0.9f
        );

        assertEquals(0f, character.left, 0.0001f);
        assertEquals(0.1f, character.top, 0.0001f);
        assertEquals(1f, character.right, 0.0001f);
        assertEquals(0.9f, character.bottom, 0.0001f);
    }
}
