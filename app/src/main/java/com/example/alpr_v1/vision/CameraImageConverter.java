package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;

import androidx.camera.core.ImageProxy;


/**
 * Konwertuje obraz CameraX do Bitmap i osobno mierzy:
 *
 * - koszt ImageProxy -> Bitmap,
 * - koszt zastosowania rotacji obrazu.
 */
public final class CameraImageConverter {

    private CameraImageConverter() {}


    public static final class Result {

        public final Bitmap bitmap;

        public final long toBitmapNanos;

        public final long rotationNanos;

        public final int rotationDegrees;


        Result(
                Bitmap bitmap,
                long toBitmapNanos,
                long rotationNanos,
                int rotationDegrees
        ) {

            this.bitmap =
                    bitmap;

            this.toBitmapNanos =
                    toBitmapNanos;

            this.rotationNanos =
                    rotationNanos;

            this.rotationDegrees =
                    rotationDegrees;
        }
    }


    public static Result convert(
            ImageProxy image
    ) {

        long started =
                SystemClock.elapsedRealtimeNanos();


        Bitmap bitmap =
                image.toBitmap();


        long toBitmapNanos =
                SystemClock.elapsedRealtimeNanos()
                        - started;


        int rotation =
                image.getImageInfo()
                        .getRotationDegrees();


        if (rotation == 0) {

            return new Result(
                    bitmap,
                    toBitmapNanos,
                    0L,
                    0
            );
        }


        Matrix matrix =
                new Matrix();

        matrix.postRotate(
                rotation
        );


        started =
                SystemClock.elapsedRealtimeNanos();


        Bitmap rotated =
                Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                );


        long rotationNanos =
                SystemClock.elapsedRealtimeNanos()
                        - started;


        if (rotated != bitmap) {

            bitmap.recycle();
        }


        return new Result(
                rotated,
                toBitmapNanos,
                rotationNanos,
                rotation
        );
    }
}