package com.example.alpr_v1.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public final class ModelInputSpec {
    private final int width;
    private final int height;
    private final int channels;
    private final String layout;
    private final String colorSpace;
    private final String dataType;
    private final float scale;
    private final float offset;

    public ModelInputSpec(
            int width,
            int height,
            int channels,
            String layout,
            String colorSpace,
            String dataType,
            float scale,
            float offset
    ) {
        if (width <= 0 || height <= 0 || channels <= 0) {
            throw new IllegalArgumentException("Wymiary wejścia modelu muszą być dodatnie");
        }
        this.width = width;
        this.height = height;
        this.channels = channels;
        this.layout = layout;
        this.colorSpace = colorSpace;
        this.dataType = dataType;
        this.scale = scale;
        this.offset = offset;
    }

    public static ModelInputSpec fromJson(JSONObject json) throws JSONException {
        return new ModelInputSpec(
                json.getInt("width"),
                json.getInt("height"),
                json.optInt("channels", 3),
                json.optString("layout", "NHWC").toUpperCase(Locale.ROOT),
                json.optString("color", "RGB").toUpperCase(Locale.ROOT),
                json.optString("data_type", "FLOAT32").toUpperCase(Locale.ROOT),
                (float) json.optDouble("scale", 1.0 / 255.0),
                (float) json.optDouble("offset", 0.0)
        );
    }

    public int width() { return width; }
    public int height() { return height; }
    public int channels() { return channels; }
    public String layout() { return layout; }
    public String colorSpace() { return colorSpace; }
    public String dataType() { return dataType; }
    public float scale() { return scale; }
    public float offset() { return offset; }
}
