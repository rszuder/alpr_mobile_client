package com.example.alpr_v1.camera;

import java.nio.ByteBuffer;

import androidx.camera.core.ImageProxy;

import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;

/** Własnościowa, mała kopia płaszczyzny Y, bez zależności od lifecycle ImageProxy. */
public final class LumaFrame {
    public final byte[] gray;
    public final int width;
    public final int height;
    public final long sourceSequence;
    public final long timestampNanos;
    public final SourceTimestampDomain timestampDomain;

    private LumaFrame(
            byte[] gray,
            int width,
            int height,
            long sourceSequence,
            long timestampNanos,
            SourceTimestampDomain timestampDomain
    ) {
        this.gray = gray;
        this.width = width;
        this.height = height;
        this.sourceSequence = Math.max(0L, sourceSequence);
        this.timestampNanos = timestampNanos;
        this.timestampDomain = timestampDomain == null
                ? SourceTimestampDomain.UNKNOWN : timestampDomain;
    }

    static LumaFrame copyFrom(ImageProxy image, int maximumWidth) {
        long timestampNanos = image == null
                ? 0L : image.getImageInfo().getTimestamp();
        return copyFrom(
                image,
                maximumWidth,
                new SourceFrameStamp(
                        0L,
                        Math.max(0L, timestampNanos),
                        SourceTimestampDomain.UNKNOWN,
                        0L, 0L, 0L
                )
        );
    }

    static LumaFrame copyFrom(
            ImageProxy image,
            int maximumWidth,
            SourceFrameStamp sourceFrameStamp
    ) {
        if (image == null || image.getPlanes().length == 0) return null;
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        android.graphics.Rect crop = image.getCropRect();
        int sourceWidth = Math.max(1, crop.width());
        int sourceHeight = Math.max(1, crop.height());
        int targetWidth = Math.min(Math.max(32, maximumWidth), sourceWidth);
        int targetHeight = Math.max(
                1,
                Math.round(sourceHeight * (targetWidth / (float) sourceWidth))
        );
        ByteBuffer buffer = plane.getBuffer().duplicate();
        int base = buffer.position();
        int limit = buffer.limit();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        byte[] gray = copyPlane(
                buffer,
                base,
                limit,
                rowStride,
                pixelStride,
                crop.left,
                crop.top,
                sourceWidth,
                sourceHeight,
                targetWidth,
                targetHeight
        );
        return new LumaFrame(
                gray,
                targetWidth,
                targetHeight,
                sourceFrameStamp == null ? 0L : sourceFrameStamp.sourceSequence,
                sourceFrameStamp == null
                        ? 0L : sourceFrameStamp.sourceTimestampNanos,
                sourceFrameStamp == null
                        ? SourceTimestampDomain.UNKNOWN : sourceFrameStamp.domain
        );
    }

    static byte[] copyPlane(
            ByteBuffer source,
            int base,
            int limit,
            int rowStride,
            int pixelStride,
            int cropLeft,
            int cropTop,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        ByteBuffer buffer = source.duplicate();
        byte[] gray = new byte[Math.max(0, targetWidth * targetHeight)];
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = cropTop + Math.min(
                    sourceHeight - 1,
                    Math.round((y + 0.5f) * sourceHeight / targetHeight - 0.5f)
            );
            int targetRow = y * targetWidth;
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = cropLeft + Math.min(
                        sourceWidth - 1,
                        Math.round((x + 0.5f) * sourceWidth / targetWidth - 0.5f)
                );
                int index = base + sourceY * rowStride + sourceX * pixelStride;
                gray[targetRow + x] = index >= base && index < limit
                        ? buffer.get(index) : 0;
            }
        }
        return gray;
    }
}
