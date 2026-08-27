package com.example.alpr_v1.camera;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.nio.ByteBuffer;

public final class LumaFrameTest {
    @Test
    public void copiesCroppedPlaneWithRowPadding() {
        byte[] source = {
                1, 2, 3, 4, 99, 99,
                5, 6, 7, 8, 99, 99,
                9, 10, 11, 12, 99, 99
        };

        byte[] result = LumaFrame.copyPlane(
                ByteBuffer.wrap(source),
                0,
                source.length,
                6,
                1,
                1,
                0,
                3,
                3,
                3,
                3
        );

        assertArrayEquals(new byte[]{2, 3, 4, 6, 7, 8, 10, 11, 12}, result);
    }

    @Test
    public void respectsPixelStride() {
        byte[] source = {
                10, 0, 20, 0, 30, 0,
                40, 0, 50, 0, 60, 0
        };

        byte[] result = LumaFrame.copyPlane(
                ByteBuffer.wrap(source),
                0,
                source.length,
                6,
                2,
                0,
                0,
                3,
                2,
                3,
                2
        );

        assertArrayEquals(new byte[]{10, 20, 30, 40, 50, 60}, result);
    }
}
