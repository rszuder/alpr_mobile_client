package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.example.alpr_v1.BuildConfig;

import java.util.concurrent.atomic.AtomicLong;


/**
 * Konwertuje obraz CameraX do Bitmap i osobno mierzy:
 *
 * - koszt ImageProxy -> Bitmap,
 * - koszt zastosowania rotacji obrazu.
 */
public final class CameraImageConverter {
    private static final AtomicLong LAST_GEOMETRY_LOG_NANOS = new AtomicLong();

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

        Rect crop = image.getCropRect();
        maybeLogGeometry(image, crop, bitmap);
        if (crop != null
                && (crop.left != 0
                || crop.top != 0
                || crop.width() != bitmap.getWidth()
                || crop.height() != bitmap.getHeight())) {
            Rect safeCrop = new Rect(
                    Math.max(0, crop.left),
                    Math.max(0, crop.top),
                    Math.min(bitmap.getWidth(), crop.right),
                    Math.min(bitmap.getHeight(), crop.bottom)
            );
            if (safeCrop.width() > 0 && safeCrop.height() > 0) {
                Bitmap cropped = Bitmap.createBitmap(
                        bitmap,
                        safeCrop.left,
                        safeCrop.top,
                        safeCrop.width(),
                        safeCrop.height()
                );
                if (cropped != bitmap) bitmap.recycle();
                bitmap = cropped;
            }
        }


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
                        false
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

    private static void maybeLogGeometry(
            ImageProxy image,
            Rect crop,
            Bitmap bitmap
    ) {
        if (!BuildConfig.DEBUG) return;
        long now = SystemClock.elapsedRealtimeNanos();
        long previous = LAST_GEOMETRY_LOG_NANOS.get();
        if (now - previous < 1_000_000_000L
                || !LAST_GEOMETRY_LOG_NANOS.compareAndSet(previous, now)) {
            return;
        }
        Log.d(
                "ALPR_GEOMETRY",
                "image_proxy=" + image.getWidth() + "x" + image.getHeight()
                        + " crop=" + (crop == null ? "none" : crop.toShortString())
                        + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + " rotation=" + image.getImageInfo().getRotationDegrees()
        );
    }
}
