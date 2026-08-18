package com.example.alpr_v1.vision;

public final class YoloOutputSpec {
    public final int classCount;
    public final int keypointCount;
    public final boolean hasObjectness;
    public final boolean channelsFirst;
    public final boolean normalizedCoordinates;
    public final int inputWidth;
    public final int inputHeight;
    public final float confidenceThreshold;
    public final float iouThreshold;

    public YoloOutputSpec(
            int classCount,
            int keypointCount,
            boolean hasObjectness,
            boolean channelsFirst,
            boolean normalizedCoordinates,
            int inputWidth,
            int inputHeight,
            float confidenceThreshold,
            float iouThreshold
    ) {
        if (classCount <= 0) throw new IllegalArgumentException("classCount musi być dodatnie");
        this.classCount = classCount;
        this.keypointCount = Math.max(0, keypointCount);
        this.hasObjectness = hasObjectness;
        this.channelsFirst = channelsFirst;
        this.normalizedCoordinates = normalizedCoordinates;
        this.inputWidth = inputWidth;
        this.inputHeight = inputHeight;
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
    }
}
