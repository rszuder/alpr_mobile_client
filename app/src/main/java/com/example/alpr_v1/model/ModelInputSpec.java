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
    private final Float quantizationScale;
    private final Integer quantizationZeroPoint;

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
        this(width,height,channels,layout,colorSpace,dataType,scale,offset,null,null);
    }

    private ModelInputSpec(int width, int height, int channels, String layout, String colorSpace,
            String dataType, float scale, float offset, Float quantizationScale, Integer quantizationZeroPoint) {
        if (width <= 0 || height <= 0 || channels <= 0) {
            throw new IllegalArgumentException("Wymiary wejścia modelu muszą być dodatnie");
        }
        if (channels != 3 || !("NHWC".equals(layout) || "NCHW".equals(layout))
                || !("RGB".equals(colorSpace) || "BGR".equals(colorSpace))
                || !("FLOAT32".equals(dataType) || "INT8".equals(dataType) || "UINT8".equals(dataType))
                || !Float.isFinite(scale) || !Float.isFinite(offset)) {
            throw new IllegalArgumentException("Nieobsługiwany kontrakt wejścia modelu");
        }
        if (quantizationScale != null && (!Float.isFinite(quantizationScale) || quantizationScale <= 0
                || quantizationZeroPoint == null || "FLOAT32".equals(dataType)
                || quantizationZeroPoint < ("INT8".equals(dataType) ? -128 : 0)
                || quantizationZeroPoint > ("INT8".equals(dataType) ? 127 : 255))) {
            throw new IllegalArgumentException("Nieprawidłowy kontrakt kwantyzacji wejścia");
        }
        this.width = width;
        this.height = height;
        this.channels = channels;
        this.layout = layout;
        this.colorSpace = colorSpace;
        this.dataType = dataType;
        this.scale = scale;
        this.offset = offset;
        this.quantizationScale = quantizationScale;
        this.quantizationZeroPoint = quantizationZeroPoint;
    }

    public static ModelInputSpec fromJson(JSONObject json) throws JSONException {
        // v1 has a fixed mobile preprocessing profile. Explicit incompatible declarations
        // must fail, rather than silently executing a different transform.
        if ((json.has("batch") && json.getDouble("batch") != 1) || !"letterbox".equals(json.optString("resize_mode","letterbox"))
                || (json.has("padding_value") && json.getDouble("padding_value") != 114)
                || !"bilinear".equals(json.optString("interpolation","bilinear"))) {
            throw new JSONException("Wymagane batch=1, resize_mode=letterbox, padding_value=114, interpolation=bilinear");
        }
        JSONObject quantization = json.has("quantization") ? json.getJSONObject("quantization") : null;
        if (quantization != null && quantization.getDouble("zero_point") != quantization.getInt("zero_point"))
            throw new JSONException("zero_point musi być liczbą całkowitą");
        return new ModelInputSpec(
                json.getInt("width"),
                json.getInt("height"),
                json.optInt("channels", 3),
                json.optString("layout", "NHWC").toUpperCase(Locale.ROOT),
                json.optString("color", "RGB").toUpperCase(Locale.ROOT),
                json.optString("data_type", "FLOAT32").toUpperCase(Locale.ROOT),
                (float) json.optDouble("scale", 1.0 / 255.0),
                (float) json.optDouble("offset", 0.0),
                quantization == null ? null : (float) quantization.getDouble("scale"),
                quantization == null ? null : quantization.getInt("zero_point")
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
    public Float quantizationScale() { return quantizationScale; }
    public Integer quantizationZeroPoint() { return quantizationZeroPoint; }
}
