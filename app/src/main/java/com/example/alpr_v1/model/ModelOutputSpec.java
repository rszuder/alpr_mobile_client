package com.example.alpr_v1.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class ModelOutputSpec {
    private final String decoder;
    private final String outputFormat;
    private final String boxFormat;
    private final int classCount;
    private final int keypointCount;
    private final int keypointDimensions;
    private final boolean hasObjectness;
    private final boolean channelsFirst;
    private final boolean normalizedCoordinates;
    private final boolean nmsInGraph;
    private final boolean nmsRequired;
    private final float confidenceThreshold;
    private final float iouThreshold;
    private final int scoreIndex;
    private final int classIndex;

    private ModelOutputSpec(
            String decoder,
            String outputFormat,
            String boxFormat,
            int classCount,
            int keypointCount,
            int keypointDimensions,
            boolean hasObjectness,
            boolean channelsFirst,
            boolean normalizedCoordinates,
            boolean nmsInGraph,
            boolean nmsRequired,
            float confidenceThreshold,
            float iouThreshold,
            int scoreIndex,
            int classIndex
    ) {
        this.decoder = decoder;
        this.outputFormat = outputFormat;
        this.boxFormat = boxFormat;
        this.classCount = classCount;
        this.keypointCount = keypointCount;
        this.keypointDimensions = keypointDimensions;
        this.hasObjectness = hasObjectness;
        this.channelsFirst = channelsFirst;
        this.normalizedCoordinates = normalizedCoordinates;
        this.nmsInGraph = nmsInGraph;
        this.nmsRequired = nmsRequired;
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
        this.scoreIndex = scoreIndex;
        this.classIndex = classIndex;
    }

    public static ModelOutputSpec fromJson(JSONObject output) throws JSONException {
        int classCount = output.getInt("class_count");
        if (classCount <= 0) throw new JSONException("class_count musi być dodatnie");
        int keypointCount = output.optInt("keypoint_count", 0);
        if (keypointCount < 0) throw new JSONException("keypoint_count nie może być ujemne");
        int keypointDimensions = output.optInt(
                "keypoint_dimensions", keypointCount > 0 ? 3 : 0
        );
        if (keypointCount > 0 && keypointDimensions < 2) {
            throw new JSONException("keypoint_dimensions musi wynosić co najmniej 2");
        }
        if (keypointCount == 0) keypointDimensions = 0;

        String tensorLayout = output.optString("tensor_layout", "channels_first");
        boolean channelsFirst;
        if ("channels_first".equals(tensorLayout)) {
            channelsFirst = true;
        } else if ("anchors_first".equals(tensorLayout)
                || "detections_first".equals(tensorLayout)) {
            channelsFirst = false;
        } else {
            throw new JSONException("Nieobsługiwany tensor_layout: " + tensorLayout);
        }

        int scoreIndex = output.optInt("score_index", 4);
        int classIndex = output.optInt("class_index", 5);
        if (scoreIndex < 0 || classIndex < 0 || scoreIndex == classIndex) {
            throw new JSONException("Nieprawidłowe indeksy score_index/class_index");
        }
        String decoder = output.getString("decoder");
        boolean endToEnd = decoder.contains("_end2end_");
        String outputFormat = output.optString(
                "output_format", endToEnd ? "end2end_detections" : "raw_yolo"
        );
        if (!"raw_yolo".equals(outputFormat) && !"end2end_detections".equals(outputFormat)) {
            throw new JSONException("Nieobsługiwany output_format: " + outputFormat);
        }
        if (endToEnd != "end2end_detections".equals(outputFormat)) {
            throw new JSONException("decoder i output_format opisują różne formaty wyjścia");
        }
        String boxFormat = output.optString("box_format", endToEnd ? "xyxy" : "xywh");
        if ((!endToEnd && !"xywh".equals(boxFormat)) || (endToEnd && !"xyxy".equals(boxFormat))) {
            throw new JSONException("Nieobsługiwany box_format dla dekodera: " + boxFormat);
        }
        boolean nmsRequired = output.optBoolean("nms_required", !endToEnd);
        if (nmsRequired == endToEnd) {
            throw new JSONException("Niespójne nms_required dla output_format " + outputFormat);
        }
        return new ModelOutputSpec(
                decoder,
                outputFormat,
                boxFormat,
                classCount,
                keypointCount,
                keypointDimensions,
                output.optBoolean("has_objectness", false),
                channelsFirst,
                output.optBoolean("normalized_coordinates", false),
                output.optBoolean("nms_in_graph", false),
                nmsRequired,
                (float) output.optDouble("confidence_threshold", 0.25),
                (float) output.optDouble("iou_threshold", 0.45),
                scoreIndex,
                classIndex
        );
    }

    public String decoder() { return decoder; }
    public String outputFormat() { return outputFormat; }
    public String boxFormat() { return boxFormat; }
    public int classCount() { return classCount; }
    public int keypointCount() { return keypointCount; }
    public int keypointDimensions() { return keypointDimensions; }
    public boolean hasObjectness() { return hasObjectness; }
    public boolean channelsFirst() { return channelsFirst; }
    public boolean normalizedCoordinates() { return normalizedCoordinates; }
    public boolean nmsInGraph() { return nmsInGraph; }
    public boolean nmsRequired() { return nmsRequired; }
    public float confidenceThreshold() { return confidenceThreshold; }
    public float iouThreshold() { return iouThreshold; }
    public int scoreIndex() { return scoreIndex; }
    public int classIndex() { return classIndex; }

    /**
     * Ultralytics/PNNX NCNN exports expose the pre-NMS two-dimensional YOLO
     * tensor even when another variant of the same logical model is end-to-end.
     */
    ModelOutputSpec asNcnnRawOutput() {
        if ("raw_yolo".equals(outputFormat)) return this;
        return new ModelOutputSpec(
                keypointCount > 0
                        ? "ultralytics_pose_raw_v1"
                        : "ultralytics_detect_raw_v1",
                "raw_yolo",
                "xywh",
                classCount,
                keypointCount,
                keypointDimensions,
                hasObjectness,
                false,
                normalizedCoordinates,
                false,
                true,
                confidenceThreshold,
                iouThreshold,
                scoreIndex,
                classIndex
        );
    }
}
