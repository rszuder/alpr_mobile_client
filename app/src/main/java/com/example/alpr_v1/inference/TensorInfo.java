package com.example.alpr_v1.inference;

import java.util.Arrays;

public final class TensorInfo {
    public final int index;
    public final int[] shape;
    public final String dataType;
    public final int byteSize;
    public final float quantizationScale;
    public final int quantizationZeroPoint;

    public TensorInfo(
            int index,
            int[] shape,
            String dataType,
            int byteSize,
            float quantizationScale,
            int quantizationZeroPoint
    ) {
        this.index = index;
        this.shape = shape.clone();
        this.dataType = dataType;
        this.byteSize = byteSize;
        this.quantizationScale = quantizationScale;
        this.quantizationZeroPoint = quantizationZeroPoint;
    }

    @Override
    public String toString() {
        return index + ":" + dataType + Arrays.toString(shape);
    }
}
