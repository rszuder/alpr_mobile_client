package com.example.alpr_v1.vision;

public final class YoloOutputSpec {
    public final int classCount;
    public final int keypointCount;
    public final int keypointDimensions;
    public final boolean hasObjectness;
    public final boolean channelsFirst;
    public final boolean normalizedCoordinates;
    public final int inputWidth;
    public final int inputHeight;
    public final float confidenceThreshold;
    public final float iouThreshold;
    public final int scoreIndex;
    public final int classIndex;

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
        this(
                classCount,
                keypointCount,
                keypointCount > 0 ? 3 : 0,
                hasObjectness,
                channelsFirst,
                normalizedCoordinates,
                inputWidth,
                inputHeight,
                confidenceThreshold,
                iouThreshold,
                4,
                5
        );
    }

    public YoloOutputSpec(
            int classCount,
            int keypointCount,
            int keypointDimensions,
            boolean hasObjectness,
            boolean channelsFirst,
            boolean normalizedCoordinates,
            int inputWidth,
            int inputHeight,
            float confidenceThreshold,
            float iouThreshold,
            int scoreIndex,
            int classIndex
    ) {
        if (classCount <= 0) throw new IllegalArgumentException("classCount musi być dodatnie");
        this.classCount = classCount;
        this.keypointCount = Math.max(0, keypointCount);
        this.keypointDimensions = this.keypointCount == 0 ? 0 : Math.max(2, keypointDimensions);
        this.hasObjectness = hasObjectness;
        this.channelsFirst = channelsFirst;
        this.normalizedCoordinates = normalizedCoordinates;
        this.inputWidth = inputWidth;
        this.inputHeight = inputHeight;
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;
        this.scoreIndex = scoreIndex;
        this.classIndex = classIndex;
    }
}
