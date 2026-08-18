package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.model.ModelInputSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BitmapTensorPreprocessor {
    private BitmapTensorPreprocessor() {}

    public static PreparedInput prepare(Bitmap source, ModelInputSpec spec, TensorInfo tensorInfo) {
        if (!"NHWC".equals(spec.layout()) && !"NCHW".equals(spec.layout())) {
            throw new IllegalArgumentException("Obsługiwane układy wejścia to NHWC i NCHW");
        }
        int targetWidth = spec.width();
        int targetHeight = spec.height();
        int bytesPerElement;
        switch (tensorInfo.dataType) {
            case "FLOAT32": bytesPerElement = 4; break;
            case "UINT8":
            case "INT8": bytesPerElement = 1; break;
            default: throw new IllegalArgumentException("Nieobsługiwany typ wejścia: " + tensorInfo.dataType);
        }
        int expectedBytes = Math.multiplyExact(
                Math.multiplyExact(targetWidth, targetHeight),
                Math.multiplyExact(spec.channels(), bytesPerElement)
        );
        if (spec.channels() != 3 || tensorInfo.byteSize != expectedBytes) {
            throw new IllegalArgumentException(
                    "Manifest wejścia nie odpowiada tensorowi modelu: manifest=" + expectedBytes
                            + " B, tensor=" + tensorInfo.byteSize + " B"
            );
        }
        float scale = Math.min(
                targetWidth / (float) source.getWidth(),
                targetHeight / (float) source.getHeight()
        );
        float scaledWidth = source.getWidth() * scale;
        float scaledHeight = source.getHeight() * scale;
        float padX = (targetWidth - scaledWidth) * 0.5f;
        float padY = (targetHeight - scaledHeight) * 0.5f;

        Bitmap letterboxed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(letterboxed);
        canvas.drawColor(Color.rgb(114, 114, 114));
        canvas.drawBitmap(
                source,
                new Rect(0, 0, source.getWidth(), source.getHeight()),
                new RectF(padX, padY, padX + scaledWidth, padY + scaledHeight),
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG)
        );

        int[] pixels = new int[targetWidth * targetHeight];
        letterboxed.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        ByteBuffer input = ByteBuffer.allocateDirect(tensorInfo.byteSize).order(ByteOrder.nativeOrder());
        boolean bgr = "BGR".equals(spec.colorSpace());
        if ("NHWC".equals(spec.layout())) {
            for (int pixel : pixels) {
                putPixelChannels(input, pixel, bgr, spec, tensorInfo);
            }
        } else {
            for (int channelIndex = 0; channelIndex < 3; channelIndex++) {
                for (int pixel : pixels) {
                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);
                    int value;
                    if (channelIndex == 0) value = bgr ? blue : red;
                    else if (channelIndex == 1) value = green;
                    else value = bgr ? red : blue;
                    put(input, value, spec, tensorInfo);
                }
            }
        }
        input.rewind();
        letterboxed.recycle();
        return new PreparedInput(input, scale, padX, padY, source.getWidth(), source.getHeight());
    }

    private static void putPixelChannels(
            ByteBuffer input,
            int pixel,
            boolean bgr,
            ModelInputSpec spec,
            TensorInfo tensorInfo
    ) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        put(input, bgr ? blue : red, spec, tensorInfo);
        put(input, green, spec, tensorInfo);
        put(input, bgr ? red : blue, spec, tensorInfo);
    }

    private static void put(ByteBuffer buffer, int channel, ModelInputSpec spec, TensorInfo tensor) {
        float realValue = channel * spec.scale() + spec.offset();
        switch (tensor.dataType) {
            case "FLOAT32":
                buffer.putFloat(realValue);
                break;
            case "UINT8": {
                int quantized = quantize(realValue, tensor);
                buffer.put((byte) Math.max(0, Math.min(255, quantized)));
                break;
            }
            case "INT8": {
                int quantized = quantize(realValue, tensor);
                buffer.put((byte) Math.max(-128, Math.min(127, quantized)));
                break;
            }
            default:
                throw new IllegalArgumentException("Nieobsługiwany typ wejścia: " + tensor.dataType);
        }
    }

    private static int quantize(float value, TensorInfo tensor) {
        if (tensor.quantizationScale <= 0f) return Math.round(value);
        return Math.round(value / tensor.quantizationScale) + tensor.quantizationZeroPoint;
    }
}
