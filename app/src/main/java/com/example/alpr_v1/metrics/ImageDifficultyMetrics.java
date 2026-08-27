package com.example.alpr_v1.metrics;

import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

/** Tanie metryki jasności liczone wyłącznie dla zapisywanego cropa. */
public final class ImageDifficultyMetrics {
    public final boolean available;
    public final double meanLuminance;
    public final double luminanceStddev;
    public final double underexposedRatio;
    public final double overexposedRatio;
    public final double computationMs;

    private ImageDifficultyMetrics(
            boolean available,
            double meanLuminance,
            double luminanceStddev,
            double underexposedRatio,
            double overexposedRatio,
            double computationMs
    ) {
        this.available = available;
        this.meanLuminance = meanLuminance;
        this.luminanceStddev = luminanceStddev;
        this.underexposedRatio = underexposedRatio;
        this.overexposedRatio = overexposedRatio;
        this.computationMs = computationMs;
    }

    public static ImageDifficultyMetrics unavailable() {
        return new ImageDifficultyMetrics(false, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static ImageDifficultyMetrics measure(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0
                || bitmap.getHeight() <= 0) return unavailable();
        long started = android.os.SystemClock.elapsedRealtimeNanos();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = Math.max(1, Math.min(width, height) / 96);
        long count = 0L;
        double sum = 0.0;
        double sumSquares = 0.0;
        long under = 0L;
        long over = 0L;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int color = bitmap.getPixel(x, y);
                double luminance = 0.2126 * android.graphics.Color.red(color)
                        + 0.7152 * android.graphics.Color.green(color)
                        + 0.0722 * android.graphics.Color.blue(color);
                sum += luminance;
                sumSquares += luminance * luminance;
                if (luminance <= 32.0) under++;
                if (luminance >= 223.0) over++;
                count++;
            }
        }
        if (count == 0L) return unavailable();
        double mean = sum / count;
        double variance = Math.max(0.0, sumSquares / count - mean * mean);
        return new ImageDifficultyMetrics(
                true,
                mean,
                Math.sqrt(variance),
                under / (double) count,
                over / (double) count,
                (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("available", available);
        if (!available) return json;
        json.put("mean_luminance", meanLuminance);
        json.put("luminance_stddev", luminanceStddev);
        json.put("underexposed_ratio", underexposedRatio);
        json.put("overexposed_ratio", overexposedRatio);
        json.put("underexposed_threshold", 32);
        json.put("overexposed_threshold", 223);
        json.put("computation_ms", computationMs);
        return json;
    }
}
