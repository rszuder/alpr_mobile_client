package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;

import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public final class RgbaImageConverter {
    private RgbaImageConverter() {}

    public static Bitmap toBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        if (pixelStride < 4) {
            throw new IllegalArgumentException("Nieprawidłowy pixelStride obrazu RGBA: " + pixelStride);
        }
        int baseOffset = buffer.position();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * rowStride;
            for (int x = 0; x < width; x++) {
                int offset = baseOffset + row + x * pixelStride;
                int alpha = buffer.get(offset) & 0xff;
                int red = buffer.get(offset + 1) & 0xff;
                int green = buffer.get(offset + 2) & 0xff;
                int blue = buffer.get(offset + 3) & 0xff;
                pixels[y * width + x] = Color.argb(alpha, red, green, blue);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        bitmap.recycle();
        return rotated;
    }
}
