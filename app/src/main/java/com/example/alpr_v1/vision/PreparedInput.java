package com.example.alpr_v1.vision;

import java.nio.ByteBuffer;

public final class PreparedInput {
    public final ByteBuffer buffer;
    public final float scale;
    public final float padX;
    public final float padY;
    public final int sourceWidth;
    public final int sourceHeight;

    PreparedInput(
            ByteBuffer buffer,
            float scale,
            float padX,
            float padY,
            int sourceWidth,
            int sourceHeight
    ) {
        this.buffer = buffer;
        this.scale = scale;
        this.padX = padX;
        this.padY = padY;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
    }

    public float toSourceX(float modelX) {
        return clamp((modelX - padX) / scale, 0, sourceWidth);
    }

    public float toSourceY(float modelY) {
        return clamp((modelY - padY) / scale, 0, sourceHeight);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
