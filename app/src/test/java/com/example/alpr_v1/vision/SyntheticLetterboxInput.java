package com.example.alpr_v1.vision;

import java.nio.ByteBuffer;

/** Test-only tensor geometry; keeps the production PreparedInput constructor package-private. */
public final class SyntheticLetterboxInput {
    private SyntheticLetterboxInput() {}
    public static PreparedInput create(float scale,float padX,float padY,int width,int height) {
        return new PreparedInput(ByteBuffer.allocate(0),scale,padX,padY,width,height);
    }
}
