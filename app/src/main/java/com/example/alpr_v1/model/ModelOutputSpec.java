package com.example.alpr_v1.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class ModelOutputSpec {
    private final String decoder;
    private final int classCount;
    private final int keypointCount;
    private final boolean hasObjectness;
    private final boolean channelsFirst;
    private final boolean normalizedCoordinates;
    private final boolean nmsInGraph;
    private final float confidenceThreshold;
    private final float iouThreshold;

    private ModelOutputSpec(
            String decoder,
            int classCount,
            int keypointCount,
            boolean hasObjectness,
            boolean channelsFirst,
            boolean normalizedCoordinates,
            boolean nmsInGraph,
            float confidenceThreshold,
            float iouThreshold
    ) {
        this.decoder = decoder;
        this.classCount = classCount;
        this.keypointCount = keypointCount;
        this.hasObjectness = hasObjectness;
        this.channelsFirst = channelsFirst;
        this.normalizedCoordinates = normalizedCoordinates;
        this.nmsInGraph = nmsInGraph;
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
    }

    public static ModelOutputSpec fromJson(JSONObject output) throws JSONException {
        int classCount = output.getInt("class_count");
        if (classCount <= 0) throw new JSONException("class_count musi być dodatnie");
        return new ModelOutputSpec(
                output.getString("decoder"),
                classCount,
                output.optInt("keypoint_count", 0),
                output.optBoolean("has_objectness", false),
                !"anchors_first".equals(output.optString("tensor_layout", "channels_first")),
                output.optBoolean("normalized_coordinates", false),
                output.optBoolean("nms_in_graph", false),
                (float) output.optDouble("confidence_threshold", 0.25),
                (float) output.optDouble("iou_threshold", 0.45)
        );
    }

    public String decoder() { return decoder; }
    public int classCount() { return classCount; }
    public int keypointCount() { return keypointCount; }
    public boolean hasObjectness() { return hasObjectness; }
    public boolean channelsFirst() { return channelsFirst; }
    public boolean normalizedCoordinates() { return normalizedCoordinates; }
    public boolean nmsInGraph() { return nmsInGraph; }
    public float confidenceThreshold() { return confidenceThreshold; }
    public float iouThreshold() { return iouThreshold; }
}
