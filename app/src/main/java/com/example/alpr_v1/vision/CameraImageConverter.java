package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.camera.core.ImageProxy;

/** Konwertuje kamerowy YUV przez natywną ścieżkę CameraX i uwzględnia obrót. */
public final class CameraImageConverter {
    private CameraImageConverter() {}

    public static Bitmap toBitmap(ImageProxy image) {
        Bitmap bitmap = image.toBitmap();
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
        );
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }
}
